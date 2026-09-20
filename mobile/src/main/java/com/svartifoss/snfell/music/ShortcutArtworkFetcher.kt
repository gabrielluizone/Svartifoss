package com.svartifoss.snfell.music

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.preference.PreferenceManager
import com.svartifoss.snfell.common.BitmapBorderTrim
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Fetches a thumbnail for a saved streaming shortcut from the service's public **oEmbed** endpoint
 * (the same open metadata Spotify/YouTube/SoundCloud expose for link previews) and caches it via
 * [ShortcutArtworkStore], so each thumbnail is downloaded at most once.
 *
 * This is the only feature that touches the network besides the update check, so it is strictly
 * **opt-in**: nothing here runs unless [isEnabled] (Settings toggle) is on. No account, API key or
 * personal data is involved - only the already-public share link is sent to the service's oEmbed
 * endpoint.
 *
 * The same oEmbed answer carries the item's title, which is what fills in a shortcut's name when
 * a link is added to the watch menu ([lookupInfo]); a YouTube Music link has no name of its own.
 */
object ShortcutArtworkFetcher {
    const val ENABLED_KEY = "streaming_shortcut_artwork"

    private const val CONNECT_TIMEOUT_MS = 12_000
    private const val READ_TIMEOUT_MS = 12_000
    // Large enough to fill a full-width quick-panel pill on current Wear OS screens without
    // visible upscaling blur - shrinkPreservingRatio only ever shrinks a cached thumbnail, never
    // enlarges it, so this is the hard ceiling on cover quality regardless of how it is displayed.
    // Referenced by ActionListTransmitter to clamp the size it sends for the cover-pill treatment.
    const val MAX_THUMBNAIL_PX = 480

    fun isEnabled(context: Context): Boolean =
            PreferenceManager.getDefaultSharedPreferences(context)
                    .getBoolean(ENABLED_KEY, false)

    /**
     * Ensures a thumbnail is cached for [link] when the feature is on and one isn't already stored
     * - unless [force] is set, which re-downloads and overwrites it regardless (used to pick up a
     * changed remote cover, or a higher [MAX_THUMBNAIL_PX] than what was cached previously).
     * Returns true when a thumbnail was (re)fetched (so callers can re-sync). Safe to call from a
     * background thread; never throws.
     */
    fun ensureCached(context: Context, link: String, force: Boolean = false): Boolean {
        if (!isEnabled(context)) return false
        if (!force && ShortcutArtworkStore.has(context, link)) return false
        val bytes = fetchThumbnailPng(context, link) ?: return false
        ShortcutArtworkStore.put(context, link, bytes)
        return true
    }

    /** Fetches thumbnails for any uncached shortcuts, or every shortcut when [force] is set.
     *  Returns true if at least one was added/refreshed. */
    fun ensureCachedAll(context: Context, shortcuts: List<PlaylistShortcut>, force: Boolean = false): Boolean {
        if (!isEnabled(context)) return false
        var changed = false
        for (shortcut in shortcuts) {
            if (ensureCached(context, shortcut.link, force)) changed = true
        }
        return changed
    }

    private fun fetchThumbnailPng(context: Context, link: String): ByteArray? {
        val thumbnailUrl = resolveThumbnailUrl(link) ?: return null
        val raw = downloadBytes(thumbnailUrl) ?: return null
        return reencodeThumbnail(raw)
    }

    /** Reads the service's oEmbed JSON and returns its `thumbnail_url`, or null when unsupported. */
    private fun resolveThumbnailUrl(link: String): String? = lookupInfo(link)?.thumbnailUrl

    /** Whether the link's service publishes an oEmbed record at all, i.e. whether [lookupInfo]
     *  can ever answer. Apple Music, Amazon Music and Tidal cannot, and neither can a bare link. */
    fun supportsLookup(link: String): Boolean = oembedEndpoint(link) != null

    /**
     * The name and cover URL the service publishes for [link], from one request to its oEmbed
     * endpoint - the same request, to the same host, that fetching a thumbnail already makes, so
     * reading the title adds nothing to what leaves the phone. Blocking; never throws.
     *
     * Deliberately **not** gated on [isEnabled]: the switch decides whether covers are fetched
     * unprompted, whereas this is also what the "Get name" button in the add-to-menu sheet calls,
     * and a person tapping that has asked for exactly this one request. The caller is the gate.
     */
    fun lookupInfo(link: String): OembedInfo? {
        val oembed = oembedEndpoint(link) ?: return null
        val json = downloadText(oembed) ?: return null
        return OembedParser.parse(json)
    }

    private fun oembedEndpoint(link: String): String? {
        val service = StreamingShortcutLinks.detect(link)
        val source = when (service) {
            StreamingService.SPOTIFY ->
                "https://open.spotify.com/oembed?url=" + encode(StreamingShortcutLinks.forBrowser(link))
            StreamingService.YOUTUBE_MUSIC ->
                "https://www.youtube.com/oembed?format=json&url=" + encode(youtubeWatchUrl(link))
            StreamingService.SOUNDCLOUD ->
                "https://soundcloud.com/oembed?format=json&url=" + encode(StreamingShortcutLinks.forBrowser(link))
            StreamingService.DEEZER ->
                "https://api.deezer.com/oembed?output=json&url=" + encode(StreamingShortcutLinks.forBrowser(link))
            // Apple Music, Amazon Music and Tidal have no public oEmbed; fall back to the app icon.
            else -> null
        }
        return source
    }

    /** YouTube's oEmbed only accepts youtube.com/watch URLs, so normalise the music.youtube link. */
    private fun youtubeWatchUrl(link: String): String {
        val playback = StreamingShortcutLinks.forPlayback(link) // music.youtube.com/watch?...
        return playback.replace("music.youtube.com", "www.youtube.com")
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun downloadText(url: String): String? = openConnection(url) { connection ->
        connection.inputStream.bufferedReader().use { it.readText() }
    }

    private fun downloadBytes(url: String): ByteArray? = openConnection(url) { connection ->
        connection.inputStream.use { it.readBytes() }
    }

    private fun <T> openConnection(url: String, block: (HttpURLConnection) -> T): T? {
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "Svartifoss-shortcut-artwork")
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
                block(connection)
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Timber.w(e, "Shortcut artwork request failed")
            null
        }
    }

    /**
     * Decodes, **center-crops to a square** and bounds the size, then re-encodes as PNG. Cropping
     * to a square here means a rectangular cover (e.g. a 16:9 YouTube thumbnail) fills a round or
     * square slot with no letterbox bars, and the circular clip applied when it is displayed has a
     * square source to work from.
     */
    private fun reencodeThumbnail(raw: ByteArray): ByteArray? {
        val decoded = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return null
        // Trim solid letterbox/pillarbox borders first. YouTube Music "art track" thumbnails wrap
        // the real square cover in bars (black, or a flat album colour); without this the square
        // crop keeps those bars and the cover ends up as a small square inside the circle.
        val trimmed = BitmapBorderTrim.trim(decoded)
        val side = minOf(trimmed.width, trimmed.height)
        if (side <= 0) return null
        val left = (trimmed.width - side) / 2
        val top = (trimmed.height - side) / 2
        val square = Bitmap.createBitmap(trimmed, left, top, side, side)
        val scaled = if (side > MAX_THUMBNAIL_PX) {
            Bitmap.createScaledBitmap(square, MAX_THUMBNAIL_PX, MAX_THUMBNAIL_PX, true)
        } else {
            square
        }
        return ByteArrayOutputStream().use { stream ->
            scaled.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        }
    }

}
