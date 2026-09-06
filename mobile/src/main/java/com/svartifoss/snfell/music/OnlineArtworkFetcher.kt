package com.svartifoss.snfell.music

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.preference.PreferenceManager
import com.svartifoss.snfell.common.AlbumArtSource
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.common.MiscPreferences
import org.json.JSONObject
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Looks up the picture behind the player when the user has asked for one that is not on the device
 * - a picture of the performing **artist**, or the **album cover** for a player that publishes
 * none - from Deezer's public catalogue search, and caches it through [OnlineArtworkCache].
 *
 * ## Why the phone does this and not the watch
 *
 * A Bluetooth-only watch has no internet route of its own, so a watch-side fetch would work on the
 * LTE models and quietly fail on every other one. It is the same rule the lyrics and track-metadata
 * lookups follow, and it keeps the app's whole network surface on the device
 * `docs/privacy-policy.md` describes.
 *
 * ## Why Deezer, and why one query serves both
 *
 * `search?q=` needs no account, no API key and no per-service track id - it matches on the *names*
 * the playing app publishes, which is the only identifier every player has - and one response
 * carries `album.cover_xl` and `artist.picture_xl` side by side. So the two sources cost the same
 * single request, and asking for one warms the other. For the artist there is no offline
 * alternative at all: Android's MediaStore has never carried artist images, `MediaMetadata` has no
 * field for one, and a player that shows them keeps them in private storage no other app can read.
 *
 * ## What is sent, and when
 *
 * Only while a face is set to a source that needs it, and only once per track/artist. The caller
 * ([MusicService]) enforces the first half; [OnlineArtworkCache] enforces the second, including for
 * a **miss**, which is cached exactly as a hit is - otherwise a record Deezer has never heard of is
 * looked up again on every track of the album, which is the case where the request is worth least.
 */
object OnlineArtworkFetcher {

    private const val CONNECT_TIMEOUT_MS = 12_000
    private const val READ_TIMEOUT_MS = 12_000

    private const val SEARCH_ENDPOINT = "https://api.deezer.com/search"

    /**
     * Bound on the stored picture's longest side.
     *
     * This one is drawn full-bleed behind a whole watch face rather than in a list row, so it is
     * larger than [ShortcutArtworkFetcher.MAX_THUMBNAIL_PX]: current Wear OS displays run to about
     * 450px and an upscaled backdrop is visible in a way an upscaled thumbnail is not.
     */
    const val MAX_ARTWORK_PX = 512

    /**
     * JPEG, not PNG.
     *
     * The same reasoning `OpenPlaylistAction.encodeThumbnail` records for queue covers, and it
     * applies harder here: this is a photograph with no alpha at the largest size anything in this
     * app transmits, and lossless PNG would cost several times the bytes for no visible difference
     * over Bluetooth. The watch decodes with `BitmapUtils.deserialize`, which is format-agnostic.
     */
    private const val JPEG_QUALITY = 88

    fun isEnabled(context: Context): Boolean =
            PreferenceManager.getDefaultSharedPreferences(context)
                    .getBoolean(
                            MiscPreferences.ONLINE_ARTWORK_ENABLED.key,
                            MiscPreferences.ONLINE_ARTWORK_ENABLED.defaultValue)

    /**
     * The picture for [source], fetching it if this is the first time it has been asked for.
     *
     * Null means "there is nothing to draw" - the caller falls back to whatever the player
     * published - and covers a disabled lookup, a blank name, a network failure and a record Deezer
     * does not list, deliberately without distinguishing them: the screen does the same thing in
     * every one of those cases, so telling them apart would be detail nobody can act on.
     *
     * Blocking. Call it off the main thread; never throws.
     */
    fun artworkFor(
            context: Context,
            source: AlbumArtSource,
            artist: String,
            title: String
    ): ByteArray? {
        val key = cacheKeyFor(source, artist, title) ?: return null
        if (!isEnabled(context)) return null

        OnlineArtworkCache.get(context, key)?.let { return it.bytes }

        val fetched = fetch(source, artist, title)
        OnlineArtworkCache.put(context, key, fetched)
        return fetched
    }

    /** The already-cached picture for [source], so a caller can skip the thread hop. */
    fun cached(
            context: Context,
            source: AlbumArtSource,
            artist: String,
            title: String
    ): ByteArray? {
        val key = cacheKeyFor(source, artist, title) ?: return null
        if (!isEnabled(context)) return null
        return OnlineArtworkCache.get(context, key)?.bytes
    }

    /**
     * What identifies this lookup on disk, or null when there is nothing to look up.
     *
     * An artist picture is keyed by the performer alone, so a whole album shares one entry; a cover
     * needs the track too. Both carry the source in the key, because the same artist yields two
     * different pictures depending on which was asked for.
     */
    fun cacheKeyFor(source: AlbumArtSource, artist: String, title: String): String? {
        val name = artist.trim()
        if (!source.needsLookup || name.isEmpty()) return null
        return when (source) {
            AlbumArtSource.ARTIST -> "artist|$name"
            AlbumArtSource.ONLINE -> {
                val track = title.trim()
                if (track.isEmpty()) null else "cover|$name|$track"
            }
            // The device-local sources resolve a picture the user picked, which needs no lookup
            // and no cache entry here - CustomArtworkSource owns both, and returning a key would
            // route them through a network fetch they must never reach.
            AlbumArtSource.LOCAL,
            AlbumArtSource.CUSTOM_IMAGE,
            AlbumArtSource.CUSTOM_FOLDER -> null
        }
    }

    private fun fetch(source: AlbumArtSource, artist: String, title: String): ByteArray? {
        // The credited name first, because it is what the user is looking at. Only if that finds
        // nothing is the primary name tried: "Artist feat. Guest" is a real credit and a poor
        // search term, but collapsing to the primary name unconditionally would hand back the
        // wrong performer's picture for a collaboration Deezer does list under its full name.
        val names = listOfNotNull(
                artist.trim(),
                primaryArtistName(artist).takeIf { it != artist.trim() && it.isNotEmpty() })
        for (name in names) {
            val query = when (source) {
                // The cover needs the track: an artist name alone matches their most popular
                // record, which is the wrong sleeve for every other one.
                AlbumArtSource.ONLINE -> "$name ${title.trim()}".trim()
                else -> name
            }
            val url = resolvePictureUrl(source, query) ?: continue
            val raw = downloadBytes(url) ?: continue
            val encoded = reencode(raw) ?: continue
            Timber.d("Online artwork resolved (%s) via query %s", source.preferenceValue, query)
            return encoded
        }
        return null
    }

    /**
     * The performer a credit line leads with, with any featured guests dropped.
     *
     * Pure and free of `android.*` so it is pinned by a JVM test: what it does is guess, the guess
     * is only ever a *second* query, and the separators it recognises are the whole of the policy.
     */
    fun primaryArtistName(credit: String): String {
        var result = credit.trim()
        for (separator in FEATURE_SEPARATORS) {
            val index = result.indexOf(separator, ignoreCase = true)
            if (index > 0) result = result.substring(0, index)
        }
        // A plain comma or ampersand separates co-headliners rather than guests, so it is only
        // trimmed after the feature markers above have had their turn.
        for (separator in LIST_SEPARATORS) {
            val index = result.indexOf(separator)
            if (index > 0) result = result.substring(0, index)
        }
        return result.trim().trim('-', '–', '—').trim()
    }

    private val FEATURE_SEPARATORS = listOf(
            " feat. ", " feat ", " featuring ", " ft. ", " ft ", " with ", " x ", " vs. ", " vs ")

    private val LIST_SEPARATORS = listOf(", ", " & ", " / ")

    /**
     * Reads the search response and returns the largest picture it offers, or null.
     *
     * One endpoint for both sources: `search?q=` carries the album cover and the artist picture in
     * the same result, so which one is read is the only difference between them.
     */
    private fun resolvePictureUrl(source: AlbumArtSource, query: String): String? {
        if (query.isBlank()) return null
        val url = "$SEARCH_ENDPOINT?limit=1&q=" + URLEncoder.encode(query, "UTF-8")
        val json = downloadText(url) ?: return null
        return try {
            val data = JSONObject(json).optJSONArray("data") ?: return null
            if (data.length() == 0) return null
            val entry = data.optJSONObject(0) ?: return null
            val (holder, fields) = when (source) {
                AlbumArtSource.ONLINE -> entry.optJSONObject("album") to COVER_FIELDS
                // Deezer serves a generic silhouette for a listed artist with no photograph, which
                // is indistinguishable from a real picture here and is accepted as one: refusing it
                // would mean guessing at image content, and the alternative on screen is the cover
                // this source was chosen to replace.
                else -> entry.optJSONObject("artist") to PICTURE_FIELDS
            }
            if (holder == null) return null
            // Largest first.
            fields.asSequence()
                    .map { holder.optString(it) }
                    .firstOrNull { it.isNotBlank() }
        } catch (e: Exception) {
            Timber.w(e, "Online artwork response could not be read")
            null
        }
    }

    private val COVER_FIELDS = listOf("cover_xl", "cover_big", "cover_medium", "cover")

    private val PICTURE_FIELDS = listOf("picture_xl", "picture_big", "picture_medium", "picture")

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
                // The same hardening ApkDownloader documents: an explicit User-Agent, and
                // Connection: close against Android's truncate-on-redirect connection pooling bug.
                connection.setRequestProperty("User-Agent", "Svartifoss-online-artwork")
                connection.setRequestProperty("Connection", "close")
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
                block(connection)
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Timber.w(e, "Online artwork request failed")
            null
        }
    }

    /**
     * Bounds the size and re-encodes as JPEG, **keeping the aspect ratio**.
     *
     * Deliberately unlike [ShortcutArtworkFetcher], which centre-crops to a square: that one fills
     * a round slot, while this fills the whole screen behind a face that already crops it to the
     * display. Cropping twice would throw away framing the face could have used.
     */
    private fun reencode(raw: ByteArray): ByteArray? =
            BitmapFactory.decodeByteArray(raw, 0, raw.size)?.let(::encodeForBackdrop)

    /**
     * Sizes and encodes one bitmap for [CommPaths.ASSET_BACKDROP_ART].
     *
     * Shared with [CustomArtworkSource], which resolves the same asset from a picture on the phone
     * rather than from the network. Everything that reaches that asset has to agree about the
     * bound and the format - it is one asset the watch draws behind a whole face, and two encoders
     * would mean a chosen photograph and a looked-up cover arriving at different sizes on the same
     * screen, which is visible the moment a face is switched between the two sources.
     */
    internal fun encodeForBackdrop(decoded: Bitmap): ByteArray? {
        val longest = maxOf(decoded.width, decoded.height)
        if (longest <= 0) return null
        val scaled = if (longest > MAX_ARTWORK_PX) {
            val ratio = MAX_ARTWORK_PX.toFloat() / longest
            Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * ratio).toInt().coerceAtLeast(1),
                    (decoded.height * ratio).toInt().coerceAtLeast(1),
                    true)
        } else {
            decoded
        }
        return try {
            ByteArrayOutputStream().use { stream ->
                scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
                stream.toByteArray()
            }
        } catch (e: Exception) {
            Timber.w(e, "Backdrop artwork could not be encoded")
            null
        }
    }
}
