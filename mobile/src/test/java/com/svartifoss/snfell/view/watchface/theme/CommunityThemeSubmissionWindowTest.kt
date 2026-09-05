package com.svartifoss.snfell.view.watchface.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityThemeSubmissionWindowTest {

    private val windowStart = 1_700_000_000_000L

    @Test
    fun `a full window reports the limit until it expires`() {
        assertTrue(isSubmissionWindowFull(
                quotaSchemaVersion = 3,
                windowSubmissionCount = COMMUNITY_THEME_SUBMISSIONS_PER_WINDOW,
                windowStartedAtMillis = windowStart,
                nowMillis = windowStart + COMMUNITY_THEME_SUBMISSION_WINDOW_MILLIS - 1))
        // The boundary is the whole point of a fixed window: the allowance returns all at once.
        assertFalse(isSubmissionWindowFull(
                quotaSchemaVersion = 3,
                windowSubmissionCount = COMMUNITY_THEME_SUBMISSIONS_PER_WINDOW,
                windowStartedAtMillis = windowStart,
                nowMillis = windowStart + COMMUNITY_THEME_SUBMISSION_WINDOW_MILLIS))
    }

    @Test
    fun `a window with room left never reports the limit`() {
        for (used in 1 until COMMUNITY_THEME_SUBMISSIONS_PER_WINDOW) {
            assertFalse(
                    "$used of $COMMUNITY_THEME_SUBMISSIONS_PER_WINDOW should not be full",
                    isSubmissionWindowFull(3, used, windowStart, windowStart))
        }
    }

    @Test
    fun `a count past the allowance still reports full rather than wrapping`() {
        // Defensive: the rules refuse to write one, so this can only arrive from a repaired or
        // hand-edited document -- and reading it as "not full" would hand back the whole window.
        assertTrue(isSubmissionWindowFull(
                3, COMMUNITY_THEME_SUBMISSIONS_PER_WINDOW + 5, windowStart, windowStart))
    }

    @Test
    fun `legacy and malformed documents are not mislabeled as a quota limit`() {
        // They fall through to the fail-closed rules path instead, which is where a v1 or v2
        // record is migrated. Reporting a limit here would refuse a submission the rules allow.
        assertFalse(isSubmissionWindowFull(1, 3, windowStart, windowStart))
        assertFalse(isSubmissionWindowFull(2, 3, windowStart, windowStart))
        assertFalse(isSubmissionWindowFull(3, null, windowStart, windowStart))
        assertFalse(isSubmissionWindowFull(3, COMMUNITY_THEME_SUBMISSIONS_PER_WINDOW, null, windowStart))
        assertFalse(isSubmissionWindowFull(null, COMMUNITY_THEME_SUBMISSIONS_PER_WINDOW, windowStart, windowStart))
    }

    @Test
    fun `the allowance and the window are the ones the rules enforce`() {
        assertEquals(10, COMMUNITY_THEME_SUBMISSIONS_PER_WINDOW)
        assertEquals(24L * 60L * 60L * 1_000L, COMMUNITY_THEME_SUBMISSION_WINDOW_MILLIS)
    }
}
