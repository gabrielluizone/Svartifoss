package com.svartifoss.snfell.watch.view.face

import com.svartifoss.snfell.watch.theme.WatchTheme

/**
 * Contract between [MainActivity][com.svartifoss.snfell.watch.view.MainActivity] and a
 * now-playing *face* - the swappable central rendering of the now-playing screen (artwork
 * texts, transport controls, progress). Faces only render and raise high-level events; they
 * own no input plumbing. Everything else on the screen is shared by all faces and stays in
 * MainActivity: the FourWayTouchLayout quadrant/swipe gestures, stem buttons, rotary input,
 * mini buttons, quick actions panel, volume/seek overlays, notification popup and the idle
 * ("nothing playing") state.
 *
 * Two faces exist, selected by [MiscPreferences.WEAR_SCREEN_FACE][com.svartifoss.snfell.common.MiscPreferences.WEAR_SCREEN_FACE]
 * on the phone:
 *  - "classic" - the original View-based presentation (bezel seek ring, quadrant hint icons,
 *    centered text block). It predates this contract and keeps rendering through MainActivity's
 *    views directly; MainActivity *is* its implementation.
 *  - "expressive" - [ExpressiveFace], a Compose face styled after the Material 3 Expressive
 *    system media controls.
 *
 * Ambient mode always falls back to the classic presentation regardless of the selected face -
 * it is burn-in-audited (outlined text, pixel jiggle) and animation-free by design.
 */
data class NowPlayingFaceState(
        val title: String = "",
        val artist: String = "",
        val playing: Boolean = false,
        /** True when there is no track at all - the face renders nothing and the shared idle
         *  ("nothing playing") group shows through instead. A *paused* track is not idle. */
        val idle: Boolean = true,
        /** Playback progress fraction (0f..1f) of [positionMs] / [durationMs]. */
        val progress: Float = 0f,
        val seekable: Boolean = false,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        /** Whether the "1:23 / 3:45" line should show - already combines the phone-synced
         *  track-time mode with whether a usable position exists. */
        val showTrackTime: Boolean = false,
        /** Raw album accent (or static theme accent) - the same color the shared UI tracks. */
        val accentColor: Int = WatchTheme.ACCENT_DEFAULT,
        /** Resolved artist text color (already accounts for color mode, desaturation and the
         *  plain-white status-message override - it mirrors the classic artist line exactly). */
        val artistColor: Int = WatchTheme.ACCENT_DEFAULT,
        /** Resolved progress accent (already accounts for the progress color mode). */
        val progressColor: Int = WatchTheme.ACCENT_DEFAULT,
        /** True while the user has no mini buttons configured - faces with a default bottom
         *  button trio (queue/volume/overflow) may show it; configured mini buttons own that
         *  part of the screen otherwise. */
        val showDefaultBottomPills: Boolean = false,
)

/** Events a face raises back to the host. Implemented by MainActivity, which routes them into
 *  the same pipelines the classic face uses (haptics, optimistic state, Data Layer sends). */
interface NowPlayingFaceListener {
    /** Single tap on the central play/pause control. */
    fun onPlayPauseTap()

    /** Double tap on the central control - toggles the quick actions panel, matching the
     *  classic face's center tap zone. */
    fun onCenterDoubleTap()

    /** Long press on the central control - opens the queue when the corresponding preference
     *  is enabled, matching the classic face's center tap zone. */
    fun onCenterLongPress()

    fun onSkipPreviousTap()

    fun onSkipNextTap()

    /** Default bottom-trio pill: open the playback queue. */
    fun onQueueTap()

    /** Default bottom-trio pill: show the volume overlay. */
    fun onVolumeTap()

    /** Default bottom-trio pill: open the actions menu. */
    fun onOverflowTap()

    /** Commit a seek to [fraction] (0f..1f) of the track. Reserved for faces that implement
     *  their own scrub interaction; rotary seek stays host-side and face-agnostic. */
    fun onSeek(fraction: Float)
}
