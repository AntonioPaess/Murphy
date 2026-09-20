package com.murphy.core.domain

public data class RouteHop(
    public val from: NodeId,
    public val to: NodeId,
)

public data class MeshLink(
    public val first: NodeId,
    public val second: NodeId,
) {
    init {
        require(first != second) { "A mesh link needs two different nodes" }
    }

    public fun contains(node: NodeId): Boolean = first == node || second == node

    public fun other(node: NodeId): NodeId = when (node) {
        first -> second
        second -> first
        else -> error("Node $node is not part of this link")
    }
}

public class MeshTopology(nodes: Set<NodeId> = emptySet()) {
    private val onlineNodes: MutableSet<NodeId> = nodes.toMutableSet()
    private val links: MutableSet<MeshLink> = mutableSetOf()

    public fun addNode(node: NodeId) {
        onlineNodes += node
    }

    public fun setNodeOnline(node: NodeId, online: Boolean) {
        if (online) {
            onlineNodes += node
        } else {
            onlineNodes -= node
        }
    }

    public fun connect(first: NodeId, second: NodeId) {
        addNode(first)
        addNode(second)
        links += MeshLink(first, second)
    }

    public fun disconnect(first: NodeId, second: NodeId) {
        links -= MeshLink(first, second)
    }

    internal fun isOnline(node: NodeId): Boolean = node in onlineNodes

    internal fun neighbours(node: NodeId): List<NodeId> = links
        .asSequence()
        .filter { it.contains(node) }
        .map { it.other(node) }
        .filter(::isOnline)
        .sortedBy(NodeId::value)
        .toList()
}

public sealed interface DeliveryResult {
    public val route: List<RouteHop>

    public data class Delivered(
        public val envelope: MeshEnvelope,
        override val route: List<RouteHop>,
    ) : DeliveryResult

    public data class Unreachable(
        override val route: List<RouteHop>,
    ) : DeliveryResult

    public data class Expired(
        override val route: List<RouteHop>,
    ) : DeliveryResult
}

public class MeshTopologySimulator(private val topology: MeshTopology) {
    public fun deliver(envelope: MeshEnvelope): DeliveryResult {
        if (!topology.isOnline(envelope.origin) || !topology.isOnline(envelope.destination)) {
            return DeliveryResult.Unreachable(emptyList())
        }

        if (envelope.origin == envelope.destination) {
            return DeliveryResult.Delivered(envelope, emptyList())
        }

        val paths: MutableList<List<NodeId>> = mutableListOf(listOf(envelope.origin))
        val visited: MutableSet<NodeId> = mutableSetOf(envelope.origin)

        while (paths.isNotEmpty()) {
            val path = paths.removeFirst()
            val current = path.last()
            val hopsUsed = path.size - 1

            if (hopsUsed >= envelope.ttl) {
                continue
            }

            for (neighbour in topology.neighbours(current)) {
                if (neighbour in visited) {
                    continue
                }

                val nextPath = path + neighbour
                val route = nextPath.zipWithNext(::RouteHop)
                if (neighbour == envelope.destination) {
                    return DeliveryResult.Delivered(envelope, route)
                }

                visited += neighbour
                paths += nextPath
            }
        }

        return if (envelope.ttl <= 0) {
            DeliveryResult.Expired(emptyList())
        } else {
            DeliveryResult.Unreachable(emptyList())
        }
    }
}

