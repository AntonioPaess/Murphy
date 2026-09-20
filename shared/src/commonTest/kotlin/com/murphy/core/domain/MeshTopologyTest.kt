package com.murphy.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MeshTopologyTest {
    private val phoneA = NodeId("phone-a")
    private val relay = NodeId("relay")
    private val phoneB = NodeId("phone-b")

    @Test
    fun routesThroughAnIntermediatePeer() {
        val topology = MeshTopology(setOf(phoneA, relay, phoneB))
        topology.connect(phoneA, relay)
        topology.connect(relay, phoneB)

        val envelope = envelope(ttl = 2)
        val result = MeshTopologySimulator(topology).deliver(envelope)

        val delivered = assertIs<DeliveryResult.Delivered>(result)
        assertEquals(
            listOf(
                RouteHop(phoneA, relay),
                RouteHop(relay, phoneB),
            ),
            delivered.route,
        )
    }

    @Test
    fun reportsTheDestinationAsUnreachableAfterTheRelayGoesOffline() {
        val topology = MeshTopology(setOf(phoneA, relay, phoneB))
        topology.connect(phoneA, relay)
        topology.connect(relay, phoneB)
        topology.setNodeOnline(relay, online = false)

        val result = MeshTopologySimulator(topology).deliver(envelope(ttl = 2))

        assertIs<DeliveryResult.Unreachable>(result)
    }

    @Test
    fun refusesToExceedTheEnvelopeTtl() {
        val topology = MeshTopology(setOf(phoneA, relay, phoneB))
        topology.connect(phoneA, relay)
        topology.connect(relay, phoneB)

        val result = MeshTopologySimulator(topology).deliver(envelope(ttl = 1))

        assertIs<DeliveryResult.Unreachable>(result)
    }

    private fun envelope(ttl: Int): MeshEnvelope = MeshEnvelope(
        id = MessageId("sos-1"),
        origin = phoneA,
        destination = phoneB,
        payload = "I am safe",
        createdAtMillis = 100L,
        ttl = ttl,
    )
}

