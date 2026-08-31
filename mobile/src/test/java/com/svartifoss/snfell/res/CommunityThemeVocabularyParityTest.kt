package com.svartifoss.snfell.res

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.json.JSONObject
import com.svartifoss.snfell.common.BackgroundLayerColor
import com.svartifoss.snfell.common.BackgroundLayerKind
import com.svartifoss.snfell.common.BackgroundLayerStack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.w3c.dom.Element

/**
 * Every value a settings picker offers must be a value the public community-theme contract
 * accepts, and every value it hides as archived must be one the contract refuses.
 *
 * The asset in `common/src/main/assets/community-theme-constraints.json` is the canonical
 * vocabulary for a published profile, and `WatchThemeRepository.prepareCommunityThemeSubmission`
 * validates the *whole* saved snapshot against it. So one setting carrying a value the asset has
 * never heard of makes that entire theme unsubmittable - and the failure is silent in the worst
 * way: nothing objects when the value is picked, the face renders it, the preview agrees, and the
 * theme only turns out to be unpublishable later, behind a message ("this saved theme cannot be
 * submitted") that names neither the setting nor the value.
 *
 * That is not hypothetical. Fifty-one fonts and both new AOD styles reached the pickers without
 * reaching the asset, and every theme whose face happened to use one was refused. Because these
 * keys are face-scoped it split by face, which made it look like certain layouts could not be
 * submitted at all.
 *
 * Archived values are the deliberate exception, and both directions matter, so the pairing is read
 * out of `WatchFacePrefsFragment` rather than retyped here: an archived value for one key is often
 * a perfectly live value for another (`solid_theme` is retired as a mini-button background and
 * current as a seek style), so a set-wide exemption would punch a hole in the check.
 */
class CommunityThemeVocabularyParityTest {

    private companion object {
        /**
         * The font pickers are built in code, not declared in XML.
         *
         * `WatchFacePrefsFragment.sortedFontChoices` reads `wear_font_values` and hands it to the
         * global font row and, with a "follow" row on top, to each per-element override, filtering
         * `archivedFonts` out of all of them. An XML sweep cannot see any of that, and these are
         * exactly the keys the missing fonts came in through.
         */
        const val FONT_ARRAY = "wear_font_values"
        const val FONT_ARCHIVED_SET = "archivedFonts"

        /**
         * The global font row declares its entries in XML but has them rebuilt in code, so it
         * filters [FONT_ARCHIVED_SET] without going through `filterArchivedListPreference` and
         * cannot be paired with its archived set by reading those call sites.
         */
        val FONT_ROWS_FILTERED_IN_CODE = setOf("wear_font")
        val DERIVED_FONT_PICKERS = mapOf(
                "font" to emptyList<String>(),
                "fontWithFollow" to listOf("follow"))
    }

    @Test
    fun everyValueAPickerOffersIsInThePublicVocabulary() {
        val problems = mutableListOf<String>()
        var checked = 0

        eachPicker { key, setName, offered, accepted, archived, source ->
            checked++
            val rejected = offered.filterNot { it in accepted || it in archived }
            if (rejected.isNotEmpty()) {
                problems += "$key ($source) offers ${rejected.size} value(s) that value set " +
                        "\"$setName\" rejects: " + rejected.joinToString(", ")
            }
        }
        assertTrue("the settings screens should declare list preferences", checked > 20)

        if (problems.isNotEmpty()) {
            fail("Settings a picker offers but a community theme cannot carry - every theme " +
                    "using one of these is refused at submission, with nothing to say which " +
                    "setting is at fault:\n  " + problems.joinToString("\n  ") +
                    "\nAdd the values to the matching value set in " +
                    "common/src/main/assets/community-theme-constraints.json, or archive them in " +
                    "WatchFacePrefsFragment if they should not spread.")
        }
    }

    /**
     * Un-archiving a value has to put it back into the vocabulary.
     *
     * Without this the exemption above would quietly become a blanket one: a value dropped from an
     * `archived*` set starts being offered to everyone while the contract still refuses it, which
     * is the same unexplained refusal in the other direction.
     */
    @Test
    fun everyArchivedValueIsRefusedByTheVocabularyItIsHiddenFrom() {
        val problems = mutableListOf<String>()
        eachPicker { key, setName, _, accepted, archived, _ ->
            val leaked = archived.filter { it in accepted }
            if (leaked.isNotEmpty()) {
                problems += "$key hides ${leaked.sorted().joinToString(", ")} as archived, but " +
                        "value set \"$setName\" still accepts them"
            }
        }
        if (problems.isNotEmpty()) {
            fail("Archived options the public vocabulary still accepts:\n  " +
                    problems.joinToString("\n  ") +
                    "\nEither drop them from the value sets in " +
                    "common/src/main/assets/community-theme-constraints.json, or stop archiving " +
                    "them in WatchFacePrefsFragment.")
        }
    }

    /**
     * The background stack's grammar is written twice, and the copies have to say the same thing.
     *
     * `BackgroundLayerStack` is what the editor builds and all three renderers read; the layer
     * rule in the constraints asset is what the gallery, the trusted publisher and the moderator
     * page read. A drift between them does not error - it makes a stack the app happily composes
     * unpublishable, reported as "this saved theme cannot be submitted", which names neither the
     * setting nor the value. That is precisely how fifty-one fonts got lost, and a stack is worse
     * to diagnose than a font because the offending value is buried inside a longer string.
     *
     * The vocabularies are derived rather than retyped, so adding a background treatment, a
     * shading or an accent floor reaches the contract or fails here.
     */
    @Test
    fun theBackgroundStackGrammarMatchesTheOneTheAppBuilds() {
        val rule = constraints().getJSONObject("layerRules")
                .getJSONObject("background-layer-stack-v1")

        assertEquals(BackgroundLayerStack.FORMAT_VERSION, rule.getString("version"))
        assertEquals(BackgroundLayerStack.MAX_LAYERS, rule.getInt("maxLayers"))
        assertEquals(BackgroundLayerStack.MAX_ENCODED_LENGTH, rule.getInt("maxLength"))
        assertEquals(0, rule.getJSONObject("opacity").getInt("min"))
        assertEquals(
                BackgroundLayerStack.MAX_OPACITY_PERCENT,
                rule.getJSONObject("opacity").getInt("max"))
        assertEquals(
                BackgroundLayerColor.entries.map { it.preferenceValue }.toSet(),
                rule.getJSONArray("colors").let { array ->
                    (0 until array.length()).map(array::getString).toSet()
                })

        val valueSets = constraints().getJSONObject("valueSets")
        val kinds = rule.getJSONObject("kinds")
        val problems = mutableListOf<String>()
        BackgroundLayerKind.entries.forEach { kind ->
            val setName = kinds.optString(kind.token).takeIf { it.isNotEmpty() }
            if (setName == null) {
                problems += "the layer rule declares no vocabulary for kind \"${kind.token}\""
                return@forEach
            }
            val declared = valueSets.optJSONArray(setName)?.let { array ->
                (0 until array.length()).map(array::getString)
            }
            if (declared == null) {
                problems += "kind \"${kind.token}\" points at value set \"$setName\", " +
                        "which the asset does not define"
                return@forEach
            }
            val offered = BackgroundLayerStack.stylesFor(kind)
            val missing = offered - declared.toSet()
            val extra = declared.toSet() - offered.toSet()
            if (missing.isNotEmpty()) {
                problems += "$kind offers ${missing.joinToString(", ")}, which value set " +
                        "\"$setName\" rejects"
            }
            if (extra.isNotEmpty()) {
                problems += "value set \"$setName\" accepts ${extra.joinToString(", ")}, " +
                        "which $kind layers cannot carry"
            }
        }
        if (problems.isNotEmpty()) {
            fail("The background layer grammar and its public contract disagree:\n  " +
                    problems.joinToString("\n  ") +
                    "\nUpdate the layerRules/valueSets entries in " +
                    "common/src/main/assets/community-theme-constraints.json to match " +
                    "BackgroundLayerStack.")
        }
    }

    /**
     * Walks every picker that stores a scoped community-theme setting, resolving its offered
     * values, the value set that judges them, and the archived values it filters out.
     */
    private fun eachPicker(
            visit: (
                    key: String,
                    setName: String,
                    offered: List<String>,
                    accepted: Set<String>,
                    archived: Set<String>,
                    source: String
            ) -> Unit
    ) {
        val asset = constraints()
        val settings = asset.getJSONObject("settings")
        val valueSets = asset.getJSONObject("valueSets")
        val arrays = stringArrays()
        val archivedSets = archivedOptionSets()
        val archivedByKey = archivedSetNamesByPreferenceKey()

        fun accepted(setName: String): Set<String> {
            val array = valueSets.optJSONArray(setName)
                    ?: fail("the asset defines no value set named \"$setName\"").let { return emptySet() }
            return (0 until array.length()).map(array::getString).toSet()
        }

        fun archivedFor(setName: String): Set<String> =
                archivedSets[setName] ?: fail(
                        "WatchFacePrefsFragment declares no archived set named \"$setName\"")
                        .let { emptySet() }

        preferenceRows().forEach { (key, arrayName) ->
            val setName = settings.optJSONObject(key)?.optString("valueSet")
                    ?.takeIf { it.isNotEmpty() }
                    ?: return@forEach
            val offered = arrays[arrayName]
                    ?: fail("$key points at @array/$arrayName, which no values/ file defines")
                            .let { return@forEach }
            visit(
                    key,
                    setName,
                    offered,
                    accepted(setName),
                    (archivedByKey[key]
                            ?: FONT_ARCHIVED_SET.takeIf { key in FONT_ROWS_FILTERED_IN_CODE })
                            ?.let(::archivedFor).orEmpty(),
                    "@array/$arrayName")
        }

        val fonts = arrays[FONT_ARRAY] ?: fail("@array/$FONT_ARRAY is missing").let { return }
        DERIVED_FONT_PICKERS.forEach { (setName, prefix) ->
            visit(
                    setName,
                    setName,
                    prefix + fonts,
                    accepted(setName),
                    archivedFor(FONT_ARCHIVED_SET),
                    "@array/$FONT_ARRAY, built in code")
        }
    }

    /** `private val archivedX = setOf("a", "b")` in the settings fragment. */
    private fun archivedOptionSets(): Map<String, Set<String>> {
        val quoted = Regex("\"([a-z_0-9]+)\"")
        val sets = Regex("private val (archived\\w+) = setOf\\(([^)]*)\\)")
                .findAll(settingsFragment())
                .associate { match ->
                    match.groupValues[1] to
                            quoted.findAll(match.groupValues[2]).map { it.groupValues[1] }.toSet()
                }
        assertTrue(
                "no archived option sets found in WatchFacePrefsFragment - has the declaration " +
                        "changed shape? The checks here would silently stop excluding them.",
                sets.isNotEmpty())
        return sets
    }

    /** The `filterArchivedListPreference(key = "...", ..., archived = archivedX, ...)` pairings. */
    private fun archivedSetNamesByPreferenceKey(): Map<String, String> =
            Regex("""key = "([a-z_0-9]+)"[^)]*?archived = (archived\w+)""")
                    .findAll(settingsFragment())
                    .associate { it.groupValues[1] to it.groupValues[2] }

    /** `android:key` paired with the `@array/` its `android:entryValues` names. */
    private fun preferenceRows(): List<Pair<String, String>> {
        val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        return listOf("watch_face_settings.xml", "settings.xml").flatMap { name ->
            val document = builder.parse(resource("res/xml/$name"))
            val elements = document.getElementsByTagName("*")
            (0 until elements.length).mapNotNull { index ->
                val element = elements.item(index) as? Element ?: return@mapNotNull null
                val key = element.getAttribute("android:key").takeIf { it.isNotEmpty() }
                        ?: return@mapNotNull null
                element.getAttribute("android:entryValues")
                        .takeIf { it.startsWith("@array/") }
                        ?.let { key to it.removePrefix("@array/") }
            }
        }
    }

    private fun stringArrays(): Map<String, List<String>> {
        val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        return resource("res/values").listFiles { file -> file.extension == "xml" }.orEmpty()
                .flatMap { file ->
                    val document = try {
                        builder.parse(file)
                    } catch (_: Exception) {
                        return@flatMap emptyList<Pair<String, List<String>>>()
                    }
                    val arrays = document.getElementsByTagName("string-array")
                    (0 until arrays.length).mapNotNull { index ->
                        val element = arrays.item(index) as? Element ?: return@mapNotNull null
                        val name = element.getAttribute("name").takeIf { it.isNotEmpty() }
                                ?: return@mapNotNull null
                        val items = element.getElementsByTagName("item")
                        name to (0 until items.length).map { items.item(it).textContent.orEmpty() }
                    }
                }
                .toMap()
    }

    private fun settingsFragment(): String =
            resource("java/com/svartifoss/snfell/view/watchface/WatchFacePrefsFragment.kt").readText()

    private fun constraints(): JSONObject = JSONObject(
            listOf(
                    File("../common/src/main/assets/community-theme-constraints.json"),
                    File("common/src/main/assets/community-theme-constraints.json"))
                    .first(File::isFile)
                    .readText())

    private fun resource(relative: String): File = listOf(
            File("src/main/$relative"),
            File("mobile/src/main/$relative"))
            .first(File::exists)
}
