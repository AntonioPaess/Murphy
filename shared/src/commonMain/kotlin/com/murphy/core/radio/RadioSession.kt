package com.murphy.core.radio

import com.murphy.core.domain.*

/** Real radio callbacks feed this state; it never fabricates peers or receipt acknowledgements.
 * Both native owners must serialize calls on their main queue. Storage is still in-memory.
 */
public class RadioSession(public val nodeId: String) {
    private var group: RadioGroup? = null
    private var name: String = ""
    private var radioStatus: String = "Bluetooth not started"
    private var notice: String? = null
    private var mesh: MeshSession = MeshSession(NodeId(nodeId), BoundedEvents())
    private val peers: MutableMap<String, RadioPeer> = linkedMapOf()
    private val groups: MutableMap<String, RadioGroup> = linkedMapOf()
    private val messages: MutableList<RadioMessage> = mutableListOf()
    private val events: MutableList<RadioActivity> = mutableListOf()
    private var nextEvent: Int = 1

    public fun snapshot(): RadioSnapshot = RadioSnapshot(group, name, radioStatus, notice,
        peers.values.sortedBy { it.name }, groups.values.sortedBy { it.name }, messages.toList(), events.toList())

    public fun start(groupId: String, groupName: String, name: String): Boolean {
        val identity = RadioPacket(0, nodeId, name.trim(), groupName.trim(), groupId)
        if (runCatching { RadioProtocol.encode(identity) }.isFailure) {
            notice = "Enter valid names (1–40 characters) and select a valid group."
            return false
        }
        leave()
        group = RadioGroup(groupId, groupName.trim())
        this.name = name.trim()
        notice = null
        activity("Group opened", "Bluetooth must be enabled and permitted. This group is not encrypted.")
        return true
    }

    public fun leave() {
        peers.clear(); messages.clear(); group = null; name = ""; notice = null
        mesh = MeshSession(NodeId(nodeId), BoundedEvents())
    }

    public fun status(value: String) { radioStatus = value }

    public fun identityBytes(): ByteArray? = group?.let {
        RadioProtocol.encode(RadioPacket(0, nodeId, name, it.name, it.id))
    }

    public fun observe(identity: RadioPacket) {
        if (identity.kind != 0 || identity.nodeId == nodeId) return
        groups[identity.groupId] = RadioGroup(identity.groupId, identity.groupName)
        if (groups.size > 32) groups.remove(groups.keys.first())
    }

    public fun accepts(identity: RadioPacket): Boolean = identity.kind == 0 &&
        identity.nodeId != nodeId && identity.groupId == group?.id &&
        (identity.nodeId in peers || peers.size < 8)

    public fun attach(identity: RadioPacket, atMillis: Long): Boolean {
        observe(identity)
        if (!accepts(identity)) return false
        val isNew = identity.nodeId !in peers
        peers[identity.nodeId] = RadioPeer(identity.nodeId, identity.name)
        if (isNew) {
            val peer = Peer(NodeId(identity.nodeId), identity.name)
            mesh.discover(peer, atMillis); mesh.markConnected(peer, atMillis)
            activity("Peer connected", "${identity.name} · Bluetooth link. Identity is not authenticated yet.")
        }
        return true
    }

    public fun detach(peerId: String, atMillis: Long) {
        val peer = peers.remove(peerId) ?: return
        mesh.markDisconnected(Peer(NodeId(peer.id), peer.name), LinkFailureReason.PEER_UNREACHABLE, atMillis)
        activity("Peer disconnected", peer.name)
    }

    public fun disconnected(atMillis: Long) { peers.keys.toList().forEach { detach(it, atMillis) } }

    public fun send(body: String, messageId: String, atMillis: Long): List<RadioSend> {
        val current = group ?: return emptyList()
        val packet = RadioPacket(1, nodeId, name, current.name, current.id, messageId, "*", body.trim(), 4, atMillis)
        if (runCatching { RadioProtocol.encode(packet) }.isFailure) {
            notice = "Write a message between 1 and 1,000 characters."
            return emptyList()
        }
        notice = null
        if (peers.isEmpty()) {
            addMessage(RadioMessage(messageId, nodeId, name, body.trim(), true, "Not sent · no connected peers"))
            mesh.recordNoRoute(MessageId(messageId), atMillis)
            activity("Message not sent", "No Bluetooth peer is connected. This message is only in memory.")
            return emptyList()
        }
        addMessage(RadioMessage(messageId, nodeId, name, body.trim(), true, "Sending · receipt unconfirmed"))
        val decision = mesh.handleIncoming(envelope(packet), atMillis) as ForwardDecision.Broadcast
        val forwarded = packet.copy(ttl = decision.relay!!.ttl)
        activity("Message queued for Bluetooth", "Destination acknowledgement is not implemented yet.")
        return outgoing(forwarded, null)
    }

    public fun receive(bytes: ByteArray, fromPeer: String, atMillis: Long): List<RadioSend> {
        val packet = RadioProtocol.decode(bytes) ?: return emptyList()
        if (packet.kind != 1 || fromPeer !in peers || packet.groupId != group?.id) return emptyList()
        val decision = mesh.handleIncoming(envelope(packet), atMillis)
        val delivered = decision is ForwardDecision.Deliver || decision is ForwardDecision.Broadcast
        if (delivered && packet.nodeId != nodeId) {
            addMessage(RadioMessage(packet.messageId, packet.nodeId, packet.name, packet.body, false, "Received on this device"))
            activity("Message received", "${packet.name} · received over Bluetooth, not authenticated yet.")
        }
        val relay = when (decision) {
            is ForwardDecision.Broadcast -> decision.relay
            is ForwardDecision.Forward -> decision.envelope
            else -> null
        } ?: return emptyList()
        return outgoing(packet.copy(ttl = relay.ttl), fromPeer)
    }

    public fun sendResult(messageId: String, peerId: String, accepted: Boolean, atMillis: Long) {
        if (accepted) mesh.recordSendAccepted(MessageId(messageId), NodeId(peerId), atMillis)
        else mesh.recordSendFailed(MessageId(messageId), NodeId(peerId), LinkFailureReason.PEER_UNREACHABLE, atMillis)
        val index = messages.indexOfFirst { it.id == messageId && it.own }
        if (index >= 0) {
            val old = messages[index]
            val status = when {
                !accepted -> "Link failed · receipt unconfirmed"
                old.status.startsWith("Link failed") -> old.status
                else -> "Bluetooth accepted · receipt unconfirmed"
            }
            messages[index] = old.copy(status = status)
        }
        activity(if (accepted) "Bluetooth write accepted" else "Bluetooth write failed",
            "Local transport result only. Not a destination acknowledgement.")
    }

    private fun outgoing(packet: RadioPacket, excluded: String?): List<RadioSend> {
        val bytes = RadioProtocol.encode(packet)
        return peers.keys.filter { it != excluded }.sorted().map { RadioSend(it, packet.messageId, bytes) }
    }

    private fun envelope(packet: RadioPacket): MeshEnvelope = MeshEnvelope(MessageId(packet.messageId),
        NodeId(packet.nodeId), NodeId(packet.destination), packet.body, packet.createdAtMillis, packet.ttl)

    private fun addMessage(message: RadioMessage) { messages.add(message); if (messages.size > 100) messages.removeAt(0) }
    private fun activity(title: String, detail: String) {
        events.add(RadioActivity(nextEvent++, title, detail)); if (events.size > 100) events.removeAt(0)
    }

    private class BoundedEvents : MeshEventStore {
        private val events = ArrayDeque<MeshEvent>()
        override fun append(event: MeshEvent) { events.addLast(event); if (events.size > 256) events.removeFirst() }
        override fun all(): List<MeshEvent> = events.toList()
        override fun clear() { events.clear() }
    }
}

public data class RadioGroup(public val id: String, public val name: String)
public data class RadioPeer(public val id: String, public val name: String)
public data class RadioMessage(public val id: String, public val senderId: String, public val sender: String,
    public val body: String, public val own: Boolean, public val status: String)
public data class RadioActivity(public val id: Int, public val title: String, public val detail: String)
public data class RadioSend(public val peerId: String, public val messageId: String, public val packet: ByteArray)
public data class RadioSnapshot(public val group: RadioGroup?, public val displayName: String,
    public val radioStatus: String, public val notice: String?, public val peers: List<RadioPeer>,
    public val nearbyGroups: List<RadioGroup>, public val messages: List<RadioMessage>, public val events: List<RadioActivity>)
