import SwiftUI
import Shared

@main
struct MurphyApp: App {
    @StateObject private var store = RadioStore()
    @Environment(\.scenePhase) private var scenePhase
    var body: some Scene {
        WindowGroup {
            MurphyRoot().environmentObject(store).preferredColorScheme(.light)
                .environment(\.locale, Locale(identifier: "en")).tint(Color("AccentColor"))
                .onChange(of: scenePhase) { phase in
                    if phase == .active { store.activate() }
                    else if phase == .background { store.background() }
                }
        }
    }
}

final class RadioStore: ObservableObject {
    private let session = RadioSession(nodeId: UUID().uuidString.lowercased())
    @Published private(set) var snapshot: RadioSnapshot
    private var wantsRadio = false
    private lazy var radio = IOSRadio(session: session) { [weak self] in self?.refresh() }
    init() { snapshot = session.snapshot() }
    private func refresh() { snapshot = session.snapshot() }
    func create(group: String, name: String) { join(id: UUID().uuidString.lowercased(), group: group, name: name) }
    func join(id: String, group: String, name: String) {
        radio.stop()
        let accepted = session.start(groupId: id, groupName: group.precomposedStringWithCanonicalMapping, name: name.precomposedStringWithCanonicalMapping)
        refresh()
        if accepted { find() }
    }
    func find() { wantsRadio = true; radio.start(); refresh() }
    func activate() { if wantsRadio { radio.start(); refresh() } }
    func background() { radio.stop(); session.status(value: "Bluetooth paused · keep Murphy open to communicate"); refresh() }
    @discardableResult func send(_ body: String) -> Bool {
        let id = UUID().uuidString.lowercased()
        let batch = session.send(body: body, messageId: id, atMillis: radioNow())
        refresh(); radio.sendAll(batch)
        return snapshot.messages.contains { $0.id == id }
    }
    func leave() { wantsRadio = false; radio.stop(); session.leave(); session.status(value: "Bluetooth not started"); refresh() }
}

private enum Destination: Hashable { case group, network, chat, history }

struct MurphyRoot: View {
    @EnvironmentObject private var store: RadioStore
    @State private var destination: Destination = .group
    @State private var settings = false
    @State private var sosInformation = false
    @ScaledMetric(relativeTo: .largeTitle) private var metricSize = 84

    var body: some View {
        Group {
            if store.snapshot.group == nil { NavigationStack { WelcomeScreen() } }
            else {
                TabView(selection: $destination) {
                    NavigationStack {
                        ScreenShell(title: "Murphy", settings: $settings) {
                            ScrollView {
                                VStack(alignment: .leading, spacing: 24) {
                                    PageHeading(eyebrow: "YOUR GROUP", title: store.snapshot.group?.name ?? "")
                                    PremiumCard {
                                        Text("\(store.snapshot.peers.count)").font(.system(size: metricSize, weight: .bold, design: .rounded))
                                        Text("direct peers").font(.headline)
                                        Hint("Connected Bluetooth links. This is not a global reach or delivery count.")
                                    }
                                    HStack {
                                        Button("Network") { destination = .network }.buttonStyle(.bordered)
                                        Button("Chat") { destination = .chat }.buttonStyle(.borderedProminent)
                                    }.controlSize(.large)
                                    PremiumCard {
                                        Text("Stay close. Stay connected.").font(.title2.bold())
                                        Hint("This is an open, unencrypted group. Keep Murphy in the foreground. Do not send sensitive information or rely on it for safety.")
                                        Hint("Group ID · \(store.snapshot.group?.id.prefix(8) ?? "")")
                                    }
                                    Button("Scan nearby") { store.find() }.buttonStyle(.borderedProminent).controlSize(.large)
                                    Button("SOS not available yet") { sosInformation = true }
                                        .buttonStyle(.bordered).controlSize(.large).tint(Color("Danger"))
                                }.padding(24)
                            }
                        }
                    }.tabItem { Label("Group", systemImage: "house") }.tag(Destination.group)
                    NavigationStack { ScreenShell(title: "Network", settings: $settings) { NetworkScreen() } }
                        .tabItem { Label("Network", systemImage: "point.3.connected.trianglepath.dotted") }.tag(Destination.network)
                    NavigationStack { ScreenShell(title: "Chat", settings: $settings) { ChatScreen() } }
                        .tabItem { Label("Chat", systemImage: "bubble.left.and.bubble.right") }.tag(Destination.chat)
                    NavigationStack { ScreenShell(title: "History", settings: $settings) { HistoryScreen() } }
                        .tabItem { Label("History", systemImage: "clock.arrow.circlepath") }.tag(Destination.history)
                }
            }
        }.sheet(isPresented: $settings) { SettingsScreen() }
            .alert("SOS is not available yet", isPresented: $sosInformation) {
                Button("Got it", role: .cancel) { }
            } message: {
                Text("This build cannot send emergency alerts. It does not contact rescue services or share your location. Do not rely on Murphy for safety.")
            }
    }
}

private struct WelcomeScreen: View {
    @EnvironmentObject private var store: RadioStore
    @State private var name = ""
    @State private var group = ""
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                PremiumCard(dark: true) {
                    Text("A little closer.\nEven offline.").font(.largeTitle.bold()).accessibilityAddTraits(.isHeader)
                    Text("Communicate with your nearby group over Bluetooth, without a server.")
                    Text("Development build · foreground only · not encrypted").font(.callout)
                }
                TextField("Your name", text: $name).textContentType(.nickname).textFieldStyle(.roundedBorder).accessibilityLabel("Your name")
                TextField("Group name", text: $group).textFieldStyle(.roundedBorder).accessibilityLabel("Group name")
                Button("Create group") { store.create(group: group, name: name) }
                    .buttonStyle(.borderedProminent).controlSize(.large)
                    .disabled(name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || group.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                Button("Find nearby groups") { store.find() }.buttonStyle(.bordered).controlSize(.large)
                ForEach(store.snapshot.nearbyGroups, id: \.id) { found in
                    PremiumCard {
                        Text(found.name).font(.title2.bold())
                        Hint("Open group · \(found.id.prefix(8))")
                        Button("Join group") { store.join(id: found.id, group: found.name, name: name) }
                            .buttonStyle(.borderedProminent).disabled(name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    }
                }
                if let notice = store.snapshot.notice { Text(notice).foregroundStyle(Color("Danger")) }
                Hint("Bluetooth permission enables nearby discovery and messaging. Murphy does not derive your location.")
                Hint("Open groups are not private. Not for emergencies. No delivery guarantee.")
            }.padding(24)
        }.background(Color("Canvas")).safeAreaInset(edge: .top, spacing: 0) { RadioBanner() }
            .navigationTitle("murphy").navigationBarTitleDisplayMode(.inline)
    }
}

private struct ScreenShell<Content: View>: View {
    let title: String
    @Binding var settings: Bool
    @ViewBuilder let content: () -> Content
    @EnvironmentObject private var store: RadioStore
    var body: some View {
        content().background(Color("Canvas")).safeAreaInset(edge: .top, spacing: 0) {
            VStack(alignment: .leading, spacing: 0) {
                RadioBanner()
                if let notice = store.snapshot.notice { Text(notice).foregroundStyle(Color("Danger")).padding() }
            }
        }.navigationTitle(title).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .primaryAction) {
                Button { settings = true } label: { Label("Open settings", systemImage: "gearshape") }
            } }
    }
}

private struct NetworkScreen: View {
    @EnvironmentObject private var store: RadioStore
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                PageHeading(eyebrow: "NEARBY BLUETOOTH", title: "Your network")
                PremiumCard(dark: true) {
                    Text(store.snapshot.peers.isEmpty ? "No connected peers" : "Connected nearby").font(.title.bold())
                    Text("Peers can relay group messages. No leader is required.")
                }
                PremiumCard { Text(store.snapshot.displayName).font(.title2.bold()); Hint("This device · Bluetooth group member") }
                ForEach(store.snapshot.peers, id: \.id) { peer in
                    PremiumCard { Text(peer.name).font(.title2.bold()); Hint("Connected peer · \(peer.id.prefix(8))") }
                }
                Hint("Bluetooth does not measure a GPS position or distance here. Peer identity is not authenticated yet.")
                Button("Scan nearby") { store.find() }.buttonStyle(.borderedProminent).controlSize(.large)
            }.padding(24)
        }
    }
}

private struct ChatScreen: View {
    @EnvironmentObject private var store: RadioStore
    @State private var draft = ""
    @FocusState private var composing: Bool
    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 24) {
                PageHeading(eyebrow: "GROUP CHAT", title: "Messages")
                Hint("Bluetooth acceptance is not destination receipt. Messages and history are in memory only.")
                if store.snapshot.peers.isEmpty { Hint("No connected peers. Your message will not be transmitted.") }
                ForEach(store.snapshot.messages, id: \.id) { message in
                    PremiumCard {
                        Text(message.own ? "You" : message.sender).font(.callout.bold()).foregroundStyle(Color("AccentColor"))
                        Text(message.body)
                        Text(message.status).font(.callout).foregroundStyle(Color("Muted"))
                    }
                }
                TextField("Message", text: $draft, axis: .vertical).lineLimit(2...6)
                    .textFieldStyle(.roundedBorder).accessibilityLabel("Message").accessibilityIdentifier("message-input").focused($composing)
                Text("\(draft.utf16.count)/1,000 characters").font(.caption).foregroundStyle(Color("Muted"))
                Button("Send message") {
                    if store.send(draft) { draft = ""; composing = false }
                }.buttonStyle(.borderedProminent).controlSize(.large)
                    .disabled(draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }.padding(24)
        }.scrollDismissesKeyboard(.interactively)
    }
}

private struct HistoryScreen: View {
    @EnvironmentObject private var store: RadioStore
    var body: some View {
        List {
            Section { Hint("Real local activity · memory only. Not a destination acknowledgement.") }
            Section("On this device") {
                ForEach(Array(store.snapshot.events.reversed()), id: \.id) { event in
                    VStack(alignment: .leading, spacing: 8) { Text(event.title).font(.headline); Hint(event.detail) }.padding(.vertical, 8)
                }
            }
        }.scrollContentBackground(.hidden)
    }
}

private struct SettingsScreen: View {
    @EnvironmentObject private var store: RadioStore
    @Environment(\.dismiss) private var dismiss
    @State private var leaving = false
    var body: some View {
        NavigationStack {
            Form {
                Section("Experience") {
                    LabeledContent("Language", value: "English")
                    Text("Native text scaling and screen-reader labels are enabled.")
                }
                Section("Development limitations") {
                    Text("Foreground Bluetooth only. No encryption or persistent history yet.")
                    Text("Restarting the process loses this session. Do not rely on this build for safety.")
                }
                Section { Button("Leave group", role: .destructive) { leaving = true } }
            }.navigationTitle("Settings").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button { dismiss() } label: { Label("Done", systemImage: "xmark") } } }
                .confirmationDialog("Leave this group? Bluetooth links close and local messages clear.", isPresented: $leaving, titleVisibility: .visible) {
                    Button("Leave group", role: .destructive) { store.leave(); dismiss() }
                    Button("Stay", role: .cancel) { }
                }
        }
    }
}

private struct RadioBanner: View {
    @EnvironmentObject private var store: RadioStore
    var body: some View {
        Text(store.snapshot.radioStatus).font(.footnote.bold()).foregroundStyle(Color("AccentColor"))
            .frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 24).padding(.vertical, 12).background(Color("AccentSoft"))
    }
}
private struct PageHeading: View {
    let eyebrow: String
    let title: String
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(eyebrow).font(.caption.bold()).foregroundStyle(Color("AccentColor"))
            Text(title).font(.largeTitle.bold()).accessibilityAddTraits(.isHeader)
        }
    }
}
private struct Hint: View {
    let text: String
    init(_ text: String) { self.text = text }
    var body: some View { Text(text).font(.callout).foregroundStyle(Color("Muted")) }
}
private struct PremiumCard<Content: View>: View {
    var dark = false
    @ViewBuilder let content: () -> Content
    var body: some View {
        VStack(alignment: .leading, spacing: 12, content: content).frame(maxWidth: .infinity, alignment: .leading).padding(24)
            .foregroundStyle(dark ? Color.white : Color("Ink"))
            .background(dark ? Color("Night") : Color("Surface"), in: RoundedRectangle(cornerRadius: 28))
    }
}
