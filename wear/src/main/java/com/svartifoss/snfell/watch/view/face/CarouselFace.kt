package com.svartifoss.snfell.watch.view.face

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.svartifoss.snfell.common.CoverShape
import com.svartifoss.snfell.common.FaceGeometry
import com.svartifoss.snfell.common.RoundScreenText
import com.svartifoss.snfell.watch.view.compose.FaceClock

/**
 * Carousel: the queue *is* the screen.
 *
 * The current track's cover sits large and centred with its neighbours peeking in from both sides,
 * and the track's own text lives underneath next to a round source badge. Nothing else - no
 * transport row, no ring - because the covers are the content and anything else competes with them.
 *
 * The interaction contract matches the rest of the curated collection: the centre card toggles
 * playback, double tap opens quick actions, long press opens the queue. The side cards are
 * deliberately *not* individually tappable: they are a preview of what is coming, and making a
 * half-occluded card a jump target invites mis-taps on a screen this size. The queue screen is one
 * long press away for actually choosing something.
 *
 * Degrades honestly. A player that publishes no queue (Retro Music before 6.x, for one) leaves
 * [NowPlayingFaceState.queueCards] empty, and the face renders the single current cover instead of
 * an empty rail.
 */
@Composable
fun CarouselFace(
        state: NowPlayingFaceState,
        listener: NowPlayingFaceListener,
        cardShape: CoverShape
) {
    // Deliberately no early return on idle: the face owns the "nothing playing" screen too, so
    // choosing a face is visible even with no session. Every layout below already tolerates absent
    // artwork and metadata, and artistOrStatus() turns the artist line into the stopped-state text.
    if (state.ambient) {
        CarouselAmbient(state)
        return
    }

    // Deliberately NO opaque background here: the host's album-art View sits *under* this
    // ComposeView, and PlayerBackgroundTreatment composes on top of it. Painting the root black
    // covered the artwork, which is why every Album background style rendered as a black screen.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screen = maxWidth
        val cards = state.queueCards
        val activeIndex = cards.indexOfFirst { it.title.equals(state.title, ignoreCase = true) }
                .takeIf { it >= 0 }
        fun cardAt(delta: Int) = activeIndex?.let { cards.getOrNull(it + delta) }

        // Everything that changes with the track travels as ONE value through ONE AnimatedContent.
        // Animating the cover while the text swapped instantly was the desync: two updates arriving
        // in the same frame but only one of them animated reads as the card lagging behind.
        val frame = CarouselFrame(
                heroArt = state.albumArt ?: cardAt(0)?.art,
                previousArt = cardAt(-1)?.art,
                nextArt = cardAt(1)?.art,
                farPreviousArt = cardAt(-2)?.art,
                farNextArt = cardAt(2)?.art,
                title = state.title,
                artist = artistOrStatus(state))

        AnimatedContent(
                targetState = frame,
                transitionSpec = {
                    (slideInHorizontally(tween(CAROUSEL_ANIM_MS)) { it / 4 } +
                            fadeIn(tween(CAROUSEL_ANIM_MS)))
                            .togetherWith(fadeOut(tween(CAROUSEL_ANIM_MS)))
                },
                // Identity is the TRACK, not the frame contents. Title, cover, source icon and
                // the queue all arrive in separate updates, and without this each one counted as a
                // new target - which is why a single skip played several stacked animations.
                contentKey = { it.title },
                label = "carouselFrame"
        ) { shown ->
            CarouselFrameContent(
                    frame = shown,
                    state = state,
                    listener = listener,
                    cardShape = cardShape,
                    screen = screen)
        }

        FaceClock(
                visible = state.showClock,
                color = Color(state.clockColor),
                fontFamily = state.clockFont,
                typography = state.clockTypography)
    }
}

/** Everything the rail redraws on a track change, so one animation can carry all of it. */
private data class CarouselFrame(
        val heroArt: androidx.compose.ui.graphics.ImageBitmap?,
        val previousArt: androidx.compose.ui.graphics.ImageBitmap?,
        val nextArt: androidx.compose.ui.graphics.ImageBitmap?,
        val farPreviousArt: androidx.compose.ui.graphics.ImageBitmap?,
        val farNextArt: androidx.compose.ui.graphics.ImageBitmap?,
        val title: String,
        val artist: String
)

@Composable
private fun CarouselFrameContent(
        frame: CarouselFrame,
        state: NowPlayingFaceState,
        listener: NowPlayingFaceListener,
        cardShape: CoverShape,
        screen: Dp
) {
    // Sizes and offsets are fractions of the SCREEN, not of the card, because what matters is where
    // each edge lands relative to the bezel. The near card's outer edge has to stop inside the glass
    // (~44%), or it covers the whole remaining width and the far card - drawn underneath it - can
    // never show. That was why only one neighbour was ever visible.
    val cardSize = screen * CARD_FRACTION
    val nearSize = screen * .46f
    val farSize = screen * .34f
    // Tuned so the two neighbours show roughly equal slivers. The near card is tucked far enough
    // behind the hero that its outer edge lands around 40% of the radius, leaving the stretch from
    // there to the bezel for the far card. Every earlier attempt widened the far card instead,
    // which only pushed it off the glass - the room has to come from hiding the near one deeper.
    val nearOffset = screen * .17f
    val farOffset = screen * .32f
    val shape = cardShape.toComposeShape(cardSize)

    // How many lines the title settled on, fed back from its own layout pass. Without it the inset
    // would have to assume the worst case and permanently narrow single-line titles, which is the
    // trade the old fixed 14% padding made in the other direction - wide enough for one line, and
    // wrong for two.
    var titleLines by remember(frame.title) { mutableIntStateOf(1) }
    // sp is treated as dp here, as everywhere else in the faces: Wear OS has no user font scaling,
    // and the phone preview measures the same way against its 192dp reference watch.
    val titleLineFraction = CAROUSEL_TITLE_LINE_HEIGHT.dp / screen
    // A mode like "wrap5" can ask for more lines than the band below the cover can hold. Capping
    // the inset at what fits keeps the column readable rather than collapsing it to a sliver for
    // lines that were never going to be on the glass anyway.
    val fittingLines = RoundScreenText.linesThatFit(
            top = TITLE_TOP, lineHeight = titleLineFraction, maxLines = MAX_TITLE_LINES)
    val titleInset = RoundScreenText.sideInsetForLines(
            top = TITLE_TOP,
            lineHeight = titleLineFraction,
            lines = titleLines.coerceAtMost(fittingLines))
    val artistInset = RoundScreenText.sideInsetFor(
            top = ARTIST_TOP, bottom = ARTIST_TOP + ARTIST_ROW_FRACTION)

    Box(Modifier.fillMaxSize()) {
        // The shared background pipeline, exactly as every other Compose face uses it. Carousel
        // used to paint its own blurred cover here, which sat ON TOP of the host's artwork View and
        // therefore ignored the Album background style, the blur radius and every player-shading
        // control - the face looked the same whatever those were set to. The blurred look is now
        // the face's *default* background (PlayerBackgroundStyle.defaultForFace) instead of a
        // hardcoded layer, so changing it actually changes something.
        PlayerBackgroundTreatment(state)

        // Artist sits under the clock, at the top. It used to share the bottom with the title,
        // which on a round screen meant both fought for the narrowest part of the glass; the top
        // band is empty apart from the clock and the line is short.
        if (state.showArtist) {
            Row(
                    modifier = Modifier.align(Alignment.TopCenter)
                            // Hugs the top of the cover, derived from the card's own bounds rather
                            // than from a second hand-tuned constant: the two used to be
                            // independent numbers that happened to line up, so any change to the
                            // rail's size or position silently detached the artist from the cover.
                            .padding(
                                    top = screen * ARTIST_TOP,
                                    start = screen * artistInset,
                                    end = screen * artistInset)
                            .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
            ) {
                // Through the shared glyph, like every other Compose face. Carousel drew its own
                // copy, which centre-CROPPED the bitmap inside a circle and never tinted it - but
                // the phone normally sends the media notification's small icon, a flat white
                // template glyph sitting on a padded optical canvas. Cropping a circle out of that
                // padding threw most of the glyph away and left the rest untinted white, so the
                // icon read as missing. SourceIconGlyph fits it whole, tints templates to the
                // artist line's own colour and applies the icon size/opacity controls - none of
                // which the local copy did. It emits its own trailing spacer, so the artist text
                // below no longer adds one.
                SourceIconGlyph(
                        state = state,
                        size = CAROUSEL_SOURCE_ICON_SIZE.dp,
                        tint = Color(state.artistColor))
                // Through the shared helper, not a bare Text: this line used to hardcode its size
                // and read none of state.artistTypography, so the Text tab's artist weight/size/
                // opacity/tracking controls moved the title on this face and left the artist
                // untouched - the two lines were styled by different rules.
                //
                // artistColor, not accentColor: this face was the only one reading the raw face
                // accent here, so the Artist text color treatment (and the adaptive-contrast
                // correction that now rides on it) did nothing at all on Carousel.
                ArtistLineText(
                        text = frame.artist.uppercase(),
                        state = state,
                        color = Color(state.artistColor),
                        fontSize = CAROUSEL_ARTIST_SIZE.sp,
                        lineHeight = (CAROUSEL_ARTIST_SIZE + 2f).sp,
                        letterSpacing = CAROUSEL_TITLE_TRACKING.sp,
                        textAlign = TextAlign.Center)
            }
        }

        Box(
                Modifier.align(Alignment.Center)
                        // Lifted off dead centre so the band under the cover is deep enough for a
                        // second title line. Centred, that band bottomed out where the chord is too
                        // narrow to hold anything, which is why a wrapped title was clipped at both
                        // ends no matter how the padding was tuned.
                        .offset(y = -screen * (.5f - RAIL_CENTER))
                        .pointerInput(listener) {
                            detectTapGestures(
                                    onTap = { listener.onPlayPauseTap() },
                                    onDoubleTap = { listener.onCenterDoubleTap() },
                                    onLongPress = { listener.onCenterLongPress() })
                        },
                contentAlignment = Alignment.Center
        ) {
            // Painted outermost-first so each nearer card overlaps the one behind it.
            frame.farPreviousArt?.let {
                CarouselCardImage(it, farSize, shape, Modifier.offset(x = -farOffset), FAR_SHADE)
            }
            frame.farNextArt?.let {
                CarouselCardImage(it, farSize, shape, Modifier.offset(x = farOffset), FAR_SHADE)
            }
            frame.previousArt?.let {
                CarouselCardImage(it, nearSize, shape, Modifier.offset(x = -nearOffset), NEAR_SHADE)
            }
            frame.nextArt?.let {
                CarouselCardImage(it, nearSize, shape, Modifier.offset(x = nearOffset), NEAR_SHADE)
            }

            if (frame.heroArt != null) {
                CarouselCardImage(frame.heroArt, cardSize, shape, Modifier)
            } else {
                Box(Modifier.size(cardSize).clip(shape).drawBehind {
                    drawRect(Color(state.accentColor).copy(alpha = .18f))
                })
            }
        }

        Column(
                Modifier.align(Alignment.TopCenter)
                        // Anchored just below the card rather than to the bottom edge, and inset by
                        // the real chord at the depth the text actually reaches - see titleInset.
                        // Sitting against the cover also reads as one block instead of two things
                        // floating apart.
                        .padding(
                                top = screen * TITLE_TOP,
                                start = screen * titleInset,
                                end = screen * titleInset)
                        .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (state.showTitle) {
                AdaptiveTitleText(
                        text = frame.title.uppercase(),
                        mode = state.titleTextMode,
                        state = state,
                        typography = state.titleTypography,
                        color = titleTextColor(state, Color.White),
                        fontSize = CAROUSEL_TITLE_SIZE.sp,
                        lineHeight = CAROUSEL_TITLE_LINE_HEIGHT.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = state.titleFont,
                        textAlign = TextAlign.Center,
                        // Only ever grows, and resets with the track. A narrower column can push
                        // the text onto one more line, which narrows it again - monotonic growth is
                        // what makes that settle instead of oscillating between two widths, and the
                        // mode's own maxLines is the ceiling that ends it.
                        onLineCount = { titleLines = maxOf(titleLines, it) })
            }
        }
    }
}

private const val CAROUSEL_ANIM_MS = 340

private const val CAROUSEL_TITLE_TRACKING = FaceGeometry.Carousel.TITLE_TRACKING_SP

/**
 * The rail's geometry, as fractions of the screen. Every text anchor is derived from these three
 * numbers rather than being tuned independently, so moving or resizing the cover keeps the artist
 * hugging its top edge and the title hugging its bottom one.
 */
private const val CARD_FRACTION = FaceGeometry.Carousel.CARD_FRACTION

/**
 * The rail's centre, above the screen's. The face needs a deeper band under the cover than a
 * centred rail leaves, because that is where a wrapped title has to live and the chord closes in
 * fast down there. Kept small: any further up and the artist line collides with the clock.
 */
private const val RAIL_CENTER = FaceGeometry.Carousel.RAIL_CENTER

private const val CARD_TOP = FaceGeometry.Carousel.CARD_TOP
private const val CARD_BOTTOM = FaceGeometry.Carousel.CARD_BOTTOM

/** Height of the artist row - the source badge, which is the taller of the two things in it. */
private const val ARTIST_ROW_FRACTION = FaceGeometry.Carousel.ARTIST_ROW_FRACTION

private const val ARTIST_TOP = FaceGeometry.Carousel.ARTIST_TOP

/** A hair of air between the cover and the title, not a gap: they read as one block. */
private const val TITLE_TOP = FaceGeometry.Carousel.TITLE_TOP

/** Ceiling for the inset calculation. Modes offering more lines than this (wrap5) still render
 *  them; this only stops the *width* being computed for lines that fall outside the glass. */
private const val MAX_TITLE_LINES = 3

private const val CAROUSEL_TITLE_SIZE = 14f
private const val CAROUSEL_TITLE_LINE_HEIGHT = 16f
private const val CAROUSEL_ARTIST_SIZE = 10f

/** Sized against the 10sp artist line it annotates, matching the curated faces' proportion. */
private const val CAROUSEL_SOURCE_ICON_SIZE = FaceGeometry.Carousel.SOURCE_ICON_SIZE_DP

/**
 * A rail card. Neighbours stay fully opaque and recede through [shade], a darkening veil, rather
 * than through alpha: a translucent card lets the blurred backdrop show through it, which reads as
 * "faded out" instead of "further away". Darkening keeps the artwork solid and puts it in shadow,
 * which is what depth actually looks like.
 */
@Composable
private fun CarouselCardImage(
        art: androidx.compose.ui.graphics.ImageBitmap,
        size: Dp,
        shape: Shape,
        modifier: Modifier,
        shade: Float = 0f
) {
    Box(modifier.size(size).clip(shape)) {
        Image(
                painter = BitmapPainter(art),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize())
        if (shade > 0f) {
            Box(Modifier.matchParentSize().drawBehind {
                drawRect(Color.Black.copy(alpha = shade))
            })
        }
    }
}

private const val NEAR_SHADE = FaceGeometry.Carousel.NEAR_SHADE
private const val FAR_SHADE = FaceGeometry.Carousel.FAR_SHADE

/**
 * Ambient variant: one card and the text, no neighbours.
 *
 * The one ambient face in the collection that keeps a cover on screen - everything else it draws
 * is text - which makes it the only place the always-on artwork controls have anything to reach.
 */
@Composable
private fun CarouselAmbient(state: NowPlayingFaceState) {
    val geo = FaceGeometry.Carousel.Ambient
    val intensity = state.ambientIntensity.coerceIn(.2f, 1f)
    val tint = Color(state.ambientTint)
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val screen = maxWidth
        // state.ambientAlbumArt, never state.albumArt: this is the one ambient face that draws a
        // cover, so it is the one that has to follow the always-on artwork controls. Reading the
        // awake cover meant "Show artwork" and the ambient photo treatment reached the host's
        // backdrop - which this face's black canvas covers anyway - and left the card showing a
        // picture the user had switched off, under the interactive filter.
        state.ambientAlbumArt?.let { cover ->
            val art = if (state.ambientAlbumArtBlurred) {
                rememberBlurredCover(cover, state.albumArtBlurRadiusPx)
            } else {
                cover
            }
            Image(
                    painter = BitmapPainter(art),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.align(Alignment.Center)
                            .offset(y = -screen * geo.CARD_RISE_FRACTION)
                            .size(screen * geo.CARD_FRACTION)
                            .clip(RoundedCornerShape(geo.CARD_CORNER_DP.dp))
                            // The always-on artwork opacity, not a constant of its own: the
                            // face was authored at .55, which is that preference's default, so
                            // the slider now moves this card and nothing else changed.
                            .alpha(state.ambientAlbumArtAlpha * intensity))
        }
        if (state.ambientShowTrackInfo && (state.showTitle || state.showArtist)) {
            Column(
                    Modifier.align(Alignment.BottomCenter)
                            .padding(bottom = screen * geo.TEXT_BOTTOM_FRACTION)
                            .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (state.showTitle) {
                    Text(state.title.uppercase(),
                            color = tint.copy(alpha = geo.TITLE_ALPHA * intensity),
                            fontSize = geo.TITLE_SP.sp, fontFamily = state.titleFont,
                            letterSpacing = CAROUSEL_TITLE_TRACKING.sp,
                            textAlign = TextAlign.Center, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                }
                if (state.showArtist) {
                    Text(state.artist.uppercase(),
                            color = tint.copy(alpha = geo.ARTIST_ALPHA * intensity),
                            fontSize = geo.ARTIST_SP.sp, fontFamily = state.artistFont,
                            letterSpacing = CAROUSEL_TITLE_TRACKING.sp,
                            textAlign = TextAlign.Center, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
