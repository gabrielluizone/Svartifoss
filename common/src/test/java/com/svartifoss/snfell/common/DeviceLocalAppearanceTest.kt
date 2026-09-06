package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The registry that decides which themes can never be published.
 *
 * Every failure here is silent in the same direction: a value that stops being recognised as
 * device-local becomes submittable, the gallery accepts a theme whose font file and background
 * picture are not in it, and the person who installs it sees a typeface and a photograph they have
 * never had - with nothing about the result looking wrong.
 */
class DeviceLocalAppearanceTest {

    @Test
    fun `the album-art half is derived from the source registry, not retyped`() {
        assertEquals(
                AlbumArtSource.entries.filter { it.isDeviceLocal }
                        .map { it.preferenceValue }
                        .toSet(),
                DeviceLocalAppearance.VALUES_BY_KEY
                        .getValue(MiscPreferences.WEAR_ALBUM_ART_SOURCE.key))
    }

    @Test
    fun `all six font controls can hold the imported font`() {
        // The global family plus the five per-element overrides. A control missing from this set is
        // one through which a theme can carry `user_font` past the submission gate.
        assertEquals(6, DeviceLocalAppearance.FONT_KEYS.size)
        DeviceLocalAppearance.FONT_KEYS.forEach { key ->
            assertTrue(
                    "$key must accept the imported font",
                    DeviceLocalAppearance.isDeviceLocal(key, DeviceLocalAppearance.USER_FONT_KEY))
        }
    }

    @Test
    fun `the check is pairing-sensitive, never a bare set membership`() {
        // The reason the archived-option exemption is per key too: a value that is device-local for
        // one setting is an ordinary published value for another, and a set-wide test would refuse
        // themes that are perfectly shareable.
        assertFalse(
                DeviceLocalAppearance.isDeviceLocal(
                        MiscPreferences.WEAR_ALBUM_ART_SOURCE.key,
                        DeviceLocalAppearance.USER_FONT_KEY))
        assertFalse(
                DeviceLocalAppearance.isDeviceLocal(
                        MiscPreferences.WEAR_FONT.key,
                        AlbumArtSource.CUSTOM_IMAGE.preferenceValue))
    }

    @Test
    fun `ordinary values and unknown keys are shareable`() {
        listOf(
                MiscPreferences.WEAR_FONT.key to "google_sans",
                MiscPreferences.WEAR_FONT.key to null,
                MiscPreferences.WEAR_ALBUM_ART_SOURCE.key to "artist",
                MiscPreferences.WEAR_SCREEN_FACE.key to "verse",
                "not_a_setting" to "user_font"
        ).forEach { (key, value) ->
            assertFalse("$key=$value must be publishable",
                    DeviceLocalAppearance.isDeviceLocal(key, value))
        }
    }

    @Test
    fun `a theme is reported private by naming the setting responsible`() {
        val found = DeviceLocalAppearance.firstDeviceLocal(mapOf(
                MiscPreferences.WEAR_SCREEN_FACE.key to "poster",
                MiscPreferences.WEAR_TITLE_FONT.key to DeviceLocalAppearance.USER_FONT_KEY))
        assertEquals(
                MiscPreferences.WEAR_TITLE_FONT.key to DeviceLocalAppearance.USER_FONT_KEY,
                found)
    }

    @Test
    fun `a theme with nothing device-local reports nothing`() {
        assertNull(DeviceLocalAppearance.firstDeviceLocal(mapOf(
                MiscPreferences.WEAR_FONT.key to "lobster",
                MiscPreferences.WEAR_ALBUM_ART_SOURCE.key to "local")))
        assertNull(DeviceLocalAppearance.firstDeviceLocal(emptyMap()))
    }

    @Test
    fun `the imported font key cannot collide with a colour mode`() {
        // `custom` is what every colour control in the app calls an explicitly picked value, and a
        // font key spelled the same way is the kind of thing a migration mistakes for the other.
        assertFalse(DeviceLocalAppearance.USER_FONT_KEY == "custom")
        assertTrue(DeviceLocalAppearance.isUserFont(DeviceLocalAppearance.USER_FONT_KEY))
        assertFalse(DeviceLocalAppearance.isUserFont("custom"))
        assertFalse(DeviceLocalAppearance.isUserFont(null))
    }

    @Test
    fun `every listed value belongs to a key that is a real preference`() {
        // A key renamed in MiscPreferences and not here would leave this registry guarding a
        // setting nothing writes, and the real one unguarded.
        val known = MiscPreferences.EXPORTABLE.map { it.key }.toSet()
        DeviceLocalAppearance.VALUES_BY_KEY.keys.forEach { key ->
            assertTrue("$key is not an exportable preference", key in known)
        }
    }

    @Test
    fun `ALL_VALUES is the flattening of the pairings`() {
        assertEquals(
                DeviceLocalAppearance.VALUES_BY_KEY.values.flatten().toSet(),
                DeviceLocalAppearance.ALL_VALUES)
        assertTrue(DeviceLocalAppearance.USER_FONT_KEY in DeviceLocalAppearance.ALL_VALUES)
        assertTrue(
                AlbumArtSource.CUSTOM_FOLDER.preferenceValue in DeviceLocalAppearance.ALL_VALUES)
    }
}
