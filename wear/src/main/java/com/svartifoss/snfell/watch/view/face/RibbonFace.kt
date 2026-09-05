package com.svartifoss.snfell.watch.view.face

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.svartifoss.snfell.common.FaceGeometry
import com.svartifoss.snfell.common.RoundScreenText
import com.svartifoss.snfell.watch.theme.GoogleSansFamily
import com.svartifoss.snfell.watch.view.compose.FaceClock

/**
 * Ribbon: a portrait cover framed by four queue-art rails.
 *
 * The rails are the surrounding covers from the playback queue, centre-cropped into vertical
 * capsules. The current cover remains in the middle, so the face makes the queue visible without
 * turning it into a second control surface. The standard background treatment remains behind it;
 * its shipped default is OLED black.
 *
 * The rail is a **timeline, read left to right**, exactly as [CarouselFace]'s is: what already
 * played sits to the left of the hero and what is coming sits to its right. The first version
 * filled all four capsules from the upcoming tracks alone, left to right - which put the very next
 * track at the far left edge and made the sequence run *backwards* toward the centre.
 */
@Composable
fun RibbonFace(state: NowPlayingFaceState, listener: NowPlayingFaceListener) {
    if (state.ambient) {
        RibbonAmbient(state)
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screen = maxWidth
        PlayerBackgroundTreatment(state)

        RibbonQueueRails(state, screen)
        RibbonArtwork(state, screen)
        RibbonProgress(state, screen)
        RibbonMetadata(state, screen)

        // Last, not first: the hero cover is opaque and sits exactly where the confirmation is
        // drawn, so a region placed underneath it flashed into the back of the artwork and a tap
        // that worked looked identical to one that missed. Nothing in this composition is
        // clickable, so there is nothing above for the region to swallow.
        CenterGestureRegion(
                listener,
                size = screen * .58f,
                pulseSize = screen * .38f,
                state = state)

        FaceClock(
                visible = state.showClock,
                color = Color(state.clockColor),
                fontFamily = state.clockFont,
                typography = state.clockTypography)
    }
}

/** Four queue-cover capsules, with the outer pair intentionally clipped by the dial edge. */
@Composable
private fun BoxWithConstraintsScope.RibbonQueueRails(state: NowPlayingFaceState, screen: Dp) {
    val cards = state.queueCards
    // Matched on the title, the same way Carousel and the host's Up Next pill do: several players
    // advance their metadata before activeQueueItemId, and the title is what is on screen.
    val activeIndex = cards.indexOfFirst { it.title.equals(state.title, ignoreCase = true) }
            .takeIf { it >= 0 }
    fun cardAt(delta: Int) = activeIndex?.let { cards.getOrNull(it + delta) }
    // Two behind, two ahead. A capsule with nothing to show stays an empty tinted window rather
    // than repeating the hero cover: five copies of one cover is not a queue, and a player that
    // publishes none (or one this face cannot locate the current track in) has genuinely nothing
    // to put there - the same honest degradation Carousel makes.
    val railCards = listOf(cardAt(-2), cardAt(-1), cardAt(1), cardAt(2))
    val width = screen * FaceGeometry.Ribbon.COLUMN_WIDTH_FRACTION
    val height = screen * FaceGeometry.Ribbon.COLUMN_HEIGHT_FRACTION
    val shape = RoundedCornerShape(width * FaceGeometry.Ribbon.COLUMN_CORNER_FRACTION)
    val centers = listOf(
            FaceGeometry.Ribbon.OUTER_COLUMN_CENTER_X,
            FaceGeometry.Ribbon.INNER_COLUMN_CENTER_X,
            1f - FaceGeometry.Ribbon.INNER_COLUMN_CENTER_X,
            1f - FaceGeometry.Ribbon.OUTER_COLUMN_CENTER_X)

    centers.forEachIndexed { index, centerX ->
        val art = railCards[index]?.art
        Box(
                modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(
                                x = screen * (centerX - FaceGeometry.Ribbon.COLUMN_WIDTH_FRACTION / 2f),
                                y = screen * FaceGeometry.Ribbon.COLUMN_TOP_FRACTION)
                        .size(width = width, height = height)
                        .clip(shape)
                        .background(Color(state.accentColor).copy(alpha = .34f)),
                contentAlignment = Alignment.Center
        ) {
            if (art != null) {
                Image(
                        painter = BitmapPainter(art),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun BoxWithConstraintsScope.RibbonArtwork(state: NowPlayingFaceState, screen: Dp) {
    val width = screen * FaceGeometry.Ribbon.CENTER_COVER_WIDTH_FRACTION
    val height = screen * FaceGeometry.Ribbon.CENTER_COVER_HEIGHT_FRACTION
    val shape = RoundedCornerShape(width * FaceGeometry.Ribbon.CENTER_COVER_CORNER_FRACTION)
    Box(
            modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = screen * FaceGeometry.Ribbon.CENTER_COVER_TOP_FRACTION)
                    .size(width = width, height = height)
                    .clip(shape)
                    .background(Color(state.accentColor).copy(alpha = .62f)),
            contentAlignment = Alignment.Center
    ) {
        // This is a composition-owned cover, like Note's disc. The default backdrop is HIDDEN,
        // and applying that background choice to this hero would make Ribbon ship without music.
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
                drawRect(Brush.verticalGradient(listOf(
                        Color(state.accentColor).copy(alpha = .72f),
                        Color.Black.copy(alpha = .42f))))
            }
        }
    }
}

/**
 * The played fraction, as a hairline under the hero cover.
 *
 * Ribbon composes the dial edge to edge, so its shipped default turns the host's edge arc off -
 * which left the face with no progress indication at all, and the "Progress ring" controls with
 * nothing to act on. This is the composition's own indicator, sized to the hero rather than to the
 * screen, and it honours the same `showInternalProgress` switch and resolved `progressColor` every
 * other face's internal progress does.
 */
@Composable
private fun BoxWithConstraintsScope.RibbonProgress(state: NowPlayingFaceState, screen: Dp) {
    if (!state.showInternalProgress) return
    val thickness = FaceGeometry.Ribbon.PROGRESS_THICKNESS_DP.dp
    val width = screen * FaceGeometry.Ribbon.PROGRESS_WIDTH_FRACTION
    // Eased rather than snapped, matching the curated faces' own progress animation: a hairline
    // this short would otherwise step visibly once a second.
    val animated by animateFloatAsState(
            targetValue = state.progress.coerceIn(0f, 1f),
            animationSpec = tween(600, easing = LinearEasing),
            label = "ribbonProgress")
    Canvas(
            modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = screen * FaceGeometry.Ribbon.PROGRESS_CENTER_FRACTION -
                            thickness / 2f)
                    .size(width = width, height = thickness)
    ) {
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(Color.White.copy(alpha = .22f), cornerRadius = radius)
        if (animated > 0f) {
            drawRoundRect(
                    color = Color(state.progressColor),
                    size = Size(size.width * animated, size.height),
                    cornerRadius = radius)
        }
    }
}

@Composable
private fun BoxWithConstraintsScope.RibbonMetadata(state: NowPlayingFaceState, screen: Dp) {
    // Both bands are inset for the glass rather than by a flat side padding. The title is the one
    // that needed it: it sits low enough that the chord has taken a quarter of the width away, so
    // the old .13 inset ran the ends of a long line under the bezel. See RoundScreenText.
    val titleLineFraction = FaceGeometry.Ribbon.TITLE_LINE_HEIGHT_DP.dp / screen
    val titleLines = RoundScreenText.linesThatFit(
            top = FaceGeometry.Ribbon.TITLE_TOP_FRACTION,
            lineHeight = titleLineFraction,
            maxLines = FaceGeometry.Ribbon.TITLE_MAX_LINES)
    val titleInset = RoundScreenText.sideInsetForLines(
            top = FaceGeometry.Ribbon.TITLE_TOP_FRACTION,
            lineHeight = titleLineFraction,
            lines = titleLines)
    if (state.showArtist && state.artist.isNotBlank()) {
        val artistInset = RoundScreenText.sideInsetFor(
                top = FaceGeometry.Ribbon.ARTIST_TOP_FRACTION,
                bottom = FaceGeometry.Ribbon.ARTIST_TOP_FRACTION +
                        FaceGeometry.Ribbon.ARTIST_LINE_HEIGHT_DP.dp / screen)
        Row(
                modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(
                                top = screen * FaceGeometry.Ribbon.ARTIST_TOP_FRACTION,
                                start = screen * artistInset,
                                end = screen * artistInset)
                        .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            // This is the music source, not a literal @ decoration. SourceIconGlyph keeps the
            // notification-template/app-icon treatment, scale and opacity identical to every
            // other face that identifies the streaming app beside its artist line.
            SourceIconGlyph(
                    state = state,
                    size = 18.dp,
                    tint = Color(state.artistColor))
            ArtistLineText(
                    text = state.artist,
                    state = state,
                    color = Color(state.artistColor),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Start)
        }
    }
    if (state.showTitle && state.title.isNotBlank()) {
        AdaptiveTitleText(
                text = state.title,
                mode = state.titleTextMode,
                state = state,
                color = titleTextColor(state, Color.White),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = state.titleFont,
                typography = state.titleTypography,
                minFontSize = 15.sp,
                maxLines = titleLines,
                modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(
                                top = screen * FaceGeometry.Ribbon.TITLE_TOP_FRACTION,
                                start = screen * titleInset,
                                end = screen * titleInset)
                        .fillMaxWidth())
    }
}

/** A burn-in-conscious outline of the same five-column silhouette. */
@Composable
private fun RibbonAmbient(state: NowPlayingFaceState) {
    val tint = Color(state.ambientTint)
    val intensity = state.ambientIntensity.coerceIn(.2f, 1f)
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val screen = maxWidth
        Canvas(Modifier.fillMaxSize()) {
            val width = size.width * FaceGeometry.Ribbon.COLUMN_WIDTH_FRACTION
            val height = size.height * FaceGeometry.Ribbon.COLUMN_HEIGHT_FRACTION
            val top = size.height * FaceGeometry.Ribbon.COLUMN_TOP_FRACTION
            val corner = width * FaceGeometry.Ribbon.COLUMN_CORNER_FRACTION
            val stroke = Stroke(1.dp.toPx())
            fun rail(centerX: Float) {
                drawRoundRect(
                        color = tint.copy(alpha = .45f * intensity),
                        topLeft = Offset(centerX - width / 2f, top),
                        size = Size(width, height),
                        cornerRadius = CornerRadius(corner, corner),
                        style = stroke)
            }
            rail(size.width * FaceGeometry.Ribbon.OUTER_COLUMN_CENTER_X)
            rail(size.width * FaceGeometry.Ribbon.INNER_COLUMN_CENTER_X)
            rail(size.width * (1f - FaceGeometry.Ribbon.INNER_COLUMN_CENTER_X))
            rail(size.width * (1f - FaceGeometry.Ribbon.OUTER_COLUMN_CENTER_X))

            val coverWidth = size.width * FaceGeometry.Ribbon.CENTER_COVER_WIDTH_FRACTION
            val coverHeight = size.height * FaceGeometry.Ribbon.CENTER_COVER_HEIGHT_FRACTION
            val coverLeft = (size.width - coverWidth) / 2f
            val coverTop = size.height * FaceGeometry.Ribbon.CENTER_COVER_TOP_FRACTION
            val coverCorner = coverWidth * FaceGeometry.Ribbon.CENTER_COVER_CORNER_FRACTION
            drawRoundRect(
                    color = tint.copy(alpha = .72f * intensity),
                    topLeft = Offset(coverLeft, coverTop),
                    size = Size(coverWidth, coverHeight),
                    cornerRadius = CornerRadius(coverCorner, coverCorner),
                    style = stroke)
        }
        if (state.ambientShowTrackInfo) {
            if (state.showArtist && state.artist.isNotBlank()) {
                Row(
                        modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(
                                        top = screen * (FaceGeometry.Ribbon.ARTIST_BASELINE_FRACTION - .08f),
                                        start = screen * .17f,
                                        end = screen * .17f)
                                .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                ) {
                    AmbientSourceIconGlyph(state = state, size = 11.dp, tint = tint.copy(
                            alpha = .62f * intensity))
                    Text(
                            text = state.artist,
                            color = tint.copy(alpha = .62f * intensity),
                            fontFamily = GoogleSansFamily,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center)
                }
            }
            if (state.showTitle && state.title.isNotBlank()) {
                Text(
                        text = state.title,
                        color = tint.copy(alpha = .86f * intensity),
                        fontFamily = GoogleSansFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(
                                        top = screen * (FaceGeometry.Ribbon.TITLE_BASELINE_FRACTION - .10f),
                                        start = screen * .16f,
                                        end = screen * .16f)
                                .fillMaxWidth())
            }
        }
    }
}
