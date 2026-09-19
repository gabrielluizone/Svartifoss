package com.svartifoss.snfell.watch.res

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.w3c.dom.Element

/**
 * Two things the mini-button row must never assume about the views above it.
 *
 * Both were true for years and both stopped being true silently, in the same change: wrapping the
 * row in a `ClaimedGestureHost` for the swipe-dispatch work put a plain `FrameLayout` between it
 * and `content_frame`. Nothing threw. One of the two produced a visible bug immediately; the other
 * only survived because that particular wrapper happens to be full-bleed at the frame's origin.
 *
 * Neither is the kind of thing a reviewer spots in a layout diff, which is why they are pinned
 * here rather than left to the next person to rediscover on a watch.
 */
class ScreenButtonsParentChainTest {

    /**
     * A parent with `clipChildren` on clips each child to *the child's own* bounds. The row is
     * `wrap_content` around the pills and a curved arrangement lifts the side pills above that box
     * deliberately, so one clipping ancestor anywhere in the chain shaves their tops off - which
     * reads as an invisible rectangle sitting over the row.
     */
    @Test
    fun `every ancestor of the mini button row disables child clipping`() {
        val document = DocumentBuilderFactory.newInstance()
                .apply { isNamespaceAware = true }
                .newDocumentBuilder()
                .parse(layout())

        val row = findById(document.documentElement, "screen_buttons_row")
                ?: fail("activity_main.xml no longer declares screen_buttons_row").let { return }

        val offenders = mutableListOf<String>()
        var ancestor = row.parentNode
        var checked = 0
        while (ancestor is Element) {
            checked++
            if (ancestor.getAttributeNS(ANDROID_NS, "clipChildren") != "false") {
                offenders += "${ancestor.tagName}${idOf(ancestor)?.let { " ($it)" }.orEmpty()}"
            }
            ancestor = ancestor.parentNode
        }

        assertTrue("the row should have at least one ancestor to check", checked > 0)
        if (offenders.isNotEmpty()) {
            fail("Mini-button row ancestors that clip their children, which shaves the tops off " +
                    "a curved row's side pills:\n  " + offenders.joinToString("\n  ") +
                    "\nAdd android:clipChildren=\"false\" to each of them in " +
                    "wear/src/main/res/layout/activity_main.xml.")
        }
    }

    /**
     * Every placement pass moves a pill *relatively* - target position minus current position -
     * so it has to know where the pill currently sits inside the content frame. Adding the chain
     * up by hand (`row.left + button.left`) answers that only while the row's parent is at the
     * frame's origin, and a wrapper inserted above the row makes it quietly wrong: the pills curve
     * around the wrong centre, or a rail lands beside the bezel instead of on it, with nothing to
     * report. `MainActivity.layoutCenterInContent` walks the real chain instead; this keeps a new
     * call site from going back to the shortcut.
     */
    @Test
    fun `no mini button placement adds up its own parent chain`() {
        val source = stripComments(activity().readText())
        val shortcuts = listOf("row.left", "row.top", "screenButtonsRow.left", "screenButtonsRow.top")
        val offenders = shortcuts.filter { it in source }
        if (offenders.isNotEmpty()) {
            fail("Mini-button geometry reading its parent chain by hand: " +
                    offenders.joinToString(", ") +
                    "\nThese are correct only while the row's parent sits at the content frame's " +
                    "origin, which a wrapper above the row silently changes. Use " +
                    "layoutCenterInContent(view) instead.")
        }
        // The helper has to still be there for the check above to mean anything.
        assertTrue(
                "MainActivity should resolve pill positions through layoutCenterInContent",
                "private fun layoutCenterInContent(" in source)
    }

    private fun stripComments(source: String): String = source
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .lines()
            .joinToString("\n") { it.substringBefore("//") }

    private fun findById(element: Element, id: String): Element? {
        if (idOf(element) == id) return element
        val children = element.childNodes
        for (index in 0 until children.length) {
            val child = children.item(index) as? Element ?: continue
            findById(child, id)?.let { return it }
        }
        return null
    }

    private fun idOf(element: Element): String? = element
            .getAttributeNS(ANDROID_NS, "id")
            .takeIf { it.isNotEmpty() }
            ?.substringAfterLast('/')

    private fun layout(): File = resolve("res/layout/activity_main.xml")

    private fun activity(): File =
            resolve("java/com/svartifoss/snfell/watch/view/MainActivity.kt")

    private fun resolve(relative: String): File = listOf(
            File("src/main/$relative"),
            File("wear/src/main/$relative"))
            .first(File::isFile)

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
