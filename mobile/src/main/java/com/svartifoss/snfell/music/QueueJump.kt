package com.svartifoss.snfell.music

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Jumping to a queue entry: the watch's queue (through [MusicService]) and the phone's queue window
 * both go through this, so a tap that works in one works in the other.
 *
 * Three ways a jump silently does nothing, each handled here (see *Playback queue* in CLAUDE.md):
 * - the command goes to the wrong session. A `queueId` indexes one session's queue, so it is sent to
 *   the session that published the queue as well as to the one playing, when they differ.
 * - the player does not implement `skipToQueueItem`. The command is always sent anyway, because the
 *   advertised actions are routinely wrong. [settle] then checks whether anything moved, and if not
 *   plays the row's own media id through the app's browser (Retro Music is the known case).
 * - the row is the one already playing. Nothing moving is then the right answer, so there is
 *   nothing to verify.
 */
object QueueJump {
    /**
     * How long a player has to answer the skip before [settle] treats it as ignored. Long enough for
     * a player that updates metadata a beat after the transport command (most of them), short
     * enough not to feel like a stall. Overshooting only costs a re-play of the track the user asked
     * for anyway.
     */
    const val VERIFY_MS = 1200L

    /** What has to change if a jump worked - see [MusicService] for why each field is there. */
    fun identity(controller: MediaController?): Triple<Long?, String?, String?> =
            Triple(
                    controller?.playbackState?.activeQueueItemId,
                    controller?.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
                    controller?.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE))

    /** A jump that was sent and still has to be checked, [VERIFY_MS] later, by [settle]. */
    class Pending internal constructor(
            val before: Triple<Long?, String?, String?>,
            val packageName: String,
            val mediaId: String)

    /**
     * Sends the jump to [queueId] to [playing] and, when it is another session, to [owner] (the
     * session whose queue the row came from). Returns what [settle] needs, or null when there is
     * nothing to check: no media id to fall back on, or the row is already playing.
     */
    fun issue(
            queueId: Long,
            mediaId: String?,
            playing: MediaController?,
            owner: MediaController?
    ): Pending? {
        val known = queueId != MediaSession.QueueItem.UNKNOWN_ID.toLong()

        // Both read *before* anything is issued. A player that reacts to the skip synchronously
        // would otherwise have already moved by the time these are sampled, making a jump that
        // worked perfectly look like a no-op and earning the track a pointless second start.
        val before = identity(playing)
        val alreadyOnThisRow = known && playing?.playbackState?.activeQueueItemId == queueId

        if (known) {
            Timber.d("Queue tap: skipToQueueItem(%d) on %s", queueId, playing?.packageName)
            playing?.transportControls?.skipToQueueItem(queueId)
            if (owner != null && owner.sessionToken != playing?.sessionToken) {
                Timber.d("Queue tap: also on queue owner %s", owner.packageName)
                owner.transportControls.skipToQueueItem(queueId)
            }
        }

        if (mediaId == null || alreadyOnThisRow) return null
        val packageName = playing?.packageName ?: owner?.packageName ?: return null
        return Pending(before, packageName, mediaId)
    }

    /**
     * [settle] after [VERIFY_MS], on a scope of its own rather than a screen's, for a caller that can
     * close in the same gesture: the phone's queue window may be dismissed right after the tap, and
     * the check has to run anyway. [now] is read when the time is up.
     */
    fun settleLater(context: Context, pending: Pending, now: () -> MediaController?) {
        val app = context.applicationContext
        detachedScope.launch {
            delay(VERIFY_MS)
            try {
                settle(app, pending, now())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Queue tap fallback failed")
            }
        }
    }

    private val detachedScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Run [VERIFY_MS] after [issue]: when the session playing [now] has not moved, plays the row's
     * media id through the app's browser instead.
     */
    suspend fun settle(context: Context, pending: Pending, now: MediaController?) {
        if (identity(now) != pending.before) return
        Timber.d("Queue tap: skip had no effect on %s; playing media id %s",
                pending.packageName, pending.mediaId)
        MediaBrowserPlayback.playMediaId(context, pending.packageName, pending.mediaId)
    }
}
