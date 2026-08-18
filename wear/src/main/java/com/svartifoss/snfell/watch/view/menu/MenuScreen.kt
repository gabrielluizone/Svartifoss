package com.svartifoss.snfell.watch.view.menu

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material3.SwipeToDismissBox
import androidx.wear.compose.material3.Text
import com.svartifoss.snfell.common.CustomLists
import com.svartifoss.snfell.watch.communication.CustomListWithBitmaps
import com.svartifoss.snfell.watch.config.ButtonAction
import com.svartifoss.snfell.watch.theme.LocalWatchUiFontFamily
import com.svartifoss.snfell.watch.theme.WatchTheme
import com.svartifoss.snfell.watch.view.compose.CurvedClock
import com.svartifoss.snfell.watch.view.compose.CurvedScrollIndicator
import com.svartifoss.snfell.watch.view.compose.LoadingSpinner
import com.svartifoss.snfell.watch.view.queue.LIST_ROW_HEIGHT
import com.svartifoss.snfell.watch.view.queue.QUEUE_ARTWORK_INSET
import com.svartifoss.snfell.watch.view.queue.QueueStyle
import com.svartifoss.snfell.watch.view.queue.listRowArtworkSize
import com.svartifoss.snfell.watch.view.queue.coverFill
import com.svartifoss.snfell.watch.view.queue.blurredCover
import com.svartifoss.snfell.watch.view.queue.coverScrimFor
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.draw.paint

/** What [MenuScreen] is currently showing. */
sealed interface MenuContent {
    /** The configurable actions menu (icon + title rows). */
    data class Actions(val items: List<ButtonAction>) : MenuContent

    /** A phone-pushed custom list (playlists, search results, ...) - title + subtitle rows. */
    data class Custom(val list: CustomListWithBitmaps) : MenuContent
}

private val PILL_COLOR = Color(WatchTheme.SURFACE_DARK)
private const val SUBTITLE_ALPHA = 0.65f

/**
 * Full-screen actions/custom-list menu, replacing the old WearableDrawerLayout drawer. A pure
 * picker: rows only report clicks upward, they don't execute anything themselves.
 *
 * [alwaysPickCenter] mirrors the ALWAYS_SELECT_CENTER_ACTION preference: instead of a tap on a
 * row selecting that row, every row's tap confirms whatever row is currently centered
 * ([onCenterConfirm]). This is done by swapping each row's onClick - NOT by laying a full-screen
 * clickable over the list, which would swallow finger drags and leave only the rotary crown able
 * to scroll. [onCenterItemChanged] keeps the host activity's stem-button confirm target up to date.
 */
@Composable
fun MenuScreen(
        content: MenuContent?,
        alwaysPickCenter: Boolean,
        /** The queue's list style. Its cover variations make custom-list rows whose entry has a
         *  thumbnail render it full-bleed behind the label instead of as a 30dp circle. */
        coverStyle: QueueStyle = QueueStyle.GLASS,
        onActionClick: (index: Int) -> Unit,
        onEntryClick: (listId: String, entryId: String) -> Unit,
        onEntryLongClick: (listId: String, entryId: String) -> Unit,
        onCenterItemChanged: (Int) -> Unit,
        onCenterConfirm: () -> Unit,
        onDismiss: () -> Unit
) {
    // Guard: SwipeToDismissBox can fire onDismissed more than once in edge cases.
    var dismissed by remember { mutableStateOf(false) }
    SwipeToDismissBox(onDismissed = {
        if (!dismissed) { dismissed = true; onDismiss() }
    }) { isBackground ->
        if (!isBackground) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                val listState = rememberScalingLazyListState()
                // The old (no-rotary-param) ScalingLazyColumn overload is a deprecated
                // compatibility shim; besides leaving the crown unsupported, its legacy touch
                // input path was also the cause of swipes over the list not registering as a
                // scroll (only the crown worked). The current overload restores both.
                val rotaryBehavior = RotaryScrollableDefaults.behavior(scrollableState = listState)

                LaunchedEffect(listState) {
                    snapshotFlow { listState.centerItemIndex }.collect { onCenterItemChanged(it) }
                }

                when (content) {
                    null -> LoadingSpinner(
                            Color(WatchTheme.ACCENT_DEFAULT),
                            Modifier.align(Alignment.Center)
                    )
                    is MenuContent.Actions -> ScalingLazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = listState,
                            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 36.dp, bottom = 26.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            rotaryScrollableBehavior = rotaryBehavior
                    ) {
                        itemsIndexed(content.items) { index, action ->
                            ActionRow(
                                    action = action,
                                    onClick = if (alwaysPickCenter) onCenterConfirm
                                              else { { onActionClick(index) } }
                            )
                        }
                    }
                    is MenuContent.Custom -> {
                        ScalingLazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                state = listState,
                                contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 36.dp, bottom = 26.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                rotaryScrollableBehavior = rotaryBehavior
                        ) {
                            // Only search history supports deleting entries directly from the
                            // watch - long-press elsewhere (playlists, past-played tracks, ...)
                            // would have no clear meaning and risks an accidental deletion of
                            // something not actually deletable.
                            val deletable = content.list.listId == CustomLists.SEARCH_HISTORY

                            itemsIndexed(content.list.items) { _, item ->
                                CustomEntryRow(
                                        title = item.listItem.entryTitle,
                                        subtitle = if (item.listItem.hasEntrySubtitle()) {
                                            item.listItem.entrySubtitle
                                        } else {
                                            null
                                        },
                                        icon = item.icon,
                                        coverStyle = coverStyle,
                                        onClick = if (alwaysPickCenter) onCenterConfirm
                                                  else { { onEntryClick(content.list.listId, item.listItem.entryId) } },
                                        onLongClick = if (deletable) {
                                            { onEntryLongClick(content.list.listId, item.listItem.entryId) }
                                        } else {
                                            null
                                        }
                                )
                            }
                        }
                    }
                }

                // derivedStateOf: only recompose when the boolean flips, not on every
                // center-item change while scrolling.
                val clockVisible by remember { derivedStateOf { listState.centerItemIndex == 0 } }
                CurvedClock(visible = clockVisible)
                CurvedScrollIndicator(listState)
            }
        }
    }
}

/**
 * Whether this row leads with genuine cover art, which is the only thing grown to fill the pill.
 *
 * Reads [ButtonAction.isCoverArt] rather than `!iconTintable`, which is what it used to do and got
 * wrong in one specific, very visible way: an app-launcher icon is full-colour and therefore
 * untintable too, so "Play YT Music" was blown up to the same size as an album cover and became
 * the largest thing on the row - a solid brand mark filling a 40dp circle, next to rows where that
 * circle holds actual artwork. `isCoverArt` is the flag the phone already sets for exactly this
 * distinction ("never a generic app-launcher icon"), and the quick panel has always used it.
 *
 * So there are three sizes here, not two: real cover art fills the pill, an app icon sits at
 * [APP_ICON_SIZE] keeping its own colours, and a monochrome template glyph keeps the glyph size -
 * a tintable glyph blown up to cover size reads as a rendering mistake, not as a bigger icon.
 */
private val ButtonAction.leadsWithArtwork: Boolean
    get() = icon != null && isCoverArt

/**
 * Whether this row leads with an app-launcher icon: full-colour, so not a template glyph, but not
 * cover art either. Drawn smaller than a cover the way a launcher list does - the row is read by
 * its label, and the icon is there to confirm the choice rather than to be the choice.
 */
private val ButtonAction.leadsWithAppIcon: Boolean
    get() = icon != null && !isCoverArt && !iconTintable

/** Kept in step with `MainActivity.APP_ICON_DP`: the quick panel lists the same actions as this
 *  menu, so an app icon that differed between them would read as a bug in one of them. */
private val APP_ICON_SIZE = 26.dp

/** Monochrome template glyphs, unchanged - they were never the thing that looked wrong. */
private val GLYPH_SIZE = 30.dp

@Composable
private fun ActionRow(action: ButtonAction, onClick: () -> Unit) {
    Row(
            modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(PILL_COLOR)
                    .clickable(onClick = onClick)
                    // Artwork rows trade padding for cover: the pill keeps the same height either
                    // way, the cover just uses more of it. Glyph rows keep the wider inset, since a
                    // monochrome template icon blown up to fill the pill reads as a mistake.
                    .height(LIST_ROW_HEIGHT)
                    .padding(
                            start = if (action.leadsWithArtwork) QUEUE_ARTWORK_INSET else 16.dp,
                            end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        val icon = remember(action.icon) { action.icon?.toImageBitmapOrNull() }
        if (icon != null) {
            // Genuine cover art (e.g. a streaming shortcut's fetched artwork) gets the circular
            // clip + center-crop the custom-list rows use, so a rectangular or square thumbnail
            // sits fully inside the circle instead of showing as a raw square. App icons and
            // monochrome glyphs stay small and unclipped - see leadsWithArtwork.
            val isArtwork = action.leadsWithArtwork
            Image(
                    bitmap = icon,
                    contentDescription = null,
                    contentScale = if (isArtwork) ContentScale.Crop else ContentScale.Fit,
                    modifier = if (isArtwork) {
                        Modifier.size(listRowArtworkSize(LIST_ROW_HEIGHT)).clip(CircleShape)
                    } else {
                        // No circular clip for either: an app icon already carries its own shape
                        // (and its own background), so cropping it to a circle would cut the
                        // corners off a squircle rather than tidy it up.
                        Modifier.size(if (action.leadsWithAppIcon) APP_ICON_SIZE else GLYPH_SIZE)
                    }
            )
            Spacer(Modifier.width(10.dp))
        }
        // Ellipsis, not marquee: several rows animating at once re-runs layout every frame and
        // makes list scrolling stutter on watch hardware (see the queue's QueueRow).
        Text(
                text = action.title.orEmpty(),
                color = Color.White,
                fontFamily = LocalWatchUiFontFamily.current,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CustomEntryRow(
        title: String,
        subtitle: String?,
        icon: Bitmap?,
        coverStyle: QueueStyle,
        onClick: () -> Unit,
        onLongClick: (() -> Unit)? = null
) {
    // Only a row that actually has a thumbnail can be cover-filled; the rest keep the plain pill,
    // which matters because shortcut artwork is opt-in and most entries have none.
    val cover = if (coverStyle.isCover) icon else null
    val coverImage = remember(cover, coverStyle) {
        cover?.let { if (coverStyle == QueueStyle.COVER_BLUR) blurredCover(it) else it }
                ?.asImageBitmap()
    }
    val showsThumbnail = icon != null && (coverImage == null || coverStyle.coverKeepsThumbnail)
    Row(
            modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(PILL_COLOR)
                    .then(
                            if (coverImage != null) {
                                Modifier.coverFill(
                                        coverImage,
                                        RoundedCornerShape(26.dp),
                                        coverScrimFor(coverStyle, Color(WatchTheme.ACCENT_DEFAULT)))
                            } else {
                                Modifier
                            }
                    )
                    .combinedClickable(
                            onClick = onClick,
                            onLongClick = onLongClick
                    )
                    // Custom-list rows always lead with real cover art, so they always trade the
                    // wider inset for a larger thumbnail - see ActionRow for the glyph case.
                    .height(LIST_ROW_HEIGHT)
                    .padding(
                            start = if (showsThumbnail) QUEUE_ARTWORK_INSET else 16.dp,
                            end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        if (showsThumbnail && icon != null) {
            Image(
                    bitmap = icon.asImageBitmap(),
                    contentDescription = null,
                    // Circular clip + center-crop so album/cover thumbnails sit fully inside the
                    // circle, and a rectangular source fills it without letterbox bars.
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                            .size(listRowArtworkSize(LIST_ROW_HEIGHT))
                            .clip(CircleShape)
            )
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                    text = title,
                    color = Color.White,
                    fontFamily = LocalWatchUiFontFamily.current,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                        text = subtitle,
                        color = Color.White.copy(alpha = SUBTITLE_ALPHA),
                        fontFamily = LocalWatchUiFontFamily.current,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Converts the phone-sent icon Drawable for Compose. Null if the drawable can't be rasterized. */
private fun Drawable.toImageBitmapOrNull(): ImageBitmap? {
    (this as? BitmapDrawable)?.bitmap?.let { return it.asImageBitmap() }

    val width = intrinsicWidth
    val height = intrinsicHeight
    if (width <= 0 || height <= 0) {
        return null
    }

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    setBounds(0, 0, width, height)
    draw(canvas)
    return bitmap.asImageBitmap()
}
