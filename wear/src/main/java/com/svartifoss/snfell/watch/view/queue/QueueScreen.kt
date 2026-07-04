package com.svartifoss.snfell.watch.view.queue

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        val isPlaying: Boolean
)

// Idle rows are near-black for an OLED-dark look; the now-playing row uses the full album accent.
private val IDLE_PILL_COLOR = Color(WatchTheme.SURFACE_DARK)
private const val SUBTITLE_ALPHA = 0.65f
// How long the loading spinner may wait for the phone's queue response before giving up and
// showing the empty message instead (e.g. phone out of range never answers at all).
private const val QUEUE_LOAD_TIMEOUT_MS = 6000L

/** The app-wide Google Sans typeface, so the queue matches the rest of the watch UI. */
private val GoogleSans = GoogleSansFamily

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
        nowPlayingTitle: String?,
        nowPlayingArtist: String?,
        onItemClick: (entryId: String) -> Unit,
        onDismiss: () -> Unit
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
                QueueList(items, accentColor, nowPlayingTitle, nowPlayingArtist, onItemClick)
            }
        }
    }
}

@Composable
private fun QueueList(
        items: List<QueueItemUi>?,
        accentColor: Color,
        nowPlayingTitle: String?,
        nowPlayingArtist: String?,
        onItemClick: (String) -> Unit
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
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    // Same fix as MenuScreen: the old overload (no rotary param) is a deprecated
                    // compatibility shim whose legacy touch path is why swipes weren't scrolling.
                    rotaryScrollableBehavior = RotaryScrollableDefaults.behavior(scrollableState = listState)
            ) {
                item { QueueHeader(nowPlayingTitle, nowPlayingArtist, animate = !isScrolling) }
                items(items, key = { it.entryId }) { item ->
                    QueueRow(item, accentColor, onItemClick, animate = !isScrolling)
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
        onItemClick: (String) -> Unit,
        animate: Boolean
) {
    // Now-playing text/glyph is always black; the accent is lightened so black always reads,
    // turning dark albums (e.g. purple) into a dark-theme-friendly pastel of the same hue.
    val pillColor = if (item.isPlaying) lightenForBlackText(accentColor) else IDLE_PILL_COLOR
    val onPill = if (item.isPlaying) Color.Black else Color.White

    // background(shape) draws an anti-aliased rounded rect directly; the previous
    // clip(RoundedCornerShape) forced an offscreen saveLayer PER ROW on every scroll frame
    // (hardware canvas can't anti-alias a rounded clip without one), which was the main source
    // of the scroll stutter. The row's content is inside padding and ellipsized, so it never
    // needs the rounded clip - only the tap ripple loses its rounded corners, which is
    // imperceptible next to smooth scrolling.
    Row(
            modifier = Modifier
                    .fillMaxWidth()
                    .background(pillColor, RoundedCornerShape(26.dp))
                    .clickable { onItemClick(item.entryId) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                    text = item.title,
                    color = onPill,
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
                        color = onPill.copy(alpha = SUBTITLE_ALPHA),
                        fontFamily = GoogleSans,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (item.isPlaying) {
            Spacer(Modifier.width(8.dp))
            NowPlayingBars(color = onPill, animate = animate)
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
                nowPlayingTitle = "WINGS",
                nowPlayingArtist = "Lieless, PRATEIN & Pimpie",
                onItemClick = {},
                onDismiss = {}
        )
    }
}
