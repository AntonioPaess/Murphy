package com.murphy.core.domain

public class MeshRouter(private val localNode: NodeId) {
    private val seenMessages: MutableSet<MessageId> = mutableSetOf()

    public fun accept(envelope: MeshEnvelope): ForwardDecision {
        if (!seenMessages.add(envelope.id)) {
            return ForwardDecision.Duplicate
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
    public data class Deliver(public val envelope: MeshEnvelope) : ForwardDecision

    public data class Forward(public val envelope: MeshEnvelope) : ForwardDecision

    public data object Duplicate : ForwardDecision

    public data object Expired : ForwardDecision
}

