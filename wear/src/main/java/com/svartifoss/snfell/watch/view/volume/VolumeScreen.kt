package com.svartifoss.snfell.watch.view.volume

import android.graphics.Bitmap
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.R as commonR
import com.svartifoss.snfell.watch.view.CircularVolumeBar
import com.svartifoss.snfell.watch.view.VolumeControlAxis
import com.svartifoss.snfell.watch.view.VolumeLayout
import com.svartifoss.snfell.watch.view.VolumeStyle
import com.svartifoss.snfell.watch.view.panel.PanelAppearance
import com.svartifoss.snfell.watch.view.panel.PanelBackdrop
import com.svartifoss.snfell.watch.view.panel.PanelCenterPlayPauseTarget
import com.svartifoss.snfell.watch.view.panel.PanelReadout
import com.svartifoss.snfell.watch.view.panel.PanelReadoutText
import com.svartifoss.snfell.watch.view.panel.PanelScaffold
import com.svartifoss.snfell.watch.view.panel.roundSideInsetFraction
import kotlin.math.roundToInt

/**
 * The dedicated volume screen: the user's *configured* volume panel, drawn by the same
 * [CircularVolumeBar] the transient overlay uses, over the Shared panel appearance backdrop and in
 * the album's colours.
 *
 * The arc is draggable here, as it is on the player. It claims the gesture only inside its own
 * band (`isTouchOnArc`) and takes the touch stream with `requestDisallowInterceptTouchEvent`, so a
 * drag on the arc scrubs the volume while a swipe anywhere else still dismisses the screen. That is
 * the sharing the first version refused to attempt - it turned touch off entirely, which fixed a
 * collision nobody had by removing the control everybody reaches for first.
 *
 * The step buttons follow [VolumeControlAxis]: above and below for an arc that fills upwards, left
 * and right for one that fills sideways, so "louder" is never pressed at ninety degrees to the
 * direction the fill travels.
 */
@Composable
fun VolumeScreen(
        volume: Float,
        appearance: PanelAppearance,
        backdrop: PanelBackdrop,
        albumArt: Bitmap?,
        screenFace: String,
        themeAccentColor: Int,
        uiTypeface: Typeface?,
        onVolumeChange: (Float) -> Unit,
        onStep: (Float) -> Unit,
        onTogglePlayPause: () -> Unit,
        onDismiss: () -> Unit
) {
    PanelScaffold(appearance, backdrop, albumArt, onDismiss) {
        val configuration = LocalConfiguration.current
        val screenWidth = configuration.screenWidthDp.dp
        val screenHeight = configuration.screenHeightDp.dp
        val layout = VolumeLayout.fromPref(appearance.volumeLayout)
        val axis = VolumeControlAxis.forLayout(layout)

        AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { CircularVolumeBar(it) },
                update = { bar ->
                    bar.barStyle = VolumeStyle.fromPref(appearance.volumeStyle)
                    bar.barLayout = layout
                    bar.progressColor = appearance.triad.primary
                    bar.secondaryColor = appearance.triad.secondary
                    bar.tertiaryColor = appearance.triad.tertiary
                    // Assigned before the callback so the first frame's write cannot be reported
                    // back to the phone as if the user had made it.
                    bar.volume = volume
                    bar.onVolumeChanged = onVolumeChange
                })

        PanelReadoutText(
                style = appearance.readoutStyle,
                content = stringResource(
                        R.string.volume_percent_format, (volume * 100).roundToInt()),
                accentColor = appearance.triad.primary,
                secondaryColor = appearance.triad.secondary,
                themeAccentColor = themeAccentColor,
                screenFace = screenFace,
                typeface = uiTypeface,
                modifier = Modifier.align(Alignment.Center),
                lightArcBehind = VolumeStyle.fromPref(appearance.volumeStyle) == VolumeStyle.LIGHT)

        PanelCenterPlayPauseTarget(onClick = onTogglePlayPause)

        when (axis) {
            VolumeControlAxis.VERTICAL -> {
                // Louder on top, quieter below - the order the arc itself fills in. The row band is
                // the full height, so the inset that matters is the vertical one; the buttons are
                // centred horizontally, where the chord is widest at both ends.
                Column(
                        modifier = Modifier
                                .align(Alignment.Center)
                                .fillMaxHeight()
                                .padding(vertical = screenHeight * EDGE_MARGIN_FRACTION),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                ) {
                    StepButton(
                            icon = commonR.drawable.action_volume_up,
                            description = stringResource(R.string.action_name_volume_up),
                            appearance = appearance,
                            onClick = { onStep(VOLUME_STEP) })
                    StepButton(
                            icon = commonR.drawable.action_volume_down,
                            description = stringResource(R.string.action_name_volume_down),
                            appearance = appearance,
                            onClick = { onStep(-VOLUME_STEP) })
                }
            }
            VolumeControlAxis.HORIZONTAL -> {
                Row(
                        modifier = Modifier
                                .align(Alignment.Center)
                                .fillMaxWidth()
                                .padding(horizontal = screenWidth * EDGE_MARGIN_FRACTION),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    StepButton(
                            icon = commonR.drawable.action_volume_down,
                            description = stringResource(R.string.action_name_volume_down),
                            appearance = appearance,
                            onClick = { onStep(-VOLUME_STEP) })
                    StepButton(
                            icon = commonR.drawable.action_volume_up,
                            description = stringResource(R.string.action_name_volume_up),
                            appearance = appearance,
                            onClick = { onStep(VOLUME_STEP) })
                }
            }
        }

    }
}

/**
 * How far the step buttons sit from the glass. A round display's chord is at its widest across the
 * centre line, so the horizontal pair needs almost no inset, while the vertical pair is pushed in
 * from the top and bottom where the glass curves away.
 */
private const val EDGE_MARGIN_FRACTION = 0.055f

/**
 * A round step button, tinted from the panel's own palette rather than a flat white - the screen is
 * meant to read as part of the player it was opened from. Local to this screen rather than reused
 * from `FaceChrome.FaceTapTarget`: that composable is package-private to the now-playing faces and
 * this screen is not one.
 */
@Composable
internal fun StepButton(
        icon: Int,
        description: String,
        appearance: PanelAppearance,
        onClick: () -> Unit
) {
    val fill = PanelReadout.tonalSurface(appearance.triad.primary, .34f)
    Box(
            modifier = Modifier
                    .size(BUTTON_SIZE)
                    .clip(CircleShape)
                    .background(Color(fill))
                    .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
    ) {
        Icon(
                painter = painterResource(icon),
                contentDescription = description,
                tint = Color(PanelReadout.contrastingIconColor(fill)),
                modifier = Modifier.size(BUTTON_SIZE * .5f))
    }
}

private val BUTTON_SIZE = 52.dp
