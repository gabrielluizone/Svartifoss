package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins how a stored app language becomes the locale actually applied.
 *
 * English is the case that broke: stored as a bare "en", applied as a bare "en", Android 10 resolved
 * its own strings to the en-XC test pseudo-locale, whose "Cancel" carries about a hundred invisible
 * direction marks - wide enough to push the action editor's Delete button out of its dialog.
 */
class AppLocalesTest {

    @Test
    fun englishIsAppliedWithARegionSoTheSystemNeverPicksAPseudoLocale() {
        assertEquals("en-US", AppLocales.localeFor("en")!!.toLanguageTag())
    }

    @Test
    fun theStoredValueStaysWhatThePickerOffers() {
        // Only the applied locale changes; the preference keeps "en", so backups, the watch and the
        // picker's checked row all go on reading the value they always did.
        assertEquals("en", AppLocales.normalize("en"))
    }

    @Test
    fun everyOtherLanguageIsAppliedAsStored() {
        AppLocales.SUPPORTED.filter { it != "en" }.forEach { tag ->
            assertEquals(tag, AppLocales.localeFor(tag)!!.toLanguageTag())
        }
    }

    @Test
    fun followingTheSystemAndUnknownTagsApplyNothing() {
        assertNull(AppLocales.localeFor(AppLocales.SYSTEM))
        assertNull(AppLocales.localeFor(null))
        assertNull(AppLocales.localeFor(""))
        assertNull(AppLocales.localeFor("en-XC"))
        assertNull(AppLocales.localeFor("xx"))
    }
}
