package com.murphy.core.transport

import com.murphy.core.domain.MeshEnvelope
import com.murphy.core.domain.Peer

/**
 * Platform-neutral contract implemented by Android Bluetooth and iOS CoreBluetooth adapters.
 * The shared module owns protocol and state decisions; platform code owns radio details.
 */
public interface BleTransport {
    public suspend fun scan(): List<Peer>

    public suspend fun connect(peer: Peer): BlePeerConnection
}

public interface BlePeerConnection {
    public suspend fun send(envelope: MeshEnvelope)

    public suspend fun receive(): MeshEnvelope

    public suspend fun close()
}

