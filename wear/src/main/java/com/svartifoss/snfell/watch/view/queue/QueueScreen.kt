package com.svartifoss.snfell.watch.view.queue

import android.graphics.Bitmap
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.draw.paint
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SwipeToDismissBox
import androidx.wear.compose.material3.Text
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.BitmapBlur
import com.svartifoss.snfell.watch.theme.LocalWatchUiFontFamily
import com.svartifoss.snfell.watch.theme.WatchTheme
import com.svartifoss.snfell.watch.view.compose.CurvedClock
import com.svartifoss.snfell.watch.view.compose.CurvedScrollIndicator
import com.svartifoss.snfell.watch.view.compose.EqualizerBars
import com.svartifoss.snfell.watch.view.compose.LoadingBars
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** View model for one queue row. [isPlaying] marks the entry the phone reports as currently active. */
data class QueueItemUi(
        val entryId: String,
        val title: String,
        val subtitle: String?,
        val isPlaying: Boolean,
        /** Cover published by the media app for this queue item. Null keeps the row text-only. */
        val artwork: Bitmap? = null
)

// Idle rows are near-black for an OLED-dark look; the now-playing row uses the full album accent.
private val IDLE_PILL_COLOR = Color(WatchTheme.SURFACE_DARK)
private const val SUBTITLE_ALPHA = 0.65f
// How long the loading spinner may wait for the phone's queue response before giving up and
// showing the empty message instead (e.g. phone out of range never answers at all).
private const val QUEUE_LOAD_TIMEOUT_MS = 6000L

/** How still the list must be before the marquee titles start scrolling again. Long enough not to
 *  restart them between two flicks of a continuing scroll, short enough that a stop feels immediate. */
private const val SCROLL_SETTLE_MS = 220L

/** The app-wide Google Sans typeface, so the queue matches the rest of the watch UI. */


/** User-selectable visual style of the queue (see [MiscPreferences.WEAR_QUEUE_STYLE] on the
 *  phone). Shares the four-name vocabulary with the volume and quick-panel overlays. */
enum class QueueStyle {
    /** Frosted glass pills; the now-playing entry is a light accent pill (original look). */
    GLASS,
    /** AMOLED-flat: no pill, a thin accent keyline marks the now-playing row. */
    MINIMAL,
    /** Material Design 2: solid dark-grey rounded cards with an accent keyline on now-playing. */
    MATERIAL,
    /** Expressive: rounded cards tinted in the album accent, tall and bold. */
    TONAL,
    /** Transparent rows with a glowing album-accent outline; now-playing text/border in accent. */
    NEON,
    /** Light theme: pale cards with dark text; now-playing is an accent pill. */
    LIGHT,
    /** Vertical-gradient cards made from two album-art swatches. */
    GRADIENT,
    /** Neutral greyscale, ignoring the album accent. */
    MONO,
    /** Thick white cartoon outline, transparent fill. */
    OUTLINE,
    /** Two-hue: primary album swatch for now-playing, secondary swatch for the rest. */
    DUOTONE,
    /** Pure black/white, thick strokes (high contrast). */
    CONTRAST,
    /** Sharp-cornered monochrome-green CRT look. */
    TERMINAL,
    /** Three real album swatches with a diagonal glass keyline. */
    PRISM,
    /** Light translucent frosted panels. */
    FROST,
    /** Quiet pastel blend with broad corners and a fine highlight. */
    SOFT,
    /** Dense neutral slabs with tight corners and an album-colour active edge. */
    SLAB,
    /** Nearly chromeless rows marked by a wet album-colour underline. */
    INK,
    /** Compact dark cards tied together visually by a persistent palette rail. */
    RAIL,
    /** Warm-feeling vertical three-swatch blend, brightening the active row. */
    SUNSET,
    /** Album-tonal speech bubbles with an asymmetric tail corner. */
    BUBBLE,
    /** Desaturated three-swatch metallic bands with a polished keyline. */
    CHROME,
    /** Iridescent sweep through all three real album swatches. */
    HOLO,
    /** The entry's own cover art fills the whole pill, with the title over a legibility scrim -
     *  the Wear OS media-template "browse" look. Rows with no artwork of their own fall back to
     *  [GLASS], since per-item art depends on the player and shortcut thumbnails are opt-in. */
    COVER,
    /** Cover, blurred: the art is a soft backdrop and the sharp thumbnail returns to its slot. */
    COVER_BLUR,
    /** Cover washed in the album accent instead of a neutral black scrim. */
    COVER_TONAL,
    /** Cover on tight rows - more entries visible per screen. */
    COVER_COMPACT,
    /** Cover on tall poster rows, closest to the Wear OS browse mock-ups. */
    COVER_TALL,
    /** Cover with squared-off corners rather than a stadium pill. */
    COVER_SQUARE;

    companion object {
        fun fromPref(value: String?): QueueStyle = when (value) {
            "minimal" -> MINIMAL
            "material" -> MATERIAL
            "tonal" -> TONAL
            "neon" -> NEON
            "light" -> LIGHT
            "gradient" -> GRADIENT
            "mono" -> MONO
            "outline" -> OUTLINE
            "duotone" -> DUOTONE
            "contrast" -> CONTRAST
            "terminal" -> TERMINAL
            "frost" -> FROST
            "prism" -> PRISM
            "soft" -> SOFT
            "slab" -> SLAB
            "ink" -> INK
            "rail" -> RAIL
            "sunset" -> SUNSET
            "bubble" -> BUBBLE
            "chrome" -> CHROME
            "holo" -> HOLO
            "cover" -> COVER
            "cover_blur" -> COVER_BLUR
            "cover_tonal" -> COVER_TONAL
            "cover_compact" -> COVER_COMPACT
            "cover_tall" -> COVER_TALL
            "cover_square" -> COVER_SQUARE
            else -> GLASS
        }

        /** Every variation that fills the pill with the entry's artwork. */
        val COVER_FAMILY = setOf(
                COVER, COVER_BLUR, COVER_TONAL, COVER_COMPACT, COVER_TALL, COVER_SQUARE)
    }

    /** True for any cover variation; the shared switch the surfaces branch on. */
    val isCover: Boolean get() = this in COVER_FAMILY

    /** Cover variations keep the sharp thumbnail only when the backdrop is blurred - otherwise
     *  the same image would appear twice. */
    val coverKeepsThumbnail: Boolean get() = this == COVER_BLUR

    /** Legacy size presets: these two values predate [QueueRowSize] and are kept only so an
     *  already-synced preference still renders at the size it named. New selections express size
     *  through the separate row-size preference, which works with every style. */
    val legacyRowSize: QueueRowSize? get() = when (this) {
        COVER_COMPACT -> QueueRowSize.COMPACT
        COVER_TALL -> QueueRowSize.TALL
        else -> null
    }
}

/** Monochrome-green used by the terminal/CRT style. */
private val TERMINAL_GREEN = Color(0xFF33FF66)

/** Material Design 2 surface grey used for the "material" queue cards. */
private val MATERIAL_CARD_COLOR = Color(0xFF2A2A2A)
private val LIGHT_SURFACE = Color(0xFFECECEC)
private val LIGHT_ON = Color(0xFF111111)
private val MONO_IDLE = Color(0xFF262626)
private val MONO_ACTIVE = Color(0xFFE0E0E0)
private val SLAB_SURFACE = Color(0xFF1E1E20)

/** The one non-uniform queue silhouette. Keeping this in the geometry contract lets tests pin
 *  that Bubble does not silently collapse back into another rounded rectangle. */
internal enum class QueueRowShapeFamily { UNIFORM, SPEECH_BUBBLE }

/** Layout tokens owned by a queue style. Colour/brush treatment stays in [queueRowSkin], while
 *  every measurement that must also govern artwork and list rhythm lives together here. */
internal data class QueueRowGeometry(
        val corner: Dp,
        val verticalPadding: Dp,
        val spacing: Dp,
        val artworkCornerFraction: Float,
        val shapeFamily: QueueRowShapeFamily = QueueRowShapeFamily.UNIFORM
)

internal fun queueRowGeometry(style: QueueStyle): QueueRowGeometry = when (style) {
    QueueStyle.GLASS -> QueueRowGeometry(26.dp, 12.dp, 6.dp, .5f)
    QueueStyle.MINIMAL -> QueueRowGeometry(0.dp, 10.dp, 2.dp, .067f)
    QueueStyle.MATERIAL -> QueueRowGeometry(12.dp, 14.dp, 8.dp, .2f)
    QueueStyle.TONAL -> QueueRowGeometry(28.dp, 16.dp, 8.dp, .5f)
    QueueStyle.NEON -> QueueRowGeometry(18.dp, 12.dp, 6.dp, .3f)
    QueueStyle.LIGHT -> QueueRowGeometry(20.dp, 13.dp, 8.dp, .333f)
    QueueStyle.GRADIENT -> QueueRowGeometry(22.dp, 14.dp, 8.dp, .367f)
    QueueStyle.MONO -> QueueRowGeometry(14.dp, 13.dp, 6.dp, .233f)
    QueueStyle.OUTLINE -> QueueRowGeometry(16.dp, 12.dp, 6.dp, .267f)
    QueueStyle.DUOTONE -> QueueRowGeometry(22.dp, 14.dp, 8.dp, .367f)
    QueueStyle.CONTRAST -> QueueRowGeometry(8.dp, 13.dp, 6.dp, .133f)
    QueueStyle.TERMINAL -> QueueRowGeometry(0.dp, 11.dp, 2.dp, 0f)
    QueueStyle.PRISM -> QueueRowGeometry(22.dp, 14.dp, 8.dp, .267f)
    QueueStyle.FROST -> QueueRowGeometry(24.dp, 13.dp, 8.dp, .4f)
    QueueStyle.SOFT -> QueueRowGeometry(30.dp, 15.dp, 8.dp, .45f)
    QueueStyle.SLAB -> QueueRowGeometry(10.dp, 12.dp, 5.dp, .167f)
    QueueStyle.INK -> QueueRowGeometry(24.dp, 11.dp, 4.dp, .3f)
    QueueStyle.RAIL -> QueueRowGeometry(6.dp, 13.dp, 3.dp, .067f)
    QueueStyle.SUNSET -> QueueRowGeometry(20.dp, 15.dp, 9.dp, .4f)
    QueueStyle.BUBBLE -> QueueRowGeometry(
            28.dp, 16.dp, 10.dp, .5f, QueueRowShapeFamily.SPEECH_BUBBLE)
    QueueStyle.CHROME -> QueueRowGeometry(12.dp, 13.dp, 6.dp, .2f)
    QueueStyle.HOLO -> QueueRowGeometry(26.dp, 14.dp, 7.dp, .367f)
    QueueStyle.COVER -> QueueRowGeometry(26.dp, 12.dp, 6.dp, .5f)
    QueueStyle.COVER_BLUR -> QueueRowGeometry(26.dp, 12.dp, 6.dp, .5f)
    QueueStyle.COVER_TONAL -> QueueRowGeometry(26.dp, 12.dp, 6.dp, .5f)
    QueueStyle.COVER_COMPACT -> QueueRowGeometry(26.dp, 8.dp, 6.dp, .5f)
    QueueStyle.COVER_TALL -> QueueRowGeometry(26.dp, 14.dp, 6.dp, .5f)
    QueueStyle.COVER_SQUARE -> QueueRowGeometry(10.dp, 12.dp, 6.dp, .2f)
}

/** Per-style skin for one queue row. */
private class QueueRowSkin(
        val background: Brush,
        val onColor: Color,
        val corner: Dp,
        val verticalPadding: Dp,
        /** Left accent bar drawn to mark the now-playing row, or null for none. */
        val keyline: Color?,
        /** Rail variants use a wider/longer left mark than the standard active keyline. */
        val keylineWidth: Dp = 3.dp,
        val keylineInsetFraction: Float = .18f,
        /** Bottom ink stroke (width, colour), independent from a full outline. */
        val underline: Pair<Dp, Color>? = null,
        /** Outline (width, colour) drawn around the row, or null for none. */
        val border: Pair<Dp, Color>? = null
)

private fun queueRowSkin(
        style: QueueStyle,
        isPlaying: Boolean,
        accent: Color,
        secondaryAccent: Color,
        tertiaryAccent: Color
): QueueRowSkin {
    val lighten = lightenForBlackText(accent)
    val geometry = queueRowGeometry(style)
    return when (style) {
        QueueStyle.GLASS -> QueueRowSkin(
                background = SolidColor(if (isPlaying) lighten else IDLE_PILL_COLOR),
                onColor = if (isPlaying) Color.Black else Color.White,
                corner = geometry.corner, verticalPadding = geometry.verticalPadding,
                keyline = null
        )
        QueueStyle.MINIMAL -> QueueRowSkin(
                background = SolidColor(Color.Transparent),
                onColor = if (isPlaying) accent else Color.White,
                corner = geometry.corner, verticalPadding = geometry.verticalPadding,
                keyline = if (isPlaying) accent else null
        )
        QueueStyle.MATERIAL -> QueueRowSkin(
                background = SolidColor(MATERIAL_CARD_COLOR),
                onColor = if (isPlaying) accent else Color.White,
                corner = geometry.corner, verticalPadding = geometry.verticalPadding,
                keyline = if (isPlaying) accent else null
        )
        QueueStyle.TONAL -> QueueRowSkin(
                background = SolidColor(if (isPlaying) lighten else tonalColor(accent, 0.22f)),
                onColor = if (isPlaying) Color.Black else Color.White,
                corner = geometry.corner, verticalPadding = geometry.verticalPadding,
                keyline = null
        )
        QueueStyle.NEON -> QueueRowSkin(
                background = SolidColor(Color.Transparent),
                onColor = if (isPlaying) accent else Color.White,
                corner = geometry.corner, verticalPadding = geometry.verticalPadding,
                keyline = null,
                border = (if (isPlaying) 2.dp else 1.dp) to (if (isPlaying) accent else accent.copy(alpha = 0.5f))
        )
        QueueStyle.LIGHT -> QueueRowSkin(
                background = SolidColor(if (isPlaying) lighten else LIGHT_SURFACE),
                onColor = if (isPlaying) Color.Black else LIGHT_ON,
                corner = geometry.corner, verticalPadding = geometry.verticalPadding,
                keyline = null
        )
        QueueStyle.GRADIENT -> QueueRowSkin(
                background = if (isPlaying) {
                    Brush.verticalGradient(listOf(lighten, tonalColor(secondaryAccent, 0.55f)))
                } else {
                    Brush.verticalGradient(listOf(
                            tonalColor(accent, 0.26f),
                            tonalColor(secondaryAccent, 0.13f)
                    ))
                },
                onColor = if (isPlaying) Color.Black else Color.White,
                corner = geometry.corner, verticalPadding = geometry.verticalPadding,
                keyline = null
        )
        QueueStyle.MONO -> QueueRowSkin(
                background = SolidColor(if (isPlaying) MONO_ACTIVE else MONO_IDLE),
                onColor = if (isPlaying) Color.Black else Color.White,
                corner = geometry.corner, verticalPadding = geometry.verticalPadding,
                keyline = null
        )
        QueueStyle.OUTLINE -> QueueRowSkin(
                background = SolidColor(Color.Transparent),
                onColor = if (isPlaying) accent else Color.White,
                corner = geometry.corner, verticalPadding = geometry.verticalPadding,
                keyline = null,
                border = (if (isPlaying) 3.dp else 2.5.dp) to (if (isPlaying) accent else Color.White)
        )
        QueueStyle.DUOTONE -> QueueRowSkin(
                background = SolidColor(
                        if (isPlaying) lighten else tonalColor(secondaryAccent, 0.24f)),
                onColor = if (isPlaying) Color.Black else Color.White,
                corner = geometry.corner, verticalPadding = geometry.verticalPadding,
                keyline = null
        )
        QueueStyle.PRISM -> QueueRowSkin(
                background = Brush.linearGradient(
                        if (isPlaying) {
                            listOf(
                                    tonalColor(tertiaryAccent, .52f),
                                    lighten,
                                    tonalColor(secondaryAccent, .46f))
                        } else {
                            listOf(
                                    tonalColor(tertiaryAccent, .18f),
                                    tonalColor(accent, .28f),
                                    tonalColor(secondaryAccent, .14f))
                        }
                ),
                onColor = if (isPlaying) Color.Black else Color.White,
                corner = geometry.corner,
                verticalPadding = geometry.verticalPadding,
                keyline = null,
                border = 1.dp to Color.White.copy(alpha = .38f)
        )
        QueueStyle.CONTRAST -> QueueRowSkin(
                background = SolidColor(if (isPlaying) Color.White else Color.Black),
                onColor = if (isPlaying) Color.Black else Color.White,
                corner = geometry.corner, verticalPadding = geometry.verticalPadding,
                keyline = null,
                border = if (isPlaying) null else 2.dp to Color.White
        )
        QueueStyle.TERMINAL -> QueueRowSkin(
                background = SolidColor(Color.Transparent),
                onColor = TERMINAL_GREEN,
                corner = geometry.corner, verticalPadding = geometry.verticalPadding,
                keyline = null,
                border = (if (isPlaying) 2.dp else 1.dp) to TERMINAL_GREEN
        )
        QueueStyle.FROST -> QueueRowSkin(
                background = SolidColor(if (isPlaying) accent.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.16f)),
                onColor = Color.White,
                corner = geometry.corner, verticalPadding = geometry.verticalPadding,
                keyline = null
        )
        QueueStyle.SOFT -> QueueRowSkin(
                background = Brush.horizontalGradient(
                        if (isPlaying) {
                            listOf(lighten, lightenForBlackText(secondaryAccent))
                        } else {
                            listOf(tonalColor(accent, .24f), tonalColor(secondaryAccent, .18f))
                        }),
                onColor = if (isPlaying) Color.Black else Color.White,
                corner = geometry.corner,
                verticalPadding = geometry.verticalPadding,
                keyline = null,
                border = 1.dp to Color.White.copy(alpha = if (isPlaying) .42f else .16f)
        )
        QueueStyle.SLAB -> QueueRowSkin(
                background = SolidColor(
                        if (isPlaying) tonalColor(secondaryAccent, .34f) else SLAB_SURFACE),
                onColor = Color.White,
                corner = geometry.corner,
                verticalPadding = geometry.verticalPadding,
                keyline = if (isPlaying) lighten else null,
                keylineWidth = 4.dp,
                keylineInsetFraction = .12f
        )
        QueueStyle.INK -> QueueRowSkin(
                background = Brush.horizontalGradient(listOf(
                        Color.White.copy(alpha = .045f),
                        tonalColor(accent, .14f).copy(alpha = .72f),
                        Color.Transparent)),
                onColor = if (isPlaying) lighten else Color.White,
                corner = geometry.corner,
                verticalPadding = geometry.verticalPadding,
                keyline = null,
                underline = (if (isPlaying) 3.dp else 1.dp) to
                        (if (isPlaying) lighten else secondaryAccent.copy(alpha = .58f))
        )
        QueueStyle.RAIL -> QueueRowSkin(
                background = Brush.horizontalGradient(listOf(
                        tonalColor(accent, if (isPlaying) .32f else .18f),
                        tonalColor(secondaryAccent, if (isPlaying) .18f else .10f))),
                onColor = Color.White,
                corner = geometry.corner,
                verticalPadding = geometry.verticalPadding,
                keyline = if (isPlaying) lighten else tertiaryAccent.copy(alpha = .68f),
                keylineWidth = if (isPlaying) 5.dp else 2.5.dp,
                keylineInsetFraction = .07f
        )
        QueueStyle.SUNSET -> QueueRowSkin(
                background = Brush.verticalGradient(
                        if (isPlaying) {
                            listOf(
                                    lightenForBlackText(tertiaryAccent),
                                    lighten,
                                    lightenForBlackText(secondaryAccent))
                        } else {
                            listOf(
                                    tonalColor(tertiaryAccent, .32f),
                                    tonalColor(accent, .22f),
                                    tonalColor(secondaryAccent, .14f))
                        }),
                onColor = if (isPlaying) Color.Black else Color.White,
                corner = geometry.corner,
                verticalPadding = geometry.verticalPadding,
                keyline = null
        )
        QueueStyle.BUBBLE -> QueueRowSkin(
                background = if (isPlaying) {
                    SolidColor(lighten)
                } else {
                    Brush.horizontalGradient(listOf(
                            tonalColor(secondaryAccent, .30f),
                            tonalColor(accent, .18f)))
                },
                onColor = if (isPlaying) Color.Black else Color.White,
                corner = geometry.corner,
                verticalPadding = geometry.verticalPadding,
                keyline = null,
                border = 1.dp to (if (isPlaying) {
                    tertiaryAccent.copy(alpha = .50f)
                } else {
                    accent.copy(alpha = .28f)
                })
        )
        QueueStyle.CHROME -> QueueRowSkin(
                background = Brush.verticalGradient(
                        if (isPlaying) {
                            listOf(
                                    chromeTone(tertiaryAccent, .88f),
                                    chromeTone(accent, .58f),
                                    chromeTone(secondaryAccent, .78f))
                        } else {
                            listOf(
                                    chromeTone(tertiaryAccent, .38f),
                                    chromeTone(accent, .10f),
                                    chromeTone(secondaryAccent, .30f))
                        }),
                onColor = if (isPlaying) Color.Black else Color.White,
                corner = geometry.corner,
                verticalPadding = geometry.verticalPadding,
                keyline = null,
                border = 1.dp to Color.White.copy(alpha = if (isPlaying) .70f else .30f)
        )
        QueueStyle.HOLO -> QueueRowSkin(
                background = Brush.sweepGradient(
                        if (isPlaying) {
                            listOf(
                                    lighten,
                                    lightenForBlackText(secondaryAccent),
                                    lightenForBlackText(tertiaryAccent),
                                    lighten)
                        } else {
                            listOf(
                                    tonalColor(accent, .20f),
                                    tonalColor(secondaryAccent, .26f),
                                    tonalColor(tertiaryAccent, .16f),
                                    tonalColor(accent, .20f))
                        }),
                onColor = if (isPlaying) Color.Black else Color.White,
                corner = geometry.corner,
                verticalPadding = geometry.verticalPadding,
                keyline = null,
                border = 1.dp to Color.White.copy(alpha = if (isPlaying) .60f else .28f)
        )
        // The cover itself is drawn by the row (a Brush cannot carry a bitmap); this is the
        // fill that shows through where there is no artwork, matching GLASS so an art-less row
        // is indistinguishable from the plain style. Text stays white over the scrim, and the
        // now-playing row is marked with an accent keyline rather than a light pill, which would
        // fight the artwork underneath it.
        QueueStyle.COVER, QueueStyle.COVER_BLUR, QueueStyle.COVER_TONAL,
        QueueStyle.COVER_COMPACT, QueueStyle.COVER_TALL, QueueStyle.COVER_SQUARE -> QueueRowSkin(
                background = SolidColor(IDLE_PILL_COLOR),
                onColor = Color.White,
                corner = geometry.corner,
                verticalPadding = geometry.verticalPadding,
                keyline = if (isPlaying) accent else null
        )
    }
}

/** The artwork keyline every queue row is built around; row height is this plus the style's
 *  vertical padding, so one-line and two-line entries come out the same height. */
private val QUEUE_ROW_CONTENT_HEIGHT = 30.dp

/**
 * User-chosen list pill height (MiscPreferences.WEAR_LIST_ROW_SIZE). Independent of the style so
 * any look can be made roomier or tighter; the style still supplies its own padding rhythm on top
 * of this content height.
 */
enum class QueueRowSize(val contentHeight: Dp) {
    COMPACT(22.dp),
    NORMAL(QUEUE_ROW_CONTENT_HEIGHT),
    TALL(52.dp),
    XTALL(78.dp);

    companion object {
        fun fromPref(value: String?): QueueRowSize = when (value) {
            "compact" -> COMPACT
            "tall" -> TALL
            "xtall" -> XTALL
            else -> NORMAL
        }
    }
}

/**
 * Legibility scrim laid over a cover-filled pill. Album art is arbitrary - it can be white,
 * busy, or the same hue as the text - so the title needs a guaranteed floor of contrast. The
 * gradient is horizontal and heaviest on the left, where the (left-aligned) title sits, letting
 * the right side of the artwork stay visible the way the Wear OS media template does.
 */
internal fun coverScrim(tint: Color = Color.Black): Brush = Brush.horizontalGradient(
        0f to tint.copy(alpha = .74f),
        .55f to tint.copy(alpha = .46f),
        1f to tint.copy(alpha = .22f)
)

/**
 * Scrim for one cover variation. Tonal washes the row in a darkened album accent instead of
 * neutral black; Blur already softens the art underneath, so it needs far less darkening to stay
 * legible and keeps more of the colour visible.
 */
internal fun coverScrimFor(style: QueueStyle, accent: Color): Brush = when (style) {
    QueueStyle.COVER_TONAL -> coverScrim(darkenForScrim(accent))
    QueueStyle.COVER_BLUR -> Brush.horizontalGradient(
            0f to Color.Black.copy(alpha = .50f),
            1f to Color.Black.copy(alpha = .28f)
    )
    else -> coverScrim()
}

/** Pulls an accent down to a dark, low-saturation tone usable as a scrim - a bright accent at
 *  70% alpha would tint the artwork without actually darkening it. */
private fun darkenForScrim(accent: Color): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(accent.toArgb(), hsl)
    hsl[1] = (hsl[1] * .8f).coerceIn(0f, .65f)
    hsl[2] = .16f
    return Color(ColorUtils.HSLToColor(hsl))
}

/**
 * Fills the node with [image], center-cropped and clipped to [shape], then lays [scrim] over it.
 *
 * Deliberately a draw-only modifier. The obvious `Modifier.paint` route participates in
 * *measurement*: it sizes the node to the painter's intrinsic size unless told otherwise, so rows
 * inherited the artwork's pixel dimensions and every entry came out a different height - small
 * covers looked right, large ones ballooned into squares. Drawing behind the content cannot affect
 * layout at all, which is the property this needs.
 */
internal fun Modifier.coverFill(image: ImageBitmap, shape: Shape, scrim: Brush): Modifier =
        clip(shape).drawBehind {
            // Center-crop by choosing the source rect that matches the destination's aspect
            // ratio, rather than by scaling the image past the node and relying on the clip.
            val dstAspect = if (size.height > 0f) size.width / size.height else 1f
            val srcAspect = if (image.height > 0) {
                image.width.toFloat() / image.height.toFloat()
            } else {
                1f
            }
            val srcWidth: Int
            val srcHeight: Int
            if (srcAspect > dstAspect) {
                srcHeight = image.height
                srcWidth = (image.height * dstAspect).roundToInt().coerceIn(1, image.width)
            } else {
                srcWidth = image.width
                srcHeight = (image.width / dstAspect).roundToInt().coerceIn(1, image.height)
            }
            drawImage(
                    image = image,
                    srcOffset = IntOffset((image.width - srcWidth) / 2, (image.height - srcHeight) / 2),
                    srcSize = IntSize(srcWidth, srcHeight),
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
            )
            drawRect(scrim)
        }

/**
 * Backdrop blur for [QueueStyle.COVER_BLUR], using the same multi-pass blur as the player
 * background so the two read as the same effect. A single hard downscale (what this used to do)
 * left visible pixel blocks rather than a blur.
 */
internal fun blurredCover(source: Bitmap): Bitmap =
        BitmapBlur.blur(source, COVER_BLUR_RADIUS_PX)

/** Tuned for a pill-sized backdrop: enough to abstract the artwork without erasing its shapes. */
private const val COVER_BLUR_RADIUS_PX = 28f

/** Row spacing per style - tighter for flat/railed lists, roomier for soft statement cards. */
internal fun queueRowSpacing(style: QueueStyle): Dp = queueRowGeometry(style).spacing

/**
 * Air left between the cover and the pill's own edge. The cover used to be pinned at 30dp whatever
 * the row height was, so every style's vertical padding read as a thick border around a small
 * thumbnail - and picking a taller row size made the border grow instead of the artwork.
 */
internal val QUEUE_ARTWORK_INSET = 5.dp

/**
 * Ceiling on the cover, so a tall row does not crowd the text column off a 192dp screen. Reached
 * only by the two largest row sizes, where the artwork is already the row's dominant element.
 */
private val QUEUE_ARTWORK_MAX = 64.dp

/**
 * The cover's side length inside any list pill of [rowHeight]: the full height less
 * [QUEUE_ARTWORK_INSET] at top and bottom, capped by [QUEUE_ARTWORK_MAX].
 *
 * Shared by the queue, the action menu and the quick panel's full-width rows, which all draw the
 * same pill with the same 12dp padding rhythm. They each used to pin the cover at 30dp
 * independently, so fixing one left the others showing a small thumbnail in a large pill.
 */
internal fun listRowArtworkSize(rowHeight: Dp): Dp =
        minOf(rowHeight - QUEUE_ARTWORK_INSET * 2, QUEUE_ARTWORK_MAX)

/**
 * [listRowArtworkSize] for a queue row, whose height is the user's chosen content height plus the
 * style's own padding rhythm.
 *
 * Derived from the row rather than fixed, so the list row size preference actually scales the
 * artwork with everything else instead of leaving a bigger gap around a constant thumbnail.
 */
internal fun queueArtworkSize(rowSize: QueueRowSize, verticalPadding: Dp): Dp =
        listRowArtworkSize(rowSize.contentHeight + verticalPadding * 2)

/**
 * Height of the action-menu and quick-panel pills: the default content height plus the 12dp
 * padding rhythm every list pill shares. Those two surfaces are not resizable the way the queue
 * is, but their covers still come from [listRowArtworkSize] so all three agree.
 *
 * Deliberately built from [QUEUE_ROW_CONTENT_HEIGHT] rather than from `QueueRowSize.NORMAL`, even
 * though they are the same number: the enum reads that constant back out of this file, so touching
 * the enum from a top-level initialiser here makes the two classes initialise each other. The JVM
 * resolves that cycle by handing out a zeroed value - a silently 24dp-tall pill - rather than by
 * failing loudly.
 */
internal val LIST_ROW_HEIGHT = QUEUE_ROW_CONTENT_HEIGHT + 12.dp * 2

/**
 * Corner treatment for the cover inside a queue row, as a fraction of the cover's own side.
 *
 * A fraction rather than a dp value because the cover is no longer a fixed 30dp: a 15dp radius made
 * that size a circle, but the same 15dp on a tall row's 64dp cover is a rounded square, which would
 * silently change each style's shape family as soon as the row size changed. The fractions preserve
 * what those dp values meant at 30dp - genuinely pill-like styles (Glass/Tonal) stay circular,
 * square styles (Material/Terminal/Contrast) stay visibly squarer.
 *
 * The artwork is still shaped independently from the row itself: copying the pill's own radius onto
 * the image would clamp almost every style back to the same circle.
 */
internal fun queueArtworkCornerFraction(style: QueueStyle): Float =
        queueRowGeometry(style).artworkCornerFraction

/** Row silhouette. Bubble deliberately keeps one tight lower-left corner so it reads as a speech
 *  shape; the artwork remains circular and therefore does not inherit that tail. */
private fun queueRowShape(style: QueueStyle, corner: Dp): RoundedCornerShape =
        if (queueRowGeometry(style).shapeFamily == QueueRowShapeFamily.SPEECH_BUBBLE) {
            RoundedCornerShape(
                    topStart = corner,
                    topEnd = corner,
                    bottomEnd = corner,
                    bottomStart = 6.dp)
        } else {
            RoundedCornerShape(corner)
        }

/** Draws the deliberately partial chrome that cannot be expressed as a regular background or
 *  border: the active keyline, Rail's persistent spine, and Ink's bottom stroke. */
private fun Modifier.queueSkinMarks(skin: QueueRowSkin): Modifier {
    val keyline = skin.keyline
    val underline = skin.underline
    if (keyline == null && underline == null) return this
    return drawBehind {
        keyline?.let { color ->
            val inset = skin.keylineInsetFraction
            drawRoundRect(
                    color = color,
                    topLeft = Offset(0f, size.height * inset),
                    size = Size(
                            skin.keylineWidth.toPx(),
                            size.height * (1f - inset * 2f)),
                    cornerRadius = CornerRadius(2.dp.toPx())
            )
        }
        underline?.let { (width, color) ->
            val widthPx = width.toPx()
            drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * .12f, size.height - widthPx),
                    size = Size(size.width * .76f, widthPx),
                    cornerRadius = CornerRadius(widthPx / 2f)
            )
        }
    }
}

/** A dark, accent-tinted surface for the tonal idle rows - keeps saturation in a readable band. */
private fun tonalColor(accent: Color, lightness: Float): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(accent.toArgb(), hsl)
    hsl[1] = hsl[1].coerceIn(0.25f, 0.60f)
    hsl[2] = lightness
    return Color(ColorUtils.HSLToColor(hsl))
}

/** Turns one real album swatch into a low-saturation metallic stop without discarding its hue. */
private fun chromeTone(swatch: Color, lightness: Float): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(swatch.toArgb(), hsl)
    hsl[1] = hsl[1].coerceAtMost(.10f)
    hsl[2] = lightness
    return Color(ColorUtils.HSLToColor(hsl))
}

/**
 * Playback queue screen. A [ScalingLazyColumn] of glass pills (with a now-playing header on top)
 * where the active entry is highlighted with the full album [accentColor] and a contrast-matched
 * text color. Wrapped in a [SwipeToDismissBox] so swiping right closes only this screen.
 *
 * [items] is null while the queue request is still in flight (loading spinner); an empty list
 * means the phone answered but has no queue to show (empty message).
 *
 * [isHistoryFallback] says these rows are the recently-played list the phone substitutes when the
 * playing app publishes no queue - see [QueueViewModel.isHistoryFallback] for why the screen must
 * label that rather than render it as a queue.
 */
@Composable
fun QueueScreen(
        items: List<QueueItemUi>?,
        accentColor: Color,
        secondaryAccentColor: Color,
        tertiaryAccentColor: Color,
        nowPlayingTitle: String?,
        nowPlayingArtist: String?,
        onItemClick: (entryId: String) -> Unit,
        onDismiss: () -> Unit,
        style: QueueStyle = QueueStyle.GLASS,
        rowSize: QueueRowSize = QueueRowSize.NORMAL,
        canLoadMore: Boolean = false,
        loadingMore: Boolean = false,
        isHistoryFallback: Boolean = false,
        onLoadMore: () -> Unit = {}
) {
    // A legacy cover_compact / cover_tall selection still names its own size; the standalone
    // preference owns it for every other value.
    val effectiveRowSize = style.legacyRowSize ?: rowSize
    // Guard: SwipeToDismissBox can fire onDismissed more than once in edge cases (e.g. the system
    // windowSwipeToDismiss racing with the Compose gesture). Only forward the first call.
    var dismissed by remember { mutableStateOf(false) }
    SwipeToDismissBox(onDismissed = {
        if (!dismissed) { dismissed = true; onDismiss() }
    }) { isBackground ->
        // Only the foreground gets content; the swipe "background" stays empty (the opaque
        // window is black, so swiping back slides the list away over black - one clean close).
        if (!isBackground) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                QueueList(
                        items,
                        accentColor,
                        secondaryAccentColor,
                        tertiaryAccentColor,
                        nowPlayingTitle,
                        nowPlayingArtist,
                        onItemClick,
                        style,
                        effectiveRowSize,
                        canLoadMore,
                        loadingMore,
                        isHistoryFallback,
                        onLoadMore
                )
            }
        }
    }
}

@Composable
private fun QueueList(
        items: List<QueueItemUi>?,
        accentColor: Color,
        secondaryAccentColor: Color,
        tertiaryAccentColor: Color,
        nowPlayingTitle: String?,
        nowPlayingArtist: String?,
        onItemClick: (String) -> Unit,
        style: QueueStyle,
        rowSize: QueueRowSize,
        canLoadMore: Boolean,
        loadingMore: Boolean,
        isHistoryFallback: Boolean,
        onLoadMore: () -> Unit
) {
    val listState = rememberScalingLazyListState()

    // Whether the marquee titles may scroll. Only the marquee is gated on this - it re-lays out the
    // whole list every frame, which is what actually stuttered the scroll. The equalizer is not:
    // it redraws one small node and nothing else (see EqualizerBars), so it runs continuously.
    //
    // Driven by "the list has stopped moving" rather than by isScrollInProgress alone. That flag is
    // not a reliable falling edge here: a rotary/bezel session and an interrupted fling can both
    // leave it stuck true after the list has visibly come to rest, so anything relying on it alone
    // stays switched off for as long as the screen is open. Position is the honest signal: any
    // change restarts the wait (collectLatest), so the delay can only complete once nothing has
    // moved for SCROLL_SETTLE_MS, whatever the flag says.
    var listAtRest by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow {
            Triple(
                    listState.isScrollInProgress,
                    listState.centerItemIndex,
                    listState.centerItemScrollOffset)
        }.collectLatest { (scrolling, _, _) ->
            if (scrolling) listAtRest = false
            delay(SCROLL_SETTLE_MS)
            listAtRest = true
        }
    }

    // How many rows sit above the queue itself, so the scroll target below is a list index rather
    // than a queue index. Derived here, right beside the `item {}` calls that produce them, because
    // the two drifting apart would centre the list one row off and there is nothing to catch that.
    val leadingRows = 1 + if (isHistoryFallback) 1 else 0

    val targetRow = remember(items, nowPlayingTitle) {
        QueueScrollPolicy.activeRowIndex(
                playing = items.orEmpty().map { it.isPlaying },
                titles = items.orEmpty().map { it.title },
                nowPlayingTitle = nowPlayingTitle)
    }

    // Keyed on the *entry* rather than on the row index or the list: the phone republishes the
    // queue on every track change and "Load more" appends to it, so keying on either would re-run
    // this over a list already in the right place - and, in the paging case, yank the user back to
    // the playing track the moment they asked to see further down it.
    val targetEntryId = items?.getOrNull(targetRow)?.entryId
    LaunchedEffect(targetEntryId, leadingRows) {
        if (targetEntryId == null) return@LaunchedEffect
        val targetIndex = leadingRows + targetRow
        when (QueueScrollPolicy.resolve(listState.centerItemIndex, targetIndex)) {
            QueueScrollPolicy.Move.NONE -> Unit
            QueueScrollPolicy.Move.ANIMATE -> listState.animateScrollToItem(targetIndex)
            QueueScrollPolicy.Move.JUMP -> listState.scrollToItem(targetIndex)
        }
    }

    // Restarts whenever the load state changes; only ever flips to true while items is still
    // null, so a late phone response after the timeout still replaces the empty message.
    var loadTimedOut by remember { mutableStateOf(false) }
    LaunchedEffect(items == null) {
        if (items == null) {
            delay(QUEUE_LOAD_TIMEOUT_MS)
            loadTimedOut = true
        }
    }

    Box(Modifier.fillMaxSize()) {
        when {
            items == null && !loadTimedOut -> QueueLoadingIndicator(accentColor)
            items.isNullOrEmpty() -> QueueEmptyMessage()
            else -> ScalingLazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    // Extra top padding leaves room for the curved clock at the top bezel.
                    contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 36.dp, bottom = 26.dp),
                    verticalArrangement = Arrangement.spacedBy(queueRowSpacing(style)),
                    // Same fix as MenuScreen: the old overload (no rotary param) is a deprecated
                    // compatibility shim whose legacy touch path is why swipes weren't scrolling.
                    rotaryScrollableBehavior = RotaryScrollableDefaults.behavior(scrollableState = listState)
            ) {
                item { QueueHeader(nowPlayingTitle, nowPlayingArtist, marquee = listAtRest) }
                // Sits between the now-playing header and the rows, which is where a section
                // caption belongs and, more to the point, is unmissable before the first row is
                // read as "what plays next".
                if (isHistoryFallback) {
                    item(key = HISTORY_CAPTION_KEY) { QueueHistoryCaption(accentColor) }
                }
                items(items, key = { it.entryId }) { item ->
                    QueueRow(
                            item,
                            accentColor,
                            secondaryAccentColor,
                            tertiaryAccentColor,
                            onItemClick,
                            marquee = listAtRest,
                            style = style,
                            rowSize = rowSize
                    )
                }
                if (canLoadMore) {
                    item(key = LOAD_MORE_KEY) {
                        LoadMoreRow(
                                accentColor = accentColor,
                                loading = loadingMore,
                                style = style,
                                rowSize = rowSize,
                                onClick = onLoadMore
                        )
                    }
                }
            }
        }

        // Fades out as the user scrolls down (centerItemIndex > 0 means the header is no longer
        // the center item) so it doesn't overlap the list content. derivedStateOf so this scope
        // only recomposes when the boolean flips, not on every center-item change while scrolling.
        val clockVisible by remember { derivedStateOf { listState.centerItemIndex == 0 } }
        CurvedClock(visible = clockVisible)

        CurvedScrollIndicator(listState)
    }
}

@Composable
private fun QueueHeader(title: String?, artist: String?, marquee: Boolean) {
    Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!title.isNullOrBlank()) {
            Text(
                    text = title,
                    color = Color.White,
                    fontFamily = LocalWatchUiFontFamily.current,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().then(
                            if (marquee) Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                            else Modifier
                    )
            )
        }
        if (!artist.isNullOrBlank()) {
            Text(
                    text = artist,
                    color = Color.White.copy(alpha = 0.6f),
                    fontFamily = LocalWatchUiFontFamily.current,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QueueRow(
        item: QueueItemUi,
        accentColor: Color,
        secondaryAccentColor: Color,
        tertiaryAccentColor: Color,
        onItemClick: (String) -> Unit,
        marquee: Boolean,
        style: QueueStyle,
        rowSize: QueueRowSize
) {
    // The lightened accent / tonal surfaces keep black or accent text readable regardless of the
    // album's hue - see queueRowSkin for how each style paints the row.
    val skin = queueRowSkin(
            style, item.isPlaying, accentColor, secondaryAccentColor, tertiaryAccentColor)
    val onRow = skin.onColor

    // background(shape) draws an anti-aliased rounded rect directly; the previous
    // clip(RoundedCornerShape) forced an offscreen saveLayer PER ROW on every scroll frame
    // (hardware canvas can't anti-alias a rounded clip without one), which was the main source
    // of the scroll stutter. The row's content is inside padding and ellipsized, so it never
    // needs the rounded clip - only the tap ripple loses its rounded corners, which is
    // imperceptible next to smooth scrolling.
    val shape = queueRowShape(style, skin.corner)
    val border = skin.border
    val nowPlayingDescription = stringResource(R.string.queue_now_playing)
    // Cover style: the entry's own art fills the pill. Null for every other style, and for a
    // cover-style row whose entry has no artwork - which then renders as a plain Glass pill.
    val coverArt = if (style.isCover) item.artwork else null
    val coverImage = remember(coverArt, style) {
        coverArt?.let { if (style == QueueStyle.COVER_BLUR) blurredCover(it) else it }
                ?.asImageBitmap()
    }
    val showsThumbnail = item.artwork != null && (coverImage == null || style.coverKeepsThumbnail)
    Row(
            modifier = Modifier
                    .fillMaxWidth()
                    .background(skin.background, shape)
                    .then(
                            if (coverImage != null) {
                                // Only this style pays for the rounded clip (and its per-row
                                // saveLayer, see the note above) - it is the one treatment that
                                // genuinely needs the artwork clipped to the pill.
                                Modifier.coverFill(
                                        coverImage, shape, coverScrimFor(style, accentColor))
                            } else {
                                Modifier
                            }
                    )
                    .then(if (border != null) Modifier.border(border.first, border.second, shape) else Modifier)
                    .clickable { onItemClick(item.entryId) }
                    .semantics {
                        selected = item.isPlaying
                        if (item.isPlaying) stateDescription = nowPlayingDescription
                    }
                    .queueSkinMarks(skin)
                    // Height is derived from the style's padding around the 30dp artwork keyline
                    // rather than left to the content. A row whose track has no artist collapses
                    // to one line, so content-driven heights made the list ragged next to the
                    // quick panel's pills; each style still keeps its own rhythm via its padding.
                    .height(rowSize.contentHeight + skin.verticalPadding * 2)
                    // A row leading with a cover insets it by the same amount top, bottom and
                    // left, so the artwork sits in an even frame instead of being pushed inwards
                    // by a text keyline it does not need. Text-only rows keep the wider inset.
                    .padding(
                            start = if (showsThumbnail) QUEUE_ARTWORK_INSET else 16.dp,
                            end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        // The cover style already shows the art full-bleed behind the text; a second thumbnail
        // of the same image would just crowd the row.
        item.artwork?.takeIf { showsThumbnail }?.let { bitmap ->
            val image = remember(bitmap) { bitmap.asImageBitmap() }
            // The row still owns its height; the artwork now fills it rather than sitting at a
            // constant 30dp inside it, which is what made every pill look like a thick border
            // around a small cover.
            val artworkSize = queueArtworkSize(rowSize, skin.verticalPadding)
            val artworkCorner = artworkSize * queueArtworkCornerFraction(style)
            Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                            .size(artworkSize)
                            .then(
                                    if (artworkCorner > 0.dp) {
                                        Modifier.clip(RoundedCornerShape(artworkCorner))
                                    } else {
                                        Modifier
                                    }
                            )
            )
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                    text = item.title,
                    color = onRow,
                    fontFamily = LocalWatchUiFontFamily.current,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // Only the now-playing row scrolls its long title, and only while the list
                    // itself is at rest ([marquee]). Marquee on EVERY row (or during a scroll)
                    // re-lays the list out each frame and made scrolling visibly stutter.
                    modifier = if (item.isPlaying && marquee) {
                        Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                    } else {
                        Modifier
                    }
            )
            if (!item.subtitle.isNullOrBlank()) {
                Text(
                        text = item.subtitle,
                        color = onRow.copy(alpha = SUBTITLE_ALPHA),
                        fontFamily = LocalWatchUiFontFamily.current,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (item.isPlaying) {
            Spacer(Modifier.width(8.dp))
            EqualizerBars(color = onRow)
        }
    }
}

/** The house pulsing bars, shown while the queue request is still in flight. */
@Composable
private fun QueueLoadingIndicator(accentColor: Color) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LoadingBars(accentColor)
    }
}

/**
 * Stable key for the trailing "load more" row.
 *
 * Distinct from any entry id (which is always `queueId|mediaId`) so the list never confuses the two
 * when the row appears and disappears as pages arrive.
 */
private const val LOAD_MORE_KEY = "__load_more__"

/** Stable key for the recently-played caption, for the same reason [LOAD_MORE_KEY] has one. */
private const val HISTORY_CAPTION_KEY = "__history_caption__"

/**
 * Caption marking the rows below as the recently-played fallback rather than the playing queue.
 *
 * Deliberately not a pill: it is not tappable and must not read as another entry in the list. The
 * second line names the cause, because "Recently played" alone still leaves the user wondering why
 * their queue is missing - the answer is the playing app, not this app failing to fetch it.
 */
@Composable
private fun QueueHistoryCaption(accentColor: Color) {
    Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
                text = stringResource(R.string.queue_history_fallback),
                color = accentColor,
                fontFamily = LocalWatchUiFontFamily.current,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
        )
        Text(
                text = stringResource(R.string.queue_history_fallback_reason),
                color = Color.White.copy(alpha = 0.55f),
                fontFamily = LocalWatchUiFontFamily.current,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Trailing row that fetches the next page of a queue longer than what was sent.
 *
 * Wears the current style's own idle skin rather than a look of its own, so it reads as part of the
 * list instead of as a system affordance dropped on top of it. It replaces its label with a spinner
 * while the request is out because the phone answers by replacing the whole list - there is nothing
 * incremental to watch, and without the spinner a slow Bluetooth round trip looks like a dead tap.
 */
@Composable
private fun LoadMoreRow(
        accentColor: Color,
        loading: Boolean,
        style: QueueStyle,
        rowSize: QueueRowSize,
        onClick: () -> Unit
) {
    // Never the cover treatment: this row has no artwork of its own, and the cover styles fall back
    // to the Glass pill in exactly that case anyway.
    val renderedStyle = if (style.isCover) QueueStyle.GLASS else style
    val skin = queueRowSkin(
            renderedStyle,
            isPlaying = false,
            accent = accentColor,
            secondaryAccent = accentColor,
            tertiaryAccent = accentColor)
    val shape = queueRowShape(renderedStyle, skin.corner)
    Box(
            modifier = Modifier
                    .fillMaxWidth()
                    .background(skin.background, shape)
                    .then(
                            if (skin.border != null) {
                                Modifier.border(skin.border.first, skin.border.second, shape)
                            } else {
                                Modifier
                            }
                    )
                    .queueSkinMarks(skin)
                    // Taps are swallowed while a request is already in flight, so an impatient
                    // double tap cannot queue up two pages.
                    .clickable(enabled = !loading) { onClick() }
                    .height(rowSize.contentHeight + skin.verticalPadding * 2)
                    .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
    ) {
        if (loading) {
            // The same pulsing bars the playing row draws - see EqualizerBars. At row height
            // *inside* the list, where a circular indicator is the one shape in the column that is
            // not part of the queue's own vocabulary.
            EqualizerBars(skin.onColor)
        } else {
            Text(
                    text = stringResource(R.string.queue_load_more),
                    color = skin.onColor,
                    fontFamily = LocalWatchUiFontFamily.current,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Shown when the phone answered with no queue, or the request timed out entirely. */
@Composable
private fun QueueEmptyMessage() {
    Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
    ) {
        Icon(
                painter = painterResource(R.drawable.ic_queue_music),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(26.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
                text = stringResource(R.string.queue_empty),
                color = Color.White.copy(alpha = 0.65f),
                fontFamily = LocalWatchUiFontFamily.current,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
        )
    }
}

/** Adapts the accent so black text always reads on it - same rule as the menu's highlight. */
private fun lightenForBlackText(color: Color): Color =
        Color(WatchTheme.accentForSurface(color.toArgb()))

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 220, heightDp = 220)
@Composable
private fun QueueScreenEmptyPreview() {
    MaterialTheme {
        QueueScreen(
                items = emptyList(),
                accentColor = Color(0xFF9C5BD0),
                secondaryAccentColor = Color(0xFF3F739C),
                tertiaryAccentColor = Color(0xFF8C4F7E),
                nowPlayingTitle = null,
                nowPlayingArtist = null,
                onItemClick = {},
                onDismiss = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 220, heightDp = 220)
@Composable
private fun QueueScreenPreview() {
    MaterialTheme {
        QueueScreen(
                items = listOf(
                        QueueItemUi("1", "Только звёзды над нами", "BXZX & prettydien", false),
                        QueueItemUi("2", "WINGS", "Lieless, PRATEIN & Pimpie", true),
                        QueueItemUi("3", "Otpusti", "hxvvxn & damnenby", false)
                ),
                accentColor = Color(0xFF9C5BD0),
                secondaryAccentColor = Color(0xFF3F739C),
                tertiaryAccentColor = Color(0xFF8C4F7E),
                nowPlayingTitle = "WINGS",
                nowPlayingArtist = "Lieless, PRATEIN & Pimpie",
                onItemClick = {},
                onDismiss = {}
        )
    }
}
