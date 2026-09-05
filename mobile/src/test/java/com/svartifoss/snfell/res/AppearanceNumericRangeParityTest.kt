package com.svartifoss.snfell.res

import com.svartifoss.snfell.common.AppearanceNumericRanges
import com.svartifoss.snfell.view.watchface.TypographyEditorModel
import com.svartifoss.snfell.view.watchface.TypographyValueSpec
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.w3c.dom.Element

/**
 * The numeric half of the community-theme vocabulary, which nothing checked until now.
 *
 * `CommunityThemeVocabularyParityTest` sweeps every *picker* against the shipped contract, so a
 * style or a font cannot reach the app without reaching the gallery. Numbers had no such sweep -
 * and all fifty numeric appearance settings are free-typing fields. The contract declared a range,
 * the watch clamped what it read, and the field in between accepted anything at all: so a value
 * outside the range was stored, drawn exactly as intended, and only surfaced much later as "The
 * gallery can't accept this theme's Overlay blur setting" - about a number nothing had objected to
 * when it was typed.
 *
 * [AppearanceNumericRanges] is the authority now, and these three checks are what stop it becoming
 * a fourth opinion instead of the single one.
 */
class AppearanceNumericRangeParityTest {

    @Test
    fun `the public contract and the app agree on every numeric range`() {
        val declared = constraintRanges()
        val problems = mutableListOf<String>()

        // One direction only. Every setting the contract bounds must be bounded by the app, or
        // the app can store what the gallery refuses - that is the whole failure. The reverse is
        // fine: the app also bounds a couple of behaviour settings that never reach a theme.
        (declared.keys - AppearanceNumericRanges.RANGES.keys).sorted().forEach { key ->
            problems += "$key is bounded by the contract but not by AppearanceNumericRanges, so " +
                    "nothing stops the app storing a value the gallery refuses"
        }
        declared.forEach { (key, range) ->
            val app = AppearanceNumericRanges.RANGES[key] ?: return@forEach
            if (app != range) {
                problems += "$key: the contract accepts ${range.first}..${range.last}, the app " +
                        "declares ${app.first}..${app.last}"
            }
        }

        assertTrue("the contract should declare numeric settings", declared.isNotEmpty())
        if (problems.isNotEmpty()) {
            fail("Numeric ranges the app and the public contract disagree about:\n  " +
                    problems.joinToString("\n  ") +
                    "\nReconcile AppearanceNumericRanges with the int settings in " +
                    "common/src/main/assets/community-theme-constraints.json.")
        }
    }

    /**
     * A typed field with no declared range is the exact shape of the bug: it accepts anything, and
     * everything downstream quietly disagrees.
     */
    @Test
    fun `every numeric preference row declares a range or is a named exception`() {
        val rows = numericRows()
        val unbounded = rows.filterNot {
            it in AppearanceNumericRanges.RANGES || it in UNBOUNDED_BY_DESIGN
        }
        assertTrue("the settings screens should declare numeric rows", rows.isNotEmpty())
        // An exemption for a row that has since been bounded would quietly stop meaning anything.
        val stale = UNBOUNDED_BY_DESIGN.filter { it in AppearanceNumericRanges.RANGES }
        if (unbounded.isNotEmpty() || stale.isNotEmpty()) {
            fail(buildString {
                if (unbounded.isNotEmpty()) {
                    append("Numeric preference rows that accept any value at all:\n  ")
                    append(unbounded.sorted().joinToString("\n  "))
                    append("\nAdd each to AppearanceNumericRanges (and, for an appearance " +
                            "setting, to the contract asset) so the field cannot store what the " +
                            "gallery will later refuse - or list it in UNBOUNDED_BY_DESIGN with " +
                            "a reason.\n")
                }
                if (stale.isNotEmpty()) {
                    append("Listed as unbounded but now bounded: ")
                    append(stale.sorted().joinToString(", "))
                }
            })
        }
    }

    /**
     * The Text page edits these through sliders, whose bounds are a second place the same numbers
     * are written. A slider wider than the contract is the same bug with a nicer control.
     */
    @Test
    fun `the typography sliders stay inside the declared ranges`() {
        val problems = mutableListOf<String>()
        var checked = 0
        TypographyEditorModel.keys.forEach { key ->
            val slider = (TypographyEditorModel.specFor(key)?.value as? TypographyValueSpec.Number)
                    ?: return@forEach
            val allowed = AppearanceNumericRanges.RANGES[key] ?: run {
                problems += "$key has a slider but no declared range"
                return@forEach
            }
            checked++
            if (slider.range != allowed) {
                problems += "$key: the slider offers ${slider.range.first}..${slider.range.last}, " +
                        "the contract accepts ${allowed.first}..${allowed.last}"
            }
        }
        assertTrue("the Text page should declare numeric controls", checked > 0)
        if (problems.isNotEmpty()) {
            fail("Typography sliders that disagree with the public contract:\n  " +
                    problems.joinToString("\n  "))
        }
    }

    private fun constraintRanges(): Map<String, IntRange> {
        val settings = JSONObject(constraintsFile().readText()).getJSONObject("settings")
        val ranges = linkedMapOf<String, IntRange>()
        settings.keys().forEach { key ->
            val definition = settings.getJSONObject(key)
            if (definition.optString("type") == "int") {
                ranges[key] = definition.getInt("min")..definition.getInt("max")
            }
        }
        return ranges
    }

    private fun numericRows(): List<String> {
        val keys = mutableListOf<String>()
        listOf("watch_face_settings.xml", "settings.xml").forEach { name ->
            val document = DocumentBuilderFactory.newInstance()
                    .apply { isNamespaceAware = true }
                    .newDocumentBuilder()
                    .parse(resource("res/xml/$name"))
            collectNumericRows(document.documentElement, keys)
        }
        return keys
    }

    private fun collectNumericRows(element: Element, into: MutableList<String>) {
        if (element.tagName.endsWith("ResettableNumericEditTextPreference") ||
                element.tagName.endsWith("NumericEditTextPreference")) {
            element.getAttributeNS(ANDROID_NS, "key")
                    .takeIf { it.isNotEmpty() }
                    ?.let(into::add)
        }
        val children = element.childNodes
        for (index in 0 until children.length) {
            (children.item(index) as? Element)?.let { collectNumericRows(it, into) }
        }
    }

    private fun constraintsFile(): File = listOf(
            File("../common/src/main/assets/community-theme-constraints.json"),
            File("common/src/main/assets/community-theme-constraints.json"))
            .first(File::isFile)

    private fun resource(relative: String): File = listOf(
            File("src/main/$relative"),
            File("mobile/src/main/$relative"))
            .first(File::exists)

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

        /**
         * Behaviour settings with no bound anywhere in the app, deliberately left that way.
         *
         * None of them is a scoped appearance setting, so none can make a theme unsubmittable -
         * they are timeouts and a crown sensitivity, read as-is by the watch. Choosing a ceiling
         * for them would be inventing product behaviour rather than relocating a bound that
         * already exists, and a wrong ceiling silently takes away a configuration somebody relies
         * on. They are listed rather than filtered out so that adding a *new* unbounded numeric
         * row is still a decision somebody has to make here.
         */
        val UNBOUNDED_BY_DESIGN = setOf(
                "close_timeout",
                "notification_timeout",
                "rotating_crown_off_period",
                "rotating_crown_sensitivity")
    }
}
