package com.murphy.core.transport

import com.murphy.core.domain.ForwardDecision
import com.murphy.core.domain.LinkFailureReason
import com.murphy.core.domain.MeshEnvelope
import com.murphy.core.domain.MeshSession
import com.murphy.core.domain.MessageDropReason
import com.murphy.core.domain.MessageId
import com.murphy.core.domain.NodeId
import com.murphy.core.domain.Peer

public class MeshTransportCoordinator(
    private val session: MeshSession,
) {
    private val connections: MutableMap<NodeId, ActiveConnection> = linkedMapOf()

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
