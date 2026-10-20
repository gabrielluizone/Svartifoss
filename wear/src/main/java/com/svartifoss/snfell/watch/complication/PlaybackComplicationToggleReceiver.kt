package com.svartifoss.snfell.watch.complication

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.google.android.gms.wearable.Wearable
import com.matejdro.wearutils.messages.sendMessageToNearestClient
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.watch.communication.GlanceableSurfaces
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * The playback complication's tap: plays or pauses on the phone, without opening anything.
 *
 * A broadcast rather than an activity because a complication's tap is any `PendingIntent` - unlike
 * a Tile's, which can only start an activity (see `ShortcutLaunchActivity` for that detour) - and a
 * play/pause that flashed a screen up would defeat the point of having it on the watch face.
 *
 * The same message the Tile and the media session send, so the phone's handling is shared,
 * including carrying it out with no service running (see `DirectTransportControl`). The glyph is
 * drawn flipped at once ([displayedPlaying]) and the phone's real state replaces it when it lands:
 * a button that showed "pause" for the second the round trip takes reads as a tap that failed.
 */
class PlaybackComplicationToggleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TOGGLE) return
        val app = context.applicationContext
        val pending = goAsync()
        SCOPE.launch {
            try {
                val sent = Wearable.getMessageClient(app).sendMessageToNearestClient(
                        Wearable.getNodeClient(app), CommPaths.MESSAGE_TOGGLE_PLAY_PAUSE) != null
                if (sent) flippedAtRealtimeMs = SystemClock.elapsedRealtime()
            } catch (e: Exception) {
                // Out of range: nothing changes, and the glyph keeps saying so.
                Timber.w(e, "Playback complication: could not send play/pause")
            } finally {
                GlanceableSurfaces.requestPlaybackComplicationUpdate(app)
                pending.finish()
            }
            // If the phone never answers (a player that ignored it, a message that was lost), the
            // flipped glyph must not outlive its memory: draw the truth again once it lapses.
            kotlinx.coroutines.delay(FLIP_MEMORY_MS + FLIP_RECHECK_SLACK_MS)
            GlanceableSurfaces.requestPlaybackComplicationUpdate(app)
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.svartifoss.snfell.watch.complication.TOGGLE_PLAY_PAUSE"

        /** How long a toggle is drawn as done before the phone has said so. */
        private const val FLIP_MEMORY_MS = 4_000L
        private const val FLIP_RECHECK_SLACK_MS = 250L

        /** Process-scoped: the receiver is gone the moment [onReceive] returns. */
        private val SCOPE = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        @Volatile
        private var flippedAtRealtimeMs = 0L

        /** The play state to draw, given what the phone last [reported]. */
        fun displayedPlaying(reported: Boolean): Boolean {
            val elapsed = SystemClock.elapsedRealtime() - flippedAtRealtimeMs
            val recentToggle = flippedAtRealtimeMs != 0L && elapsed in 0 until FLIP_MEMORY_MS
            return if (recentToggle) !reported else reported
        }

        /**
         * Forgets a toggle once the phone's answer has arrived, so a later state that happens to
         * agree with the old one is not drawn inverted. Called when a new music state lands.
         */
        fun onStateArrived() {
            flippedAtRealtimeMs = 0L
        }

        fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, PlaybackComplicationToggleReceiver::class.java).setAction(ACTION_TOGGLE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
