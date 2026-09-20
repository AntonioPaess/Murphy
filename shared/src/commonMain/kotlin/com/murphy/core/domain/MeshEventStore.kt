package com.murphy.core.domain

public interface MeshEventStore {
    public fun append(event: MeshEvent)

    public fun all(): List<MeshEvent>

    public fun clear()
}

public class InMemoryMeshEventStore : MeshEventStore {
    private val events: MutableList<MeshEvent> = mutableListOf()

    override fun append(event: MeshEvent) {
        events += event
    }

    override fun all(): List<MeshEvent> = events.toList()

    override fun clear() {
        events.clear()
    }
}
