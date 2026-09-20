package com.murphy.core.domain

public class MeshSession(
    public val localNode: NodeId,
    private val eventStore: MeshEventStore = InMemoryMeshEventStore(),
) {
    private val discoveredPeers: MutableMap<NodeId, Peer> = linkedMapOf()
    private val router: MeshRouter = MeshRouter(localNode)
    private var currentState: LinkState = LinkState.Disconnected

    public fun snapshot(): MeshSnapshot = MeshSnapshot(
        state = currentState,
        peers = discoveredPeers.values.toList(),
        history = eventStore.all(),
    )

    public fun beginScan(atMillis: Long): MeshSnapshot {
        currentState = LinkState.Scanning
        eventStore.append(MeshEvent.ScanStarted(atMillis))
        return snapshot()
    }

    public fun discover(peer: Peer, atMillis: Long): MeshSnapshot {
        discoveredPeers[peer.id] = peer
        eventStore.append(MeshEvent.PeerDiscovered(peer, atMillis))
        return snapshot()
    }

    public fun finishScan(atMillis: Long): MeshSnapshot {
        eventStore.append(MeshEvent.ScanFinished(discoveredPeers.size, atMillis))
        currentState = LinkState.Disconnected
        return snapshot()
    }

    public fun startConnection(peer: Peer, atMillis: Long): MeshSnapshot {
        discoveredPeers[peer.id] = peer
        currentState = LinkState.Connecting(peer)
        eventStore.append(MeshEvent.ConnectionStarted(peer, atMillis))
        return snapshot()
    }

    public fun markConnected(peer: Peer, atMillis: Long): MeshSnapshot {
        discoveredPeers[peer.id] = peer
        currentState = LinkState.Connected(peer, atMillis)
        eventStore.append(MeshEvent.ConnectionEstablished(peer, atMillis))
        return snapshot()
    }

    public fun markDisconnected(
        peer: Peer,
        reason: LinkFailureReason,
        atMillis: Long,
    ): MeshSnapshot {
        val nextAttempt = when (val state = currentState) {
            is LinkState.Reconnecting -> state.attempt + 1
            else -> 1
        }
        currentState = LinkState.Reconnecting(peer, nextAttempt, reason)
        eventStore.append(MeshEvent.ConnectionLost(peer, reason, atMillis))
        return snapshot()
    }

    public fun markFailed(reason: LinkFailureReason, atMillis: Long): MeshSnapshot {
        currentState = LinkState.Failed(reason)
        eventStore.append(MeshEvent.ConnectionFailed(reason, atMillis))
        return snapshot()
    }

    public fun handleIncoming(envelope: MeshEnvelope, atMillis: Long): ForwardDecision {
        val decision = router.accept(envelope)
        when (decision) {
            is ForwardDecision.Deliver -> eventStore.append(
                MeshEvent.MessageDelivered(
                    node = localNode,
                    envelope = decision.envelope,
                    atMillis = atMillis,
                ),
            )

            is ForwardDecision.Forward -> eventStore.append(
                MeshEvent.MessageForwarded(
                    node = localNode,
                    envelope = decision.envelope,
                    atMillis = atMillis,
                ),
            )

            ForwardDecision.Duplicate -> eventStore.append(
                MeshEvent.MessageDropped(
                    node = localNode,
                    messageId = envelope.id,
                    reason = MessageDropReason.DUPLICATE,
                    atMillis = atMillis,
                ),
            )

            ForwardDecision.Expired -> eventStore.append(
                MeshEvent.MessageDropped(
                    node = localNode,
                    messageId = envelope.id,
                    reason = MessageDropReason.TTL_EXPIRED,
                    atMillis = atMillis,
                ),
            )
        }
        return decision
    }
}
