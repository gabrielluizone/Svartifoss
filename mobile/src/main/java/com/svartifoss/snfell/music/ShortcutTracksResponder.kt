package com.svartifoss.snfell.music

import android.content.Context
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.protobuf.InvalidProtocolBufferException
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.proto.ShortcutTracks
import com.svartifoss.snfell.proto.ShortcutTracksRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/**
 * Answers the watch's shortcut screen when it asks for a saved playlist's songs
 * ([CommPaths.MESSAGE_REQUEST_SHORTCUT_TRACKS]).
 *
 * Handled before `WatchCommandDelivery`'s music inbox, the way `PinchCalibrationReceiver` is, so
 * asking never starts the music service: listing a playlist plays nothing, and a start Android
 * refuses on a locked phone would leave the screen waiting for an answer that never comes. The work
 * runs on a process-owned scope because the manifest listener that delivers the request returns at
 * once.
 *
 * What is asked online, and when, is the privacy-relevant part:
 * - Only YouTube playlists and albums are listed, through the YouTube Data API with the key the
 *   cover lookup already uses ([YouTubeDataApi]); everything else is answered NOT_LISTABLE at once,
 *   with no request.
 * - The lookup is made when the shortcut lookup switch ([ShortcutArtworkFetcher.isEnabled], off by
 *   default) is on, or when the user tapped "See songs" on the watch ([ShortcutTracksRequest.explicit])
 *   - the pattern "Get name from link" set: the explicit tap is the consent.
 * - An answer the phone already holds ([ShortcutTrackCache]) is shown without asking again, fresh
 *   for a day and never kept past the YouTube API policies' 30 days.
 */
internal object ShortcutTracksResponder {

    private const val ENDPOINT = "https://www.googleapis.com/youtube/v3/playlistItems"
    private const val USER_AGENT = "Svartifoss-shortcut-tracks"

    /** Under the Data Layer's 100 KB message limit, with room to spare. */
    private const val MAX_MESSAGE_BYTES = 90_000

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // The manifest listener and the running music service both receive the same message; one
    // request is answered once. Keyed by the event's own identity, as the command inbox is.
    private val answered = LinkedHashSet<String>()

    fun receive(context: Context, event: MessageEvent) {
        val request = try {
            ShortcutTracksRequest.parseFrom(event.data)
        } catch (e: InvalidProtocolBufferException) {
            Timber.w(e, "Unreadable song list request")
            return
        }
        val node = event.sourceNodeId ?: return
        if (!firstDelivery("$node/${event.requestId}")) return
        val app = context.applicationContext
        scope.launch {
            val answer = try {
                answer(app, request)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Could not list the songs of a shortcut")
                reply(request, ShortcutTracks.Status.FAILED)
            }
            send(app, node, answer)
        }
    }

    @Synchronized
    private fun firstDelivery(key: String): Boolean {
        if (!answered.add(key)) return false
        while (answered.size > 32) answered.remove(answered.first())
        return true
    }

    private fun answer(context: Context, request: ShortcutTracksRequest): ShortcutTracks {
        val playlistId = ShortcutTrackListing.playlistIdFor(request.link)
        if (playlistId == null || !YouTubeDataApi.isConfigured(context)) {
            return reply(request, ShortcutTracks.Status.NOT_LISTABLE)
        }
        val now = System.currentTimeMillis()
        val cached = ShortcutTrackCache.read(context, playlistId, now)
        if (cached != null && ShortcutTrackListing.cacheUse(now - cached.fetchedAtMs) ==
                ShortcutTrackListing.CacheUse.FRESH) {
            return fromCache(request, playlistId, cached)
        }

        val consented = request.explicit || ShortcutArtworkFetcher.isEnabled(context)
        if (!consented) {
            // What the phone already holds costs no request, so it is shown; anything else would
            // have to be looked up, and that is the user's to agree to.
            return if (cached != null && !cached.missing) fromCache(request, playlistId, cached)
            else reply(request, ShortcutTracks.Status.NEEDS_CONSENT)
        }

        return when (val result = fetch(context, playlistId)) {
            is Fetched.Listed -> {
                if (result.complete) {
                    ShortcutTrackCache.writeTracks(context, playlistId, now, result.total, result.tracks)
                }
                listed(request, playlistId, result.tracks, result.total)
            }
            Fetched.NotFound -> {
                ShortcutTrackCache.writeMissing(context, playlistId, now)
                reply(request, ShortcutTracks.Status.NOT_LISTABLE)
            }
            Fetched.Failed ->
                if (cached != null && !cached.missing) fromCache(request, playlistId, cached)
                else reply(request, ShortcutTracks.Status.FAILED)
        }
    }

    private sealed interface Fetched {
        /** [complete] when nothing went wrong on the way (a playlist cut at the cap is complete). */
        class Listed(val tracks: List<ShortcutTrackListing.Track>, val total: Int?,
                     val complete: Boolean) : Fetched
        object NotFound : Fetched
        object Failed : Fetched
    }

    /** Up to [ShortcutTrackListing.MAX_TRACKS] songs, one quota unit per page. */
    private fun fetch(context: Context, playlistId: String): Fetched {
        val tracks = mutableListOf<ShortcutTrackListing.Track>()
        var total: Int? = null
        var pageToken: String? = null
        val pages = ShortcutTrackListing.MAX_TRACKS / ShortcutTrackListing.PAGE_SIZE
        for (pageIndex in 0 until pages) {
            val params = mutableMapOf(
                    "part" to "snippet,contentDetails,status",
                    "maxResults" to ShortcutTrackListing.PAGE_SIZE.toString(),
                    "playlistId" to playlistId,
                    "fields" to ShortcutTrackListing.PAGE_FIELDS)
            pageToken?.let { params["pageToken"] = it }
            val response = YouTubeDataApi.get(context, ENDPOINT, params, USER_AGENT)
            // A later page that fails leaves what was read so far: shown, but not stored, so the
            // next opening asks again for the whole list.
            val partial = Fetched.Listed(tracks.toList(), total, complete = false)
            if (response == null) return if (tracks.isEmpty()) Fetched.Failed else partial
            if (response.code == 404) return if (pageIndex == 0) Fetched.NotFound else partial
            val page = response.body?.let(ShortcutTrackListing::parsePage)
                    ?: return if (tracks.isEmpty()) Fetched.Failed else partial
            if (total == null) total = page.totalResults
            tracks += page.tracks
            pageToken = page.nextPageToken ?: break
            if (tracks.size >= ShortcutTrackListing.MAX_TRACKS) break
        }
        Timber.d("Listed %d song(s) of a playlist", tracks.size)
        return Fetched.Listed(tracks.take(ShortcutTrackListing.MAX_TRACKS), total, complete = true)
    }

    private fun fromCache(request: ShortcutTracksRequest, playlistId: String,
                          cached: ShortcutTrackCache.Entry): ShortcutTracks =
            if (cached.missing) reply(request, ShortcutTracks.Status.NOT_LISTABLE)
            else listed(request, playlistId, cached.tracks, cached.total)

    private fun listed(request: ShortcutTracksRequest, playlistId: String,
                       tracks: List<ShortcutTrackListing.Track>, total: Int?): ShortcutTracks {
        var shown = tracks
        while (true) {
            val message = ShortcutTracks.newBuilder()
                    .setLink(request.link)
                    .setRequestId(request.requestId)
                    .setStatus(ShortcutTracks.Status.LISTED)
                    .setTotalCount(maxOf(total ?: 0, tracks.size))
                    .addAllTracks(shown.map { track ->
                        ShortcutTracks.Track.newBuilder()
                                .setEntryId(ShortcutTrackListing.trackLink(track.videoId, playlistId))
                                .setTitle(track.title)
                                .apply { track.artist?.let(::setArtist) }
                                .build()
                    })
                    .build()
            // Titles can run to a hundred characters each; never let a long list outgrow the
            // message it has to fit in - drop from the end, the part furthest from the top.
            if (message.serializedSize <= MAX_MESSAGE_BYTES || shown.isEmpty()) return message
            shown = shown.take(shown.size * 3 / 4)
        }
    }

    private fun reply(request: ShortcutTracksRequest, status: ShortcutTracks.Status): ShortcutTracks =
            ShortcutTracks.newBuilder()
                    .setLink(request.link)
                    .setRequestId(request.requestId)
                    .setStatus(status)
                    .build()

    private suspend fun send(context: Context, node: String, answer: ShortcutTracks) {
        try {
            Wearable.getMessageClient(context)
                    .sendMessage(node, CommPaths.MESSAGE_SHORTCUT_TRACKS, answer.toByteArray())
                    .await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Could not send a song list to the watch")
        }
    }
}
