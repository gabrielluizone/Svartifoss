package com.svartifoss.snfell.watch.view.face

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.svartifoss.snfell.common.AdaptiveTextContrast
import com.svartifoss.snfell.common.FaceGeometry
import com.svartifoss.snfell.common.ColorHarmony
import com.svartifoss.snfell.common.CoverShape
import com.svartifoss.snfell.common.RoundScreenText
import com.svartifoss.snfell.watch.theme.WatchTheme
import com.svartifoss.snfell.watch.view.compose.FaceClock

/**
 * Note: the track as a single line of text, the way a watch renders a message notification.
 *
 * A round cover thumbnail on top, then one centred line reading `Artist: Title` - the artist bold
 * in the theme accent, the title in whatever colour reads against the backdrop - and the track time
 * at the foot of the screen. Nothing else: no transport row, no ring, no pills.
 *
 * The backdrop defaults to plain black (`PlayerBackgroundStyle.HIDDEN`, set as this face's default)
 * because the composition is text on emptiness; that is the whole idea. It is only a *default*
 * though, so the title colour is worked out rather than hardcoded white - see [noteTitleColor].
 *
 * The cover circle is drawn from [NowPlayingFaceState.albumArt] regardless of `albumArtHidden`,
 * which is the one place this face departs from the convention the other curated layouts follow.
 * On them a small cover is decoration that a "hide artwork" background may legitimately remove;
 * here it is one of only two elements on the screen, and the face's own default backdrop is exactly
 * the artwork-hiding style - honouring the flag would leave the face permanently empty out of the box.
 *
 * Gestures live on the centre of the screen ([CenterGestureRegion]) as on the other faces with no
 * central control. Mini buttons are deliberately *not* defaulted off here, unlike Chat and Split:
 * this layout leaves the bottom band free, so the shortcut row is the natural way to reach anything.
 */
@Composable
fun NoteFace(
        state: NowPlayingFaceState,
        listener: NowPlayingFaceListener,
        coverShape: CoverShape = CoverShape.CIRCLE,
        showCover: Boolean = true
) {
    if (state.ambient) {
        NoteAmbient(state)
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screen = maxWidth
        // Shared pipeline: the host's artwork View sits below this ComposeView, and the selected
        // background composes on top of it. Not painted over - a user who picks an artwork
        // backdrop on this face should get one.
        PlayerBackgroundTreatment(state)
        PlayerShadingOverlay(state)

        CenterGestureRegion(listener, size = screen * .60f, pulseSize = screen * .36f)

        // Cover and sentence centre together as one block. Anchoring them separately was tried -
        // it pins the artwork so it cannot shift when a title wraps - but it costs a visible gap
        // between the two whenever the title fits on one line, which is most of the time, and the
        // pair reading as one object is what makes this composition work.
        Column(
                modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = screen * NOTE_TEXT_INSET),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
        ) {
            // Hiding the cover is a layout decision, not a shape - with it off the sentence alone
            // fills the block the Column already centres, rather than leaving the gap the disc
            // used to occupy.
            if (showCover) {
                CoverDisc(
                        state,
                        size = (screen * COVER_FRACTION).coerceIn(34.dp, 60.dp),
                        shape = coverShape)
                Spacer(Modifier.height(screen * .05f))
            }
            NoteLine(state, screen)
        }

        if (state.showTrackTime) {
            TrackTimeText(
                    text = "${formatFaceClockTime(state.positionMs)} / " +
                            formatFaceClockTime(state.durationMs),
                    state = state,
                    color = noteTitleColor(state).copy(alpha = .55f),
                    fontFamily = state.artistFont,
                    fontSize = 11.sp,
                    maxLines = 1,
                    modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = screen * .07f)
            )
        }

        FaceClock(
                visible = state.showClock,
                color = Color(state.clockColor),
                fontFamily = state.clockFont,
                typography = state.clockTypography)
    }
}

/** Cover diameter as a fraction of the screen - small, as in the reference: it identifies the
 *  track, the text carries it. */
private const val COVER_FRACTION = FaceGeometry.Note.COVER_FRACTION

/** Side padding for the text block. Wide, because a centred two-line sentence on a round screen
 *  loses its corners first. */
private const val NOTE_TEXT_INSET = .17f

/**
 * The album cover, cut to the user's chosen [shape]. Falls back to an accent fill before any
 * artwork arrives, so the composition never collapses to text floating alone.
 *
 * A circle by default - beside a line of text it reads as the contact photo on a message, which is
 * the composition this face is after - but the shape is the shared [CoverShape] vocabulary
 * Carousel's rail already offers, because a square sleeve is a perfectly reasonable thing to want
 * here and there was no way to ask for one.
 */
@Composable
private fun CoverDisc(
        state: NowPlayingFaceState,
        size: Dp,
        shape: CoverShape,
        modifier: Modifier = Modifier
) {
    val art = state.albumArt
    Box(
            modifier = modifier
                    .size(size)
                    .clip(shape.toComposeShape(size))
                    .background(Color(state.accentColor).copy(alpha = if (art == null) .5f else 1f)),
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
 * `Artist: Title` as one flowing sentence.
 *
 * Built as a single annotated string rather than two Text composables so it wraps like the sentence
 * it is - the title continuing on the artist's line when there is room, exactly as the reference
 * notification does. Two stacked Texts would always break after the artist, which reads as a label
 * and a value instead.
 */
@Composable
private fun NoteLine(state: NowPlayingFaceState, screen: Dp, modifier: Modifier = Modifier) {
    val showArtist = state.showArtist && state.artist.isNotEmpty()
    val showTitle = state.showTitle && state.title.isNotEmpty()
    if (!showArtist && !showTitle) return

    val titleColor = noteTitleColor(state)
    val titleSpec = state.titleTypography
    val artistSpec = state.artistTypography
    val artistColor = Color(state.artistColor).copy(alpha = artistSpec.alpha)
    // This face is one sentence, so the artist cannot take ArtistLineText - it is a span, not a
    // line. Everything the spec carries that a span can express is applied here instead; only the
    // *size* is deliberately left to the title's scale, because "Artist: Title" is set as a single
    // run and two independent sizes inside it would stop it reading as a sentence, which is the
    // whole composition. Bold is the face's design, so it survives the identity weight the way
    // AdaptiveTitleText and ArtistLineText preserve a designed value.
    val artistWeight =
            if (artistSpec.weight == 400) FontWeight.Bold else FontWeight(artistSpec.weight)
    val text = buildAnnotatedString {
        if (showArtist) {
            withStyle(SpanStyle(
                    color = artistColor,
                    fontWeight = artistWeight,
                    fontStyle = state.artistFontStyle,
                    letterSpacing = state.artistLetterSpacing,
                    fontFamily = state.artistFont)) {
                append(artistSpec.case.apply(state.artist))
            }
            if (showTitle) {
                withStyle(SpanStyle(color = artistColor, fontWeight = artistWeight)) {
                    append(":")
                }
                append(" ")
            }
        }
        if (showTitle) {
            // The title is a span here for the same reason the artist is, so like the artist it
            // carries the whole spec by hand - slant, tracking and opacity included. Only the
            // *size* is left to the run (see above): one sentence, one size.
            //
            // Each span cases its own text before appending, rather than casing the finished
            // sentence afterwards: the artist and title cases are independent settings, and a case
            // transform can change a run's character count (German ß -> SS), so casing after the
            // fact risks shifting this span's boundary into what the artist span already wrote.
            withStyle(SpanStyle(
                    color = titleColor.copy(alpha = titleColor.alpha * titleSpec.alpha),
                    fontWeight = state.titleFontWeight,
                    fontStyle = state.titleFontStyle,
                    letterSpacing = state.titleLetterSpacing,
                    fontFamily = state.titleFont)) {
                append(titleSpec.case.apply(state.title))
            }
        }
    }
    // The usable chord narrows towards the bottom of a round screen, and this block sits just below
    // centre, so ask the shared geometry instead of trusting one hand-tuned padding.
    val inset = RoundScreenText.sideInsetForLines(
            top = .52f,
            lineHeight = 19.dp / screen,
            lines = NOTE_MAX_LINES)
    // Through AdaptiveTitleText, not a bare Text: the sentence is this face's title, so the user's
    // Title text behaviour (marquee/wrap/static/shrink/smart) has to reach it like it reaches every
    // other face. It used to draw a fixed three-line wrap here, which made that setting a no-op on
    // Note alone. The typography spec is applied to the spans above rather than passed in, so the
    // already-scaled size goes in as the run's own - passing both would scale it twice. Three lines
    // is the ceiling the composition has room for, which is also the depth [inset] is measured at.
    AdaptiveTitleText(
            text = text,
            mode = state.titleTextMode,
            fontSize = titleSpec.scaled(16f).sp,
            color = titleColor,
            textAlign = TextAlign.Center,
            maxLines = NOTE_MAX_LINES,
            modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = (screen * inset - screen * NOTE_TEXT_INSET)
                            .coerceAtLeast(0.dp))
    )
}

private const val NOTE_MAX_LINES = FaceGeometry.Note.MAX_LINES

/**
 * The title colour, decided against the backdrop rather than assumed.
 *
 * The user's own title-colour preference wins first ([titleTextColor] applies it). Otherwise: with
 * the artwork hidden the backdrop is flat black, so white; with artwork showing, the album accent
 * stands in for the artwork's brightness. That is a proxy and worth naming as one - the face cannot
 * sample the bitmap behind it - but it is the right proxy: the accent is extracted *from* that
 * artwork, so a pale cover yields a pale accent and the title correctly darkens.
 *
 * On a dark backdrop the white gives way to the palette's tertiary slot, which is what makes a
 * second colour visible on this face at all: Note paints one line and nothing else, so with the
 * title fixed at white the only colour on screen was the primary. It is skipped when the slot lands
 * too close to the artist's colour to read as a second colour, the same
 * [ColorHarmony.MIN_DUOTONE_HUE_GAP] gate the progress ring's gradient uses.
 *
 * **The slot is not legible as it arrives.** Only the harmony treatments build their companions at
 * [ColorHarmony.TERTIARY_LIGHTNESS]; Expressive and Desaturated pass the cover's *raw* swatches
 * through, whose lightness is whatever the artwork happened to contain - and on a dark album that
 * is near-black, which put the title one shade off invisible against this face's black backdrop.
 * So it goes through [WatchTheme.accentForText], the same lightness floor every other album-derived
 * text on the watch is held to. Hue and saturation survive, so the line still reads as a colour out
 * of the cover rather than as white.
 *
 * A light backdrop keeps black: this is a lifted, light tone by construction and would vanish there.
 */
@Composable
private fun noteTitleColor(state: NowPlayingFaceState): Color {
    val backdropIsDark = state.albumArtHidden ||
            !AdaptiveTextContrast.prefersDarkText(state.accentColor)
    if (!backdropIsDark) return titleTextColor(state, Color.Black.copy(alpha = .87f))
    val companion = state.tertiaryAccentColor
            .takeIf {
                ColorHarmony.hueDistance(it, state.artistColor) >= ColorHarmony.MIN_DUOTONE_HUE_GAP
            }
            ?.let { WatchTheme.accentForText(it) }
    return titleTextColor(state, companion?.let(::Color) ?: Color.White)
}

/**
 * Ambient variant: the same sentence, outlined, with no cover.
 *
 * The cover is dropped rather than outlined - a filled circle is the most expensive thing an AOD
 * can draw, and its outline says nothing the text does not.
 */
@Composable
private fun NoteAmbient(state: NowPlayingFaceState) {
    val tint = Color(state.ambientTint).copy(alpha = state.ambientIntensity.coerceIn(.2f, 1f))
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val screen = maxWidth
        if (!state.ambientShowTrackInfo) return@BoxWithConstraints
        Column(
                modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = screen * .18f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
        ) {
            val text = buildAnnotatedString {
                if (state.artist.isNotEmpty()) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(state.artist) }
                    if (state.title.isNotEmpty()) append(": ")
                }
                if (state.title.isNotEmpty()) append(state.title)
            }
            Text(
                    text = text,
                    color = tint,
                    fontFamily = state.titleFont,
                    fontSize = 15.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
            )
        }
    }
}
