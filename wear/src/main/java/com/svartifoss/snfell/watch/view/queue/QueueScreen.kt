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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
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
import com.svartifoss.snfell.watch.theme.GoogleSansFamily
import com.svartifoss.snfell.watch.theme.WatchTheme
import com.svartifoss.snfell.watch.view.compose.CurvedClock
import com.svartifoss.snfell.watch.view.compose.CurvedScrollIndicator
import com.svartifoss.snfell.watch.view.compose.LoadingSpinner
import kotlinx.coroutines.delay

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

/** The app-wide Google Sans typeface, so the queue matches the rest of the watch UI. */
private val GoogleSans = GoogleSansFamily

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
    FROST;

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
            else -> GLASS
        }
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

/** Per-style skin for one queue row. */
private class QueueRowSkin(
        val background: Brush,
        val onColor: Color,
        val corner: Dp,
        val verticalPadding: Dp,
        /** Left accent bar drawn to mark the now-playing row, or null for none. */
        val keyline: Color?,
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
    return when (style) {
        QueueStyle.GLASS -> QueueRowSkin(
                background = SolidColor(if (isPlaying) lighten else IDLE_PILL_COLOR),
                onColor = if (isPlaying) Color.Black else Color.White,
                corner = 26.dp, verticalPadding = 12.dp, keyline = null
        )
        QueueStyle.MINIMAL -> QueueRowSkin(
                background = SolidColor(Color.Transparent),
                onColor = if (isPlaying) accent else Color.White,
                corner = 0.dp, verticalPadding = 10.dp,
                keyline = if (isPlaying) accent else null
        )
        QueueStyle.MATERIAL -> QueueRowSkin(
                background = SolidColor(MATERIAL_CARD_COLOR),
                onColor = if (isPlaying) accent else Color.White,
                corner = 12.dp, verticalPadding = 14.dp,
                keyline = if (isPlaying) accent else null
        )
        QueueStyle.TONAL -> QueueRowSkin(
                background = SolidColor(if (isPlaying) lighten else tonalColor(accent, 0.22f)),
                onColor = if (isPlaying) Color.Black else Color.White,
                corner = 28.dp, verticalPadding = 16.dp, keyline = null
        )
        QueueStyle.NEON -> QueueRowSkin(
                background = SolidColor(Color.Transparent),
                onColor = if (isPlaying) accent else Color.White,
                corner = 18.dp, verticalPadding = 12.dp, keyline = null,
                border = (if (isPlaying) 2.dp else 1.dp) to (if (isPlaying) accent else accent.copy(alpha = 0.5f))
        )
        QueueStyle.LIGHT -> QueueRowSkin(
                background = SolidColor(if (isPlaying) lighten else LIGHT_SURFACE),
                onColor = if (isPlaying) Color.Black else LIGHT_ON,
                corner = 20.dp, verticalPadding = 13.dp, keyline = null
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
                corner = 22.dp, verticalPadding = 14.dp, keyline = null
        )
        QueueStyle.MONO -> QueueRowSkin(
                background = SolidColor(if (isPlaying) MONO_ACTIVE else MONO_IDLE),
                onColor = if (isPlaying) Color.Black else Color.White,
                corner = 14.dp, verticalPadding = 13.dp, keyline = null
        )
        QueueStyle.OUTLINE -> QueueRowSkin(
                background = SolidColor(Color.Transparent),
                onColor = if (isPlaying) accent else Color.White,
                corner = 16.dp, verticalPadding = 12.dp, keyline = null,
                border = (if (isPlaying) 3.dp else 2.5.dp) to (if (isPlaying) accent else Color.White)
        )
        QueueStyle.DUOTONE -> QueueRowSkin(
                background = SolidColor(
                        if (isPlaying) lighten else tonalColor(secondaryAccent, 0.24f)),
                onColor = if (isPlaying) Color.Black else Color.White,
                corner = 22.dp, verticalPadding = 14.dp, keyline = null
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
                corner = 22.dp,
                verticalPadding = 14.dp,
                keyline = null,
                border = 1.dp to Color.White.copy(alpha = .38f)
        )
        QueueStyle.CONTRAST -> QueueRowSkin(
                background = SolidColor(if (isPlaying) Color.White else Color.Black),
                onColor = if (isPlaying) Color.Black else Color.White,
                corner = 8.dp, verticalPadding = 13.dp, keyline = null,
                border = if (isPlaying) null else 2.dp to Color.White
        )
        QueueStyle.TERMINAL -> QueueRowSkin(
                background = SolidColor(Color.Transparent),
                onColor = TERMINAL_GREEN,
                corner = 0.dp, verticalPadding = 11.dp, keyline = null,
                border = (if (isPlaying) 2.dp else 1.dp) to TERMINAL_GREEN
        )
        QueueStyle.FROST -> QueueRowSkin(
                background = SolidColor(if (isPlaying) accent.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.16f)),
                onColor = Color.White,
                corner = 24.dp, verticalPadding = 13.dp, keyline = null
        )
    }
}

/** Row spacing per style - tighter for the flat minimal list, roomier for the bold card styles. */
private fun queueRowSpacing(style: QueueStyle): Dp = when (style) {
    QueueStyle.MINIMAL, QueueStyle.TERMINAL -> 2.dp
    QueueStyle.MATERIAL, QueueStyle.TONAL, QueueStyle.LIGHT, QueueStyle.GRADIENT,
    QueueStyle.DUOTONE, QueueStyle.PRISM, QueueStyle.FROST -> 8.dp
    else -> 6.dp
}

/** A dark, accent-tinted surface for the tonal idle rows - keeps saturation in a readable band. */
private fun tonalColor(accent: Color, lightness: Float): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(accent.toArgb(), hsl)
    hsl[1] = hsl[1].coerceIn(0.25f, 0.60f)
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
        style: QueueStyle = QueueStyle.GLASS
) {
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
                        style
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
        style: QueueStyle
) {
    val listState = rememberScalingLazyListState()

    // While the list is actively scrolling, freeze the continuous animations (the now-playing
    // equalizer and the marquee titles). Those redraw/relayout every frame and were stealing the
    // scroll's frame budget on watch hardware; the flag flips only twice per gesture (start/stop),
    // so gating on it is far cheaper than letting them run through the scroll.
    val isScrolling by remember { derivedStateOf { listState.isScrollInProgress } }

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
                item { QueueHeader(nowPlayingTitle, nowPlayingArtist, animate = !isScrolling) }
                items(items, key = { it.entryId }) { item ->
                    QueueRow(
                            item,
                            accentColor,
                            secondaryAccentColor,
                            tertiaryAccentColor,
                            onItemClick,
                            animate = !isScrolling,
                            style = style
                    )
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
private fun QueueHeader(title: String?, artist: String?, animate: Boolean) {
    Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!title.isNullOrBlank()) {
            Text(
                    text = title,
                    color = Color.White,
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().then(
                            if (animate) Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                            else Modifier
                    )
            )
        }
        if (!artist.isNullOrBlank()) {
            Text(
                    text = artist,
                    color = Color.White.copy(alpha = 0.6f),
                    fontFamily = GoogleSans,
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
        animate: Boolean,
        style: QueueStyle
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
    val keyline = skin.keyline
    val shape = RoundedCornerShape(skin.corner)
    val border = skin.border
    val nowPlayingDescription = stringResource(R.string.queue_now_playing)
    Row(
            modifier = Modifier
                    .fillMaxWidth()
                    .background(skin.background, shape)
                    .then(if (border != null) Modifier.border(border.first, border.second, shape) else Modifier)
                    .clickable { onItemClick(item.entryId) }
                    .semantics {
                        selected = item.isPlaying
                        if (item.isPlaying) stateDescription = nowPlayingDescription
                    }
                    .then(
                            if (keyline != null) {
                                // A short rounded accent bar hugging the left edge marks the
                                // now-playing row on the flat minimal/material styles (which have
                                // no coloured pill to signal it).
                                Modifier.drawBehind {
                                    drawRoundRect(
                                            color = keyline,
                                            topLeft = Offset(0f, size.height * 0.18f),
                                            size = Size(3.dp.toPx(), size.height * 0.64f),
                                            cornerRadius = CornerRadius(2.dp.toPx())
                                    )
                                }
                            } else {
                                Modifier
                            }
                    )
                    .padding(horizontal = 16.dp, vertical = skin.verticalPadding),
            verticalAlignment = Alignment.CenterVertically
    ) {
        item.artwork?.let { bitmap ->
            val image = remember(bitmap) { bitmap.asImageBitmap() }
            Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                            // The row owns its height; artwork fits inside the existing text
                            // keyline instead of expanding every pill that has a cover.
                            .size(30.dp)
                            .clip(RoundedCornerShape(50))
            )
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                    text = item.title,
                    color = onRow,
                    fontFamily = GoogleSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // Only the now-playing row scrolls its long title, and only while the list
                    // itself is at rest ([animate]). Marquee on EVERY row (or during a scroll)
                    // re-lays the list out each frame and made scrolling visibly stutter.
                    modifier = if (item.isPlaying && animate) {
                        Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                    } else {
                        Modifier
                    }
            )
            if (!item.subtitle.isNullOrBlank()) {
                Text(
                        text = item.subtitle,
                        color = onRow.copy(alpha = SUBTITLE_ALPHA),
                        fontFamily = GoogleSans,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (item.isPlaying) {
            Spacer(Modifier.width(8.dp))
            NowPlayingBars(color = onRow, animate = animate)
        }
    }
}

/** Small indeterminate arc spinner shown while the queue request is still in flight. */
@Composable
private fun QueueLoadingIndicator(accentColor: Color) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LoadingSpinner(accentColor)
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
                fontFamily = GoogleSans,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
        )
    }
}

/** Frozen bar heights shown while the list is scrolling - a readable mid-pose of the animation. */
private val STATIC_BAR_HEIGHTS = listOf(0.5f, 0.85f, 0.65f)

/**
 * Three-bar "now playing" equalizer. When [animate] is true it pulses via an infinite transition;
 * while the list is scrolling ([animate] false) the transition isn't composed at all, so it adds
 * zero per-frame work to the scroll. Drawn in a single Canvas so animation only invalidates the
 * *draw* phase - the previous Box-per-bar version animated `fillMaxHeight`, which re-ran layout for
 * the whole row on every animation frame and contributed to the queue stuttering while scrolling.
 */
@Composable
private fun NowPlayingBars(color: Color, animate: Boolean) {
    if (animate) {
        val transition = rememberInfiniteTransition(label = "equalizer")
        val h1 by transition.animateFloat(
                initialValue = 0.30f, targetValue = 1.0f,
                animationSpec = infiniteRepeatable(tween(480), RepeatMode.Reverse), label = "bar1"
        )
        val h2 by transition.animateFloat(
                initialValue = 1.0f, targetValue = 0.40f,
                animationSpec = infiniteRepeatable(tween(360), RepeatMode.Reverse), label = "bar2"
        )
        val h3 by transition.animateFloat(
                initialValue = 0.55f, targetValue = 0.90f,
                animationSpec = infiniteRepeatable(tween(560), RepeatMode.Reverse), label = "bar3"
        )
        // Reading the animated values inside the draw lambda (not composition) keeps each frame a
        // redraw-only invalidation.
        EqualizerCanvas(color) { listOf(h1, h2, h3) }
    } else {
        EqualizerCanvas(color) { STATIC_BAR_HEIGHTS }
    }
}

@Composable
private fun EqualizerCanvas(color: Color, fractions: () -> List<Float>) {
    val barWidth = 3.dp
    val barGap = 2.dp
    Canvas(Modifier.size(width = barWidth * 3 + barGap * 2, height = 16.dp)) {
        val widthPx = barWidth.toPx()
        val gapPx = barGap.toPx()
        val corner = CornerRadius(2.dp.toPx())
        fractions().forEachIndexed { index, fraction ->
            val barHeight = size.height * fraction
            drawRoundRect(
                    color = color,
                    topLeft = Offset(index * (widthPx + gapPx), size.height - barHeight),
                    size = Size(widthPx, barHeight),
                    cornerRadius = corner
            )
        }
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
