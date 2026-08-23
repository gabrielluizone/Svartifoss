package com.svartifoss.snfell.watch.view.facepicker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.SwipeToDismissBox
import androidx.wear.compose.material3.Text
import com.svartifoss.snfell.R
import com.svartifoss.snfell.watch.theme.LocalWatchUiFontFamily
import com.svartifoss.snfell.watch.theme.WatchTheme
import com.svartifoss.snfell.watch.view.compose.CurvedClock
import com.svartifoss.snfell.watch.view.compose.CurvedScrollIndicator

/**
 * On-watch face picker: change the now-playing face from the wrist instead of from the phone app.
 *
 * **Deliberately a vertical list, not a horizontal gallery.** The system's own watch-face picker
 * pages sideways, but a left-to-right drag is also Wear OS's global dismiss gesture, and a
 * horizontal pager here would have to fight it for every swipe - the failure mode being that the
 * user leaves the app when they meant to see the next face. Scrolling vertically leaves the dismiss
 * gesture completely untouched and gets rotary/bezel scrolling for free.
 *
 * The dismiss gesture is then handled rather than merely avoided: [SwipeToDismissBox] catches the
 * right-swipe and closes *this screen* back to the player, the same contract the queue and menu
 * screens use. Because the picker is its own Activity layered over MainActivity, that swipe can
 * never exit the app - which is precisely the risk of building it as an overlay inside the player.
 *
 * The user's saved themes get their own titled section: grouped and labelled with the face they
 * are based on, they read as "mine" instead of as more built-ins with odd names.
 */
@Composable
fun FacePickerScreen(
        builtIn: List<WatchFaceOption>,
        custom: List<WatchFaceOption>,
        selectedFace: String,
        accentColor: Color,
        phoneConnected: Boolean,
        onSelect: (WatchFaceOption) -> Unit,
        onDismiss: () -> Unit
) {
    // Same guard as the queue screen: the system dismiss and the Compose gesture can both fire.
    var dismissed by remember { mutableStateOf(false) }
    SwipeToDismissBox(onDismissed = {
        if (!dismissed) { dismissed = true; onDismiss() }
    }) { isBackground ->
        if (!isBackground) {
            Box(
                    Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                            // A faint wash of the playing album's colour, so the picker belongs to
                            // the track on screen rather than looking like a system dialog.
                            .background(
                                    Brush.verticalGradient(
                                            listOf(
                                                    accentColor.copy(alpha = .22f),
                                                    Color.Transparent,
                                                    Color.Transparent)))
            ) {
                FaceList(builtIn, custom, selectedFace, accentColor, phoneConnected, onSelect)
            }
        }
    }
}

@Composable
private fun FaceList(
        builtIn: List<WatchFaceOption>,
        custom: List<WatchFaceOption>,
        selectedFace: String,
        accentColor: Color,
        phoneConnected: Boolean,
        onSelect: (WatchFaceOption) -> Unit
) {
    val listState = rememberScalingLazyListState()

    // Item indices, so the opening scroll lands on the right row. The order below is: header,
    // then (if any) the custom section label and its rows, then the built-in label and its rows.
    // Must be kept in step with the list itself - the two disagreeing sends the opening scroll to
    // a neighbouring row, which reads as the picker having lost track of the current face.
    val customStart = 2
    val builtInStart = if (custom.isEmpty()) 2 else customStart + custom.size + 1
    val selectedIndex = custom.indexOfFirst { it.key == selectedFace }
            .takeIf { it >= 0 }?.let { customStart + it }
            ?: builtIn.indexOfFirst { it.key == selectedFace }
                    .takeIf { it >= 0 }?.let { builtInStart + it }

    // Open on the face already in use rather than at the top. With seventeen built-in entries plus
    // the user's own themes the current one is usually off-screen, and a picker that cannot show
    // you what you have now makes you scroll just to get your bearings.
    LaunchedEffect(selectedIndex) {
        selectedIndex?.let { listState.scrollToItem(it) }
    }

    Box(Modifier.fillMaxSize()) {
        ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(
                        start = 8.dp, end = 8.dp, top = 32.dp, bottom = 30.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                // Same reason as the queue and menu screens: the no-rotary overload is a deprecated
                // shim whose legacy touch path breaks scrolling.
                rotaryScrollableBehavior = RotaryScrollableDefaults.behavior(scrollableState = listState)
        ) {
            item { PickerHeader(phoneConnected) }

            // The user's own themes come first, ahead of the built-in list. Someone who has made
            // themes is almost always switching between *those*, and behind seventeen built-in
            // rows they were a scroll away every time. Omitted entirely when there are none: an
            // empty titled section is a promise of content that never arrives, and the phone app
            // is the only place themes can be made.
            if (custom.isNotEmpty()) {
                item {
                    SectionLabel(stringResource(R.string.face_picker_section_custom), accentColor)
                }
                items(custom) { option ->
                    FaceRow(option, selectedFace, accentColor, onSelect)
                }
            }

            item {
                SectionLabel(stringResource(R.string.face_picker_section_builtin), accentColor)
            }
            items(builtIn) { option ->
                FaceRow(option, selectedFace, accentColor, onSelect)
            }
        }

        CurvedClock(visible = true)
        CurvedScrollIndicator(listState)
    }
}

/**
 * `items` taking a plain list; the stock overload needs an explicit key and every call site here
 * keys the same way (the face key is unique across both sections by construction).
 */
private fun androidx.wear.compose.foundation.lazy.ScalingLazyListScope.items(
        options: List<WatchFaceOption>,
        row: @Composable (WatchFaceOption) -> Unit
) {
    options.forEach { option ->
        item { row(option) }
    }
}

@Composable
private fun PickerHeader(phoneConnected: Boolean) {
    Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
                text = stringResource(R.string.face_picker_title),
                color = Color.White,
                fontFamily = LocalWatchUiFontFamily.current,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                maxLines = 1
        )
        // Stated up front rather than discovered later: with no phone the choice still applies
        // here, but the phone owns the setting and will re-assert its own on the next sync, so the
        // change is not durable until they reconnect.
        if (!phoneConnected) {
            Spacer(Modifier.height(3.dp))
            Text(
                    text = stringResource(R.string.face_picker_offline),
                    color = Color.White.copy(alpha = .55f),
                    fontFamily = LocalWatchUiFontFamily.current,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String, accentColor: Color) {
    Text(
            text = text,
            color = accentColor.copy(alpha = .85f),
            fontFamily = LocalWatchUiFontFamily.current,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 2.dp)
    )
}

@Composable
private fun FaceRow(
        option: WatchFaceOption,
        selectedFace: String,
        accentColor: Color,
        onSelect: (WatchFaceOption) -> Unit
) {
    val selected = option.key == selectedFace
    val label = option.customName ?: stringResource(option.labelRes)
    val shape = RoundedCornerShape(26.dp)
    // The active face is the accent pill; the rest are the near-black idle surface the queue and
    // menu use, so the picker looks like part of the app rather than a system dialog.
    val background = if (selected) {
        Color(WatchTheme.accentForSurface(accentColor.toArgb()))
    } else {
        Color(WatchTheme.SURFACE_DARK)
    }
    val onSurface = if (selected) Color.Black else Color.White
    Row(
            modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(background)
                    .then(
                            if (selected) {
                                Modifier.border(1.dp, accentColor.copy(alpha = .6f), shape)
                            } else {
                                Modifier
                            }
                    )
                    .clickable { onSelect(option) }
                    .semantics { this.selected = selected }
                    .height(46.dp)
                    .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
                painter = painterResource(R.drawable.ic_face_palette),
                contentDescription = null,
                tint = onSurface.copy(alpha = if (selected) .8f else .65f),
                modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                    text = label,
                    color = onSurface,
                    fontFamily = LocalWatchUiFontFamily.current,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
            )
            // A saved theme is a *look* layered on a built-in face, and the miniature can only show
            // the face underneath. Naming it stops the preview from reading as wrong.
            if (option.isCustomTheme) {
                Text(
                        text = stringResource(
                                R.string.face_picker_custom_base,
                                stringResource(faceLabelRes(option.baseFace))),
                        color = onSurface.copy(alpha = .6f),
                        fontFamily = LocalWatchUiFontFamily.current,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (selected) {
            Spacer(Modifier.width(6.dp))
            Box(
                    modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = .55f))
            )
        }
    }
}

@Composable
private fun faceLabelRes(faceKey: String): Int =
        WatchFaceCatalog.labelFor(faceKey) ?: R.string.face_name_classic
