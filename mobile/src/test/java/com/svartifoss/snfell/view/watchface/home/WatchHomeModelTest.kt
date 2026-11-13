package com.svartifoss.snfell.view.watchface.home

import com.svartifoss.snfell.common.ThemeAppearance
import com.svartifoss.snfell.view.watchface.AodEditorModel
import com.svartifoss.snfell.view.watchface.BackgroundEditorModel
import com.svartifoss.snfell.view.watchface.ColorEditorModel
import com.svartifoss.snfell.view.watchface.MiniButtonEditorModel
import com.svartifoss.snfell.view.watchface.PanelEditorModel
import com.svartifoss.snfell.view.watchface.PlayerEditorModel
import com.svartifoss.snfell.view.watchface.TypographyEditorModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Home's quick adjustments are views over settings that live on the seven pages, never settings of
 * their own: every key exists, belongs to exactly one page, and is offered on exactly the layouts
 * that page offers it on.
 */
class WatchHomeModelTest {

    private val owners = mapOf(
            "Player" to PlayerEditorModel.keys,
            "Text" to TypographyEditorModel.keys,
            "Colors" to ColorEditorModel.keys,
            "Background" to BackgroundEditorModel.keys,
            "Always-on" to AodEditorModel.keys,
            "Panels" to PanelEditorModel.keys,
            "Mini buttons" to MiniButtonEditorModel.keys)

    private fun declaredKeys(): Set<String> {
        val xml = File("src/main/res/xml/watch_face_settings.xml").takeIf { it.exists() }
                ?: File("mobile/src/main/res/xml/watch_face_settings.xml")
        return Regex("""android:key="([^"]+)"""").findAll(xml.readText())
                .map { it.groupValues[1] }
                .toSet()
    }

    @Test
    fun `every key is declared in the watch settings`() {
        val declared = declaredKeys()
        val missing = WatchHomeModel.keys - declared
        assertTrue("Not declared in watch_face_settings.xml: $missing", missing.isEmpty())
    }

    /** A Home control is the same setting as on its page; nothing on Home is stored anywhere else. */
    @Test
    fun `every key belongs to exactly one page`() {
        WatchHomeModel.keys.forEach { key ->
            val pages = owners.filterValues { key in it }.keys
            assertEquals("$key should belong to exactly one page, found $pages", 1, pages.size)
        }
    }

    @Test
    fun `every chip has a label`() {
        WatchHomeModel.CHIPS.forEach { chip ->
            assertNotEquals("${chip.name} has no label", 0, chip.labelRes)
        }
    }

    /** The rule is the Player page's, not a copy: a chip appears exactly where its row does. */
    @Test
    fun `chips follow the owning page on every layout`() {
        ThemeAppearance.ALLOWED_BASE_FACES.forEach { face ->
            WatchHomeModel.CHIPS.forEach { chip ->
                val spec = PlayerEditorModel.specFor(chip.key)
                val expected = spec?.let { PlayerEditorModel.appliesToFace(it.control, face) } ?: true
                assertEquals("${chip.name} on $face", expected, WatchHomeModel.appliesToFace(chip, face))
            }
        }
    }

    @Test
    fun `the controls chip is absent where the layout draws no controls`() {
        val withoutControls = ThemeAppearance.ALLOWED_BASE_FACES - PlayerEditorModel.PLAYER_CONTROLS_FACES
        assertTrue("expected some layout without player controls", withoutControls.isNotEmpty())
        withoutControls.forEach { face ->
            assertTrue(!WatchHomeModel.appliesToFace(HomeChip.CONTROLS, face))
        }
    }

    @Test
    fun `the normal colour follows only the normal treatment`() {
        assertTrue(WatchHomeModel.showsNormalColor("normal"))
        listOf("expressive", "album", null).forEach { treatment ->
            assertTrue(!WatchHomeModel.showsNormalColor(treatment))
        }
    }
}
