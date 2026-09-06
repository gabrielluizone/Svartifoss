package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The source fallback, and the translation of the value this setting used to be.
 *
 * Both are the kind of decision that fails silently: an unreadable source that resolved to a
 * network lookup would make an imported theme fetch pictures nobody asked for, and a retired style
 * left untranslated leaves a settings row showing a raw string with nothing selected in its picker.
 */
class AlbumArtSourceTest {

    @Test
    fun `named values round-trip through their preference strings`() {
        AlbumArtSource.entries.forEach { value ->
            assertEquals(value, AlbumArtSource.fromPref(value.preferenceValue))
        }
    }

    @Test
    fun `an absent or unreadable value never resolves to a source that leaves the device`() {
        listOf(null, "", "   ", "gallery", "LOCAL", "deezer").forEach { stored ->
            val resolved = AlbumArtSource.fromPref(stored)
            assertEquals("$stored must resolve to the offline source", AlbumArtSource.LOCAL, resolved)
            assertFalse("$stored must not resolve to a lookup", resolved.needsLookup)
        }
    }

    @Test
    fun `surrounding whitespace is tolerated`() {
        assertEquals(AlbumArtSource.ARTIST, AlbumArtSource.fromPref("  artist "))
    }

    @Test
    fun `only the two online sources report needing a lookup`() {
        assertFalse(AlbumArtSource.LOCAL.needsLookup)
        assertTrue(AlbumArtSource.ONLINE.needsLookup)
        assertTrue(AlbumArtSource.ARTIST.needsLookup)
        // The device-local pair resolve a file already on the phone. `needsLookup` used to be
        // "anything but LOCAL", and left that way it would have routed both through the network
        // gate - a connectivity preference refusing to draw a picture out of the gallery.
        assertFalse(AlbumArtSource.CUSTOM_IMAGE.needsLookup)
        assertFalse(AlbumArtSource.CUSTOM_FOLDER.needsLookup)
    }

    @Test
    fun `only the two picked sources are device-local`() {
        assertTrue(AlbumArtSource.CUSTOM_IMAGE.isDeviceLocal)
        assertTrue(AlbumArtSource.CUSTOM_FOLDER.isDeviceLocal)
        listOf(AlbumArtSource.LOCAL, AlbumArtSource.ONLINE, AlbumArtSource.ARTIST).forEach {
            assertFalse("$it is reproducible on any phone", it.isDeviceLocal)
        }
    }

    @Test
    fun `every source but the local one travels as the backdrop asset`() {
        // The watch decides whether to draw the backdrop from this property, and it must not be
        // `needsLookup`. The two agreed while every non-local source was a network lookup, which is
        // what made the wrong one look correct - and then a source that resolves a file on the
        // phone sent its picture through the very same asset, the watch asked about lookups, and
        // threw it away. Nothing errored: the background could be chosen, previewed on the phone,
        // and simply never appeared on the wrist.
        assertFalse(AlbumArtSource.LOCAL.usesBackdropAsset)
        AlbumArtSource.entries.filter { it != AlbumArtSource.LOCAL }.forEach {
            assertTrue("$it sends a picture that is not the sleeve", it.usesBackdropAsset)
        }
    }

    @Test
    fun `the backdrop question is not the lookup question`() {
        // The device-local sources are exactly the pair that separates them, so this fails the
        // moment someone collapses the two properties back into one.
        val differ = AlbumArtSource.entries.filter { it.usesBackdropAsset != it.needsLookup }
        assertEquals(
                listOf(AlbumArtSource.CUSTOM_IMAGE, AlbumArtSource.CUSTOM_FOLDER),
                differ)
    }

    @Test
    fun `no source both needs a lookup and is device-local`() {
        // The two properties gate opposite things - one the network switch, one publication - and
        // a value answering yes to both would be asking the phone to fetch a file it already has
        // while calling the result shareable.
        AlbumArtSource.entries.forEach {
            assertFalse("$it claims to be both", it.needsLookup && it.isDeviceLocal)
        }
    }

    @Test
    fun `the retired style translates losslessly into the pair that replaced it`() {
        val migrated = AlbumArtSource.migrate(AlbumArtSource.RETIRED_STYLE_VALUE)
        assertEquals(
                PlayerBackgroundStyle.COVER.preferenceValue to AlbumArtSource.ARTIST,
                migrated)
    }

    @Test
    fun `anything else is left alone so a correct value is never rewritten`() {
        listOf(null, "", "cover", "blur", "expressive", "hidden").forEach { stored ->
            assertNull("$stored needs no migration", AlbumArtSource.migrate(stored))
        }
    }

    @Test
    fun `the retired value is no longer a background style`() {
        // If it came back, the migration would keep rewriting a value the picker could show, and
        // the source and the style would both claim to own the same choice.
        assertTrue(
                PlayerBackgroundStyle.entries.none {
                    it.preferenceValue == AlbumArtSource.RETIRED_STYLE_VALUE
                })
    }

    @Test
    fun `the source is face-scoped and exportable`() {
        val key = MiscPreferences.WEAR_ALBUM_ART_SOURCE.key
        assertTrue("$key must be face-scoped", key in FaceScopedPreferences.SCOPED_KEYS)
        assertTrue("$key must be exportable", MiscPreferences.EXPORTABLE.any { it.key == key })
        assertEquals(AlbumArtSource.LOCAL.preferenceValue,
                MiscPreferences.WEAR_ALBUM_ART_SOURCE.defaultValue)
    }

    @Test
    fun `the artist face ships on the artist source`() {
        assertEquals(
                AlbumArtSource.ARTIST.preferenceValue,
                FaceScopedPreferences.perFaceDefault(
                        "artist", MiscPreferences.WEAR_ALBUM_ART_SOURCE.key))
    }

    @Test
    fun `every other face is left on the offline source`() {
        (ThemeAppearance.ALLOWED_BASE_FACES - "artist").forEach { face ->
            assertNull(
                    "$face must not default to a source that makes a network request",
                    FaceScopedPreferences.perFaceDefault(
                            face, MiscPreferences.WEAR_ALBUM_ART_SOURCE.key))
        }
    }
}
