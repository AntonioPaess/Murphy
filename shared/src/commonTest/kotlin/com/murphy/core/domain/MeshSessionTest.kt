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

    @Test
    fun restoresHistoryFromAnInjectedEventStore() {
        val store = InMemoryMeshEventStore()
        val firstSession = MeshSession(NodeId("local"), eventStore = store)

        firstSession.beginScan(atMillis = 1L)

        val restoredSession = MeshSession(NodeId("local"), eventStore = store)

        assertEquals(1, restoredSession.snapshot().history.size)
        assertIs<MeshEvent.ScanStarted>(restoredSession.snapshot().history.single())
    }

    @Test
    fun recordsRoutingDecisionsInTheOfflineHistory() {
        val session = MeshSession(NodeId("local"))
        val envelope = MeshEnvelope(
            id = MessageId("message-1"),
            origin = NodeId("origin"),
            destination = NodeId("remote"),
            payload = "hello",
            createdAtMillis = 10L,
            ttl = 2,
        )

        val forwardingDecision = session.handleIncoming(envelope, atMillis = 11L)

        assertEquals(ForwardDecision.Forward(envelope.copy(ttl = 1)), forwardingDecision)
        assertEquals(
            MeshEvent.MessageForwarded(
                node = NodeId("local"),
                envelope = envelope.copy(ttl = 1),
                atMillis = 11L,
            ),
            session.snapshot().history.single(),
        )

        session.handleIncoming(envelope, atMillis = 12L)

        assertEquals(
            MeshEvent.MessageDropped(
                node = NodeId("local"),
                messageId = envelope.id,
                reason = MessageDropReason.DUPLICATE,
                atMillis = 12L,
            ),
            session.snapshot().history.last(),
        )
    }

    @Test
    fun recordsDeliveryAndTtlExpirationInTheOfflineHistory() {
        val session = MeshSession(NodeId("local"))
        val deliveredEnvelope = MeshEnvelope(
            id = MessageId("message-2"),
            origin = NodeId("origin"),
            destination = NodeId("local"),
            payload = "arrived",
            createdAtMillis = 20L,
            ttl = 0,
        )
        val expiredEnvelope = deliveredEnvelope.copy(
            id = MessageId("message-3"),
            destination = NodeId("remote"),
        )

        assertEquals(
            ForwardDecision.Deliver(deliveredEnvelope),
            session.handleIncoming(deliveredEnvelope, atMillis = 21L),
        )
        assertEquals(
            MeshEvent.MessageDelivered(
                node = NodeId("local"),
                envelope = deliveredEnvelope,
                atMillis = 21L,
            ),
            session.snapshot().history.last(),
        )

        assertEquals(
            ForwardDecision.Expired,
            session.handleIncoming(expiredEnvelope, atMillis = 22L),
        )
        assertEquals(
            MeshEvent.MessageDropped(
                node = NodeId("local"),
                messageId = expiredEnvelope.id,
                reason = MessageDropReason.TTL_EXPIRED,
                atMillis = 22L,
            ),
            session.snapshot().history.last(),
        )
    }
}
