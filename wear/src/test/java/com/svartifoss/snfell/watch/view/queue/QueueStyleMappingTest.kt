package com.svartifoss.snfell.watch.view.queue

import androidx.compose.ui.unit.dp
import com.svartifoss.snfell.common.QueueAppearance
import com.svartifoss.snfell.common.QueueRowLayout
import com.svartifoss.snfell.common.QueueRowShape
import com.svartifoss.snfell.common.QueueStyles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The watch's [QueueStyle] is a handle on the shared registry ([QueueStyles]); what each style is
 * drawn with is pinned by the registry's own tests in `common`. What has to hold here is that the
 * two name the same styles and that the measurements the rows are laid out with follow from them.
 */
class QueueStyleMappingTest {

    @Test
    fun everyStyleRoundTripsThroughItsToken() {
        QueueStyle.entries.forEach { style ->
            assertEquals("${style.token} must not fall back to Glass", style,
                    QueueStyle.fromPref(style.token))
            assertTrue("${style.token} has no entry in the shared registry",
                    QueueStyles.isKnown(style.token))
            assertEquals(style.token, style.spec.token)
        }
        assertEquals(QueueStyle.GLASS, QueueStyle.fromPref(null))
        assertEquals(QueueStyle.GLASS, QueueStyle.fromPref("unknown_future_style"))
    }

    @Test
    fun everyRegisteredStyleHasAWatchHandle() {
        QueueStyles.ALL.forEach { spec ->
            assertEquals(spec.token, QueueStyle.fromPref(spec.token).token)
        }
    }

    @Test
    fun theCoverFamilyIsTheRegistrysCoverFill() {
        assertEquals(
                QueueStyles.COVER_FILL_TOKENS,
                QueueStyle.COVER_FAMILY.map { it.token }.toSet())
        QueueStyle.entries.forEach { style ->
            assertEquals(style.spec.fillsWithCover, style.isCover)
            assertEquals(style.isCover && style.spec.drawsThumbnail, style.coverKeepsThumbnail)
        }
    }

    @Test
    fun onlyTheTwoLegacyPresetsNameTheirOwnSize() {
        assertEquals(QueueRowSize.COMPACT, QueueStyle.COVER_COMPACT.legacyRowSize)
        assertEquals(QueueRowSize.TALL, QueueStyle.COVER_TALL.legacyRowSize)
        QueueStyle.entries.filter { it != QueueStyle.COVER_COMPACT && it != QueueStyle.COVER_TALL }
                .forEach { assertNull("$it must use the row-size setting", it.legacyRowSize) }
    }

    /** The phone's miniature sizes its rows from the shared table; the watch from this enum. */
    @Test
    fun rowSizesAgreeWithTheSharedTable() {
        mapOf(
                "compact" to QueueRowSize.COMPACT,
                "normal" to QueueRowSize.NORMAL,
                "tall" to QueueRowSize.TALL,
                "xtall" to QueueRowSize.XTALL
        ).forEach { (token, size) ->
            assertEquals(token, size, QueueRowSize.fromPref(token))
            assertEquals(token, QueueRowLayout.contentHeightDp(token), size.contentHeight.value, 0f)
        }
        assertEquals(QueueRowLayout.ARTWORK_INSET_DP, QUEUE_ARTWORK_INSET.value, 0f)
    }

    @Test
    fun aRowIsItsContentPlusTheStylesPadding() {
        val tonal = queueRowMetrics(
                QueueAppearance.resolve("tonal", "follow", "follow"), QueueRowSize.NORMAL)
        assertEquals(30.dp + 16.dp * 2, tonal.height)
        // A different outline does not re-space the style.
        val squareTonal = queueRowMetrics(
                QueueAppearance.resolve("tonal", QueueRowShape.SQUARE.token, "follow"),
                QueueRowSize.NORMAL)
        assertEquals(tonal.height, squareTonal.height)
        assertEquals(16.dp, squareTonal.textInset)
    }

    @Test
    fun theDefaultRowKeepsItsCoverWhereItAlwaysWas() {
        val glass = queueRowMetrics(
                QueueAppearance.resolve("glass", "follow", "follow"), QueueRowSize.NORMAL)
        assertEquals(54.dp, glass.height)
        assertEquals(44.dp, glass.artworkSize)
        assertEquals(QUEUE_ARTWORK_INSET.value, glass.coverStart.value, 1e-3f)
    }

    @Test
    fun aSquareCoverStepsIntoACapsule() {
        val metrics = queueRowMetrics(
                QueueAppearance.resolve("glass", QueueRowShape.PILL.token, "square"),
                QueueRowSize.XTALL)
        assertTrue("square cover in a capsule started at ${metrics.coverStart}",
                metrics.coverStart > QUEUE_ARTWORK_INSET + 4.dp)
    }

    @Test
    fun hexagonalTextKeepsClearOfThePoints() {
        val basalt = queueRowMetrics(
                QueueAppearance.resolve("basalt", "follow", "follow"), QueueRowSize.NORMAL)
        assertEquals(24.dp, basalt.textInset)
    }

    @Test
    fun theRouteRunsThroughTheMiddleOfEveryCover() {
        val route = queueRowMetrics(
                QueueAppearance.resolve("route", "follow", "follow"), QueueRowSize.NORMAL)
        assertEquals(route.coverStart + route.artworkSize / 2, route.stationCenter)
    }
}
