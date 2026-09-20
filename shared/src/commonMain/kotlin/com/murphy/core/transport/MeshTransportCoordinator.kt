package com.murphy.core.transport

import com.murphy.core.domain.ForwardDecision
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

                for ((_, activeConnection) in targets) {
                    activeConnection.connection.send(decision.envelope)
                }

                MeshTransportDecision.Forwarded(
                    envelope = decision.envelope,
                    peerIds = targets.map { (peerId, _) -> peerId },
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

public sealed interface MeshTransportDecision {
    public data class Delivered(
        public val envelope: MeshEnvelope,
    ) : MeshTransportDecision

    public data class Forwarded(
        public val envelope: MeshEnvelope,
        public val peerIds: List<NodeId>,
    ) : MeshTransportDecision

    public data class Dropped(
        public val messageId: MessageId,
        public val reason: MessageDropReason,
    ) : MeshTransportDecision
}
