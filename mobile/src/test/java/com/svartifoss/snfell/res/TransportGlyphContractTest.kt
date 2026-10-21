package com.svartifoss.snfell.res

import com.svartifoss.snfell.common.TransportGlyph
import com.svartifoss.snfell.common.TransportGlyphs
import com.svartifoss.snfell.common.TransportMark
import com.svartifoss.snfell.view.watchface.PlayerEditorModel
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * Pins the play and pause mark setting, which lives in as many places as any scoped appearance key:
 * the registry, ten drawings, two pickers with their arrays and forty-five translations, the
 * Player page's rows, and the community-theme contract. Each fails quietly when one is missed - a
 * picker value with no drawing renders nothing, a locale without the strings falls back to English,
 * a theme carrying a key the asset has not heard of is refused at submission - so each is checked
 * against the registry rather than hoped for.
 *
 * Reads the tree off disk like its siblings; no Android context.
 */
class TransportGlyphContractTest {

    private fun file(path: String): File =
            listOf(File(path), File("../$path")).firstOrNull { it.exists() }
                    ?: fail("Not found from either module dir or repo root: $path").let { error("unreachable") }

    private fun text(path: String) = file(path).readText()

    private fun xml(path: String): Document =
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file(path))

    private fun Document.all(tag: String): List<Element> =
            (0 until getElementsByTagName(tag).length).map { getElementsByTagName(tag).item(it) as Element }

    private fun items(path: String, array: String): List<String> =
            xml(path).all("string-array").first { it.getAttribute("name") == array }
                    .getElementsByTagName("item").let { n -> (0 until n.length).map { n.item(it).textContent.trim() } }

    private val keys = listOf("wear_play_icon", "wear_pause_icon")

    // ---- the drawings ----

    private fun pathData(path: String): String =
            Regex("""android:pathData="([^"]+)"""").find(text(path))!!.groupValues[1]

    private fun svgPath(name: String): String =
            Regex("""\sd="([^"]+)"""").findAll(text("icons/icons_4/$name"))
                    .map { it.groupValues[1] }.first { it != "M0 0h24v24H0z" && it != "M0 0h24v24H0V0z" }

    @Test
    fun everyStyleHasADrawingForBothMarksTakenUnchangedFromTheSvgs() {
        val sources = mapOf(
                "play" to listOf("PLAY-A", "PLAY-B", "PLAY-C", "PLAY-D", "PLAY-E"),
                "pause" to listOf("PAUASE-A", "PAUSE-B", "PAUSE-C", "PAUSE-D", "PAUSE-E"))
        sources.forEach { (mark, svgs) ->
            TransportGlyph.entries.forEachIndexed { i, glyph ->
                val drawable = "common/src/main/res/drawable/glyph_${mark}_${glyph.preferenceValue}.xml"
                assertEquals("$drawable must carry the path of ${svgs[i]}.svg unchanged",
                        svgPath(svgs[i] + ".svg"), pathData(drawable))
            }
        }
    }

    /** "Follow" must look like it always did, so the registry's designed glyphs are the old drawables. */
    @Test
    fun theDesignedGlyphsAreTheDrawablesTheFacesAlwaysUsed() {
        fun same(a: String, b: String) = pathData("common/src/main/res/drawable/$a.xml")
                .replace(",", " ").replace(Regex("\\s+"), "") ==
                pathData("common/src/main/res/drawable/$b.xml").replace(",", " ").replace(Regex("\\s+"), "")
        assertTrue("play D is the filled play every face drew", same("glyph_play_d", "action_play_filled"))
        assertTrue("pause D is the filled pause", same("glyph_pause_d", "action_pause_filled"))
        assertTrue("pause C is Expressive's own pause", same("glyph_pause_c", "action_pause_expressive"))
        assertEquals(TransportGlyph.D, TransportGlyphs.designed(TransportMark.PLAY, "expressive"))
        assertEquals(TransportGlyph.C, TransportGlyphs.designed(TransportMark.PAUSE, "expressive"))
    }

    // ---- the pickers ----

    @Test
    fun theArraysAreTheRegistryInOrderAndBothRowsUseThem() {
        val file = "mobile/src/main/res/values/transport_icon_options.xml"
        assertEquals(TransportGlyphs.CHOICES, items(file, "wear_transport_icon_values"))
        assertEquals(
                listOf("follow", "a", "b", "c", "d", "e").map { "@string/transport_icon_$it" },
                items(file, "wear_transport_icon_entries"))

        val settings = xml("mobile/src/main/res/xml/watch_face_settings.xml")
                .all("com.svartifoss.snfell.view.settings.TransportGlyphPreference")
                .associateBy { it.getAttribute("android:key") }
        assertEquals(keys.toSet(), settings.keys)
        settings.values.forEach {
            assertEquals("follow", it.getAttribute("android:defaultValue"))
            assertEquals("@array/wear_transport_icon_entries", it.getAttribute("android:entries"))
            assertEquals("@array/wear_transport_icon_values", it.getAttribute("android:entryValues"))
        }
    }

    @Test
    fun everyLanguageTranslatesEveryString() {
        val names = listOf("setting_wear_play_icon", "setting_wear_pause_icon",
                "setting_wear_transport_icon_description", "transport_icon_follow") +
                TransportGlyph.entries.map { "transport_icon_${it.preferenceValue}" }
        val res = file("mobile/src/main/res")
        val dirs = res.listFiles { f -> f.isDirectory && f.name.startsWith("values") && f.name != "values-night" }!!
                .filter { File(it, "transport_icon_options.xml").exists() }
        assertTrue("the base and 44 translations", dirs.size == 45)

        val missing = mutableListOf<String>()
        dirs.sortedBy { it.name }.forEach { dir ->
            val strings = xml("mobile/src/main/res/${dir.name}/transport_icon_options.xml").all("string")
                    .associate { it.getAttribute("name") to it.textContent.trim() }
            names.filter { strings[it].isNullOrBlank() }.forEach { missing += "${dir.name}: $it" }
        }
        assertEquals(emptyList<String>(), missing)
    }

    // ---- where they apply ----

    @Test
    fun theRowsAreOfferedExactlyWhereAFaceDrawsAMark() {
        assertEquals(TransportGlyphs.FACES, PlayerEditorModel.TRANSPORT_GLYPH_FACES)
        assertTrue("every such face also draws icon glyphs of its own",
                PlayerEditorModel.CONTROL_STYLE_FACES.containsAll(TransportGlyphs.FACES))
        val specs = PlayerEditorModel.specs.filter { it.key in keys }
        assertEquals(keys.toSet(), specs.map { it.key }.toSet())
        assertTrue(specs.all { it.slot == com.svartifoss.snfell.view.watchface.PlayerSlot.CONTROLS })
    }

    // ---- the community-theme contract ----

    @Test
    fun theThemeContractKnowsTheKeysTheirValuesAndTheirFaces() {
        val asset = JSONObject(text("common/src/main/assets/community-theme-constraints.json"))
        val values = asset.getJSONObject("valueSets").getJSONArray("transportGlyph")
        assertEquals(TransportGlyphs.CHOICES, (0 until values.length()).map { values.getString(it) })
        val overrides = asset.getJSONObject("originalityApplicableFaces").getJSONObject("overrides")
        keys.forEach { key ->
            assertEquals("transportGlyph", asset.getJSONObject("settings").getJSONObject(key).getString("valueSet"))
            val faces = overrides.getJSONArray(key)
            assertEquals("$key counts toward originality only on faces that draw the mark",
                    TransportGlyphs.FACES, (0 until faces.length()).map { faces.getString(it) }.toSet())
        }
    }
}
