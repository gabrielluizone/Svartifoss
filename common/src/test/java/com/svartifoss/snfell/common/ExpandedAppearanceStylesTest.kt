package com.svartifoss.snfell.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpandedAppearanceStylesTest {
    @Test
    fun `new album treatments decode from stable preference values`() {
        val expected = mapOf(
                "ocean" to PlayerBackgroundStyle.OCEAN,
                "sunset" to PlayerBackgroundStyle.SUNSET,
                "spotlight" to PlayerBackgroundStyle.SPOTLIGHT,
                "glass_veil" to PlayerBackgroundStyle.GLASS_VEIL,
                "velvet" to PlayerBackgroundStyle.VELVET,
                "noir" to PlayerBackgroundStyle.NOIR,
                "ice" to PlayerBackgroundStyle.ICE,
                "rose" to PlayerBackgroundStyle.ROSE)
        expected.forEach { (value, style) ->
            assertEquals(style, PlayerBackgroundStyle.fromPreference(value))
        }
        assertTrue(PlayerBackgroundStyle.GLASS_VEIL.usesBlurRadius)
        assertTrue(PlayerBackgroundStyle.NOIR.grayscaleArtwork)
    }

    @Test
    fun `new album compositions stay separate from filter layer styles`() {
        val compositions = listOf(
                "prismatic", "crescent", "tidal", "paper", "lantern", "mirage",
                "grid", "nocturne", "cloud", "liquid", "monolith", "split_tone")
        compositions.forEach { value ->
            assertEquals(value, PlayerBackgroundStyle.fromPreference(value).preferenceValue)
        }
        assertTrue(PlayerBackgroundStyle.PRISMATIC in BackgroundLayerStack.washStyles)
        assertTrue(PlayerBackgroundStyle.SPLIT_TONE in BackgroundLayerStack.washStyles)
        assertTrue(PlayerBackgroundStyle.FILTER_WARM !in BackgroundLayerStack.washStyles)
    }

    @Test
    fun `new shading and accent floor values decode without changing unknown fallbacks`() {
        assertEquals(PlayerShadingStyle.TOP_FADE,
                PlayerShadingStyle.fromPreference("top_fade"))
        assertEquals(PlayerShadingStyle.CENTER_SPOTLIGHT,
                PlayerShadingStyle.fromPreference("center_spotlight"))
        assertEquals(PlayerShadingStyle.CENTER_BAND,
                PlayerShadingStyle.fromPreference("center_band"))
        assertEquals(PlayerShadingStyle.CROSSFADE,
                PlayerShadingStyle.fromPreference("crossfade"))
        assertEquals(PlayerShadingStyle.FOLLOW,
                PlayerShadingStyle.fromPreference("future_style"))

        assertEquals(AccentFloorStyle.WHISPER,
                AccentFloorStyle.fromPreference("whisper"))
        assertEquals(AccentFloorStyle.RADIANT,
                AccentFloorStyle.fromPreference("radiant"))
        assertEquals(AccentFloorStyle.FLOOD,
                AccentFloorStyle.fromPreference("flood"))
        assertEquals(AccentFloorStyle.GLIMMER,
                AccentFloorStyle.fromPreference("glimmer"))
        assertEquals(AccentFloorStyle.DEEP,
                AccentFloorStyle.fromPreference("deep"))
        assertEquals(AccentFloorStyle.DEFAULT,
                AccentFloorStyle.fromPreference("future_style"))
    }
}
