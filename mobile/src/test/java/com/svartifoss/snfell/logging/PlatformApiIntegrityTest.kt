package com.svartifoss.snfell.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformApiIntegrityTest {

    @Test
    fun aDeviceBelowTheProbedLevelIsNotAccusedOfAnything() {
        val report = PlatformApiIntegrity.resolve(33, emptyList())

        assertTrue(report.consistent)
        assertFalse(report.probed)
        assertEquals(PlatformApiIntegrity.NOT_PROBED, report.summary)
    }

    @Test
    fun belowTheProbedLevelAnAbsentMemberIsNotEvidence() {
        // Nothing asks a pre-34 framework for an API 34 member, so a label arriving here is a
        // caller mistake rather than a lying device, and must not be reported as one.
        val report = PlatformApiIntegrity.resolve(28, listOf("TextView.setLineHeight"))

        assertTrue(report.consistent)
        assertFalse(report.probed)
        assertEquals(PlatformApiIntegrity.NOT_PROBED, report.summary)
    }

    @Test
    fun aFrameworkThatHasWhatItClaimsReportsOk() {
        val report = PlatformApiIntegrity.resolve(34, emptyList())

        assertTrue(report.consistent)
        assertTrue(report.probed)
        assertEquals(PlatformApiIntegrity.CONSISTENT, report.summary)
    }

    @Test
    fun aMissingMemberIsNamedInTheSummary() {
        val report = PlatformApiIntegrity.resolve(
                34, listOf("android.credentials.CredentialManager"))

        assertFalse(report.consistent)
        assertTrue(report.probed)
        assertEquals("missing-api-34:android.credentials.CredentialManager", report.summary)
    }

    @Test
    fun aLaterPlatformIsStillHeldToTheProbedLevel() {
        // The observed device reported 34; a spoof reporting 36 over the same framework is the
        // same fault and must not fall through as consistent.
        val report = PlatformApiIntegrity.resolve(36, listOf("TextView.setLineHeight"))

        assertFalse(report.consistent)
        assertEquals("missing-api-34:TextView.setLineHeight", report.summary)
    }

    @Test
    fun oneBrokenPlatformYieldsOneValueWhateverTheProbeOrder() {
        val first = PlatformApiIntegrity.resolve(
                34, listOf("TextView.setLineHeight", "AccessibilityAction.ACTION_SCROLL_IN_DIRECTION"))
        val second = PlatformApiIntegrity.resolve(
                34, listOf("AccessibilityAction.ACTION_SCROLL_IN_DIRECTION", "TextView.setLineHeight"))

        assertEquals(first.summary, second.summary)
        assertEquals(
                "missing-api-34:AccessibilityAction.ACTION_SCROLL_IN_DIRECTION,TextView.setLineHeight",
                first.summary)
    }

    @Test
    fun repeatedAndBlankLabelsDoNotSplitTheGrouping() {
        val report = PlatformApiIntegrity.resolve(
                34, listOf("TextView.setLineHeight", "", "TextView.setLineHeight", "   "))

        assertEquals("missing-api-34:TextView.setLineHeight", report.summary)
    }

    @Test
    fun theThreeObservedAbsencesReportTogether() {
        val report = PlatformApiIntegrity.resolve(34, listOf(
                "AccessibilityAction.ACTION_SCROLL_IN_DIRECTION",
                "TextView.setLineHeight",
                "android.credentials.CredentialManager"))

        assertFalse(report.consistent)
        assertEquals(
                "missing-api-34:AccessibilityAction.ACTION_SCROLL_IN_DIRECTION," +
                        "TextView.setLineHeight,android.credentials.CredentialManager",
                report.summary)
    }
}
