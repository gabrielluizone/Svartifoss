package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the title colour's identity default.
 *
 * The whole promise of this control is that adding it changes nothing until the user picks
 * something: all thirteen Compose faces draw their title in their own white (several at different
 * alphas) and the classic face has its own, so a default of "follow the watch treatment" would
 * repaint every one of them on update.
 */
class TitleColorDefaultTest {

    @Test
    fun titleColorDefaultsToTheFacesOwnChoice() {
        assertEquals(
                MiscPreferences.TITLE_COLOR_FACE_DEFAULT,
                MiscPreferences.WEAR_TITLE_COLOR_MODE.defaultValue)
    }

    /**
     * "follow" already means "follow the watch-wide treatment" for the artist, progress ring,
     * volume and quick panel. The title's identity value has to be a distinct token or the two
     * meanings collide in one vocabulary.
     */
    @Test
    fun theIdentityValueIsNotTheTreatmentVocabularysFollow() {
        assertEquals("face", MiscPreferences.TITLE_COLOR_FACE_DEFAULT)
        assertTrue(MiscPreferences.TITLE_COLOR_FACE_DEFAULT != "follow")
    }

    /** Adaptive contrast stays opt-in, matching the artist's and the clock's. */
    @Test
    fun theContrastCorrectionIsOptInLikeItsSiblings() {
        assertFalse(MiscPreferences.WEAR_TITLE_ADAPTIVE_CONTRAST.defaultValue)
        assertFalse(MiscPreferences.WEAR_ARTIST_ADAPTIVE_CONTRAST.defaultValue)
        assertFalse(MiscPreferences.WEAR_CLOCK_ADAPTIVE_CONTRAST.defaultValue)
    }

    /**
     * All three must be exported and face-scoped, or a custom watch theme captures an incomplete
     * snapshot and a backup silently drops them - the failure the two registries exist to prevent.
     */
    @Test
    fun theNewTitleKeysAreExportedAndScoped() {
        listOf(
                MiscPreferences.WEAR_TITLE_COLOR_MODE,
                MiscPreferences.WEAR_TITLE_CUSTOM_COLOR,
                MiscPreferences.WEAR_TITLE_ADAPTIVE_CONTRAST
        ).forEach { definition ->
            assertTrue(
                    "${definition.key} must be exportable",
                    MiscPreferences.EXPORTABLE.any { it.key == definition.key })
            assertTrue(
                    "${definition.key} must be face-scoped",
                    FaceScopedPreferences.isScoped(definition.key))
        }
    }

    /** The same pairing for the clock's correction, added alongside. */
    @Test
    fun theClockContrastKeyIsExportedAndScoped() {
        val key = MiscPreferences.WEAR_CLOCK_ADAPTIVE_CONTRAST.key
        assertTrue(MiscPreferences.EXPORTABLE.any { it.key == key })
        assertTrue(FaceScopedPreferences.isScoped(key))
    }
}
