package com.svartifoss.snfell.watch.view.panel

import android.content.SharedPreferences
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.svartifoss.snfell.common.AlbumAccentSource
import com.svartifoss.snfell.common.AppearanceContext
import java.lang.ref.WeakReference

/**
 * The album triad most recently extracted from a cover, shared across the whole process.
 *
 * `Palette.generate` is a callback, so a screen that starts from nothing has to draw once on the
 * fallback accent and again when the cover's colour lands. On the player that is invisible - it is
 * already on screen when the cover changes. On the dedicated Volume and Progress screens it was
 * the *first* thing you saw: they are separate Activities that re-extracted from scratch, so
 * opening one flashed the fallback sage green and then snapped to the album's colour.
 *
 * Nothing about that extraction was uncertain, though. The player had already done it, for the
 * same Bitmap instance (both read `PhoneConnection.albumArt`), with the same code - the two
 * implementations agree down to the fallback colour. The answer simply had nowhere to live. It
 * lives here now, so the second screen to ask gets it synchronously and paints the right colour on
 * its first frame.
 *
 * One entry, because the question is always about the cover playing right now. The Bitmap is held
 * weakly: a stale entry must never be the reason a cover cannot be collected on a watch.
 *
 * Deliberately not thread-safe. `Palette.generate` answers on the main thread and every reader is
 * a main-thread composition; synchronising would only hide a caller that wandered off.
 */
object AlbumPaletteCache {

    private var artRef: WeakReference<Bitmap>? = null
    private var cachedSource: AlbumAccentSource? = null
    private var cachedTriad: PanelTriad? = null

    /**
     * The triad already extracted for [art] under [source], or null.
     *
     * [source] is part of the key rather than an afterthought: it decides *which* swatch becomes
     * the primary, so the same cover legitimately has two different answers - the same reason
     * `MainActivity`'s own extraction guard carries `lastPaletteAccentSource`.
     */
    fun get(art: Bitmap?, source: AlbumAccentSource): PanelTriad? {
        if (art == null || art.isRecycled) return null
        if (artRef?.get() !== art || cachedSource != source) return null
        return cachedTriad
    }

    fun put(art: Bitmap, source: AlbumAccentSource, triad: PanelTriad) {
        if (art.isRecycled) return
        artRef = WeakReference(art)
        cachedSource = source
        cachedTriad = triad
    }
}

/** A panel screen's two resolved colour products: its own surface, and the player behind it. */
data class PanelPalette(
        val appearance: PanelAppearance,
        val backdrop: PanelBackdrop
)

/**
 * Everything a dedicated panel screen needs to paint itself in the album's colours.
 *
 * Both screens ran an identical twenty-five line copy of this - two states, a `LaunchedEffect`, a
 * surface triad and a backdrop - differing only in which [surface] they named. That is the shape
 * that drifts, and it had already produced one bug in both places at once.
 *
 * The seed is the point. State initialised from [AlbumPaletteCache] means a cover the player has
 * already extracted is on screen at the *first* composition, not one frame later: a `LaunchedEffect`
 * runs after the first frame is drawn, so even an instant answer would have flashed the fallback
 * once. The effect still runs for the case the cache cannot answer - a screen opened from a Tile
 * with the player never having been up - where drawing the fallback first is honest rather than a
 * defect.
 */
@Composable
fun rememberPanelPalette(
        prefs: SharedPreferences,
        appearanceContext: AppearanceContext,
        surface: PanelSurface,
        albumArt: Bitmap?,
        accentSource: AlbumAccentSource,
        themeAccent: Int
): PanelPalette {
    val fallbackTriad = remember(themeAccent) {
        PanelTriad(
                themeAccent,
                PanelAppearanceResolver.albumToneFallback(themeAccent, .42f),
                PanelAppearanceResolver.albumToneFallback(themeAccent, .68f))
    }
    val seed = AlbumPaletteCache.get(albumArt, accentSource)

    /** The album's own colours before any treatment - what the backdrop derives from. */
    var rawTriad by remember(albumArt, accentSource) {
        mutableStateOf(seed ?: fallbackTriad)
    }
    var triad by remember(albumArt, accentSource) {
        mutableStateOf(PanelAppearanceResolver.surfaceTriad(
                prefs, appearanceContext, surface, seed ?: fallbackTriad, themeAccent))
    }

    LaunchedEffect(albumArt, accentSource) {
        // Already seeded above; asking again would re-run Palette for an answer we are showing.
        if (seed != null) return@LaunchedEffect
        PanelAppearanceResolver.albumTriad(albumArt, accentSource, themeAccent) { raw ->
            rawTriad = raw
            triad = PanelAppearanceResolver.surfaceTriad(
                    prefs, appearanceContext, surface, raw, themeAccent)
        }
    }

    val appearance = remember(triad) {
        PanelAppearanceResolver.resolve(prefs, appearanceContext, surface, triad)
    }
    // The player composition this panel background is painted over - see PanelScaffold. Resolved
    // from the *raw* album triad, because the artwork treatment and shading use the watch-wide
    // colour treatment rather than this panel's own.
    val backdrop = remember(rawTriad) {
        PanelAppearanceResolver.resolveBackdrop(prefs, appearanceContext, rawTriad, themeAccent)
    }
    return PanelPalette(appearance, backdrop)
}
