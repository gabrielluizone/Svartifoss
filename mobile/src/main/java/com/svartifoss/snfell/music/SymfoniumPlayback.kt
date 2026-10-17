package com.svartifoss.snfell.music

import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * The small part of Symfonium's public remote API that is useful to the watch: append a song to
 * the current playback queue without starting a second browser session.  This is intentionally a
 * player-specific adapter. Android's MediaSession queue command is optional, and using an opaque
 * browser media id as if it were a filesystem path is not valid across players.
 */
object SymfoniumPlayback {
    const val PACKAGE_NAME = "app.symfonik.music.player"

    private const val API_RECEIVER = "app.symfonium.remote.api.ApiReceiver"
    const val ACTION_MEDIA_START = "app.symfonium.api.MEDIA_START"
    const val ACTION_MEDIA_SYNC = "app.symfonium.api.MEDIA_SYNC"
    const val MEDIA_TYPE_SONG = "song"
    const val QUEUE_NEXT = 1
    const val QUEUE_AT_END = 2

    data class MediaStartRequest(
            val mediaType: String,
            val name: String? = null,
            val artist: String? = null,
            val album: String? = null,
            val id: Long? = null,
            val filePath: String? = null,
            val resume: Boolean? = null,
            val shuffle: Boolean? = null,
            val queue: Int? = null
    )

    /**
     * Appends [title] to Symfonium's queue. [artist] is optional disambiguation metadata; the
     * broadcast is best-effort because Symfonium does not return an acknowledgement for this API.
     */
    fun appendToQueue(context: Context, title: String, artist: String?, filePath: String?): Boolean {
        val songTitle = title.trim()
        if (songTitle.isEmpty()) return false
        return startMedia(context, MediaStartRequest(
                mediaType = MEDIA_TYPE_SONG,
                name = songTitle,
                artist = artist,
                filePath = filePath,
                queue = QUEUE_AT_END))
    }

    /** Starts a Symfonium media item or collection using the documented broadcast API. */
    fun startMedia(context: Context, request: MediaStartRequest): Boolean {
        if (request.mediaType.isBlank() || !isInstalled(context)) return false

        val intent = Intent(ACTION_MEDIA_START)
                .setComponent(ComponentName(PACKAGE_NAME, API_RECEIVER))
                .putExtra("MEDIA_TYPE", request.mediaType)

        request.name?.trim()?.takeIf { it.isNotEmpty() }?.let { intent.putExtra("NAME", it) }
        request.artist?.trim()?.takeIf { it.isNotEmpty() }?.let { intent.putExtra("ARTIST", it) }
        request.album?.trim()?.takeIf { it.isNotEmpty() }?.let { intent.putExtra("ALBUM", it) }
        request.id?.let { intent.putExtra("ID", it) }
        request.filePath?.trim()?.takeIf { it.isNotEmpty() }?.let { intent.putExtra("FILE", it) }
        request.resume?.let { intent.putExtra("RESUME", it) }
        request.shuffle?.let { intent.putExtra("SHUFFLE", it) }
        request.queue?.let { intent.putExtra("QUEUE", it) }

        return send(intent, context)
    }

    /** Forces a provider scan, useful after adding local files or changing a server library. */
    fun syncProvider(context: Context, providerId: Int): Boolean {
        if (providerId <= 0 || !isInstalled(context)) return false
        val intent = Intent(ACTION_MEDIA_SYNC)
                .setComponent(ComponentName(PACKAGE_NAME, API_RECEIVER))
                .putExtra("PROVIDER_ID", providerId)
        return send(intent, context)
    }

    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getApplicationInfo(PACKAGE_NAME, 0)
        true
    } catch (_: Exception) {
        false
    }

    private fun send(intent: Intent, context: Context): Boolean = try {
        context.applicationContext.sendBroadcast(intent)
        true
    } catch (_: SecurityException) {
        false
    }
}
