package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Test

class VerseGeometryTest {

    @Test
    fun `awake Verse content is anchored at the centre of the display`() {
        // VerseFace and WatchPreviewView share this anchor. Keeping it at 50% prevents the
        // title-card fallback and the synced lyric reel from drifting below the centre together.
        assertEquals(.5f, FaceGeometry.Verse.BAND_CENTER, 0f)
    }

    @Test
    fun `running head keeps clear of a three-line lyric`() {
        assertEquals(.14f, FaceGeometry.Verse.HEADER_TOP, .0001f)
        assertEquals(.20f, FaceGeometry.Verse.HEADER_ARTIST_BASELINE, .0001f)
    }
}
