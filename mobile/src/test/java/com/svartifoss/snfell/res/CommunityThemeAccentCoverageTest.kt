package com.svartifoss.snfell.res

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.w3c.dom.Element

/**
 * A stock text button takes its label colour from the theme's `colorPrimary`, which the Lyra theme
 * resolves once at inflation to the static sage. Every community-theme screen therefore re-tints
 * its controls at runtime, because the accent on screen can be a colour the user picked or one
 * pulled out of the album art - and a control that missed that pass is the only green thing in
 * front of them.
 *
 * It is missed the same way every time. A filled button announces its colour, so it gets tinted
 * when the screen is written; a text button looks like a label, so it does not - and the ones that
 * arrive *after* the accent function was written are missed even when the screen was done
 * correctly. The detail card's report row was found that way, and the submission screen's two
 * screenshot actions were found the same way months later, having shipped with a feature that
 * post-dated the function meant to cover them.
 *
 * So the check is not "is the button mentioned in the Activity" - both bugs did call findViewById
 * on it. It is "is the field that button was read into referenced *inside* the accent function".
 */
class CommunityThemeAccentCoverageTest {

    private data class Screen(
            val layout: String,
            val activity: String,
            val accentFunction: String
    )

    private val screens = listOf(
            Screen(
                    "activity_community_theme_detail.xml",
                    "view/watchface/theme/CommunityThemeDetailActivity.kt",
                    "applyCommunityAccent"),
            Screen(
                    "activity_submit_community_theme.xml",
                    "view/watchface/theme/SubmitCommunityThemeActivity.kt",
                    "applyRuntimeAccent"),
            Screen(
                    "activity_community_theme_gallery.xml",
                    "view/watchface/theme/OnlineThemesActivity.kt",
                    "applyRuntimeAccent"))

    @Test
    fun `every text button on a community theme screen is re-tinted at runtime`() {
        val problems = mutableListOf<String>()
        var checked = 0

        screens.forEach { screen ->
            val source = sourceFile(screen.activity).readText()
            val body = functionBody(source, screen.accentFunction)
                    ?: run {
                        problems += "${screen.activity} no longer declares ${screen.accentFunction}"
                        return@forEach
                    }
            textButtonIds(layoutFile(screen.layout)).forEach { id ->
                checked++
                val field = fieldReadFrom(source, id)
                if (field == null) {
                    problems += "${screen.layout}: $id is never read into a field in " +
                            screen.activity
                    return@forEach
                }
                if (!Regex("\\b${Regex.escape(field)}\\b").containsMatchIn(body)) {
                    problems += "${screen.layout}: $id (read into `$field`) is never touched by " +
                            "${screen.activity}'s ${screen.accentFunction}"
                }
            }
        }

        assertTrue("the community theme screens should declare text buttons to check", checked > 0)
        if (problems.isNotEmpty()) {
            fail("Community-theme text buttons left on the static theme accent:\n  " +
                    problems.joinToString("\n  ") +
                    "\nA text button's label comes from colorPrimary, so it stays the static Lyra " +
                    "sage under a custom or album-derived accent. Tint it in the screen's accent " +
                    "function, next to the controls that are already there.")
        }
    }

    /** Ids of every view styled as a borderless/text button, which are the ones that inherit
     *  their label colour rather than declaring one. */
    private fun textButtonIds(layout: File): List<String> {
        val document = DocumentBuilderFactory.newInstance()
                .apply { isNamespaceAware = true }
                .newDocumentBuilder()
                .parse(layout)
        val ids = mutableListOf<String>()
        fun walk(element: Element) {
            val style = element.getAttribute("style")
            if (style.contains("TextButton") || style.contains("Borderless")) {
                element.getAttributeNS(ANDROID_NS, "id")
                        .removePrefix("@+id/")
                        .removePrefix("@id/")
                        .takeIf { it.isNotEmpty() }
                        ?.let(ids::add)
            }
            val children = element.childNodes
            for (index in 0 until children.length) {
                (children.item(index) as? Element)?.let(::walk)
            }
        }
        walk(document.documentElement)
        return ids
    }

    private fun fieldReadFrom(source: String, id: String): String? =
            Regex("""(\w+)\s*=\s*findViewById[^(]*\(\s*R\.id\.$id\s*\)""")
                    .find(source)
                    ?.groupValues
                    ?.get(1)

    /** The function's body by brace balance - enough to answer "is this name in there". */
    private fun functionBody(source: String, name: String): String? {
        val start = source.indexOf("private fun $name(")
        if (start < 0) return null
        val open = source.indexOf('{', start)
        if (open < 0) return null
        var depth = 0
        for (index in open until source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(open, index + 1)
                }
            }
        }
        return null
    }

    private fun layoutFile(name: String): File = resolve("res/layout/$name")

    private fun sourceFile(relative: String): File =
            resolve("java/com/svartifoss/snfell/$relative")

    private fun resolve(relative: String): File = listOf(
            File("src/main/$relative"),
            File("mobile/src/main/$relative"))
            .first(File::isFile)

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
