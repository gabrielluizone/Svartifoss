package com.svartifoss.snfell.common

import org.junit.Assert.*
import org.junit.Test

class ShortcutRequestTrackerTest {
    @Test fun oldReplyCannotConsumeNewRequestEvenForTheSameLink() {
        val tracker = ShortcutRequestTracker()
        tracker.begin("first", "same-link", true)
        tracker.begin("second", "same-link", true)
        assertNull(tracker.take("first"))
        assertNull(tracker.take(null))
        assertEquals("second", tracker.take("second")?.id)
        assertNull(tracker.take("second"))
    }

    @Test fun timerAndVerdictCanConsumeARequestOnlyOnce() {
        val tracker = ShortcutRequestTracker()
        tracker.begin("request", "link", true)
        assertNotNull(tracker.take("request"))
        assertNull(tracker.take("request"))
    }

    @Test fun olderPhoneCanAnswerASingleUnambiguousPlaybackRequest() {
        val tracker = ShortcutRequestTracker()
        tracker.begin("request", "link", true)
        assertEquals("request", tracker.take(null)?.id)
    }

    @Test fun openOnlyCannotBeCompletedByALegacyPlaybackReply() {
        val tracker = ShortcutRequestTracker()
        tracker.begin("open", "link", false)
        assertNull(tracker.take(null))
        assertEquals("open", tracker.take("open")?.id)
    }
}
