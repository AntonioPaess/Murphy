package com.murphy.core.transport

import com.murphy.core.domain.MeshEnvelope
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
