package com.murphy.core.radio

import kotlin.test.*

class RadioSessionTest {
    private val a = "00000000-0000-0000-0000-000000000001"
    private val b = "00000000-0000-0000-0000-000000000002"
    private val c = "00000000-0000-0000-0000-000000000003"
    private val group = "00000000-0000-0000-0000-000000000004"
    private val message = "00000000-0000-0000-0000-000000000005"
    private fun session(id: String) = RadioSession(id).also { assertTrue(it.start(group, "Trail", id.takeLast(1))) }
    private fun identity(session: RadioSession) = RadioProtocol.decode(session.identityBytes()!!)!!

    @Test fun onlyCountsPeersAfterNativeHandshakeAndRejectsSameNamedForeignGroup() {
        val first = session(a); val second = session(b)
        first.observe(identity(second))
        assertTrue(first.snapshot().peers.isEmpty())
        assertEquals("Trail", first.snapshot().nearbyGroups.single().name)
        assertFalse(first.attach(identity(first), 0))
        assertFalse(first.attach(identity(second).copy(groupId = c), 0))
        assertTrue(first.attach(identity(second), 0))
        assertEquals(1, first.snapshot().peers.size)
        first.detach(b, 1)
        assertTrue(first.snapshot().peers.isEmpty())
    }

    @Test fun routesGroupBroadcastAcrossThreeNodesOnceAndExcludesIncomingLink() {
        val first = session(a); val relay = session(b); val last = session(c)
        first.attach(identity(relay), 0); relay.attach(identity(first), 0)
        relay.attach(identity(last), 0); last.attach(identity(relay), 0)
        val sent = first.send("Hello trail", message, 1).single()
        assertEquals(b, sent.peerId)
        assertEquals(3, RadioProtocol.decode(sent.packet)!!.ttl)
        val forwarded = relay.receive(sent.packet, a, 2).single()
        assertEquals(c, forwarded.peerId)
        assertEquals(2, RadioProtocol.decode(forwarded.packet)!!.ttl)
        assertTrue(last.receive(forwarded.packet, b, 3).isEmpty())
        assertTrue(relay.receive(sent.packet, a, 4).isEmpty())
        assertEquals("Hello trail", relay.snapshot().messages.single().body)
        assertEquals("Hello trail", last.snapshot().messages.single().body)
        assertFalse(last.snapshot().messages.single().own)
    }

    @Test fun rejectsUnattachedSourcesForeignGroupsAndMalformedPackets() {
        val first = session(a); val second = session(b)
        first.attach(identity(second), 0); second.attach(identity(first), 0)
        val sent = second.send("Hello", message, 1).single()
        assertTrue(first.receive(sent.packet, c, 2).isEmpty())
        val packet = RadioProtocol.decode(sent.packet)!!.copy(groupId = c)
        assertTrue(first.receive(RadioProtocol.encode(packet), b, 3).isEmpty())
        assertTrue(first.receive(byteArrayOf(1), b, 4).isEmpty())
        assertTrue(first.snapshot().messages.isEmpty())
    }

    @Test fun transportAcceptanceNeverBecomesDestinationAcknowledgement() {
        val first = session(a); val second = session(b)
        first.attach(identity(second), 0)
        first.send("Hello", message, 1)
        first.sendResult(message, b, true, 2)
        assertEquals("Bluetooth accepted · receipt unconfirmed", first.snapshot().messages.single().status)
        first.sendResult(message, b, false, 3)
        assertEquals("Link failed · receipt unconfirmed", first.snapshot().messages.single().status)
        first.sendResult(message, b, true, 4)
        assertTrue(first.snapshot().messages.single().status.startsWith("Link failed"))
    }

    @Test fun noPeerDoesNotFabricateTransmissionAndLeaveClearsLocalGroupState() {
        val first = session(a)
        assertTrue(first.send("Hello", message, 0).isEmpty())
        assertEquals("Not sent · no connected peers", first.snapshot().messages.single().status)
        first.leave()
        assertNull(first.snapshot().group)
        assertNull(first.identityBytes())
        assertTrue(first.snapshot().messages.isEmpty())
        assertFalse(first.start(group, "", "Alex"))
    }

    @Test fun hopBoundaryDeliversButDoesNotRelay() {
        val first = session(a); val second = session(b)
        first.attach(identity(second), 0); second.attach(identity(first), 0)
        val packet = RadioProtocol.decode(first.send("Hello", message, 1).single().packet)!!.copy(ttl = 0)
        assertTrue(second.receive(RadioProtocol.encode(packet), a, 2).isEmpty())
        assertEquals(1, second.snapshot().messages.size)
    }
}
