package com.svartifoss.snfell.watch.view.lyrics

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.requestFocusOnHierarchyActive
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material3.SwipeToDismissBox
import com.svartifoss.snfell.watch.view.panel.PanelBackdropLayer
import com.svartifoss.snfell.watch.view.panel.ScreenBackdrop
import androidx.wear.compose.material3.Text
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.AccentFloorStyle
import com.svartifoss.snfell.common.LyricLine
import com.svartifoss.snfell.common.LyricsParser
import com.svartifoss.snfell.common.MusicGlyphs
import com.svartifoss.snfell.watch.theme.LocalWatchUiFontFamily
import com.svartifoss.snfell.watch.view.compose.LoadingBars
import com.svartifoss.snfell.watch.view.compose.rememberLyricText
import com.svartifoss.snfell.watch.view.compose.svartifossNoteContent
import com.svartifoss.snfell.watch.view.compose.accentFloorGlow

/**
 * How the three-line window is shaded.
 *
 * The screen shows a *trio* - the line before, the line now, the line next - and everything past it
 * recedes. It used to show two: the neighbour treatment was applied to `currentIndex + 1` only, so
 * the line that had just been sung dropped straight to [INACTIVE_ALPHA] and was, in practice,
 * unreadable. Reading along wants the line you just heard as much as the one coming, because that
 * is what tells you the screen is following the song rather than guessing.
 *
 * [INACTIVE_ALPHA] is set below the old value on purpose. With three lines carried rather than two,
 * the trio only reads as a trio if the fourth line and beyond clearly do not belong to it.
 */
private const val INACTIVE_ALPHA = 0.26f
private const val NEIGHBOUR_ALPHA = 0.55f

/**
 * Type sizes for the window, in sp.
 *
 * The current line keeps the size it always had. The extra line is paid for out of the padding and
 * the space between rows, never out of the type - shrinking the words to fit more of them is how a
 * lyrics screen stops being readable, which is the one thing it has to be.
 */
private const val CURRENT_LINE_SP = 17f
private const val NEIGHBOUR_LINE_SP = 15.5f
private const val DISTANT_LINE_SP = 14f

/**
 * Vertical room given back to the content, in dp.
 *
 * The band was padded 56dp top and bottom, which centred a pair comfortably and left the third line
 * pushed to where `ScalingLazyColumn` has already scaled and faded it past reading. Opening the band
 * is what makes room for the trio; the column's own edge scaling still keeps text off the bezel, so
 * this is not the round-screen guard being given up.
 */
private const val BAND_PADDING_DP = 34

/** Space between rows. Widened along with the band: three lines at the old 6dp read as a block of
 *  text rather than as three separate lines, and separating them is most of what makes the current
 *  one findable at a glance. */
private const val LINE_SPACING_DP = 11


/** Marker drawn for a timed line with no words - an instrumental break in the LRC. Without it the
 *  screen goes blank mid-song and reads as a bug rather than as the song's own silence. Drawn as
 *  the Svartifoss mark like any other note character - see [MusicGlyphs]. */
private val INSTRUMENTAL_MARKER = MusicGlyphs.INSTRUMENTAL_MARKER

/** Width of the current line's progress bar, as a fraction of the row. Narrow on purpose: it marks
 *  the line rather than competing with it. */
private const val LINE_PROGRESS_WIDTH = 0.38f

/**
 * Lyrics for the phone's current track.
 *
 * Deliberately black rather than themed: this screen is read for minutes at a time on an OLED
 * panel, and it is the one surface in the app whose entire job is a wall of text. The album accent
 * appears twice, and only twice - as a wash along the bottom edge and on the line currently being
 * sung - so the eye has exactly one place to land.
 */
@Composable
fun LyricsScreen(
        state: LyricsUiState,
        positionMs: Long,
        durationMs: Long,
        accentColor: Color,
        ambient: Boolean,
        onDismiss: () -> Unit,
        modifier: Modifier = Modifier,
        /**
         * Tapping a line jumps playback to where it begins.
         *
         * Only meaningful for [LyricsUiState.Synced] - a plain block has no timings to jump to, so
         * that variant is not clickable at all rather than being clickable and inert.
         */
        onSeekToLine: (LyricLine) -> Unit = {},
        /**
         * Typeface for the words themselves - a replaceable piece of the theme rather than
         * something this composition owns (MiscPreferences.WEAR_LYRICS_FONT). Null keeps the
         * screen's designed font, which is what the default "follow" resolves to, so a theme that
         * never touched this renders exactly as before.
         *
         * Only the lyric text takes it. The status messages below stay on the UI font: "no lyrics
         * for this track" is chrome, and setting it in a display face chosen for song lyrics reads
         * as a rendering mistake.
         */
        lyricsFontFamily: FontFamily? = null,
        /**
         * The configured ground behind the words, or null to keep the flat black field.
         *
         * Ambient passes null deliberately: an always-on panel must not light artwork.
         */
        screenBackdrop: ScreenBackdrop? = null,
) {
    SwipeToDismissBox(onDismissed = onDismiss) { isBackground ->
        if (isBackground) {
            Box(Modifier.fillMaxSize().background(Color.Black))
            return@SwipeToDismissBox
        }

        // Beneath the accent floor and the words: the floor is this screen's own decoration and
        // has to sit over the ground, not under it.
        screenBackdrop?.let { PanelBackdropLayer(it) }
        Box(
                modifier = modifier
                        .fillMaxSize()
                        .background(
                                if (screenBackdrop == null) Color.Black else Color.Transparent)
                        // Shared with the Verse face - see accentFloorGlow. Ambient drops
                        // it: it is decoration, and decoration is what an always-on panel must
                        // not burn in.
                        // Fixed at STANDARD rather than following the face-scoped preference:
                        // this screen belongs to no face, and it is the composition that piece was
                        // designed for. See AccentFloorStyle.
                        .accentFloorGlow(
                                accentColor,
                                AccentFloorStyle.STANDARD,
                                enabled = !ambient),
                contentAlignment = Alignment.Center
        ) {
            when (state) {
                is LyricsUiState.Synced -> SyncedLyrics(
                        lines = state.lines,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        accentColor = accentColor,
                        ambient = ambient,
                        onSeekToLine = onSeekToLine,
                        fontFamily = lyricsFontFamily ?: LocalWatchUiFontFamily.current)
                is LyricsUiState.Plain -> PlainLyrics(
                        state.text, ambient, lyricsFontFamily ?: LocalWatchUiFontFamily.current)
                LyricsUiState.Loading -> LoadingBars(color = accentColor)
                LyricsUiState.None -> Message(stringResource(R.string.lyrics_none))
                LyricsUiState.Failed -> Message(stringResource(R.string.lyrics_failed))
                LyricsUiState.Disabled -> Message(stringResource(R.string.lyrics_disabled))
                LyricsUiState.NoTrack -> Message(stringResource(R.string.lyrics_no_track))
            }
        }
    }
}

@Composable
private fun SyncedLyrics(
        lines: List<LyricLine>,
        positionMs: Long,
        durationMs: Long,
        accentColor: Color,
        ambient: Boolean,
        onSeekToLine: (LyricLine) -> Unit,
        fontFamily: FontFamily,
) {
    val currentIndex = LyricsParser.indexAt(lines, positionMs)
    val listState = rememberScalingLazyListState()

    // Only on a line change, never on every position tick - animating to the same index each tick
    // fights the user's own scrolling and never settles.
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) {
            listState.animateScrollToItem(currentIndex)
        }
    }

    ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = BAND_PADDING_DP.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(LINE_SPACING_DP.dp),
            // Passed explicitly, and never null: the overload without it is a deprecated
            // compatibility shim whose legacy touch path stops plain swipes scrolling at all -
            // the same trap QueueScreen and MenuScreen document. Ambient delivers no input, so
            // there is nothing to suppress there.
            rotaryScrollableBehavior =
                    RotaryScrollableDefaults.behavior(scrollableState = listState)
    ) {
        itemsIndexed(lines) { index, line ->
            LyricRow(
                    line = line,
                    isCurrent = index == currentIndex,
                    // Both sides, not just the next one: the trio is the point - see
                    // INACTIVE_ALPHA. Guarded on currentIndex being real, or with no current line
                    // (before the first lyric) index -1 would light row 0 as a neighbour.
                    isNeighbour = currentIndex >= 0 &&
                            (index == currentIndex - 1 || index == currentIndex + 1),
                    accentColor = accentColor,
                    ambient = ambient,
                    fontFamily = fontFamily,
                    onSeek = { onSeekToLine(line) },
                    // Shared with the Verse face, which draws the same bar under its own current
                    // line - see LyricsParser.lineProgress.
                    lineProgress = if (index == currentIndex) {
                        LyricsParser.lineProgress(lines, index, positionMs, durationMs)
                    } else {
                        0f
                    })
        }
    }
}

@Composable
private fun LyricRow(
        line: LyricLine,
        isCurrent: Boolean,
        isNeighbour: Boolean,
        accentColor: Color,
        ambient: Boolean,
        fontFamily: FontFamily,
        lineProgress: Float,
        onSeek: () -> Unit,
) {
    val target = when {
        // Ambient is outline-only: the accent would be a large block of lit colour on a panel
        // that must stay dim, so the current line is carried by brightness alone.
        isCurrent && ambient -> Color.White
        isCurrent -> accentColor
        isNeighbour -> Color.White.copy(alpha = NEIGHBOUR_ALPHA)
        else -> Color.White.copy(alpha = INACTIVE_ALPHA)
    }
    // Animation is skipped in ambient, where the display refreshes about once a minute and a
    // tween would only ever be seen part-finished.
    val color by animateColorAsState(
            targetValue = if (ambient) target.copy(alpha = target.alpha * 0.7f) else target,
            animationSpec = tween(durationMillis = if (ambient) 0 else 260),
            label = "lyricColor")
    // Three steps rather than two, so the trio is a hierarchy rather than "the current one and
    // everything else". The current line's own size never moves.
    val targetSize = when {
        isCurrent -> CURRENT_LINE_SP
        isNeighbour -> NEIGHBOUR_LINE_SP
        else -> DISTANT_LINE_SP
    }
    val fontSize by animateFloatAsState(
            targetValue = targetSize,
            animationSpec = tween(durationMillis = if (ambient) 0 else 260),
            label = "lyricSize")

    // Tap the line, hear it again. No ripple: this screen is a wall of text on pure black whose
    // whole design is that the accent appears in exactly two places, and a light rectangle
    // sweeping a row would be a third. The confirmation is better than an indication anyway - the
    // tapped line *becomes* the current line the same instant, taking the accent, the larger type
    // and the progress bar with it, and the list scrolls it to centre.
    //
    // Disabled in ambient. The system does not deliver touches there at all, so this is a
    // statement of intent rather than a guard - but an enabled clickable on a dimmed screen also
    // advertises an affordance that does not exist.
    val interactionSource = remember { MutableInteractionSource() }
    val seekLabel = stringResource(R.string.lyrics_seek_to_line)
    val seekModifier = if (ambient) {
        Modifier
    } else {
        Modifier.clickable(
                interactionSource = interactionSource,
                indication = null,
                onClickLabel = seekLabel,
                onClick = onSeek)
    }

    if (line.text.isBlank()) {
        Text(
                text = rememberLyricText(INSTRUMENTAL_MARKER),
                color = color,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                inlineContent = svartifossNoteContent(color),
                // Clickable like any other row: an instrumental break is a real position in the
                // song, and "take me back to the solo" is as reasonable as any lyric tap.
                modifier = Modifier.fillMaxWidth().then(seekModifier).padding(vertical = 2.dp))
        return
    }

    Column(
            modifier = Modifier.fillMaxWidth().then(seekModifier),
            horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
                // Note characters in the words themselves become the mark too, not just our own
                // instrumental marker above - see MusicNoteText.
                text = rememberLyricText(line.text),
                color = color,
                fontFamily = fontFamily,
                inlineContent = svartifossNoteContent(color),
                fontSize = fontSize.sp,
                // Line height tied to the size rather than left to the font's own default: at
                // three lines a wrapped lyric's rows have to sit as tightly as the lines
                // themselves, or one long line eats the room the trio needs.
                lineHeight = (fontSize * 1.24f).sp,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth())

        // How far through *this line* the song is - the one figure a wall of lyrics cannot show
        // and the reader actually wants, since a line can hold for the better part of ten seconds.
        // Only under the current line, and never in ambient, where it would be a lit bar redrawn
        // once a minute: wrong most of the time and burning in while it is.
        if (isCurrent && !ambient) {
            val fraction by animateFloatAsState(
                    targetValue = lineProgress,
                    animationSpec = tween(durationMillis = 240),
                    label = "lyricLineProgress")
            Spacer(Modifier.height(5.dp))
            Box(
                    modifier = Modifier
                            .fillMaxWidth(LINE_PROGRESS_WIDTH)
                            .height(1.dp)
                            .drawBehind {
                                drawRect(color = accentColor.copy(alpha = 0.18f))
                                drawRect(
                                        color = accentColor.copy(alpha = 0.85f),
                                        topLeft = Offset.Zero,
                                        size = Size(size.width * fraction, size.height))
                            })
        }
    }
}

/**
 * Lyrics with no timing: one scrollable block, since there is nothing to follow.
 *
 * The crown drives it, like every other scrollable surface in the app. It did not, and the reason
 * is worth recording because it is easy to reintroduce: rotary input on Wear Compose is not
 * ambient, it is delivered to a *focused* scrollable. `ScalingLazyColumn` takes a
 * `rotaryScrollableBehavior` parameter and arranges the focus itself, which is how the synced view
 * above, the queue, the menu and the face picker all got the crown without anyone thinking about
 * it - and this screen is the one place with a plain `verticalScroll`, which has no such parameter
 * and therefore silently had no crown at all. Same [RotaryScrollableDefaults] behaviour as
 * everywhere else, just attached by hand because there is no list to attach it to.
 *
 * Scoped to this branch rather than hoisted to [LyricsScreen]: a rotary handler around the whole
 * screen would be a second claimant alongside the synced view's own, and two handlers over one
 * gesture is how a crown starts scrolling two things at once.
 */
@Composable
private fun PlainLyrics(text: String, ambient: Boolean, fontFamily: FontFamily) {
    val scrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }
    Column(
            modifier = Modifier
                    .fillMaxSize()
                    // requestFocusOnHierarchyActive, not the older rememberActiveFocusRequester:
                    // that one is deprecated, and this project has already been caught once taking
                    // a deprecated Wear rotary API at face value - the ScalingLazyColumn overload
                    // without an explicit behaviour, whose legacy touch path stopped plain swipes
                    // scrolling at all (see the note on the synced view above).
                    //
                    // Order matters and the requester is attached only once: this node is itself a
                    // FocusRequesterModifierNode, so it focuses the next focus target in the chain
                    // - which is the one rotaryScrollable adds. Adding a second .focusRequester()
                    // here would bind the same requester to two nodes and make "which one gets
                    // focused" a coin toss.
                    .requestFocusOnHierarchyActive()
                    .rotaryScrollable(
                            RotaryScrollableDefaults.behavior(scrollableState = scrollState),
                            focusRequester = focusRequester)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 52.dp),
            horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val plainColor = Color.White.copy(alpha = if (ambient) 0.6f else 0.86f)
        Text(
                text = rememberLyricText(text),
                color = plainColor,
                fontFamily = fontFamily,
                inlineContent = svartifossNoteContent(plainColor),
                fontSize = 15.sp,
                textAlign = TextAlign.Center)
        Box(Modifier.height(20.dp))
    }
}

@Composable
private fun Message(text: String) {
    Text(
            text = text,
            color = Color.White.copy(alpha = 0.72f),
            fontFamily = LocalWatchUiFontFamily.current,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 26.dp))
}
