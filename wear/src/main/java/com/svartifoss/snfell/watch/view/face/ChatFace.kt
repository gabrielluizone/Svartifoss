package com.svartifoss.snfell.watch.view.face

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import androidx.annotation.DrawableRes
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.toArgb
import androidx.wear.compose.material3.Icon
import com.svartifoss.snfell.common.AdaptiveTextContrast
import com.svartifoss.snfell.R
import androidx.compose.ui.res.stringResource
import com.svartifoss.snfell.watch.view.compose.FaceClock

/**
 * Chat: the listening session rendered as a message thread.
 *
 * The track playing right now is a message and its voice note: one bubble carrying the title and
 * artist, and under it the wide voice-message bubble - the cover as the sender's avatar, a waveform
 * that fills as the track advances, the elapsed time and the delivered ticks.
 *
 * The palette is the album's: the outgoing (current-track) bubble takes the album accent, the
 * received bubbles a dark tonal derivative of it, so the whole conversation re-skins per track
 * without any per-face colour preference of its own.
 *
 * It deliberately shows **only the current track**. An earlier version kept a session thread of
 * previously played tracks above these, which is the more literal reading of "a chat", but on a
 * 192dp screen every past bubble came directly out of the two elements that carry the information,
 * and neither could then be the size the composition wants. The queue button below is the way back
 * through what has played.
 *
 * Interaction keeps the shared contract, but the target is the **centre of the screen** rather than
 * a control inside the layout: tap toggles playback, double tap opens quick actions, long press
 * opens the face picker. The voice bubble deliberately is not clickable - it sits over that centre,
 * and making it a tap target is what swallowed the play/pause taps in the first version.
 *
 * It does carry two round actions (queue, skip next), which the rest of the curated collection
 * deliberately does not. The reason is this face's own defaults: the thread occupies the band the
 * mini-button row lives in, so Chat turns that row off (see [FaceScopedPreferences]) and would
 * otherwise ship with no shortcuts at all. Play/pause is still not among them - it stays on the
 * centre tap, as everywhere else.
 */
@Composable
fun ChatFace(state: NowPlayingFaceState, listener: NowPlayingFaceListener) {
    if (state.ambient) {
        ChatAmbient(state)
        return
    }

    val accent = Color(state.accentColor)
    val incoming = Color(faceTonal(state.accentColor, INCOMING_LIGHTNESS))
    val outgoing = Color(faceTonal(state.accentColor, OUTGOING_LIGHTNESS, minSat = .35f))

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screen = maxWidth
        // Shared pipeline, same as every Compose face: the host's artwork View sits under this
        // ComposeView and the background style composes on top of it.
        PlayerBackgroundTreatment(state)
        PlayerShadingOverlay(state)

        // The centre of the screen carries the standard contract - tap play/pause, double tap quick
        // actions, long press the face picker - because the host's own center_tap_zone View is GONE
        // for every Compose face, so a face that wires nothing here has no working centre at all.
        // Placed under the content: the bubbles are not interactive, so taps land here, while the
        // two round actions below sit on top and take their own.
        CenterGestureRegion(listener, size = screen * .62f)

        Column(
                modifier = Modifier
                        .fillMaxSize()
                        // Wide side padding: bubbles are rectangles on a round screen, so their
                        // corners are the first thing the bezel eats.
                        .padding(horizontal = screen * .09f)
                        .padding(top = screen * .085f, bottom = screen * .05f),
                horizontalAlignment = Alignment.CenterHorizontally,
                // Bottom-anchored, so the newest message is always the one at a fixed place and
                // older ones drift up and out - a thread, not a list that grows downward off-screen.
                verticalArrangement = Arrangement.Bottom
        ) {
            // Just the day chip, the current track and its voice note. The thread used to draw
            // past tracks above these, but on a 192dp screen every past bubble came straight out
            // of the two elements that matter, and neither could then be the size the design
            // wants. Looking further back is what the queue button below is for.
            DayChip(incoming)
            Spacer(Modifier.height(6.dp))

            // The current track's own text. It has to be here: the voice bubble below shows a
            // cover and a waveform but names nothing. Sent as an outgoing bubble like the voice
            // note under it - same sender, two messages.
            CurrentMessageBubble(state = state, color = outgoing)
            Spacer(Modifier.height(4.dp))

            VoiceBubble(
                    state = state,
                    bubbleColor = outgoing,
                    accent = accent,
                    screen = screen)

            Spacer(Modifier.height(5.dp))
            ChatActionRow(listener = listener, accent = accent, screen = screen)
        }

        FaceClock(
                visible = state.showClock,
                color = Color(state.clockColor),
                fontFamily = state.clockFont,
                typography = state.clockTypography)
    }
}

/** Bubble and action sizes. The current track's bubble is the screen's subject, so it is the
 *  largest element on it. */
private val CURRENT_BUBBLE_HEIGHT = 58.dp

/** Bubble rounding. Softer than a card but well short of a stadium: at the full half-height the
 *  bubble stops reading as a bubble and starts reading as a pill. The tail corner - the one facing
 *  the sender - stays small, which is the single detail that makes the shape a speech bubble. */
private val BUBBLE_CORNER = 18.dp
private val BUBBLE_TAIL_CORNER = 6.dp
private const val ACTION_DIAMETER_FRACTION = .215f
private const val GLYPH_FRACTION = .48f

private const val INCOMING_LIGHTNESS = .17f
private const val OUTGOING_LIGHTNESS = .32f

/**
 * The "Today" divider a chat puts above the day's first message. Scene-setting: it is what makes
 * the screen read as a conversation at a glance rather than as two coloured pills.
 */
@Composable
private fun DayChip(color: Color) {
    Box(
            modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(color.copy(alpha = .85f))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
                text = stringResource(R.string.chat_face_today),
                color = Color.White.copy(alpha = .75f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
        )
    }
}

/**
 * Title over artist for the track playing now, as one outgoing bubble.
 *
 * One bubble with two lines rather than the two separate bubbles this face started with: on a
 * 192dp screen the pair cost about twenty vertical dp in padding and gap alone, and that is the
 * difference between the thread fitting and the oldest message being clipped off the top.
 */
@Composable
private fun CurrentMessageBubble(state: NowPlayingFaceState, color: Color) {
    val showTitle = state.showTitle && state.title.isNotEmpty()
    val showArtist = state.showArtist && state.artist.isNotEmpty()
    if (!showTitle && !showArtist) return
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        ChatBubble(color = color, incoming = false) {
            Column {
                if (showTitle) {
                    Text(
                            text = state.title,
                            color = Color.White,
                            fontFamily = state.titleFont,
                            fontWeight = FontWeight.Bold,
                            fontStyle = state.titleFontStyle,
                            letterSpacing = state.titleLetterSpacing,
                            fontSize = state.titleTypography.scaled(15f).sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                    )
                }
                if (showArtist) {
                    Text(
                            text = state.artist,
                            color = Color.White.copy(alpha = .70f),
                            fontFamily = state.artistFont,
                            fontStyle = state.artistFontStyle,
                            fontSize = state.artistTypography.scaled(12f).sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * A message bubble. The corner facing the sender is squared off, which is the one detail that makes
 * a rounded rectangle read as a speech bubble rather than as a button.
 */
@Composable
private fun ChatBubble(
        color: Color,
        incoming: Boolean,
        content: @Composable () -> Unit
) {
    val shape = if (incoming) {
        RoundedCornerShape(
                topStart = BUBBLE_TAIL_CORNER,
                topEnd = BUBBLE_CORNER,
                bottomEnd = BUBBLE_CORNER,
                bottomStart = BUBBLE_CORNER)
    } else {
        RoundedCornerShape(
                topStart = BUBBLE_CORNER,
                topEnd = BUBBLE_TAIL_CORNER,
                bottomEnd = BUBBLE_CORNER,
                bottomStart = BUBBLE_CORNER)
    }
    Box(
            modifier = Modifier
                    .widthIn(max = 210.dp)
                    .clip(shape)
                    .background(color)
                    .padding(horizontal = 11.dp, vertical = 6.dp),
            contentAlignment = Alignment.CenterStart
    ) {
        content()
    }
}

/**
 * The current track as a voice message: avatar, waveform, play glyph - and, *below* the bubble and
 * right-aligned, the elapsed time and the delivered ticks.
 *
 * Those two sit outside the bubble because that is where a chat puts them: the timestamp and the
 * read receipt belong to the message, not to its contents, and inside they were competing with the
 * waveform for the same band. Outside they also stop constraining the bubble's height.
 *
 * The bubble is deliberately NOT a tap target. The gesture already belongs to the centre of the
 * screen, and making it clickable would swallow the taps meant for [CenterGestureRegion]
 * underneath - exactly why centre play/pause did nothing on the first version of this face.
 */
@Composable
private fun VoiceBubble(
        state: NowPlayingFaceState,
        bubbleColor: Color,
        accent: Color,
        screen: Dp
) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Box(
                modifier = Modifier
                        .fillMaxWidth()
                        .height(CURRENT_BUBBLE_HEIGHT)
                        .clip(RoundedCornerShape(
                                topStart = BUBBLE_CORNER,
                                topEnd = BUBBLE_TAIL_CORNER,
                                bottomEnd = BUBBLE_CORNER,
                                bottomStart = BUBBLE_CORNER))
                        .background(bubbleColor)
                        .padding(horizontal = 8.dp)
        ) {
            Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar(state.albumArt, accent, size = 34.dp)
                Spacer(Modifier.width(8.dp))
                // Centred in what is left, with nothing stacked under it - that stacking is what
                // used to push the waveform above the bubble's midline by half a line of text.
                Box(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentAlignment = Alignment.Center
                ) {
                    Waveform(
                            progress = state.progress,
                            playing = state.playing,
                            played = accent,
                            remaining = Color.White.copy(alpha = .35f))
                }
                Spacer(Modifier.width(7.dp))
                // Display-only: it mirrors playback state so the bubble reads as a voice note, but
                // the gesture that changes it is the centre tap.
                PlayGlyph(playing = state.playing, showControls = state.showControls)
            }
        }

        if (state.showTrackTime) {
            Row(
                    modifier = Modifier.padding(top = 3.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                        text = formatFaceClockTime(state.positionMs),
                        color = Color.White.copy(alpha = .60f),
                        fontFamily = state.artistFont,
                        fontSize = 10.sp,
                        maxLines = 1
                )
                Spacer(Modifier.width(4.dp))
                DoubleTick(if (state.playing) accent else Color.White.copy(alpha = .35f))
            }
        }
    }
}

/** The album cover as the sender's profile picture; a flat accent disc before any art arrives. */
@Composable
private fun Avatar(art: ImageBitmap?, accent: Color, size: Dp) {
    Box(
            modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = .55f)),
            contentAlignment = Alignment.Center
    ) {
        if (art != null) {
            Image(
                    bitmap = art,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Voice-note waveform doubling as the progress bar: bars left of [progress] are filled, the rest
 * are not, and the bar at the playhead breathes while playing.
 *
 * The bar heights are a fixed pattern rather than real audio, which the watch has no access to -
 * randomising them per frame would read as noise, and re-randomising per track would make the
 * waveform jump on every skip for no information gain.
 */
@Composable
private fun Waveform(
        progress: Float,
        playing: Boolean,
        played: Color,
        remaining: Color
) {
    val transition = rememberInfiniteTransition(label = "chatWave")
    val pulse by transition.animateFloat(
            initialValue = if (playing) .82f else 1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(620), RepeatMode.Reverse),
            label = "chatWavePulse"
    )
    Canvas(Modifier.fillMaxWidth().height(18.dp)) {
        val count = WAVE_PATTERN.size
        val slot = size.width / count
        val barWidth = (slot * .45f).coerceAtLeast(1f)
        val playedBars = (progress.coerceIn(0f, 1f) * count).toInt()
        WAVE_PATTERN.forEachIndexed { index, fraction ->
            // Only the bar at the playhead breathes; animating all of them made the whole bubble
            // shimmer, which fights the text sitting right under it.
            val scale = if (playing && index == playedBars) pulse else 1f
            val barHeight = (size.height * fraction * scale).coerceAtLeast(2f)
            drawRoundRect(
                    color = if (index < playedBars) played else remaining,
                    topLeft = Offset(
                            index * slot + (slot - barWidth) / 2f,
                            (size.height - barHeight) / 2f),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2f)
            )
        }
    }
}

/** Static, deliberately irregular bar heights - see [Waveform]. */
private val WAVE_PATTERN = listOf(
        .30f, .55f, .80f, .45f, 1f, .65f, .35f, .75f, .50f, .90f,
        .40f, .70f, .25f, .60f, .85f, .45f, .30f, .55f
)

/** Filled play/pause glyph in the dark circle the reference chat uses. */
@Composable
private fun PlayGlyph(playing: Boolean, showControls: Boolean) {
    Box(
            modifier = Modifier
                    .size(31.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = .45f)),
            contentAlignment = Alignment.Center
    ) {
        // "Hidden" screen themes keep the hit target and the accessibility action but draw no
        // glyph, matching ScreenTheme's contract everywhere else.
        if (!showControls) return@Box
        Canvas(Modifier.size(13.dp)) {
            if (playing) {
                val barWidth = size.width * .3f
                drawRect(Color.White, Offset(0f, 0f), Size(barWidth, size.height))
                drawRect(Color.White, Offset(size.width - barWidth, 0f), Size(barWidth, size.height))
            } else {
                drawPath(
                        Path().apply {
                            moveTo(0f, 0f)
                            lineTo(size.width, size.height / 2f)
                            lineTo(0f, size.height)
                            close()
                        },
                        Color.White
                )
            }
        }
    }
}

/** The two "delivered" ticks. */
@Composable
private fun DoubleTick(color: Color) {
    Canvas(Modifier.size(width = 13.dp, height = 8.dp)) {
        val stroke = Stroke(width = size.height * .18f)
        fun tick(offsetX: Float) {
            drawPath(
                    Path().apply {
                        moveTo(offsetX, size.height * .55f)
                        lineTo(offsetX + size.width * .18f, size.height * .85f)
                        lineTo(offsetX + size.width * .52f, size.height * .18f)
                    },
                    color = color,
                    style = stroke
            )
        }
        tick(0f)
        tick(size.width * .33f)
    }
}

/**
 * The two round actions the reference chat puts under the thread.
 *
 * A deliberate departure from the curated collection's "no fixed action row" rule, and the reason
 * is the layout itself: Chat defaults its mini-button row off (see FaceScopedPreferences) because
 * the conversation occupies that band, so without these the face would ship with no shortcuts at
 * all. Queue takes the microphone's place and skip-next the keyboard's; play/pause is deliberately
 * *not* here - it stays on the centre tap, as on every other face.
 */
@Composable
private fun ChatActionRow(
        listener: NowPlayingFaceListener,
        accent: Color,
        screen: Dp
) {
    val diameter = (screen * ACTION_DIAMETER_FRACTION).coerceIn(38.dp, 50.dp)
    // Worked out against the button's own fill rather than assumed dark. The buttons are painted in
    // the album accent, which arrives anywhere from near-black to near-white, so a fixed dark glyph
    // disappeared entirely on dark covers - which is exactly what happened.
    val onAccent = if (AdaptiveTextContrast.prefersDarkText(accent.toArgb())) {
        Color.Black.copy(alpha = .82f)
    } else {
        Color.White
    }
    Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
    ) {
        FaceTapTarget(
                width = diameter,
                height = diameter,
                shape = CircleShape,
                background = accent,
                label = stringResource(R.string.quick_action_up_next),
                onClick = listener::onQueueTap
        ) {
            ActionGlyph(R.drawable.ic_face_queue_music, onAccent, diameter * GLYPH_FRACTION)
        }
        Spacer(Modifier.width(screen * .05f))
        FaceTapTarget(
                width = diameter,
                height = diameter,
                shape = CircleShape,
                background = accent,
                label = stringResource(R.string.action_name_skip_next),
                onClick = listener::onSkipNextTap
        ) {
            ActionGlyph(R.drawable.ic_face_skip_next, onAccent, diameter * GLYPH_FRACTION)
        }
    }
}

/**
 * One action glyph, drawn from the Material Symbols vector rather than hand-plotted on a Canvas.
 *
 * The hand-drawn versions this replaced were the wrong shape and sat off-centre in their circles;
 * a real icon asset is centred by its own viewBox and is the same mark the rest of the app uses.
 */
@Composable
private fun ActionGlyph(@DrawableRes icon: Int, tint: Color, size: Dp) {
    Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size)
    )
}

/**
 * Ambient variant: the thread reduced to one outlined bubble.
 *
 * Outline-only and fill-free for burn-in, like every other ambient face here. The history bubbles
 * are dropped entirely rather than outlined too - on an always-on screen they are stale by
 * definition, and three stacked outlines is a lot of lit pixels for information nobody is reading.
 */
@Composable
private fun ChatAmbient(state: NowPlayingFaceState) {
    val tint = Color(state.ambientTint).copy(alpha = state.ambientIntensity.coerceIn(.2f, 1f))
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val screen = maxWidth
        Column(
                modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = screen * .14f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
        ) {
            if (state.ambientShowTrackInfo && state.title.isNotEmpty()) {
                Box(
                        modifier = Modifier
                                .widthIn(max = 190.dp)
                                .border(
                                        width = 1.dp,
                                        color = tint,
                                        shape = RoundedCornerShape(
                                                topStart = 12.dp,
                                                topEnd = 3.dp,
                                                bottomEnd = 12.dp,
                                                bottomStart = 12.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                                text = state.title,
                                color = tint,
                                fontFamily = state.titleFont,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Start
                        )
                        if (state.artist.isNotEmpty()) {
                            Text(
                                    text = state.artist,
                                    color = tint.copy(alpha = tint.alpha * .7f),
                                    fontFamily = state.artistFont,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val CHAT_ANIM_MS = 260
