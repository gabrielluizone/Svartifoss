package com.svartifoss.snfell.watch.view.panel

import android.content.SharedPreferences
import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.matejdro.wearutils.preferences.definition.PreferenceDefinition
import com.svartifoss.snfell.common.AlbumAccentSource
import com.svartifoss.snfell.common.AppearanceContext
import com.svartifoss.snfell.common.FaceScopedPreferences
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.common.OverlayBackdrop
import com.svartifoss.snfell.common.OverlayBackdropResolver

/**
 * The configured ground for a full screen that is not a panel - the queue and the lyrics screen.
 *
 * Those two were the only surfaces in the app painting a flat black field: the player draws the
 * whole background stack, and the volume, progress and quick-action panels draw that stack with
 * the *Shared panel appearance* background composited over it. So the same theme produced three
 * kinds of screen, and the two odd ones out were the two you spend the longest looking at. It is
 * also what the queue's own default style promises and never delivered - "glass" is *frosted
 * panels over the blur backdrop*, and frosted glass over black is just dark grey.
 *
 * [triad] is the surface's own accent, not the face-wide one, so the ground is tinted by the same
 * colour its content is - see `wear_queue_color_mode` / `wear_lyrics_color_mode`.
 */
data class ScreenBackdrop(
        val overlay: OverlayBackdrop,
        val triad: PanelTriad,
        val backdrop: PanelBackdrop,
        val albumArt: Bitmap?
)

/**
 * The player backdrop plus the *Shared panel appearance* background, as one full-screen layer.
 *
 * Split out of [PanelScaffold] rather than reusing it, because that composable brings its own
 * `SwipeToDismissBox` and both callers here already own one - nesting a second would put two
 * dismiss gestures on one screen.
 */
@Composable
fun PanelBackdropLayer(screen: ScreenBackdrop, modifier: Modifier = Modifier) {
    AndroidView(
            modifier = modifier.fillMaxSize(),
            factory = { PanelBackdropView(it) },
            update = { it.render(screen.overlay, screen.triad, screen.backdrop, screen.albumArt) })
}

/**
 * Resolves that ground, seeded from [AlbumPaletteCache] for the reason [rememberPanelPalette]
 * documents: a screen opened over a cover the player has already extracted paints the album's
 * colours on its *first* frame instead of flashing the fallback and snapping over.
 *
 * [contentStyle] is what the *Shared panel appearance* "Follow style" option follows - the queue
 * passes its own row style, so the vocabulary stays the one [OverlayBackdropResolver] already
 * knows. The lyrics screen has no style of its own and passes null, which resolves to solid black:
 * the ground it was designed on, kept unless the user names a different one.
 */
@Composable
fun rememberScreenBackdrop(
        prefs: SharedPreferences,
        appearanceContext: AppearanceContext,
        albumArt: Bitmap?,
        accentSource: AlbumAccentSource,
        themeAccent: Int,
        triad: PanelTriad,
        contentStyle: String?,
        /** The screen's own background preference; "shared" defers to the page-wide choice. */
        backdropStyle: PreferenceDefinition<String>
): ScreenBackdrop {
    val fallbackTriad = remember(themeAccent) {
        PanelTriad(
                themeAccent,
                PanelAppearanceResolver.albumToneFallback(themeAccent, .42f),
                PanelAppearanceResolver.albumToneFallback(themeAccent, .68f))
    }
    val seed = AlbumPaletteCache.get(albumArt, accentSource)
    var rawTriad by remember(albumArt, accentSource) {
        mutableStateOf(seed ?: fallbackTriad)
    }
    LaunchedEffect(albumArt, accentSource) {
        if (seed != null) return@LaunchedEffect
        PanelAppearanceResolver.albumTriad(albumArt, accentSource, themeAccent) { raw ->
            rawTriad = raw
        }
    }
    val backdrop = remember(rawTriad) {
        PanelAppearanceResolver.resolveBackdrop(prefs, appearanceContext, rawTriad, themeAccent)
    }
    val overlay = remember(contentStyle, backdropStyle) {
        OverlayBackdropResolver.resolveSurface(
                FaceScopedPreferences.getString(prefs, backdropStyle, appearanceContext),
                FaceScopedPreferences.getString(
                        prefs, MiscPreferences.WEAR_OVERLAY_BACKDROP_STYLE, appearanceContext),
                contentStyle)
    }
    return ScreenBackdrop(overlay, triad, backdrop, albumArt)
}
