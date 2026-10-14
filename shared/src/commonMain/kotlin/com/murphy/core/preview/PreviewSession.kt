package com.murphy.core.preview

/** UI-only state. Never connects to a radio, sends a packet or claims delivery. */
public class PreviewSession {
    private var groupName: String? = null
    private var displayName: String = ""
    private val drafts: MutableList<PreviewDraft> = mutableListOf()
    private val events: MutableList<PreviewEvent> = mutableListOf()
    private var notice: String? = null
    private var nextId: Int = 1
    private var sosPreview: Boolean = false

    public fun snapshot(): PreviewSnapshot = PreviewSnapshot(
        groupName, displayName, drafts.toList(), events.toList(), sosPreview, notice,
    )

    public fun start(groupName: String, displayName: String): Boolean {
        val group = groupName.trim()
        val name = displayName.trim()
        if (group.isEmpty() || name.isEmpty() || group.length > 40 || name.length > 40) {
            notice = "Enter a name and group name, each between 1 and 40 characters."
            return false
        }
        if (this.groupName != null) {
            notice = "Leave the current demo group before opening another."
            return false
        }
        this.groupName = group
        this.displayName = name
        notice = null
        record("Demo group opened", "No Bluetooth discovery or connection has started.")
        return true
    }

    public fun saveDraft(body: String): Boolean {
        if (groupName == null) {
            notice = "Open a demo group first."
            return false
        }
        val text = body.trim()
        if (text.isEmpty() || text.length > 1000) {
            notice = "Write a message between 1 and 1,000 characters."
            return false
        }
        if (drafts.size >= 50) {
            notice = "The demo holds up to 50 drafts. Clear drafts in Settings to continue."
            return false
        }
        drafts.add(PreviewDraft(nextId++, text))
        notice = null
        record("Draft saved in memory", "Not sent. Drafts disappear when this app process ends.")
        return true
    }

    public fun previewSos(): Boolean {
        if (groupName == null) return false
        sosPreview = true
        notice = null
        record("SOS preview opened", "No alert sent. No rescue service contacted or location shared.")
        return true
    }

    public fun closeSosPreview() {
        if (!sosPreview) return
        sosPreview = false
        record("SOS preview closed", "This was a UI demonstration, not a transmitted alert.")
    }

    public fun clearDrafts() {
        drafts.clear()
        notice = null
        record("Demo drafts cleared", "Only local in-memory demo drafts were removed.")
    }

    public fun leave() {
        groupName = null
        displayName = ""
        drafts.clear()
        sosPreview = false
        notice = null
        record("Demo group closed", "Local drafts cleared. No radio session was active.")
    }

    private fun record(title: String, detail: String) {
        events.add(PreviewEvent(nextId++, title, detail))
        if (events.size > 100) events.removeAt(0)
    }
}

public data class PreviewSnapshot(
    public val groupName: String?,
    public val displayName: String,
    public val drafts: List<PreviewDraft>,
    public val events: List<PreviewEvent>,
    public val sosPreview: Boolean,
    public val notice: String?,
)

public data class PreviewDraft(public val id: Int, public val body: String)
public data class PreviewEvent(public val id: Int, public val title: String, public val detail: String)
