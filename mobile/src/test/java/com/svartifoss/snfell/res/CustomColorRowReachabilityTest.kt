package com.svartifoss.snfell.res

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Every picked-colour row on the Watch tab must have something that opens a colour picker.
 *
 * A `HexColorDotPreference` is inert on its own: it stores a hex string and draws a dot, and the
 * dialog that lets anyone choose that hex is installed by `WatchFacePrefsFragment`, one row at a
 * time. A row added without that line looks completely finished — it is declared in the XML,
 * scoped per face, carried by backups and community themes, and its colour mode offers "Custom" —
 * and choosing Custom then sets a mode whose colour can never be named.
 *
 * That is not hypothetical. The three text effects (shadow, outline and backdrop, for title and
 * artist) shipped six such rows, and because those rows are hidden on the compact Text page there
 * was no second route to them either: picking Custom simply did nothing you could act on.
 *
 * The check is a source sweep because the wiring is a call, not data — there is no registry to
 * compare against, which is exactly how six rows went unnoticed.
 */
class CustomColorRowReachabilityTest {

    @Test
    fun everyHexColorRowHasAPickerWiredToIt() {
        val fragment = fragmentSource().readText()
        val rows = hexColorRows()

        assertTrue("Expected to find the Watch tab's hex colour rows", rows.size >= 16)

        val unreachable = rows.filterNot { key ->
            fragment.contains("\"$key\"") ||
                    fragment.contains("MiscPreferences.${key.uppercase()}")
        }

        if (unreachable.isNotEmpty()) {
            fail("Hex colour rows with no picker wired in WatchFacePrefsFragment:\n  " +
                    unreachable.joinToString("\n  ") +
                    "\nA row nothing opens means its colour mode can be set to Custom and the " +
                    "colour itself never chosen. Wire it with initAccentColorTarget (for a row " +
                    "whose mode also drives visibility) or initTextEffectCustomColorRow.")
        }
    }

    // ----------------------------------------------------------------- source

    private fun hexColorRows(): List<String> {
        val file = listOf(
                File("src/main/res/xml/watch_face_settings.xml"),
                File("mobile/src/main/res/xml/watch_face_settings.xml")
        ).first { it.exists() }
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val keys = mutableListOf<String>()
        fun walk(node: Node) {
            var child = node.firstChild
            while (child != null) {
                if (child is Element) {
                    if (child.tagName.endsWith("HexColorDotPreference")) {
                        child.getAttribute("android:key").takeIf { it.isNotEmpty() }?.let(keys::add)
                    }
                    walk(child)
                }
                child = child.nextSibling
            }
        }
        walk(document.documentElement)
        return keys
    }

    private fun fragmentSource(): File = listOf(
            File("src/main/java/com/svartifoss/snfell/view/watchface/WatchFacePrefsFragment.kt"),
            File("mobile/src/main/java/com/svartifoss/snfell/view/watchface/WatchFacePrefsFragment.kt")
    ).first { it.exists() }
}
