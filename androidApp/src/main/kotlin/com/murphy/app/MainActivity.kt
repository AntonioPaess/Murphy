package com.murphy.app

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import com.murphy.core.radio.RadioSession
import java.text.Normalizer
import java.util.UUID

private val Cobalt = Color(0xFF224BFA)
private val Canvas = Color(0xFFF6F7FA)
private val Graphite = Color(0xFF111217)
private val Muted = Color(0xFF646873)
private val Danger = Color(0xFFBF263B)

class MainActivity : ComponentActivity() {
    private lateinit var model: RadioViewModel
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (requiredPermissions().all { name -> ContextCompat.checkSelfPermission(this, name) == PackageManager.PERMISSION_GRANTED }) model.activate()
        else model.permissionDenied()
    }
    private fun requiredPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= 31)
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    private fun requestRadio() {
        model.wantsRadio = true
        if (requiredPermissions().all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) model.activate()
        else permissions.launch(requiredPermissions())
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = ViewModelProvider(this)[RadioViewModel::class.java]
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT))
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Cobalt, background = Canvas, surface = Color.White,
                primaryContainer = Color(0xFFEAF0FF), onPrimaryContainer = Cobalt, secondary = Cobalt, onSecondary = Color.White,
                secondaryContainer = Color(0xFFEAF0FF), onSecondaryContainer = Cobalt, onSurface = Graphite, onBackground = Graphite, error = Danger)) {
                MurphyApp(model, ::requestRadio) { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
            }
        }
    }
    override fun onStart() { super.onStart(); if (::model.isInitialized) { model.foreground = true; if (model.wantsRadio) requestRadio() } }
    override fun onStop() { if (::model.isInitialized) model.background(); super.onStop() }
}

class RadioViewModel(app: Application) : AndroidViewModel(app) {
    private val session = RadioSession(UUID.randomUUID().toString())
    var snapshot by mutableStateOf(session.snapshot())
        private set
    var foreground = false
    var wantsRadio = false
    private val radio = AndroidRadio(app, session) { refresh() }
    private fun refresh() { snapshot = session.snapshot() }
    fun create(group: String, name: String): Boolean = join(UUID.randomUUID().toString(), group, name)
    fun join(id: String, group: String, name: String): Boolean {
        radio.stop()
        val accepted = session.start(id, Normalizer.normalize(group, Normalizer.Form.NFC), Normalizer.normalize(name, Normalizer.Form.NFC))
        refresh(); return accepted
    }
    fun activate() { if (foreground) { radio.start(); refresh() } }
    fun permissionDenied() { session.status("Nearby devices permission is required · try again"); refresh() }
    fun background() { foreground = false; radio.stop(); session.status("Bluetooth paused · keep Murphy open to communicate"); refresh() }
    fun send(text: String): Boolean {
        val id = UUID.randomUUID().toString()
        val batch = session.send(text, id, SystemClock.elapsedRealtime())
        refresh(); radio.sendAll(batch)
        return snapshot.messages.any { it.id == id }
    }
    fun leave() { wantsRadio = false; radio.stop(); session.leave(); session.status("Bluetooth not started"); refresh() }
    override fun onCleared() { radio.stop() }
}

private enum class Destination(val title: String, val icon: ImageVector) {
    GROUP("Group", Icons.Default.Home), NETWORK("Network", Icons.Default.Share),
    CHAT("Chat", Icons.Default.Email), HISTORY("History", Icons.AutoMirrored.Filled.List),
}

@Composable
fun MurphyApp(model: RadioViewModel, requestRadio: () -> Unit, bluetoothSettings: () -> Unit) {
    val state = model.snapshot
    val keyboard = LocalSoftwareKeyboardController.current
    var destination by remember { mutableStateOf(Destination.GROUP) }
    var settings by remember { mutableStateOf(false) }
    var leaveConfirmation by remember { mutableStateOf(false) }
    var sosInformation by remember { mutableStateOf(false) }
    Scaffold(containerColor = Canvas, topBar = {
        Row(Modifier.windowInsetsPadding(WindowInsets.statusBars).fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("murphy", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            if (state.group != null) IconButton(onClick = { settings = true }) { Icon(Icons.Default.Settings, "Open settings") }
        }
    }, bottomBar = {
        if (state.group != null) NavigationBar(containerColor = Color.White) {
            Destination.entries.forEach { item -> NavigationBarItem(selected = destination == item,
                modifier = Modifier.testTag("tab-${item.name}"), onClick = { destination = item },
                icon = { Icon(item.icon, null) }, label = { Text(item.title) }) }
        }
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
            Text(state.radioStatus, color = Cobalt, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.fillMaxWidth().background(Color(0xFFEAF0FF)).padding(horizontal = 24.dp, vertical = 12.dp))
            state.notice?.let { Text(it, color = Danger, modifier = Modifier.padding(24.dp)) }
            if (state.group == null) Welcome(model, requestRadio) { keyboard?.hide(); destination = Destination.GROUP }
            else when (destination) {
                Destination.GROUP -> Screen {
                    item { Heading("YOUR GROUP", state.group!!.name) }
                    item { PremiumCard {
                        Text(state.peers.size.toString(), fontSize = 84.sp, fontWeight = FontWeight.Bold)
                        Text("direct peers", style = MaterialTheme.typography.titleMedium)
                        Hint("Connected Bluetooth links. This is not a global reach or delivery count.")
                    } }
                    item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton({ destination = Destination.NETWORK }, Modifier.weight(1f)) { Text("Network") }
                        Button({ destination = Destination.CHAT }, Modifier.weight(1f)) { Text("Chat") }
                    } }
                    item { PremiumCard {
                        Text("Stay close. Stay connected.", style = MaterialTheme.typography.titleLarge)
                        Hint("This is an open, unencrypted group. Keep Murphy in the foreground. Do not send sensitive information or rely on it for safety.")
                        Hint("Group ID · ${state.group!!.id.take(8)}")
                    } }
                    item { Button(requestRadio, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Scan nearby") } }
                    item { TextButton(bluetoothSettings) { Text("Bluetooth settings") } }
                    item { OutlinedButton({ sosInformation = true }, Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger)) { Text("SOS not available yet") } }
                }
                Destination.NETWORK -> Screen {
                    item { Heading("NEARBY BLUETOOTH", "Your network") }
                    item { PremiumCard(dark = true) {
                        Text(if (state.peers.isEmpty()) "No connected peers" else "Connected nearby", style = MaterialTheme.typography.headlineMedium)
                        Text("Peers can relay group messages. No leader is required.")
                    } }
                    item { PremiumCard { Text(state.displayName, style = MaterialTheme.typography.titleLarge); Hint("This device · Bluetooth group member") } }
                    items(state.peers, key = { it.id }) { peer -> PremiumCard {
                        Text(peer.name, style = MaterialTheme.typography.titleLarge); Hint("Connected peer · ${peer.id.take(8)}")
                    } }
                    item { Hint("Bluetooth does not measure a GPS position or distance here. Peer identity is not authenticated yet.") }
                    item { Button(requestRadio, Modifier.fillMaxWidth()) { Text("Scan nearby") } }
                }
                Destination.CHAT -> Chat(model)
                Destination.HISTORY -> Screen {
                    item { Heading("ON THIS DEVICE", "Activity") }
                    item { Hint("Real local activity · memory only. Not a destination acknowledgement.") }
                    items(state.events.reversed(), key = { it.id }) { event -> PremiumCard {
                        Text(event.title, style = MaterialTheme.typography.titleMedium); Hint(event.detail)
                    } }
                }
            }
        }
    }
    if (settings) AlertDialog(onDismissRequest = { settings = false }, title = { Text("Settings") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("English interface · native text scaling")
            Hint("Foreground Bluetooth only. No encryption or persistent history yet. Restarting the process loses this session.")
            OutlinedButton({ settings = false; leaveConfirmation = true }) { Text("Leave group") }
        } }, confirmButton = { TextButton({ settings = false }) { Text("Done") } })
    if (leaveConfirmation) AlertDialog(onDismissRequest = { leaveConfirmation = false }, title = { Text("Leave this group?") },
        text = { Text("Bluetooth links close and local messages clear. No rescue service is contacted.") },
        confirmButton = { TextButton({ model.leave(); leaveConfirmation = false }) { Text("Leave group") } },
        dismissButton = { TextButton({ leaveConfirmation = false }) { Text("Stay") } })
    if (sosInformation) AlertDialog(onDismissRequest = { sosInformation = false }, title = { Text("SOS is not available yet") },
        text = { Text("This build cannot send emergency alerts. It does not contact rescue services or share your location. Do not rely on Murphy for safety.") },
        confirmButton = { TextButton({ sosInformation = false }) { Text("Got it") } })
}

@Composable
private fun Welcome(model: RadioViewModel, requestRadio: () -> Unit, opened: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf("") }
    Screen {
        item { PremiumCard(dark = true) {
            Text("A little closer.\nEven offline.", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() })
            Text("Communicate with your nearby group over Bluetooth, without a server.")
            Text("Development build · foreground only · not encrypted", color = Color(0xFFBFCBF8))
        } }
        item { OutlinedTextField(name, { name = it }, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(group, { group = it }, label = { Text("Group name") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        item { Button({
            if (model.create(group, name)) { opened(); requestRadio() }
        }, Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = name.isNotBlank() && group.isNotBlank()) { Text("Create group") } }
        item { OutlinedButton(requestRadio, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Find nearby groups") } }
        items(model.snapshot.nearbyGroups, key = { it.id }) { found -> PremiumCard {
            Text(found.name, style = MaterialTheme.typography.titleLarge); Hint("Open group · ${found.id.take(8)}")
            Button({
                if (model.join(found.id, found.name, name)) { opened(); requestRadio() }
            }, enabled = name.isNotBlank()) { Text("Join group") }
        } }
        item { Hint("Nearby devices permission enables discovery, advertising and messaging. Older Android versions also require location permission for BLE scanning; Murphy does not derive your location.") }
        item { Hint("Open groups are not private. Not for emergencies. No delivery guarantee.") }
    }
}

@Composable
private fun Chat(model: RadioViewModel) {
    var draft by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    Screen {
        item { Heading("GROUP CHAT", "Messages") }
        item { Hint("Bluetooth acceptance is not destination receipt. Messages and history are in memory only.") }
        if (model.snapshot.peers.isEmpty()) item { Hint("No connected peers. Your message will not be transmitted.") }
        items(model.snapshot.messages, key = { it.id }) { message -> PremiumCard {
            Text(if (message.own) "You" else message.sender, style = MaterialTheme.typography.labelLarge, color = Cobalt)
            Text(message.body, style = MaterialTheme.typography.bodyLarge)
            Text(message.status, style = MaterialTheme.typography.labelLarge, color = Muted)
        } }
        item { OutlinedTextField(draft, { draft = it }, label = { Text("Message") },
            supportingText = { Text("${draft.length}/1,000 characters") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 6) }
        item { Button({
            if (model.send(draft)) { draft = ""; keyboard?.hide() }
        }, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = draft.isNotBlank()) { Text("Send message") } }
    }
}

@Composable
private fun Screen(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp), content = content)
}
@Composable
private fun Heading(eyebrow: String, title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(eyebrow, color = Cobalt, style = MaterialTheme.typography.labelLarge)
        Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
    }
}
@Composable private fun Hint(text: String) { Text(text, color = Muted, style = MaterialTheme.typography.bodyMedium) }
@Composable
private fun PremiumCard(dark: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = if (dark) Color(0xFF0C111D) else Color.White, contentColor = if (dark) Color.White else Graphite,
        shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
