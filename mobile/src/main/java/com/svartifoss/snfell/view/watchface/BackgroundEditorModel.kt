package com.svartifoss.snfell.view.watchface

import com.svartifoss.snfell.common.BackgroundLayerKind
import com.svartifoss.snfell.common.MiscPreferences

/**
 * Which control on the Background page owns each of the settings that used to be a row there.
 *
 * The page is now two things: the artwork underneath, which is still one value per control, and
 * the stack on top of it, which is a list. Six of the legacy rows describe *one particular* layer -
 * a shading style, its strength, its colour; an accent floor and its colour - and once a face can
 * carry several of each, none of them has a single control to be pointed at any more. Search still
 * has to answer for them, so they resolve to the layer list, which is where the answer now is.
 *
 * Presentation metadata only. Keys, types and defaults stay owned by [MiscPreferences], and the
 * legacy rows stay inflated behind this surface owning storage, validation and their own dialogs -
 * the same arrangement the Text, Color, Panel and Player pages already use.
 */
internal enum class BackgroundControl {
    /** How the cover itself is composed: square-fit, blurred or hidden. */
    ARTWORK,

    /** The independent colour layer applied over the selected artwork style. */
    FILTER,

    /** Blur radius, meaningful only for the artwork treatments that blur. */
    BLUR,

    /** Whether a new cover crossfades in. */
    FADE,

    /** The ordered stack: every treatment drawn over the artwork. */
    LAYERS
}

internal object BackgroundEditorModel {

    /** The artwork controls, top to bottom, in the order the editor renders them. */
    val artworkKeys: List<String> = listOf(
            MiscPreferences.ALBUM_ART_STYLE.key,
            MiscPreferences.ALBUM_ART_FILTER.key,
            MiscPreferences.ALBUM_ART_BLUR_RADIUS.key,
            MiscPreferences.WEAR_ALBUM_ART_FADE.key)

    /**
     * The legacy rows the stack absorbed.
     *
     * They are still the persisted fallback for anyone who has never composed a stack, and they
     * are still what an older watch build reads, so they are not going anywhere - but on this page
     * they are read through the layer list rather than edited one at a time.
     */
    val layerKeys: List<String> = listOf(
            MiscPreferences.DIM_ALBUM_ART.key,
            MiscPreferences.WEAR_PLAYER_SHADING_STYLE.key,
            MiscPreferences.ALBUM_ART_DIM_STRENGTH.key,
            MiscPreferences.WEAR_SHADING_COLOR_MODE.key,
            MiscPreferences.WEAR_SHADING_CUSTOM_COLOR.key,
            MiscPreferences.WEAR_ACCENT_FLOOR.key,
            MiscPreferences.WEAR_ACCENT_FLOOR_COLOR_MODE.key,
            MiscPreferences.WEAR_ACCENT_FLOOR_CUSTOM_COLOR.key)

    val keys: Set<String> = (artworkKeys + layerKeys).toSet()

    fun controlFor(key: String): BackgroundControl? = when (key) {
        MiscPreferences.ALBUM_ART_STYLE.key -> BackgroundControl.ARTWORK
        MiscPreferences.ALBUM_ART_FILTER.key -> BackgroundControl.FILTER
        MiscPreferences.ALBUM_ART_BLUR_RADIUS.key -> BackgroundControl.BLUR
        MiscPreferences.WEAR_ALBUM_ART_FADE.key -> BackgroundControl.FADE
        in layerKeys -> BackgroundControl.LAYERS
        else -> null
    }

    /** The picker whose entries and archived filtering a layer of [kind] draws its styles from. */
    fun styleSourceKey(kind: BackgroundLayerKind): String = when (kind) {
        // The wash vocabulary is the artwork picker minus the treatments that only transform the
        // bitmap, so its labels come from that row rather than from a second list to translate.
        BackgroundLayerKind.WASH -> MiscPreferences.ALBUM_ART_STYLE.key
        BackgroundLayerKind.SHADE -> MiscPreferences.WEAR_PLAYER_SHADING_STYLE.key
        BackgroundLayerKind.FLOOR -> MiscPreferences.WEAR_ACCENT_FLOOR.key
    }
}
