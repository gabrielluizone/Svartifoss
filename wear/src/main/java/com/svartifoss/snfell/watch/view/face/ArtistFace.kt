package com.svartifoss.snfell.watch.view.face

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.svartifoss.snfell.common.FaceGeometry
import com.svartifoss.snfell.common.RoundScreenText
import com.svartifoss.snfell.common.TextBlockPosition
import com.svartifoss.snfell.R
import com.svartifoss.snfell.watch.view.compose.FaceClock

/**
 * Artist: the performer's own picture, their name first, the track underneath.
 *
 * Every other face here is built around the record sleeve and treats the credit line as a caption.
 * This one inverts that. The backdrop is a photograph of the artist, the largest type on the screen
 * is their name with the playing app's mark in front of it, and the track sits a step below at what
 * would elsewhere be the artist's size:
 *
 * ```
 *   > Artist Name
 *     Track Title
 * ```
 *
 * ## Where the picture comes from, and why the face does not draw it
 *
 * It never asks for an artist picture and never paints one. The host substitutes it for the album
 * cover in the *one* artwork pipeline before anything here runs (see
 * `MainActivity.resolveBackdropArtwork`), which is what makes the whole shared apparatus apply to
 * it: the ~20 background styles, the artwork filters, the shading stack, the accent floor, and the
 * album-accent palette that every colour on this face resolves through - including the clock, which
 * is therefore tinted from the picture actually behind it rather than from a sleeve nobody can see.
 * Painting the photograph here would have cost all of that; it is the mistake [SplitFace] documents
 * as its own deliberate exception, and this face has no reason to repeat it.
 *
 * When the phone finds no picture for an artist the cover arrives instead, and the face becomes a
 * different typographic treatment of the same screen. That is the honest fallback: "this performer
 * has no photograph" is not an error state, and there is nothing useful to say about it on a watch.
 *
 * ## Placement
 *
 * The block ships pinned to the leading edge and grounded at the foot of the screen (see
 * `FaceScopedPreferences.ARTIST_DEFAULTS`) - a name reads as a name when it starts where the eye
 * already is, and a portrait's subject is usually above the lower third. Both are ordinary
 * [com.svartifoss.snfell.common.TextBlockAlign]/[TextBlockPosition] values rather than fixtures in
 * this file, so centring the block or lifting it to the top is one tap and the face does not fight
 * it.
 *
 * ## Legibility is a setting, not something this face paints
 *
 * An early version drew its own dark gradient behind the text. It is gone: the app already has a
 * whole ordered stack for making artwork legible ([PlayerBackgroundTreatment] walks it, and the
 * shading layers, the dim strength and the background washes are all controls the user owns), so a
 * scrim welded into one composition was a second, unremovable answer to a question already
 * answered - and on most covers it simply read as a smear. A photograph that needs darkening is
 * darkened the same way it is on every other face.
 */
@Composable
fun ArtistFace(state: NowPlayingFaceState, listener: NowPlayingFaceListener) {
    if (state.ambient) {
        ArtistAmbient(state)
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screen = maxWidth

        PlayerBackgroundTreatment(state)

        // A centred region, never the whole screen. `size` is the side of a centred square that
        // *takes the taps*, so passing `screen` here does not mean "tapping anywhere toggles
        // playback" - it means this face swallows every touch on the display, and the quadrant
        // taps, the full-screen swipes and the mini-button row underneath all stop working. The
        // fraction is what leaves the edges to the shared input layers, and every face in the
        // collection picks one for that reason (Frame .40, Metadata .50, Verse .52, Chat .52,
        // Note .52). This face has no transport control of its own, so the region is generous -
        // but it stops clear of the bottom band the mini buttons occupy and of the four edges.
        CenterGestureRegion(
                listener = listener,
                size = screen * FaceGeometry.Artist.CENTER_REGION_FRACTION,
                pulseSize = screen * FaceGeometry.Artist.CENTER_PULSE_FRACTION,
                state = state)

        if (state.showTitle || state.showArtist || state.showTrackTime) {
            ArtistTextBlock(state, screen)
        }

        FaceClock(
                visible = state.showClock,
                color = Color(state.clockColor),
                fontFamily = state.clockFont,
                typography = state.clockTypography
        )
    }
}

/**
 * The name, the track and the elapsed time, as one movable block.
 *
 * ## Keeping it on the glass
 *
 * The screen is a circle, so the usable width collapses towards the top and bottom - and this
 * block is grounded at the foot of the screen by default and can be moved to the very top. A fixed
 * side padding is therefore only ever correct for the one depth and the one line count its author
 * happened to look at: the moment the artist's name wrapped, the second line sat lower, where the
 * chord is narrower, and ran into the bezel. [RoundScreenText] measures the real chord instead,
 * and the block's depth is recomputed from the placement the user chose rather than assumed, since
 * the whole point of those controls is that this block does not stay where it was drawn.
 *
 * The face's own [FaceGeometry.Artist.SIDE_PADDING_FRACTION] survives as a *floor*: near the centre
 * the circle constrains nothing, and the composition still wants its margin there.
 */
@Composable
private fun BoxScope.ArtistTextBlock(state: NowPlayingFaceState, screen: Dp) {
    val edge = screen * FaceGeometry.Artist.EDGE_PADDING_FRACTION
    // The face owns its real chord calculation, but a top override must clear the optional clock
    // before that calculation chooses its top edge. Otherwise the name is safely inside the glass
    // while still painting directly underneath the clock.
    val topEdge = maxOf(edge, state.blockSafeVerticalInset(screen))
    val nameColor = artistOrStatusColor(state, 1f)

    // sp is treated as dp here, as everywhere else in the faces: Wear OS has no user font scaling,
    // and the phone preview measures the same way against its 192dp reference watch.
    val nameLineFraction = FaceGeometry.Artist.NAME_LINE_HEIGHT_SP.dp / screen
    val trackFraction = if (state.showTitle) {
        (FaceGeometry.Artist.TRACK_TOP_PADDING_DP + FaceGeometry.Artist.TRACK_LINE_HEIGHT_SP).dp / screen
    } else 0f
    val timeFraction = if (state.showTrackTime) {
        (FaceGeometry.Artist.TRACK_TIME_TOP_PADDING_DP +
                FaceGeometry.Artist.TRACK_TIME_LINE_HEIGHT_SP).dp / screen
    } else 0f

    val edgeFraction = FaceGeometry.Artist.EDGE_PADDING_FRACTION
    val topEdgeFraction = topEdge / screen
    // Where the block's own top and bottom edges land, in screen-height fractions measured from the
    // top - which is what RoundScreenText's depth means. Resolved per placement because the block
    // moves; taking the bottom-anchored case for all three would inset a top-anchored block by the
    // chord of a band it is nowhere near.
    // One line, always: the artist's Text behaviour offers only the single-line modes (static,
    // marquee, shrink), because every face reserves one line for this credit. That is what removed
    // the measure-then-re-inset settle this block used to need.
    val blockFraction = nameLineFraction + trackFraction + timeFraction
    val (blockTop, blockBottom) = when (state.textBlockPosition) {
        TextBlockPosition.TOP -> topEdgeFraction to (topEdgeFraction + blockFraction)
        TextBlockPosition.MIDDLE -> (0.5f - blockFraction / 2f) to (0.5f + blockFraction / 2f)
        // BOTTOM and FOLLOW alike: the face is grounded by design.
        else -> (1f - edgeFraction - blockFraction) to (1f - edgeFraction)
    }
    val chordInset = RoundScreenText.sideInsetFor(top = blockTop, bottom = blockBottom)
    val side = screen * maxOf(FaceGeometry.Artist.SIDE_PADDING_FRACTION, chordInset)

    Box(
            Modifier
                    .align(state.blockPlacement(Alignment.BottomStart))
                    .fillMaxWidth()
                    .padding(
                            start = side,
                            end = side,
                            top = if (state.textBlockPosition == TextBlockPosition.TOP) topEdge else 0.dp,
                            bottom = if (state.textBlockPosition == TextBlockPosition.BOTTOM ||
                                    state.textBlockPosition == TextBlockPosition.FOLLOW) edge else 0.dp
                    )
    ) {
        Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = state.blockAlignment(Alignment.Start)
        ) {
            if (state.showArtist) {
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = state.blockArrangement(Arrangement.Start)
                ) {
                    // The mark sits *in front of the name* rather than beside the track, which is
                    // the one arrangement decision this face makes about it: the name is the
                    // subject here, so the glyph reads as the bullet of a list item instead of as a
                    // badge on a caption. SourceIconGlyph draws its own trailing gap.
                    SourceIconGlyph(
                            state,
                            FaceGeometry.Artist.SOURCE_ICON_SIZE_DP.dp,
                            nameColor
                    )
                    // The artist goes through AdaptiveTitleText rather than ArtistLineText because
                    // on this face it is the line that can be too long for the screen, and it
                    // therefore needs the user's title-overflow behaviour (marquee / wrap /
                    // shrink); ArtistLineText has none, being a single line everywhere else. Its
                    // typography, colour and effects still come from the *artist* controls - which
                    // line is biggest is this face's composition, not a relabelling of the
                    // settings, and an artist who set their artist colour must see it here.
                    // The shared artist helper, like every other face - it owns the overflow
                    // modes now, so this face needs no machinery of its own for the one line it
                    // makes biggest. Reading the *title's* mode here is what made a long name
                    // wrap onto a second line: that key defaults to `smart`, whose cascade ends
                    // in a wrap, where the artist line has always been one line.
                    ArtistLineText(
                            text = artistOrStatus(state),
                            state = state,
                            color = nameColor,
                            fontSize = FaceGeometry.Artist.NAME_SP.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = FaceGeometry.Artist.NAME_LINE_HEIGHT_SP.sp,
                            textAlign = TextAlign.Start
                    )
                }
            }
            if (state.showTitle) {
                TitleLineText(
                        text = state.title,
                        state = state,
                        color = titleTextColor(state, Color.White.copy(alpha = .78f)),
                        fontSize = FaceGeometry.Artist.TRACK_SP.sp,
                        modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = FaceGeometry.Artist.TRACK_TOP_PADDING_DP.dp),
                        lineHeight = FaceGeometry.Artist.TRACK_LINE_HEIGHT_SP.sp,
                        textAlign = state.blockTextAlign(TextAlign.Start),
                        maxLines = 2
                )
            }
            if (state.showTrackTime) {
                TrackTimeText(
                        stringResource(
                                R.string.playback_time_format,
                                formatFaceClockTime(state.positionMs),
                                formatFaceClockTime(state.durationMs)
                        ),
                        state = state,
                        color = Color.White.copy(alpha = .62f),
                        // The family this face *authored* the readout in, which is what
                        // TrackTimeText falls back to when the playback-time font key is `follow`
                        // (its default). Passing nothing leaves that fallback null and the time
                        // renders in the system font, ignoring the chosen typeface entirely - a
                        // dedicated playback-time font still overrides this, as it should.
                        fontFamily = state.titleFont,
                        fontSize = FaceGeometry.Artist.TRACK_TIME_SP.sp,
                        modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = FaceGeometry.Artist.TRACK_TIME_TOP_PADDING_DP.dp),
                        lineHeight = FaceGeometry.Artist.TRACK_TIME_LINE_HEIGHT_SP.sp,
                        textAlign = state.blockTextAlign(TextAlign.Start)
                )
            }
        }
    }
}

/**
 * The always-on variant: the two lines on black, with no picture.
 *
 * It drops the photograph rather than dimming it, the same call every other ambient variant here
 * makes about the cover - a full-screen image is the worst thing to leave lit for hours on an AMOLED
 * panel, and the name is what this face is for. It reads `ambientShowTrackInfo` rather than the
 * awake `showTitle`/`showArtist`: AOD has its own element switches, and a face consulting the awake
 * ones makes the AOD controls silently do nothing on that one style - the failure
 * `AmbientFaceContractTest` sweeps for.
 */
@Composable
private fun ArtistAmbient(state: NowPlayingFaceState) {
    val geo = FaceGeometry.Artist.Ambient
    val tint = Color(state.ambientTint).copy(alpha = state.ambientIntensity.coerceIn(.2f, 1f))
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        if (!state.ambientShowTrackInfo) return@BoxWithConstraints
        val screen = maxWidth
        // The same chord measurement the awake block makes, and needed just as much: this variant
        // is grounded too, and two lines low on a round screen is exactly where a fixed side
        // padding runs into the bezel. Two lines is the whole block here, so there is nothing to
        // feed back from a layout pass - the height is known up front.
        val edgeFraction = FaceGeometry.Artist.EDGE_PADDING_FRACTION
        val blockFraction = (geo.NAME_SP + geo.TRACK_SP * 1.6f).dp / screen
        val (blockTop, blockBottom) = when (state.textBlockPosition) {
            TextBlockPosition.TOP -> edgeFraction to (edgeFraction + blockFraction)
            TextBlockPosition.MIDDLE -> (0.5f - blockFraction / 2f) to (0.5f + blockFraction / 2f)
            else -> (1f - edgeFraction - blockFraction) to (1f - edgeFraction)
        }
        val side = maxOf(
                FaceGeometry.Artist.SIDE_PADDING_FRACTION,
                RoundScreenText.sideInsetFor(top = blockTop, bottom = blockBottom))
        Column(
                Modifier
                        .align(state.blockPlacement(Alignment.BottomStart))
                        .fillMaxWidth()
                        .padding(
                                horizontal = screen * side,
                                vertical = screen * edgeFraction
                        ),
                horizontalAlignment = state.blockAlignment(Alignment.Start)
        ) {
            Text(
                    text = artistOrStatus(state),
                    color = tint.copy(alpha = tint.alpha * geo.NAME_ALPHA),
                    fontFamily = state.artistFont,
                    fontSize = geo.NAME_SP.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = state.blockTextAlign(TextAlign.Start),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
            )
            Text(
                    text = state.title,
                    color = tint.copy(alpha = tint.alpha * geo.TRACK_ALPHA),
                    fontFamily = state.titleFont,
                    fontSize = geo.TRACK_SP.sp,
                    textAlign = state.blockTextAlign(TextAlign.Start),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
            )
        }
    }
}
