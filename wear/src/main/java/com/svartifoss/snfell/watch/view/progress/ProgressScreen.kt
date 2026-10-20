package com.svartifoss.snfell.watch.view.progress

import android.graphics.Bitmap
import android.graphics.Typeface
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.svartifoss.snfell.common.PlaybackSpeeds
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.ProgressShape
import com.svartifoss.snfell.common.R as commonR
import com.svartifoss.snfell.common.TrackTimeFormat
import com.svartifoss.snfell.watch.view.CircularProgressSeekBar
import com.svartifoss.snfell.watch.view.OverlayProgressMeter
import com.svartifoss.snfell.watch.view.ProgressRingLayout
import com.svartifoss.snfell.watch.view.RingStyle
import com.svartifoss.snfell.watch.view.panel.PanelAppearance
import com.svartifoss.snfell.watch.view.panel.PanelBackdrop
import com.svartifoss.snfell.watch.view.panel.PanelCenterPlayPauseTarget
import com.svartifoss.snfell.watch.view.panel.PanelReadout
import com.svartifoss.snfell.watch.view.panel.PanelReadoutText
import com.svartifoss.snfell.watch.view.panel.PanelScaffold
import java.util.Locale

/** Seek layouts drawn by [OverlayProgressMeter] instead of by the edge ring - the same split
 *  `MainActivity.applySeekPanelLayout` makes, and from the same preference. */
private val EDGE_SEEK_LAYOUTS = setOf("edge", "edge_thin", "edge_thick")

/**
 * The dedicated progress screen: the user's *configured* seek panel plus focused relative-seek
 * controls. It repeats play/pause only as the primary player's familiar invisible centre tap.
 *
 * Built the same way as [com.svartifoss.snfell.watch.view.volume.VolumeScreen] and for the same
 * reasons - see its doc. The ring is the real [CircularProgressSeekBar] in the chosen ring style
 * and layout, over the Shared panel appearance backdrop, in the album's colours; the earlier
 * version drew a fixed-accent circle of its own in the middle of a black screen, which both
 * ignored every Panels setting and sat exactly where a swipe back lands.
 */
@Composable
fun ProgressScreen(
        state: ProgressUiState,
        /** What the centre readout shows: the position being chosen, if one is. */
        livePositionMs: Long,
        /** What the ring is fed: always the real playback position, because its drag marker draws
         *  from it. See `ProgressActivity` for why the two differ. */
        ringPositionMs: Long,
        appearance: PanelAppearance,
        backdrop: PanelBackdrop,
        albumArt: Bitmap?,
        showBackdrop: Boolean = true,
        screenFace: String,
        themeAccentColor: Int,
        uiTypeface: Typeface?,
        onSeekPreview: (Float) -> Unit,
        onSeekFinished: (Float) -> Unit,
        onSeekCancelled: () -> Unit,
        /** True while the drag sits in the ring's cancel zone. */
        cancelArmed: Boolean,
        onCancelArmedChanged: (Boolean) -> Unit,
        onTogglePlayPause: () -> Unit,
        onSkipBy: (Long) -> Unit,
        onCycleSpeed: () -> Unit,
        onDismiss: () -> Unit,
        /** The speed to show: the one a press just chose, else the reported one. */
        shownSpeed: Float = state.speed,
        /** Whether the fine speed control is open over the screen - see [FineSpeedPanel]. */
        fineSpeedOpen: Boolean = false,
        onOpenFineSpeed: () -> Unit = {},
        onCloseFineSpeed: () -> Unit = {},
        onStepSpeed: (Int) -> Unit = {},
        onResetSpeed: () -> Unit = {}
) {
    PanelScaffold(appearance, backdrop, albumArt, showBackdrop, onDismiss) {
        val configuration = LocalConfiguration.current
        val screenWidth = configuration.screenWidthDp.dp
        val screenHeight = configuration.screenHeightDp.dp

        fun fractionOf(positionMs: Long) = if (state.durationMs > 0L) {
            (positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
        } else 0f

        val fraction = fractionOf(livePositionMs)
        val ringFraction = fractionOf(ringPositionMs)
        val customMeter = appearance.seekLayout !in EDGE_SEEK_LAYOUTS

        AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { CircularProgressSeekBar(it) },
                update = { ring ->
                    ring.ringStyle = RingStyle.fromPref(appearance.progressStyle)
                    ring.ringLayout = ProgressRingLayout.fromPref(appearance.progressLayout)
                    ring.gradientEnabled = appearance.progressGradient
                    ring.weightScale = ProgressShape.weightScale(appearance.progressWeight)
                    ring.trackDurationMs = state.durationMs
                    // A wave ring travels while the music plays, here as on the player.
                    ring.playing = state.playing
                    ring.setPaletteColors(
                            appearance.triad.primary,
                            appearance.triad.secondary,
                            appearance.triad.tertiary)
                    ring.edgeStrokeScale = when (appearance.seekLayout) {
                        "edge_thin" -> 0.6f
                        "edge_thick" -> 1.8f
                        else -> 1f
                    }
                    // Offered whatever the session claims: an ignored seek is a harmless no-op the
                    // clock's next check undoes, while withholding it on a bit that is routinely
                    // under-reported loses the control entirely - the rule the queue tap and the
                    // lyrics screen's tap-to-seek already follow.
                    ring.seekable = state.durationMs > 0L
                    ring.touchSeekingEnabled = state.durationMs > 0L
                    ring.onSeekPreview = onSeekPreview
                    ring.onSeekFinished = onSeekFinished
                    ring.onSeekCancelled = onSeekCancelled
                    ring.onCancelArmedChanged = onCancelArmedChanged
                    ring.progress = ringFraction
                    // The ring is the meter only for the edge layouts; the others hand the drawing
                    // to OverlayProgressMeter below, exactly as the transient overlay does.
                    ring.alpha = if (customMeter) 0f else 1f
                })

        if (customMeter) {
            AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = {
                        OverlayProgressMeter(it).apply {
                            // Display only - the ring underneath owns the drag.
                            isClickable = false
                            isFocusable = false
                            visibility = View.VISIBLE
                        }
                    },
                    update = { meter ->
                        meter.progress = fraction
                        meter.accentColor = appearance.triad.primary
                        meter.secondaryColor = appearance.triad.secondary
                        meter.mode = when (appearance.seekLayout) {
                            "segments" -> OverlayProgressMeter.Mode.SEGMENTS
                            "timeline_top" -> OverlayProgressMeter.Mode.TIMELINE_TOP
                            "timeline_bottom" -> OverlayProgressMeter.Mode.TIMELINE_BOTTOM
                            "segments_top" -> OverlayProgressMeter.Mode.SEGMENTS_TOP
                            "segments_bottom" -> OverlayProgressMeter.Mode.SEGMENTS_BOTTOM
                            "center_stack" -> OverlayProgressMeter.Mode.CENTER_STACK
                            "vertical_left" -> OverlayProgressMeter.Mode.VERTICAL_LEFT
                            "vertical_right" -> OverlayProgressMeter.Mode.VERTICAL_RIGHT
                            "dial" -> OverlayProgressMeter.Mode.DIAL
                            "twin" -> OverlayProgressMeter.Mode.TWIN
                            "wave_timeline" -> OverlayProgressMeter.Mode.WAVE
                            else -> OverlayProgressMeter.Mode.TIMELINE
                        }
                        meter.animating = state.playing
                    })
        }

        SpeedChip(
                speed = shownSpeed,
                appearance = appearance,
                modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = screenHeight * -SPEED_VERTICAL_OFFSET_FRACTION),
                onClick = onCycleSpeed,
                onLongClick = onOpenFineSpeed)
        // "split"/"stacked_pill" stack the target over the total; every other style is the single
        // position, matching applySeekOverlayStyle.
        val readout = when (appearance.readoutStyle) {
            "split", "stacked_pill" ->
                formatDuration(livePositionMs) + "\n" + formatDuration(state.durationMs)
            else -> formatDuration(livePositionMs)
        }
        // The readout gives way to a cancel glyph while the drag is inside the zone: leaving it up
        // would keep advertising a destination the release is about to discard. Same swap the
        // player makes - see `MainActivity.showSeekCancelAffordance`.
        Box(
                modifier = Modifier.align(Alignment.Center),
                contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(visible = !cancelArmed, enter = fadeIn(), exit = fadeOut()) {
                PanelReadoutText(
                        style = appearance.readoutStyle,
                        content = readout,
                        accentColor = appearance.triad.primary,
                        secondaryColor = appearance.triad.secondary,
                        themeAccentColor = themeAccentColor,
                        screenFace = screenFace,
                        typeface = uiTypeface)
            }
            AnimatedVisibility(
                    visible = cancelArmed,
                    enter = fadeIn() + scaleIn(initialScale = CANCEL_GLYPH_ENTER_SCALE),
                    exit = fadeOut() + scaleOut(targetScale = CANCEL_GLYPH_ENTER_SCALE)
            ) {
                Icon(
                        painter = painterResource(commonR.drawable.action_close),
                        contentDescription = stringResource(R.string.progress_screen_cancel_seek),
                        tint = Color(PanelReadout.liftedAccent(appearance.triad.tertiary)),
                        modifier = Modifier.size(CANCEL_GLYPH_SIZE))
            }
        }

        // Drawn after the readout so the otherwise passive TextView cannot swallow the centre tap.
        // It is intentionally invisible; the time itself remains the only centre affordance.
        PanelCenterPlayPauseTarget(onClick = onTogglePlayPause)

        // Track navigation stays on the primary player. Relative seeking stands vertically at the
        // two sides: rewind on the left, forward on the right. The 10-second action is deliberately
        // larger because it is the useful default between fine and coarse.
        SkipColumn(
                modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = screenWidth * SKIP_COLUMN_SIDE_INSET_FRACTION),
                appearance = appearance,
                fiveIcon = commonR.drawable.action_replay_5,
                tenIcon = commonR.drawable.action_replay_10,
                thirtyIcon = commonR.drawable.action_reverse_30_seconds,
                fiveDescription = stringResource(R.string.progress_screen_skip_back, 5),
                tenDescription = stringResource(R.string.progress_screen_skip_back, 10),
                thirtyDescription = stringResource(R.string.progress_screen_skip_back, 30),
                edgeButtonInwardOffset =
                        screenWidth * SKIP_EDGE_BUTTON_INWARD_FRACTION,
                onFive = { onSkipBy(-SKIP_FIVE_MS) },
                onTen = { onSkipBy(-SKIP_TEN_MS) },
                onThirty = { onSkipBy(-SKIP_THIRTY_MS) })

        SkipColumn(
                modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = screenWidth * SKIP_COLUMN_SIDE_INSET_FRACTION),
                appearance = appearance,
                fiveIcon = commonR.drawable.action_forward_5,
                tenIcon = commonR.drawable.action_forward_10,
                thirtyIcon = commonR.drawable.action_skip_30_seconds,
                fiveDescription = stringResource(R.string.progress_screen_skip_forward, 5),
                tenDescription = stringResource(R.string.progress_screen_skip_forward, 10),
                thirtyDescription = stringResource(R.string.progress_screen_skip_forward, 30),
                edgeButtonInwardOffset =
                        screenWidth * -SKIP_EDGE_BUTTON_INWARD_FRACTION,
                onFive = { onSkipBy(SKIP_FIVE_MS) },
                onTen = { onSkipBy(SKIP_TEN_MS) },
                onThirty = { onSkipBy(SKIP_THIRTY_MS) })

        // Last, so it sits over every control on the screen and takes their taps while open.
        AnimatedVisibility(
                visible = fineSpeedOpen,
                modifier = Modifier.fillMaxSize(),
                enter = fadeIn(),
                exit = fadeOut()
        ) {
            FineSpeedPanel(
                    speed = shownSpeed,
                    appearance = appearance,
                    onStep = onStepSpeed,
                    onReset = onResetSpeed,
                    onClose = onCloseFineSpeed)
        }
    }
}

/**
 * The time readout owns the physical centre. The symmetric side columns follow the natural
 * orientation of rewind/forward and keep their controls away from the top and bottom bezel.
 */
/** Centres the speed pill in the free space immediately above the centred time readout. */
private const val SPEED_VERTICAL_OFFSET_FRACTION = 0.20f
// Pulls each three-button group slightly toward the centre without entering the 64dp centre tap
// zone. A fixed gap keeps 5/10/30 equally close on small and large watches without touching.
private const val SKIP_COLUMN_SIDE_INSET_FRACTION = 0.045f
/** Top/bottom buttons sit where the round glass is narrower, so they step toward the centre. */
private const val SKIP_EDGE_BUTTON_INWARD_FRACTION = 0.045f

private const val SKIP_FIVE_MS = 5_000L
private const val SKIP_TEN_MS = 10_000L
private const val SKIP_THIRTY_MS = 30_000L

private val CANCEL_GLYPH_SIZE = 40.dp
private const val CANCEL_GLYPH_ENTER_SCALE = 0.7f
private val SKIP_BUTTON_SIZE = 36.dp
private val FEATURED_SKIP_BUTTON_SIZE = 48.dp
private val SKIP_BUTTON_GAP = 4.dp

/**
 * The speed pill. A tap steps the shared ladder ([PlaybackSpeeds.next]); a long press opens the
 * fine control, which is the only way to reach the tenths and the range above 2x - kept off the
 * tap so the one-press habit the quick panel's chip teaches still works here.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpeedChip(
        speed: Float,
        appearance: PanelAppearance,
        modifier: Modifier = Modifier,
        onClick: () -> Unit,
        onLongClick: () -> Unit
) {
    val fill = PanelReadout.tonalSurface(appearance.triad.primary, .34f)
    val description = stringResource(R.string.progress_screen_speed_description, PlaybackSpeeds.label(speed))
    Box(
            modifier = modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(fill))
                    .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                    .semantics { contentDescription = description }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
    ) {
        Text(
                text = PlaybackSpeeds.label(speed),
                color = Color(PanelReadout.contrastingIconColor(fill)),
                fontSize = 10.sp)
    }
}

/**
 * The fine speed control: the speed large in the middle, a step of a tenth on either side, and a
 * way back to 1x. The crown steps it too while it is open (see `ProgressActivity`), which is the
 * quicker of the two for a long move. A tap on the dimmed ground closes it.
 *
 * Over the screen rather than a screen of its own because it is a detail of this one: the ring
 * and the time stay visible underneath, and closing it lands exactly where it was opened.
 */
@Composable
private fun FineSpeedPanel(
        speed: Float,
        appearance: PanelAppearance,
        onStep: (Int) -> Unit,
        onReset: () -> Unit,
        onClose: () -> Unit
) {
    val fill = PanelReadout.tonalSurface(appearance.triad.primary, .34f)
    val onFill = Color(PanelReadout.contrastingIconColor(fill))
    val atNormal = kotlin.math.abs(speed - 1f) < 0.001f
    // A stem press closes the control, not the screen under it.
    BackHandler(onBack = onClose)
    Box(
            modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = FINE_SPEED_SCRIM_ALPHA))
                    .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClose),
            contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                    text = stringResource(R.string.quick_tool_speed),
                    color = Color.White.copy(alpha = .7f),
                    fontSize = 12.sp)
            Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(vertical = 6.dp)
            ) {
                FineSpeedButton(
                        glyph = "\u2212",
                        description = stringResource(R.string.progress_screen_speed_slower),
                        enabled = speed > PlaybackSpeeds.FINE_MIN + 0.001f,
                        fill = fill,
                        onFill = onFill,
                        onClick = { onStep(-1) })
                // A fixed width, so "1×" and "2.75×" leave the two buttons where the finger
                // already is: a row that re-centred on every step moved its own targets.
                Text(
                        text = PlaybackSpeeds.label(speed),
                        color = Color.White,
                        fontSize = 28.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier
                                .width(FINE_SPEED_VALUE_WIDTH)
                                .semantics { contentDescription = PlaybackSpeeds.label(speed) })
                FineSpeedButton(
                        glyph = "+",
                        description = stringResource(R.string.progress_screen_speed_faster),
                        enabled = speed < PlaybackSpeeds.FINE_MAX - 0.001f,
                        fill = fill,
                        onFill = onFill,
                        onClick = { onStep(1) })
            }
            Box(
                    modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(fill).copy(alpha = if (atNormal) .4f else 1f))
                            .clickable(enabled = !atNormal, onClick = onReset)
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
            ) {
                Text(
                        text = stringResource(R.string.progress_screen_speed_reset),
                        color = onFill.copy(alpha = if (atNormal) .6f else 1f),
                        fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun FineSpeedButton(
        glyph: String,
        description: String,
        enabled: Boolean,
        fill: Int,
        onFill: Color,
        onClick: () -> Unit
) {
    Box(
            modifier = Modifier
                    .size(FINE_SPEED_BUTTON_SIZE)
                    .clip(CircleShape)
                    .background(Color(fill).copy(alpha = if (enabled) 1f else .4f))
                    .clickable(enabled = enabled, onClick = onClick)
                    .semantics { contentDescription = description },
            contentAlignment = Alignment.Center
    ) {
        Text(
                text = glyph,
                color = onFill.copy(alpha = if (enabled) 1f else .5f),
                fontSize = 22.sp)
    }
}

/** Dark enough that the time readout underneath does not show through the speed. */
private const val FINE_SPEED_SCRIM_ALPHA = 0.93f
private val FINE_SPEED_BUTTON_SIZE = 40.dp
private val FINE_SPEED_VALUE_WIDTH = 76.dp

private fun formatDuration(ms: Long): String = TrackTimeFormat.format(ms, Locale.US)

@Composable
private fun SkipColumn(
        modifier: Modifier,
        appearance: PanelAppearance,
        fiveIcon: Int,
        tenIcon: Int,
        thirtyIcon: Int,
        fiveDescription: String,
        tenDescription: String,
        thirtyDescription: String,
        edgeButtonInwardOffset: Dp,
        onFive: () -> Unit,
        onTen: () -> Unit,
        onThirty: () -> Unit
) {
    Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SKIP_BUTTON_GAP)
    ) {
        IconStepButton(
                icon = fiveIcon,
                description = fiveDescription,
                size = SKIP_BUTTON_SIZE,
                appearance = appearance,
                modifier = Modifier.offset(x = edgeButtonInwardOffset),
                onClick = onFive)
        IconStepButton(
                icon = tenIcon,
                description = tenDescription,
                size = FEATURED_SKIP_BUTTON_SIZE,
                appearance = appearance,
                onClick = onTen)
        IconStepButton(
                icon = thirtyIcon,
                description = thirtyDescription,
                size = SKIP_BUTTON_SIZE,
                appearance = appearance,
                modifier = Modifier.offset(x = edgeButtonInwardOffset),
                onClick = onThirty)
    }
}

@Composable
private fun IconStepButton(
        icon: Int,
        description: String,
        size: Dp,
        appearance: PanelAppearance,
        modifier: Modifier = Modifier,
        onClick: () -> Unit
) {
    val fill = PanelReadout.tonalSurface(appearance.triad.primary, .34f)
    Box(
            modifier = modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(Color(fill))
                    .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
    ) {
        Icon(
                painter = painterResource(icon),
                contentDescription = description,
                tint = Color(PanelReadout.contrastingIconColor(fill)),
                modifier = Modifier.size(size * .55f))
    }
}
