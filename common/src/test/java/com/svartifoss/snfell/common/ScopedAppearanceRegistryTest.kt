package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the registry membership every replaceable theme piece depends on.
 *
 * A face-scoped appearance key has to be in **both** registries to work at all, and neither
 * omission announces itself:
 *
 *  - missing from [FaceScopedPreferences.SCOPED_KEYS], it silently changes every face at once
 *    (what `AppearancePreferenceScopingTest` guards from the settings-XML side);
 *  - missing from [MiscPreferences.EXPORTABLE], the phone happily stores it and nothing else
 *    happens - `WatchPreferenceSyncCoordinator` filters on that registry, so the value never
 *    crosses to the watch, and `SCOPED_DEFINITIONS` is the intersection of the two, so a saved
 *    theme cannot capture it either. The setting appears to do nothing.
 *
 * The second failure is the quiet one, and it is what makes this worth a test rather than a
 * convention: the phone's own preview reads the value straight out of SharedPreferences and
 * therefore looks completely correct while the wrist ignores it.
 */
class ScopedAppearanceRegistryTest {

    @Test
    fun `every face-scoped appearance key is exportable`() {
        val exportable = MiscPreferences.EXPORTABLE.mapTo(HashSet()) { it.key }
        val orphans = (FaceScopedPreferences.SCOPED_KEYS - exportable).sorted()

        assertEquals(
                "These face-scoped keys are missing from MiscPreferences.EXPORTABLE, so they " +
                        "never reach the watch and no saved theme can carry them: $orphans",
                emptyList<String>(),
                orphans)
    }

    /**
     * The intersection is what a theme profile actually materialises, so a key absent from it is a
     * piece of the puzzle that cannot be saved - see WatchThemeRepository.
     */
    @Test
    fun `scoped definitions cover every scoped key`() {
        val defined = FaceScopedPreferences.SCOPED_DEFINITIONS.mapTo(HashSet()) { it.key }
        val missing = (FaceScopedPreferences.SCOPED_KEYS - defined).sorted()

        assertEquals(
                "These scoped keys have no PreferenceDefinition behind them, so theme capture " +
                        "and import drop them silently: $missing",
                emptyList<String>(),
                missing)
    }

    /** The typeface controls specifically, since each was added as a separate replaceable piece
     *  and each is invisible-when-broken in exactly the way described above. */
    @Test
    fun `the typeface pieces are all registered`() {
        val defined = FaceScopedPreferences.SCOPED_DEFINITIONS.mapTo(HashSet()) { it.key }
        listOf(
                MiscPreferences.WEAR_FONT.key,
                MiscPreferences.WEAR_FONT_ALL_SCREENS.key,
                MiscPreferences.WEAR_CLOCK_FONT.key,
                MiscPreferences.WEAR_LYRICS_FONT.key
        ).forEach { key ->
            assertTrue("$key is not a saveable, syncable theme piece", key in defined)
        }
    }
}
