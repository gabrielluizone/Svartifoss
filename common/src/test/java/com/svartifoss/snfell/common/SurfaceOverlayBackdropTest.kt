package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins [OverlayBackdropResolver.resolveSurface] - the per-surface background's deferral.
 *
 * Five surfaces can now each keep the shared background or name their own, and the promise that
 * nothing changes until one is named lives entirely here. The trap this guards is specific:
 * [OverlayBackdrop.fromPreference] funnels *every* unrecognised string into
 * [OverlayBackdrop.FOLLOW_STYLE], so without a deliberate check a value from an imported backup,
 * a community theme or a newer build would silently become "follow my own style" - a real, and
 * different, background - rather than the deferral the default expresses.
 */
class SurfaceOverlayBackdropTest {

    @Test
    fun theSentinelDefersToTheSharedChoice() {
        assertEquals(
                OverlayBackdrop.SOLID_ALBUM,
                OverlayBackdropResolver.resolveSurface(
                        OverlayBackdropResolver.SHARED, "album", contentStyle = null))
    }

    @Test
    fun aNamedBackgroundOverridesTheSharedChoice() {
        assertEquals(
                OverlayBackdrop.SOLID_BLACK,
                OverlayBackdropResolver.resolveSurface("black", "album", contentStyle = null))
    }

    /**
     * Deferring reaches the shared key's *own* "follow" behaviour, rather than short-circuiting
     * it: the shared choice may itself be "derive from this surface's style".
     */
    @Test
    fun deferringStillHonoursTheSharedFollowStyleOption() {
        assertEquals(
                OverlayBackdrop.GLASS,
                OverlayBackdropResolver.resolveSurface(
                        OverlayBackdropResolver.SHARED, "follow", contentStyle = "glass"))
    }

    /** A surface may name "follow" for itself, which is not the same as deferring. */
    @Test
    fun aSurfaceMayFollowItsOwnStyleWhileTheSharedChoiceIsFixed() {
        assertEquals(
                OverlayBackdrop.GLASS,
                OverlayBackdropResolver.resolveSurface("follow", "black", contentStyle = "glass"))
        assertFalse(OverlayBackdropResolver.followsShared("follow"))
    }

    @Test
    fun unknownEmptyAndMissingValuesAllDefer() {
        listOf(null, "", "   ", "chartreuse").forEach { value ->
            assertTrue(
                    "followsShared($value) must defer rather than become FOLLOW_STYLE",
                    OverlayBackdropResolver.followsShared(value))
            assertEquals(
                    "resolveSurface($value) must fall back to the shared choice",
                    OverlayBackdrop.SOLID_BLACK,
                    OverlayBackdropResolver.resolveSurface(value, "black", contentStyle = "glass"))
        }
    }

    /** The default stored on all five preferences has to be the sentinel this resolver honours. */
    @Test
    fun theShippedDefaultIsTheSharedSentinel() {
        listOf(
                MiscPreferences.WEAR_VOLUME_BACKDROP_STYLE,
                MiscPreferences.WEAR_PROGRESS_BACKDROP_STYLE,
                MiscPreferences.WEAR_QUICK_PANEL_BACKDROP_STYLE,
                MiscPreferences.WEAR_QUEUE_BACKDROP_STYLE,
                MiscPreferences.WEAR_LYRICS_BACKDROP_STYLE
        ).forEach { definition ->
            assertEquals(
                    "${definition.key} must default to the shared sentinel",
                    OverlayBackdropResolver.SHARED,
                    definition.defaultValue)
        }
    }
}
