package com.svartifoss.snfell.watch.view.facepicker

import com.svartifoss.snfell.common.ArchivedFaces
import com.svartifoss.snfell.common.ThemeAppearance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The on-watch picker derives its rows from [ThemeAppearance.ALLOWED_BASE_FACES] but names them
 * from its own label map, and a key with no label is *dropped* - which is the right thing to draw
 * and the wrong thing to ship. A face registered everywhere else and forgotten here is invisible
 * on the wrist with nothing anywhere reporting it: exactly how "artist" (and, once someone was on
 * it, the archived "matejdro") became unselectable from the watch while the phone offered both.
 *
 * So the omission is pinned here rather than left to be noticed on a device.
 */
class WatchFaceCatalogTest {

    @Test
    fun `every registered face has a label`() {
        val unnamed = ThemeAppearance.ALLOWED_BASE_FACES.filter { WatchFaceCatalog.labelFor(it) == null }
        assertEquals(
                "Faces registered in ThemeAppearance.ALLOWED_BASE_FACES with no entry in " +
                        "WatchFaceCatalog.LABELS - they are silently missing from the on-watch " +
                        "picker. Add a face_name_<key> string in wear/src/main/res/values/strings.xml " +
                        "and map it in WatchFaceCatalog.",
                emptyList<String>(),
                unnamed)
    }

    @Test
    fun `the picker offers every current face`() {
        val offered = WatchFaceCatalog.builtInOptions(activeFace = "classic").map { it.key }
        val expected = ThemeAppearance.ALLOWED_BASE_FACES.filterNot { it in ArchivedFaces.KEYS }
        assertEquals(expected, offered)
    }

    @Test
    fun `an archived face is offered only while it is the one in use`() {
        val archived = ArchivedFaces.KEYS.first()

        assertTrue(archived !in WatchFaceCatalog.builtInOptions(activeFace = "classic").map { it.key })
        assertTrue(archived in WatchFaceCatalog.builtInOptions(activeFace = archived).map { it.key })
    }

    @Test
    fun `an option renders with the face it names`() {
        WatchFaceCatalog.builtInOptions(activeFace = "classic").forEach {
            assertEquals(it.key, it.baseFace)
        }
    }
}
