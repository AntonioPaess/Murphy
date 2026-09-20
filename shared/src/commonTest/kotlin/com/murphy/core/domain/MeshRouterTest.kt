package com.murphy.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class MeshRouterTest {
    private val local = NodeId("local")
    private val remote = NodeId("remote")

    @Test
    fun forwardsAnEnvelopeAndDecrementsTtl() {
        val router = MeshRouter(local)
        val envelope = MeshEnvelope(
            id = MessageId("message-1"),
            origin = NodeId("origin"),
            destination = remote,
            payload = "hello",
            createdAtMillis = 10L,
            ttl = 3,
        )

        val decision = router.accept(envelope)

        assertEquals(ForwardDecision.Forward(envelope.copy(ttl = 2)), decision)
    }

    @Test
    fun doesNotForwardTheSameMessageTwice() {
        val router = MeshRouter(local)
        val envelope = MeshEnvelope(
            id = MessageId("message-1"),
            origin = NodeId("origin"),
            destination = remote,
            payload = "hello",
            createdAtMillis = 10L,
            ttl = 3,
        )

        router.accept(envelope)

        assertEquals(ForwardDecision.Duplicate, router.accept(envelope))
    }

    @Test
    fun deliversAnEnvelopeForTheLocalNodeEvenWhenTtlIsZero() {
        val router = MeshRouter(local)
        val envelope = MeshEnvelope(
            id = MessageId("message-1"),
            origin = NodeId("origin"),
            destination = local,
            payload = "hello",
            createdAtMillis = 10L,
            ttl = 0,
        )

        assertEquals(ForwardDecision.Deliver(envelope), router.accept(envelope))
    }
}

