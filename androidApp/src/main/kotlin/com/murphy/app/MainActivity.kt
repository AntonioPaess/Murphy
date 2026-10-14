package com.murphy.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.murphy.core.preview.PreviewSession

private val Cobalt = Color(0xFF224BFA)
private val Canvas = Color(0xFFF6F7FA)
private val Graphite = Color(0xFF111217)
private val Muted = Color(0xFF646873)
private val Danger = Color(0xFFBF263B)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = Cobalt, background = Canvas, surface = Color.White,
                primaryContainer = Color(0xFFEAF0FF), onPrimaryContainer = Cobalt,
                secondary = Cobalt, onSecondary = Color.White,
                secondaryContainer = Color(0xFFEAF0FF), onSecondaryContainer = Cobalt,
                onSurface = Graphite, onBackground = Graphite, error = Danger,
            )) { MurphyApp() }
        }
    }
}

class PreviewViewModel : ViewModel() {
    private val session = PreviewSession()
    var snapshot by mutableStateOf(session.snapshot())
        private set

    fun change(action: PreviewSession.() -> Unit) {
        session.action()
        snapshot = session.snapshot()
    }
}

private enum class Destination(val title: String, val icon: ImageVector) {
    GROUP("Group", Icons.Default.Home), NETWORK("Network", Icons.Default.Share),
    CHAT("Chat", Icons.Default.Email), HISTORY("History", Icons.AutoMirrored.Filled.List),
}

@Composable
fun MurphyApp(model: PreviewViewModel = viewModel()) {
    val state = model.snapshot
    val keyboard = LocalSoftwareKeyboardController.current
    var destination by remember { mutableStateOf(Destination.GROUP) }
    var settings by remember { mutableStateOf(false) }
    var sosConfirmation by remember { mutableStateOf(false) }
    var leaveConfirmation by remember { mutableStateOf(false) }
    var explanation by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = Canvas,
        topBar = {
            Row(Modifier.windowInsetsPadding(WindowInsets.statusBars)
                .fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("murphy", style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold)
                if (state.groupName != null) IconButton(onClick = { settings = true }) {
                    Icon(Icons.Default.Settings, contentDescription = "Open settings")
                }
            }
        },
        bottomBar = {
            if (state.groupName != null) NavigationBar(containerColor = Color.White) {
                Destination.entries.forEach { item ->
                    NavigationBarItem(selected = destination == item,
                        modifier = Modifier.testTag("tab-${item.name}"),
                        onClick = { destination = item },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.title) })
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
            Text("DEMO MODE · Bluetooth not connected", color = Cobalt,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.fillMaxWidth().background(Color(0xFFEAF0FF))
                    .padding(horizontal = 24.dp, vertical = 12.dp))
            state.notice?.let { Text(it, color = Danger,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) }
            if (state.groupName == null) {
                Welcome(onOpen = { group, name ->
                    model.change { start(group, name) }
                    if (model.snapshot.groupName != null) keyboard?.hide()
                    destination = Destination.GROUP
                })
            } else when (destination) {
                Destination.GROUP -> Screen {
                    item { Heading("YOUR GROUP", state.groupName!!) }
                    item { PremiumCard {
                        Text("0", style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Bold, fontSize = 84.sp)
                        Text("direct peers", style = MaterialTheme.typography.titleMedium)
                        Hint("No radio connected. Open this demo to explore the experience.")
                    } }
                    item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { destination = Destination.NETWORK },
                            modifier = Modifier.weight(1f)) { Text("Network") }
                        Button(onClick = { destination = Destination.CHAT },
                            modifier = Modifier.weight(1f)) { Text("Chat") }
                    } }
                    item { PremiumCard {
                        Text("Stay close. Stay connected.", style = MaterialTheme.typography.titleLarge)
                        Hint("Murphy is being built for nearby, offline communication. " +
                            "Discovery, messaging and delivery confirmation are not active in this demo.")
                    } }
                    item { OutlinedButton(onClick = { sosConfirmation = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger)) {
                        Text("Preview SOS")
                    } }
                    item { Hint("Not an emergency service. Never rely on this demo for safety.") }
                }
                Destination.NETWORK -> Screen {
                    item { Heading("OFFLINE BY DESIGN", "Your network") }
                    item { PremiumCard(dark = true) {
                        Text("Just you, for now.", style = MaterialTheme.typography.headlineMedium)
                        Text("No connected peers", style = MaterialTheme.typography.titleMedium)
                        Text("This demo does not scan, advertise or connect over Bluetooth.")
                    } }
                    item { PremiumCard {
                        Text(state.displayName, style = MaterialTheme.typography.titleLarge)
                        Hint("This device · local demo identity")
                        OutlinedButton(onClick = { explanation = true }) { Text("How the mesh will work") }
                    } }
                    item { Hint("The mesh view is not a GPS map. Peer distance and location are not measured.") }
                }
                Destination.CHAT -> Chat(model)
                Destination.HISTORY -> Screen {
                    item { Heading("ON THIS DEVICE", "Activity") }
                    item { Hint("Demo activity only · memory, not durable storage. " +
                        "Newest first. No remote receipt is implied.") }
                    items(state.events.reversed(), key = { it.id }) { event -> PremiumCard {
                        Text(event.title, style = MaterialTheme.typography.titleMedium)
                        Hint(event.detail)
                    } }
                }
            }
        }
    }
    if (settings) AlertDialog(onDismissRequest = { settings = false },
        title = { Text("Settings") }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("English interface · native text scaling")
            Hint("UI demo only. No encryption, background radio or durable storage is active.")
            OutlinedButton(onClick = { model.change { clearDrafts() }; settings = false }) {
                Text("Clear demo drafts")
            }
            OutlinedButton(onClick = { settings = false; leaveConfirmation = true }) {
                Text("Leave demo group")
            }
        } }, confirmButton = { TextButton(onClick = { settings = false }) { Text("Done") } })
    if (leaveConfirmation) AlertDialog(onDismissRequest = { leaveConfirmation = false },
        title = { Text("Leave this demo group?") },
        text = { Text("Your local demo drafts will be cleared. No peer will be notified.") },
        confirmButton = { TextButton(onClick = {
            model.change { leave() }; leaveConfirmation = false
        }) { Text("Leave group") } },
        dismissButton = { TextButton(onClick = { leaveConfirmation = false }) { Text("Keep exploring") } })
    if (explanation) AlertDialog(onDismissRequest = { explanation = false },
        title = { Text("A mesh, not a central leader") },
        text = { Text("The shared routing core can relay packets between peers with hop limits " +
            "and duplicate protection. Native discovery and radio transport are still being built. " +
            "No live network is shown here.") },
        confirmButton = { TextButton(onClick = { explanation = false }) { Text("Got it") } })
    if (sosConfirmation) AlertDialog(onDismissRequest = { sosConfirmation = false },
        title = { Text("Preview an SOS?") },
        text = { Text("This opens a UI preview only. No alert is sent, no rescue service is " +
            "contacted and no location is shared. Real SOS will be limited to your group.") },
        confirmButton = { TextButton(onClick = {
            sosConfirmation = false; model.change { previewSos() }
        }) { Text("Open preview") } },
        dismissButton = { TextButton(onClick = { sosConfirmation = false }) { Text("Cancel") } })
    if (state.sosPreview) AlertDialog(onDismissRequest = { model.change { closeSosPreview() } },
        title = { Text("SOS preview · not sent", color = Danger) },
        text = { Text("No one has been alerted. This is not a working emergency channel. " +
            "No destination acknowledgement is available.") },
        confirmButton = { TextButton(onClick = { model.change { closeSosPreview() } }) { Text("Close preview") } })
}

@Composable
private fun Welcome(onOpen: (String, String) -> Unit) {
    var opening by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf("") }
    Screen {
        item { PremiumCard(dark = true) {
            Text("A little closer.\nEven offline.", style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            Text("A calmer way to stay with your people, beyond cellular coverage.")
            Text("Product preview · no Bluetooth messaging", color = Color(0xFFBFCBF8))
        } }
        if (!opening) {
            item { Button(onClick = { opening = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text("Explore the demo")
            } }
            item { Hint("No account. No permission request. No real messages are sent.") }
        } else {
            item { Heading("GET STARTED", "Your demo group") }
            item { OutlinedTextField(value = name, onValueChange = { name = it },
                label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(value = group, onValueChange = { group = it },
                label = { Text("Group name") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { Button(onClick = { onOpen(group, name) }, enabled = name.isNotBlank() && group.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Open demo group") } }
            item { TextButton(onClick = { opening = false }) { Text("Back to welcome") } }
            item { Hint("This creates a local demo, not a discoverable group. Bluetooth permissions " +
                "and nearby group discovery will arrive with the native radio integration.") }
        }
        item { Hint("Not for emergencies. Never rely on Murphy Demo for rescue or delivery.") }
    }
}

@Composable
private fun Chat(model: PreviewViewModel) {
    var draft by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    Screen {
        item { Heading("NOT TRANSMITTING", "Group chat") }
        item { Hint("No connected peers. Save an unsent demo draft to explore this screen. " +
            "Drafts are kept in memory only.") }
        items(model.snapshot.drafts, key = { it.id }) { message ->
            PremiumCard {
                Text(message.body, style = MaterialTheme.typography.bodyLarge)
                Text("Not sent · demo only", color = Cobalt, style = MaterialTheme.typography.labelLarge)
            }
        }
        item { OutlinedTextField(value = draft, onValueChange = { draft = it },
            label = { Text("Demo message") }, supportingText = { Text("${draft.length}/1,000 characters") },
            modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 6) }
        item { Button(onClick = {
            val count = model.snapshot.drafts.size
            model.change { saveDraft(draft) }
            if (model.snapshot.drafts.size > count) { draft = ""; keyboard?.hide() }
        }, enabled = draft.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("Save unsent draft")
        } }
    }
}

@Composable
private fun Screen(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp), content = content)
}

@Composable
private fun Heading(eyebrow: String, title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(eyebrow, color = Cobalt, style = MaterialTheme.typography.labelLarge)
        Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() })
    }
}

@Composable
private fun Hint(text: String) { Text(text, color = Muted, style = MaterialTheme.typography.bodyMedium) }

@Composable
private fun PremiumCard(dark: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = if (dark) Color(0xFF0C111D) else Color.White,
        contentColor = if (dark) Color.White else Graphite,
        shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
