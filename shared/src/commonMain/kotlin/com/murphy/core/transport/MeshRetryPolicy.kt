package com.murphy.core.transport

import com.murphy.core.domain.MeshEnvelope
import com.murphy.core.domain.LinkFailureReason
import com.murphy.core.domain.MessageId
import com.murphy.core.domain.NodeId

/** Limits apply per message and peer, including the original send attempt. */
public data class MeshRetryPolicy(
    public val maxAttempts: Int = 3,
    public val delayMillis: Long = 1_000L,
    public val capacity: Int = 128,
) {
    init {
        require(maxAttempts >= 1) { "At least one send attempt is required" }
        require(delayMillis > 0) { "Retry delay must be positive" }
        require(capacity > 0) { "Retry capacity must be positive" }
    }
}

public data class PendingMeshSend(
    public val envelope: MeshEnvelope,
    public val peerId: NodeId,
    public val attempts: Int,
    public val nextAttemptAtMillis: Long,
)

public data class RetrySendAccepted(
    public val messageId: MessageId,
    public val peerId: NodeId,
)

public data class RetrySendFailure(
    public val messageId: MessageId,
    public val peerId: NodeId,
    public val reason: LinkFailureReason,
)

public data class MeshRetryResult(
    public val accepted: List<RetrySendAccepted>,
    public val failures: List<RetrySendFailure>,
    public val exhausted: List<PendingMeshSend>,
)
