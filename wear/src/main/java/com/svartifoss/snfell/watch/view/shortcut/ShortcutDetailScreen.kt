package com.svartifoss.snfell.watch.view.shortcut

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.AlbumAccentSource
import com.svartifoss.snfell.proto.ShortcutPlayMode
import com.svartifoss.snfell.watch.theme.LocalWatchUiFontFamily
import com.svartifoss.snfell.watch.theme.WatchTheme
import com.svartifoss.snfell.watch.view.compose.CurvedClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** One streaming shortcut, as its own screen shows it. */
data class ShortcutDetailUi(
        /** The launch target the phone plays - a list entry id, or a menu entry's remote URI. */
        val entryId: String,
        val title: String,
        /** Service and kind ("YouTube Music • Playlist"), as the phone described it. */
        val subtitle: String?,
        /** Whether Shuffle is offered beside Play - a collection, on a phone that honours it. */
        val shuffleable: Boolean,
        /** Public link-preview creator/byline, when the service publishes one. */
        val creator: String? = null,
        /** Public link-preview description, deliberately absent when no preview published it. */
        val description: String? = null,
        /**
         * The cover: the fetched one when the opt-in lookup found it, otherwise the one the phone
         * draws (a liked-songs collection's own design, or the service's colour with its mark).
         * The screen shows it as its backdrop and takes its colour from it.
         */
        val cover: Bitmap? = null,
        /**
         * The status-bar glyph of the app the link opens in, a white template the phone learned
         * from that app's notifications - drawn on "Open on phone". Null when the phone has not
         * learned one, and the screen then draws its own mark for the service.
         */
        val mark: Bitmap? = null
)

/**
 * A streaming shortcut's own screen, in the shape the music apps give a playlist's page on the
 * watch: the clock, the name, two round buttons - Shuffle and Play - and "Open on phone" under
 * them. Adopted here for the links the phone has saved.
 *
 * The cover is the backdrop, centred on the screen and softened, closing to black at the edges and
 * below ([ShortcutBackdrop]), and it gives the page its one colour ([ShortcutPalette]): the
 * service line and the Play button take tones of it, extracted once per cover ([ShortcutGlow]).
 *
 * It exists because the choice between playing in order and shuffled used to be made on the phone,
 * once, when a link was saved - and only for YouTube Music, the one service whose links carry it.
 * Here it is made each time, for any service whose player accepts a shuffle request; the phone
 * decides how to ask (see `StreamingShortcutLinks.planFor`).
 *
 * Content only: the host owns the dismiss gesture, because the two hosts need different things
 * from it. Inside the menu a swipe returns to the list the shortcut was picked from; on its own
 * (from the Shortcuts Tile or the quick panel) it closes the screen.
 *
 * Nothing here downloads or stores music - Play and Shuffle are requests to the phone's own player,
 * and "Open on phone" opens the link in the service's app on the phone.
 */
@Composable
fun ShortcutDetailContent(
        detail: ShortcutDetailUi,
        /** Which of the cover's colours is its colour, the user's choice for the player too. */
        accentSource: AlbumAccentSource,
        /** The playing album's colour - the faint wash from the top the screen has when the
         *  shortcut has no cover, the same as the menu under it. */
        fallbackAccent: Color,
        onPlay: (ShortcutPlayMode) -> Unit,
        onOpenOnPhone: () -> Unit
) {
    val screen = LocalConfiguration.current.let { minOf(it.screenWidthDp, it.screenHeightDp) }.toFloat()
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
    val buttonSize = ShortcutDetailGeometry.buttonSize(screen).dp
    val hasAbout = !detail.creator.isNullOrBlank() || !detail.description.isNullOrBlank()

    val glow = rememberShortcutGlow(detail.cover)
    // Everything below is worked out once per cover (or per place the service line lands), never
    // per frame: the colour from the palette already extracted, the ground from the glow's pixels.
    val accent = remember(glow, accentSource) { glow?.accent(accentSource) }
    var subtitleBand by remember(screen) { mutableStateOf(TextBand.estimatedSubtitle(screen)) }
    val subtitleGround = remember(glow, subtitleBand) {
        glow?.let { subtitleBand.groundColor(it) }
    }
    val neutralPlay = remember(detail.entryId) {
        ShortcutDestination.isYouTubeMusicLikedMusic(detail.entryId)
    }
    val colors = remember(accent, subtitleGround, neutralPlay) {
        ShortcutPalette.resolve(accent, subtitleGround, neutralPlay)
    }
    // The colour arrives with the glow, a moment after the screen opens; it eases in with it.
    val subtitleColor by animateColorAsState(Color(colors.subtitle), tween(COLOR_FADE_MS), "subtitle")
    val playContainer by animateColorAsState(Color(colors.playContainer), tween(COLOR_FADE_MS), "play")
    val playContent by animateColorAsState(Color(colors.playContent), tween(COLOR_FADE_MS), "playGlyph")

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        ShortcutBackdrop(glow, washed = detail.cover == null, fallbackAccent = fallbackAccent)
        ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                // Laid out from the top like a page, not centred on its second row like a list:
                // the name belongs under the clock, with the buttons and the pill below it.
                autoCentering = null,
                contentPadding = PaddingValues(
                        start = ShortcutDetailGeometry.sideInset(screen).dp,
                        end = ShortcutDetailGeometry.sideInset(screen).dp,
                        top = ShortcutDetailGeometry.titleTop(screen).dp,
                        bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(ITEM_SPACING.dp),
                // A page rather than a long list: only a light version of the edge scaling, so the
                // buttons stay the circles they are drawn as.
                scalingParams = ScalingLazyColumnDefaults.scalingParams(edgeScale = .9f, edgeAlpha = .85f),
                rotaryScrollableBehavior = RotaryScrollableDefaults.behavior(scrollableState = listState)
        ) {
            item(key = "title") {
                ShortcutHeading(
                        title = detail.title,
                        subtitle = detail.subtitle,
                        subtitleColor = subtitleColor,
                        minHeight = ShortcutDetailGeometry.titleBlockMinHeight(screen, ITEM_SPACING).dp,
                        onSubtitlePlaced = { band ->
                            // Only where it rests: while the page scrolls the line moves over the
                            // backdrop, and following it would recolour the text on every frame.
                            if (!listState.canScrollBackward && band != subtitleBand) {
                                subtitleBand = band
                            }
                        })
            }
            item(key = "buttons") {
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(
                                ShortcutDetailGeometry.buttonGap(screen).dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    // Shuffle first and Play last, as the music apps lay the pair out: the light
                    // circle is the primary action and rests where the thumb falls.
                    if (detail.shuffleable) {
                        RoundActionButton(
                                iconRes = com.svartifoss.snfell.common.R.drawable.action_shuffle,
                                label = stringResource(R.string.quick_action_shuffle),
                                container = Color(WatchTheme.SURFACE_DARK),
                                content = Color.White,
                                size = buttonSize,
                                onClick = { onPlay(ShortcutPlayMode.SHUFFLE) })
                    }
                    RoundActionButton(
                            iconRes = com.svartifoss.snfell.common.R.drawable.action_play_filled,
                            label = stringResource(R.string.action_name_play),
                            container = playContainer,
                            content = playContent,
                            size = buttonSize,
                            onClick = { onPlay(ShortcutPlayMode.IN_ORDER) })
                }
            }
            item(key = "phone") {
                OpenOnPhonePill(
                        mark = detail.mark,
                        service = remember(detail.entryId) {
                            ShortcutDestination.serviceOf(detail.entryId)
                        },
                        maxWidth = ShortcutDetailGeometry.pillMaxWidth(screen).dp,
                        topGap = ShortcutDetailGeometry.pillTopGap(screen, ITEM_SPACING).dp,
                        onClick = onOpenOnPhone)
            }
            if (hasAbout) {
                item(key = "about") {
                    ShortcutAbout(detail.creator, detail.description)
                }
            }
        }

        // Over the title's top edge only while the page sits at rest at the top: the first pixel
        // of scroll starts carrying the title towards the clock, and the two must not overlap.
        val clockVisible by remember { derivedStateOf { !listState.canScrollBackward } }
        CurvedClock(visible = clockVisible, fontWeight = FontWeight.Bold)
    }
}

/**
 * [cover]'s glow: at once when it was worked out before (the screen opened again on the same
 * shortcut), otherwise off the main thread - the screen opens black and the backdrop fades in.
 */
@Composable
private fun rememberShortcutGlow(cover: Bitmap?): ShortcutGlow? {
    val glow by produceState(cover?.let { ShortcutGlow.cached(it) }, cover) {
        // Set on every change of cover, not only the first: the state outlives its key, and a
        // cover that lands after the screen opened must replace whatever was shown before it.
        value = cover?.let { picture ->
            ShortcutGlow.cached(picture)
                    ?: withContext(Dispatchers.Default) { ShortcutGlow.obtain(picture) }
        }
    }
    return glow
}

/**
 * Where a line of text sits, as fractions of the screen - what [ShortcutGlowMath.groundColor] is
 * asked about. Rounded, so a layout pass that moves the line by a fraction of a pixel is not a
 * new place to read the backdrop at.
 */
@Immutable
private data class TextBand(val left: Float, val top: Float, val right: Float, val bottom: Float) {

    fun groundColor(glow: ShortcutGlow): Int = ShortcutGlowMath.groundColor(
            glow.pixels, ShortcutGlowMath.SAMPLE_PX, left, top, right, bottom)

    companion object {
        fun of(left: Float, top: Float, right: Float, bottom: Float) =
                TextBand(round(left), round(top), round(right), round(bottom))

        /** Where the service line sits under a one-line name - the first guess, so the line has
         *  its colour on the first frame and is only corrected once it has been laid out. */
        fun estimatedSubtitle(screen: Float): TextBand {
            val top = (ShortcutDetailGeometry.titleTop(screen) + TITLE_LINE_DP + SUBTITLE_GAP_DP) / screen
            return of(.25f, top, .75f, top + SUBTITLE_LINE_DP / screen)
        }

        private fun round(value: Float) = (value * 100f).roundToInt() / 100f
    }
}

/**
 * The backdrop: the cover softened (see [ShortcutGlow]), drawn over the whole screen and centred
 * on it, so the cover's middle is the screen's middle - its biggest shape lands between the name
 * and the buttons, as the music apps' playlist pages have it - under two masks of black that close
 * it at the sides, above the clock and below the buttons ([ShortcutDetailGeometry.RADIAL_MASK],
 * [ShortcutDetailGeometry.VERTICAL_MASK]).
 *
 * Without a cover there is nothing to show and the page is black, which on an OLED screen costs
 * nothing and is the honest answer; a faint wash of the playing album's colour from the top stands
 * in for it, the same as the menu this page is opened from, so it does not read as a system screen.
 *
 * Drawn into a layer of its own, rendered once: it never changes while the page scrolls over it,
 * and without the layer every scroll frame drew the cover and both masks across the whole screen
 * again.
 */
@Composable
private fun ShortcutBackdrop(glow: ShortcutGlow?, washed: Boolean, fallbackAccent: Color) {
    val reveal = animateFloatAsState(
            targetValue = if (glow != null) 1f else 0f,
            animationSpec = tween(durationMillis = 360),
            label = "shortcutGlowReveal")
    val image = remember(glow) { glow?.bitmap?.asImageBitmap() }
    Box(
            Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    // Read here, in the draw phase: the fade runs for a third of a second and
                    // must not recompose the page under it for each of its frames.
                    .drawBehind {
                        if (washed) {
                            drawRect(Brush.verticalGradient(
                                    0f to fallbackAccent.copy(alpha = .20f),
                                    .45f to Color.Transparent))
                        }
                        val picture = image ?: return@drawBehind
                        drawGlow(picture, ShortcutGlowMath.GLOW_ALPHA * reveal.value)
                    })
}

/** The masks as the gradient stops a brush draws: the same (position, opacity) pairs that
 *  [ShortcutGlowMath.groundColor] reads, as black at that opacity. */
private val RADIAL_MASK_STOPS = ShortcutDetailGeometry.RADIAL_MASK
        .map { (position, opacity) -> position to Color.Black.copy(alpha = opacity) }.toTypedArray()
private val VERTICAL_MASK_STOPS = ShortcutDetailGeometry.VERTICAL_MASK
        .map { (position, opacity) -> position to Color.Black.copy(alpha = opacity) }.toTypedArray()

/**
 * Draws [image] as a square as wide as the screen, centred on it, then lays the masks over it.
 *
 * The masks are black over the picture rather than the picture's alpha: the ground is black
 * anyway, so the two are the same pixels.
 */
private fun DrawScope.drawGlow(image: ImageBitmap, alpha: Float) {
    if (alpha <= 0f) return
    val side = size.width
    drawImage(
            image = image,
            dstOffset = IntOffset(0, ((size.height - side) / 2f).roundToInt()),
            dstSize = IntSize(side.roundToInt(), side.roundToInt()),
            alpha = alpha,
            filterQuality = FilterQuality.High)
    drawRect(Brush.radialGradient(
            *RADIAL_MASK_STOPS,
            center = Offset(size.width / 2f, size.height / 2f),
            radius = size.width * ShortcutDetailGeometry.RADIAL_MASK_RADIUS))
    drawRect(Brush.verticalGradient(*VERTICAL_MASK_STOPS))
}

/**
 * The name in bold, and under it the service line ("YouTube Music • Playlist") that says what
 * kind of thing it is, in a tone of the cover's colour chosen against the backdrop behind it.
 *
 * [minHeight] keeps the buttons where the page puts them whether or not the service line exists;
 * a name of two lines is taller than it and pushes them down. [onSubtitlePlaced] reports where
 * the service line landed, so its colour is chosen against the backdrop there.
 */
@Composable
private fun ShortcutHeading(
        title: String,
        subtitle: String?,
        subtitleColor: Color,
        minHeight: Dp,
        onSubtitlePlaced: (TextBand) -> Unit
) {
    val font = LocalWatchUiFontFamily.current
    Column(
            modifier = Modifier.fillMaxWidth().heightIn(min = minHeight),
            horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
                text = title,
                color = Color.White,
                fontFamily = font,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                lineHeight = TITLE_LINE_DP.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() })
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(SUBTITLE_GAP_DP.dp))
            Text(
                    text = subtitle,
                    color = subtitleColor,
                    fontFamily = font,
                    fontSize = 11.sp,
                    lineHeight = SUBTITLE_LINE_DP.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        val root = coordinates.findRootCoordinates().size
                        if (root.width == 0 || root.height == 0) return@onGloballyPositioned
                        val bounds = coordinates.boundsInRoot()
                        onSubtitlePlaced(TextBand.of(
                                bounds.left / root.width, bounds.top / root.height,
                                bounds.right / root.width, bounds.bottom / root.height))
                    })
        }
    }
}

/** What the page holds below the pill: the byline and description a public preview gave. */
@Composable
private fun ShortcutAbout(creator: String?, description: String?) {
    val font = LocalWatchUiFontFamily.current
    Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!creator.isNullOrBlank()) {
            Text(
                    text = creator,
                    color = Color.White.copy(alpha = .72f),
                    fontFamily = font,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
        }
        if (!description.isNullOrBlank()) {
            if (!creator.isNullOrBlank()) Spacer(Modifier.height(3.dp))
            Text(
                    text = description,
                    color = Color.White.copy(alpha = .6f),
                    fontFamily = font,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * A round, icon-only button that sinks slightly while pressed.
 *
 * Icon-only on purpose: Play and Shuffle are the two most recognisable glyphs in music, and a label
 * under each would push them off a small round screen. The label still exists - for TalkBack.
 */
@Composable
private fun RoundActionButton(
        iconRes: Int,
        label: String,
        container: Color,
        content: Color,
        size: Dp,
        onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
            targetValue = if (pressed) .9f else 1f,
            animationSpec = tween(durationMillis = 90),
            label = "roundButtonPress")
    Box(
            modifier = Modifier
                    .size(size)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(CircleShape)
                    .background(container)
                    .clickable(
                            interactionSource = interaction,
                            indication = LocalIndication.current,
                            onClick = onClick)
                    .semantics {
                        role = Role.Button
                        contentDescription = label
                    },
            contentAlignment = Alignment.Center
    ) {
        Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(size * .46f))
    }
}

/**
 * "Open on phone": a capsule centred under the buttons, as wide as its label and never wider than
 * the glass allows where it rests ([ShortcutDetailGeometry.pillMaxWidth]), so it is whole on the
 * screen rather than running into the bezel - a long translation ellipsizes instead.
 *
 * It carries the mark of the app the link opens in, so it says where the link goes before it is
 * read: the app's own status-bar glyph when the phone sent it, the service's mark when it did not,
 * and a phone only for a service neither knows.
 */
@Composable
private fun OpenOnPhonePill(
        mark: Bitmap?,
        service: ShortcutDestination.Service?,
        maxWidth: Dp,
        topGap: Dp,
        onClick: () -> Unit
) {
    Box(Modifier.fillMaxWidth().padding(top = topGap), contentAlignment = Alignment.Center) {
        Row(
                modifier = Modifier
                        .widthIn(max = maxWidth)
                        .height(ShortcutDetailGeometry.PILL_HEIGHT_DP.dp)
                        .clip(CircleShape)
                        .background(Color(WatchTheme.SURFACE_DARK))
                        .clickable(onClick = onClick)
                        .semantics(mergeDescendants = true) { role = Role.Button }
                        // Measured on a 233dp watch: "Abrir no celular" with its mark comes to
                        // about 140dp at these sizes, just inside the 142dp the glass allows there.
                        .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
        ) {
            PillMark(mark, service)
            Text(
                    text = stringResource(R.string.shortcut_open_on_phone),
                    color = Color.White,
                    fontFamily = LocalWatchUiFontFamily.current,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false))
        }
    }
}

/** The pill's mark, in white like its label - see [OpenOnPhonePill] for which one. */
@Composable
private fun PillMark(mark: Bitmap?, service: ShortcutDestination.Service?) {
    if (mark != null) {
        val image = remember(mark) { mark.asImageBitmap() }
        Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(Color.White),
                // A notification glyph keeps a margin inside its square (about an eighth on
                // each side) that the drawn marks do not, so it is given more room to come out
                // the same size.
                modifier = Modifier.size(20.dp))
        return
    }
    Icon(
            painter = painterResource(when (service) {
                ShortcutDestination.Service.YOUTUBE_MUSIC -> R.drawable.service_mark_youtube_music
                ShortcutDestination.Service.SPOTIFY -> R.drawable.service_mark_spotify
                ShortcutDestination.Service.SOUNDCLOUD -> R.drawable.service_mark_soundcloud
                ShortcutDestination.Service.DEEZER -> R.drawable.service_mark_deezer
                null -> R.drawable.ic_send_to_mobile
            }),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(if (service != null) 16.dp else 18.dp))
}

/** The gap the page's items are laid out with; the geometry takes it out of the spaces it fills. */
private const val ITEM_SPACING = 8f

/** The name's and the service line's line heights and the gap between them, in dp - shared by
 *  the text and the first guess at where the service line sits. */
private const val TITLE_LINE_DP = 19f
private const val SUBTITLE_LINE_DP = 14f
private const val SUBTITLE_GAP_DP = 3f

private const val COLOR_FADE_MS = 300
