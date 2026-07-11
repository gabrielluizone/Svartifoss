package com.svartifoss.snfell.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerVersionTest {
    @Test
    fun newerPatchVersion() {
        assertTrue(UpdateChecker.isNewer("2.1.2", "2.1.1"))
    }

    @Test
    fun newerMinorVersionWithFewerSegments() {
        assertTrue(UpdateChecker.isNewer("2.2", "2.1.1"))
    }

    @Test
    fun olderVersion() {
        assertFalse(UpdateChecker.isNewer("2.1", "2.1.1"))
        assertFalse(UpdateChecker.isNewer("1.13", "2.1.1"))
    }

    @Test
    fun equalVersions() {
        assertFalse(UpdateChecker.isNewer("2.1.1", "2.1.1"))
    }

    @Test
    fun leadingVPrefixIsIgnored() {
        assertTrue(UpdateChecker.isNewer("v2.2", "2.1.1"))
        assertFalse(UpdateChecker.isNewer("v1.13", "2.1.1"))
    }

    @Test
    fun prereleaseSuffixComparesOnNumericPart() {
        assertTrue(UpdateChecker.isNewer("2.2-rc1", "2.1.1"))
        assertFalse(UpdateChecker.isNewer("2.2-rc1", "2.2"))
    }

    @Test
    fun garbageTagIsNeverNewer() {
        assertFalse(UpdateChecker.isNewer("latest", "2.1.1"))
        assertFalse(UpdateChecker.isNewer("", "2.1.1"))
    }
}
