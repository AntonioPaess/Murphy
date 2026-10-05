package com.murphy.core.transport

import com.murphy.core.domain.MeshEnvelope
import com.murphy.core.domain.LinkFailureReason
import com.murphy.core.domain.MeshEvent
import com.murphy.core.domain.MeshSession
import com.murphy.core.domain.MessageDropReason
import com.murphy.core.domain.MessageId
import com.murphy.core.domain.NodeId
import com.murphy.core.domain.Peer
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MeshTransportCoordinatorTest {
    private val local = NodeId("local")
    private val relay = Peer(NodeId("relay"), "Relay")
    private val companion = Peer(NodeId("companion"), "Companion")

    @Test
    fun floodsAForwardedEnvelopeToAttachedPeersExceptTheSource() {
        val relayConnection = FakeBlePeerConnection()
        val companionConnection = FakeBlePeerConnection()
        val coordinator = MeshTransportCoordinator(MeshSession(local))
        coordinator.attach(relay, relayConnection)
        coordinator.attach(companion, companionConnection)
        val envelope = envelope(ttl = 2)

        val result = runSuspend {
            coordinator.handleIncoming(
                envelope = envelope,
                fromPeer = relay.id,
                atMillis = 11L,
            )
        }

        assertEquals(
            MeshTransportDecision.Forwarded(
                envelope = envelope.copy(ttl = 1),
                peerIds = listOf(companion.id),
            ),
            result,
        )
        assertEquals(emptyList(), relayConnection.sent)
        assertEquals(listOf(envelope.copy(ttl = 1)), companionConnection.sent)
    }

    @Test
    fun receivesFromBleAndDeliversLocallyWithoutSendingItBack() {
        val connection = FakeBlePeerConnection(incoming = envelope(destination = local, ttl = 0))
        val coordinator = MeshTransportCoordinator(MeshSession(local))
        coordinator.attach(relay, connection)

        val result = runSuspend {
            coordinator.receiveFrom(peerId = relay.id, atMillis = 12L)
        }

        assertEquals(
            MeshTransportDecision.Delivered(connection.incoming),
            result,
        )
        assertEquals(emptyList(), connection.sent)
    }

    @Test
    fun exposesDuplicateDropsFromTheSharedRouter() {
        val coordinator = MeshTransportCoordinator(MeshSession(local))
        val envelope = envelope(ttl = 2)

        runSuspend { coordinator.handleIncoming(envelope, atMillis = 13L) }
        val result = runSuspend { coordinator.handleIncoming(envelope, atMillis = 14L) }

        assertEquals(
            MeshTransportDecision.Dropped(
                messageId = envelope.id,
                reason = MessageDropReason.DUPLICATE,
            ),
            result,
        )
    }

    @Test
    fun aFailedPeerDoesNotPreventSendingToTheRemainingPeer() {
        val session = MeshSession(local)
        val coordinator = MeshTransportCoordinator(session)
        val failedConnection = FakeBlePeerConnection(
            sendFailure = BleSendException(LinkFailureReason.PEER_UNREACHABLE),
        )
        val healthyConnection = FakeBlePeerConnection()
        // "companion" is attempted before "relay" in deterministic peer order.
        coordinator.attach(companion, failedConnection)
        coordinator.attach(relay, healthyConnection)
        val envelope = envelope(ttl = 2)

        val result = runSuspend { coordinator.handleIncoming(envelope, atMillis = 15L) }

        assertEquals(
            MeshTransportDecision.Forwarded(
                envelope.copy(ttl = 1),
                peerIds = listOf(relay.id),
                failures = listOf(PeerSendFailure(companion.id, LinkFailureReason.PEER_UNREACHABLE)),
            ),
            result,
        )
        assertEquals(listOf(envelope.copy(ttl = 1)), healthyConnection.sent)
        assertEquals(
            listOf(
                MeshEvent.MessageSendFailed(envelope.id, companion.id, LinkFailureReason.PEER_UNREACHABLE, 15L),
                MeshEvent.MessageSendAccepted(envelope.id, relay.id, 15L),
            ),
            session.snapshot().history.takeLast(2),
        )
    }

    @Test
    fun reportsEveryFailureWhenNoSendWasAccepted() {
        val coordinator = MeshTransportCoordinator(MeshSession(local))
        coordinator.attach(relay, FakeBlePeerConnection(
            sendFailure = BleSendException(LinkFailureReason.CONNECTION_TIMEOUT),
        ))
        val envelope = envelope(ttl = 2)

        assertEquals(
            MeshTransportDecision.Forwarded(
                envelope.copy(ttl = 1),
                peerIds = emptyList(),
                failures = listOf(PeerSendFailure(relay.id, LinkFailureReason.CONNECTION_TIMEOUT)),
            ),
            runSuspend { coordinator.handleIncoming(envelope, atMillis = 16L) },
        )
    }

    @Test
    fun reportsNoRouteWhenOnlyTheSourceIsConnected() {
        val session = MeshSession(local)
        val coordinator = MeshTransportCoordinator(session)
        val connection = FakeBlePeerConnection()
        coordinator.attach(relay, connection)
        val envelope = envelope(ttl = 2)

        assertEquals(
            MeshTransportDecision.NoRoute(envelope.copy(ttl = 1)),
            runSuspend { coordinator.handleIncoming(envelope, fromPeer = relay.id, atMillis = 17L) },
        )
        assertEquals(emptyList(), connection.sent)
        assertEquals(MeshEvent.MessageNoRoute(envelope.id, 17L), session.snapshot().history.last())
    }

    @Test
    fun propagatesCancellationInsteadOfReportingARadioFailure() {
        val session = MeshSession(local)
        val coordinator = MeshTransportCoordinator(session)
        coordinator.attach(relay, FakeBlePeerConnection(
            sendFailure = kotlin.coroutines.cancellation.CancellationException("cancelled"),
        ))

        assertFailsWith<kotlin.coroutines.cancellation.CancellationException> {
            runSuspend { coordinator.handleIncoming(envelope(ttl = 2), atMillis = 18L) }
        }
        assertEquals(emptyList(), session.snapshot().history.filterIsInstance<MeshEvent.MessageSendFailed>())
    }

    private fun envelope(
        destination: NodeId = NodeId("remote"),
        ttl: Int,
    ): MeshEnvelope = MeshEnvelope(
        id = MessageId("message-1"),
        origin = NodeId("origin"),
        destination = destination,
        payload = "hello",
        createdAtMillis = 10L,
        ttl = ttl,
    )

    private class FakeBlePeerConnection(
        val sendFailure: Exception? = null,
        val incoming: MeshEnvelope = MeshEnvelope(
            id = MessageId("unset"),
            origin = NodeId("unset"),
            destination = NodeId("unset"),
            payload = "unset",
            createdAtMillis = 0L,
            ttl = 0,
        ),
    ) : BlePeerConnection {
        val sent: MutableList<MeshEnvelope> = mutableListOf()

        override suspend fun send(envelope: MeshEnvelope) {
            sendFailure?.let { throw it }
            sent += envelope
        }

        override suspend fun receive(): MeshEnvelope = incoming

        override suspend fun close() = Unit
    }

    private fun <T> runSuspend(block: suspend () -> T): T {
        var result: Result<T>? = null
        block.startCoroutine(
            object : Continuation<T> {
                override val context = EmptyCoroutineContext

                override fun resumeWith(value: Result<T>) {
                    result = value
                }
            },
        )
        return checkNotNull(result).getOrThrow()
    }
}
