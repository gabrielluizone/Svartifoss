package com.svartifoss.snfell.view.mainactivity

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A category header in a settings list stays on the accent, however often the list is rebound.
 *
 * `PreferenceGroupAdapter.onBindViewHolder` calls `PreferenceViewHolder.resetState()`, which puts a
 * row's title back to the color it was inflated with - for a header, the static `lyra_accent`
 * (sage green). The accent used to be applied only when a row was attached to the window, and a row
 * rebound in place is never detached, so the first rebind of "Community themes" or "Updates" made it
 * green for good while a header nothing had rebound ("Appearance") stayed on the accent. It
 * survived switching tabs because each visit builds the page again and the same rebind follows.
 *
 * A source sweep, because the drawing is right either way: what fails is a callback that never
 * fires again, which no test of one function can see.
 */
class AccentPreferenceListContractTest {

    private companion object {
        const val ACTIVITY_PATH =
                "src/main/java/com/svartifoss/snfell/view/mainactivity/MainActivity.kt"
    }

    @Test
    fun everyPreferenceListGoesThroughTheOneTracker() {
        val source = activitySource()
        assertTrue(
                "Fragment views that are PreferenceFragmentCompat lists must use the shared tracker.",
                source.contains("f.listView?.let(::trackAccentOnPreferenceList)"))
        assertTrue(
                "applyAccentColorToFragmentView must use the shared tracker, not its own listener.",
                bodyOf(source, "private fun applyAccentColorToFragmentView(")
                        .contains("trackAccentOnPreferenceList(recyclerView)"))
    }

    @Test
    fun theTrackerPutsTitlesBackAfterEachLayout() {
        val tracker = bodyOf(activitySource(), "private fun trackAccentOnPreferenceList(")
        assertTrue(
                "Rows are rebound during layout without being detached; the attach callback alone " +
                        "never sees them again.",
                tracker.contains("addOnChildAttachStateChangeListener") &&
                        tracker.contains("addOnLayoutChangeListener"))
        assertTrue(
                "The layout hook must reach the title through the holder, which is where " +
                        "PreferenceViewHolder.resetState() puts the old color back.",
                tracker.contains("PreferenceViewHolder") &&
                        tracker.contains("findViewById(android.R.id.title)"))
        assertTrue(
                "Only a title that already follows the accent may be recolored.",
                tracker.contains("tag_uses_accent"))
        assertTrue(
                "A layout hook that asks for another layout would loop; setting a text color only " +
                        "invalidates.",
                !tracker.contains("requestLayout"))
    }

    private fun activitySource(): String {
        val file = listOf(File("."), File("mobile"))
                .map { File(it, ACTIVITY_PATH) }
                .firstOrNull { it.isFile }
                ?: throw AssertionError(
                        "Could not locate MainActivity from ${File(".").absolutePath}")
        return withoutComments(file.readText())
    }

    /** The body of the function declared by [signature], found by brace counting. */
    private fun bodyOf(source: String, signature: String): String {
        val start = source.indexOf(signature)
        assertTrue("MainActivity no longer declares '$signature'", start >= 0)
        val open = source.indexOf('{', start)
        var depth = 0
        for (index in open until source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return source.substring(open, index + 1)
            }
        }
        throw AssertionError("Unbalanced braces after '$signature' in MainActivity")
    }

    /** Comments removed, so an explanation that names the old calls is not read as code. */
    private fun withoutComments(source: String): String =
            source.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
                    .lineSequence()
                    .joinToString("\n") { line ->
                        var quotes = 0
                        line.forEachIndexed { index, ch ->
                            if (ch == '"' && (index == 0 || line[index - 1] != '\\')) quotes++
                            if (ch == '/' && index > 0 && line[index - 1] == '/' &&
                                    quotes % 2 == 0) {
                                return@joinToString line.substring(0, index - 1)
                            }
                        }
                        line
                    }
}
