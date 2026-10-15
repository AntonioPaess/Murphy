package com.murphy.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class MeshRouterTest {
    private val local = NodeId("local")
    private val remote = NodeId("remote")

    @Test fun boundsDeduplicationCacheAndDeliversBroadcastAtHopBoundary() {
        val router = MeshRouter(local, seenCapacity = 2)
        val first = MeshEnvelope(MessageId("1"), remote, NodeId("*"), "hello", 0, 0)
        assertEquals(ForwardDecision.Broadcast(first, null), router.accept(first))
        router.accept(first.copy(id = MessageId("2")))
        assertEquals(ForwardDecision.Duplicate, router.accept(first))
        router.accept(first.copy(id = MessageId("3")))
        assertEquals(ForwardDecision.Broadcast(first, null), router.accept(first))
    }

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

