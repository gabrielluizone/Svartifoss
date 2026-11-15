package com.svartifoss.snfell.music

import android.content.Context
import com.svartifoss.snfell.BuildConfig
import com.svartifoss.snfell.common.SampledBitmaps
import org.json.JSONObject
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL

/**
 * Retrieves a YouTube video's own thumbnail for a track whose player published no artwork.
 *
 * The API key normally comes from the build and is optionally overridden by the user setting.
 * Requests are made by the phone, never by the watch, and the Android package/certificate headers
 * are supplied so an Android-restricted key works for both the Play-signed and sideloaded builds.
 * Results and definite misses share the existing bounded artwork cache; transport/API failures
 * are not cached.
 */
object YouTubeThumbnailFetcher {

    private const val SEARCH_ENDPOINT = "https://www.googleapis.com/youtube/v3/search"
    private const val CONNECT_TIMEOUT_MS = 12_000
    private const val READ_TIMEOUT_MS = 12_000
    private const val CANDIDATES = 5

    /** Whether this APK was built with the project-owned distribution key. */
    fun hasBundledKey(): Boolean = BuildConfig.YOUTUBE_DATA_API_KEY.trim().isNotEmpty()

    private fun configuredApiKey(context: Context): String = YouTubeDataApi.apiKey(context)

    fun isConfigured(context: Context): Boolean =
            configuredApiKey(context).isNotEmpty()

    /**
     * Returns the matched thumbnail, or null when the key is absent, no result matches, or the
     * request fails. Call off the main thread.
     */
    fun thumbnailFor(context: Context, channel: String, title: String): ByteArray? {
        val key = YouTubeThumbnailMatch.cacheKey(channel, title) ?: return null
        val apiKey = configuredApiKey(context)
        if (apiKey.isEmpty()) return null

        OnlineArtworkCache.get(context, key)?.let { return it.bytes }

        val query = YouTubeThumbnailMatch.searchQuery(channel, title) ?: return null
        val response = fetchSearch(context, query) ?: return null
        val items = try {
            JSONObject(response).optJSONArray("items")
        } catch (e: Exception) {
            Timber.w(e, "YouTube thumbnail response could not be read")
            null
        } ?: return null
        var exactMatchFound = false
        for (index in 0 until items.length()) {
            val snippet = items.optJSONObject(index)?.optJSONObject("snippet") ?: continue
            val resultChannel = snippet.optString("channelTitle")
            val resultTitle = snippet.optString("title")
            if (!YouTubeThumbnailMatch.accepts(channel, title, resultChannel, resultTitle)) {
                continue
            }
            exactMatchFound = true

            val thumbnailUrl = thumbnailUrl(snippet) ?: continue
            // Do not turn a transient image-server or decoding failure into a cached miss.
            val raw = downloadBytes(thumbnailUrl) ?: return null
            val encoded = decodeAndEncode(raw) ?: return null
            OnlineArtworkCache.put(context, key, encoded)
            Timber.d("YouTube thumbnail resolved for '%s'", title)
            return encoded
        }

        // A valid search with no exact match is a definite absence for this track. Caching it is
        // what prevents a live stream that republishes metadata from spending one quota call per
        // state rebuild.
        if (!exactMatchFound) {
            OnlineArtworkCache.put(context, key, null)
        }
        return null
    }

    private fun thumbnailUrl(snippet: JSONObject): String? {
        val thumbnails = snippet.optJSONObject("thumbnails") ?: return null
        return listOf("maxres", "standard", "high", "medium", "default")
                .asSequence()
                .mapNotNull { thumbnails.optJSONObject(it)?.optString("url") }
                .firstOrNull { it.isNotBlank() }
    }

    private fun fetchSearch(context: Context, query: String): String? {
        val response = YouTubeDataApi.get(
                context,
                SEARCH_ENDPOINT,
                mapOf("part" to "snippet", "type" to "video",
                        "maxResults" to CANDIDATES.toString(), "q" to query),
                userAgent = "Svartifoss-youtube-artwork") ?: return null
        return response.body
    }

    private fun downloadBytes(url: String): ByteArray? {
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "Svartifoss-youtube-artwork")
                connection.setRequestProperty("Connection", "close")
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
                connection.inputStream.use { it.readBytes() }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Timber.w(e, "YouTube thumbnail download failed")
            null
        }
    }

    private fun decodeAndEncode(raw: ByteArray): ByteArray? =
            SampledBitmaps.decode(raw, OnlineArtworkFetcher.MAX_ARTWORK_PX)
                    ?.let(OnlineArtworkFetcher::encodeForBackdrop)
}
