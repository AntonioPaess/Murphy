package com.murphy.core.domain

public class MeshSession(public val localNode: NodeId) {
    private val discoveredPeers: MutableMap<NodeId, Peer> = linkedMapOf()
    private val events: MutableList<MeshEvent> = mutableListOf()
    private var currentState: LinkState = LinkState.Disconnected

    public fun snapshot(): MeshSnapshot = MeshSnapshot(
        state = currentState,
        peers = discoveredPeers.values.toList(),
        history = events.toList(),
    )

    public fun beginScan(atMillis: Long): MeshSnapshot {
        currentState = LinkState.Scanning
        events += MeshEvent.ScanStarted(atMillis)
        return snapshot()
    }

    public fun discover(peer: Peer, atMillis: Long): MeshSnapshot {
        discoveredPeers[peer.id] = peer
        events += MeshEvent.PeerDiscovered(peer, atMillis)
        return snapshot()
    }

    public fun finishScan(atMillis: Long): MeshSnapshot {
        events += MeshEvent.ScanFinished(discoveredPeers.size, atMillis)
        currentState = LinkState.Disconnected
        return snapshot()
    }

    public fun startConnection(peer: Peer, atMillis: Long): MeshSnapshot {
        discoveredPeers[peer.id] = peer
        currentState = LinkState.Connecting(peer)
        events += MeshEvent.ConnectionStarted(peer, atMillis)
        return snapshot()
    }

    public fun markConnected(peer: Peer, atMillis: Long): MeshSnapshot {
        discoveredPeers[peer.id] = peer
        currentState = LinkState.Connected(peer, atMillis)
        events += MeshEvent.ConnectionEstablished(peer, atMillis)
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
        events += MeshEvent.ConnectionLost(peer, reason, atMillis)
        return snapshot()
    }

    public fun markFailed(reason: LinkFailureReason, atMillis: Long): MeshSnapshot {
        currentState = LinkState.Failed(reason)
        events += MeshEvent.ConnectionFailed(reason, atMillis)
        return snapshot()
    }
}

