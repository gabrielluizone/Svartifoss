package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The stack is what every background renderer now walks, so the decisions worth pinning are the
 * ones that are invisible when they go wrong: that a value nobody has touched keeps rendering the
 * pre-stack look, that a value this build only half understands is refused rather than partly
 * applied, and that one composition has exactly one encoding.
 */
class BackgroundLayerStackTest {

    @Test
    fun `an absent value has no explicit stack`() {
        assertNull(BackgroundLayerStack.parse(null))
        assertNull(BackgroundLayerStack.parse(""))
        assertNull(BackgroundLayerStack.parse("   "))
        assertFalse(BackgroundLayerStack.isExplicit(null))
    }

    @Test
    fun `an empty stack is a decision and survives the round trip`() {
        val encoded = BackgroundLayerStack.encode(emptyList())
        assertEquals("1", encoded)
        // The distinction the class doc calls load-bearing: "" means nobody chose, "1" means the
        // user removed every layer and expects bare artwork.
        assertEquals(emptyList<BackgroundLayer>(), BackgroundLayerStack.parse(encoded))
    }

    @Test
    fun `a stack round trips through its shortest form`() {
        val layers = listOf(
                BackgroundLayer(BackgroundLayerKind.WASH, "poster"),
                BackgroundLayer(
                        BackgroundLayerKind.SHADE, "bottom_fade", 60,
                        BackgroundLayerColor.CUSTOM, "#1A2B3C"),
                BackgroundLayer(
                        BackgroundLayerKind.FLOOR, "standard", 100,
                        BackgroundLayerColor.TERTIARY))
        val encoded = BackgroundLayerStack.encode(layers)

        assertEquals("1|w.poster|s.bottom_fade.60.custom.#1A2B3C|f.standard.100.tertiary", encoded)
        assertEquals(layers, BackgroundLayerStack.parse(encoded))
    }

    @Test
    fun `one composition has one encoding`() {
        // Defaults are dropped, so a layer built by adding and one built by editing back to the
        // default encode identically - settingsDigest is computed over this string, and duplicate
        // detection stops working the moment two spellings of the same theme exist.
        val plain = BackgroundLayer(BackgroundLayerKind.SHADE, "bottom_fade")
        val spelledOut = plain.copy(
                opacityPercent = BackgroundLayerStack.DEFAULT_OPACITY_PERCENT,
                color = BackgroundLayerColor.DEFAULT)
        assertEquals(
                BackgroundLayerStack.encode(listOf(plain)),
                BackgroundLayerStack.encode(listOf(spelledOut)))
    }

    @Test
    fun `a colour choice carries its opacity even at the default`() {
        // The grammar is positional, so a colour cannot be written without the field before it.
        val encoded = BackgroundLayerStack.encode(
                listOf(BackgroundLayer(
                        BackgroundLayerKind.FLOOR, "soft", color = BackgroundLayerColor.SECONDARY)))
        assertEquals("1|f.soft.100.secondary", encoded)
        assertEquals(
                BackgroundLayerColor.SECONDARY,
                BackgroundLayerStack.parse(encoded)?.single()?.color)
    }

    @Test
    fun `a stack this build only half understands is refused whole`() {
        // Not "drop the layer we could not read": that would publish a different composition than
        // the one that was saved, and quietly.
        assertNull(BackgroundLayerStack.parse("1|s.bottom_fade|s.from_a_newer_build"))
        assertNull(BackgroundLayerStack.parse("1|x.bottom_fade"))
        assertNull(BackgroundLayerStack.parse("2|s.bottom_fade"))
        assertNull(BackgroundLayerStack.parse("s.bottom_fade"))
        assertNull(BackgroundLayerStack.parse("1|s"))
        assertNull(BackgroundLayerStack.parse("1|s.bottom_fade.60.custom.notahex"))
        assertNull(BackgroundLayerStack.parse("1|s.bottom_fade.60.custom"))
        assertNull(BackgroundLayerStack.parse("1|s.bottom_fade.60.black.#112233"))
        assertNull(BackgroundLayerStack.parse("1|s.bottom_fade.999"))
        assertNull(BackgroundLayerStack.parse("1|s.bottom_fade. 60"))
        assertNull(BackgroundLayerStack.parse("1|s.bottom_fade.060"))
    }

    @Test
    fun `the kinds refuse each other's vocabulary`() {
        // A shading value on a floor layer is not a near miss to be tolerated: the two enums each
        // own their own geometry, and reading one as the other draws something nobody chose.
        assertNull(BackgroundLayerStack.parse("1|f.bottom_fade"))
        assertNull(BackgroundLayerStack.parse("1|s.standard"))
        // "follow" and "off" are the absence of a choice; a layer is the choice.
        assertNull(BackgroundLayerStack.parse("1|s.follow"))
        assertNull(BackgroundLayerStack.parse("1|f.off"))
        // A plain artwork treatment draws nothing on top of the bitmap, so as a wash it would be
        // an entry that silently does nothing.
        assertNull(BackgroundLayerStack.parse("1|w.cover"))
        assertNull(BackgroundLayerStack.parse("1|w.filter_sepia"))
        assertNotNull(BackgroundLayerStack.parse("1|w.hidden"))
    }

    @Test
    fun `depth and length are both bounded`() {
        val tooDeep = (1..BackgroundLayerStack.MAX_LAYERS + 1).map {
            BackgroundLayer(BackgroundLayerKind.SHADE, "bottom_fade")
        }
        assertNull(BackgroundLayerStack.parse(
                (listOf("1") + tooDeep.map { "s.bottom_fade" }).joinToString("|")))
        assertNull(BackgroundLayerStack.parse("1|" + "s.bottom_fade|".repeat(200)))
    }

    @Test
    fun `the deepest stack the editor can build fits the declared length`() {
        // MAX_ENCODED_LENGTH is what the public contract declares for this key, so a stack the app
        // lets someone build must not be one the gallery then refuses.
        val worst = (1..BackgroundLayerStack.MAX_LAYERS).map {
            BackgroundLayer(
                    BackgroundLayerKind.SHADE,
                    BackgroundLayerStack.shadeStyles.maxBy { style ->
                        style.preferenceValue.length
                    }.preferenceValue,
                    BackgroundLayerStack.MAX_OPACITY_PERCENT,
                    BackgroundLayerColor.CUSTOM,
                    "#ABCDEF")
        }
        val encoded = BackgroundLayerStack.encode(worst)
        assertTrue(
                "the deepest stack encodes to ${encoded.length} characters, over the declared " +
                        "${BackgroundLayerStack.MAX_ENCODED_LENGTH}",
                encoded.length <= BackgroundLayerStack.MAX_ENCODED_LENGTH)
        assertEquals(worst, BackgroundLayerStack.parse(encoded))
    }

    @Test
    fun `the implicit stack reproduces the shipped three-slot arrangement`() {
        // Plain artwork, dimming on, nothing else chosen: one neutral bottom fade, which is what
        // PlayerShadingOverlay draws for `follow` over a plain treatment.
        assertEquals(
                listOf(BackgroundLayer(
                        BackgroundLayerKind.SHADE, "bottom_fade", 80,
                        BackgroundLayerColor.BLACK)),
                implicit(background = PlayerBackgroundStyle.COVER))
    }

    @Test
    fun `an authored background becomes the bottom layer at its designed depth`() {
        val layers = implicit(background = PlayerBackgroundStyle.POSTER)
        assertEquals(
                listOf(BackgroundLayer(BackgroundLayerKind.WASH, "poster", 100)),
                layers)
        // The shipped 80% strength has always meant "exactly the designed depth" - every renderer
        // divides it by .8 - so it has to read as 100% once it is a number the user can see.
        assertEquals(100, BackgroundLayerStack.washPercentFor(80))
        // And the top of the legacy range has to survive the same division rather than being
        // clipped at the slider's 150, or a strong dim would come out gentler after the stack.
        assertEquals(
                (SHADING_MAX_PERCENT / .8f).toInt() + 1,
                BackgroundLayerStack.washPercentFor(SHADING_MAX_PERCENT))
        assertTrue(
                BackgroundLayerStack.washPercentFor(SHADING_MAX_PERCENT) <=
                        BackgroundLayerStack.MAX_OPACITY_PERCENT)
    }

    @Test
    fun `an explicit shading suppresses the authored wash, as the renderers do`() {
        // Reads oddly and is deliberate: tidying it would redraw every face whose owner ever
        // touched the shading picker.
        val layers = implicit(
                background = PlayerBackgroundStyle.POSTER,
                shading = PlayerShadingStyle.EDGE_VIGNETTE)
        assertEquals(
                listOf(BackgroundLayer(
                        BackgroundLayerKind.SHADE, "edge_vignette", 80, BackgroundLayerColor.BLACK)),
                layers)
    }

    @Test
    fun `an authored wash survives the dim switch but the shading does not`() {
        assertEquals(
                listOf(BackgroundLayer(BackgroundLayerKind.WASH, "aurora", 100)),
                implicit(background = PlayerBackgroundStyle.AURORA, dimEnabled = false))
        assertEquals(emptyList<BackgroundLayer>(), implicit(dimEnabled = false))
    }

    @Test
    fun `the accent floor sits under the shading, where it is drawn today`() {
        val layers = implicit(floor = AccentFloorStyle.STANDARD)
        assertEquals(
                listOf(BackgroundLayerKind.FLOOR, BackgroundLayerKind.SHADE),
                layers.map { it.kind })
    }

    @Test
    fun `an explicit stack wins over every legacy key`() {
        // Never some of them: a half-applied stack would put back a shading the user removed.
        val explicit = BackgroundLayerStack.encode(
                listOf(BackgroundLayer(BackgroundLayerKind.FLOOR, "flood")))
        val resolved = BackgroundLayerStack.resolve(
                raw = explicit,
                background = PlayerBackgroundStyle.POSTER,
                dimEnabled = true,
                dimPercent = 80,
                shading = PlayerShadingStyle.EDGE_VIGNETTE,
                shadingColor = BackgroundLayerColor.ALBUM,
                floor = AccentFloorStyle.SOFT,
                floorColor = BackgroundLayerColor.SECONDARY)
        assertEquals(
                listOf(BackgroundLayer(BackgroundLayerKind.FLOOR, "flood")),
                resolved)
    }

    @Test
    fun `an unreadable stack falls back to the legacy look rather than to nothing`() {
        // This is also what an older watch build does with a key it has never heard of, so the
        // degradation is the same on both sides.
        assertEquals(
                implicit(background = PlayerBackgroundStyle.POSTER),
                BackgroundLayerStack.resolve(
                        raw = "1|s.from_a_newer_build",
                        background = PlayerBackgroundStyle.POSTER,
                        dimEnabled = true,
                        dimPercent = 80,
                        shading = PlayerShadingStyle.FOLLOW,
                        shadingColor = BackgroundLayerColor.BLACK,
                        floor = AccentFloorStyle.OFF,
                        floorColor = BackgroundLayerColor.ALBUM))
    }

    @Test
    fun `moving refuses to fall off either end`() {
        val layers = listOf(
                BackgroundLayer(BackgroundLayerKind.WASH, "poster"),
                BackgroundLayer(BackgroundLayerKind.SHADE, "bottom_fade"))
        assertEquals(layers, BackgroundLayerStack.move(layers, 0, -1))
        assertEquals(layers, BackgroundLayerStack.move(layers, 1, 1))
        assertEquals(layers.reversed(), BackgroundLayerStack.move(layers, 0, 1))
    }

    @Test
    fun `duplicate lands directly above its original and respects the ceiling`() {
        val layers = listOf(
                BackgroundLayer(BackgroundLayerKind.WASH, "poster"),
                BackgroundLayer(BackgroundLayerKind.SHADE, "bottom_fade"))
        assertEquals(
                listOf(layers[0], layers[0], layers[1]),
                BackgroundLayerStack.duplicate(layers, 0))

        val full = (1..BackgroundLayerStack.MAX_LAYERS).map {
            BackgroundLayer(BackgroundLayerKind.SHADE, "bottom_fade")
        }
        assertEquals(full, BackgroundLayerStack.duplicate(full, 0))
        assertEquals(full, BackgroundLayerStack.add(
                full, BackgroundLayer(BackgroundLayerKind.FLOOR, "soft")))
    }

    @Test
    fun `stacking the same style twice is the point`() {
        // The request this whole file exists for: two shadings at once, which the single-value
        // picker could not express at all.
        val encoded = BackgroundLayerStack.encode(listOf(
                BackgroundLayer(BackgroundLayerKind.SHADE, "bottom_corner"),
                BackgroundLayer(BackgroundLayerKind.SHADE, "bottom_fade")))
        assertEquals(2, BackgroundLayerStack.parse(encoded)?.size)
    }

    @Test
    fun `a default colour resolves to its kind's own answer`() {
        assertEquals(
                BackgroundLayerColor.BLACK,
                BackgroundLayer(BackgroundLayerKind.SHADE, "bottom_fade").effectiveColor)
        assertEquals(
                BackgroundLayerColor.ALBUM,
                BackgroundLayer(BackgroundLayerKind.FLOOR, "standard").effectiveColor)
    }

    @Test
    fun `resolving decodes each kind into its own renderer's vocabulary`() {
        val resolved = listOf(
                BackgroundLayer(BackgroundLayerKind.WASH, "poster", 50),
                BackgroundLayer(BackgroundLayerKind.SHADE, "bottom_fade", 60),
                BackgroundLayer(BackgroundLayerKind.FLOOR, "flood", 70))
                .resolveLayers(shadeColor = { 0x111111 }, floorColor = { 0x222222 })

        assertEquals(
                listOf(
                        ResolvedBackgroundLayer.Wash(PlayerBackgroundStyle.POSTER, .5f),
                        ResolvedBackgroundLayer.Shade(
                                PlayerShadingStyle.BOTTOM_FADE, .6f, 0x111111),
                        ResolvedBackgroundLayer.Floor(
                                AccentFloorStyle.FLOOD, .7f, 0x222222)),
                resolved)
    }

    private fun implicit(
            background: PlayerBackgroundStyle = PlayerBackgroundStyle.COVER,
            dimEnabled: Boolean = true,
            dimPercent: Int = 80,
            shading: PlayerShadingStyle = PlayerShadingStyle.FOLLOW,
            shadingColor: BackgroundLayerColor = BackgroundLayerColor.BLACK,
            floor: AccentFloorStyle = AccentFloorStyle.OFF,
            floorColor: BackgroundLayerColor = BackgroundLayerColor.ALBUM
    ): List<BackgroundLayer> = BackgroundLayerStack.implicitStack(
            background = background,
            dimEnabled = dimEnabled,
            dimPercent = dimPercent,
            shading = shading,
            shadingColor = shadingColor,
            floor = floor,
            floorColor = floorColor)
}
