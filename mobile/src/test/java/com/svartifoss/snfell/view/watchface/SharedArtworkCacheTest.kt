package com.svartifoss.snfell.view.watchface

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the sharing the watch previews rely on: one picture per key however many previews ask, a
 * fresh one when the cached picture can no longer be drawn, and [SharedArtworkCache.holds] telling
 * a preview not to recycle a picture another preview may be drawing.
 */
class SharedArtworkCacheTest {

    private class Picture(val name: String) {
        var recycled = false
    }

    @Test
    fun everyPreviewAskingForOneKeyGetsTheSamePicture() {
        val cache = SharedArtworkCache<String, Picture>()
        var builds = 0

        val pictures = List(12) { cache.obtain("417 sharp") { builds++; Picture("sample") } }

        assertEquals("twelve gallery cards must share one sample cover", 1, builds)
        pictures.forEach { assertSame(pictures[0], it) }
    }

    @Test
    fun aDifferentKeyIsADifferentPicture() {
        val cache = SharedArtworkCache<String, Picture>()

        val sharp = cache.obtain("417 sharp") { Picture("sharp") }
        val blurred = cache.obtain("417 blur 24") { Picture("blurred") }

        assertNotSame(sharp, blurred)
    }

    @Test
    fun aPictureThatCanNoLongerBeDrawnIsBuiltAgain() {
        val cache = SharedArtworkCache<String, Picture> { !it.recycled }
        val first = cache.obtain("key") { Picture("first") }
        first.recycled = true

        val second = cache.obtain("key") { Picture("second") }

        assertEquals("second", second.name)
    }

    @Test
    fun onlyPicturesTheCacheHandedOutAreProtectedFromRecycling() {
        val cache = SharedArtworkCache<String, Picture>()
        val shared = cache.obtain("key") { Picture("shared") }

        assertTrue(cache.holds(shared))
        assertFalse(cache.holds(Picture("a preview's own copy")))
    }
}
