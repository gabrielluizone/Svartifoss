package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the two fallbacks, which is the whole reason [TextBlockAlign] and [TextBlockPosition] exist
 * as enums rather than as raw strings read at three draw sites.
 *
 * The controls are offered on every face, and that is only safe because an unset, unknown or
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
}
