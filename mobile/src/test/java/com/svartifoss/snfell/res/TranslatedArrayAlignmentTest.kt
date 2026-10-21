package com.svartifoss.snfell.res

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Every translated `<string-array>` must hold exactly as many items as its base counterpart.
 *
 * `ListPreference` pairs `entries` with `entryValues` **by index**, and the `*_values` arrays are
 * declared only in `values/` because they are not translatable. A short translated `entries` array
 * therefore does not degrade to English - it silently breaks the picker:
 *
 *  - items missing from the END become unreachable. Five colour harmonies shipped in 3.1-beta1 and
 *    could not be selected in any of the twelve translated languages, because every locale's
 *    `wear_color_treatment_entries` still had the three pre-3.1 entries.
 *  - an item missing from the MIDDLE is worse: it shifts every later label onto the wrong value.
 *    "Frosted edges" was absent at position 3 of `album_art_style_entries`, so picking "Square
 *    (sharp corners)" in Portuguese actually selected the frosted style, and so on for 21 styles.
 *
 * Android lint's InconsistentArrays check covers this, but the module carries a large backlog of
 * unrelated lint errors and aborts before reporting - so this pins it as a plain JVM test instead,
 * following the same "extract the decision and test it directly" convention the rest of the project
 * uses.
 *
 * Deliberately checks only the item COUNT, not the text: a locale legitimately translates the
 * labels, and this is about the index contract, not about translation quality.
 */
class TranslatedArrayAlignmentTest {

    @Test
    fun everyTranslatedArrayHasTheSameItemCountAsTheBase() {
        val res = resDir()
        val base = parseArrays(File(res, "values/strings.xml"))
        assertTrue("base strings.xml should declare arrays", base.isNotEmpty())

        val problems = mutableListOf<String>()
        res.listFiles { f -> f.isDirectory && f.name.startsWith("values-") }
                ?.sortedBy { it.name }
                ?.forEach { localeDir ->
                    val file = File(localeDir, "strings.xml")
                    if (!file.exists()) return@forEach
                    parseArrays(file).forEach { (name, count) ->
                        val expected = base[name] ?: return@forEach
                        if (count != expected) {
                            problems += "${localeDir.name}/$name: base has $expected items, " +
                                    "locale has $count"
                        }
                    }
                }

        if (problems.isNotEmpty()) {
            fail("Translated arrays out of step with the base (entries/entryValues are paired by " +
                    "index, so this mislabels or hides options):\n  " + problems.joinToString("\n  "))
        }
    }

    /**
     * A `*_values` array is not translatable, so a locale either leaves it out and inherits the
     * base's, or carries a copy - and a copy has to be the base's, item for item, in order.
     *
     * The count check above cannot see this. 4.3 moved the two new always-on-display styles next to
     * Chrono in the base `wear_aod_style_values` and in every locale's `wear_aod_style_entries`, but
     * 39 locales still carried their own copy of the values in the old order, with the new styles
     * last. Both arrays had nineteen items, so every check passed, while the picker paired each
     * translated label with the neighbouring value: in Portuguese "Anel de minutos" stored `poster`
     * and "Pôster" stored `immersive`. A locale's copy overrides the base outright, so it is the
     * copy that decides what a choice means.
     */
    @Test
    fun aLocaleCopyOfAValuesArrayIsIdenticalToTheBase() {
        val res = resDir()
        val base = parseArrayItems(File(res, "values/strings.xml")).filterKeys { it.endsWith("_values") }
        assertTrue("base strings.xml should declare *_values arrays", base.isNotEmpty())

        val problems = mutableListOf<String>()
        res.listFiles { f -> f.isDirectory && f.name.startsWith("values-") }
                ?.sortedBy { it.name }
                ?.forEach { localeDir ->
                    val file = File(localeDir, "strings.xml")
                    if (!file.exists()) return@forEach
                    parseArrayItems(file).forEach { (name, items) ->
                        val expected = base[name] ?: return@forEach
                        if (items != expected) {
                            problems += "${localeDir.name}/$name: base is $expected, locale has $items"
                        }
                    }
                }

        if (problems.isNotEmpty()) {
            fail("A locale's copy of a values array differs from the base, so its picker pairs " +
                    "labels with the wrong values (delete the copy or make it identical):\n  " +
                    problems.joinToString("\n  "))
        }
    }

    /**
     * A locale that declares an array must not declare it empty either - that yields a picker with
     * no rows at all rather than an English fallback.
     */
    @Test
    fun noTranslatedArrayIsEmpty() {
        val res = resDir()
        res.listFiles { f -> f.isDirectory && f.name.startsWith("values-") }
                ?.forEach { localeDir ->
                    val file = File(localeDir, "strings.xml")
                    if (!file.exists()) return@forEach
                    parseArrays(file).forEach { (name, count) ->
                        assertTrue("${localeDir.name}/$name is declared but empty", count > 0)
                    }
                }
    }

    private fun resDir(): File {
        // Unit tests run with the module directory as the working directory; fall back to the repo
        // root so the test also passes when invoked from there.
        val fromModule = File("src/main/res")
        return if (fromModule.isDirectory) fromModule else File("mobile/src/main/res")
    }

    private fun parseArrays(file: File): Map<String, Int> {
        val text = file.readText()
        return ARRAY.findAll(text).associate { match ->
            match.groupValues[1] to ITEM.findAll(match.groupValues[2]).count()
        }
    }

    private fun parseArrayItems(file: File): Map<String, List<String>> {
        val text = file.readText()
        return ARRAY.findAll(text).associate { match ->
            match.groupValues[1] to ITEM_TEXT.findAll(match.groupValues[2]).map { it.groupValues[1] }.toList()
        }
    }

    private companion object {
        val ARRAY = Regex("""<string-array name="([^"]+)">(.*?)</string-array>""", RegexOption.DOT_MATCHES_ALL)
        val ITEM = Regex("<item>")
        val ITEM_TEXT = Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL)
    }
}
