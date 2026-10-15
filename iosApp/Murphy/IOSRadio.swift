import Foundation
import CoreBluetooth
import Shared

func radioBytes(_ data: Data) -> KotlinByteArray {
    let bytes = KotlinByteArray(size: Int32(data.count))
    for (index, value) in data.enumerated() { bytes.set(index: Int32(index), value: Int8(bitPattern: value)) }
    return bytes
}
func radioData(_ bytes: KotlinByteArray) -> Data {
    Data((0..<Int(bytes.size)).map { UInt8(bitPattern: bytes.get(index: Int32($0))) })
}
func radioNow() -> Int64 { Int64(ProcessInfo.processInfo.systemUptime * 1000) }

/// Real, foreground-only CoreBluetooth central/peripheral; no simulator radio substitutes.
final class IOSRadio: NSObject, CBCentralManagerDelegate, CBPeripheralManagerDelegate, CBPeripheralDelegate {
    private let session: RadioSession
    private let changed: () -> Void
    private var central: CBCentralManager?
    private var peripheral: CBPeripheralManager?
    private var active = false
    private var generation = 0
    private var scanning = false
    private var publishing = false
    private var serviceReady = false
    private var tx: CBMutableCharacteristic?
    private var clients: [UUID: Client] = [:]
    private var servers: [UUID: Server] = [:]
    private var seen = Set<UUID>()
    private let serviceId = CBUUID(string: RadioProtocol.shared.SERVICE_UUID)
    private let identityId = CBUUID(string: RadioProtocol.shared.IDENTITY_UUID)
    private let writeId = CBUUID(string: RadioProtocol.shared.WRITE_UUID)
    private let notifyId = CBUUID(string: RadioProtocol.shared.NOTIFY_UUID)

    private final class Transfer {
        var frames: [Data]
        let done: (Bool) -> Void
        init(_ packet: KotlinByteArray, done: @escaping (Bool) -> Void) {
            frames = RadioFragments.shared.split(packet: packet).map(radioData)
            self.done = done
        }
    }
    private final class Client {
        let device: CBPeripheral
        var identity: RadioPacket?
        var write: CBCharacteristic?
        var notify: CBCharacteristic?
        var ready = false
        let assembler = RadioAssembler()
        var queue: [Transfer] = []
        init(_ device: CBPeripheral) { self.device = device }
    }
    private final class Server {
        let device: CBCentral
        var identity: RadioPacket?
        var ready = false
        var subscribed = false
        let assembler = RadioAssembler()
        var queue: [Transfer] = []
        init(_ device: CBCentral) { self.device = device }
    }

    init(session: RadioSession, changed: @escaping () -> Void) { self.session = session; self.changed = changed }
    private func status(_ text: String) { session.status(value: text); changed() }

    func start() {
        #if targetEnvironment(simulator)
        status("No physical Bluetooth in Simulator · use an iPhone")
        return
        #else
        if active {
            configurePeripheral()
            if serviceReady, peripheral?.isAdvertising == false { peripheral?.startAdvertising([CBAdvertisementDataServiceUUIDsKey: [serviceId]]) }
            scan(); return
        }
        active = true; generation += 1
        if central == nil { central = CBCentralManager(delegate: self, queue: .main, options: [CBCentralManagerOptionShowPowerAlertKey: false]) }
        if peripheral == nil { peripheral = CBPeripheralManager(delegate: self, queue: .main, options: [CBPeripheralManagerOptionShowPowerAlertKey: false]) }
        configurePeripheral(); scan()
        #endif
    }

    func stop() {
        active = false; generation += 1; scanning = false; publishing = false; serviceReady = false
        central?.stopScan(); peripheral?.stopAdvertising(); peripheral?.removeAllServices()
        let oldClients = Array(clients.values); let oldServers = Array(servers.values)
        clients.removeAll(); servers.removeAll(); seen.removeAll(); tx = nil
        for client in oldClients { fail(client.queue); central?.cancelPeripheralConnection(client.device) }
        for server in oldServers { fail(server.queue) }
        session.disconnected(atMillis: radioNow()); changed()
    }

    func centralManagerDidUpdateState(_ manager: CBCentralManager) {
        guard active else { return }
        if manager.state == .poweredOn { scan() }
        else { lostRadio(manager.state) }
    }
    func peripheralManagerDidUpdateState(_ manager: CBPeripheralManager) {
        guard active else { return }
        if manager.state == .poweredOn { configurePeripheral() }
        else { lostRadio(manager.state) }
    }
    private func lostRadio(_ state: CBManagerState) {
        if state == .unknown || state == .resetting { status("Bluetooth is starting"); return }
        stop()
        switch state {
        case .unauthorized: status("Bluetooth permission is required · check Settings")
        case .poweredOff: status("Bluetooth is off · enable it in Control Center")
        case .unsupported: status("Bluetooth is unavailable on this device")
        default: status("Bluetooth is unavailable · try again")
        }
    }

    private func configurePeripheral() {
        guard active, peripheral?.state == .poweredOn, session.identityBytes() != nil, !publishing else { return }
        publishing = true
        let identity = CBMutableCharacteristic(type: identityId, properties: [.read], value: nil, permissions: [.readable])
        let write = CBMutableCharacteristic(type: writeId, properties: [.write], value: nil, permissions: [.writeable])
        let notification = CBMutableCharacteristic(type: notifyId, properties: [.notify], value: nil, permissions: [.readable])
        tx = notification
        let service = CBMutableService(type: serviceId, primary: true)
        service.characteristics = [identity, write, notification]
        peripheral?.add(service)
    }
    func peripheralManager(_ manager: CBPeripheralManager, didAdd service: CBService, error: Error?) {
        guard active else { return }
        if let error = error as NSError? { publishing = false; status("Bluetooth service failed (\(error.code))"); return }
        serviceReady = true
        manager.startAdvertising([CBAdvertisementDataServiceUUIDsKey: [serviceId]])
    }
    func peripheralManagerDidStartAdvertising(_ manager: CBPeripheralManager, error: Error?) {
        guard active else { return }
        if let error = error as NSError? { status("Bluetooth advertising failed (\(error.code))") }
    }

    private func scan() {
        guard active, central?.state == .poweredOn, !scanning else { return }
        scanning = true; seen.removeAll()
        central?.scanForPeripherals(withServices: [serviceId], options: [CBCentralManagerScanOptionAllowDuplicatesKey: false])
        status("Looking for nearby groups and peers")
        let token = generation
        DispatchQueue.main.asyncAfter(deadline: .now() + 10) { [weak self] in
            guard let self, self.generation == token, self.scanning else { return }
            self.central?.stopScan(); self.scanning = false
            self.status(self.session.snapshot().group == nil ? "Nearby group scan finished" :
                (self.peripheral?.isAdvertising == true ? "Group discoverable · scan finished" : "Peer scan finished · advertising unavailable"))
        }
    }
    func centralManager(_ manager: CBCentralManager, didDiscover device: CBPeripheral, advertisementData: [String: Any], rssi: NSNumber) {
        guard active, clients.count < 8, seen.count < 64, seen.insert(device.identifier).inserted, clients[device.identifier] == nil else { return }
        let link = Client(device); clients[device.identifier] = link
        device.delegate = self; manager.connect(device)
        let token = generation
        DispatchQueue.main.asyncAfter(deadline: .now() + 15) { [weak self, weak link] in
            guard let self, let link, self.generation == token, self.clients[device.identifier] === link, !link.ready else { return }
            self.close(link)
        }
    }
    func centralManager(_ manager: CBCentralManager, didConnect device: CBPeripheral) {
        guard active, clients[device.identifier]?.device === device else { manager.cancelPeripheralConnection(device); return }
        device.discoverServices([serviceId])
    }
    func centralManager(_ manager: CBCentralManager, didFailToConnect device: CBPeripheral, error: Error?) { if let link = clients[device.identifier] { close(link) } }
    func centralManager(_ manager: CBCentralManager, didDisconnectPeripheral device: CBPeripheral, error: Error?) { if let link = clients[device.identifier] { close(link) } }
    private func close(_ link: Client) {
        guard clients[link.device.identifier] === link else { return }
        clients.removeValue(forKey: link.device.identifier); fail(link.queue)
        central?.cancelPeripheralConnection(link.device)
        if let id = link.identity?.nodeId { detachIfUnused(id) }
        changed()
    }
    private func detachIfUnused(_ id: String) {
        if !clients.values.contains(where: { $0.ready && $0.identity?.nodeId == id }) &&
            !servers.values.contains(where: { $0.ready && $0.identity?.nodeId == id }) {
            session.detach(peerId: id, atMillis: radioNow())
        }
    }

    func peripheral(_ device: CBPeripheral, didDiscoverServices error: Error?) {
        guard let link = clients[device.identifier], error == nil, let service = device.services?.first(where: { $0.uuid == serviceId }) else {
            if let link = clients[device.identifier] { close(link) }; return
        }
        link.device.discoverCharacteristics([identityId, writeId, notifyId], for: service)
    }
    func peripheral(_ device: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        guard let link = clients[device.identifier], link.device === device else { return }
        guard error == nil else { close(link); return }
        link.write = service.characteristics?.first { $0.uuid == writeId }
        link.notify = service.characteristics?.first { $0.uuid == notifyId }
        guard let identity = service.characteristics?.first(where: { $0.uuid == identityId }), link.write != nil, link.notify != nil else { close(link); return }
        device.readValue(for: identity)
    }
    func peripheral(_ device: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        guard let link = clients[device.identifier], link.device === device else { return }
        guard error == nil, let data = characteristic.value else { close(link); return }
        if characteristic.uuid == identityId {
            guard let identity = RadioProtocol.shared.decode(bytes: radioBytes(data)), identity.kind == 0 else { close(link); return }
            session.observe(identity: identity); changed()
            guard session.accepts(identity: identity), let notification = link.notify else { close(link); return }
            link.identity = identity; device.setNotifyValue(true, for: notification)
        } else if characteristic.uuid == notifyId {
            let result = link.assembler.append(frame: radioBytes(data), atMillis: radioNow())
            if result.accepted, let packet = result.packet, let id = link.identity?.nodeId { process(packet, from: id) }
        }
    }
    func peripheral(_ device: CBPeripheral, didUpdateNotificationStateFor characteristic: CBCharacteristic, error: Error?) {
        guard let link = clients[device.identifier], characteristic.uuid == notifyId else { return }
        guard error == nil, characteristic.isNotifying, let identity = link.identity, let hello = session.identityBytes() else { close(link); return }
        link.ready = session.attach(identity: identity, atMillis: radioNow())
        guard link.ready else { close(link); return }
        enqueue(link, packet: hello) { [weak self, weak link] ok in if !ok, let link { self?.close(link) } }
        changed()
    }
    func peripheral(_ device: CBPeripheral, didWriteValueFor characteristic: CBCharacteristic, error: Error?) {
        guard let link = clients[device.identifier], characteristic.uuid == writeId, let entry = link.queue.first else { return }
        if error != nil { close(link); return }
        else { entry.frames.removeFirst(); if entry.frames.isEmpty { link.queue.removeFirst(); entry.done(true) } }
        pump(link)
    }

    func peripheralManager(_ manager: CBPeripheralManager, didReceiveRead request: CBATTRequest) {
        guard active, request.characteristic.uuid == identityId, let bytes = session.identityBytes() else { manager.respond(to: request, withResult: .readNotPermitted); return }
        let data = radioData(bytes)
        guard request.offset >= 0, request.offset <= data.count else { manager.respond(to: request, withResult: .invalidOffset); return }
        request.value = data.subdata(in: request.offset..<data.count)
        manager.respond(to: request, withResult: .success)
    }
    func peripheralManager(_ manager: CBPeripheralManager, didReceiveWrite requests: [CBATTRequest]) {
        guard let first = requests.first else { return }
        guard active else { manager.respond(to: first, withResult: .writeNotPermitted); return }
        for request in requests {
            guard request.characteristic.uuid == writeId, request.offset == 0, let data = request.value else { manager.respond(to: first, withResult: .writeNotPermitted); return }
            let key = request.central.identifier
            if servers[key] == nil {
                if servers.count >= 8 { manager.respond(to: first, withResult: .insufficientResources); return }
                let link = Server(request.central); servers[key] = link; expireHandshake(link)
            }
            let link = servers[key]!
            let result = link.assembler.append(frame: radioBytes(data), atMillis: radioNow())
            guard result.accepted else { manager.respond(to: first, withResult: .invalidAttributeValueLength); return }
            if let bytes = result.packet, let packet = RadioProtocol.shared.decode(bytes: bytes) {
                if packet.kind == 0, session.accepts(identity: packet) { link.identity = packet; ready(link) }
                else if link.ready, let id = link.identity?.nodeId { process(bytes, from: id) }
            }
        }
        manager.respond(to: first, withResult: .success)
    }
    func peripheralManager(_ manager: CBPeripheralManager, central: CBCentral, didSubscribeTo characteristic: CBCharacteristic) {
        guard active, characteristic.uuid == notifyId, servers.count < 8 || servers[central.identifier] != nil else { return }
        let link = servers[central.identifier] ?? Server(central)
        if servers[central.identifier] == nil { servers[central.identifier] = link; expireHandshake(link) }
        link.subscribed = true; ready(link)
    }
    func peripheralManager(_ manager: CBPeripheralManager, central: CBCentral, didUnsubscribeFrom characteristic: CBCharacteristic) {
        guard let link = servers.removeValue(forKey: central.identifier) else { return }
        fail(link.queue); if let id = link.identity?.nodeId { detachIfUnused(id) }; changed()
    }
    func peripheralManagerIsReady(toUpdateSubscribers manager: CBPeripheralManager) { for link in Array(servers.values) { pump(link) } }
    private func expireHandshake(_ link: Server) {
        let token = generation
        DispatchQueue.main.asyncAfter(deadline: .now() + 15) { [weak self, weak link] in
            guard let self, let link, self.generation == token, self.servers[link.device.identifier] === link, !link.ready else { return }
            self.servers.removeValue(forKey: link.device.identifier)
        }
    }
    private func ready(_ link: Server) {
        if link.subscribed, !link.ready, let identity = link.identity { link.ready = session.attach(identity: identity, atMillis: radioNow()); changed() }
    }
    private func process(_ packet: KotlinByteArray, from id: String) { sendAll(session.receive(bytes: packet, fromPeer: id, atMillis: radioNow())); changed() }

    func sendAll(_ batch: [RadioSend]) {
        for item in batch {
            let client = clients.values.first { $0.ready && $0.identity?.nodeId == item.peerId }
            let server = servers.values.first { $0.ready && $0.subscribed && $0.identity?.nodeId == item.peerId }
            let group = session.snapshot().group?.id
            let done: (Bool) -> Void = { [weak self] ok in
                guard let self, self.session.snapshot().group?.id == group else { return }
                self.session.sendResult(messageId: item.messageId, peerId: item.peerId, accepted: ok, atMillis: radioNow()); self.changed()
            }
            if let client, server == nil || session.nodeId < item.peerId { enqueue(client, packet: item.packet, done: done) }
            else if let server { enqueue(server, packet: item.packet, done: done) }
            else { done(false) }
        }
    }
    private func fail(_ queue: [Transfer]) { for transfer in queue { transfer.done(false) } }
    private func enqueue(_ client: Client, packet: KotlinByteArray, done: @escaping (Bool) -> Void) {
        guard client.queue.count < 8 else { done(false); return }
        let entry = Transfer(packet, done: done)
        client.queue.append(entry)
        let token = generation
        DispatchQueue.main.asyncAfter(deadline: .now() + 60) { [weak self, weak client, weak entry] in
            guard let self, let client, let entry, self.generation == token, client.queue.contains(where: { $0 === entry }) else { return }
            self.close(client)
        }
        if client.queue.count == 1 { pump(client) }
    }
    private func enqueue(_ server: Server, packet: KotlinByteArray, done: @escaping (Bool) -> Void) {
        guard server.queue.count < 8 else { done(false); return }
        let entry = Transfer(packet, done: done)
        server.queue.append(entry)
        let token = generation
        DispatchQueue.main.asyncAfter(deadline: .now() + 60) { [weak self, weak server, weak entry] in
            guard let self, let server, let entry, self.generation == token, server.queue.contains(where: { $0 === entry }) else { return }
            // CoreBluetooth cannot disconnect a subscribing central. Reset the service rather
            // than assign a late completion to a different packet on this connection.
            self.stop(); self.status("Bluetooth transfer timed out · scan again")
        }
        pump(server)
    }
    private func pump(_ client: Client) {
        guard let frame = client.queue.first?.frames.first, let characteristic = client.write else { return }
        client.device.writeValue(frame, for: characteristic, type: .withResponse)
    }
    private func pump(_ server: Server) {
        guard let characteristic = tx else { return }
        while let entry = server.queue.first {
            while let frame = entry.frames.first {
                guard peripheral?.updateValue(frame, for: characteristic, onSubscribedCentrals: [server.device]) == true else { return }
                entry.frames.removeFirst()
            }
            server.queue.removeFirst(); entry.done(true)
        }
    }
}
