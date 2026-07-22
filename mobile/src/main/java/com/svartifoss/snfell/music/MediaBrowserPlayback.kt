package com.svartifoss.snfell.music

import android.content.Context
import android.net.Uri
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Starts playback of a streaming link by talking to the target app's MediaBrowserService
 * directly - the Android Auto / Assistant mechanism, and the only path that reliably works when
 * the phone screen is off or the app is not running: binding the browser service wakes the app's
 * playback machinery in the background, no Activity launch (blocked for background services on
 * modern Android) required. This is why the YouTube Music "Liked Music" shortcut plays even after
 * a force-stop, and the same path all playable shortcuts take.
 *
 * Two commands are used: [MediaControllerCompat.TransportControls.playFromUri] for a precise
 * content link, and [playFromSearch] with the shortcut's name as a fallback. `playFromSearch` is
 * the single command both YouTube Music and Spotify implement well (it is the "play X on
 * <app>" voice contract), and the only thing that plays an *artist* - whose page URL merely
 * navigates. Artists therefore go straight to search.
 */
object MediaBrowserPlayback {
    private const val CONNECT_TIMEOUT_MS = 4000L

    /** How long to wait for one command to reach a playing state before trying the next strategy.
     *  Kept alive so the freshly-woken process does not die before promoting itself to foreground. */
    private const val STRATEGY_TIMEOUT_MS = 5000L
    private const val PLAYBACK_POLL_MS = 200L

    /** A just-woken session often reports STATE_NONE with no transport actions for the first few
     *  hundred ms, silently dropping the first command. Re-issue while it warms up. */
    private const val COMMAND_RETRY_INTERVAL_MS = 700L

    private sealed interface Strategy {
        fun issue(controller: MediaControllerCompat)

        data class PlayUri(val uri: Uri) : Strategy {
            override fun issue(controller: MediaControllerCompat) {
                val actions = controller.playbackState?.actions ?: 0L
                if (actions and PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID != 0L &&
                        actions and PlaybackStateCompat.ACTION_PLAY_FROM_URI == 0L) {
                    controller.transportControls.playFromMediaId(uri.toString(), null)
                } else {
                    // A freshly-bound service often advertises nothing yet; playFromUri has an
                    // unambiguous meaning for a content link, so try it regardless.
                    controller.transportControls.playFromUri(uri, null)
                }
            }
        }

        data class PlaySearch(val query: String) : Strategy {
            override fun issue(controller: MediaControllerCompat) {
                controller.transportControls.playFromSearch(query, null)
            }
        }
    }

    /**
     * True when the target app accepted a command and reported an active playback state.
     * False means the caller should fall back to the visible deep-link flow (no browser service,
     * connection rejected - e.g. an app that allowlists browser clients like Spotify often does -
     * or playback never started).
     *
     * @param preferSearch when true (artists), the named search is tried first/only, because
     *   playing an artist by URI is not honored.
     */
    suspend fun play(
            context: Context,
            packageName: String,
            playbackLink: String,
            searchQuery: String?,
            preferSearch: Boolean
    ): Boolean {
        val query = searchQuery?.trim()?.takeIf { it.isNotEmpty() }
        val strategies = buildStrategies(Uri.parse(playbackLink), query, preferSearch)
        if (strategies.isEmpty()) return false

        val component = MediaBrowserSearch.findBrowserService(context, packageName) ?: return false
        val browser = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
            MediaBrowserSearch.connect(context, component)
        } ?: return false

        return try {
            val controller = MediaControllerCompat(context, browser.sessionToken)
            strategies.any { strategy -> awaitActivePlayback(controller, strategy) }
        } catch (e: RuntimeException) {
            // Dead session token, remote app crash mid-call, security rejection - all mean this
            // path is unavailable, not that the request itself was invalid.
            Timber.w(e, "Browser playback failed for %s", packageName)
            false
        } finally {
            browser.disconnect()
        }
    }

    private fun buildStrategies(
            uri: Uri,
            query: String?,
            preferSearch: Boolean
    ): List<Strategy> = if (preferSearch && query != null) {
        // Artists: the URI only navigates, so a named search is the only thing that plays.
        listOf(Strategy.PlaySearch(query))
    } else {
        // Precise entities: the URI is authoritative. A named search is deliberately NOT used as a
        // fallback here - it could match a different item; the visible deep-link open covers the
        // precise retry instead.
        listOf(Strategy.PlayUri(uri))
    }

    private suspend fun awaitActivePlayback(
            controller: MediaControllerCompat,
            strategy: Strategy
    ): Boolean {
        var waited = 0L
        var nextCommandAt = 0L
        while (waited < STRATEGY_TIMEOUT_MS) {
            when (controller.playbackState?.state) {
                PlaybackStateCompat.STATE_PLAYING,
                PlaybackStateCompat.STATE_BUFFERING,
                PlaybackStateCompat.STATE_CONNECTING -> return true
                else -> Unit
            }
            if (waited >= nextCommandAt) {
                strategy.issue(controller)
                nextCommandAt = waited + COMMAND_RETRY_INTERVAL_MS
            }
            delay(PLAYBACK_POLL_MS)
            waited += PLAYBACK_POLL_MS
        }
        return when (controller.playbackState?.state) {
            PlaybackStateCompat.STATE_PLAYING,
            PlaybackStateCompat.STATE_BUFFERING,
            PlaybackStateCompat.STATE_CONNECTING -> true
            else -> false
        }
    }
}
