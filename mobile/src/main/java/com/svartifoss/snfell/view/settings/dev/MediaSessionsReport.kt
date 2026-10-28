package com.svartifoss.snfell.view.settings.dev

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import com.svartifoss.snfell.NotificationService
import com.svartifoss.snfell.R
import com.svartifoss.snfell.music.MediaBrowserSearch
import com.svartifoss.snfell.music.StreamingContentType
import com.svartifoss.snfell.music.StreamingService
import com.svartifoss.snfell.music.VisibleLaunchIntents
import com.svartifoss.snfell.music.VisiblePlaybackMemory
import com.svartifoss.snfell.music.VisiblePlaybackPlan

/** Named framework [PlaybackState] actions, for a human-readable dump of what a session claims to
 *  support - see [com.svartifoss.snfell.music.MediaSessionCapabilities] for why that claim is a
 *  hint, not a contract. */
private val KNOWN_ACTIONS: List<Pair<Long, String>> = listOf(
        PlaybackState.ACTION_PLAY to "PLAY",
        PlaybackState.ACTION_PAUSE to "PAUSE",
        PlaybackState.ACTION_PLAY_PAUSE to "PLAY_PAUSE",
        PlaybackState.ACTION_STOP to "STOP",
        PlaybackState.ACTION_SKIP_TO_NEXT to "SKIP_NEXT",
        PlaybackState.ACTION_SKIP_TO_PREVIOUS to "SKIP_PREVIOUS",
        PlaybackState.ACTION_SKIP_TO_QUEUE_ITEM to "SKIP_TO_QUEUE_ITEM",
        PlaybackState.ACTION_SEEK_TO to "SEEK_TO",
        PlaybackState.ACTION_FAST_FORWARD to "FAST_FORWARD",
        PlaybackState.ACTION_REWIND to "REWIND",
        PlaybackState.ACTION_SET_RATING to "SET_RATING",
        PlaybackState.ACTION_PLAY_FROM_MEDIA_ID to "PLAY_FROM_MEDIA_ID",
        PlaybackState.ACTION_PLAY_FROM_SEARCH to "PLAY_FROM_SEARCH",
        PlaybackState.ACTION_PLAY_FROM_URI to "PLAY_FROM_URI",
        PlaybackState.ACTION_PREPARE to "PREPARE",
        PlaybackState.ACTION_PREPARE_FROM_MEDIA_ID to "PREPARE_FROM_MEDIA_ID",
        PlaybackState.ACTION_PREPARE_FROM_SEARCH to "PREPARE_FROM_SEARCH",
        PlaybackState.ACTION_PREPARE_FROM_URI to "PREPARE_FROM_URI"
)

private fun decodeActions(actions: Long): String =
        KNOWN_ACTIONS.filter { (flag, _) -> actions and flag != 0L }
                .joinToString(", ") { it.second }
                .ifEmpty { "(none advertised)" }

private fun stateName(state: Int): String = when (state) {
    PlaybackState.STATE_NONE -> "NONE"
    PlaybackState.STATE_STOPPED -> "STOPPED"
    PlaybackState.STATE_PAUSED -> "PAUSED"
    PlaybackState.STATE_PLAYING -> "PLAYING"
    PlaybackState.STATE_FAST_FORWARDING -> "FAST_FORWARDING"
    PlaybackState.STATE_REWINDING -> "REWINDING"
    PlaybackState.STATE_BUFFERING -> "BUFFERING"
    PlaybackState.STATE_ERROR -> "ERROR"
    PlaybackState.STATE_CONNECTING -> "CONNECTING"
    PlaybackState.STATE_SKIPPING_TO_NEXT -> "SKIPPING_TO_NEXT"
    PlaybackState.STATE_SKIPPING_TO_PREVIOUS -> "SKIPPING_TO_PREVIOUS"
    PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM -> "SKIPPING_TO_QUEUE_ITEM"
    else -> "UNKNOWN($state)"
}

/**
 * Dumps every currently active [MediaController], not only the one this app tracks as "current" -
 * the gap `docs/player-integration-notes.md` exists to close by reading source. A player that
 * runs several sessions at once (Retro Music's browser session beside its playback one), a queue
 * that publishes null vs. empty, and an advertised-actions bitmask that is a hint rather than a
 * contract are all invisible from the running app; this is the same data
 * [com.svartifoss.snfell.music.ActiveMediaSessionProvider] already reads, without instantiating a
 * second long-lived listener for a one-shot dump.
 */
internal fun buildMediaSessionsReport(context: Context): String {
    val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    val notificationListenerComponent = ComponentName(context, NotificationService::class.java)

    val sessions = try {
        manager.getActiveSessions(notificationListenerComponent)
    } catch (e: SecurityException) {
        return context.getString(R.string.error_notification_access)
    }

    if (sessions.isEmpty()) {
        return buildString {
            appendLine("No active MediaSessions.")
            appendStreamingApps(context)
        }
    }

    return buildString {
        appendLine("${sessions.size} active session(s):")
        sessions.forEachIndexed { index, controller: MediaController ->
            appendLine()
            appendLine("[$index] ${controller.packageName}")
            val state = controller.playbackState
            if (state == null) {
                appendLine("  Playback state: (none published yet)")
            } else {
                appendLine("  Playback state: ${stateName(state.state)}")
                appendLine("  Advertised actions: ${decodeActions(state.actions)}")
            }
            val metadata = controller.metadata
            val title = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)
            val artist = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST)
            appendLine("  Metadata: ${if (title != null || artist != null) "\"$title\" - \"$artist\"" else "(none)"}")

            val queue = controller.queue
            appendLine("  Queue: " + when {
                queue == null -> "not published (null)"
                queue.isEmpty() -> "published, empty"
                else -> "${queue.size} item(s)"
            })

            val playbackInfo = controller.playbackInfo
            val volumeControl = when (playbackInfo.volumeControl) {
                android.media.VolumeProvider.VOLUME_CONTROL_FIXED -> "FIXED"
                android.media.VolumeProvider.VOLUME_CONTROL_RELATIVE -> "RELATIVE"
                android.media.VolumeProvider.VOLUME_CONTROL_ABSOLUTE -> "ABSOLUTE"
                else -> "UNKNOWN"
            }
            val playbackType = if (playbackInfo.playbackType ==
                    MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE) "REMOTE (cast)" else "LOCAL"
            appendLine("  Playback route: $playbackType, volume $volumeControl " +
                    "(${playbackInfo.currentVolume}/${playbackInfo.maxVolume})")

            val browserService = MediaBrowserSearch.findBrowserService(context, controller.packageName)
            appendLine("  MediaBrowserService: " +
                    if (browserService != null) browserService.className else "(none found)")
        }
        appendStreamingApps(context)
    }
}

/**
 * Links that exercise every hand-off technique, so the report shows exactly what the playback
 * ladder would find declared - by asking the same [VisiblePlaybackPlan] - rather than a separate
 * list of intents that could drift from it.
 */
private val PLAY_PROBES: List<Pair<String, StreamingContentType>> = listOf(
        "spotify:playlist:probe" to StreamingContentType.PLAYLIST,
        "spotify:collection:tracks" to StreamingContentType.PLAYLIST,
        "https://soundcloud.com/you/likes" to StreamingContentType.PLAYLIST,
        "https://soundcloud.com/probe/sets/probe" to StreamingContentType.PLAYLIST,
        "https://example.com/artist/probe" to StreamingContentType.ARTIST)

/**
 * What each installed streaming app *declares* to Android - its media browser services and the
 * public ways it can be asked to play - and what this phone has seen each way do. All of it comes
 * from PackageManager, the same public declarations `adb shell dumpsys package` prints; nothing is
 * read out of any app's code (see docs/streaming-app-interop.md).
 */
private fun StringBuilder.appendStreamingApps(context: Context) {
    val installed = StreamingService.entries.mapNotNull { service ->
        service.packageName?.takeIf { packageName ->
            try {
                context.packageManager.getApplicationInfo(packageName, 0).enabled
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }
    }
    appendLine()
    appendLine("Streaming apps (public declarations):")
    if (installed.isEmpty()) {
        appendLine("  none of the known streaming apps is installed")
        return
    }
    val memory = VisiblePlaybackMemory.Store(context)
    installed.forEach { packageName ->
        appendLine()
        appendLine("  $packageName")
        val browsers = MediaBrowserSearch.findBrowserServices(context, packageName)
        appendLine("    Browser services: " + if (browsers.isEmpty()) "(none)"
                else browsers.joinToString { it.className.substringAfterLast('.') })
        val declared = PLAY_PROBES.flatMap { (link, type) ->
            VisiblePlaybackPlan.candidates(link, packageName, type, "probe")
        }.filter { VisibleLaunchIntents.isDeclared(context, it) }
                .mapNotNull { it.technique?.storageKey }
                .distinct()
        appendLine("    Play techniques declared: " +
                if (declared.isEmpty()) "(none)" else declared.joinToString())
        memory.recordsFor(packageName).forEach { (kind, records) ->
            appendLine("    Seen on this phone for $kind: " + records.entries.joinToString { (technique, record) ->
                when {
                    record.provenGood -> "${technique.storageKey} played"
                    record.failures > 0 -> "${technique.storageKey} failed x${record.failures}"
                    else -> technique.storageKey
                }
            })
        }
    }
}
