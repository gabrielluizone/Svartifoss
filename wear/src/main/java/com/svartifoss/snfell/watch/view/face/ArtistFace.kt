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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.svartifoss.snfell.common.TitleBand
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

        // The bottom band the mini-button row would otherwise take - the text block above stops
        // short of it (see ArtistTextBlock), which is what lets this face carry the pill at all.
        if (state.showUpNextPill) {
            AwakeUpNextPill(
                    state = state,
                    screen = screen,
                    onClick = listener::onQueueTap,
                    modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = screen * .07f)
            )
        }
    }
}

/**
 * The name, the track and the elapsed time, as one movable block.
 *
 * ## Keeping it on the glass, and off the bottom band
 *
 * The screen is a circle, so the usable width collapses towards the top and bottom - and this
 * block is grounded at the foot of the screen by default and can be moved to the very top. A fixed
 * side padding is therefore only ever correct for the one depth and the one line count its author
 * happened to look at. [RoundScreenText] measures the real chord instead, at the depth the block
 * really reaches.
 *
 * "Really" is the part that went wrong first. The block's height used to be read off the 100%
 * design - one name line and one track line - whatever the user had done to the text, so a title
 * at 220% that wrapped to two lines sat far deeper than the chord was measured at; and the foot of
 * the block was the screen's edge margin, so the mini-button row and the Up Next pill (which share
 * the bottom band) were painted straight over it. Both now come from what is drawn: the line
 * heights at the user's sizes, the title's settled line count, and a floor that stops at the top of
 * whatever occupies the bottom band ([NowPlayingFaceState.miniButtonsTopFraction]).
 *
 * The track is the face's *title* and gets the user's title behaviour (marquee, wrap, shrink,
 * smart) like every other title, up to the lines its band holds. It used to be a fixed two-line
 * [TitleLineText], so a theme set to scroll its title wrapped it here instead - and with the leading
 * fixed at the 100% design, a large wrapped title drew its second line over its first.
 *
 * The face's own [FaceGeometry.Artist.SIDE_PADDING_FRACTION] survives as a *floor* on the inset:
 * near the centre the circle constrains nothing, and the composition still wants its margin there.
 */
@Composable
private fun BoxScope.ArtistTextBlock(state: NowPlayingFaceState, screen: Dp) {
    val geo = FaceGeometry.Artist
    val screenDp = screen.value
    val edgeFraction = geo.EDGE_PADDING_FRACTION
    // Above: the clock's clearance when it shows, the face's own margin when it does not.
    val topFraction = maxOf(edgeFraction, TitleBand.movedTop(state.showClock))
    // Below: the face's margin, or the top of the mini-button row / Up Next pill when either is
    // in the bottom band. The host reports 1 when the band is empty.
    val floor = TitleBand.effectiveFloor(1f - edgeFraction, state.miniButtonsTopFraction)
    val position = state.textBlockPosition
    val band = when (position) {
        TextBlockPosition.MIDDLE -> TitleBand.centredBand(floor)
        // TOP, BOTTOM and FOLLOW (grounded by design): between the clearance above and the floor.
        else -> TitleBand.movedBand(topFraction, floor)
    }

    // Fixed parts of the block that are neither the title nor the artist line, in dp.
    val trackPadDp = if (state.showTitle) geo.TRACK_TOP_PADDING_DP else 0f
    val timeDp = if (state.showTrackTime) {
        geo.TRACK_TIME_TOP_PADDING_DP + geo.TRACK_TIME_LINE_HEIGHT_SP * state.trackTimeTypography.scale
    } else 0f
    val nameLineDp = if (state.showArtist) geo.NAME_LINE_HEIGHT_SP else 0f
    val titleLineDp = if (state.showTitle) geo.TRACK_LINE_HEIGHT_SP else 0f
    val textBandDp = (screenDp * band - trackPadDp - timeDp).coerceAtLeast(0f)
    // Past the sizes the band holds, the name and the track shrink together rather than either
    // being cut - TitleBand's rule, anchored at the 100% design so default sizes never change.
    val text = state.withTextFit(TitleBand.fitScale(
            bandDp = textBandDp,
            titleLineDp = titleLineDp,
            titleScale = state.titleTypography.scale,
            artistRowDp = nameLineDp,
            artistScale = state.artistTypography.scale))
    val titleScale = text.titleTypography.scale
    val artistScale = text.artistTypography.scale
    val titleCeiling = TitleBand.lineCeiling(
            bandDp = textBandDp,
            titleLineDp = geo.TRACK_LINE_HEIGHT_SP,
            titleScale = titleScale,
            artistRowDp = nameLineDp,
            artistScale = artistScale)

    // How many lines the title settled on, which decides how deep the block reaches. Only ever
    // grows for one title at one size: a deeper block is narrower, which can add a line and never
    // removes one, so the settle converges instead of oscillating.
    var titleLines by remember(state.title, titleScale, state.titleTextMode, titleCeiling) {
        mutableIntStateOf(1)
    }
    val nameColor = artistOrStatusColor(text, 1f)

    // sp is treated as dp here, as everywhere else in the faces: Wear OS has no user font scaling,
    // and the phone preview measures the same way against its 192dp reference watch.
    val blockDp = nameLineDp * artistScale +
            (if (state.showTitle) trackPadDp + titleLineDp * titleScale * titleLines else 0f) +
            timeDp
    val blockFraction = blockDp / screenDp
    // Where the block's own top and bottom edges land, in screen-height fractions from the top -
    // which is what RoundScreenText's depth means. Resolved per placement because the block moves.
    val (blockTop, blockBottom) = when (position) {
        TextBlockPosition.TOP -> topFraction to (topFraction + blockFraction)
        TextBlockPosition.MIDDLE -> (0.5f - blockFraction / 2f) to (0.5f + blockFraction / 2f)
        // BOTTOM and FOLLOW alike: the face is grounded by design.
        else -> (floor - blockFraction) to floor
    }
    val chordInset = RoundScreenText.sideInsetFor(top = blockTop, bottom = blockBottom)
    val side = screen * maxOf(geo.SIDE_PADDING_FRACTION, chordInset)

    Box(
            Modifier
                    .align(state.blockPlacement(Alignment.BottomStart))
                    .fillMaxWidth()
                    .padding(
                            start = side,
                            end = side,
                            top = if (position == TextBlockPosition.TOP) screen * topFraction else 0.dp,
                            bottom = if (position == TextBlockPosition.BOTTOM ||
                                    position == TextBlockPosition.FOLLOW) {
                                screen * (1f - floor)
                            } else 0.dp
                    )
                    // The safety net under the fit and the ceiling: a typeface taller than the
                    // reference line metrics still cannot push the block into the bottom band.
                    .heightIn(max = screen * band)
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
                    SourceIconGlyph(text, geo.SOURCE_ICON_SIZE_DP.dp, nameColor)
                    // The shared artist helper, like every other face - it owns the overflow
                    // modes, so this face needs no machinery of its own for the one line it makes
                    // biggest. Reading the *title's* mode here is what once made a long name wrap
                    // onto a second line: that key defaults to `smart`, whose cascade ends in a
                    // wrap, where the artist line has always been one line.
                    ArtistLineText(
                            text = artistOrStatus(text),
                            state = text,
                            color = nameColor,
                            fontSize = geo.NAME_SP.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = geo.NAME_LINE_HEIGHT_SP.sp,
                            textAlign = TextAlign.Start
                    )
                }
            }
            if (state.showTitle) {
                AdaptiveTitleText(
                        text = text.title,
                        mode = text.titleTextMode,
                        state = text,
                        typography = text.titleTypography,
                        color = titleTextColor(text, Color.White.copy(alpha = .78f)),
                        fontSize = geo.TRACK_SP.sp,
                        fontStyle = text.titleFontStyle,
                        fontFamily = text.titleFont,
                        lineHeight = geo.TRACK_LINE_HEIGHT_SP.sp,
                        textAlign = TextAlign.Start,
                        maxLines = titleCeiling,
                        onLineCount = { lines -> if (lines > titleLines) titleLines = lines },
                        modifier = Modifier
                                // Measured after the name and the time, so a block that is
                                // still too tall gives up title lines, never the name.
                                .weight(1f, fill = false)
                                .fillMaxWidth()
                                .padding(top = geo.TRACK_TOP_PADDING_DP.dp)
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
                        fontSize = geo.TRACK_TIME_SP.sp,
                        modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = geo.TRACK_TIME_TOP_PADDING_DP.dp),
                        lineHeight = geo.TRACK_TIME_LINE_HEIGHT_SP.sp,
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
