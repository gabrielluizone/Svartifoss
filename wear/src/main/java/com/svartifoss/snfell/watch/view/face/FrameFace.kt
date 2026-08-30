package com.svartifoss.snfell.watch.view.face

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.svartifoss.snfell.common.AdaptiveTextContrast
import com.svartifoss.snfell.common.FaceGeometry
import com.svartifoss.snfell.common.PaletteTransforms
import com.svartifoss.snfell.watch.theme.GoogleSansFamily
import com.svartifoss.snfell.watch.theme.WatchTheme
import com.svartifoss.snfell.watch.view.compose.FaceClock

/**
 * Frame: a rounded tonal card that takes its hues from the active player palette and holds the
 * artist label, title and a wide crop of the current cover.
 */
@Composable
fun FrameFace(state: NowPlayingFaceState, listener: NowPlayingFaceListener) {
    if (state.ambient) {
        FrameAmbient(state)
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screen = maxWidth
        PlayerBackgroundTreatment(state)
        PlayerShadingOverlay(state)

        FrameCard(state, screen)

        // The card is intentionally visual-only; this shared region keeps centre tap, double tap
        // and long press available without adding a conflicting play control to the reference.
        // It goes *after* the card, not before: the card is opaque, so a region underneath it drew
        // its confirmation into the back of the surface and every tap looked like a miss. Nothing
        // inside the card is clickable, so there is nothing here for the region to swallow.
        CenterGestureRegion(
                listener,
                size = screen * .68f,
                pulseSize = screen * .46f,
                state = state)

        FaceClock(
                visible = state.showClock,
                color = Color(state.clockColor),
                fontFamily = state.clockFont,
                typography = state.clockTypography)
    }
}

@Composable
private fun BoxWithConstraintsScope.FrameCard(state: NowPlayingFaceState, screen: Dp) {
    val cardWidth = screen * (1f - FaceGeometry.Frame.CARD_INSET_FRACTION * 2f)
    val cardHeight = screen *
            (FaceGeometry.Frame.CARD_BOTTOM_FRACTION - FaceGeometry.Frame.CARD_TOP_FRACTION)
    // Corner fractions are relative to the actual card/artwork window, matching the Canvas
    // preview. Using the full dial here made the watch card much rounder than its preview.
    val cardShape = RoundedCornerShape(
            minOf(cardWidth, cardHeight) * FaceGeometry.Frame.CARD_CORNER_FRACTION)
    val contentInset = screen * FaceGeometry.Frame.CONTENT_INSET_FRACTION
    // A dark tonal transform of the player's resolved primary accent keeps the card readable
    // without disconnecting Frame from the music currently playing.
    val cardColor = Color(PaletteTransforms.tunedFaceColor(
            state.accentColor, .16f, .42f))
    Box(
            modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = screen * FaceGeometry.Frame.CARD_TOP_FRACTION)
                    .size(width = cardWidth, height = cardHeight)
                    .clip(cardShape)
                    .background(cardColor)
    ) {
        FrameHeaderRow(state, screen, contentInset)
        if (state.showTitle && state.title.isNotBlank()) {
            // The title lives in a fixed band between the chip and the cover, and the band is
            // *bounded*. The title mode is the user's, and "wrap5" or a two-line "smart" title
            // would otherwise grow downward into artwork that is positioned absolutely - the card
            // is not a Column, so nothing below it moves out of the way.
            Box(
                    modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(
                                    top = screen * (FaceGeometry.Frame.TITLE_TOP_FRACTION -
                                            FaceGeometry.Frame.CARD_TOP_FRACTION),
                                    start = contentInset,
                                    end = contentInset)
                            .fillMaxWidth()
                            .height(screen * (FaceGeometry.Frame.ARTWORK_TOP_FRACTION -
                                    FaceGeometry.Frame.TITLE_TOP_FRACTION))
                            .clipToBounds()
            ) {
                AdaptiveTitleText(
                        text = state.title,
                        mode = state.titleTextMode,
                        color = titleTextColor(state, Color.White),
                        fontSize = FaceGeometry.Frame.TITLE_TEXT_SIZE_SP.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = state.titleFont,
                        typography = state.titleTypography,
                        minFontSize = FaceGeometry.Frame.TITLE_MIN_TEXT_SIZE_SP.sp,
                        maxLines = FaceGeometry.Frame.TITLE_MAX_LINES,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.align(Alignment.TopStart).fillMaxWidth())
            }
        }
        FrameArtwork(state, screen, contentInset)
    }
}

/**
 * The card's header: the artist chip, and the elapsed/total readout right-aligned beside it.
 *
 * The time shares this row rather than getting one of its own because it is the only band in the
 * card with room to spare - the chip is as wide as an artist name and no wider - and because a
 * timestamp opposite the sender is what the notification card this face is built after does. It
 * was simply absent before, so "Track time display" was a setting with nothing to act on here.
 */
@Composable
private fun BoxScope.FrameHeaderRow(state: NowPlayingFaceState, screen: Dp, contentInset: Dp) {
    val showChip = state.showArtist && state.artist.isNotBlank()
    if (!showChip && !state.showTrackTime) return
    Row(
            modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                            top = screen * (FaceGeometry.Frame.ART_TOP_FRACTION -
                                    FaceGeometry.Frame.CARD_TOP_FRACTION),
                            start = contentInset,
                            end = contentInset)
                    .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
    ) {
        if (showChip) FrameArtistChip(state)
        // Always present, so the readout stays on the right edge whether or not the chip is drawn.
        Spacer(Modifier.weight(1f))
        if (state.showTrackTime) {
            TrackTimeText(
                    text = "${formatFaceClockTime(state.positionMs)} / " +
                            formatFaceClockTime(state.durationMs),
                    state = state,
                    // The card is a dark tonal surface, so this is the one label here that reads
                    // against the card itself rather than against a filled chip.
                    color = Color.White.copy(alpha = .62f),
                    fontFamily = state.artistFont,
                    fontSize = FaceGeometry.Frame.TRACK_TIME_TEXT_SIZE_SP.sp,
                    textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun FrameArtistChip(state: NowPlayingFaceState) {
    // The chip is player chrome, not artist-text chrome: an artist custom-colour preference must
    // not pin this face to a stale teal. Lift the live primary accent for a reliable on-chip pair.
    val fillArgb = WatchTheme.accentForSurface(state.accentColor)
    val fill = Color(fillArgb)
    val ink = if (AdaptiveTextContrast.prefersDarkText(fillArgb)) Color.Black else Color.White
    Row(
            modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(fill)
                    .padding(
                            horizontal = FaceGeometry.Frame.ARTIST_HORIZONTAL_PADDING_DP.dp,
                            vertical = FaceGeometry.Frame.ARTIST_VERTICAL_PADDING_DP.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        SourceIconGlyph(
                state = state,
                size = FaceGeometry.Frame.ARTIST_ICON_SIZE_DP.dp,
                tint = ink)
        ArtistLineText(
                text = state.artist,
                state = state,
                color = ink,
                fontSize = FaceGeometry.Frame.ARTIST_TEXT_SIZE_SP.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start)
    }
}

@Composable
private fun BoxScope.FrameArtwork(state: NowPlayingFaceState, screen: Dp, contentInset: Dp) {
    val cardWidth = screen * (1f - FaceGeometry.Frame.CARD_INSET_FRACTION * 2f)
    val width = cardWidth - contentInset * 2f
    val height = screen *
            (FaceGeometry.Frame.ARTWORK_BOTTOM_FRACTION - FaceGeometry.Frame.ARTWORK_TOP_FRACTION)
    val shape = RoundedCornerShape(screen * FaceGeometry.Frame.artworkCornerFraction())
    Box(
            modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = screen * (FaceGeometry.Frame.ARTWORK_TOP_FRACTION -
                            FaceGeometry.Frame.CARD_TOP_FRACTION))
                    .size(width = width, height = height)
                    .clip(shape)
                    .background(Color(state.accentColor).copy(alpha = .55f)),
            contentAlignment = Alignment.Center
    ) {
        // Frame's artwork is authored inside its card, just as Note's cover disc is. HIDDEN is
        // the default *backdrop* for this face, not an instruction to remove the card's subject.
        val art = state.albumArt
        if (art != null) {
            val grayscale = if (state.albumArtGrayscale) {
                ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
            } else {
                null
            }
            Image(
                    painter = BitmapPainter(art),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = grayscale,
                    modifier = Modifier.fillMaxSize())
        } else {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Brush.linearGradient(listOf(
                        Color(state.secondaryAccentColor).copy(alpha = .72f),
                        Color(state.accentColor).copy(alpha = .42f))))
            }
        }
        FrameProgress(state)
    }
}

/**
 * The played fraction, along the cover's own bottom edge.
 *
 * Frame's shipped default turns the host's edge arc off, as every self-composed face does, which
 * left it with no progress indication at all and the "Progress ring" controls with nothing to act
 * on. The card has no spare row for a bar, and the cover is the one element wide enough to carry
 * one - the same place a video thumbnail puts it. It sits inside the artwork's clip, so it takes
 * the cover's rounded corners for free.
 */
@Composable
private fun BoxScope.FrameProgress(state: NowPlayingFaceState) {
    if (!state.showInternalProgress) return
    val animated by animateFloatAsState(
            targetValue = state.progress.coerceIn(0f, 1f),
            animationSpec = tween(600, easing = LinearEasing),
            label = "frameProgress")
    Canvas(
            modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(FaceGeometry.Frame.PROGRESS_THICKNESS_DP.dp)
    ) {
        // Dark rather than the usual translucent white: this track lies on the artwork, and a pale
        // cover is exactly the case where a white track disappears and the bar stops reading as a
        // bar at all.
        drawRect(Color.Black.copy(alpha = .42f))
        if (animated > 0f) {
            drawRect(
                    color = Color(state.progressColor),
                    size = Size(size.width * animated, size.height))
        }
    }
}

/** AOD keeps the card's hierarchy as outlines and text, with no filled cover or tonal surface. */
@Composable
private fun FrameAmbient(state: NowPlayingFaceState) {
    val tint = Color(state.ambientTint)
    val intensity = state.ambientIntensity.coerceIn(.2f, 1f)
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val screen = maxWidth
        Canvas(Modifier.fillMaxSize()) {
            val cardLeft = size.width * FaceGeometry.Frame.CARD_INSET_FRACTION
            val cardTop = size.height * FaceGeometry.Frame.CARD_TOP_FRACTION
            val cardWidth = size.width * (1f - FaceGeometry.Frame.CARD_INSET_FRACTION * 2f)
            val cardHeight = size.height *
                    (FaceGeometry.Frame.CARD_BOTTOM_FRACTION - FaceGeometry.Frame.CARD_TOP_FRACTION)
            val cardCorner = minOf(cardWidth, cardHeight) *
                    FaceGeometry.Frame.CARD_CORNER_FRACTION
            val stroke = Stroke(1.dp.toPx())
            drawRoundRect(
                    color = tint.copy(alpha = .66f * intensity),
                    topLeft = Offset(cardLeft, cardTop),
                    size = Size(cardWidth, cardHeight),
                    cornerRadius = CornerRadius(cardCorner, cardCorner),
                    style = stroke)

            val artLeft = size.width * (FaceGeometry.Frame.CARD_INSET_FRACTION +
                    FaceGeometry.Frame.CONTENT_INSET_FRACTION)
            val artTop = size.height * FaceGeometry.Frame.ARTWORK_TOP_FRACTION
            val artWidth = size.width * (1f - FaceGeometry.Frame.CARD_INSET_FRACTION * 2f -
                    FaceGeometry.Frame.CONTENT_INSET_FRACTION * 2f)
            val artHeight = size.height *
                    (FaceGeometry.Frame.ARTWORK_BOTTOM_FRACTION - FaceGeometry.Frame.ARTWORK_TOP_FRACTION)
            val artCorner = size.width * FaceGeometry.Frame.artworkCornerFraction()
            drawRoundRect(
                    color = tint.copy(alpha = .38f * intensity),
                    topLeft = Offset(artLeft, artTop),
                    size = Size(artWidth, artHeight),
                    cornerRadius = CornerRadius(artCorner, artCorner),
                    style = stroke)
        }
        if (state.ambientShowTrackInfo) {
            if (state.showArtist && state.artist.isNotBlank()) {
                Row(
                        modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(
                                        top = screen * FaceGeometry.Frame.ART_TOP_FRACTION,
                                        start = screen * (FaceGeometry.Frame.CARD_INSET_FRACTION +
                                                FaceGeometry.Frame.CONTENT_INSET_FRACTION),
                                        end = screen * (FaceGeometry.Frame.CARD_INSET_FRACTION +
                                                FaceGeometry.Frame.CONTENT_INSET_FRACTION))
                                .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    AmbientSourceIconGlyph(state = state, size = 10.dp, tint = tint.copy(
                            alpha = .62f * intensity))
                    Text(
                            text = state.artist,
                            color = tint.copy(alpha = .62f * intensity),
                            fontFamily = GoogleSansFamily,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                }
            }
            if (state.showTitle && state.title.isNotBlank()) {
                Text(
                        text = state.title,
                        color = tint.copy(alpha = .88f * intensity),
                        fontFamily = GoogleSansFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Start,
                        modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(
                                        top = screen * FaceGeometry.Frame.TITLE_TOP_FRACTION,
                                        start = screen * (FaceGeometry.Frame.CARD_INSET_FRACTION +
                                                FaceGeometry.Frame.CONTENT_INSET_FRACTION),
                                        end = screen * (FaceGeometry.Frame.CARD_INSET_FRACTION +
                                                FaceGeometry.Frame.CONTENT_INSET_FRACTION))
                                .fillMaxWidth())
            }
        }
    }
}
