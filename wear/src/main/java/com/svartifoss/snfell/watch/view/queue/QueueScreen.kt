package com.svartifoss.snfell.watch.view.queue

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SwipeToDismissBox
import androidx.wear.compose.material3.Text
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.BitmapBlur
import com.svartifoss.snfell.common.CoverCorners
import com.svartifoss.snfell.common.QueueAppearance
import com.svartifoss.snfell.common.QueuePosition
import com.svartifoss.snfell.common.QueueRouteColors
import com.svartifoss.snfell.common.QueueRowAppearance
import com.svartifoss.snfell.common.QueueRowFill
import com.svartifoss.snfell.common.QueueRowLayout
import com.svartifoss.snfell.common.QueueRowOutline
import com.svartifoss.snfell.common.QueueRowShape
import com.svartifoss.snfell.common.QueueRowSkin
import com.svartifoss.snfell.common.QueueRowSkins
import com.svartifoss.snfell.common.QueueShape
import com.svartifoss.snfell.common.QueueShortcutLink
import com.svartifoss.snfell.common.QueueStyleSpec
import com.svartifoss.snfell.common.QueueStyles
import com.svartifoss.snfell.watch.theme.LocalWatchTrackTextStyles
import com.svartifoss.snfell.watch.theme.LocalWatchUiFontFamily
import com.svartifoss.snfell.watch.theme.WatchTheme
import com.svartifoss.snfell.watch.view.compose.CurvedClock
import com.svartifoss.snfell.watch.view.compose.CurvedScrollIndicator
import com.svartifoss.snfell.watch.view.compose.EqualizerBars
import com.svartifoss.snfell.watch.view.compose.LoadingBars
import com.svartifoss.snfell.watch.view.face.marqueeFade
import com.svartifoss.snfell.watch.view.panel.PanelBackdropLayer
import com.svartifoss.snfell.watch.view.panel.ScreenBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** View model for one queue row. [isPlaying] marks the entry the phone reports as currently active. */
data class QueueItemUi(
        val entryId: String,
        val title: String,
        val subtitle: String?,
        val isPlaying: Boolean,
        /** Cover published by the media app for this queue item. Null keeps the row text-only. */
        val artwork: Bitmap? = null,
        /** Provider link published by the media app, if this row can become a shortcut. */
        val mediaUri: String? = null
)

private const val SUBTITLE_ALPHA = 0.65f
// How long the loading spinner may wait for the phone's queue response before giving up and
// showing the empty message instead (e.g. phone out of range never answers at all).
private const val QUEUE_LOAD_TIMEOUT_MS = 6000L

/** How still the list must be before the marquee titles start scrolling again. Long enough not to
 *  restart them between two flicks of a continuing scroll, short enough that a stop feels immediate. */
private const val SCROLL_SETTLE_MS = 220L

/**
 * User-selectable visual style of the queue (see [MiscPreferences.WEAR_QUEUE_STYLE] on the phone):
 * the rows' colours, gradients, strokes and marks. What each one is drawn with lives in the shared
 * registry ([QueueStyles], [QueueRowSkins]) so the phone's miniature paints the same thing; this
 * enum is the watch's handle on it, and the one the menu and the quick panel read for the cover
 * treatment they share with the queue.
 *
 * The outline and the cover cut are separate choices now ([QueueRowShape] and the queue's cover
 * shape); a style only supplies the ones it was drawn with, for when those are left on "follow".
 */
enum class QueueStyle(val token: String) {
    /** Frosted glass pills; the now-playing entry is a light accent pill (original look). */
    GLASS("glass"),
    /** AMOLED-flat: no pill, a thin accent keyline marks the now-playing row. */
    MINIMAL("minimal"),
    /** Material Design 2: solid dark-grey rounded cards with an accent keyline on now-playing. */
    MATERIAL("material"),
    /** Expressive: rounded cards tinted in the album accent, tall and bold. */
    TONAL("tonal"),
    /** Transparent rows with a glowing album-accent outline; now-playing text/border in accent. */
    NEON("neon"),
    /** Light theme: pale cards with dark text; now-playing is an accent pill. */
    LIGHT("light"),
    /** Vertical-gradient cards made from two album-art swatches. */
    GRADIENT("gradient"),
    /** Neutral greyscale, ignoring the album accent. */
    MONO("mono"),
    /** Thick white cartoon outline, transparent fill. */
    OUTLINE("outline"),
    /** Two-hue: primary album swatch for now-playing, secondary swatch for the rest. */
    DUOTONE("duotone"),
    /** Pure black/white, thick strokes (high contrast). */
    CONTRAST("contrast"),
    /** Sharp-cornered monochrome-green CRT look. */
    TERMINAL("terminal"),
    /** Three real album swatches with a diagonal glass keyline. */
    PRISM("prism"),
    /** Light translucent frosted panels. */
    FROST("frost"),
    /** Quiet pastel blend with broad corners and a fine highlight. */
    SOFT("soft"),
    /** Dense neutral slabs with tight corners and an album-colour active edge. */
    SLAB("slab"),
    /** Nearly chromeless rows marked by a wet album-colour underline. */
    INK("ink"),
    /** Compact dark cards tied together visually by a persistent palette rail. */
    RAIL("rail"),
    /** Warm-feeling vertical three-swatch blend, brightening the active row. */
    SUNSET("sunset"),
    /** Album-tonal speech bubbles with an asymmetric tail corner. */
    BUBBLE("bubble"),
    /** Desaturated three-swatch metallic bands with a polished keyline. */
    CHROME("chrome"),
    /** Iridescent sweep through all three real album swatches. */
    HOLO("holo"),
    /** The entry's own cover art fills the whole pill, with the title over a legibility scrim -
     *  the Wear OS media-template "browse" look. Rows with no artwork of their own fall back to
     *  [GLASS], since per-item art depends on the player and shortcut thumbnails are opt-in. */
    COVER("cover"),
    /** Cover, blurred: the art is a soft backdrop and the sharp thumbnail returns to its slot. */
    COVER_BLUR("cover_blur"),
    /** Cover washed in the album accent instead of a neutral black scrim. */
    COVER_TONAL("cover_tonal"),
    /** Cover on tight rows - a legacy size preset, see [legacyRowSize]. */
    COVER_COMPACT("cover_compact"),
    /** Cover on tall poster rows - a legacy size preset, see [legacyRowSize]. */
    COVER_TALL("cover_tall"),
    /** Cover with squared-off corners. No longer offered - it is Cover with the Soft corners pill
     *  shape - but a configuration holding it renders as it always did. */
    COVER_SQUARE("cover_square"),
    /** Stone rows cut as hexagonal columns - basalt columns on their side. */
    BASALT("basalt"),
    /** Flat rows with three thin layers of the album's own colours along the foot, like sediment. */
    STRATA("strata"),
    /** The queue as a route: every cover a station on one line through the list, the stretch
     *  already played in the album's colour and the playing entry held in a soft capsule. */
    ROUTE("route");

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
            "basalt" -> BASALT
            "strata" -> STRATA
            "route" -> ROUTE
            else -> GLASS
        }

        /** Every variation that fills the pill with the entry's artwork. */
        val COVER_FAMILY = setOf(
                COVER, COVER_BLUR, COVER_TONAL, COVER_COMPACT, COVER_TALL, COVER_SQUARE)
    }

    /** This style's entry in the shared registry. */
    val spec: QueueStyleSpec get() = QueueStyles.spec(token)

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
 * Legibility scrim laid over a cover-filled pill, in [tint]. Album art is arbitrary - it can be
 * white, busy, or the same hue as the text - so the title needs a guaranteed floor of contrast; the
 * stops are [QueueRowSkins.neutralScrim]'s, shared with the phone's miniature.
 */
internal fun coverScrim(tint: Color = Color.Black): Brush =
        scrimBrush(QueueRowSkins.neutralScrim(tint.toArgb()))

/** The scrim for one cover variation - see [QueueRowSkins.coverScrim]. */
internal fun coverScrimFor(style: QueueStyle, accent: Color): Brush =
        scrimBrush(QueueRowSkins.coverScrim(style.token, accent.toArgb()))

private fun scrimBrush(stops: List<Pair<Float, Int>>): Brush =
        Brush.horizontalGradient(*stops.map { (at, color) -> at to Color(color) }.toTypedArray())

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
 *
 * Several full-size bitmap passes, so it is not free: a list row reaches it through
 * [rememberCoverImage], which runs it off the main thread.
 */
internal fun blurredCover(source: Bitmap): Bitmap =
        blurredCovers.get(source)
                ?: BitmapBlur.blur(source, COVER_BLUR_RADIUS_PX).also { blurredCovers.put(source, it) }

/**
 * The image a cover-style row paints behind its text: the cover itself, or its blur for
 * [QueueStyle.COVER_BLUR]. Null while there is nothing to paint yet.
 *
 * The blur used to be made inside `remember` during composition - on the main thread, in the
 * middle of a scroll, once for every row entering the screen, and again whenever a row came back
 * into view (a lazy list disposes the rows it scrolls past, and `remember` goes with them). It is now
 * made on a background thread and kept in [blurredCovers], so a row scrolled back to finds it done.
 * Until the first blur lands the row is the plain pill - a frame or two, and usually before the row
 * is on screen at all, since the list composes the next rows ahead of the scroll. Never the sharp
 * cover in the meantime: a photograph turning into a blur reads as a glitch, a pill gaining its
 * backdrop does not.
 */
@Composable
internal fun rememberCoverImage(cover: Bitmap?, style: QueueStyle): ImageBitmap? {
    if (cover == null) return null
    if (style != QueueStyle.COVER_BLUR) return remember(cover) { cover.asImageBitmap() }
    // Looked up during composition, so a row scrolled back to draws its blur on its first frame.
    val made = remember(cover) { blurredCovers.get(cover)?.asImageBitmap() }
    if (made != null) return made
    val blurred by produceState<ImageBitmap?>(null, cover) {
        value = withContext(Dispatchers.Default) { blurredCover(cover) }.asImageBitmap()
    }
    return blurred
}

/**
 * The blurred covers made most recently, by the cover they were made from (by identity - the
 * watch keeps one decoded bitmap per cover asset across queue publications, so an unchanged cover
 * comes back as the same key). Bounded by bytes rather than entries because a blur keeps its
 * source's size: a Cover-style queue thumbnail is 320 px, several hundred kilobytes each.
 */
private val blurredCovers = object : LruCache<Bitmap, Bitmap>(BLURRED_COVER_CACHE_BYTES) {
    override fun sizeOf(key: Bitmap, value: Bitmap): Int = value.allocationByteCount
}

/** A little over a screenful of Cover-style rows. */
private const val BLURRED_COVER_CACHE_BYTES = 3 * 1024 * 1024

/** Tuned for a pill-sized backdrop: enough to abstract the artwork without erasing its shapes. */
private const val COVER_BLUR_RADIUS_PX = 28f

/**
 * Air left between the cover and the pill's own edge. The cover used to be pinned at 30dp whatever
 * the row height was, so every style's vertical padding read as a thick border around a small
 * thumbnail - and picking a taller row size made the border grow instead of the artwork.
 */
internal val QUEUE_ARTWORK_INSET = QueueRowLayout.ARTWORK_INSET_DP.dp

/**
 * Ceiling on the cover, so a tall row does not crowd the text column off a 192dp screen. Reached
 * only by the two largest row sizes, where the artwork is already the row's dominant element.
 */
private val QUEUE_ARTWORK_MAX = QueueRowLayout.ARTWORK_MAX_DP.dp

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

/** [QueueRowLayout] in Compose units: one row's height, cover, cover start and text inset. */
internal data class QueueRowMetrics(
        val height: Dp,
        val artworkSize: Dp,
        val coverStart: Dp,
        val textInset: Dp
) {
    /** Where the Route style's line passes through the row: the middle of its cover. */
    val stationCenter: Dp get() = coverStart + artworkSize / 2
}

internal fun queueRowMetrics(appearance: QueueRowAppearance, rowSize: QueueRowSize): QueueRowMetrics {
    val layout = QueueRowLayout.of(appearance, rowSize.contentHeight.value)
    return QueueRowMetrics(
            layout.heightDp.dp, layout.artworkDp.dp, layout.coverStartDp.dp, layout.textInsetDp.dp)
}

/**
 * A [QueueRowOutline] as a Compose [Shape]. The corners come from the shared outline, so the watch
 * and the phone's miniature round them with the same arithmetic - see [QueueRowOutline.Rounded.radii]
 * for why that cannot be left to each toolkit.
 */
private data class QueueOutlineShape(val outline: QueueRowOutline) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
            when (outline) {
                is QueueRowOutline.Rounded -> {
                    val radii = outline.radii(size.width, size.height, density.density)
                    Outline.Rounded(RoundRect(
                            left = 0f,
                            top = 0f,
                            right = size.width,
                            bottom = size.height,
                            topLeftCornerRadius = CornerRadius(radii[0]),
                            topRightCornerRadius = CornerRadius(radii[1]),
                            bottomRightCornerRadius = CornerRadius(radii[2]),
                            bottomLeftCornerRadius = CornerRadius(radii[3])))
                }
                is QueueRowOutline.Hexagon -> {
                    val points = outline.points(0f, 0f, size.width, size.height, density.density)
                    Outline.Generic(androidx.compose.ui.graphics.Path().apply {
                        moveTo(points[0], points[1])
                        for (corner in 1..5) lineTo(points[corner * 2], points[corner * 2 + 1])
                        close()
                    })
                }
            }
}

/** A cover's cut ([CoverCorners]) as a Compose [Shape], each corner a fraction of the cover. */
private data class CoverCornersShape(val corners: CoverCorners) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val side = min(size.width, size.height)
        return Outline.Rounded(RoundRect(
                left = 0f,
                top = 0f,
                right = size.width,
                bottom = size.height,
                topLeftCornerRadius = CornerRadius(corners.topLeft * side),
                topRightCornerRadius = CornerRadius(corners.topRight * side),
                bottomRightCornerRadius = CornerRadius(corners.bottomRight * side),
                bottomLeftCornerRadius = CornerRadius(corners.bottomLeft * side)))
    }
}

private fun QueueRowFill.toBrush(): Brush = when (this) {
    is QueueRowFill.Solid -> SolidColor(Color(color))
    is QueueRowFill.Gradient -> {
        val stops = colors.map(::Color)
        when (direction) {
            QueueRowFill.Direction.VERTICAL -> Brush.verticalGradient(stops)
            QueueRowFill.Direction.HORIZONTAL -> Brush.horizontalGradient(stops)
            QueueRowFill.Direction.DIAGONAL -> Brush.linearGradient(stops)
            QueueRowFill.Direction.SWEEP -> Brush.sweepGradient(stops)
        }
    }
}

/**
 * Draws the deliberately partial chrome that cannot be expressed as a regular background or
 * border: the active keyline, Rail's persistent spine, Ink's underline and Strata's layers.
 *
 * Each is placed inside the [outline] rather than against the row's box: the outline is chosen
 * apart from the style now, and a keyline at the box's edge hangs outside a capsule's round end.
 */
private fun Modifier.queueSkinMarks(skin: QueueRowSkin, outline: QueueRowOutline): Modifier {
    val keyline = skin.keyline
    val underline = skin.underline
    val strata = skin.strata
    if (keyline == null && underline == null && strata == null) return this
    return drawBehind {
        val width = size.width
        val height = size.height
        strata?.let { layers ->
            val line = QueueShape.STRATA_LINE_DP.dp.toPx()
            val tops = QueueShape.strataLineTops(height, line, QueueShape.STRATA_GAP_DP.dp.toPx())
            layers.forEachIndexed { index, color ->
                val top = tops[index]
                val middle = top + line / 2f
                val left = max(width * .06f, outline.leftInset(middle, width, height, density))
                val right = min(width * .94f, width - outline.rightInset(middle, width, height, density))
                if (right > left) {
                    drawRect(color = Color(color), topLeft = Offset(left, top), size = Size(right - left, line))
                }
            }
        }
        keyline?.let { mark ->
            val top = height * mark.insetFraction
            val bottom = height * (1f - mark.insetFraction)
            drawRoundRect(
                    color = Color(mark.color),
                    topLeft = Offset(outline.leftEdgeOver(top, bottom, width, height, density), top),
                    size = Size(mark.widthDp.dp.toPx(), bottom - top),
                    cornerRadius = CornerRadius(2.dp.toPx())
            )
        }
        underline?.let { stroke ->
            val thickness = stroke.widthDp.dp.toPx()
            val middle = height - thickness / 2f
            val left = max(width * .12f, outline.leftInset(middle, width, height, density))
            val right = min(width * .88f, width - outline.rightInset(middle, width, height, density))
            if (right > left) {
                drawRoundRect(
                        color = Color(stroke.color),
                        topLeft = Offset(left, height - thickness),
                        size = Size(right - left, thickness),
                        cornerRadius = CornerRadius(thickness / 2f)
                )
            }
        }
    }
}

/**
 * Playback queue screen. A [ScalingLazyColumn] of rows (with a now-playing header on top) where the
 * active entry is set apart by its style, in the album's colours. Wrapped in a [SwipeToDismissBox]
 * so swiping right closes only this screen.
 *
 * Three separate choices decide how a row looks: [style] (its colours and effects), [rowShape] (its
 * outline) and [coverShape] (how its cover is cut) - reconciled by [QueueAppearance], which ignores
 * a choice the style has nothing to apply it to. [rowSize] sets the height on top.
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
        /** "Track 3 of 47" under the header, when the phone said where the playing entry is. */
        queuePosition: QueuePosition? = null,
        onItemClick: (entryId: String) -> Unit,
        onItemLongClick: (QueueItemUi) -> Unit = {},
        onDismiss: () -> Unit,
        style: QueueStyle = QueueStyle.GLASS,
        rowSize: QueueRowSize = QueueRowSize.NORMAL,
        /** The rows' outline, apart from their style - see [QueueRowShape]. */
        rowShape: QueueRowShape = QueueRowShape.FOLLOW,
        /** How the covers are cut: a `CoverShape` value, or "follow" for the style's own cut. */
        coverShape: String? = QueueAppearance.FOLLOW,
        /** Dim the entries that already played. */
        dimPlayed: Boolean = false,
        canLoadMore: Boolean = false,
        loadingMore: Boolean = false,
        isHistoryFallback: Boolean = false,
        onLoadMore: () -> Unit = {},
        /**
         * The configured ground behind the rows, or null to keep the flat black field.
         *
         * Null is what every preview and test passes; the app always supplies one - see
         * [com.svartifoss.snfell.watch.view.panel.ScreenBackdrop] for why this screen stopped
         * being one of the two that ignored the theme.
         */
        screenBackdrop: ScreenBackdrop? = null
) {
    // A legacy cover_compact / cover_tall selection still names its own size; the standalone
    // preference owns it for every other value.
    val effectiveRowSize = style.legacyRowSize ?: rowSize
    val appearance = remember(style, rowShape, coverShape) {
        QueueAppearance.resolve(style.token, rowShape.token, coverShape)
    }
    val screenClip = if (LocalConfiguration.current.isScreenRound) {
        Modifier.clip(CircleShape)
    } else {
        Modifier
    }
    // Guard: SwipeToDismissBox can fire onDismissed more than once in edge cases (e.g. the system
    // windowSwipeToDismiss racing with the Compose gesture). Only forward the first call.
    var dismissed by remember { mutableStateOf(false) }
    SwipeToDismissBox(onDismissed = {
        if (!dismissed) { dismissed = true; onDismiss() }
    }) { isBackground ->
        // Only the foreground gets content; the swipe "background" stays empty (the opaque
        // window is black, so swiping back slides the list away over black - one clean close).
        if (!isBackground) {
            // ScalingLazyColumn scales edge rows, but still draws inside a rectangular
            // viewport. Clip the entire surface to the same circle as the round backdrop.
            Box(Modifier.fillMaxSize().then(screenClip).background(Color.Black)) {
                // Under the rows and above the black ground, exactly where the panels put it.
                screenBackdrop?.let { PanelBackdropLayer(it) }
                QueueList(
                        items,
                        accentColor,
                        secondaryAccentColor,
                        tertiaryAccentColor,
                        nowPlayingTitle,
                        nowPlayingArtist,
                        queuePosition,
                        onItemClick,
                        onItemLongClick,
                        style,
                        appearance,
                        effectiveRowSize,
                        dimPlayed,
                        canLoadMore,
                        loadingMore,
                        isHistoryFallback,
                        onLoadMore
                )
            }
        }
    }
}

/** The list's side padding - also how far in from the viewport each row starts, which the
 *  Route style's line needs to find the covers. */
private val QUEUE_LIST_SIDE_PADDING = 10.dp

@Composable
private fun QueueList(
        items: List<QueueItemUi>?,
        accentColor: Color,
        secondaryAccentColor: Color,
        tertiaryAccentColor: Color,
        nowPlayingTitle: String?,
        nowPlayingArtist: String?,
        queuePosition: QueuePosition?,
        onItemClick: (String) -> Unit,
        onItemLongClick: (QueueItemUi) -> Unit,
        style: QueueStyle,
        appearance: QueueRowAppearance,
        rowSize: QueueRowSize,
        dimPlayed: Boolean,
        canLoadMore: Boolean,
        loadingMore: Boolean,
        isHistoryFallback: Boolean,
        onLoadMore: () -> Unit
) {
    val listState = rememberScalingLazyListState()
    val metrics = remember(appearance, rowSize) { queueRowMetrics(appearance, rowSize) }
    val routeColors = if (appearance.style.route) {
        remember(accentColor) { QueueRowSkins.routeColors(accentColor.toArgb()) }
    } else {
        null
    }

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

    // How far each entry is from the playing one. Null when none is marked playing - the history
    // fallback - because there is then nothing to be relative to.
    val offsets = remember(items) { QueueShape.offsets(items.orEmpty().map { it.isPlaying }) }

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
        // Under the rows, so the line passes behind every cover and through the gaps between
        // them - the stations sit on it rather than it being drawn over their artwork.
        if (routeColors != null && !items.isNullOrEmpty()) {
            QueueRouteLine(
                    listState = listState,
                    leadingRows = leadingRows,
                    itemCount = items.size,
                    playingIndex = items.indexOfFirst { it.isPlaying },
                    continuesBelow = canLoadMore,
                    stationX = metrics.stationCenter,
                    colors = routeColors)
        }
        when {
            items == null && !loadTimedOut -> QueueLoadingIndicator(accentColor)
            items.isNullOrEmpty() -> QueueEmptyMessage()
            else -> ScalingLazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    // Extra top padding leaves room for the curved clock at the top bezel.
                    contentPadding = PaddingValues(
                            start = QUEUE_LIST_SIDE_PADDING,
                            end = QUEUE_LIST_SIDE_PADDING,
                            top = 36.dp,
                            bottom = 26.dp),
                    verticalArrangement = Arrangement.spacedBy(appearance.spacingDp.dp),
                    // Same fix as MenuScreen: the old overload (no rotary param) is a deprecated
                    // compatibility shim whose legacy touch path is why swipes weren't scrolling.
                    rotaryScrollableBehavior = RotaryScrollableDefaults.behavior(scrollableState = listState)
            ) {
                item {
                    QueueHeader(nowPlayingTitle, nowPlayingArtist, queuePosition, accentColor,
                            marquee = listAtRest)
                }
                // Sits between the now-playing header and the rows, which is where a section
                // caption belongs and, more to the point, is unmissable before the first row is
                // read as "what plays next".
                if (isHistoryFallback) {
                    item(key = HISTORY_CAPTION_KEY) { QueueHistoryCaption(accentColor) }
                }
                itemsIndexed(items, key = { _, item -> item.entryId }) { index, item ->
                    QueueRow(
                            item,
                            accentColor,
                            secondaryAccentColor,
                            tertiaryAccentColor,
                            onItemClick,
                            onItemLongClick,
                            marquee = listAtRest,
                            style = style,
                            appearance = appearance,
                            metrics = metrics,
                            offset = offsets?.getOrNull(index),
                            dimPlayed = dimPlayed,
                            routeColors = routeColors,
                            routeAbove = index > 0,
                            routeBelow = index < items.size - 1 || canLoadMore
                    )
                }
                if (canLoadMore) {
                    item(key = LOAD_MORE_KEY) {
                        LoadMoreRow(
                                accentColor = accentColor,
                                loading = loadingMore,
                                style = style,
                                appearance = appearance,
                                metrics = metrics,
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
private fun QueueHeader(
        title: String?,
        artist: String?,
        position: QueuePosition?,
        accentColor: Color,
        marquee: Boolean
) {
    Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!title.isNullOrBlank()) {
            var roomWidth by remember { mutableIntStateOf(0) }
            var lineWidth by remember(title) { mutableIntStateOf(0) }
            Text(
                    text = title,
                    color = Color.White,
                    style = LocalWatchTrackTextStyles.current.title,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // basicMarquee measures the text at its natural width. Compare that with
                    // the viewport outside it so a stationary title keeps every glyph opaque.
                    onTextLayout = { lineWidth = it.size.width },
                    modifier = Modifier.fillMaxWidth().onSizeChanged { roomWidth = it.width }.then(
                            if (marquee) {
                                Modifier.marqueeFade(enabled = roomWidth in 1 until lineWidth)
                                        .basicMarquee(iterations = Int.MAX_VALUE)
                            } else {
                                Modifier
                            }
                    )
            )
        }
        if (!artist.isNullOrBlank()) {
            Text(
                    text = artist,
                    color = Color.White.copy(alpha = 0.6f),
                    style = LocalWatchTrackTextStyles.current.artist,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
            )
        }
        if (position != null) {
            // The one number a queue cannot show by scrolling: where in the whole of it this
            // track sits. In the album colour, lifted for a black ground, so it reads as part of
            // the header rather than as a row.
            Text(
                    text = stringResource(R.string.queue_position, position.position, position.size),
                    color = Color(WatchTheme.accentForText(accentColor.toArgb())),
                    style = LocalWatchTrackTextStyles.current.artist,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun QueueRow(
        item: QueueItemUi,
        accentColor: Color,
        secondaryAccentColor: Color,
        tertiaryAccentColor: Color,
        onItemClick: (String) -> Unit,
        onItemLongClick: (QueueItemUi) -> Unit,
        marquee: Boolean,
        style: QueueStyle,
        appearance: QueueRowAppearance,
        metrics: QueueRowMetrics,
        offset: Int? = null,
        dimPlayed: Boolean = false,
        /** The Route style's colours, or null for every other style. */
        routeColors: QueueRouteColors? = null,
        /** Whether the route goes on above and below this entry - false at the queue's ends. */
        routeAbove: Boolean = false,
        routeBelow: Boolean = false
) {
    // The lightened accent / tonal surfaces keep black or accent text readable regardless of the
    // album's hue - see QueueRowSkins for how each style paints the row.
    val skin = remember(appearance.style, item.isPlaying, accentColor, secondaryAccentColor,
            tertiaryAccentColor) {
        QueueRowSkins.resolve(
                appearance.style.token,
                item.isPlaying,
                accentColor.toArgb(),
                secondaryAccentColor.toArgb(),
                tertiaryAccentColor.toArgb())
    }
    val background = remember(skin) { skin.fill.toBrush() }
    val onRow = Color(skin.onColor)

    // background(shape) draws an anti-aliased outline directly; the previous
    // clip(RoundedCornerShape) forced an offscreen saveLayer PER ROW on every scroll frame
    // (hardware canvas can't anti-alias a rounded clip without one), which was the main source
    // of the scroll stutter. The row's content is inside padding and ellipsized, so it never
    // needs the clip - only the tap ripple loses its rounded corners, which is imperceptible next
    // to smooth scrolling.
    val shape = remember(appearance.outline) { QueueOutlineShape(appearance.outline) }
    val coverClip = remember(appearance.coverCorners) { CoverCornersShape(appearance.coverCorners) }
    val border = skin.border
    val nowPlayingDescription = stringResource(R.string.queue_now_playing)
    // Cover style: the entry's own art fills the pill. Null for every other style, and for a
    // cover-style row whose entry has no artwork - which then renders as a plain Glass pill.
    val coverArt = if (style.isCover) item.artwork else null
    val coverImage = rememberCoverImage(coverArt, style)
    val showsThumbnail = item.artwork != null && (coverImage == null || style.coverKeepsThumbnail)
    // A Route row keeps its station's place even without a cover, so every station lines up on
    // the one line; the node it draws there instead is the stop without a picture.
    val leadsWithStation = routeColors != null
    val shortcutLink = QueueShortcutLink.candidate(item.mediaUri)
    val addShortcutLabel = stringResource(R.string.queue_add_shortcut)
    val rowAlpha = QueueShape.rowAlpha(offset ?: 0, dimPlayed)
    Row(
            modifier = Modifier
                    .fillMaxWidth()
                    .then(if (rowAlpha < 1f) Modifier.alpha(rowAlpha) else Modifier)
                    .background(background, shape)
                    .then(
                            if (coverImage != null) {
                                // Only this style pays for the clip (and its per-row saveLayer,
                                // see the note above) - it is the one treatment that genuinely
                                // needs the artwork clipped to the pill.
                                Modifier.coverFill(
                                        coverImage, shape, coverScrimFor(style, accentColor))
                            } else {
                                Modifier
                            }
                    )
                    .then(
                            if (border != null) {
                                Modifier.border(border.widthDp.dp, Color(border.color), shape)
                            } else {
                                Modifier
                            })
                    .then(
                            // The playing station's capsule is opaque, so the line the list draws
                            // under the rows would break off at its edges; the row carries its own
                            // stretch of it through the capsule to its station.
                            if (routeColors != null && item.isPlaying) {
                                Modifier.routeThroughRow(
                                        metrics.stationCenter, routeColors, routeAbove, routeBelow)
                            } else {
                                Modifier
                            })
                    .combinedClickable(
                            onClick = { onItemClick(item.entryId) },
                            onLongClickLabel = if (shortcutLink != null && item.title.isNotBlank()) {
                                addShortcutLabel
                            } else {
                                null
                            },
                            onLongClick = if (shortcutLink != null && item.title.isNotBlank()) {
                                { onItemLongClick(item) }
                            } else {
                                null
                            })
                    .semantics {
                        selected = item.isPlaying
                        if (item.isPlaying) stateDescription = nowPlayingDescription
                    }
                    .queueSkinMarks(skin, appearance.outline)
                    // Height is derived from the style's padding around the artwork keyline rather
                    // than left to the content. A row whose track has no artist collapses to one
                    // line, so content-driven heights made the list ragged next to the quick
                    // panel's pills; each style still keeps its own rhythm via its padding.
                    .height(metrics.height)
                    // A row leading with a cover insets it evenly top, bottom and left, so the
                    // artwork sits in a frame instead of being pushed inwards by a text keyline it
                    // does not need. Text-only rows keep the wider inset the outline asks for.
                    .padding(
                            start = if (showsThumbnail || leadsWithStation) {
                                metrics.coverStart
                            } else {
                                metrics.textInset
                            },
                            end = metrics.textInset),
            verticalAlignment = Alignment.CenterVertically
    ) {
        if (routeColors != null) {
            RouteStation(
                    artwork = item.artwork,
                    node = offset?.let(QueueShape::node),
                    size = metrics.artworkSize,
                    clip = coverClip,
                    colors = routeColors)
            Spacer(Modifier.width(10.dp))
        } else {
            // The cover style already shows the art full-bleed behind the text; a second thumbnail
            // of the same image would just crowd the row.
            item.artwork?.takeIf { showsThumbnail }?.let { bitmap ->
                val image = remember(bitmap) { bitmap.asImageBitmap() }
                Image(
                        bitmap = image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                                .size(metrics.artworkSize)
                                .clip(coverClip)
                )
                Spacer(Modifier.width(10.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            var roomWidth by remember { mutableIntStateOf(0) }
            var lineWidth by remember(item.title) { mutableIntStateOf(0) }
            Text(
                    text = item.title,
                    color = onRow,
                    style = LocalWatchTrackTextStyles.current.title,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // Only the now-playing row scrolls its long title, and only while the list
                    // itself is at rest ([marquee]). Marquee on EVERY row (or during a scroll)
                    // re-lays the list out each frame and made scrolling visibly stutter.
                    onTextLayout = { lineWidth = it.size.width },
                    modifier = Modifier.onSizeChanged { roomWidth = it.width }.then(
                            if (item.isPlaying && marquee) {
                                Modifier.marqueeFade(enabled = roomWidth in 1 until lineWidth)
                                        .basicMarquee(iterations = Int.MAX_VALUE)
                            } else {
                                Modifier
                            })
            )
            if (!item.subtitle.isNullOrBlank()) {
                Text(
                        text = item.subtitle,
                        color = onRow.copy(alpha = SUBTITLE_ALPHA),
                        style = LocalWatchTrackTextStyles.current.artist,
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

/** The playing row's own stretch of the route: down from its top edge to its station, then on
 *  below it, in the colours the line has on either side of "you are here". */
private fun Modifier.routeThroughRow(
        stationCenter: Dp,
        colors: QueueRouteColors,
        above: Boolean,
        below: Boolean
): Modifier = drawBehind {
    val x = stationCenter.toPx()
    val middle = size.height / 2f
    val stroke = QueueShape.ROUTE_LINE_DP.dp.toPx()
    if (above) drawLine(Color(colors.travelled), Offset(x, 0f), Offset(x, middle), stroke)
    if (below) drawLine(Color(colors.ahead), Offset(x, middle), Offset(x, size.height), stroke)
}

/**
 * One stop on the Route style's line: the entry's cover, cut as the queue's covers are and ringed
 * in the colour of the stretch it sits on - lit, with a glow, for the one playing - or, for an entry
 * with no cover, a node in the same place: a lit dot now, a small dot behind, an open ring ahead.
 * [node] is null when nothing is playing (the recently-played fallback), which draws them all as
 * stops still to come rather than claiming any of them is current.
 */
@Composable
private fun RouteStation(
        artwork: Bitmap?,
        node: QueueShape.Node?,
        size: Dp,
        clip: Shape,
        colors: QueueRouteColors
) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        if (artwork != null) {
            val image = remember(artwork) { artwork.asImageBitmap() }
            val now = node == QueueShape.Node.NOW
            val ring = Color(when (node) {
                QueueShape.Node.NOW -> colors.lit
                QueueShape.Node.PAST -> colors.travelled
                else -> colors.ahead
            })
            Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                            .fillMaxSize()
                            .then(
                                    if (now) {
                                        Modifier.drawBehind {
                                            val grow = 4.dp.toPx()
                                            val halo = clip.createOutline(
                                                    Size(this.size.width + grow * 2,
                                                            this.size.height + grow * 2),
                                                    layoutDirection,
                                                    this)
                                            translate(-grow, -grow) {
                                                drawOutline(halo, Color(colors.halo))
                                            }
                                        }
                                    } else {
                                        Modifier
                                    })
                            .clip(clip)
                            .border(if (now) 2.5.dp else 1.5.dp, ring, clip))
        } else {
            Canvas(Modifier.fillMaxSize()) { drawRouteNode(node, colors) }
        }
    }
}

private fun DrawScope.drawRouteNode(node: QueueShape.Node?, colors: QueueRouteColors) {
    val radius = QueueShape.ROUTE_NODE_DP.dp.toPx()
    when (node) {
        QueueShape.Node.NOW -> {
            drawCircle(Color(colors.halo), radius + 4.dp.toPx(), center)
            drawCircle(Color(colors.lit), radius, center)
        }
        QueueShape.Node.PAST ->
            drawCircle(Color(colors.past), radius * QueueShape.nodeScale(QueueShape.Node.PAST), center)
        else -> {
            val ring = radius * QueueShape.nodeScale(QueueShape.Node.UPCOMING)
            // A dark core keeps the ring legible where the line passes behind it.
            drawCircle(Color(colors.upcomingCore), ring + 1.dp.toPx(), center)
            drawCircle(Color(colors.upcomingRing), ring, center, style = Stroke(1.6.dp.toPx()))
        }
    }
}

/**
 * The Route style's line: one stroke through the middle of every visible station, in the album's
 * colour above the playing entry and quiet white below it, running on and fading out past the
 * stations on screen where the queue goes on.
 *
 * Drawn once under the whole list from where its rows really are - see [QueueShape.routeSegments]
 * - rather than by each row: the list scales its rows towards the edges of the round screen, so a
 * line drawn inside each row would step sideways from one to the next. Joined through the covers
 * themselves, it bends with that scaling instead. The timeline layout this replaces ran a fixed arc
 * along the bezel, near the rows rather than through them, and had to shrink every row to make room
 * for it.
 */
@Composable
private fun QueueRouteLine(
        listState: ScalingLazyListState,
        leadingRows: Int,
        itemCount: Int,
        playingIndex: Int,
        continuesBelow: Boolean,
        stationX: Dp,
        colors: QueueRouteColors
) {
    Canvas(Modifier.fillMaxSize()) {
        val info = listState.layoutInfo
        val width = info.viewportSize.width.toFloat()
        val height = info.viewportSize.height.toFloat()
        if (width <= 0f || height <= 0f) return@Canvas
        val itemWidth = width - QUEUE_LIST_SIDE_PADDING.toPx() * 2
        val stationInRow = stationX.toPx()
        val stations = info.visibleItemsInfo.mapNotNull { row ->
            val index = row.index - leadingRows
            if (index !in 0 until itemCount) {
                null
            } else {
                QueueShape.station(
                        index, width, height, itemWidth, stationInRow,
                        row.offset.toFloat(), row.scale, row.alpha)
            }
        }
        val stroke = QueueShape.ROUTE_LINE_DP.dp.toPx()
        QueueShape.routeSegments(
                stations, itemCount, playingIndex, continuesBelow,
                QueueShape.ROUTE_TAIL_DP.dp.toPx()
        ).forEach { segment ->
            val base = Color(if (segment.travelled) colors.travelled else colors.ahead)
            val color = base.copy(alpha = base.alpha * segment.alpha)
            val start = Offset(segment.fromX, segment.fromY)
            val end = Offset(segment.toX, segment.toY)
            if (segment.fadesAtStart || segment.fadesAtEnd) {
                val clear = color.copy(alpha = 0f)
                drawLine(
                        brush = Brush.linearGradient(
                                listOf(
                                        if (segment.fadesAtStart) clear else color,
                                        if (segment.fadesAtEnd) clear else color),
                                start, end),
                        start = start,
                        end = end,
                        strokeWidth = stroke,
                        cap = StrokeCap.Round)
            } else {
                drawLine(color, start, end, stroke, StrokeCap.Round)
            }
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
 * Wears the current style's own idle skin and outline rather than a look of its own, so it reads as
 * part of the list instead of as a system affordance dropped on top of it. It replaces its label
 * with a spinner while the request is out because the phone answers by replacing the whole list -
 * there is nothing incremental to watch, and without the spinner a slow Bluetooth round trip looks
 * like a dead tap.
 */
@Composable
private fun LoadMoreRow(
        accentColor: Color,
        loading: Boolean,
        style: QueueStyle,
        appearance: QueueRowAppearance,
        metrics: QueueRowMetrics,
        onClick: () -> Unit
) {
    // Never the cover treatment: this row has no artwork of its own, and the cover styles fall back
    // to the Glass pill in exactly that case anyway.
    val token = if (style.isCover) QueueStyles.DEFAULT_TOKEN else appearance.style.token
    val skin = remember(token, accentColor) {
        val accent = accentColor.toArgb()
        QueueRowSkins.resolve(token, isPlaying = false, accent, accent, accent)
    }
    val background = remember(skin) { skin.fill.toBrush() }
    val shape = remember(appearance.outline) { QueueOutlineShape(appearance.outline) }
    val border = skin.border
    Box(
            modifier = Modifier
                    .fillMaxWidth()
                    .background(background, shape)
                    .then(
                            if (border != null) {
                                Modifier.border(border.widthDp.dp, Color(border.color), shape)
                            } else {
                                Modifier
                            }
                    )
                    .queueSkinMarks(skin, appearance.outline)
                    // Taps are swallowed while a request is already in flight, so an impatient
                    // double tap cannot queue up two pages.
                    .clickable(enabled = !loading) { onClick() }
                    .height(metrics.height)
                    .padding(horizontal = metrics.textInset),
            contentAlignment = Alignment.Center
    ) {
        val onRow = Color(skin.onColor)
        if (loading) {
            // The same pulsing bars the playing row draws - see EqualizerBars. At row height
            // *inside* the list, where a circular indicator is the one shape in the column that is
            // not part of the queue's own vocabulary.
            EqualizerBars(onRow)
        } else {
            Text(
                    text = stringResource(R.string.queue_load_more),
                    color = onRow,
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

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 220, heightDp = 220)
@Composable
private fun QueueScreenRoutePreview() {
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
                onDismiss = {},
                style = QueueStyle.ROUTE
        )
    }
}
