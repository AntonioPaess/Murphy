package com.murphy.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MeshSessionTest {
    private val peer = Peer(
        id = NodeId("peer-1"),
        displayName = "Trail buddy",
    )

    @Test
    fun recordsTheConnectionLifecycle() {
        val session = MeshSession(NodeId("local"))

        session.beginScan(atMillis = 1L)
        session.discover(peer, atMillis = 2L)
        session.finishScan(atMillis = 3L)
        session.startConnection(peer, atMillis = 4L)
        val snapshot = session.markConnected(peer, atMillis = 5L)

        assertIs<LinkState.Connected>(snapshot.state)
        assertEquals(peer, snapshot.peers.single())
        assertEquals(5, snapshot.history.size)
        assertEquals(5L, (snapshot.state as LinkState.Connected).connectedAtMillis)
    }

    @Test
    fun movesToReconnectingAfterALinkLoss() {
        val session = MeshSession(NodeId("local"))
        session.startConnection(peer, atMillis = 1L)
        session.markConnected(peer, atMillis = 2L)

        val snapshot = session.markDisconnected(
            peer = peer,
            reason = LinkFailureReason.PEER_UNREACHABLE,
            atMillis = 3L,
        )

        assertEquals(
            LinkState.Reconnecting(
                peer = peer,
                attempt = 1,
                reason = LinkFailureReason.PEER_UNREACHABLE,
            ),
            snapshot.state,
        )
    }
}

