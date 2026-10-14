package com.murphy.core.preview

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PreviewSessionTest {
    @Test fun validatesNamesAndDoesNotReplaceAnOpenGroup() {
        val session = PreviewSession()
        assertFalse(session.start(" ", "Alex"))
        assertFalse(session.start("Trail", "x".repeat(41)))
        assertNotNull(session.snapshot().notice)
        assertTrue(session.start(" Trail ", " Alex "))
        assertNull(session.snapshot().notice)
        assertFalse(session.start("Other", "Sam"))
        assertEquals("Trail", session.snapshot().groupName)
        assertEquals("Alex", session.snapshot().displayName)
    }

    @Test fun userContentIsPreservedRatherThanTranslatedOrSilentlyTruncated() {
        val session = PreviewSession()
        assertTrue(session.start("Trilha do Antônio", "Antônio"))
        val text = "Vamos pela trilha! 🏕️"
        assertTrue(session.saveDraft(text))
        assertEquals(text, session.snapshot().drafts.single().body)
        assertEquals("Trilha do Antônio", session.snapshot().groupName)
        assertFalse(session.saveDraft("x".repeat(1001)))
        assertEquals(text, session.snapshot().drafts.single().body)
    }

    @Test fun savesOnlyBoundedUnsentDraftsInsideAnOpenGroup() {
        val session = PreviewSession()
        assertFalse(session.saveDraft("Hello"))
        session.start("Trail", "Alex")
        assertFalse(session.saveDraft(" "))
        assertFalse(session.saveDraft("x".repeat(1001)))
        repeat(50) { assertTrue(session.saveDraft(" Hello $it ")) }
        assertFalse(session.saveDraft("Too many"))
        val snapshot = session.snapshot()
        assertEquals(50, snapshot.drafts.size)
        assertEquals("Hello 0", snapshot.drafts.first().body)
        session.clearDrafts()
        assertEquals(50, snapshot.drafts.size, "Snapshots must not mutate behind the UI")
        assertTrue(session.snapshot().drafts.isEmpty())
        assertTrue(session.saveDraft("New"))
    }

    @Test fun sosIsExplicitlyAPreviewAndNeverCreatesAMessage() {
        val session = PreviewSession()
        assertFalse(session.previewSos())
        session.start("Trail", "Alex")
        assertTrue(session.previewSos())
        assertTrue(session.snapshot().sosPreview)
        assertTrue(session.snapshot().drafts.isEmpty())
        assertTrue(session.snapshot().events.last().detail.startsWith("No alert sent."))
        session.closeSosPreview()
        assertFalse(session.snapshot().sosPreview)
        val count = session.snapshot().events.size
        session.closeSosPreview()
        assertEquals(count, session.snapshot().events.size)
    }

    @Test fun leavingClearsDraftsIdentityAndPreviewButHistoryStaysBounded() {
        val session = PreviewSession()
        session.start("Trail", "Alex")
        session.saveDraft("Hello")
        session.previewSos()
        session.leave()
        assertNull(session.snapshot().groupName)
        assertEquals("", session.snapshot().displayName)
        assertFalse(session.snapshot().sosPreview)
        assertTrue(session.snapshot().drafts.isEmpty())
        repeat(120) { session.clearDrafts() }
        assertEquals(100, session.snapshot().events.size)
        assertTrue(PreviewSession().snapshot().events.isEmpty(), "No persistence is implied")
    }
}
