package com.svartifoss.snfell.view.watchface

import com.svartifoss.snfell.common.AlbumArtSource
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
    /** Which picture goes behind the player at all - see [AlbumArtSource]. */
    SOURCE,

    /**
     * The file or folder a device-local source draws from.
     *
     * One control for two keys, because the two sources are alternatives: at most one of
     * `custom_album_art_image` and `custom_album_art_folder` is ever the one being read, so a
     * control per key would mean one of them permanently pointing at a picture nothing draws.
     */
    PICTURE,

    /** How that picture is composed: square-fit, blurred or hidden. */
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
            MiscPreferences.WEAR_ALBUM_ART_SOURCE.key,
            MiscPreferences.CUSTOM_ALBUM_ART_IMAGE.key,
            MiscPreferences.CUSTOM_ALBUM_ART_FOLDER.key,
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

    /**
     * The keys on this page that are deliberately *not* face-scoped, named here rather than in the
     * test that checks them - the shape [PanelEditorModel.globalKeys] already uses.
     *
     * Both hold a `content://` URI for a device-local artwork source. Which *source* a face draws
     * is an appearance choice and is scoped like every other; which file on this phone that source
     * points at is not, and scoping it would mean re-picking the same photograph once per face and
     * writing a storage reference into every saved theme. See
     * [com.svartifoss.snfell.common.DeviceLocalAppearance].
     */
    val globalKeys: Set<String> = setOf(
            MiscPreferences.CUSTOM_ALBUM_ART_IMAGE.key,
            MiscPreferences.CUSTOM_ALBUM_ART_FOLDER.key)

    fun controlFor(key: String): BackgroundControl? = when (key) {
        MiscPreferences.WEAR_ALBUM_ART_SOURCE.key -> BackgroundControl.SOURCE
        MiscPreferences.CUSTOM_ALBUM_ART_IMAGE.key,
        MiscPreferences.CUSTOM_ALBUM_ART_FOLDER.key -> BackgroundControl.PICTURE
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
