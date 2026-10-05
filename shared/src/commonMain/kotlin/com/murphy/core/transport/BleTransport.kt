package com.murphy.core.transport

import com.murphy.core.domain.MeshEnvelope
import com.murphy.core.domain.Peer
import com.murphy.core.domain.LinkFailureReason

/**
 * Platform-neutral contract implemented by Android Bluetooth and iOS CoreBluetooth adapters.
 * The shared module owns protocol and state decisions; platform code owns radio details.
 */
public interface BleTransport {
    public suspend fun scan(): List<Peer>

    public suspend fun connect(peer: Peer): BlePeerConnection
}

public interface BlePeerConnection {
    /** Successful return means the adapter accepted the send, not end-to-end delivery.
     * Expected radio failures must use BleSendException; cancellation must propagate.
     */
    public suspend fun send(envelope: MeshEnvelope)

    public suspend fun receive(): MeshEnvelope

    public suspend fun close()
}

public class BleSendException(
    public val reason: LinkFailureReason,
    cause: Throwable? = null,
) : Exception("BLE send failed: $reason", cause)
