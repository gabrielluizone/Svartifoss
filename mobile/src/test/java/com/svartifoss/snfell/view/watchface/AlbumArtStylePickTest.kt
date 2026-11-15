package com.svartifoss.snfell.view.watchface

import com.svartifoss.snfell.common.AccentFloorStyle
import com.svartifoss.snfell.common.BackgroundLayer
import com.svartifoss.snfell.common.BackgroundLayerColor
import com.svartifoss.snfell.common.BackgroundLayerKind
import com.svartifoss.snfell.common.BackgroundLayerStack
import com.svartifoss.snfell.common.PlayerBackgroundStyle
import com.svartifoss.snfell.common.PlayerShadingStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Picking an album art style shows that style - see [AlbumArtStylePick].
 *
 * The bug this pins: with a shading chosen (or a stack composed) the style's wash never reached
 * the screen, so sixty of the seventy-one styles changed nothing and the picker's pictures were
 * all the same.
 */
class AlbumArtStylePickTest {

    private val vignette = AlbumArtStylePick.LegacyBackground(
            dimEnabled = true,
            dimPercent = 80,
            shading = PlayerShadingStyle.EDGE_VIGNETTE_STRONG,
            shadingColor = BackgroundLayerColor.BLACK,
            floor = AccentFloorStyle.OFF,
            floorColor = BackgroundLayerColor.ALBUM,
            baseWashDrawn = true)

    private val following = vignette.copy(shading = PlayerShadingStyle.FOLLOW)

    private fun resolved(raw: String?, style: String, legacy: AlbumArtStylePick.LegacyBackground) =
            BackgroundLayerStack.parse(raw)
                    ?: legacy.implicitFor(PlayerBackgroundStyle.fromPreference(style))

    private fun washes(layers: List<BackgroundLayer>) =
            layers.filter { it.kind == BackgroundLayerKind.WASH }.map { it.style }

    @Test
    fun `an authored style picked under a chosen shading brings its wash below the shading`() {
        val after = AlbumArtStylePick.stackAfter("", "cover", "poster", vignette)
        val layers = BackgroundLayerStack.parse(after)!!
        assertEquals(listOf("poster"), washes(layers))
        assertEquals(BackgroundLayerKind.WASH, layers.first().kind)
        assertEquals(BackgroundLayerKind.SHADE, layers.last().kind)
        assertEquals(PlayerShadingStyle.EDGE_VIGNETTE_STRONG.preferenceValue, layers.last().style)
        // At the depth the legacy arrangement gives a wash for the same strength.
        assertEquals(BackgroundLayerStack.washPercentFor(80), layers.first().opacityPercent)
    }

    @Test
    fun `nothing is written when the legacy arrangement already shows the style`() {
        assertNull(AlbumArtStylePick.stackAfter("", "cover", "poster", following))
        assertNull(AlbumArtStylePick.stackAfter("", "poster", "studio", following))
        // A plain treatment with no stack stored: there is no wash to add or take away.
        assertNull(AlbumArtStylePick.stackAfter("", "poster", "blur", vignette))
        assertNull(AlbumArtStylePick.stackAfter(null, "cover", "frosted", vignette))
    }

    @Test
    fun `every authored style is visible after it is picked, whatever the shading`() {
        PlayerBackgroundStyle.entries.filterNot { it.isPlainArtworkTreatment }.forEach { style ->
            listOf(vignette, following).forEach { legacy ->
                val raw = AlbumArtStylePick.stackAfter("", "cover", style.preferenceValue, legacy)
                        ?: ""
                val shown = resolved(raw, style.preferenceValue, legacy)
                assertTrue("${style.preferenceValue} under ${legacy.shading} is not drawn",
                        style.preferenceValue in washes(shown))
            }
        }
    }

    @Test
    fun `a composed stack gets the style as its lowest wash and keeps everything else`() {
        val stored = BackgroundLayerStack.encode(listOf(
                BackgroundLayer(BackgroundLayerKind.SHADE, "bottom_fade", 60),
                BackgroundLayer(BackgroundLayerKind.WASH, "velvet", 70),
                BackgroundLayer(BackgroundLayerKind.FLOOR, "soft")))
        val after = BackgroundLayerStack.parse(
                AlbumArtStylePick.stackAfter(stored, "cover", "poster", vignette))!!
        assertEquals(3, after.size)
        assertEquals("poster", after[1].style)
        // The layer keeps its place and its depth; only its style is the picked one.
        assertEquals(70, after[1].opacityPercent)
        assertEquals("bottom_fade", after[0].style)
    }

    @Test
    fun `a composed stack without a wash gets one at the bottom`() {
        val stored = BackgroundLayerStack.encode(listOf(
                BackgroundLayer(BackgroundLayerKind.SHADE, "bottom_fade", 60)))
        val after = BackgroundLayerStack.parse(
                AlbumArtStylePick.stackAfter(stored, "cover", "halo", vignette))!!
        assertEquals(listOf(BackgroundLayerKind.WASH, BackgroundLayerKind.SHADE), after.map { it.kind })
        assertEquals("halo", after[0].style)
    }

    @Test
    fun `a full stack is left alone rather than losing a layer`() {
        val full = BackgroundLayerStack.encode(List(BackgroundLayerStack.MAX_LAYERS) {
            BackgroundLayer(BackgroundLayerKind.SHADE, "bottom_fade", 20)
        })
        assertNull(AlbumArtStylePick.stackAfter(full, "cover", "poster", vignette))
    }

    @Test
    fun `going back to a plain treatment removes the wash the previous style added`() {
        val withPoster = AlbumArtStylePick.stackAfter("", "cover", "poster", vignette)!!
        // Back to the legacy arrangement: the stored stack is cleared, not kept as a copy of it.
        assertEquals("", AlbumArtStylePick.stackAfter(withPoster, "poster", "cover", vignette))
    }

    @Test
    fun `a plain treatment never removes a wash composed on the Background page`() {
        val stored = BackgroundLayerStack.encode(listOf(
                BackgroundLayer(BackgroundLayerKind.WASH, "velvet"),
                BackgroundLayer(BackgroundLayerKind.SHADE, "bottom_fade", 60)))
        // The previous style was plain, so the velvet layer is the user's own.
        assertNull(AlbumArtStylePick.stackAfter(stored, "cover", "blur", vignette))
        // The previous style was another wash, so velvet is still not the picker's to remove.
        assertNull(AlbumArtStylePick.stackAfter(stored, "poster", "blur", vignette))
    }

    @Test
    fun `switching between two authored styles replaces the wash instead of stacking them`() {
        val first = AlbumArtStylePick.stackAfter("", "cover", "poster", vignette)!!
        val second = BackgroundLayerStack.parse(
                AlbumArtStylePick.stackAfter(first, "poster", "studio", vignette))!!
        assertEquals(listOf("studio"), washes(second))
    }

    @Test
    fun `a layout that paints its own backdrop is never given a wash`() {
        val split = vignette.copy(baseWashDrawn = false)
        assertNull(AlbumArtStylePick.stackAfter("", "cover", "poster", split))
    }
}
