import SwiftUI
import Shared

@main
struct MurphyApp: App {
    @StateObject private var store = PreviewStore()

    var body: some Scene {
        WindowGroup {
            MurphyRoot()
                .environmentObject(store)
                .preferredColorScheme(.light)
                .environment(\.locale, Locale(identifier: "en"))
                .tint(Color("AccentColor"))
        }
    }
}

/// Main-thread UI facade over the same Kotlin demo state used by Android.
final class PreviewStore: ObservableObject {
    private let session = PreviewSession()
    @Published private(set) var snapshot: PreviewSnapshot

    init() { snapshot = session.snapshot() }

    func update(_ action: (PreviewSession) -> Void) {
        action(session)
        snapshot = session.snapshot()
    }
}

private enum Destination: Hashable { case group, network, chat, history }

struct MurphyRoot: View {
    @EnvironmentObject private var store: PreviewStore
    @State private var destination: Destination = .group
    @State private var settings = false
    @State private var confirmSos = false
    @ScaledMetric(relativeTo: .largeTitle) private var metricSize = 84

    var body: some View {
        Group {
            if store.snapshot.groupName == nil {
                NavigationStack { WelcomeScreen() }
            } else {
                TabView(selection: $destination) {
                    NavigationStack {
                        ScreenShell(title: "Murphy", settings: $settings) {
                            ScrollView {
                                VStack(alignment: .leading, spacing: 24) {
                                    PageHeading(eyebrow: "YOUR GROUP", title: store.snapshot.groupName ?? "")
                                    PremiumCard {
                                        Text("0").font(.system(size: metricSize, weight: .bold, design: .rounded))
                                        Text("direct peers").font(.headline)
                                        Hint("No radio connected. Open this demo to explore the experience.")
                                    }
                                    HStack {
                                        Button("Network") { destination = .network }.buttonStyle(.bordered)
                                        Button("Chat") { destination = .chat }.buttonStyle(.borderedProminent)
                                    }.controlSize(.large)
                                    PremiumCard {
                                        Text("Stay close. Stay connected.").font(.title2.bold())
                                        Hint("Murphy is being built for nearby, offline communication. Discovery, messaging and delivery confirmation are not active in this demo.")
                                    }
                                    Button("Preview SOS") { confirmSos = true }
                                        .buttonStyle(.bordered).controlSize(.large).tint(Color("Danger"))
                                    Hint("Not an emergency service. Never rely on this demo for safety.")
                                }.padding(24)
                            }
                        }
                    }.tabItem { Label("Group", systemImage: "house") }.tag(Destination.group)
                    NavigationStack {
                        ScreenShell(title: "Network", settings: $settings) { NetworkScreen() }
                    }.tabItem { Label("Network", systemImage: "point.3.connected.trianglepath.dotted") }.tag(Destination.network)
                    NavigationStack {
                        ScreenShell(title: "Chat", settings: $settings) { ChatScreen() }
                    }.tabItem { Label("Chat", systemImage: "bubble.left.and.bubble.right") }.tag(Destination.chat)
                    NavigationStack {
                        ScreenShell(title: "History", settings: $settings) { HistoryScreen() }
                    }.tabItem { Label("History", systemImage: "clock.arrow.circlepath") }.tag(Destination.history)
                }
            }
        }
        .sheet(isPresented: $settings) { SettingsScreen() }
        .alert("Preview an SOS?", isPresented: $confirmSos) {
            Button("Open preview") { store.update { _ = $0.previewSos() } }
            Button("Cancel", role: .cancel) { }
        } message: {
            Text("This opens a UI preview only. No alert is sent, no rescue service is contacted and no location is shared. Real SOS will be limited to your group.")
        }
        .sheet(isPresented: Binding(get: { store.snapshot.sosPreview }, set: { if !$0 { store.update { $0.closeSosPreview() } } })) {
            NavigationStack {
                VStack(alignment: .leading, spacing: 24) {
                    PageHeading(eyebrow: "NO ALERT SENT", title: "SOS preview")
                    PremiumCard {
                        Text("No one has been alerted.").font(.title2.bold()).foregroundStyle(Color("Danger"))
                        Text("This is not a working emergency channel. No destination acknowledgement is available.")
                    }
                    Button("Close preview") { store.update { $0.closeSosPreview() } }
                        .buttonStyle(.borderedProminent).controlSize(.large)
                    Spacer()
                }.padding(24).background(Color("Canvas"))
                    .navigationTitle("Preview only").navigationBarTitleDisplayMode(.inline)
                    .toolbar { ToolbarItem(placement: .cancellationAction) {
                        Button { store.update { $0.closeSosPreview() } } label: { Label("Close preview", systemImage: "xmark") }
                    } }
            }
        }
    }
}

private struct WelcomeScreen: View {
    @EnvironmentObject private var store: PreviewStore
    @State private var opening = false
    @State private var name = ""
    @State private var group = ""

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                PremiumCard(dark: true) {
                    Text("A little closer.\nEven offline.").font(.largeTitle.bold()).accessibilityAddTraits(.isHeader)
                    Text("A calmer way to stay with your people, beyond cellular coverage.")
                    Text("Product preview · no Bluetooth messaging").font(.callout)
                }
                if opening {
                    PageHeading(eyebrow: "GET STARTED", title: "Your demo group")
                    TextField("Your name", text: $name).textContentType(.nickname)
                        .textFieldStyle(.roundedBorder).accessibilityLabel("Your name")
                    TextField("Group name", text: $group).textFieldStyle(.roundedBorder)
                        .accessibilityLabel("Group name")
                    Button("Open demo group") { store.update { _ = $0.start(groupName: group, displayName: name) } }
                        .buttonStyle(.borderedProminent).controlSize(.large)
                        .disabled(name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || group.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    Button("Back to welcome") { opening = false }
                    Hint("This creates a local demo, not a discoverable group. Bluetooth permissions and nearby group discovery will arrive with the native radio integration.")
                } else {
                    Button("Explore the demo") { opening = true }.buttonStyle(.borderedProminent).controlSize(.large)
                    Hint("No account. No permission request. No real messages are sent.")
                }
                if let notice = store.snapshot.notice { Text(notice).foregroundStyle(Color("Danger")) }
                Hint("Not for emergencies. Never rely on Murphy Demo for rescue or delivery.")
            }.padding(24)
        }.background(Color("Canvas"))
            .safeAreaInset(edge: .top, spacing: 0) { DemoBanner() }
            .navigationTitle("murphy").navigationBarTitleDisplayMode(.inline)
    }
}

private struct ScreenShell<Content: View>: View {
    let title: String
    @Binding var settings: Bool
    @ViewBuilder let content: () -> Content
    @EnvironmentObject private var store: PreviewStore

    var body: some View {
        content().background(Color("Canvas"))
            .safeAreaInset(edge: .top, spacing: 0) {
                VStack(alignment: .leading, spacing: 0) {
                    DemoBanner()
                    if let notice = store.snapshot.notice { Text(notice).foregroundStyle(Color("Danger")).padding() }
                }
            }
            .navigationTitle(title).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .primaryAction) {
                Button { settings = true } label: { Label("Open settings", systemImage: "gearshape") }
            } }
    }
}

private struct NetworkScreen: View {
    @EnvironmentObject private var store: PreviewStore
    @State private var explanation = false
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                PageHeading(eyebrow: "OFFLINE BY DESIGN", title: "Your network")
                PremiumCard(dark: true) {
                    Text("Just you, for now.").font(.title.bold())
                    Text("No connected peers").font(.headline)
                    Text("This demo does not scan, advertise or connect over Bluetooth.")
                }
                PremiumCard {
                    Text(store.snapshot.displayName).font(.title2.bold())
                    Hint("This device · local demo identity")
                    Button("How the mesh will work") { explanation = true }.buttonStyle(.bordered)
                }
                Hint("The mesh view is not a GPS map. Peer distance and location are not measured.")
            }.padding(24)
        }.alert("A mesh, not a central leader", isPresented: $explanation) {
            Button("Got it", role: .cancel) { }
        } message: {
            Text("The shared routing core can relay packets between peers with hop limits and duplicate protection. Native discovery and radio transport are still being built. No live network is shown here.")
        }
    }
}

private struct ChatScreen: View {
    @EnvironmentObject private var store: PreviewStore
    @State private var draft = ""
    @FocusState private var composing: Bool
    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 24) {
                PageHeading(eyebrow: "NOT TRANSMITTING", title: "Group chat")
                Hint("No connected peers. Save an unsent demo draft to explore this screen. Drafts are kept in memory only.")
                ForEach(store.snapshot.drafts, id: \.id) { message in
                    PremiumCard {
                        Text(message.body)
                        Text("Not sent · demo only").font(.callout.bold()).foregroundStyle(Color("AccentColor"))
                    }
                }
                TextField("Demo message", text: $draft, axis: .vertical).lineLimit(2...6)
                    .textFieldStyle(.roundedBorder).accessibilityLabel("Demo message")
                    .accessibilityIdentifier("demo-message-input")
                    .focused($composing)
                Text("\(draft.count)/1,000 characters").font(.caption).foregroundStyle(Color("Muted"))
                Button("Save unsent draft") {
                    let count = store.snapshot.drafts.count
                    store.update { _ = $0.saveDraft(body: draft) }
                    if store.snapshot.drafts.count > count { draft = ""; composing = false }
                }.buttonStyle(.borderedProminent).controlSize(.large)
                    .disabled(draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }.padding(24)
        }.scrollDismissesKeyboard(.interactively)
    }
}

private struct HistoryScreen: View {
    @EnvironmentObject private var store: PreviewStore
    var body: some View {
        List {
            Section {
                Hint("Demo activity only · memory, not durable storage. Newest first. No remote receipt is implied.")
            }
            Section("On this device") {
                ForEach(Array(store.snapshot.events.reversed()), id: \.id) { event in
                    NavigationLink {
                        VStack(alignment: .leading, spacing: 24) {
                            Text(event.title).font(.title.bold())
                            Text(event.detail)
                            Hint("Local demo event. Not a remote delivery acknowledgement.")
                            Spacer()
                        }.padding(24).navigationTitle("Activity detail").navigationBarTitleDisplayMode(.inline)
                    } label: {
                        VStack(alignment: .leading, spacing: 8) {
                            Text(event.title).font(.headline)
                            Hint(event.detail)
                        }.padding(.vertical, 8)
                    }
                }
            }
        }.scrollContentBackground(.hidden)
    }
}

private struct SettingsScreen: View {
    @EnvironmentObject private var store: PreviewStore
    @Environment(\.dismiss) private var dismiss
    @State private var leaving = false
    var body: some View {
        NavigationStack {
            Form {
                Section("Experience") {
                    LabeledContent("Language", value: "English")
                    Text("Native text scaling and screen-reader labels are enabled.")
                }
                Section("Demo limitations") {
                    Text("No encryption, background radio or durable storage is active.")
                    Text("Drafts disappear when the app process ends. No real messages are sent.")
                }
                Section {
                    Button("Clear demo drafts", role: .destructive) { store.update { $0.clearDrafts() }; dismiss() }
                    Button("Leave demo group", role: .destructive) { leaving = true }
                }
            }.navigationTitle("Settings").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .cancellationAction) {
                    Button { dismiss() } label: { Label("Done", systemImage: "xmark") }
                } }
                .confirmationDialog("Leave this demo group? Your local demo drafts will be cleared. No peer will be notified.", isPresented: $leaving, titleVisibility: .visible) {
                    Button("Leave group", role: .destructive) { store.update { $0.leave() }; dismiss() }
                    Button("Keep exploring", role: .cancel) { }
                }
        }
    }
}

private struct DemoBanner: View {
    var body: some View {
        Text("DEMO MODE · Bluetooth not connected").font(.footnote.bold())
            .foregroundStyle(Color("AccentColor")).frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 24).padding(.vertical, 12).background(Color("AccentSoft"))
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
        VStack(alignment: .leading, spacing: 12, content: content)
            .frame(maxWidth: .infinity, alignment: .leading).padding(24)
            .foregroundStyle(dark ? Color.white : Color("Ink"))
            .background(dark ? Color("Night") : Color("Surface"), in: RoundedRectangle(cornerRadius: 28))
    }
}
