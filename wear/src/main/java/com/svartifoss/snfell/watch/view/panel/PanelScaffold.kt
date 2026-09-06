package com.svartifoss.snfell.watch.view.panel

import android.graphics.Bitmap
import android.graphics.Typeface
import android.view.View
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.wear.compose.material3.SwipeToDismissBox
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.RoundScreenText

/**
 * The shell every dedicated panel screen (volume, progress) is built in: the player's own backdrop
 * with the *Shared panel appearance* background composited on top of it, then the screen's own
 * content, inside a [SwipeToDismissBox].
 *
 * Getting that composition right is the whole point of this composable, and it took two attempts.
 * The panel backgrounds are not backgrounds in their own right - on the player they are painted
 * over the live artwork, its background treatment and its shading, which is what the double-tap
 * quick panel shows. Painting only the panel background here left every translucent treatment
 * resolving over black, so the same setting produced two visibly different screens. [PanelBackdropView]
 * reproduces the stack `activity_main.xml` declares, layer for layer.
 *
 * The panel view a screen puts inside this is deliberately **display-only** (`touchAdjustEnabled`
 * / `touchSeekingEnabled` off). A bezel arc or edge ring occupies exactly the band the Wear
 * swipe-back gesture starts in, and an Android View that claims a drag calls
 * `requestDisallowInterceptTouchEvent`, so the two cannot both have it: whichever wins, the other
 * silently stops working. On a screen that stays open until it is dismissed, dismissing has to
 * win - which is the complaint the old centre ring produced in its clearest form, since it sat
 * where every swipe passes. Adjustment is by the crown and the on-screen buttons instead.
 */
@Composable
fun PanelScaffold(
        appearance: PanelAppearance,
        backdrop: PanelBackdrop,
        albumArt: Bitmap?,
        /** Keep the opaque black window visible until an uncached cover palette is ready. */
        showBackdrop: Boolean = true,
        onDismiss: () -> Unit,
        content: @Composable BoxScope.() -> Unit
) {
    var dismissed by remember { mutableStateOf(false) }
    SwipeToDismissBox(onDismissed = {
        if (!dismissed) {
            dismissed = true
            onDismiss()
        }
    }) { isBackground ->
        if (isBackground) return@SwipeToDismissBox

        Box(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center
        ) {
            if (showBackdrop) {
                AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { PanelBackdropView(it) },
                        update = { it.render(appearance, backdrop, albumArt) })
                content()
            }
        }
    }
}

/**
 * The readout - seek time or volume percentage - styled by `MiscPreferences.WEAR_SEEK_STYLE`.
 *
 * A `TextView` hosted in Compose rather than a `Text`, so it goes through the identical
 * [PanelReadout] branch the transient overlay uses. Twenty-odd styles reimplemented in Compose
 * would be a second table to keep in step, and the first setting to drift would be invisible until
 * someone compared the two surfaces side by side.
 */
@Composable
fun PanelReadoutText(
        style: String,
        content: String,
        accentColor: Int,
        secondaryColor: Int,
        themeAccentColor: Int,
        screenFace: String,
        typeface: Typeface?,
        modifier: Modifier = Modifier,
        /** True while the volume arc is painted in the LIGHT style - see
         *  [PanelReadout.applyLightArcContrast]. */
        lightArcBehind: Boolean = false
) {
    AndroidView(
            modifier = modifier,
            factory = {
                TextView(it).apply {
                    // TextView's legacy extra font padding makes a mathematically centred clock
                    // or percentage look low inside Compose. The readout owns its padding in
                    // PanelReadout, so the platform padding is both redundant and asymmetric.
                    includeFontPadding = false
                    textAlignment = View.TEXT_ALIGNMENT_CENTER
                }
            },
            update = { view ->
                PanelReadout.apply(
                        view, style, content, accentColor, secondaryColor, themeAccentColor,
                        screenFace, view.resources.displayMetrics.density, typeface)
                PanelReadout.applyLightArcContrast(view, style, lightArcBehind)
            })
}

/**
 * Invisible centre action shared by the dedicated progress and volume panels. It deliberately has
 * no fill or icon: the readout remains the visual centre, exactly as requested, while this layer
 * supplies the same play/pause tap that the primary player's centre owns.
 *
 * The main player can afford a 110dp zone, but that would overlap these panels' side controls. A
 * 64dp circle preserves a comfortable Wear touch target without stealing their taps.
 */
@Composable
fun PanelCenterPlayPauseTarget(onClick: () -> Unit) {
    val label = stringResource(R.string.action_name_play_pause)
    Box(
            modifier = Modifier
                    .size(PANEL_CENTER_TAP_SIZE)
                    .clip(CircleShape)
                    .semantics { contentDescription = label }
                    .clickable(onClickLabel = label, onClick = onClick))
}

private val PANEL_CENTER_TAP_SIZE = 64.dp

/**
 * Side inset that keeps a row of controls spanning [top]..[bottom] (fractions of screen height,
 * measured from the top) inside a round display.
 *
 * Both screens shipped with rows laid out as `fillMaxWidth()` plus a hand-picked horizontal
 * padding, which is only ever right at the vertical centre: the five-button transport row sat low
 * enough that the chord had narrowed well past it and the outer buttons were clipped by the bezel.
 * [RoundScreenText] already owns this geometry for text blocks; a control row has exactly the same
 * problem.
 */
fun roundSideInsetFraction(top: Float, bottom: Float): Float =
        RoundScreenText.sideInsetFor(top, bottom)
