package com.svartifoss.snfell.view.watchface

import android.content.SharedPreferences
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.svartifoss.snfell.R
import com.svartifoss.snfell.music.isPlaying
import com.svartifoss.snfell.view.TitledActivity
import com.svartifoss.snfell.view.mainactivity.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PLAYBACK_TICK_INTERVAL_MS = 500L

/**
 * "Watch" tab (the slot the Guide used to occupy - the guide now opens from the toolbar's
 * help button): visual customization of the watch's now-playing screen. A live miniature
 * ([WatchPreviewView]) is docked on top and re-renders on every preference change, while the
 * settings themselves live in the nested [WatchFacePrefsFragment] below it - which also pushes
 * each change to the watch, so the real screen follows the preview.
 *
 * When music is playing on the phone, the miniature shows the *actual* current track - album
 * art, title and artist from the active media session (the same one the mini player uses),
 * plus live playback state: a 500ms ticker (the watch's own position cadence) advances the
 * progress ring and track time, and pausing morphs the preview just like the real face. Falls
 * back to a built-in sample when nothing is playing.
 */
class WatchFaceFragment : Fragment() {

    private var preview: WatchPreviewView? = null
    private var mediaController: MediaController? = null

    private val tickHandler = Handler(Looper.getMainLooper())
    private val playbackTick = object : Runnable {
        override fun run() {
            pushPlayback()
            if (mediaController?.playbackState?.isPlaying() == true) {
                tickHandler.postDelayed(this, PLAYBACK_TICK_INTERVAL_MS)
            }
        }
    }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        preview?.refresh()
    }

    /** Loads the user's configured mini buttons (their real icons) off the main thread and
     *  hands them to the preview. Re-run on start so a change made in the Playing-controls tab
     *  is reflected when the user comes back here. */
    private fun loadMiniButtons() {
        val context = context?.applicationContext ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val icons = withContext(Dispatchers.IO) {
                MiniButtonIconLoader.loadConfiguredIcons(context)
            }
            preview?.setMiniButtons(icons)
        }
    }

    private val mediaCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            pushNowPlaying(metadata)
            pushPlayback()
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            restartPlaybackTicker()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_watch_face, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preview = view.findViewById(R.id.watch_preview)

        if (childFragmentManager.findFragmentById(R.id.watch_face_prefs) == null) {
            childFragmentManager.beginTransaction()
                    .replace(R.id.watch_face_prefs, WatchFacePrefsFragment())
                    .commit()
        }

        (activity as? MainActivity)?.activeMediaSession()?.observe(viewLifecycleOwner) { resource ->
            bindMediaController(resource?.data)
        }
    }

    private fun bindMediaController(controller: MediaController?) {
        mediaController?.unregisterCallback(mediaCallback)
        mediaController = controller
        controller?.registerCallback(mediaCallback)
        pushNowPlaying(controller?.metadata)
        restartPlaybackTicker()
    }

    private fun pushNowPlaying(metadata: MediaMetadata?) {
        val art = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        preview?.setNowPlaying(
                art,
                metadata?.getString(MediaMetadata.METADATA_KEY_TITLE),
                metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
        )
    }

    /** Pushes the current (interpolated) playback position/state into the preview - the state's
     *  position snapshot is anchored at lastPositionUpdateTime, so extrapolate while playing. */
    private fun pushPlayback() {
        val controller = mediaController
        val state = controller?.playbackState
        if (controller == null || state == null) {
            preview?.setPlayback(null, -1, -1)
            return
        }

        val playing = state.isPlaying()
        val duration = controller.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: -1L
        val position = if (playing) {
            state.position +
                    ((SystemClock.elapsedRealtime() - state.lastPositionUpdateTime) * state.playbackSpeed).toLong()
        } else {
            state.position
        }
        preview?.setPlayback(playing, position, duration)
    }

    private fun restartPlaybackTicker() {
        tickHandler.removeCallbacks(playbackTick)
        playbackTick.run()
    }

    override fun onStart() {
        super.onStart()
        if (parentFragmentManager.findFragmentById(R.id.fragment_container) === this) {
            (activity as? TitledActivity)?.updateActivityTitle(getString(R.string.watch_face_header))
        }
        PreferenceManager.getDefaultSharedPreferences(requireContext())
                .registerOnSharedPreferenceChangeListener(prefListener)
        preview?.refresh()
        loadMiniButtons()
        restartPlaybackTicker()
    }

    override fun onStop() {
        super.onStop()
        PreferenceManager.getDefaultSharedPreferences(requireContext())
                .unregisterOnSharedPreferenceChangeListener(prefListener)
        tickHandler.removeCallbacks(playbackTick)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        tickHandler.removeCallbacks(playbackTick)
        mediaController?.unregisterCallback(mediaCallback)
        mediaController = null
        preview = null
    }
}
