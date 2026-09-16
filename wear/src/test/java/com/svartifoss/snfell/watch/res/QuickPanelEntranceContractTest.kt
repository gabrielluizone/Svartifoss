package com.svartifoss.snfell.watch.res

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The quick actions panel arrives the way the overlays around it do.
 *
 * Its backdrop has always faded in, and the volume ring fades in on its own, but the panel itself
 * was set straight to `alpha = 1f` and `VISIBLE` - so the busiest overlay in the app was the only
 * one that appeared instantly, on a scrim that was still arriving. It was reported as the panel
 * having no opening animation while its dismiss plainly had one.
 *
 * A source sweep rather than a behavioural test, for the reason [TapActionIconContractTest] is one:
 * what goes wrong here is never the animation itself but a later edit setting the frame's alpha
 * directly again, which compiles, runs, and silently restores the pop.
 */
class QuickPanelEntranceContractTest {

    private val host by lazy {
        repoFile("wear/src/main/java/com/svartifoss/snfell/watch/view/MainActivity.kt").readText()
    }

    @Test
    fun `opening the panel goes through the entrance animation`() {
        val show = functionBody(host, "showQuickActionsPanel")
        assertTrue("the panel must enter through enterQuickActionsPanel",
                show.contains("enterQuickActionsPanel()"))
        assertFalse(
                "showQuickActionsPanel must not place the frame itself - that is the pop this " +
                        "exists to prevent",
                show.contains("quickActionsDismissFrame.alpha") ||
                        show.contains("quickActionsDismissFrame.visibility"))
    }

    @Test
    fun `the entrance animates from transparent and slightly smaller`() {
        val enter = functionBody(host, "enterQuickActionsPanel")
        assertTrue("it must start transparent", enter.contains("alpha = 0f"))
        assertTrue("and settle in rather than pop", enter.contains("QUICK_PANEL_ENTER_SCALE"))
        assertTrue(enter.contains("scaleX(1f)") && enter.contains("scaleY(1f)"))
        // A second open while the panel is already up must not restart it from transparent: the
        // panel is re-rendered in place when its content changes, not only when it is first shown.
        assertTrue("re-entry must be distinguished from a first open",
                enter.contains("reopening"))
    }

    /**
     * Closing stays instant, and that is a decision rather than an omission.
     *
     * `isQuickActionsPanelShowing` reads the frame's visibility, so a fade-out would leave it
     * VISIBLE but invisible for the length of the animation - a window in which a tap aimed at the
     * player behind it would still be routed to a panel button nobody can see.
     */
    @Test
    fun `the frame is never left visible while transparent`() {
        val enter = functionBody(host, "enterQuickActionsPanel")
        assertFalse("the entrance must not fade the panel out",
                enter.contains("alpha(0f)"))
        assertTrue(
                "hiding must place the frame directly, without an exit animation",
                host.contains("binding.quickActionsDismissFrame.visibility = View.GONE"))
    }

    private fun functionBody(source: String, name: String): String {
        val start = Regex("""fun $name\(""").find(source)?.range?.first
                ?: throw AssertionError("$name is gone from MainActivity")
        val open = source.indexOf('{', start)
        var depth = 0
        for (i in open until source.length) {
            when (source[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(open, i + 1)
                }
            }
        }
        throw AssertionError("unbalanced body for $name")
    }

    private fun repoFile(relative: String): File =
            listOf(File("../$relative"), File(relative))
                    .firstOrNull { it.exists() }
                    ?: throw AssertionError("Could not locate $relative")
}
