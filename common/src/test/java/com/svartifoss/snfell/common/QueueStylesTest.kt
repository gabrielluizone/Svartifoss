package com.svartifoss.snfell.common

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueStylesTest {

    @Test
    fun `every style a theme may carry has an entry in the registry`() {
        val offered = valueSet("overlayStyle")
        assertTrue("the asset should declare the queue styles", offered.size > 20)
        offered.forEach { token ->
            assertTrue("$token has no QueueStyleSpec", QueueStyles.isKnown(token))
        }
        // The archived and legacy tokens still render: a configuration may hold them.
        listOf("cover_square", "cover_compact", "cover_tall").forEach {
            assertTrue("$it must keep rendering", QueueStyles.isKnown(it))
        }
    }

    @Test
    fun `an unknown or missing style is glass`() {
        assertSame(QueueStyles.spec("glass"), QueueStyles.spec(null))
        assertSame(QueueStyles.spec("glass"), QueueStyles.spec("unknown_future_style"))
    }

    /**
     * The numbers each style was drawn with before its outline became a choice of its own. A
     * queue left on "follow" must render exactly as it did, so these are pinned rather than derived.
     */
    @Test
    fun `each style keeps the geometry it was drawn with`() {
        val expected = mapOf(
                "glass" to Geometry(26f, 12f, 6f, .5f),
                "minimal" to Geometry(0f, 10f, 2f, .067f),
                "material" to Geometry(12f, 14f, 8f, .2f),
                "tonal" to Geometry(28f, 16f, 8f, .5f),
                "terminal" to Geometry(0f, 11f, 2f, 0f),
                "contrast" to Geometry(8f, 13f, 6f, .133f),
                "soft" to Geometry(30f, 15f, 8f, .45f),
                "slab" to Geometry(10f, 12f, 5f, .167f),
                "ink" to Geometry(24f, 11f, 4f, .3f),
                "rail" to Geometry(6f, 13f, 3f, .067f),
                "sunset" to Geometry(20f, 15f, 9f, .4f),
                "chrome" to Geometry(12f, 13f, 6f, .2f),
                "holo" to Geometry(26f, 14f, 7f, .367f),
                "cover" to Geometry(26f, 12f, 6f, .5f),
                "cover_compact" to Geometry(26f, 8f, 6f, .5f),
                "cover_tall" to Geometry(26f, 14f, 6f, .5f),
                "cover_square" to Geometry(10f, 12f, 6f, .2f),
                "strata" to Geometry(6f, 12f, 3f, .2f))
        expected.forEach { (token, geometry) ->
            val spec = QueueStyles.spec(token)
            assertEquals("$token outline", QueueRowOutline.Rounded.uniform(geometry.corner), spec.outline)
            assertEquals("$token padding", geometry.padding, spec.verticalPaddingDp, 0f)
            assertEquals("$token spacing", geometry.spacing, spec.spacingDp, 0f)
            assertEquals("$token cover", geometry.cover, spec.coverCornerFraction, 1e-4f)
        }
        assertEquals(QueueRowOutline.speechBubble(), QueueStyles.spec("bubble").outline)
        assertEquals(QueueRowOutline.Hexagon(12f), QueueStyles.spec("basalt").outline)
    }

    @Test
    fun `cover styles are the ones that fill the row, and only blur keeps a thumbnail`() {
        assertEquals(
                setOf("cover", "cover_blur", "cover_tonal", "cover_compact", "cover_tall",
                        "cover_square"),
                QueueStyles.COVER_FILL_TOKENS)
        QueueStyles.ALL.forEach { spec ->
            assertEquals(
                    "${spec.token} thumbnail",
                    spec.token !in QueueStyles.COVER_FILL_TOKENS || spec.token == "cover_blur",
                    spec.drawsThumbnail)
        }
    }

    @Test
    fun `left on follow, every style draws what it was drawn with`() {
        QueueStyles.ALL.forEach { spec ->
            val appearance = QueueAppearance.resolve(spec.token, "follow", "follow")
            assertEquals(spec.outline, appearance.outline)
            assertEquals(CoverCorners.uniform(spec.coverCornerFraction), appearance.coverCorners)
        }
        // Unknown values follow too, rather than landing on some fixed shape.
        val glass = QueueAppearance.resolve("glass", "ticket", "star")
        assertEquals(QueueStyles.spec("glass").outline, glass.outline)
        assertEquals(CoverCorners.uniform(.5f), glass.coverCorners)
    }

    @Test
    fun `a chosen outline and cover cut replace the style's own`() {
        val tonal = QueueAppearance.resolve("tonal", "square", "pebble")
        assertEquals(QueueRowOutline.Rounded.uniform(0f), tonal.outline)
        assertEquals(CoverCorners.of(CoverShape.PEBBLE), tonal.coverCorners)
        assertTrue(tonal.rowShapeApplies)
        assertTrue(tonal.coverShapeApplies)
        // The style still sets the rhythm: only the outline was asked for.
        assertEquals(16f, tonal.verticalPaddingDp, 0f)
        assertEquals(8f, tonal.spacingDp, 0f)
    }

    @Test
    fun `cover square is cover with soft corners`() {
        val square = QueueAppearance.resolve("cover_square", "follow", "follow")
        val rebuilt = QueueAppearance.resolve("cover", "soft", "follow")
        assertEquals(square.outline, rebuilt.outline)
    }

    @Test
    fun `a style with no outline to show ignores the pill shape`() {
        val minimal = QueueAppearance.resolve("minimal", "bubble", "follow")
        assertFalse(minimal.rowShapeApplies)
        assertEquals(QueueStyles.spec("minimal").outline, minimal.outline)
        assertFalse(QueueAppearance.rowShapeApplies("minimal"))
        assertTrue(QueueAppearance.rowShapeApplies("terminal"))
    }

    @Test
    fun `a style that fills the row with the cover ignores the cover shape`() {
        listOf("cover", "cover_tonal", "cover_square").forEach { token ->
            val appearance = QueueAppearance.resolve(token, "follow", "square")
            assertFalse("$token draws no thumbnail to cut", appearance.coverShapeApplies)
            assertEquals(CoverCorners.uniform(QueueStyles.spec(token).coverCornerFraction),
                    appearance.coverCorners)
        }
        // Blur keeps its sharp thumbnail, so the cut applies there.
        val blur = QueueAppearance.resolve("cover_blur", "follow", "square")
        assertTrue(blur.coverShapeApplies)
        assertEquals(CoverCorners.of(CoverShape.SQUARE), blur.coverCorners)
    }

    @Test
    fun `the route style strings its covers on a line and holds the playing one in a capsule`() {
        val route = QueueStyles.spec(QueueStyles.ROUTE_TOKEN)
        assertTrue(route.route)
        assertTrue("the capsule is an outline the pill shape can change", route.drawsOutline)
        assertTrue(route.drawsThumbnail)
        assertEquals(.5f, route.coverCornerFraction, 0f)
        assertEquals(QueueRowOutline.Rounded.uniform(QueueRowOutline.STADIUM), route.outline)
        QueueStyles.ALL.filter { it.token != QueueStyles.ROUTE_TOKEN }.forEach {
            assertFalse("${it.token} is not the route", it.route)
        }
    }

    private data class Geometry(
            val corner: Float, val padding: Float, val spacing: Float, val cover: Float)

    /** One value set from the shipped constraints asset, read without org.json (an Android stub
     *  in a JVM test). */
    private fun valueSet(name: String): List<String> {
        val asset = listOf(
                File("src/main/assets/community-theme-constraints.json"),
                File("common/src/main/assets/community-theme-constraints.json"))
                .first(File::isFile)
                .readText()
        val body = Regex("\"${Regex.escape(name)}\"\\s*:\\s*\\[([^\\]]*)]").find(asset)
                ?.groupValues?.get(1)
                ?: error("value set $name missing from the constraints asset")
        return Regex("\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.toList()
    }
}
