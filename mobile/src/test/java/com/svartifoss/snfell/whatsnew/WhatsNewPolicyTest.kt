package com.svartifoss.snfell.whatsnew

import com.svartifoss.snfell.whatsnew.WhatsNewPolicy.Action
import com.svartifoss.snfell.whatsnew.WhatsNewPolicy.NEVER_RECORDED
import org.junit.Assert.assertEquals
import org.junit.Test

class WhatsNewPolicyTest {

    private val firstWithNotes = 95L

    private fun decide(lastSeen: Long, current: Long, update: Boolean) =
            WhatsNewPolicy.decide(lastSeen, current, firstWithNotes, update)

    @Test
    fun anUpdateFromABuildThatNeverRecordedAVersionShowsTheNotes() {
        // 4.3.1 (92) did not have this code, so nothing is recorded, and the OS says the install
        // has been updated. This is the person the window is for.
        assertEquals(Action.SHOW, decide(NEVER_RECORDED, 95, update = true))
    }

    @Test
    fun aFreshInstallIsRecordedAndShownNothing() {
        assertEquals(Action.REMEMBER, decide(NEVER_RECORDED, 95, update = false))
    }

    @Test
    fun aVersionAlreadyRecordedIsLeftAlone() {
        assertEquals(Action.NOTHING, decide(95, 95, update = true))
        assertEquals(Action.NOTHING, decide(95, 95, update = false))
    }

    @Test
    fun anUpdateFromBeforeTheNotesShowsThemWhateverTheJump() {
        assertEquals(Action.SHOW, decide(92, 95, update = true))
        assertEquals(Action.SHOW, decide(94, 95, update = true))
        // Skipping releases in between still lands on the latest notes.
        assertEquals(Action.SHOW, decide(80, 99, update = true))
    }

    @Test
    fun aPatchAfterTheNotesIsRecordedAndStaysQuiet() {
        assertEquals(Action.REMEMBER, decide(95, 96, update = true))
        assertEquals(Action.REMEMBER, decide(96, 100, update = true))
    }

    @Test
    fun aBuildOlderThanTheNotesCannotShowThem() {
        // A downgrade, or an older build sideloaded over the top: it does not carry the notes.
        assertEquals(Action.REMEMBER, decide(NEVER_RECORDED, 93, update = true))
        assertEquals(Action.REMEMBER, decide(92, 93, update = true))
    }

    @Test
    fun aDowngradeThenAnUpdateShowsTheNotesAgainOnlyIfTheyWereNeverSeen() {
        // Recorded 95, went back to 93 (recorded as 93), updated again to 95: the notes are shown
        // once more, because the last version seen is now older than the release that carries them.
        assertEquals(Action.REMEMBER, decide(95, 93, update = true))
        assertEquals(Action.SHOW, decide(93, 95, update = true))
    }

    @Test
    fun theFirstBuildThatCarriesTheNotesIsTheBoundary() {
        assertEquals(Action.SHOW, WhatsNewPolicy.decide(94, 95, 95, true))
        assertEquals(Action.NOTHING, WhatsNewPolicy.decide(94, 94, 95, true))
        assertEquals(Action.NOTHING, WhatsNewPolicy.decide(95, 95, 95, true))
    }
}
