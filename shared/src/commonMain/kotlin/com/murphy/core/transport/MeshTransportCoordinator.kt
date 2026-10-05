package com.murphy.core.transport

import com.murphy.core.domain.ForwardDecision
import com.murphy.core.domain.LinkFailureReason
import com.murphy.core.domain.MeshEnvelope
import com.murphy.core.domain.MeshSession
import com.murphy.core.domain.MessageDropReason
import com.murphy.core.domain.MessageId
import com.murphy.core.domain.NodeId
import com.murphy.core.domain.Peer
import com.murphy.core.domain.RetryStopReason

public class MeshTransportCoordinator(
    private val session: MeshSession,
    private val retryPolicy: MeshRetryPolicy = MeshRetryPolicy(),
) {
    private val connections: MutableMap<NodeId, ActiveConnection> = linkedMapOf()
    private val pending: MutableMap<SendKey, PendingMeshSend> = linkedMapOf()

    public fun pendingSends(): List<PendingMeshSend> = pending.values.toList()

    /** Called by the platform scheduler with the same clock used for incoming batches.
     * Detached peers stay pending without consuming attempts. Cancellation preserves
     * the current entry and propagates. No routing/TTL decision is repeated here.
     */
    public suspend fun retryPending(atMillis: Long): MeshRetryResult {
        val accepted: MutableList<RetrySendAccepted> = mutableListOf()
        val failures: MutableList<RetrySendFailure> = mutableListOf()
        val exhausted: MutableList<PendingMeshSend> = mutableListOf()
        val due = pending.values.filter { it.nextAttemptAtMillis <= atMillis }
        for (entry in due) {
            val connection = connections[entry.peerId]?.connection ?: continue
            val key = SendKey(entry.envelope.id, entry.peerId)
            try {
                connection.send(entry.envelope)
            } catch (failure: BleSendException) {
                failures += RetrySendFailure(entry.envelope.id, entry.peerId, failure.reason)
                session.recordSendFailed(entry.envelope.id, entry.peerId, failure.reason, atMillis)
                val attempted = entry.copy(attempts = entry.attempts + 1)
                if (!scheduleRetry(attempted, atMillis)) {
                    exhausted += attempted
                }
                continue
            }
            pending.remove(key)
            accepted += RetrySendAccepted(entry.envelope.id, entry.peerId)
            session.recordSendAccepted(entry.envelope.id, entry.peerId, atMillis)
        }
        return MeshRetryResult(accepted, failures, exhausted)
    }

    private fun scheduleRetry(entry: PendingMeshSend, atMillis: Long): Boolean {
        val key = SendKey(entry.envelope.id, entry.peerId)
        val stopReason = when {
            entry.attempts >= retryPolicy.maxAttempts -> RetryStopReason.ATTEMPTS_EXHAUSTED
            key !in pending && pending.size >= retryPolicy.capacity -> RetryStopReason.QUEUE_FULL
            else -> null
        }
        if (stopReason != null) {
            pending.remove(key)
            session.recordRetryStopped(entry.envelope.id, entry.peerId, stopReason, atMillis)
            return false
        }
        // Saturate at Long.MAX_VALUE instead of wrapping a far-future deadline.
        val nextAttempt = if (atMillis > Long.MAX_VALUE - retryPolicy.delayMillis) {
            Long.MAX_VALUE
        } else {
            atMillis + retryPolicy.delayMillis
        }
        pending[key] = entry.copy(nextAttemptAtMillis = nextAttempt)
        session.recordRetryScheduled(
            entry.envelope.id, entry.peerId, entry.attempts, nextAttempt, atMillis,
        )
        return true
    }

    public fun attach(peer: Peer, connection: BlePeerConnection) {
        connections[peer.id] = ActiveConnection(peer, connection)
    }

    public fun detach(peerId: NodeId): Peer? = connections.remove(peerId)?.peer

    public fun connectedPeers(): List<Peer> = connections.values
        .map(ActiveConnection::peer)
        .sortedBy { it.id.value }

    public suspend fun receiveFrom(
        peerId: NodeId,
        atMillis: Long,
    ): MeshTransportDecision {
        val connection = connections[peerId]?.connection
            ?: error("No BLE connection is attached for peer $peerId")
        return handleIncoming(
            envelope = connection.receive(),
            fromPeer = peerId,
            atMillis = atMillis,
        )
    }

    public suspend fun handleIncoming(
        envelope: MeshEnvelope,
        fromPeer: NodeId? = null,
        atMillis: Long,
    ): MeshTransportDecision {
        return when (val decision = session.handleIncoming(envelope, atMillis)) {
            is ForwardDecision.Deliver -> MeshTransportDecision.Delivered(decision.envelope)

            is ForwardDecision.Forward -> {
                val targets = connections
                    .asSequence()
                    .filterNot { (peerId, _) -> peerId == fromPeer }
                    .sortedBy { (peerId, _) -> peerId.value }
                    .toList()

                if (targets.isEmpty()) {
                    session.recordNoRoute(envelope.id, atMillis)
                    return MeshTransportDecision.NoRoute(decision.envelope)
                }

                val accepted: MutableList<NodeId> = mutableListOf()
                val failures: MutableList<PeerSendFailure> = mutableListOf()
                for ((peerId, activeConnection) in targets) {
                    try {
                        activeConnection.connection.send(decision.envelope)
                    } catch (failure: BleSendException) {
                        failures += PeerSendFailure(peerId, failure.reason)
                        session.recordSendFailed(envelope.id, peerId, failure.reason, atMillis)
                        scheduleRetry(PendingMeshSend(decision.envelope, peerId, 1, atMillis), atMillis)
                        continue
                    }
                    accepted += peerId
                    session.recordSendAccepted(envelope.id, peerId, atMillis)
                }

                MeshTransportDecision.Forwarded(
                    envelope = decision.envelope,
                    peerIds = accepted,
                    failures = failures,
                )
            }

            ForwardDecision.Duplicate -> MeshTransportDecision.Dropped(
                messageId = envelope.id,
                reason = MessageDropReason.DUPLICATE,
            )

            ForwardDecision.Expired -> MeshTransportDecision.Dropped(
                messageId = envelope.id,
                reason = MessageDropReason.TTL_EXPIRED,
            )
        }
    }

    private data class ActiveConnection(
        val peer: Peer,
        val connection: BlePeerConnection,
    )

    private data class SendKey(val messageId: MessageId, val peerId: NodeId)
}

public data class PeerSendFailure(
    public val peerId: NodeId,
    public val reason: LinkFailureReason,
)

public sealed interface MeshTransportDecision {
    public data class Delivered(
        public val envelope: MeshEnvelope,
    ) : MeshTransportDecision

    public data class Forwarded(
        public val envelope: MeshEnvelope,
        public val peerIds: List<NodeId>,
        public val failures: List<PeerSendFailure> = emptyList(),
    ) : MeshTransportDecision

    public data class NoRoute(
        public val envelope: MeshEnvelope,
    ) : MeshTransportDecision

    public data class Dropped(
        public val messageId: MessageId,
        public val reason: MessageDropReason,
    ) : MeshTransportDecision
}
