package com.svartifoss.snfell.watch.view.face

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.svartifoss.snfell.common.AdaptiveTextContrast
import com.svartifoss.snfell.common.RoundScreenText
import com.svartifoss.snfell.watch.view.compose.FaceClock

/**
 * Split: the cover on top, a solid panel below it, styled after a Wear OS notification card.
 *
 * The artwork fills the upper band edge to edge, the album's own colour fills the lower one, and the
 * track text sits in that panel with the playing app's icon as a round badge straddling the seam -
 * the place a messaging notification puts its app badge.
 *
 * **This face paints its own opaque backdrop**, which is unusual here: every other Compose face
 * leaves the background to the host's artwork View underneath and the shared
 * [PlayerBackgroundTreatment]. It cannot, because the whole composition *is* a specific treatment -
 * artwork confined to the top half, flat colour below. Letting the shared full-screen artwork show
 * through would simply erase the design. The consequence to know: the Album background styles have
 * no effect on this face, by construction. It still honours [NowPlayingFaceState.albumArtHidden],
 * falling back to a tonal panel rather than a blank band.
 *
 * Text colour is decided against the panel it sits on rather than assumed, via
 * [AdaptiveTextContrast.prefersDarkText] - the panel is the album's colour and can arrive anywhere
 * from near-black to near-white, so a fixed white would be invisible half the time.
 *
 * Gestures follow the same rule as Chat: the centre of the screen carries tap / double-tap /
 * long-press (see [CenterGestureRegion]) because there is no central control to hang them on.
 */
@Composable
fun SplitFace(state: NowPlayingFaceState, listener: NowPlayingFaceListener) {
    if (state.ambient) {
        SplitAmbient(state)
        return
    }

    // The panel colour, and everything else derives from it. Lightened enough to be a surface
    // rather than a smear of the raw accent, which on dark album art is indistinguishable from the
    // black behind it.
    val panelArgb = faceTonal(state.accentColor, PANEL_LIGHTNESS, minSat = .18f, maxSat = .62f)
    val panel = Color(panelArgb)
    val darkText = AdaptiveTextContrast.prefersDarkText(panelArgb)
    val primaryText = if (darkText) Color.Black.copy(alpha = .87f) else Color.White
    val secondaryText = if (darkText) Color.Black.copy(alpha = .58f) else Color.White.copy(alpha = .70f)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screen = maxWidth
        val seam = screen * SEAM_FRACTION

        Column(Modifier.fillMaxSize()) {
            CoverBand(state = state, height = seam, panel = panel)
            // weight, not fillMaxHeight: inside a Column the latter fills the *incoming* max height
            // rather than what is left after the band above, so the panel would run off the bottom.
            Box(
                    modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(panel)
            )
        }

        // Under the badge, over the panel: the badge is a real control-free ornament, so the centre
        // gestures must not be blocked by it - nothing here is clickable.
        CenterGestureRegion(listener, size = screen * .58f)

        TrackText(
                state = state,
                primary = primaryText,
                secondary = secondaryText,
                screen = screen,
                seam = seam)

        SourceBadge(
                state = state,
                darkPanel = !darkText,
                screen = screen,
                seam = seam)

        FaceClock(
                visible = state.showClock,
                color = Color(state.clockColor),
                fontFamily = state.clockFont,
                typography = state.clockTypography)
    }
}

/** Where the cover stops and the panel starts, as a fraction of screen height. Two thirds:
 *  the artwork is the subject and the panel only has to hold two lines of text. */
private const val SEAM_FRACTION = .66f

/** Lightness the album accent is taken to for the lower panel. */
private const val PANEL_LIGHTNESS = .40f

/** The artwork band. Falls back to a tonal wash when the artwork is hidden or has not arrived. */
@Composable
private fun CoverBand(state: NowPlayingFaceState, height: Dp, panel: Color) {
    val art = state.albumArt?.takeUnless { state.albumArtHidden }
    Box(
            modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
                    // Painted even when there is artwork: a cover with transparency, or one still
                    // loading, would otherwise show the window's black through the band and break
                    // the two-tone composition for a frame.
                    .background(
                            Brush.verticalGradient(
                                    listOf(panel.copy(alpha = .55f), panel))
                    )
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
 * Artist above, title below - the order the reference card uses (sender, then message), and the
 * opposite of most faces here. It is deliberate: on a notification card the smaller line identifies
 * *who*, and the larger one is the content, which maps onto artist and track.
 */
@Composable
private fun TrackText(
        state: NowPlayingFaceState,
        primary: Color,
        secondary: Color,
        screen: Dp,
        seam: Dp
) {
    // The panel's usable width narrows towards the bottom of a round screen; ask the shared
    // geometry rather than hand-tuning a padding that is only correct for one line count.
    val inset = RoundScreenText.sideInsetFor(
            top = SEAM_FRACTION + .04f,
            bottom = SEAM_FRACTION + .28f)
    Column(
            modifier = Modifier
                    .fillMaxSize()
                    .padding(top = seam + screen * .04f)
                    .padding(horizontal = screen * inset),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Top
    ) {
        if (state.showArtist && state.artist.isNotEmpty()) {
            ArtistLineText(
                    text = state.artist,
                    state = state,
                    color = secondary,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
            )
        }
        if (state.showTitle && state.title.isNotEmpty()) {
            // The shared adaptive title rather than a bare Text with maxLines = 2. A plain wrap
            // breaks wherever the width runs out, which on a title like "Footprints" left a single
            // orphaned "s" on the second line - technically correct and unreadable. This honours
            // the user's own title text mode (shrink / smart / marquee / wrap) and, in every mode
            // but wrap, resizes to fit rather than breaking a word badly.
            AdaptiveTitleText(
                    text = state.title,
                    mode = state.titleTextMode,
                    fontSize = 21.sp,
                    color = titleTextColor(state, primary),
                    fontWeight = FontWeight.SemiBold,
                    fontStyle = state.titleFontStyle,
                    fontFamily = state.titleFont,
                    letterSpacing = state.titleLetterSpacing,
                    // No explicit lineHeight on purpose. AdaptiveTitleText shrinks the font to make
                    // a long title fit but passes lineHeight straight through, so a fixed 23sp
                    // stayed put while the text dropped to ~14sp - leaving a gap between the two
                    // lines wider than the letters themselves. Unspecified lets Compose derive it
                    // from whatever size the text actually settled on.
                    textAlign = TextAlign.Start,
                    typography = state.titleTypography,
                    modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * The playing app's icon on the seam, right of centre - where a messaging notification puts its
 * app badge.
 *
 * The icon is drawn bare: no disc behind it, no ring around it. That leaves the glyph itself as the
 * only mark, which is what the seam needs - a filled circle straddling cover and panel read as a
 * third surface competing with both.
 *
 * Drawing it bare moves the contrast problem rather than removing it, so the tint is decided against
 * the panel via [darkPanel] - the same call the panel's own text makes, so the icon can never come
 * out invisible against the half it shares with the text. The seam means the glyph's upper half sits
 * on artwork instead, which no fixed colour can guarantee; the panel is the surface it belongs to
 * and the one this face controls. A full-colour launcher icon is still left untinted, since tinting
 * real artwork would destroy it.
 */
@Composable
private fun SourceBadge(
        state: NowPlayingFaceState,
        darkPanel: Boolean,
        screen: Dp,
        seam: Dp
) {
    val icon = state.sourceIcon ?: return
    val spec = state.sourceIconTypography
    // With the disc gone the glyph takes over the footprint the badge used to occupy, so removing
    // the circle does not also shrink the mark down to the inset it needed to clear the ring.
    val glyph = (screen * BADGE_FRACTION * spec.scale).coerceIn(26.dp, 52.dp)

    val onPanel = if (darkPanel) Color.White else Color.Black.copy(alpha = .85f)

    Box(
            modifier = Modifier
                    .fillMaxSize()
                    .padding(end = screen * .10f)
                    // CenterEnd puts the icon on the vertical midpoint; shift it to the seam so it
                    // straddles cover and panel exactly as the reference does.
                    .offset(y = seam - screen * .5f),
            contentAlignment = Alignment.CenterEnd
    ) {
        Image(
                bitmap = icon,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                colorFilter = if (state.sourceIconTemplate) ColorFilter.tint(onPanel) else null,
                alpha = spec.alpha,
                modifier = Modifier
                        .size(glyph)
                        .then(
                                if (state.sourceIconTemplate) Modifier
                                else Modifier.clip(CircleShape)
                        )
        )
    }
}

/** Footprint of the source icon on the seam. Named for the badge it used to be the disc of - the
 *  disc is gone, but this is still the mark's size on the seam. */
private const val BADGE_FRACTION = .21f

/**
 * Ambient variant: outline only, no fills.
 *
 * The two-tone composition is dropped entirely rather than outlined - a filled half-screen band is
 * the single most expensive thing an AOD can draw, and an outline of a colour panel conveys nothing.
 * What survives is the seam as a hairline and the text, which is the information.
 */
@Composable
private fun SplitAmbient(state: NowPlayingFaceState) {
    val tint = Color(state.ambientTint).copy(alpha = state.ambientIntensity.coerceIn(.2f, 1f))
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val screen = maxWidth
        Box(
                modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = screen * .18f)
                        .offset(y = screen * SEAM_FRACTION)
                        .height(1.dp)
                        .background(tint.copy(alpha = tint.alpha * .5f))
        )
        if (state.ambientShowTrackInfo) {
            Column(
                    modifier = Modifier
                            .fillMaxSize()
                            .padding(top = screen * (SEAM_FRACTION + .07f))
                            .padding(horizontal = screen * .16f),
                    horizontalAlignment = Alignment.Start
            ) {
                if (state.artist.isNotEmpty()) {
                    Text(
                            text = state.artist,
                            color = tint.copy(alpha = tint.alpha * .7f),
                            fontFamily = state.artistFont,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                    )
                }
                if (state.title.isNotEmpty()) {
                    Text(
                            text = state.title,
                            color = tint,
                            fontFamily = state.titleFont,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
