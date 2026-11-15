package com.svartifoss.snfell.view.watchface

import com.svartifoss.snfell.common.AccentFloorStyle
import com.svartifoss.snfell.common.BackgroundLayer
import com.svartifoss.snfell.common.BackgroundLayerColor
import com.svartifoss.snfell.common.BackgroundLayerKind
import com.svartifoss.snfell.common.BackgroundLayerStack
import com.svartifoss.snfell.common.PlayerBackgroundStyle
import com.svartifoss.snfell.common.PlayerShadingStyle

/**
 * What picking an album art style does to the background stack, so that the style picked is the
 * style the watch shows.
 *
 * An authored album art style (Poster, Velvet, Crescent rim...) is two things: a treatment of the
 * artwork, which `album_art_style` keeps, and a wash painted over it, which since the background
 * stack is a layer. Neither way the stack is resolved let a pick reach the screen for everyone:
 *
 *  - with no stack stored, the legacy arrangement drops the style's wash as soon as any shading
 *    other than `follow` is chosen. [BackgroundLayerStack.implicitStack] keeps that rule on
 *    purpose, because changing it would redraw every existing face and published theme whose owner
 *    once touched the shading;
 *  - with a stack stored, the style's wash is not read at all: the stack's own wash layers are.
 *
 * So a user with a vignette, or with a composed stack, could pick sixty of the seventy-one styles
 * and see nothing change - on the watch, in the docked preview and in every picture of the grid,
 * which were therefore rightly all the same.
 *
 * A pick is a decision, so this answers it instead of changing how an untouched value renders: the
 * picked style's wash becomes the lowest wash of the stack, under whatever shading and floors are
 * already there. Picking a plain treatment (Cover, Blurred, Hidden...) takes back out only a wash
 * that the previous style had put there, never one composed on the Background page. Whenever the
 * result is exactly what the legacy arrangement would draw, the stored stack is cleared instead of
 * written, so a look that needs no stack does not keep one.
 *
 * Pure and JVM-tested. The fragment applies it when the preference changes, and the picker's
 * pictures pass the same result to the preview as a companion candidate, so a picture and the
 * outcome of choosing it come from one rule.
 */
internal object AlbumArtStylePick {

    /** The legacy settings the background stack falls back to when none is stored. */
    data class LegacyBackground(
            val dimEnabled: Boolean,
            val dimPercent: Int,
            val shading: PlayerShadingStyle,
            val shadingColor: BackgroundLayerColor,
            val floor: AccentFloorStyle,
            val floorColor: BackgroundLayerColor,
            /** False for a layout that paints its own backdrop, where no wash reaches the screen. */
            val baseWashDrawn: Boolean
    ) {
        fun implicitFor(style: PlayerBackgroundStyle): List<BackgroundLayer> =
                BackgroundLayerStack.implicitStack(
                        background = style,
                        dimEnabled = dimEnabled,
                        dimPercent = dimPercent,
                        shading = shading,
                        shadingColor = shadingColor,
                        floor = floor,
                        floorColor = floorColor,
                        baseWashDrawn = baseWashDrawn)
    }

    /**
     * The value `wear_background_layers` should hold once [newStyle] replaces [oldStyle], or null
     * when the stored value already shows it and is best left alone.
     *
     * An empty string is a real answer: no stack, the legacy arrangement, which is then exactly
     * what the picked style looks like.
     */
    fun stackAfter(
            storedRaw: String?,
            oldStyle: String?,
            newStyle: String,
            legacy: LegacyBackground
    ): String? {
        if (!legacy.baseWashDrawn) return null
        val picked = PlayerBackgroundStyle.fromPreference(newStyle)
        val previous = PlayerBackgroundStyle.fromPreference(oldStyle)
        val stored = BackgroundLayerStack.parse(storedRaw)
        val legacyLook = legacy.implicitFor(picked)
        val result = if (picked.isPlainArtworkTreatment) {
            withoutPreviousWash(stored ?: return null, previous) ?: return null
        } else {
            withWash(stored ?: legacyLook, picked, legacy.dimPercent) ?: return null
        }
        if (result == legacyLook) return if (stored == null) null else ""
        return BackgroundLayerStack.encode(result)
    }

    /** [layers] showing [style]'s wash as its lowest wash, or null when they already do. */
    private fun withWash(
            layers: List<BackgroundLayer>,
            style: PlayerBackgroundStyle,
            dimPercent: Int
    ): List<BackgroundLayer>? {
        val washIndex = layers.indexOfFirst { it.kind == BackgroundLayerKind.WASH }
        if (washIndex >= 0) {
            if (layers[washIndex].style == style.preferenceValue) return null
            return layers.toMutableList().also {
                it[washIndex] = it[washIndex].copy(style = style.preferenceValue)
            }
        }
        if (layers.size >= BackgroundLayerStack.MAX_LAYERS) return null
        // Closest to the artwork, where the legacy arrangement draws it, at the depth the legacy
        // arrangement would give it for the current shading strength.
        return listOf(BackgroundLayer(
                kind = BackgroundLayerKind.WASH,
                style = style.preferenceValue,
                opacityPercent = BackgroundLayerStack.washPercentFor(dimPercent))) + layers
    }

    /**
     * [layers] without the lowest wash when it is [previous]'s own, or null to leave them as they
     * are. A wash of any other style was put there on the Background page, deliberately.
     */
    private fun withoutPreviousWash(
            layers: List<BackgroundLayer>,
            previous: PlayerBackgroundStyle
    ): List<BackgroundLayer>? {
        if (previous.isPlainArtworkTreatment) return null
        val washIndex = layers.indexOfFirst { it.kind == BackgroundLayerKind.WASH }
        if (washIndex < 0 || layers[washIndex].style != previous.preferenceValue) return null
        return BackgroundLayerStack.remove(layers, washIndex)
    }
}
