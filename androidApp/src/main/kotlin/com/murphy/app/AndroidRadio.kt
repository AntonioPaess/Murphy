@file:Suppress("MissingPermission", "DEPRECATION")
package com.murphy.app

import android.bluetooth.*
import android.bluetooth.le.*
import android.content.*
import android.os.*
import androidx.core.content.ContextCompat
import com.murphy.core.radio.*
import java.util.UUID

/** Foreground-only GATT central + peripheral. Permission approval is owned by the Activity. */
class AndroidRadio(private val context: Context, private val session: RadioSession,
    private val changed: () -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private val manager = context.getSystemService(BluetoothManager::class.java)
    private val adapter get() = manager?.adapter
    private val serviceId = UUID.fromString(RadioProtocol.SERVICE_UUID)
    private val identityId = UUID.fromString(RadioProtocol.IDENTITY_UUID)
    private val writeId = UUID.fromString(RadioProtocol.WRITE_UUID)
    private val notifyId = UUID.fromString(RadioProtocol.NOTIFY_UUID)
    private val cccd = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    private val clients = linkedMapOf<String, Client>()
    private val servers = linkedMapOf<String, Server>()
    private val seen = mutableSetOf<String>()
    private var gattServer: BluetoothGattServer? = null
    private var tx: BluetoothGattCharacteristic? = null
    private var notifying: Server? = null
    private var lastNotified: String? = null
    private var active = false
    private var scanning = false
    private var advertising = false
    private var registered = false
    private var generation = 0

    private class Transfer(val frames: ArrayDeque<ByteArray>, val done: (Boolean) -> Unit)
    private class Client(val device: BluetoothDevice) {
        var gatt: BluetoothGatt? = null; var identity: RadioPacket? = null; var ready = false
        var write: BluetoothGattCharacteristic? = null; var notify: BluetoothGattCharacteristic? = null
        val assembler = RadioAssembler(); val queue = ArrayDeque<Transfer>()
    }
    private class Server(val device: BluetoothDevice) {
        var identity: RadioPacket? = null; var subscribed = false; var ready = false
        val assembler = RadioAssembler(); val queue = ArrayDeque<Transfer>()
    }

    private fun now() = SystemClock.elapsedRealtime()
    private fun dispatch(block: () -> Unit) { if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block) }
    private fun status(text: String) { session.status(text); changed() }

    fun start() {
        if (active) { scan(); return }
        try {
            val radio = adapter
            if (radio == null) { status("Bluetooth is unavailable on this device"); return }
            if (!radio.isEnabled) { status("Bluetooth is off · enable it in Settings"); return }
            active = true; generation++
            if (!registered) {
                ContextCompat.registerReceiver(context, receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED)
                registered = true
            }
            if (session.identityBytes() != null) {
                if (radio.bluetoothLeAdvertiser == null) { status("This device cannot advertise BLE groups"); stop(); return }
                gattServer = manager?.openGattServer(context, serverCallback(generation))
                val service = BluetoothGattService(serviceId, BluetoothGattService.SERVICE_TYPE_PRIMARY)
                service.addCharacteristic(BluetoothGattCharacteristic(identityId, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ))
                service.addCharacteristic(BluetoothGattCharacteristic(writeId, BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattCharacteristic.PERMISSION_WRITE))
                tx = BluetoothGattCharacteristic(notifyId, BluetoothGattCharacteristic.PROPERTY_NOTIFY, BluetoothGattCharacteristic.PERMISSION_READ).also {
                    it.addDescriptor(BluetoothGattDescriptor(cccd, BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE))
                    service.addCharacteristic(it)
                }
                if (gattServer?.addService(service) != true) status("Could not publish the Bluetooth service")
            }
            scan()
        } catch (_: SecurityException) { status("Nearby devices permission is required"); stop() }
    }

    fun stop() {
        active = false; generation++
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        scanning = false; advertising = false; notifying = null; lastNotified = null
        runCatching { adapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback) }
        clients.values.toList().forEach { link -> fail(link.queue); runCatching { link.gatt?.disconnect(); link.gatt?.close() } }
        servers.values.toList().forEach { fail(it.queue) }
        clients.clear(); servers.clear(); seen.clear()
        runCatching { gattServer?.close() }; gattServer = null; tx = null
        if (registered) { runCatching { context.unregisterReceiver(receiver) }; registered = false }
        session.disconnected(now()); changed()
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) != BluetoothAdapter.STATE_ON) {
                stop(); status("Bluetooth is off · links disconnected")
            }
        }
    }

    private fun scan() {
        if (!active || scanning) return
        seen.clear(); scanning = true
        val current = generation
        try {
            adapter?.bluetoothLeScanner?.startScan(listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(serviceId)).build()),
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build(), scanCallback)
            status("Looking for nearby groups and peers")
            main.postDelayed({
                if (generation == current && scanning) {
                    runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }; scanning = false
                    status(if (session.snapshot().group == null) "Nearby group scan finished" else
                        if (advertising) "Group discoverable · scan finished" else "Peer scan finished · advertising unavailable")
                }
            }, 10_000)
        } catch (_: SecurityException) { scanning = false; status("Nearby devices permission is required") }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanFailed(errorCode: Int) = dispatch { if (active) { scanning = false; status("Bluetooth scan failed ($errorCode) · try again") } }
        override fun onScanResult(callbackType: Int, result: ScanResult) = dispatch {
            val device = result.device
            if (!active || clients.size >= 8 || seen.size >= 64 || !seen.add(device.address) || device.address in clients) return@dispatch
            val link = Client(device); clients[device.address] = link
            try { link.gatt = device.connectGatt(context, false, clientCallback, BluetoothDevice.TRANSPORT_LE) }
            catch (_: SecurityException) { clients.remove(device.address); status("Nearby devices permission is required") }
            val current = generation
            main.postDelayed({ if (generation == current && clients[device.address] === link && !link.ready) close(link) }, 15_000)
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) = dispatch { if (active) advertising = true }
        override fun onStartFailure(errorCode: Int) = dispatch { if (active) { advertising = false; status("Bluetooth advertising failed ($errorCode)") } }
    }

    private fun close(link: Client) {
        if (clients[link.device.address] !== link) return
        clients.remove(link.device.address); fail(link.queue)
        runCatching { link.gatt?.disconnect(); link.gatt?.close() }
        link.identity?.let { detachIfUnused(it.nodeId) }; changed()
    }
    private fun detachIfUnused(id: String) {
        if (clients.values.none { it.ready && it.identity?.nodeId == id } && servers.values.none { it.ready && it.identity?.nodeId == id }) session.detach(id, now())
    }
    private fun close(link: Server) {
        if (servers[link.device.address] !== link) return
        servers.remove(link.device.address); fail(link.queue)
        runCatching { gattServer?.cancelConnection(link.device) }
        link.identity?.let { detachIfUnused(it.nodeId) }; changed()
        if (notifying === link) {
            // No callback token identifies a notification within a connection. Reset the
            // server epoch so a delayed completion cannot acknowledge a new packet.
            stop(); status("Bluetooth link interrupted · scan again")
        }
    }

    private val clientCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) = dispatch {
            val link = clients[gatt.device.address]
            if (link == null || link.gatt !== gatt) { gatt.close(); return@dispatch }
            if (status != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED) close(link)
            else if (newState == BluetoothProfile.STATE_CONNECTED && !gatt.discoverServices()) close(link)
        }
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) = dispatch {
            val link = clients[gatt.device.address]?.takeIf { it.gatt === gatt } ?: return@dispatch
            val service = gatt.getService(serviceId)
            link.write = service?.getCharacteristic(writeId); link.notify = service?.getCharacteristic(notifyId)
            val hello = service?.getCharacteristic(identityId)
            if (status != 0 || hello == null || link.write == null || link.notify == null || !gatt.readCharacteristic(hello)) close(link)
        }
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            onCharacteristicRead(gatt, characteristic, characteristic.value ?: byteArrayOf(), status)
        }
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) = dispatch {
            val link = clients[gatt.device.address]?.takeIf { it.gatt === gatt } ?: return@dispatch
            if (characteristic.uuid != identityId) return@dispatch
            val identity = if (status == 0) RadioProtocol.decode(value) else null
            if (identity == null || identity.kind != 0) { close(link); return@dispatch }
            session.observe(identity); changed()
            if (!session.accepts(identity)) { close(link); return@dispatch }
            link.identity = identity
            val notification = link.notify!!
            val descriptor = notification.getDescriptor(cccd)
            if (descriptor == null || !gatt.setCharacteristicNotification(notification, true)) { close(link); return@dispatch }
            val accepted = if (Build.VERSION.SDK_INT >= 33) gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS
            else { descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE; gatt.writeDescriptor(descriptor) }
            if (!accepted) close(link)
        }
        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) = dispatch {
            val link = clients[gatt.device.address]?.takeIf { it.gatt === gatt } ?: return@dispatch
            if (status != 0 || descriptor.uuid != cccd || link.identity == null) { close(link); return@dispatch }
            link.ready = session.attach(link.identity!!, now())
            val identity = session.identityBytes()
            if (!link.ready || identity == null) close(link) else enqueue(link.queue, identity, { if (!it) close(link) }, { close(link) }) { pump(link) }
            changed()
        }
        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) = dispatch {
            val link = clients[gatt.device.address]?.takeIf { it.gatt === gatt } ?: return@dispatch
            if (characteristic.uuid != writeId) return@dispatch
            if (status == 0) completed(link.queue, true) { pump(link) } else close(link)
        }
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            onCharacteristicChanged(gatt, characteristic, characteristic.value ?: byteArrayOf())
        }
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) = dispatch {
            val link = clients[gatt.device.address]?.takeIf { it.gatt === gatt } ?: return@dispatch
            if (characteristic.uuid == notifyId) incoming(link.assembler, value, link.identity?.nodeId)
        }
    }

    private fun serverCallback(token: Int) = object : BluetoothGattServerCallback() {
        override fun onServiceAdded(status: Int, service: BluetoothGattService) = dispatch {
            if (!active || generation != token) return@dispatch
            if (status != 0) { status("Could not publish the Bluetooth service"); return@dispatch }
            try { adapter?.bluetoothLeAdvertiser?.startAdvertising(
                AdvertiseSettings.Builder().setConnectable(true).setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED).build(),
                AdvertiseData.Builder().addServiceUuid(ParcelUuid(serviceId)).build(), advertiseCallback)
            } catch (_: SecurityException) { status("Nearby devices permission is required") }
        }
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) = dispatch {
            if (!active || generation != token) return@dispatch
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                servers[device.address]?.let { close(it) }; changed()
            } else if (status == 0 && newState == BluetoothProfile.STATE_CONNECTED && servers.size < 8) {
                val link = servers.getOrPut(device.address) { Server(device) }
                main.postDelayed({ if (generation == token && !link.ready) close(link) }, 15_000)
            }
            else gattServer?.cancelConnection(device)
        }
        override fun onCharacteristicReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic) = dispatch {
            if (!active || generation != token) return@dispatch
            val bytes = if (characteristic.uuid == identityId) session.identityBytes() else null
            if (bytes == null || offset !in 0..bytes.size) gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset, null)
            else gattServer?.sendResponse(device, requestId, 0, offset, bytes.copyOfRange(offset, bytes.size))
        }
        override fun onDescriptorReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, descriptor: BluetoothGattDescriptor) = dispatch {
            if (!active || generation != token) return@dispatch
            gattServer?.sendResponse(device, requestId, if (offset == 0) 0 else BluetoothGatt.GATT_INVALID_OFFSET, offset,
                if (servers[device.address]?.subscribed == true) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)
        }
        override fun onDescriptorWriteRequest(device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray) = dispatch {
            if (!active || generation != token) return@dispatch
            val link = servers[device.address]
            val valid = link != null && descriptor.uuid == cccd && !preparedWrite && offset == 0 &&
                (value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) || value.contentEquals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE))
            if (valid) {
                link!!.subscribed = value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                if (link.subscribed) ready(link) else {
                    close(link)
                }
            }
            if (responseNeeded) gattServer?.sendResponse(device, requestId, if (valid) 0 else BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, offset, null)
        }
        override fun onCharacteristicWriteRequest(device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray) = dispatch {
            if (!active || generation != token) return@dispatch
            val link = servers[device.address]
            val result = if (link != null && characteristic.uuid == writeId && !preparedWrite && offset == 0) link.assembler.append(value, now()) else null
            if (responseNeeded) gattServer?.sendResponse(device, requestId, if (result?.accepted == true) 0 else BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, 0, null)
            result?.packet?.let { bytes ->
                val packet = RadioProtocol.decode(bytes) ?: return@let
                if (packet.kind == 0 && session.accepts(packet)) { link!!.identity = packet; ready(link) }
                else if (link!!.ready) process(bytes, link.identity!!.nodeId)
            }
        }
        override fun onNotificationSent(device: BluetoothDevice, status: Int) = dispatch {
            if (!active || generation != token) return@dispatch
            val link = notifying ?: return@dispatch
            if (link.device.address != device.address) return@dispatch
            notifying = null
            if (status == 0) completed(link.queue, true) {} else close(link)
            pumpServers()
        }
    }

    private fun ready(link: Server) {
        val identity = link.identity ?: return
        if (!link.ready && link.subscribed) { link.ready = session.attach(identity, now()); changed() }
    }
    private fun incoming(assembler: RadioAssembler, frame: ByteArray, id: String?) {
        val result = assembler.append(frame, now())
        if (result.accepted && id != null) result.packet?.let { process(it, id) }
    }
    private fun process(bytes: ByteArray, id: String) { sendAll(session.receive(bytes, id, now())); changed() }

    fun sendAll(batch: List<RadioSend>) {
        batch.forEach { item ->
            val client = clients.values.firstOrNull { it.ready && it.identity?.nodeId == item.peerId }
            val server = servers.values.firstOrNull { it.ready && it.subscribed && it.identity?.nodeId == item.peerId }
            val groupId = session.snapshot().group?.id
            val completion: (Boolean) -> Unit = { accepted ->
                if (session.snapshot().group?.id == groupId) { session.sendResult(item.messageId, item.peerId, accepted, now()); changed() }
            }
            // Per-link role tie-break avoids duplicate sends; there is no group leader.
            if (client != null && (server == null || session.nodeId < item.peerId)) enqueue(client.queue, item.packet, completion, { close(client) }) { pump(client) }
            else if (server != null) enqueue(server.queue, item.packet, completion, { close(server) }) { pumpServers() }
            else completion(false)
        }
    }
    private fun enqueue(queue: ArrayDeque<Transfer>, bytes: ByteArray, completion: (Boolean) -> Unit, timeout: () -> Unit, pump: () -> Unit) {
        if (queue.size >= 8) { completion(false); return }
        val start = queue.isEmpty()
        val entry = Transfer(ArrayDeque(RadioFragments.split(bytes)), completion)
        queue.addLast(entry)
        val token = generation
        main.postDelayed({ if (generation == token && entry in queue) timeout() }, 60_000)
        if (start) pump()
    }
    private fun completed(queue: ArrayDeque<Transfer>, accepted: Boolean, pump: () -> Unit) {
        val entry = queue.firstOrNull() ?: return
        if (!accepted) { queue.removeFirst(); entry.done(false) }
        else { entry.frames.removeFirst(); if (entry.frames.isEmpty()) { queue.removeFirst(); entry.done(true) } }
        if (queue.isNotEmpty()) pump()
    }
    private fun fail(queue: ArrayDeque<Transfer>) { val pending = queue.toList(); queue.clear(); pending.forEach { it.done(false) } }
    private fun pump(link: Client) {
        val frame = link.queue.firstOrNull()?.frames?.firstOrNull() ?: return
        val characteristic = link.write ?: return fail(link.queue)
        try {
            val accepted = if (Build.VERSION.SDK_INT >= 33) link.gatt?.writeCharacteristic(characteristic, frame, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
            else { characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT; characteristic.value = frame; link.gatt?.writeCharacteristic(characteristic) == true }
            if (!accepted) close(link)
        } catch (_: SecurityException) { close(link); status("Nearby devices permission was revoked") }
    }
    private fun pumpServers() {
        if (!active || notifying != null) return
        val available = servers.values.filter { it.ready && it.subscribed && it.queue.isNotEmpty() }
        if (available.isEmpty()) return
        val index = (available.indexOfFirst { it.device.address == lastNotified } + 1) % available.size
        val link = available[index]
        lastNotified = link.device.address
        pump(link)
    }
    private fun pump(link: Server) {
        if (!active || notifying != null || servers[link.device.address] !== link) return
        val frame = link.queue.firstOrNull()?.frames?.firstOrNull() ?: return
        val characteristic = tx ?: return fail(link.queue)
        // GATT server notifications are globally serialized, not just per device.
        // https://developer.android.com/reference/android/bluetooth/BluetoothGattServerCallback#onNotificationSent(android.bluetooth.BluetoothDevice,%20int)
        notifying = link
        try {
            val accepted = if (Build.VERSION.SDK_INT >= 33) gattServer?.notifyCharacteristicChanged(link.device, characteristic, false, frame) == BluetoothStatusCodes.SUCCESS
            else { characteristic.value = frame; gattServer?.notifyCharacteristicChanged(link.device, characteristic, false) == true }
            if (!accepted) { notifying = null; close(link); pumpServers() }
        } catch (_: SecurityException) { stop(); status("Nearby devices permission was revoked") }
    }
}
