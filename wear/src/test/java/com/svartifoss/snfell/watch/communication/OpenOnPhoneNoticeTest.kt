package com.svartifoss.snfell.watch.communication

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * When the watch says "continue on your phone" for a shortcut the phone could not start silently.
 */
class OpenOnPhoneNoticeTest {

    @Test
    fun aPickThePhoneCouldNotStartSilentlyIsAnnounced() {
        assertTrue(OpenOnPhoneNotice.shouldShow(openOnly = false, hasFrontScreen = true, interactive = true))
    }

    @Test
    fun openOnPhoneIsNotAnnouncedTwice() {
        // Its own screen showed the confirmation when the pill was tapped.
        assertFalse(OpenOnPhoneNotice.shouldShow(openOnly = true, hasFrontScreen = true, interactive = true))
    }

    @Test
    fun nothingIsShownWithNoneOfTheAppsScreensInFront() {
        // A pick from the Tile: a confirmation there would bring the player forward over it.
        assertFalse(OpenOnPhoneNotice.shouldShow(openOnly = false, hasFrontScreen = false, interactive = true))
    }

    @Test
    fun aDimmedWatchIsNotLitForIt() {
        assertFalse(OpenOnPhoneNotice.shouldShow(openOnly = false, hasFrontScreen = true, interactive = false))
    }

    /**
     * The opener has to ask on the one path that carries a link from the phone, and with the kind
     * of the request that is outstanding - forgetting either compiles and merely goes quiet again.
     */
    @Test
    fun theOpenerAnnouncesTheLinkItWasAskedToOpen() {
        val source = sourceOf("PhoneUriOpener.kt")
        val verdict = source.substringAfter("fun onVerdict(").substringBefore("suspend fun openNow(")
        assertTrue("onVerdict must announce a link it is about to open",
                verdict.contains("OpenOnPhoneNotice.showIfWanted(appContext, pendingOpenOnly)"))
        assertTrue("the announcement comes before the open, which has Bluetooth to cross",
                verdict.indexOf("OpenOnPhoneNotice.showIfWanted") < verdict.indexOf("openNow(appContext, uri)"))
        val begin = source.substringAfter("fun requestOpenAfterPhoneTries(").substringBefore("fun onVerdict(")
        assertTrue("each request must record whether it is an Open on phone",
                begin.contains("pendingOpenOnly = openOnly"))
        // The late fallback for a phone that never answered opens the cached link, long after the
        // tap; a confirmation then would arrive out of nowhere.
        assertFalse(begin.contains("OpenOnPhoneNotice"))
    }

    private fun sourceOf(name: String): String {
        val relative = "src/main/java/com/svartifoss/snfell/watch/communication/$name"
        val file = listOf(File(relative), File("wear/$relative")).firstOrNull { it.isFile }
                ?: error("Cannot find $name from ${File("").absolutePath}")
        return file.readText()
    }
}
