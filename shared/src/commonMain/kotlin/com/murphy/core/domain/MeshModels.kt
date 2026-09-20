package com.murphy.core.domain

@JvmInline
public value class NodeId(public val value: String)

@JvmInline
public value class MessageId(public val value: String)

public data class Peer(
    public val id: NodeId,
    public val displayName: String,
    public val advertisedServices: Set<String> = emptySet(),
)

public enum class LinkFailureReason {
    RADIO_UNAVAILABLE,
    PEER_UNREACHABLE,
    CONNECTION_TIMEOUT,
    UNKNOWN,
}

public sealed interface LinkState {
    public data object Disconnected : LinkState

    public data object Scanning : LinkState

    public data class Connecting(public val peer: Peer) : LinkState

    public data class Connected(
        public val peer: Peer,
        public val connectedAtMillis: Long,
    ) : LinkState

    public data class Reconnecting(
        public val peer: Peer,
        public val attempt: Int,
        public val reason: LinkFailureReason,
    ) : LinkState

    public data class Failed(public val reason: LinkFailureReason) : LinkState
}

public data class MeshEnvelope(
    public val id: MessageId,
    public val origin: NodeId,
    public val destination: NodeId,
    public val payload: String,
    public val createdAtMillis: Long,
    public val ttl: Int,
)

public sealed interface MeshEvent {
    public val atMillis: Long

    public data class ScanStarted(override val atMillis: Long) : MeshEvent

    public data class PeerDiscovered(
        public val peer: Peer,
        override val atMillis: Long,
    ) : MeshEvent

    public data class ScanFinished(
        public val peerCount: Int,
        override val atMillis: Long,
    ) : MeshEvent

    public data class ConnectionStarted(
        public val peer: Peer,
        override val atMillis: Long,
    ) : MeshEvent

    public data class ConnectionEstablished(
        public val peer: Peer,
        override val atMillis: Long,
    ) : MeshEvent

    public data class ConnectionLost(
        public val peer: Peer,
        public val reason: LinkFailureReason,
        override val atMillis: Long,
    ) : MeshEvent

    public data class ConnectionFailed(
        public val reason: LinkFailureReason,
        override val atMillis: Long,
    ) : MeshEvent
}

public data class MeshSnapshot(
    public val state: LinkState,
    public val peers: List<Peer>,
    public val history: List<MeshEvent>,
)

