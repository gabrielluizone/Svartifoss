package com.svartifoss.snfell.watch.view.face

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.svartifoss.snfell.common.AdaptiveTextContrast
import com.svartifoss.snfell.common.FaceGeometry
import com.svartifoss.snfell.common.LyricLine
import com.svartifoss.snfell.common.LyricsParser
import com.svartifoss.snfell.common.MusicGlyphs
import com.svartifoss.snfell.common.RoundScreenText
import com.svartifoss.snfell.watch.view.compose.rememberLyricText
import com.svartifoss.snfell.watch.view.compose.svartifossNoteContent
import com.svartifoss.snfell.watch.theme.WatchTheme
import com.svartifoss.snfell.watch.view.compose.FaceClock

/** Alpha of the lines either side of the one being sung. */
private const val NEIGHBOUR_ALPHA = 0.38f

/** Marker for a timed line with no words - an instrumental break the LRC marks explicitly. Drawn
 *  as the Svartifoss mark like any other note character - see [MusicGlyphs]. */
private val INSTRUMENTAL_MARKER = MusicGlyphs.INSTRUMENTAL_MARKER

/**
 * Vertical band the three lyric lines occupy, as fractions of screen height.
 *
 * It sets how wide the text may be without running into the bezel (a round screen's usable chord
 * collapses fast towards the bottom - see [RoundScreenText]). The block itself stays centred on
 * the display.
 *
 * The band reaches into the former empty strip above the elapsed time. The cost is real and worth
 * stating: a deeper band means a narrower chord, so each line holds fewer characters than it did.
 * Three narrower lines still carry appreciably more of a lyric than two wide ones, which is the
 * trade this is making.
 */
private const val VERSE_BAND_TOP = FaceGeometry.Verse.BAND_TOP
private const val VERSE_BAND_BOTTOM = FaceGeometry.Verse.BAND_BOTTOM
private const val VERSE_HEADER_TOP = FaceGeometry.Verse.HEADER_TOP

/**
 * Where the block's centre sits, as a fraction of screen height. This is deliberately independent
 * of the asymmetric safe-width band: users perceive the title card and current lyric against the
 * centre of the dial, not the centre of that calculation.
 */
private const val VERSE_BAND_CENTER = FaceGeometry.Verse.BAND_CENTER

/**
 * Verse: the song's words on the main screen, three lines at a time.
 *
 * Where the full lyrics screen is something you open and read, this is something you glance at. It
 * shows only the line before, the line now, and the line next - which is as much as a wrist-sized
 * screen can carry without becoming a page - over the same black-and-accent floor the lyrics screen
 * uses, so the two read as one feature on two surfaces.
 *
 * Four things are deliberate:
 *
 *  - **The lyric lines take their typeface from the theme**, through
 *    [NowPlayingFaceState.lyricFont] rather than from anything named here
 *    (MiscPreferences.WEAR_LYRICS_FONT, which follows the track typeface until you set it). This
 *    face was designed around Marcellus - every other face in the collection is sans, and a face
 *    whose entire subject is written language should not look like a dashboard - but that is now a
 *    recommendation the picker can honour, not a fixture: welding it in was what made the one thing
 *    this face is *about* the one thing about it nobody could change. Whatever it resolves to is
 *    scoped to the *words* - the running head, the elapsed time and everything else ordinary go
 *    through the shared text helpers, so the font, colour and typography preferences reach this
 *    face exactly as they reach the others. The face designs its own composition, not its own
 *    settings.
 *  - **The hairline under the current line tracks that line, not the track.** The progress arc and
 *    the mini-button row are both off by default here (this face composes the whole screen edge to
 *    edge, as Chat and Split do), which would otherwise leave a screen with no motion at all
 *    between one line and the next - several seconds of a still image, on a device where stillness
 *    reads as "frozen". Tying it to the current line also answers a question the track-level bar
 *    cannot: how long this line has left.
 *  - **The accent is lifted before use.** A near-black cover yields a near-black accent, and on
 *    this backdrop that renders the current line invisible - taking with it the only thing marking
 *    where the song is. [AdaptiveTextContrast] raises lightness only, so it stays the album's
 *    colour rather than becoming white.
 *  - **A track with no lyric gets a composition, not an error.** That is the common case, not a
 *    failure, so the face falls back to a title card: the track set large in the same serif with
 *    the artist beneath it. A face that can look broken is a face people switch away from.
 */
@Composable
fun VerseFace(state: NowPlayingFaceState, listener: NowPlayingFaceListener) {
    if (state.ambient) {
        VerseAmbient(state)
        return
    }

    val accent = Color(AdaptiveTextContrast.adapt(state.accentColor, BACKDROP_LUMINANCE))

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screen = maxWidth

        // Shared pipeline, not a backdrop of its own. This face *defaults* to the black backdrop
        // and to wearing the accent floor, but both are ordinary face-scoped choices: someone who
        // wants these words over an Expressive blur can have that, and the floor is a piece any
        // face can wear (see AccentFloorStyle). Painting black here instead is what made the
        // background picker do nothing on this face.
        PlayerBackgroundTreatment(state)

        // With no lyric the band falls back to a title card, which *is* the track's name and
        // artist set large - so the running head above would print both of them a second time.
        // The header is a caption for the words; with no words there is nothing to caption.
        val hasLyrics = state.lyricLines.isNotEmpty()
        if (hasLyrics) {
            Header(state, screen)
        }

        VerseBand(state = state, accent = accent, screen = screen)

        ElapsedTime(state, screen)

        FaceClock(
                visible = state.showClock,
                color = Color(state.clockColor),
                fontFamily = state.clockFont,
                typography = state.clockTypography)

        // No central control to hang gestures on, so the whole middle of the screen is the target -
        // the host's own centre tap zone is GONE for every Compose face, so a face that wires
        // nothing here has no working play/pause at all.
        CenterGestureRegion(listener = listener, size = screen * 0.52f, pulseSize = screen * 0.34f)
    }
}

/** Artist over title, small and letterspaced, sitting above the words like a running head. */
@Composable
private fun androidx.compose.foundation.layout.BoxWithConstraintsScope.Header(
        state: NowPlayingFaceState,
        screen: Dp,
) {
    val visibility = resolveMetadataVisibility(
            title = state.title,
            artist = state.artist,
            showTitle = state.showTitle,
            showArtist = state.showArtist,
            titleIsStatus = state.titleIsStatus,
            artistIsStatus = state.artistIsStatus)

    val inset = RoundScreenText.sideInsetFor(top = 0.13f, bottom = 0.26f)

    Column(
            modifier = Modifier
                    .fillMaxWidth()
                    .align(state.blockPlacement(Alignment.TopCenter))
                            .padding(horizontal = state.blockSafeSideInset(screen))
                            .padding(vertical = state.blockSafeVerticalInset(screen))
                    // Leave a small extra gap above a three-line current lyric without moving the
                    // lyrics themselves away from the centre of the dial.
                    .padding(
                            top = state.blockDesignedTopPadding(screen * VERSE_HEADER_TOP),
                            start = screen * inset,
                            end = screen * inset),
            horizontalAlignment = state.blockAlignment(Alignment.CenterHorizontally)
    ) {
        // Both through the shared helpers, so the artist/title colour treatments, the per-element
        // typography (weight, italic, scale, opacity, tracking) and the title's wrap/marquee/shrink
        // mode all reach this face. The header is ordinary metadata; only the *lyric* lines below
        // are this face's own design.
        if (visibility.artist) {
            ArtistLineText(
                    text = state.artist.uppercase(),
                    state = state,
                    color = Color(state.artistColor).copy(alpha = 0.78f),
                    fontSize = 10.sp,
                    letterSpacing = 0.18.em)
        }
        if (visibility.title) {
            // It applies the typography spec itself, so the size passed here is the one this face
            // designed and the user's scale multiplies it rather than replacing it.
            AdaptiveTitleText(
                    text = state.title,
                    mode = state.titleTextMode,
                    state = state,
                    fontSize = 13.sp,
                    color = titleTextColor(state, Color.White.copy(alpha = 0.92f)),
                    fontWeight = state.titleFontWeight,
                    fontStyle = state.titleFontStyle,
                    fontFamily = state.titleFont,
                    typography = state.titleTypography)
        }
    }
}

/** Previous / current / next, or the title card when this track has no words. */
@Composable
private fun androidx.compose.foundation.layout.BoxWithConstraintsScope.VerseBand(
        state: NowPlayingFaceState,
        accent: Color,
        screen: Dp,
) {
    val inset = RoundScreenText.sideInsetFor(top = VERSE_BAND_TOP, bottom = VERSE_BAND_BOTTOM)

    Box(
            modifier = Modifier
                    .fillMaxSize()
                    .padding(start = screen * inset, end = screen * inset)
                    // Keep the fallback title card and the lyric reel at the same central anchor.
                    // The asymmetric band above only narrows their safe text width.
                    .offset(y = screen * (VERSE_BAND_CENTER - 0.5f)),
            contentAlignment = Alignment.Center
    ) {
        val lines = state.lyricLines
        if (lines.isEmpty()) {
            TitleCard(state, accent, pending = state.lyricsPending)
            return@Box
        }

        val index = LyricsParser.indexAt(lines, state.positionMs)
        // -1 is the intro: the song has started but the first line has not. Showing the opening
        // line as "current" there would have it highlighted for several seconds before it is sung,
        // so it sits as the *next* line instead and lights up on cue.
        val current = lines.getOrNull(index)
        val previous = lines.getOrNull(index - 1)
        val next = lines.getOrNull(index + 1)

        Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = state.blockAlignment(Alignment.CenterHorizontally)
        ) {
            NeighbourLine(previous, state.lyricFont)
            Box(Modifier.height(screen * 0.035f))
            CurrentLine(
                    line = current,
                    // Shared with the lyrics screen, so the two cannot disagree about how far
                    // through a line the song is.
                    rawFraction = LyricsParser.lineProgress(
                            lines, index, state.positionMs, state.durationMs),
                    accent = accent,
                    progressColor = Color(state.progressColor),
                    showProgress = state.showInternalProgress,
                    fontFamily = state.lyricFont,
                    screen = screen,
                    align = state.blockAlignment(Alignment.CenterHorizontally))
            Box(Modifier.height(screen * 0.035f))
            NeighbourLine(next, state.lyricFont)
        }
    }
}

@Composable
private fun NeighbourLine(line: LyricLine?, fontFamily: FontFamily) {
    val color = Color.White.copy(alpha = NEIGHBOUR_ALPHA)
    Text(
            text = rememberLyricText(line?.let { it.text.ifBlank { INSTRUMENTAL_MARKER } } ?: ""),
            color = color,
            fontFamily = fontFamily,
            inlineContent = svartifossNoteContent(color),
            fontSize = 12.sp,
            lineHeight = 15.sp,
            // One line each, and the line below is why: at two lines apiece the worst case runs to
            // 71% of the screen height once the current line also wraps, which collides with the
            // running head above and the time below. Anyone who wants the full text has the lyrics
            // screen; this face is a glance.
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth())
}

/**
 * The line being sung, with a hairline beneath it filling as that line runs out.
 *
 * The fill needs the *next* line's timestamp to know how long this one lasts; on the final line
 * there is none, so it falls back to the end of the track. A line with no measurable span (two
 * timestamps on the same millisecond, which malformed files do contain) draws the hairline empty
 * rather than dividing by zero.
 */
@Composable
private fun CurrentLine(
        line: LyricLine?,
        rawFraction: Float,
        accent: Color,
        progressColor: Color,
        showProgress: Boolean,
        fontFamily: FontFamily,
        screen: Dp,
        /** Resolved by the caller, which is the one place that holds the face state. The lyric is
         *  part of this face's text block, so it follows the block's alignment like the rest. */
        align: Alignment.Horizontal,
) {
    val color by animateColorAsState(
            targetValue = if (line == null) Color.White.copy(alpha = NEIGHBOUR_ALPHA) else accent,
            animationSpec = tween(260),
            label = "verseCurrentColor")

    val fraction by animateFloatAsState(
            targetValue = rawFraction, animationSpec = tween(240), label = "verseLineProgress")

    Column(horizontalAlignment = align) {
        Text(
                text = rememberLyricText(
                        line?.let { it.text.ifBlank { INSTRUMENTAL_MARKER } } ?: INSTRUMENTAL_MARKER),
                color = color,
                fontFamily = fontFamily,
                inlineContent = svartifossNoteContent(color),
                fontSize = 17.sp,
                lineHeight = 21.sp,
                // Three. Two was chosen when the band stopped at 0.72 and the block was centred on
                // the screen, where a third row genuinely did collide with the running head; the
                // band now reaches lower and the block is centred within it, so the row it needed
                // is there. Deliberately not more: past three the block is deep enough that the
                // chord narrows faster than the extra row adds, and the neighbours start being
                // squeezed out of a face whose whole point is showing three lines at once.
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth())

        // It is a progress indicator, so it answers to the progress preferences like any other:
        // hidden when the user turns composition-owned progress off, and drawn in the resolved
        // progress colour rather than the raw accent.
        if (showProgress) {
            Box(Modifier.height(screen * 0.03f))

            Box(
                    modifier = Modifier
                            .fillMaxWidth(0.42f)
                            .height(1.dp)
                            .drawBehind {
                                drawRect(color = progressColor.copy(alpha = 0.16f))
                                drawRect(
                                        color = progressColor.copy(alpha = 0.85f),
                                        topLeft = Offset.Zero,
                                        size = Size(size.width * fraction, size.height))
                            })
        }
    }
}

/**
 * What the face shows when the track has no synced lyric - the common case, not a failure.
 *
 * Deliberately not an error message: the track set large in the same serif reads as a designed
 * title card, and the only hint that anything is missing is the accent rule being absent.
 *
 * It carries the title and artist itself, which is why the caller drops the running head while
 * this is showing - otherwise both lines appear twice on one screen.
 */
@Composable
private fun TitleCard(state: NowPlayingFaceState, accent: Color, pending: Boolean) {
    // Now the only place the metadata is drawn on this face, so it answers to the same visibility
    // rules the header did - including status text, which stays visible whatever the user chose.
    val visibility = resolveMetadataVisibility(
            title = state.title,
            artist = state.artist,
            showTitle = state.showTitle,
            showArtist = state.showArtist,
            titleIsStatus = state.titleIsStatus,
            artistIsStatus = state.artistIsStatus)

    Column(horizontalAlignment = state.blockAlignment(Alignment.CenterHorizontally)) {
        if (visibility.title) {
            Text(
                    text = state.title,
                    color = Color.White.copy(alpha = if (pending) 0.55f else 0.90f),
                    fontFamily = state.lyricFont,
                    fontSize = 19.sp,
                    lineHeight = 24.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth())
        }

        if (visibility.title && visibility.artist) {
            Box(Modifier.height(10.dp))
        }

        if (visibility.artist) {
            Text(
                    text = state.artist,
                    color = accent.copy(alpha = if (pending) 0.45f else 0.80f),
                    fontFamily = state.lyricFont,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxWithConstraintsScope.ElapsedTime(
        state: NowPlayingFaceState,
        screen: Dp,
) {
    if (!state.showTrackTime) return

    TrackTimeText(
            text = formatFaceClockTime(state.positionMs),
            state = state,
            color = Color.White.copy(alpha = 0.62f),
            fontSize = 11.sp,
            fontFamily = state.artistFont,
            letterSpacing = 0.10.em,
            modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // Closer to the floor than the 0.09 it shipped with: the strip it was
                    // floating above was the emptiest part of the composition.
                    .padding(bottom = screen * 0.065f))
}

/**
 * Ambient Verse: the current line alone, outlined on black.
 *
 * No floor glow, no hairline, no neighbours. The display refreshes about once a minute here, so a
 * three-line reel would be showing two lines that are wrong most of the time - one line, which is
 * merely late, is the honest amount of information to carry at that refresh rate.
 */
@Composable
private fun VerseAmbient(state: NowPlayingFaceState) {
    val geo = FaceGeometry.Verse.Ambient
    val tint = Color(state.ambientTint)

    BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
    ) {
        // No backdrop of its own, matching Expressive. The host owns the ambient canvas: it hides
        // its artwork layer when "Show artwork" is off, leaving black, and shows the treated cover
        // when it is on. Painting black here made those AOD preferences do nothing on this face -
        // the same mistake as the awake side, in the one place it is easiest to justify.
        val screen = maxWidth
        val inset = RoundScreenText.sideInsetFor(top = geo.BAND_TOP, bottom = geo.BAND_BOTTOM)
        val line = state.lyricLines
                .getOrNull(LyricsParser.indexAt(state.lyricLines, state.positionMs))

        Column(
                modifier = Modifier.padding(horizontal = screen * inset),
                horizontalAlignment = state.blockAlignment(Alignment.CenterHorizontally)
        ) {
            if (state.ambientShowTrackInfo && state.title.isNotEmpty()) {
                Text(
                        text = state.title.uppercase(),
                        color = tint.copy(alpha = geo.TITLE_ALPHA * state.ambientIntensity),
                        fontFamily = state.titleFont,
                        fontSize = geo.TITLE_SP.sp,
                        letterSpacing = geo.TITLE_TRACKING_EM.em,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center)
                Box(Modifier.height(geo.TITLE_TO_LINE_GAP_DP.dp))
            }

            val ambientColor = tint.copy(alpha = geo.LINE_ALPHA * state.ambientIntensity)
            Text(
                    text = rememberLyricText(
                            line?.text?.ifBlank { INSTRUMENTAL_MARKER } ?: state.artist),
                    color = ambientColor,
                    fontFamily = state.lyricFont,
                    inlineContent = svartifossNoteContent(ambientColor),
                    fontSize = geo.LINE_SP.sp,
                    lineHeight = geo.LINE_HEIGHT_SP.sp,
                    maxLines = geo.LINE_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center)
        }
    }
}

/**
 * The accent is lifted against this before use.
 *
 * A constant, and knowingly an approximation: this face *defaults* to the black backdrop, and on
 * that default it is exact. A user who puts the words over bright artwork gets a colour lifted for
 * a darker background than they actually have - still legible, since lifting only ever increases
 * separation from black, and the shading overlay above keeps the artwork itself dark enough to
 * read on. Sampling the real backdrop per frame is what AdaptiveTextContrast's opt-in artist mode
 * does; it is not worth the per-frame cost for a line that changes every few seconds.
 */
private const val BACKDROP_LUMINANCE = 0f
