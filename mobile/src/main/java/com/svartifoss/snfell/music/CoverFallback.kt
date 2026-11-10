package com.svartifoss.snfell.music

import android.content.Context
import android.graphics.Bitmap
import com.svartifoss.snfell.common.SampledBitmaps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * A cover for a track whose player published none, when one can be found that is really its own.
 *
 * YouTube is the case that made it: a video or a live stream there carries a title and a channel
 * and nothing else - no bitmap, no art address, no video id (its media-browser service answers with
 * an empty root, and the notification has no large icon) - so the video's own thumbnail is out of
 * reach from here. What is in reach is the album behind a music video, looked up in Deezer by
 * channel and title and accepted only when the result can be tied to what is playing
 * ([OnlineCoverMatch]). When the optional YouTube Data API key is configured, the real YouTube
 * thumbnail is tried first and this Deezer lookup remains the conservative fallback. For a live
 * stream, or somebody else's compilation, both can find nothing, and **nothing is the answer**:
 * the watch shows what it always showed.
 *
 * It does not draw a stand-in. One was tried - the playing app's colour with its mark on it - and
 * removed: it said nothing the face did not already say (the source icon sits beside the artist), a
 * full-screen fill is the opposite of what a black-background AMOLED face is for, and a screen lit
 * red all day costs battery that a dark one does not.
 *
 * The lookup runs off the main thread and the answer arrives later, so [coverFor] returns whatever
 * is known *now* - a cached cover, or null - and [onRebuild] has the state rebuilt when one
 * lands. The result is a plain [Bitmap] the caller treats as the player's cover, so the watch needs
 * no change at all: every face, the always-on display and the queue draw it like any other.
 *
 * Nothing is decided until the track has been without a cover for [CoverLookupGrace.GRACE_MS]:
 * many players publish a track a moment before its cover, and looking one up in that moment cost a
 * request on nearly every YouTube Music track change. [onRebuild] is also called when that wait
 * ends, so a track that still has no cover is decided then even if the player says nothing more.
 *
 * Not thread-safe: the state build that asks, and the callback that answers, are on the main thread.
 */
class CoverFallback(
        private val context: Context,
        private val scope: CoroutineScope,
        /** Called on the main thread, with the trigger to log, when the state should be rebuilt:
         *  an online cover arrived, or the wait before deciding is over. */
        private val onRebuild: (String) -> Unit
) {

    /** What the lookup under way (or the cover held) is for. Null when nothing is decided. */
    private var lookupKey: String? = null
    private var online: Bitmap? = null

    private val grace = CoverLookupGrace()

    /** The track whose wait has a rebuild scheduled for its end. */
    private var recheckKey: String? = null

    /** Re-try the current track after an online-artwork setting changes. */
    fun invalidate() {
        lookupKey = null
        online = null
    }

    /** The player published a cover of its own; a track waiting to be looked up no longer is. */
    fun playerHasCover() {
        grace.playerCover()
        recheckKey = null
    }

    fun coverFor(channel: String, title: String, nowMs: Long): Bitmap? {
        val key = OnlineArtworkFetcher.fallbackCacheKey(channel, title)
        if (key == lookupKey) return online
        val enabled = OnlineArtworkFetcher.isEnabled(context)
        if (key != null && enabled && !grace.withoutCover(key, nowMs)) {
            scheduleRecheck(key, nowMs)
            return null
        }
        lookupKey = key
        online = null
        recheckKey = null
        if (key != null && enabled) {
            val cached = OnlineArtworkFetcher.cachedFallbackCover(context, channel, title)
            when {
                // A previous Deezer miss must not prevent a newly configured YouTube key from
                // being tried for the same track. YouTubeThumbnailFetcher has its own cache,
                // so this still performs at most one YouTube search per identity.
                cached == null || YouTubeThumbnailFetcher.isConfigured(context) ->
                        startLookup(key, channel, title)
                cached.bytes != null -> online = decode(cached.bytes)
                // A recorded absence: asked already, and nothing there to draw.
            }
        }
        return online
    }

    private fun scheduleRecheck(key: String, nowMs: Long) {
        if (recheckKey == key) return
        recheckKey = key
        val waitMs = ((grace.dueAtMs() ?: nowMs) - nowMs).coerceAtLeast(0L)
        scope.launch {
            delay(waitMs)
            if (recheckKey != key) return@launch
            recheckKey = null
            onRebuild("cover lookup wait over")
        }
    }

    private fun startLookup(key: String, channel: String, title: String) {
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                OnlineArtworkFetcher.fallbackCoverFor(context, channel, title)
            }
            bytes ?: return@launch
            val bitmap = withContext(Dispatchers.Default) { decode(bytes) } ?: return@launch
            // The track can move on while a lookup is out. Its answer then belongs to nothing that
            // is playing, and drawing it would put one record's cover on another's.
            if (key != lookupKey) return@launch
            online = bitmap
            Timber.d("Fallback cover ready for %s", key)
            onRebuild("fallback cover found")
        }
    }

    private fun decode(bytes: ByteArray): Bitmap? =
            SampledBitmaps.decode(bytes, OnlineArtworkFetcher.MAX_ARTWORK_PX)
}
