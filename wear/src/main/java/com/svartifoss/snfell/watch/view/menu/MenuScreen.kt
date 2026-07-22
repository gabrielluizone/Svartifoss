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
import com.svartifoss.snfell.watch.theme.GoogleSansFamily
import com.svartifoss.snfell.watch.theme.WatchTheme
import com.svartifoss.snfell.watch.view.compose.CurvedClock
import com.svartifoss.snfell.watch.view.compose.CurvedScrollIndicator
import com.svartifoss.snfell.watch.view.compose.LoadingSpinner
import com.svartifoss.snfell.watch.view.queue.QueueStyle
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

@Composable
private fun ActionRow(action: ButtonAction, onClick: () -> Unit) {
    Row(
            modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(PILL_COLOR)
                    .clickable(onClick = onClick)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        val icon = remember(action.icon) { action.icon?.toImageBitmapOrNull() }
        if (icon != null) {
            // A full-colour cover (e.g. a streaming shortcut's fetched artwork, iconTintable=false)
            // gets the same circular clip + center-crop the custom-list rows use, so a rectangular
            // or square thumbnail sits fully inside the circle instead of showing as a raw square.
            // Monochrome action glyphs (iconTintable=true) stay unclipped - circling a glyph looks
            // wrong. Mirrors CustomEntryRow's thumbnail and the quick panel's isArtwork rule.
            val isArtwork = !action.iconTintable
            Image(
                    bitmap = icon,
                    contentDescription = null,
                    contentScale = if (isArtwork) ContentScale.Crop else ContentScale.Fit,
                    modifier = if (isArtwork) {
                        Modifier.size(30.dp).clip(CircleShape)
                    } else {
                        Modifier.size(30.dp)
                    }
            )
            Spacer(Modifier.width(10.dp))
        }
        // Ellipsis, not marquee: several rows animating at once re-runs layout every frame and
        // makes list scrolling stutter on watch hardware (see the queue's QueueRow).
        Text(
                text = action.title.orEmpty(),
                color = Color.White,
                fontFamily = GoogleSansFamily,
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
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null && (coverImage == null || coverStyle.coverKeepsThumbnail)) {
            Image(
                    bitmap = icon.asImageBitmap(),
                    contentDescription = null,
                    // Circular clip + center-crop so album/cover thumbnails sit fully inside the
                    // circle, and a rectangular source fills it without letterbox bars.
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
            )
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                    text = title,
                    color = Color.White,
                    fontFamily = GoogleSansFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                        text = subtitle,
                        color = Color.White.copy(alpha = SUBTITLE_ALPHA),
                        fontFamily = GoogleSansFamily,
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
