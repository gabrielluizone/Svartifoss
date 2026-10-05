package com.svartifoss.snfell.watch.view.shortcut

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.AlbumAccentSource
import com.svartifoss.snfell.common.BitmapBlur
import com.svartifoss.snfell.common.SwatchInfo
import com.svartifoss.snfell.common.selectPrimaryAccent
import com.svartifoss.snfell.proto.ShortcutPlayMode
import com.svartifoss.snfell.watch.theme.LocalWatchUiFontFamily
import com.svartifoss.snfell.watch.theme.WatchTheme
import com.svartifoss.snfell.watch.view.compose.CurvedClock
import com.svartifoss.snfell.watch.view.compose.CurvedScrollIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
         */
        val cover: Bitmap? = null,
        /**
         * The player's mark, for when no cover reached the watch at all - an older phone build, or
         * a link to an app the phone could draw nothing for. Shown in the cover's place instead of
         * a generic glyph, so the screen still says which player the link opens in.
         */
        val mark: Bitmap? = null,
        /** Whether [mark] is a white template glyph (drawn white) rather than a launcher icon. */
        val markTintable: Boolean = false
)

/**
 * A streaming shortcut's own screen: its cover, its name, and Play beside Shuffle - the detail view
 * the music apps on the watch open for a playlist, adopted here for the links the phone has saved.
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
        accentSource: AlbumAccentSource,
        /** The colour the screen uses when the shortcut has no cover of its own. */
        fallbackAccent: Color,
        onPlay: (ShortcutPlayMode) -> Unit,
        onOpenOnPhone: () -> Unit
) {
    // A launcher icon standing in for the cover has colour of its own - the player's - and the
    // screen takes it, so even without a cover it is not one fixed colour. A template glyph is
    // white, so it leaves the caller's accent standing.
    val colourSource = detail.cover ?: detail.mark?.takeUnless { detail.markTintable }
    val accent = rememberCoverAccent(colourSource, accentSource) ?: fallbackAccent
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    // Proportional rather than fixed, so a 192dp watch and a 233dp one both keep the cover, the
    // name and the two buttons on the first screen, the buttons above the narrowing bottom of the
    // circle; clamped at both ends so neither size turns the composition into a thumbnail or a
    // poster. With fixed sizes tuned on 233dp, a 192dp screen put the buttons' lower edge past it.
    val topInset = (screenWidth * .12f).coerceIn(22.dp, 32.dp)
    val coverSize = (screenWidth * .32f).coerceIn(56.dp, 92.dp)
    val buttonSize = (screenWidth * .24f).coerceIn(46.dp, 58.dp)
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        ShortcutBackdrop(detail.cover, accent)
        ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                // Laid out from the top like a page, not centred on its second row like a list:
                // the cover belongs under the clock, with the name and the buttons below it.
                autoCentering = null,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = topInset, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                // A page rather than a long list: only a light version of the edge scaling, so the
                // buttons at the bottom of a small screen are not shrunk to an awkward size.
                scalingParams = ScalingLazyColumnDefaults.scalingParams(edgeScale = .9f, edgeAlpha = .85f),
                rotaryScrollableBehavior = RotaryScrollableDefaults.behavior(scrollableState = listState)
        ) {
            item(key = "cover") {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ShortcutCover(detail.cover, detail.mark, detail.markTintable, accent, coverSize)
                }
            }
            item(key = "title") {
                ShortcutTitle(detail.title, detail.creator, detail.subtitle, detail.description)
            }
            item(key = "buttons") {
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    // Play is the primary action and wears the accent, filled; its black glyph is
                    // legible on any accent because accentForSurface keeps the fill in a light band.
                    RoundActionButton(
                            iconRes = com.svartifoss.snfell.common.R.drawable.action_play_filled,
                            label = stringResource(R.string.action_name_play),
                            container = Color(WatchTheme.accentForSurface(accent.toArgb())),
                            content = Color.Black,
                            size = buttonSize,
                            onClick = { onPlay(ShortcutPlayMode.IN_ORDER) })
                    if (detail.shuffleable) {
                        RoundActionButton(
                                iconRes = com.svartifoss.snfell.common.R.drawable.action_shuffle,
                                label = stringResource(R.string.quick_action_shuffle),
                                container = tonal(accent, .24f),
                                content = Color.White,
                                size = buttonSize,
                                onClick = { onPlay(ShortcutPlayMode.SHUFFLE) })
                    }
                }
            }
            item(key = "phone") {
                OpenOnPhoneRow(accent, onOpenOnPhone)
            }
        }

        // Over the cover's top edge only while the page sits at rest at the top.
        val clockVisible by remember { derivedStateOf { listState.centerItemIndex <= 1 } }
        CurvedClock(visible = clockVisible)
        CurvedScrollIndicator(listState)
    }
}

/**
 * The cover, blurred across the whole screen under a darkening wash of its own colour - the ground
 * the music apps' playlist pages use, and the same blur the player's backdrop uses, so the two read
 * as one family. Without a cover it is the wash alone.
 *
 * The blur runs off the main thread; until it lands the wash is already there, so the screen opens
 * coloured rather than black and only gains its texture.
 */
@Composable
private fun ShortcutBackdrop(cover: Bitmap?, accent: Color) {
    val blurred by produceState<Bitmap?>(null, cover) {
        value = cover?.let { withContext(Dispatchers.Default) { BitmapBlur.blur(it, BACKDROP_BLUR_PX) } }
    }
    val fade by animateFloatAsState(
            targetValue = if (blurred != null) 1f else 0f,
            animationSpec = tween(durationMillis = 260),
            label = "shortcutBackdropFade")
    blurred?.let { picture ->
        val image = remember(picture) { picture.asImageBitmap() }
        Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = fade * .55f })
    }
    val deep = tonal(accent, .18f)
    Box(
            Modifier
                    .fillMaxSize()
                    .background(
                            Brush.verticalGradient(
                                    0f to deep.copy(alpha = .55f),
                                    .45f to deep.copy(alpha = .78f),
                                    1f to Color.Black)))
}

@Composable
private fun ShortcutCover(
        cover: Bitmap?,
        mark: Bitmap?,
        markTintable: Boolean,
        accent: Color,
        size: Dp
) {
    val shape = RoundedCornerShape(size * .16f)
    if (cover != null) {
        val image = remember(cover) { cover.asImageBitmap() }
        Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                        .size(size)
                        .clip(shape)
                        .border(1.dp, Color.White.copy(alpha = .14f), shape))
    } else {
        // No cover reached the watch. The player's mark stands in for it, on a tile in the
        // screen's colour - the mark's own colour for a launcher icon, otherwise the colour of
        // what is playing - so the placeholder still says where the link plays.
        Box(
                modifier = Modifier
                        .size(size)
                        .clip(shape)
                        .background(Brush.linearGradient(
                                listOf(tonal(accent, .40f), tonal(accent, .20f))))
                        .border(1.dp, Color.White.copy(alpha = .10f), shape),
                contentAlignment = Alignment.Center
        ) {
            if (mark != null) {
                val image = remember(mark) { mark.asImageBitmap() }
                Image(
                        bitmap = image,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        colorFilter = if (markTintable) ColorFilter.tint(Color.White) else null,
                        modifier = Modifier.size(size * if (markTintable) .44f else .56f))
            } else {
                Icon(
                        painter = painterResource(R.drawable.ic_shortcut_queue_music),
                        contentDescription = null,
                        tint = Color(WatchTheme.accentForText(accent.toArgb())),
                        modifier = Modifier.size(size * .42f))
            }
        }
    }
}

@Composable
private fun ShortcutTitle(title: String, creator: String?, subtitle: String?, description: String?) {
    Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
                text = title,
                color = Color.White,
                fontFamily = LocalWatchUiFontFamily.current,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis)
        if (!creator.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                    text = creator,
                    color = Color.White.copy(alpha = .68f),
                    fontFamily = LocalWatchUiFontFamily.current,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
        }
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = .56f),
                    fontFamily = LocalWatchUiFontFamily.current,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
        }
        if (!description.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                    text = description,
                    color = Color.White.copy(alpha = .62f),
                    fontFamily = LocalWatchUiFontFamily.current,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
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
 * A compact chip centred under the buttons rather than a full-width row: it sits at the bottom of
 * a round screen, where the display is narrowest and a full-width pill loses both ends to the
 * bezel - and it is the secondary action, so it should not be drawn as wide as the primary ones.
 */
@Composable
private fun OpenOnPhoneRow(accent: Color, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
                modifier = Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        // In the cover's colour, a step quieter than Shuffle's container, so the
                        // three controls read as one family and this one still as the secondary.
                        .background(tonal(accent, .20f).copy(alpha = .92f))
                        .clickable(onClick = onClick)
                        .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                    painter = painterResource(R.drawable.ic_send_to_mobile),
                    contentDescription = null,
                    tint = Color(WatchTheme.accentForText(accent.toArgb())),
                    modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                    text = stringResource(R.string.shortcut_open_on_phone),
                    color = Color.White,
                    fontFamily = LocalWatchUiFontFamily.current,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * The cover's own colour, chosen the way the player chooses the album's ([selectPrimaryAccent],
 * under the user's [AlbumAccentSource]) so a shortcut and the same album playing look alike.
 *
 * Null until extraction lands, and null for no cover - the caller's fallback then stands. The
 * extraction is deliberately not stored in `AlbumPaletteCache`: that one-entry cache belongs to the
 * cover that is *playing*, and a shortcut's cover written into it would evict the player's answer.
 */
@Composable
fun rememberCoverAccent(cover: Bitmap?, accentSource: AlbumAccentSource): Color? {
    var accent by remember(cover, accentSource) { mutableStateOf<Color?>(null) }
    LaunchedEffect(cover, accentSource) {
        if (cover == null || cover.isRecycled) return@LaunchedEffect
        accent = withContext(Dispatchers.Default) {
            val palette = Palette.from(cover).generate()
            val vibrant = palette.vibrantSwatch?.let { SwatchInfo(it.rgb, it.population) }
            val swatches = palette.swatches.map { SwatchInfo(it.rgb, it.population) }
            selectPrimaryAccent(vibrant, swatches, accentSource)?.let { Color(it) }
        }
    }
    return accent
}

/** A dark tone of [accent] at [lightness], saturation held in a readable band - the same
 *  treatment the queue's tonal rows use. */
private fun tonal(accent: Color, lightness: Float): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(accent.toArgb(), hsl)
    hsl[1] = hsl[1].coerceIn(.25f, .60f)
    hsl[2] = lightness
    return Color(ColorUtils.HSLToColor(hsl))
}

/** Enough to turn the cover into colour and light without leaving recognisable shapes. */
private const val BACKDROP_BLUR_PX = 26f
