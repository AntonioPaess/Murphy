package com.murphy.core.domain

public class MeshRouter(private val localNode: NodeId, private val seenCapacity: Int = 4096) {
    init { require(seenCapacity > 0) }
    // A bounded session cache, not replay protection or durable deduplication.
    private val seenMessages: MutableSet<MessageId> = linkedSetOf()

    public fun accept(envelope: MeshEnvelope): ForwardDecision {
        if (!seenMessages.add(envelope.id)) {
            return ForwardDecision.Duplicate
        }
        if (seenMessages.size > seenCapacity) seenMessages.remove(seenMessages.first())

        if (envelope.destination.value == "*") {
            return ForwardDecision.Broadcast(envelope,
                if (envelope.ttl > 0) envelope.copy(ttl = envelope.ttl - 1) else null)
        }

        if (envelope.destination == localNode) {
            return ForwardDecision.Deliver(envelope)
        }

        if (envelope.ttl <= 0) {
            return ForwardDecision.Expired
        }

        return ForwardDecision.Forward(envelope.copy(ttl = envelope.ttl - 1))
    }

    public fun clearSeenMessages() {
        seenMessages.clear()
    }
}

public sealed interface ForwardDecision {
    /** Group broadcast is delivered here and optionally relayed. Native code enforces group scope. */
    public data class Broadcast(public val envelope: MeshEnvelope, public val relay: MeshEnvelope?) : ForwardDecision
    public data class Deliver(public val envelope: MeshEnvelope) : ForwardDecision

    public data class Forward(public val envelope: MeshEnvelope) : ForwardDecision

    public data object Duplicate : ForwardDecision

    public data object Expired : ForwardDecision
}

