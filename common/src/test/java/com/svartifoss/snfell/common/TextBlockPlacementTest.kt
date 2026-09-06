package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the two fallbacks, which is the whole reason [TextBlockAlign] and [TextBlockPosition] exist
 * as enums rather than as raw strings read at three draw sites.
 *
 * Also pins [TextBlockPlacementSupport], which decides *which faces are asked at all* - two sets,
 * one per axis, because nine faces can honour one of the two and not the other.
 *
 * The keys are stored on every face, and that is only safe because an unset, unknown or
 * malformed value resolves to `follow` - meaning "keep the composition this face authored". If any
 * of those resolved to a real position instead, adding these keys would silently rearrange every
 * saved theme and every published community theme built before they existed.
 */
class TextBlockPlacementTest {

    @Test
    fun `named values round-trip through their preference strings`() {
        TextBlockAlign.entries.forEach { value ->
            assertEquals(value, TextBlockAlign.fromPref(value.preferenceValue))
        }
        TextBlockPosition.entries.forEach { value ->
            assertEquals(value, TextBlockPosition.fromPref(value.preferenceValue))
        }
    }

    @Test
    fun `surrounding whitespace is tolerated`() {
        assertEquals(TextBlockAlign.END, TextBlockAlign.fromPref("  end "))
        assertEquals(TextBlockPosition.TOP, TextBlockPosition.fromPref("\ttop\n"))
    }

    @Test
    fun `an absent value keeps the face's own composition`() {
        assertEquals(TextBlockAlign.FOLLOW, TextBlockAlign.fromPref(null))
        assertEquals(TextBlockAlign.FOLLOW, TextBlockAlign.fromPref(""))
        assertEquals(TextBlockPosition.FOLLOW, TextBlockPosition.fromPref(null))
        assertEquals(TextBlockPosition.FOLLOW, TextBlockPosition.fromPref(""))
    }

    @Test
    fun `an unrecognised value falls back to follow rather than to a named alternative`() {
        // The value can arrive from an imported backup, a published community theme or a newer
        // build on the other device. Resolving "middle" as an *alignment*, or any unknown token as
        // a real edge, would make an unreadable value look like a deliberate choice.
        assertEquals(TextBlockAlign.FOLLOW, TextBlockAlign.fromPref("middle"))
        assertEquals(TextBlockAlign.FOLLOW, TextBlockAlign.fromPref("START"))
        assertEquals(TextBlockAlign.FOLLOW, TextBlockAlign.fromPref("left"))
        assertEquals(TextBlockPosition.FOLLOW, TextBlockPosition.fromPref("center"))
        assertEquals(TextBlockPosition.FOLLOW, TextBlockPosition.fromPref("baseline"))
    }

    @Test
    fun `the default is the sentinel on both axes`() {
        assertEquals(TextBlockAlign.FOLLOW, TextBlockAlign.DEFAULT)
        assertEquals(TextBlockPosition.FOLLOW, TextBlockPosition.DEFAULT)
    }

    @Test
    fun `the preference definitions ship the sentinel as their default`() {
        assertEquals(
                TextBlockAlign.FOLLOW.preferenceValue,
                MiscPreferences.WEAR_TEXT_BLOCK_ALIGN.defaultValue)
        assertEquals(
                TextBlockPosition.FOLLOW.preferenceValue,
                MiscPreferences.WEAR_TEXT_BLOCK_POSITION.defaultValue)
    }

    @Test
    fun `the artist face ships pinned to the leading edge and grounded`() {
        // Expressed as ordinary placement defaults rather than as anything the renderer
        // special-cases, which is what lets a user centre or lift the block without the face
        // fighting them.
        assertEquals(
                TextBlockAlign.START.preferenceValue,
                FaceScopedPreferences.perFaceDefault(
                        "artist", MiscPreferences.WEAR_TEXT_BLOCK_ALIGN.key))
        assertEquals(
                TextBlockPosition.BOTTOM.preferenceValue,
                FaceScopedPreferences.perFaceDefault(
                        "artist", MiscPreferences.WEAR_TEXT_BLOCK_POSITION.key))
    }

    @Test
    fun `every other face is left composing for itself`() {
        (ThemeAppearance.ALLOWED_BASE_FACES - "artist").forEach { face ->
            assertEquals(
                    "$face must not carry a placement default: the control is offered everywhere " +
                            "precisely because it changes nothing until chosen",
                    null,
                    FaceScopedPreferences.perFaceDefault(
                            face, MiscPreferences.WEAR_TEXT_BLOCK_ALIGN.key))
            assertEquals(
                    "$face must not carry a placement default",
                    null,
                    FaceScopedPreferences.perFaceDefault(
                            face, MiscPreferences.WEAR_TEXT_BLOCK_POSITION.key))
        }
    }

    @Test
    fun `both keys are face-scoped and exportable`() {
        // Scoped, or choosing an alignment for one face would silently move every other face's
        // text; exportable, or the choice never reaches the wrist and no saved theme can carry it.
        listOf(
                MiscPreferences.WEAR_TEXT_BLOCK_ALIGN,
                MiscPreferences.WEAR_TEXT_BLOCK_POSITION
        ).forEach { definition ->
            assert(definition.key in FaceScopedPreferences.SCOPED_KEYS) {
                "${definition.key} must be face-scoped"
            }
            assert(MiscPreferences.EXPORTABLE.any { it.key == definition.key }) {
                "${definition.key} must be exportable"
            }
        }
    }

    @Test
    fun `both support sets name only registered faces`() {
        // A rule naming a face that no longer exists hides a control forever, which is
        // indistinguishable from the control never having been written.
        listOf(
                "align" to TextBlockPlacementSupport.ALIGN_FACES,
                "position" to TextBlockPlacementSupport.POSITION_FACES
        ).forEach { (name, faces) ->
            assertEquals(
                    "$name names unregistered faces",
                    emptySet<String>(),
                    faces - ThemeAppearance.ALLOWED_BASE_FACES)
        }
    }

    @Test
    fun `a face that ships a placement default must be able to honour it`() {
        // The Artist face is pinned to the leading edge and grounded by its own per-face defaults.
        // Excluding it from either set would leave those defaults stored, exported, published in
        // every saved theme, and silently ignored by both renderers.
        ThemeAppearance.ALLOWED_BASE_FACES.forEach { face ->
            if (FaceScopedPreferences.perFaceDefault(
                            face, MiscPreferences.WEAR_TEXT_BLOCK_ALIGN.key) != null) {
                assertTrue(
                        "$face ships an alignment default it cannot honour",
                        TextBlockPlacementSupport.allowsAlign(face))
            }
            if (FaceScopedPreferences.perFaceDefault(
                            face, MiscPreferences.WEAR_TEXT_BLOCK_POSITION.key) != null) {
                assertTrue(
                        "$face ships a position default it cannot honour",
                        TextBlockPlacementSupport.allowsPosition(face))
            }
        }
    }

    @Test
    fun `an unsupported face reads back as the sentinel rather than as the stored value`() {
        // The gate has to hold at the *renderer*, not only in the picker: a value still arrives
        // from an imported backup, a published theme or the other app on an older build.
        assertEquals(
                TextBlockAlign.FOLLOW,
                TextBlockPlacementSupport.resolveAlign("chat", TextBlockAlign.END))
        assertEquals(
                TextBlockPosition.FOLLOW,
                TextBlockPlacementSupport.resolvePosition("carousel", TextBlockPosition.TOP))
        assertEquals(
                TextBlockAlign.FOLLOW,
                TextBlockPlacementSupport.resolveAlign(null, TextBlockAlign.START))
        assertEquals(
                TextBlockAlign.FOLLOW,
                TextBlockPlacementSupport.resolveAlign("no-such-face", TextBlockAlign.START))
    }

    @Test
    fun `a supported face is handed its stored value untouched`() {
        assertEquals(
                TextBlockAlign.END,
                TextBlockPlacementSupport.resolveAlign("classic", TextBlockAlign.END))
        assertEquals(
                TextBlockPosition.BOTTOM,
                TextBlockPlacementSupport.resolvePosition("artist", TextBlockPosition.BOTTOM))
    }

    @Test
    fun `the two axes are asked separately`() {
        // The whole reason there are two sets. Carousel pins its artist above the cover rail and
        // its title below it, so both may be aligned and neither may be moved; Note's sentence may
        // be raised or grounded, but aligning it drags the cover disc that centres with it.
        assertTrue(TextBlockPlacementSupport.allowsAlign("carousel"))
        assertTrue(!TextBlockPlacementSupport.allowsPosition("carousel"))
        assertTrue(!TextBlockPlacementSupport.allowsAlign("note"))
        assertTrue(TextBlockPlacementSupport.allowsPosition("note"))
    }
}
