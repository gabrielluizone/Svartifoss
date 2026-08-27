package com.svartifoss.snfell.view.watchface.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityThemeRollingQuotaTest {

    @Test
    fun `a complete three-submission history reports its remaining rolling wait`() {
        val firstSubmission = 1_700_000_000_000L

        assertTrue(isRollingSubmissionLimitReached(
                quotaSchemaVersion = 2,
                recentSubmissionCount = 3,
                recentSubmissionFirstAtMillis = firstSubmission,
                nowMillis = firstSubmission + COMMUNITY_THEME_SUBMISSION_WINDOW_MILLIS - 1))
        assertFalse(isRollingSubmissionLimitReached(
                quotaSchemaVersion = 2,
                recentSubmissionCount = 3,
                recentSubmissionFirstAtMillis = firstSubmission,
                nowMillis = firstSubmission + COMMUNITY_THEME_SUBMISSION_WINDOW_MILLIS))
    }

    @Test
    fun `legacy incomplete and malformed histories are not mislabeled as a quota limit`() {
        val firstSubmission = 1_700_000_000_000L

        assertFalse(isRollingSubmissionLimitReached(1, 3, firstSubmission, firstSubmission))
        assertFalse(isRollingSubmissionLimitReached(2, 2, firstSubmission, firstSubmission))
        assertFalse(isRollingSubmissionLimitReached(2, 3, null, firstSubmission))
    }
}
