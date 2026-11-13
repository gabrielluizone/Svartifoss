package com.svartifoss.snfell.watch.communication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PositionViewersTest {

    private val player = Any()
    private val lyrics = Any()

    private val nothing = PositionViewers.Transition()

    @Test
    fun aScreenComingOnDisplayIsShowingAndMoving() {
        val viewers = PositionViewers()

        val change = viewers.setShowing(player, true)

        assertEquals(PositionViewers.Transition(firstArrived = true, liveStarted = true), change)
        assertTrue(viewers.any)
        assertTrue(viewers.anyLive)
    }

    /**
     * The case this class exists for. The player is still on display - its covers must stay
     * decoded, since a stem button skips from here - but it redraws once a minute, and nothing
     * should be asking the phone where playback is for that.
     */
    @Test
    fun goingAmbientStopsTheChecksAndKeepsTheScreen() {
        val viewers = PositionViewers()
        viewers.setShowing(player, true)

        val change = viewers.setLive(player, false)

        assertEquals(PositionViewers.Transition(liveEnded = true), change)
        assertTrue(viewers.any)
        assertFalse(viewers.anyLive)
    }

    /** The wrist coming up is the moment to check: the estimate ran unchecked the whole time. */
    @Test
    fun leavingAmbientStartsTheChecksAgainWithoutCountingAsANewScreen() {
        val viewers = PositionViewers()
        viewers.setShowing(player, true)
        viewers.setLive(player, false)

        val change = viewers.setLive(player, true)

        assertEquals(PositionViewers.Transition(liveStarted = true), change)
    }

    @Test
    fun aScreenLeavingTakesItsMovingReadoutWithIt() {
        val viewers = PositionViewers()
        viewers.setShowing(player, true)

        val change = viewers.setShowing(player, false)

        assertEquals(PositionViewers.Transition(liveEnded = true), change)
        assertFalse(viewers.any)
        assertFalse(viewers.anyLive)
    }

    /**
     * Ambient callbacks are not ordered against onStop. One that says "moving again" after its
     * screen has gone must not leave the watch asking the phone on behalf of nothing.
     */
    @Test
    fun aScreenThatIsNotShowingCannotBecomeLive() {
        val viewers = PositionViewers()
        viewers.setShowing(player, true)
        viewers.setShowing(player, false)

        val change = viewers.setLive(player, true)

        assertEquals(nothing, change)
        assertFalse(viewers.anyLive)
    }

    @Test
    fun aSecondScreenChangesNothingWhileTheFirstIsStillMoving() {
        val viewers = PositionViewers()
        viewers.setShowing(player, true)

        assertEquals(nothing, viewers.setShowing(lyrics, true))
        assertEquals(nothing, viewers.setLive(lyrics, false))
        // The player leaving while the lyrics are ambient is what ends the moving readout.
        assertEquals(PositionViewers.Transition(liveEnded = true), viewers.setShowing(player, false))
        assertFalse(viewers.anyLive)
        assertTrue(viewers.any)
    }

    /** The lyrics open over an ambient player: the lyric line moves, so the checks run. */
    @Test
    fun oneMovingScreenIsEnoughToKeepChecking() {
        val viewers = PositionViewers()
        viewers.setShowing(player, true)
        viewers.setLive(player, false)

        val opened = viewers.setShowing(lyrics, true)

        assertEquals(PositionViewers.Transition(liveStarted = true), opened)
        assertEquals(PositionViewers.Transition(liveEnded = true), viewers.setShowing(lyrics, false))
        assertTrue(viewers.any)
    }

    @Test
    fun repeatingACallChangesNothing() {
        val viewers = PositionViewers()
        viewers.setShowing(player, true)

        assertEquals(nothing, viewers.setShowing(player, true))
        assertEquals(nothing, viewers.setLive(player, true))
        viewers.setLive(player, false)
        assertEquals(nothing, viewers.setLive(player, false))
    }

    /**
     * A screen that registers again while it is still ambient would otherwise come back as moving.
     * Both screens that support ambient have to say so at that moment, and nothing but a reading of
     * their source can check it: the mistake compiles, runs, and costs only battery.
     */
    @Test
    fun theAmbientScreensSayTheyAreNotMovingWhenTheyComeBack() {
        val player = source("view/MainActivity.kt")
        val onStart = player.substringAfter("override fun onStart()").substringBefore("override fun onResume()")
        assertTrue("MainActivity.onStart must register the player as not live while ambient",
                Regex("""setShowingPosition\(true\)\s*\n\s*if \(inAmbient\) viewModel\.setPositionLive\(false\)""")
                        .containsMatchIn(onStart))

        val enter = player.substringAfter("override fun onEnterAmbient(").substringBefore("override fun onUpdateAmbient()")
        assertTrue("onEnterAmbient must stop the position checks",
                enter.contains("viewModel.setPositionLive(false)"))
        val exit = player.substringAfter("override fun onExitAmbient()").substringBefore("override fun onUserInteraction()")
        assertTrue("onExitAmbient must start the position checks again",
                exit.contains("viewModel.setPositionLive(true)"))

        val lyrics = source("view/lyrics/LyricsViewModel.kt")
        assertTrue("LyricsViewModel.setAmbient must tell the connection",
                lyrics.substringAfter("fun setAmbient(").substringBefore("fun refreshPosition()")
                        .contains("phoneConnection.setPositionViewerLive(this, !value)"))
        assertTrue("LyricsViewModel.setShowingPosition must not come back live while ambient",
                lyrics.substringAfter("fun setShowingPosition(").substringBefore("\n    }")
                        .contains("if (showing && ambient) phoneConnection.setPositionViewerLive(this, false)"))
    }

    /** The loop that asks the phone must not exist without a moving readout, nor send for one. */
    @Test
    fun thePositionCheckLoopFollowsTheMovingReadouts() {
        val connection = source("communication/PhoneConnection.kt")
        val schedule = connection.substringAfter("private fun scheduleNextPlaybackSync(")
                .substringBefore("fun requestPlaybackResync()")

        assertTrue("the loop must not be launched with no moving readout",
                schedule.substringBefore("scope.launch").contains("if (!positionViewers.anyLive)"))
        assertTrue("a check must only be sent for a moving readout",
                schedule.contains("playbackClock.isPlaying() && positionViewers.anyLive"))
    }

    private fun source(path: String): String {
        val relative = "src/main/java/com/svartifoss/snfell/watch/$path"
        return listOf(File(relative), File("wear/$relative")).first { it.isFile }.readText()
    }
}
