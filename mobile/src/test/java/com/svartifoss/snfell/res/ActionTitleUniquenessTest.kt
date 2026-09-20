package com.svartifoss.snfell.res

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * No two actions may be called the same thing.
 *
 * This was reported as an icon bug - the same setting drawing a numbered repeat glyph on one
 * Controls tab and a plain one on the other - and the icon really was wrong. But the reason the two
 * tabs held different settings at all is that two distinct actions carried the identical title:
 * `RepeatOneAction`, which toggles repeat-one on and off, and `SetRepeatModeAction(ONE)`, which
 * always sets it. Both read "Repeat one". Picking one of them and getting the other is not a
 * mistake a user can be expected to avoid.
 *
 * Fixing the icon actually made that worse, because the two rows then matched in every visible
 * respect. So the names have to carry the difference, and this keeps them carrying it: the
 * parameterised setters are now "Set repeat: …" and "Set shuffle: …", the shape
 * `action_set_playback_speed` and `action_set_volume_percent` already used.
 *
 * English only, deliberately. The other forty-four locales are translations of the *old* names and
 * several are still identical to each other; disambiguating them is a translation pass, not
 * something a test - or an author who does not speak them - can invent. Pinning the source language
 * is what stops a new collision being introduced here, which is where every one of them starts.
 */
class ActionTitleUniquenessTest {

    @Test
    fun `no two action titles are the same`() {
        val strings = repoFile("mobile/src/main/res/values/strings.xml").readText()
        val byTitle = HashMap<String, MutableList<String>>()

        for (key in titleStringKeys()) {
            val value = Regex("""<string name="$key"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
                    .find(strings)?.groupValues?.get(1)?.trim()
                    ?: continue
            // A format string is distinguished by what is substituted into it, not by its template.
            if (value.contains('%')) continue
            byTitle.getOrPut(value) { mutableListOf() } += key
        }

        val collisions = byTitle.filterValues { it.size > 1 }
        assertTrue(
                "These actions share a title, so choosing between them in the picker is guesswork " +
                        "and an assigned one cannot be told from the other:\n" +
                        collisions.entries.joinToString("\n") { (title, keys) ->
                            "  \"$title\" <- ${keys.sorted().joinToString(", ")}"
                        },
                collisions.isEmpty())
    }

    /** A sanity floor: if the scan stops finding titles, the test above passes for the wrong reason. */
    @Test
    fun `the scan still finds the action titles`() {
        val keys = titleStringKeys()
        assertTrue("Only ${keys.size} action titles found; the retrieveTitle scan has stopped working",
                keys.size >= 40)
        assertTrue("The repeat pair must be in scope - it is what this test exists for",
                keys.containsAll(setOf("action_repeat_one", "action_set_repeat_one")))
    }

    /**
     * Every string an action names from its own `retrieveTitle()`.
     *
     * Read from the source rather than from a list, for the reason the rest of the suite reads
     * registries rather than repeating them: a new action is covered without a second place to
     * remember.
     */
    private fun titleStringKeys(): Set<String> {
        val keys = mutableSetOf<String>()
        val body = Regex(
                """retrieveTitle\(\)\s*:\s*String\s*=?(.{0,400}?)(?=\n\s*(?:override|private|internal|public|@|\}\s*\n))""",
                RegexOption.DOT_MATCHES_ALL)
        repoFile("mobile/src/main/java/com/svartifoss/snfell/actions")
                .walkTopDown()
                .filter { it.extension == "kt" }
                .forEach { file ->
                    body.findAll(file.readText()).forEach { match ->
                        Regex("""R\.string\.(\w+)""").findAll(match.groupValues[1])
                                .mapTo(keys) { it.groupValues[1] }
                    }
                }
        return keys
    }

    private fun repoFile(relative: String): File =
            listOf(File("../$relative"), File(relative))
                    .firstOrNull { it.exists() }
                    ?: throw AssertionError("Could not locate $relative")
}
