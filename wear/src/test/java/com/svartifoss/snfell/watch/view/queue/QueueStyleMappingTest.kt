package com.svartifoss.snfell.watch.view.queue

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class QueueStyleMappingTest {

    private val newStyles = linkedMapOf(
            "soft" to QueueStyle.SOFT,
            "slab" to QueueStyle.SLAB,
            "ink" to QueueStyle.INK,
            "rail" to QueueStyle.RAIL,
            "sunset" to QueueStyle.SUNSET,
            "bubble" to QueueStyle.BUBBLE,
            "chrome" to QueueStyle.CHROME,
            "holo" to QueueStyle.HOLO
    )

    @Test
    fun newPreferenceTokensMapToTheirOwnStyles() {
        newStyles.forEach { (token, style) ->
            assertEquals("$token must not fall back to Glass", style, QueueStyle.fromPref(token))
        }
        assertEquals(QueueStyle.GLASS, QueueStyle.fromPref(null))
        assertEquals(QueueStyle.GLASS, QueueStyle.fromPref("unknown_future_style"))
    }

    @Test
    fun newStylesHaveDeliberatelyDifferentGeometry() {
        val expected = mapOf(
                QueueStyle.SOFT to QueueRowGeometry(30.dp, 15.dp, 8.dp, .45f),
                QueueStyle.SLAB to QueueRowGeometry(10.dp, 12.dp, 5.dp, .167f),
                QueueStyle.INK to QueueRowGeometry(24.dp, 11.dp, 4.dp, .3f),
                QueueStyle.RAIL to QueueRowGeometry(6.dp, 13.dp, 3.dp, .067f),
                QueueStyle.SUNSET to QueueRowGeometry(20.dp, 15.dp, 9.dp, .4f),
                QueueStyle.BUBBLE to QueueRowGeometry(
                        28.dp, 16.dp, 10.dp, .5f, QueueRowShapeFamily.SPEECH_BUBBLE),
                QueueStyle.CHROME to QueueRowGeometry(12.dp, 13.dp, 6.dp, .2f),
                QueueStyle.HOLO to QueueRowGeometry(26.dp, 14.dp, 7.dp, .367f)
        )

        expected.forEach { (style, geometry) ->
            assertEquals("unexpected geometry for $style", geometry, queueRowGeometry(style))
            assertEquals(geometry.spacing, queueRowSpacing(style))
            assertEquals(
                    geometry.artworkCornerFraction,
                    queueArtworkCornerFraction(style),
                    .0001f)
        }
        assertEquals(
                "each new skin should keep its own corner/padding/spacing/artwork signature",
                expected.size,
                expected.values.toSet().size)
    }

    @Test
    fun onlyBubbleUsesTheSpeechBubbleSilhouette() {
        QueueStyle.entries.forEach { style ->
            assertEquals(
                    "$style shape family",
                    if (style == QueueStyle.BUBBLE) {
                        QueueRowShapeFamily.SPEECH_BUBBLE
                    } else {
                        QueueRowShapeFamily.UNIFORM
                    },
                    queueRowGeometry(style).shapeFamily)
        }
    }

    @Test
    fun decorativeStylesRemainOrdinaryThumbnailRows() {
        newStyles.values.forEach { style ->
            assertFalse("$style must not request full-bleed cover rendering", style.isCover)
            assertFalse("$style must keep its thumbnail", style.coverKeepsThumbnail)
            assertNull("$style must use the independent row-size setting", style.legacyRowSize)
        }
    }
}
