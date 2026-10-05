package com.murphy.core.transport

import com.murphy.core.domain.LinkFailureReason
import com.murphy.core.domain.MeshEnvelope
import com.murphy.core.domain.MeshEvent
import com.murphy.core.domain.MeshSession
import com.murphy.core.domain.MessageId
import com.murphy.core.domain.NodeId
import com.murphy.core.domain.Peer
import com.murphy.core.domain.RetryStopReason
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MeshRetryTest {
    private val local = NodeId("sender")
    private val destination = Peer(NodeId("destination"), "Destination")
    private val envelope = MeshEnvelope(
        MessageId("sos"), local, destination.id, "Need help", 0L, 2,
    )

    @Test
    fun retriesOnlyAFailedPeerAfterTheDeadlineWithoutReducingTtlAgain() {
        val coordinator = MeshTransportCoordinator(MeshSession(local))
        val failed = FakeConnection(failure = BleSendException(LinkFailureReason.PEER_UNREACHABLE))
        val healthy = FakeConnection()
        coordinator.attach(destination, failed)
        coordinator.attach(Peer(NodeId("healthy"), "Healthy"), healthy)
        runSuspend { coordinator.handleIncoming(envelope, atMillis = 10L) }

        // Replaying incoming traffic remains deduplicated; retry has its own path.
        assertIs<MeshTransportDecision.Dropped>(runSuspend {
            coordinator.handleIncoming(envelope, atMillis = 11L)
        })
        assertTrue(runSuspend { coordinator.retryPending(1_009L) }.accepted.isEmpty())
        assertEquals(1, failed.attempts)

        failed.failure = null
        val result = runSuspend { coordinator.retryPending(1_010L) }
        assertEquals(listOf(RetrySendAccepted(envelope.id, destination.id)), result.accepted)
        assertEquals(listOf(envelope.copy(ttl = 1)), failed.sent)
        assertEquals(1, healthy.attempts)
        assertTrue(coordinator.pendingSends().isEmpty())
    }

    @Test
    fun stopsAfterTheConfiguredTotalAttemptBudget() {
        val session = MeshSession(local)
        val coordinator = MeshTransportCoordinator(session, MeshRetryPolicy(maxAttempts = 2))
        val failed = FakeConnection(failure = BleSendException(LinkFailureReason.CONNECTION_TIMEOUT))
        coordinator.attach(destination, failed)
        runSuspend { coordinator.handleIncoming(envelope, atMillis = 0L) }

        val result = runSuspend { coordinator.retryPending(1_000L) }
        assertEquals(2, result.exhausted.single().attempts)
        assertEquals(
            listOf(RetrySendFailure(envelope.id, destination.id, LinkFailureReason.CONNECTION_TIMEOUT)),
            result.failures,
        )
        assertTrue(coordinator.pendingSends().isEmpty())
        runSuspend { coordinator.retryPending(10_000L) }
        assertEquals(2, failed.attempts)
        assertEquals(
            MeshEvent.MessageRetryStopped(envelope.id, destination.id, RetryStopReason.ATTEMPTS_EXHAUSTED, 1_000L),
            session.snapshot().history.last(),
        )
    }

    @Test
    fun resumesAfterReconnectWithoutSpendingAttemptsWhileDetached() {
        val coordinator = MeshTransportCoordinator(MeshSession(local))
        coordinator.attach(destination, FakeConnection(failure = BleSendException(LinkFailureReason.PEER_UNREACHABLE)))
        runSuspend { coordinator.handleIncoming(envelope, atMillis = 0L) }
        coordinator.detach(destination.id)
        runSuspend { coordinator.retryPending(1_000L) }
        assertEquals(1, coordinator.pendingSends().single().attempts)

        val recovered = FakeConnection()
        coordinator.attach(destination, recovered)
        runSuspend { coordinator.retryPending(2_000L) }
        assertEquals(listOf(envelope.copy(ttl = 1)), recovered.sent)
        assertTrue(coordinator.pendingSends().isEmpty())
    }

    @Test
    fun preservesAPendingSendWhenRetryIsCancelled() {
        val coordinator = MeshTransportCoordinator(MeshSession(local))
        val connection = FakeConnection(failure = BleSendException(LinkFailureReason.PEER_UNREACHABLE))
        coordinator.attach(destination, connection)
        runSuspend { coordinator.handleIncoming(envelope, atMillis = 0L) }
        val pendingBefore = coordinator.pendingSends()
        connection.failure = CancellationException("Cancelled")

        assertFailsWith<CancellationException> {
            runSuspend { coordinator.retryPending(1_000L) }
        }
        assertEquals(pendingBefore, coordinator.pendingSends())
        connection.failure = null
        runSuspend { coordinator.retryPending(1_000L) }
        assertEquals(listOf(envelope.copy(ttl = 1)), connection.sent)
    }

    @Test
    fun rejectsOverflowWithoutEvictingAnExistingPendingMessage() {
        val session = MeshSession(local)
        val coordinator = MeshTransportCoordinator(session, MeshRetryPolicy(capacity = 1))
        coordinator.attach(destination, FakeConnection(failure = BleSendException(LinkFailureReason.PEER_UNREACHABLE)))
        runSuspend { coordinator.handleIncoming(envelope, atMillis = 0L) }
        val second = envelope.copy(id = MessageId("second"))
        runSuspend { coordinator.handleIncoming(second, atMillis = 1L) }

        assertEquals(envelope.id, coordinator.pendingSends().single().envelope.id)
        assertEquals(
            MeshEvent.MessageRetryStopped(second.id, destination.id, RetryStopReason.QUEUE_FULL, 1L),
            session.snapshot().history.last(),
        )
    }

    @Test
    fun canDisableRetriesAndRejectsInvalidPolicyValues() {
        assertFailsWith<IllegalArgumentException> { MeshRetryPolicy(maxAttempts = 0) }
        assertFailsWith<IllegalArgumentException> { MeshRetryPolicy(delayMillis = 0) }
        assertFailsWith<IllegalArgumentException> { MeshRetryPolicy(capacity = 0) }
        val coordinator = MeshTransportCoordinator(MeshSession(local), MeshRetryPolicy(maxAttempts = 1))
        coordinator.attach(destination, FakeConnection(failure = BleSendException(LinkFailureReason.PEER_UNREACHABLE)))
        runSuspend { coordinator.handleIncoming(envelope, atMillis = 0L) }
        assertTrue(coordinator.pendingSends().isEmpty())
    }

    @Test
    fun threeNodesDeliverOnceAfterTheRelayReconnectsToTheDestination() {
        val relay = Peer(NodeId("relay"), "Relay")
        val receiverSession = MeshSession(destination.id)
        val receiver = MeshTransportCoordinator(receiverSession)
        val relayCoordinator = MeshTransportCoordinator(MeshSession(relay.id))
        val relayToDestination = FakeConnection(
            failure = BleSendException(LinkFailureReason.PEER_UNREACHABLE),
            onSend = { receiver.handleIncoming(it, fromPeer = relay.id, atMillis = 1_000L) },
        )
        relayCoordinator.attach(destination, relayToDestination)
        val sender = MeshTransportCoordinator(MeshSession(local))
        sender.attach(relay, FakeConnection(onSend = {
            relayCoordinator.handleIncoming(it, fromPeer = local, atMillis = 0L)
        }))

        runSuspend { sender.handleIncoming(envelope, atMillis = 0L) }
        assertTrue(receiverSession.snapshot().history.isEmpty())
        assertEquals(0, relayCoordinator.pendingSends().single().envelope.ttl)

        relayToDestination.failure = null
        runSuspend { relayCoordinator.retryPending(1_000L) }
        assertEquals(1, receiverSession.snapshot().history.filterIsInstance<MeshEvent.MessageDelivered>().size)
        // An ambiguous radio failure can result in a repeated packet: delivery is still once per session.
        assertIs<MeshTransportDecision.Dropped>(runSuspend {
            receiver.handleIncoming(envelope.copy(ttl = 0), fromPeer = relay.id, atMillis = 1_001L)
        })
        assertEquals(1, receiverSession.snapshot().history.filterIsInstance<MeshEvent.MessageDelivered>().size)
        assertTrue(relayCoordinator.pendingSends().isEmpty())
    }

    private class FakeConnection(
        var failure: Exception? = null,
        val onSend: suspend (MeshEnvelope) -> Unit = {},
    ) : BlePeerConnection {
        var attempts = 0
        val sent = mutableListOf<MeshEnvelope>()
        override suspend fun send(envelope: MeshEnvelope) {
            attempts++
            failure?.let { throw it }
            sent += envelope
            onSend(envelope)
        }
        override suspend fun receive(): MeshEnvelope = error("Receive is not used here")
        override suspend fun close() = Unit
    }

    private fun <T> runSuspend(block: suspend () -> T): T {
        var result: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(resultValue: Result<T>) { result = resultValue }
        })
        return checkNotNull(result) { "Fake connection unexpectedly suspended" }.getOrThrow()
    }
}
