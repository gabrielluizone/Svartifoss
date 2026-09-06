package com.svartifoss.snfell.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionComparisonTest {
    @Test
    fun newerPatchVersion() {
        assertTrue(AppVersionComparison.isNewer("2.1.2", "2.1.1"))
    }

    @Test
    fun newerMinorVersionWithFewerSegments() {
        assertTrue(AppVersionComparison.isNewer("2.2", "2.1.1"))
    }

    @Test
    fun olderVersion() {
        assertFalse(AppVersionComparison.isNewer("2.1", "2.1.1"))
        assertFalse(AppVersionComparison.isNewer("1.13", "2.1.1"))
    }

    @Test
    fun equalVersions() {
        assertFalse(AppVersionComparison.isNewer("2.1.1", "2.1.1"))
    }

    @Test
    fun leadingVPrefixIsIgnored() {
        assertTrue(AppVersionComparison.isNewer("v2.2", "2.1.1"))
        assertFalse(AppVersionComparison.isNewer("v1.13", "2.1.1"))
    }

    @Test
    fun prereleaseSuffixComparesOnNumericPart() {
        assertTrue(AppVersionComparison.isNewer("2.2-rc1", "2.1.1"))
        assertFalse(AppVersionComparison.isNewer("2.2-rc1", "2.2"))
    }

    /**
     * The regression this pins: both tags reduce to "3.1" if the suffix is dropped, so the app
     * reported "you're up to date" to every 3.1-beta1 user while displaying 3.1-beta2 as the
     * latest release.
     */
    @Test
    fun laterBetaOfTheSameVersionIsNewer() {
        assertTrue(AppVersionComparison.isNewer("3.1-beta2", "3.1-beta1"))
        assertFalse(AppVersionComparison.isNewer("3.1-beta1", "3.1-beta2"))
        assertFalse(AppVersionComparison.isNewer("3.1-beta2", "3.1-beta2"))
    }

    /** Digits inside an identifier compare as numbers - a string compare puts "beta10" first. */
    @Test
    fun betaNumberComparesNumericallyNotAlphabetically() {
        assertTrue(AppVersionComparison.isNewer("3.1-beta10", "3.1-beta9"))
        assertFalse(AppVersionComparison.isNewer("3.1-beta9", "3.1-beta10"))
    }

    /** A beta tester must still be offered the finished release of the same version. */
    @Test
    fun finalReleaseSupersedesItsOwnPrereleases() {
        assertTrue(AppVersionComparison.isNewer("3.1", "3.1-beta2"))
        assertFalse(AppVersionComparison.isNewer("3.1-beta2", "3.1"))
    }

    @Test
    fun prereleaseStillLosesToAHigherNumericVersion() {
        assertTrue(AppVersionComparison.isNewer("3.2-beta1", "3.1"))
        assertTrue(AppVersionComparison.isNewer("3.1-beta1", "3.0"))
        assertFalse(AppVersionComparison.isNewer("3.1-beta1", "3.1.1"))
    }

    @Test
    fun prereleaseStagesAreOrdered() {
        assertTrue(AppVersionComparison.isNewer("3.1-beta1", "3.1-alpha3"))
        assertTrue(AppVersionComparison.isNewer("3.1-rc1", "3.1-beta7"))
        assertFalse(AppVersionComparison.isNewer("3.1-alpha1", "3.1-rc1"))
    }

    @Test
    fun taggedPrereleaseWithVPrefixStillCompares() {
        assertTrue(AppVersionComparison.isNewer("v3.1-beta2", "3.1-beta1"))
    }

    /** Missing trailing segments read as zero, so these name the same release. */
    @Test
    fun trailingZeroSegmentsAreEquivalent() {
        assertFalse(AppVersionComparison.isNewer("2.2.0", "2.2"))
        assertFalse(AppVersionComparison.isNewer("2.2", "2.2.0"))
    }

    @Test
    fun garbageTagIsNeverNewer() {
        assertFalse(AppVersionComparison.isNewer("latest", "2.1.1"))
        assertFalse(AppVersionComparison.isNewer("", "2.1.1"))
    }
}
