package com.svartifoss.snfell.common

import com.svartifoss.snfell.common.PlayerChromeLayout.ClockStyle
import com.svartifoss.snfell.common.PlayerChromeLayout.Inputs
import com.svartifoss.snfell.common.PlayerChromeLayout.LowerContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The rules [PlayerChromeLayout] exists to hold, pinned as arithmetic rather than as appearance.
 *
 * Passing these says the bands are disjoint and the cross is symmetric. It does not say the result
 * is comfortable to read on a wrist - see `docs/player-layout-validation.md` for what only a device
 * settles.
 */
class PlayerChromeLayoutTest {

    private val screens = listOf(160f, 192f, 224f, 240f)

    private fun inputs(
            screen: Float = 192f,
            round: Boolean = true,
            face: String = "poster",
            quadrants: Set<Int> = emptySet(),
            hintsVisible: Boolean = true,
            hintScale: Float = 1f,
            clock: Boolean = false,
            lower: LowerContent? = null
    ) = Inputs(
            screenDp = screen,
            round = round,
            face = face,
            configuredQuadrants = quadrants,
            hintsVisible = hintsVisible,
            hintScale = hintScale,
            clockVisible = clock,
            lowerContent = lower)

    private val allQuadrants = setOf(
            ScreenQuadrant.TOP, ScreenQuadrant.BOTTOM, ScreenQuadrant.LEFT, ScreenQuadrant.RIGHT)

    // -------------------------------------------------------------------------------------
    // R1 - the cross
    // -------------------------------------------------------------------------------------

    @Test
    fun `the four hints are one size and one inset on every screen`() {
        for (screen in screens) {
            val chrome = PlayerChromeLayout.resolve(
                    inputs(screen = screen, quadrants = allQuadrants))
            assertEquals(4, chrome.hints.size)
            assertEquals(1, chrome.hints.map { it.sizeDp }.distinct().size)

            val bottom = chrome.hint(ScreenQuadrant.BOTTOM)!!
            val left = chrome.hint(ScreenQuadrant.LEFT)!!
            val right = chrome.hint(ScreenQuadrant.RIGHT)!!
            val bottomInset = (1f - bottom.centerYFraction) * screen - bottom.sizeDp / 2f
            val leftInset = left.centerXFraction * screen - left.sizeDp / 2f
            val rightInset = (1f - right.centerXFraction) * screen - right.sizeDp / 2f
            assertEquals("bottom vs left inset on ${screen}dp", bottomInset, leftInset, .01f)
            assertEquals("left vs right inset on ${screen}dp", leftInset, rightInset, .01f)
        }
    }

    @Test
    fun `the cross is centred on both axes`() {
        val chrome = PlayerChromeLayout.resolve(inputs(quadrants = allQuadrants))
        assertEquals(.5f, chrome.hint(ScreenQuadrant.TOP)!!.centerXFraction, .0001f)
        assertEquals(.5f, chrome.hint(ScreenQuadrant.BOTTOM)!!.centerXFraction, .0001f)
        assertEquals(.5f, chrome.hint(ScreenQuadrant.LEFT)!!.centerYFraction, .0001f)
        assertEquals(.5f, chrome.hint(ScreenQuadrant.RIGHT)!!.centerYFraction, .0001f)

        val left = chrome.hint(ScreenQuadrant.LEFT)!!.centerXFraction
        val right = chrome.hint(ScreenQuadrant.RIGHT)!!.centerXFraction
        assertEquals("left and right mirror about the vertical axis", .5f, (left + right) / 2f, .0001f)
    }

    @Test
    fun `a hint is never resized to make room for anything`() {
        val crowded = PlayerChromeLayout.resolve(inputs(
                screen = 160f,
                quadrants = allQuadrants,
                clock = true,
                lower = LowerContent(widthDp = 150f, heightDp = 42f)))
        val bare = PlayerChromeLayout.resolve(inputs(screen = 160f, quadrants = allQuadrants))
        assertEquals(PlayerChromeLayout.HINT_SIZE_DP, crowded.hints.first().sizeDp, .0001f)
        assertEquals(bare.hints.first().sizeDp, crowded.hints.first().sizeDp, .0001f)
    }

    @Test
    fun `the control style scales every hint together`() {
        val chrome = PlayerChromeLayout.resolve(
                inputs(quadrants = allQuadrants, hintScale = 1.08f))
        assertEquals(1, chrome.hints.map { it.sizeDp }.distinct().size)
        assertEquals(PlayerChromeLayout.HINT_SIZE_DP * 1.08f, chrome.hints.first().sizeDp, .0001f)
    }

    /**
     * The geometric promise: a hint's whole box is on the glass, corners included. This is what the
     * `bezelInsetFraction` floor is for, and it only binds when an icon is large relative to the
     * screen - where it does bind the box is tangent to the circle by construction.
     */
    @Test
    fun `every hint stays inside the round glass at every size`() {
        for (screen in screens) {
            for (scale in listOf(.78f, 1f, 1.08f, 2.5f)) {
                val chrome = PlayerChromeLayout.resolve(
                        inputs(screen = screen, quadrants = allQuadrants, hintScale = scale))
                for (hint in chrome.hints) {
                    val half = hint.sizeDp / (2f * screen)
                    val dx = abs(hint.centerXFraction - .5f) + half
                    val dy = abs(hint.centerYFraction - .5f) + half
                    val corner = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                    assertTrue(
                            "quadrant ${hint.quadrant} at ${screen}dp scale $scale left the glass" +
                                    " (corner radius $corner)",
                            corner <= .5f + .0005f)
                }
            }
        }
    }

    // -------------------------------------------------------------------------------------
    // R2 - the clock and the apex
    // -------------------------------------------------------------------------------------

    @Test
    fun `the clock keeps the straight apex when no top hint is drawn`() {
        val chrome = PlayerChromeLayout.resolve(
                inputs(quadrants = setOf(ScreenQuadrant.BOTTOM), clock = true))
        assertEquals(ClockStyle.STRAIGHT_APEX, chrome.clock)
        assertNull(chrome.hint(ScreenQuadrant.TOP))
    }

    @Test
    fun `the clock curves out to the arc when it has to share the top`() {
        val chrome = PlayerChromeLayout.resolve(
                inputs(quadrants = setOf(ScreenQuadrant.TOP), clock = true))
        assertEquals(ClockStyle.CURVED_ARC, chrome.clock)
    }

    @Test
    fun `a square screen stacks the hint under the clock instead of curving`() {
        val chrome = PlayerChromeLayout.resolve(
                inputs(round = false, quadrants = setOf(ScreenQuadrant.TOP), clock = true))
        assertEquals(ClockStyle.STRAIGHT_APEX, chrome.clock)
        val top = chrome.hint(ScreenQuadrant.TOP)!!
        val hintTop = top.centerYFraction * 192f - top.sizeDp / 2f
        assertTrue("the hint must start below the clock's band",
                hintTop >= chrome.clockBandDp + PlayerChromeLayout.CHROME_GAP_DP - .01f)
    }

    @Test
    fun `the top hint always clears the clock band, curved or straight`() {
        for (round in listOf(true, false)) {
            for (clockText in listOf(11f, 15f, 30f)) {
                val chrome = PlayerChromeLayout.resolve(
                        inputs(round = round, quadrants = setOf(ScreenQuadrant.TOP), clock = true)
                                .copy(clockTextDp = clockText))
                val top = chrome.hint(ScreenQuadrant.TOP)!!
                val hintTop = top.centerYFraction * 192f - top.sizeDp / 2f
                assertTrue("round=$round clock=${clockText}sp: hint overlapped the clock",
                        hintTop >= chrome.clockBandDp + PlayerChromeLayout.CHROME_GAP_DP - .01f)
            }
        }
    }

    @Test
    fun `a larger clock pushes the hint down rather than shrinking either`() {
        val small = PlayerChromeLayout.resolve(
                inputs(quadrants = setOf(ScreenQuadrant.TOP), clock = true).copy(clockTextDp = 12f))
        val large = PlayerChromeLayout.resolve(
                inputs(quadrants = setOf(ScreenQuadrant.TOP), clock = true).copy(clockTextDp = 24f))
        assertTrue(large.hint(ScreenQuadrant.TOP)!!.centerYFraction >
                small.hint(ScreenQuadrant.TOP)!!.centerYFraction)
        assertEquals(small.hint(ScreenQuadrant.TOP)!!.sizeDp,
                large.hint(ScreenQuadrant.TOP)!!.sizeDp, .0001f)
    }

    @Test
    fun `hiding the clock frees the apex entirely`() {
        val chrome = PlayerChromeLayout.resolve(inputs(quadrants = setOf(ScreenQuadrant.TOP)))
        assertEquals(ClockStyle.HIDDEN, chrome.clock)
        assertEquals(0f, chrome.clockBandDp, .0001f)
        val top = chrome.hint(ScreenQuadrant.TOP)!!
        assertEquals(PlayerChromeLayout.HINT_EDGE_INSET_DP,
                top.centerYFraction * 192f - top.sizeDp / 2f, .01f)
    }

    // -------------------------------------------------------------------------------------
    // R3 - the bottom band
    // -------------------------------------------------------------------------------------

    /**
     * The reported defect, as numbers: the row's chord margin shrinks with its width, so a single
     * configured button rested 9.6dp from the glass on a 192dp screen - inside the bottom hint.
     */
    @Test
    fun `a narrow row is lifted clear of the bottom hint`() {
        val oneButton = LowerContent(widthDp = 52f, heightDp = 42f)
        val withoutHint = PlayerChromeLayout.resolve(inputs(lower = oneButton))
        val withHint = PlayerChromeLayout.resolve(
                inputs(quadrants = setOf(ScreenQuadrant.BOTTOM), lower = oneButton))

        assertTrue("the bare chord margin is what used to collide",
                withoutHint.lowerMarginDp < 12f)
        val hint = withHint.hint(ScreenQuadrant.BOTTOM)!!
        val hintTop = (1f - hint.centerYFraction) * 192f + hint.sizeDp / 2f
        assertTrue("the row must rest above the hint", withHint.lowerMarginDp >= hintTop)
    }

    @Test
    fun `a wide row keeps following the bezel chord`() {
        val threeButtons = LowerContent(widthDp = 150f, heightDp = 42f)
        val withHint = PlayerChromeLayout.resolve(
                inputs(quadrants = setOf(ScreenQuadrant.BOTTOM), lower = threeButtons))
        val expectedChord = PlayerChromeLayout.bezelInsetDp(192f, 150f) +
                PlayerChromeLayout.CHROME_GAP_DP
        assertTrue("a wide row is already above the hint, so the chord governs",
                expectedChord > 34f)
        assertEquals(expectedChord, withHint.lowerMarginDp, .01f)
    }

    @Test
    fun `the row and the bottom hint never overlap, at any width or screen`() {
        for (screen in screens) {
            for (width in listOf(38f, 52f, 96f, 150f, 220f)) {
                val chrome = PlayerChromeLayout.resolve(inputs(
                        screen = screen,
                        quadrants = setOf(ScreenQuadrant.BOTTOM),
                        lower = LowerContent(widthDp = width, heightDp = 42f)))
                val hint = chrome.hint(ScreenQuadrant.BOTTOM)!!
                val hintTop = (1f - hint.centerYFraction) * screen + hint.sizeDp / 2f
                assertTrue("${screen}dp / ${width}dp row overlapped the hint",
                        chrome.lowerMarginDp >= hintTop - .01f)
            }
        }
    }

    @Test
    fun `the Up Next pill keeps its designed margin instead of following the chord`() {
        val pill = LowerContent.upNextPill(192f)
        val chrome = PlayerChromeLayout.resolve(inputs(lower = pill))
        assertEquals(192f * FaceGeometry.UpNextPill.BOTTOM_MARGIN_FRACTION,
                chrome.lowerMarginDp, .001f)
        // Nearly screen-wide, so the chord would drop it a long way up the face.
        assertTrue("the chord answer is the one this exists to refuse",
                PlayerChromeLayout.bezelInsetDp(192f, pill.widthDp) > chrome.lowerMarginDp)
    }

    @Test
    fun `the pill still clears the bottom hint`() {
        val pill = LowerContent.upNextPill(192f)
        val chrome = PlayerChromeLayout.resolve(
                inputs(quadrants = setOf(ScreenQuadrant.BOTTOM), lower = pill))
        val hint = chrome.hint(ScreenQuadrant.BOTTOM)!!
        val hintTop = (1f - hint.centerYFraction) * 192f + hint.sizeDp / 2f
        assertTrue("a designed margin is not a licence to sit on the hint",
                chrome.lowerMarginDp >= hintTop)
    }

    @Test
    fun `the pill reserves its own height plus its margin`() {
        val pill = LowerContent.upNextPill(192f)
        val chrome = PlayerChromeLayout.resolve(inputs(lower = pill))
        assertEquals(chrome.lowerMarginDp + pill.heightDp + PlayerChromeLayout.CHROME_GAP_DP,
                chrome.safeArea.bottomDp, .001f)
    }

    @Test
    fun `the band's content is told where it rests, not what it reserves`() {
        val pill = LowerContent.upNextPill(192f)
        val chrome = PlayerChromeLayout.resolve(
                inputs(quadrants = setOf(ScreenQuadrant.BOTTOM), lower = pill))
        assertEquals(chrome.lowerMarginDp, chrome.safeArea.lowerContentMarginDp, .0001f)
        assertTrue("reading the reserve would push the pill up by its own height",
                chrome.safeArea.bottomDp > chrome.safeArea.lowerContentMarginDp)
    }

    @Test
    fun `a rail or a face-hosted row reserves no bottom band`() {
        val rail = LowerContent.row(150f, 42f, MiniButtonPlacement.SIDE_RIGHT, hostedByFace = false)
        val hosted = LowerContent.row(150f, 42f, MiniButtonPlacement.FLAT, hostedByFace = true)
        for (content in listOf(rail, hosted)) {
            val chrome = PlayerChromeLayout.resolve(inputs(face = "chat", lower = content))
            assertEquals(0f, chrome.safeArea.bottomDp, .0001f)
        }
    }

    @Test
    fun `a bottom hint alone still reserves its own band`() {
        val chrome = PlayerChromeLayout.resolve(inputs(quadrants = setOf(ScreenQuadrant.BOTTOM)))
        assertEquals(PlayerChromeLayout.HINT_EDGE_INSET_DP + PlayerChromeLayout.HINT_SIZE_DP +
                PlayerChromeLayout.CHROME_GAP_DP, chrome.safeArea.bottomDp, .01f)
    }

    // -------------------------------------------------------------------------------------
    // R4/R5 - the safe area
    // -------------------------------------------------------------------------------------

    @Test
    fun `an empty screen reserves nothing`() {
        val chrome = PlayerChromeLayout.resolve(inputs())
        assertEquals(0f, chrome.safeArea.topDp, .0001f)
        assertEquals(0f, chrome.safeArea.bottomDp, .0001f)
        assertEquals(0f, chrome.safeArea.sideDp, .0001f)
    }

    @Test
    fun `adding chrome never reduces the reserve`() {
        val bare = PlayerChromeLayout.resolve(inputs())
        val clocked = PlayerChromeLayout.resolve(inputs(clock = true))
        val hinted = PlayerChromeLayout.resolve(inputs(quadrants = allQuadrants, clock = true))
        val full = PlayerChromeLayout.resolve(inputs(
                quadrants = allQuadrants, clock = true,
                lower = LowerContent(widthDp = 150f, heightDp = 42f)))

        assertTrue(clocked.safeArea.topDp >= bare.safeArea.topDp)
        assertTrue(hinted.safeArea.topDp >= clocked.safeArea.topDp)
        assertTrue(hinted.safeArea.sideDp > bare.safeArea.sideDp)
        assertTrue(full.safeArea.bottomDp > hinted.safeArea.bottomDp)
    }

    @Test
    fun `the top reserve covers whatever is actually at the top`() {
        val clockOnly = PlayerChromeLayout.resolve(inputs(clock = true))
        assertEquals(clockOnly.clockBandDp + PlayerChromeLayout.CHROME_GAP_DP,
                clockOnly.safeArea.topDp, .01f)

        val both = PlayerChromeLayout.resolve(
                inputs(quadrants = setOf(ScreenQuadrant.TOP), clock = true))
        val top = both.hint(ScreenQuadrant.TOP)!!
        assertEquals(top.centerYFraction * 192f + top.sizeDp / 2f +
                PlayerChromeLayout.CHROME_GAP_DP, both.safeArea.topDp, .01f)
    }

    @Test
    fun `the side reserve is symmetric even when only one side is configured`() {
        val leftOnly = PlayerChromeLayout.resolve(inputs(quadrants = setOf(ScreenQuadrant.LEFT)))
        val both = PlayerChromeLayout.resolve(
                inputs(quadrants = setOf(ScreenQuadrant.LEFT, ScreenQuadrant.RIGHT)))
        assertEquals(both.safeArea.sideDp, leftOnly.safeArea.sideDp, .0001f)
    }

    @Test
    fun `Immersive lets the row draw over it but still clears the bottom hint`() {
        val lower = LowerContent(widthDp = 150f, heightDp = 42f)
        val immersive = PlayerChromeLayout.resolve(
                inputs(face = "immersive", quadrants = setOf(ScreenQuadrant.BOTTOM), lower = lower))
        val poster = PlayerChromeLayout.resolve(
                inputs(face = "poster", quadrants = setOf(ScreenQuadrant.BOTTOM), lower = lower))
        val hintOnly = PlayerChromeLayout.resolve(
                inputs(face = "immersive", quadrants = setOf(ScreenQuadrant.BOTTOM)))

        assertTrue("the row's own band is declined", immersive.safeArea.bottomDp <
                poster.safeArea.bottomDp)
        assertEquals("the hint is still cleared",
                hintOnly.safeArea.bottomDp, immersive.safeArea.bottomDp, .0001f)
        assertTrue(immersive.safeArea.bottomDp > 0f)
        assertEquals("declining the band must not move the chrome itself",
                poster.lowerMarginDp, immersive.lowerMarginDp, .0001f)
        assertNotNull(immersive.hint(ScreenQuadrant.BOTTOM))
    }

    @Test
    fun `a declining face with no bottom hint reserves nothing at all`() {
        val chrome = PlayerChromeLayout.resolve(inputs(
                face = "immersive", lower = LowerContent(widthDp = 150f, heightDp = 42f)))
        assertEquals(0f, chrome.safeArea.bottomDp, .0001f)
    }

    // -------------------------------------------------------------------------------------
    // R8 - who draws the side arms
    // -------------------------------------------------------------------------------------

    @Test
    fun `faces whose transport is the side affordance draw no side hints`() {
        for (face in listOf("expressive", "material")) {
            val chrome = PlayerChromeLayout.resolve(inputs(face = face, quadrants = allQuadrants))
            assertNull("$face drew a left hint over its own prev button",
                    chrome.hint(ScreenQuadrant.LEFT))
            assertNull(chrome.hint(ScreenQuadrant.RIGHT))
            assertNotNull(chrome.hint(ScreenQuadrant.TOP))
            assertNotNull(chrome.hint(ScreenQuadrant.BOTTOM))
            assertEquals(0f, chrome.safeArea.sideDp, .0001f)
        }
    }

    @Test
    fun `every other face draws the full cross`() {
        val faces = ThemeAppearance.ALLOWED_BASE_FACES - ArchivedFaces.KEYS -
                setOf("expressive", "material")
        for (face in faces) {
            val chrome = PlayerChromeLayout.resolve(inputs(face = face, quadrants = allQuadrants))
            assertEquals("$face did not draw all four hints", 4, chrome.hints.size)
        }
    }

    @Test
    fun `hiding the controls removes every hint and returns the apex to the clock`() {
        val chrome = PlayerChromeLayout.resolve(
                inputs(quadrants = allQuadrants, hintsVisible = false, clock = true))
        assertTrue(chrome.hints.isEmpty())
        assertEquals(ClockStyle.STRAIGHT_APEX, chrome.clock)
        assertEquals(0f, chrome.safeArea.sideDp, .0001f)
        assertEquals(0f, chrome.safeArea.bottomDp, .0001f)
    }

    @Test
    fun `an unconfigured quadrant draws nothing`() {
        val chrome = PlayerChromeLayout.resolve(inputs(quadrants = setOf(ScreenQuadrant.TOP)))
        assertEquals(1, chrome.hints.size)
        assertEquals(ScreenQuadrant.TOP, chrome.hints.single().quadrant)
    }

    // -------------------------------------------------------------------------------------
    // The circle equation
    // -------------------------------------------------------------------------------------

    @Test
    fun `bezelInsetFraction inverts halfChordAt`() {
        for (halfWidth in listOf(.05f, .1f, .2f, .35f, .49f)) {
            val inset = PlayerChromeLayout.bezelInsetFraction(halfWidth)
            // The chord at that depth is exactly wide enough to hold the thing that asked for it.
            assertEquals("half-width $halfWidth", halfWidth,
                    RoundScreenText.halfChordAt(inset), .0005f)
        }
    }

    @Test
    fun `bezelInsetFraction is clamped rather than undefined past the diameter`() {
        assertEquals(0f, PlayerChromeLayout.bezelInsetFraction(0f), .0001f)
        assertEquals(.5f, PlayerChromeLayout.bezelInsetFraction(.5f), .0001f)
        assertEquals(.5f, PlayerChromeLayout.bezelInsetFraction(4f), .0001f)
        assertEquals(0f, PlayerChromeLayout.bezelInsetFraction(-1f), .0001f)
    }

    // -------------------------------------------------------------------------------------
    // The curved clock's arc
    // -------------------------------------------------------------------------------------

    @Test
    fun `the curved clock keeps its text the same distance from the glass at any size`() {
        for (ascent in listOf(9f, 12f, 24f)) {
            val radius = PlayerChromeLayout.curvedClockBaselineRadiusDp(192f, ascent)
            val textTopRadius = radius + ascent
            assertEquals("ascent $ascent", 96f - PlayerChromeLayout.CURVED_CLOCK_EDGE_INSET_DP,
                    textTopRadius, .0001f)
        }
    }

    @Test
    fun `the curved clock run is centred on the apex whatever its length`() {
        val radius = PlayerChromeLayout.curvedClockBaselineRadiusDp(192f, 12f)
        for (width in listOf(20f, 38f, 64f)) {
            val sweep = PlayerChromeLayout.curvedClockSweepDegrees(width, radius)
            val start = PlayerChromeLayout.curvedClockStartAngleDegrees(sweep)
            assertEquals("width $width is not centred on 12 o'clock", 270f, start + sweep / 2f, .0001f)
        }
    }

    @Test
    fun `a wider clock subtends a wider arc`() {
        val radius = PlayerChromeLayout.curvedClockBaselineRadiusDp(192f, 12f)
        assertTrue(PlayerChromeLayout.curvedClockSweepDegrees(64f, radius) >
                PlayerChromeLayout.curvedClockSweepDegrees(38f, radius))
    }

    @Test
    fun `the curved clock arc survives degenerate input`() {
        assertEquals(0f, PlayerChromeLayout.curvedClockSweepDegrees(40f, 0f), .0001f)
        assertEquals(0f, PlayerChromeLayout.curvedClockSweepDegrees(-5f, 90f), .0001f)
        assertTrue(PlayerChromeLayout.curvedClockBaselineRadiusDp(0f, 12f) > 0f)
        assertTrue(PlayerChromeLayout.curvedClockSweepDegrees(10_000f, 90f) <= 360f)
    }

    @Test
    fun `a square screen gives the lower content a flat margin`() {
        val chrome = PlayerChromeLayout.resolve(
                inputs(round = false, lower = LowerContent(widthDp = 52f, heightDp = 42f)))
        assertEquals(PlayerChromeLayout.SQUARE_LOWER_MARGIN_DP, chrome.lowerMarginDp, .0001f)
    }

    @Test
    fun `a zero-sized screen cannot divide by zero`() {
        val chrome = PlayerChromeLayout.resolve(
                inputs(screen = 0f, quadrants = allQuadrants, clock = true,
                        lower = LowerContent(widthDp = 52f, heightDp = 42f)))
        for (hint in chrome.hints) {
            assertTrue(hint.centerXFraction.isFinite())
            assertTrue(hint.centerYFraction.isFinite())
        }
        assertTrue(chrome.safeArea.topDp.isFinite())
        assertTrue(chrome.lowerMarginDp.isFinite())
    }
}
