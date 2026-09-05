package com.svartifoss.snfell.music

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaDescription
import android.media.MediaMetadata
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.svartifoss.snfell.common.BitmapBorderTrim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Finds the cover for one playback-queue entry.
 *
 * Music apps publish queue artwork in genuinely different places, and the original single check
 * (`iconBitmap`, else a local `iconUri`) missed two whole categories of player:
 *
 *  - **Local library players** put a MediaStore URI on the item -
 *    `content://media/external/audio/albumart/<albumId>` is what Retro Music sends. Opening it
 *    needs a media-read permission, and the app's declared `READ_EXTERNAL_STORAGE` stopped being
 *    grantable at `targetSdk` 33, so on Android 13+ every one of those covers silently failed to
 *    decode and the queue rendered with empty thumbnails. [MEDIA_PERMISSION] is the modern
 *    replacement; without it this resolver simply skips step 4 rather than failing.
 *  - **Streaming clients** put an `http(s)` artwork URL on the item (Media3's `artworkUri` maps
 *    straight onto the legacy description, which is what Echo Music and other YouTube Music
 *    clients send). Those were skipped outright, deliberately, because opening the queue must
 *    never block on the network - so they stay behind the opt-in [REMOTE_ARTWORK_KEY] toggle and
 *    a disk cache, exactly like [ShortcutArtworkFetcher].
 *
 * The chain is ordered cheapest-first, and every step is best-effort: a queue with no resolvable
 * art renders with collapsed thumbnails (which the watch already handles) rather than failing.
 */
object QueueArtworkResolver {

    /**
     * Toggle for downloading queue covers that are only available as remote URLs.
     *
     * **On by default**, unlike the other optional network path
     * ([ShortcutArtworkFetcher.ENABLED_KEY]). The difference is what "off" costs the user: a
     * shortcut without its thumbnail still shows a usable app icon, whereas a streaming client's
     * queue has *no* other artwork source at all - Media3 hands a queue item a bitmap only when
     * the app embedded raw `artworkData`, which YouTube-Music-style clients never do - so off
     * meant a queue of permanently blank rows that reads as a broken feature rather than a
     * setting. Turning it off remains a single switch, and nothing but the cover URL the playing
     * app already published is ever requested.
     */
    const val REMOTE_ARTWORK_KEY = "queue_remote_artwork"

    /** See [REMOTE_ARTWORK_KEY]. Kept as a named constant so the two preference-screen
     *  declarations and this reader cannot drift apart. */
    const val REMOTE_ARTWORK_DEFAULT = true

    /** The runtime permission that makes MediaStore album art readable. `READ_EXTERNAL_STORAGE`
     *  is ignored from Android 13 on, where audio access moved to this narrower grant. */
    val MEDIA_PERMISSION: String =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_AUDIO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }

    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 8_000

    /** Hard ceiling on a downloaded cover, before it is shrunk to the queue's thumbnail size. */
    private const val MAX_REMOTE_BYTES = 2 * 1024 * 1024

    /** Size asked of a size-in-URL image host when the caller does not specify one. Matches the
     *  larger of the two thumbnail sizes the queue sends, so the default never under-fetches. */
    const val DEFAULT_TARGET_PX = 320

    /** Extras keys apps use to carry artwork that is not on the description itself. */
    private val ART_EXTRA_KEYS = listOf(
            MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
            MediaMetadata.METADATA_KEY_ART_URI,
            MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)

    fun remoteArtworkEnabled(context: Context): Boolean =
            PreferenceManager.getDefaultSharedPreferences(context)
                    .getBoolean(REMOTE_ARTWORK_KEY, REMOTE_ARTWORK_DEFAULT)

    fun hasMediaPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, MEDIA_PERMISSION) ==
                    PackageManager.PERMISSION_GRANTED

    /**
     * The cover for [description], or null when this player publishes none we can reach.
     *
     * @param allowRemote whether the caller wants step 5 (network) attempted at all. Callers
     *   resolve this once per queue rather than per item so a 20-entry queue reads the preference
     *   once, and so a future caller on a metered path can suppress it without a preference write.
     */
    suspend fun resolve(
            context: Context,
            description: MediaDescription,
            allowRemote: Boolean,
            targetPx: Int = DEFAULT_TARGET_PX
    ): Bitmap? = resolveRaw(context, description, allowRemote, targetPx)?.let(BitmapBorderTrim::trim)

    /**
     * Same chain as [resolve], before the [BitmapBorderTrim] pass. Split out only so that pass
     * applies once, in one place, regardless of which step below found the cover - a streaming
     * client's "art track" thumbnail can arrive via the remote step here exactly as it does through
     * [ShortcutArtworkFetcher], and every other step is left alone since a bordered cover from
     * MediaStore or a local URI is possible in principle, not just the remote case.
     */
    private suspend fun resolveRaw(
            context: Context,
            description: MediaDescription,
            allowRemote: Boolean,
            targetPx: Int
    ): Bitmap? {
        description.iconBitmap?.let {
            Timber.v("Queue cover for '%s': iconBitmap", description.title)
            return it
        }

        val candidates = buildList {
            description.iconUri?.let { add(it) }
            val extras = description.extras
            if (extras != null) {
                for (key in ART_EXTRA_KEYS) {
                    val raw = try {
                        extras.getString(key)
                    } catch (_: RuntimeException) {
                        // Extras cross a process boundary as a Parcel; a malformed bundle from
                        // another app must not take the whole queue down.
                        null
                    }
                    raw?.takeIf { it.isNotBlank() }?.let { add(Uri.parse(it)) }
                }
            }
        }
        Timber.v("Queue cover for '%s': candidates=%s mediaId=%s",
                description.title, candidates, description.mediaId)

        for (uri in candidates) {
            if (isRemote(uri)) continue
            loadLocalUri(context, uri)?.let {
                Timber.v("Queue cover for '%s': local URI %s", description.title, uri)
                return it
            }
        }

        mediaStoreArtwork(context, description)?.let {
            Timber.v("Queue cover for '%s': MediaStore", description.title)
            return it
        }

        if (!allowRemote) {
            val hadRemoteCandidate = candidates.any(::isRemote)
            if (hadRemoteCandidate) {
                Timber.v(
                        "Queue cover for '%s': remote candidate %s available but fetch is disabled",
                        description.title, candidates.first(::isRemote))
            }
            return null
        }
        for (uri in candidates) {
            if (!isRemote(uri)) continue
            remoteArtwork(context, uri, targetPx)?.let {
                Timber.v("Queue cover for '%s': downloaded from %s", description.title, uri)
                return it
            }
            Timber.v("Queue cover for '%s': download failed for %s", description.title, uri)
        }
        Timber.v("Queue cover for '%s': no source resolved", description.title)
        return null
    }

    private fun isRemote(uri: Uri): Boolean =
            uri.scheme.equals("http", ignoreCase = true) ||
                    uri.scheme.equals("https", ignoreCase = true)

    /**
     * The *now-playing* cover fetched from a remote URL, for players that publish the player's own
     * artwork only as an address.
     *
     * Same download, disk cache, size rewrite and border trim a queue row gets - deliberately the
     * same code rather than a second copy, since it is the same problem: `MusicService` hands the
     * watch a bitmap that gets scaled up to the watch's display size, so a source smaller than that
     * is visibly soft, and SoundCloud's default `large` rendition is 100px square. See
     * [sizedArtworkUrl] for how the size is raised before the request goes out.
     *
     * Returns null for anything that is not an http(s) address, so a caller can pass whatever the
     * metadata held without pre-checking. Honouring the same preference as the queue is the
     * caller's job - [remoteArtworkEnabled].
     */
    suspend fun remoteArtworkForUri(context: Context, uri: Uri, targetPx: Int): Bitmap? {
        if (!isRemote(uri)) return null
        return remoteArtwork(context, uri, targetPx)?.let(BitmapBorderTrim::trim)
    }

    /**
     * [url] rewritten to ask its image host for something close to [targetPx], or [url] unchanged
     * when it is not a host whose URL encodes the size.
     *
     * YouTube-Music-style clients publish a *list-sized* thumbnail for queue entries - often
     * 60 or 120px, sized for the phone's own compact rows. Downloading that verbatim and then
     * letting the watch stretch it across a Cover-style pill is what makes those covers look
     * pixelated: [BitmapUtils.shrinkPreservingRatio] only ever shrinks, so a small source is never
     * rescued later in the chain. Google's image CDN takes the size straight from the URL, so
     * asking for the right one up front costs nothing extra.
     *
     * Pure and host-gated on purpose: an unrecognised URL is returned untouched rather than
     * guessed at, and only the size tokens are replaced - every other option the app attached
     * (crop, quality, format) is preserved in place.
     */
    fun sizedArtworkUrl(url: String, targetPx: Int): String {
        val queryStart = url.indexOf('?')
        val path = if (queryStart >= 0) url.substring(0, queryStart) else url
        val query = if (queryStart >= 0) url.substring(queryStart) else ""

        if (GOOGLE_IMAGE_HOSTS.any { path.contains(it, ignoreCase = true) }) {
            val eq = path.lastIndexOf('=')
            if (eq > 0) {
                val options = path.substring(eq + 1).split('-')
                var changed = false
                val rewritten = options.map { option ->
                    val digits = option.drop(1)
                    if (option.length > 1 && option[0] in SIZE_TOKENS &&
                            digits.all { it.isDigit() }) {
                        changed = true
                        "${option[0]}$targetPx"
                    } else {
                        option
                    }
                }
                if (changed) {
                    return path.substring(0, eq) + "=" + rewritten.joinToString("-") + query
                }
            }
        }

        // SoundCloud encodes the size as a trailing token on the filename. Its default is `large`,
        // which is only 100x100 - the reason its covers look soft everywhere outside its own app.
        // `original` is deliberately never requested: it is the one token that keeps the source
        // extension (.png), so swapping it in blind produces a 404 on a .jpg cover, and it is
        // unbounded in size, which is the wrong trade for a Bluetooth hop to a watch.
        if (path.contains("sndcdn.com", ignoreCase = true)) {
            val dot = path.lastIndexOf('.')
            val dash = if (dot > 0) path.lastIndexOf('-', dot) else -1
            if (dash > 0) {
                val current = path.substring(dash + 1, dot)
                val currentPx = SOUNDCLOUD_SIZES[current.lowercase(Locale.US)]
                // Rewrite only when what the URL already asks for is too small. Anything that
                // clears the target is left exactly as the app wrote it - both because a larger
                // download would buy nothing visible, and because that rule is what keeps this
                // from ever downgrading (the ytimg branch below states the same reasoning).
                if (currentPx != null && currentPx < targetPx) {
                    val wanted = SOUNDCLOUD_UPGRADE_TARGETS
                            .firstOrNull { (SOUNDCLOUD_SIZES[it] ?: 0) >= targetPx }
                            ?: SOUNDCLOUD_LARGEST
                    if ((SOUNDCLOUD_SIZES[wanted] ?: 0) > currentPx) {
                        return path.substring(0, dash + 1) + wanted + ".jpg" + query
                    }
                }
            }
        }

        // YouTube's static thumbnail host encodes the size in the filename instead. Only ever
        // upgraded, never downgraded: a caller asking for a small thumbnail can shrink a large
        // source, but nothing can recover detail from a 120px one.
        val ytMatch = YTIMG_NAMES.firstOrNull { path.endsWith("/$it.jpg", ignoreCase = true) }
        if (ytMatch != null && path.contains("ytimg.com", ignoreCase = true)) {
            val wanted = when {
                targetPx > 480 -> "maxresdefault"
                targetPx > 320 -> "hqdefault"
                targetPx > 120 -> "mqdefault"
                else -> ytMatch
            }
            if (wanted != ytMatch) {
                return path.removeSuffix("$ytMatch.jpg") + "$wanted.jpg" + query
            }
        }
        return url
    }

    /**
     * Every SoundCloud artwork size token and the square pixel size it serves, smallest first.
     *
     * Used to read what a URL *currently* asks for, so an already-large cover is never downgraded.
     * `original` is absent on purpose - see the rewrite site. Every token here resolves to a
     * `.jpg`, which is what makes substituting one for another safe regardless of the source
     * extension.
     */
    private val SOUNDCLOUD_SIZES = linkedMapOf(
            "mini" to 16,
            "tiny" to 20,
            "small" to 32,
            "badge" to 47,
            "t67x67" to 67,
            "large" to 100,
            "t300x300" to 300,
            "crop" to 400,
            "t500x500" to 500)

    /**
     * The subset worth *asking* for, smallest first.
     *
     * `crop` is deliberately not here even though it is larger than `t300x300`: it is a cropped
     * 400x400 rendition, not a plain resize, so choosing it to satisfy a size request would change
     * how the cover is framed. The `tNxN` renditions keep the artwork's own framing, which is the
     * only thing that makes one token a safe substitute for another.
     */
    private val SOUNDCLOUD_UPGRADE_TARGETS = listOf("large", "t300x300", "t500x500")

    private const val SOUNDCLOUD_LARGEST = "t500x500"

    /** Hosts whose URLs carry the requested size as `=w<N>-h<N>` options. */
    private val GOOGLE_IMAGE_HOSTS = listOf("googleusercontent.com", "ggpht.com")

    /** Size option prefixes: width, height and the square "s" form. */
    private val SIZE_TOKENS = charArrayOf('w', 'h', 's')

    /** Ordered small-to-large; only the ones smaller than what we may ask for need listing. */
    private val YTIMG_NAMES = listOf("default", "mqdefault", "hqdefault")

    /** Decodes a content/file/resource URI already readable by this process. */
    private suspend fun loadLocalUri(context: Context, uri: Uri): Bitmap? =
            withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                } catch (_: Exception) {
                    // A MediaStore album-art URI throws SecurityException without the media
                    // permission, and FileNotFoundException for an album with no stored cover.
                    // Both mean "no art from here", not "the queue is broken".
                    null
                }
            }

    /**
     * Album art via MediaStore, for local players whose queue items identify a library track.
     *
     * Two shapes are covered: a numeric `mediaId` (the `MediaStore.Audio.Media` row id, which most
     * local players use) resolved through [MediaStore.Audio.Media.EXTERNAL_CONTENT_URI], and an
     * `albumart` URI already on the description. On API 29+ the track's own embedded artwork is
     * preferred via `loadThumbnail`, which works for tracks whose album has no separate cover row.
     */
    private suspend fun mediaStoreArtwork(context: Context, description: MediaDescription): Bitmap? {
        if (!hasMediaPermission(context)) return null
        val trackId = description.mediaId?.toLongOrNull() ?: return null
        return withContext(Dispatchers.IO) {
            val trackUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, trackId)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    return@withContext context.contentResolver.loadThumbnail(
                            trackUri, Size(THUMBNAIL_REQUEST_PX, THUMBNAIL_REQUEST_PX), null)
                } catch (_: Exception) {
                    // Falls through to the album-art table below.
                }
            }
            try {
                context.contentResolver.query(
                        trackUri,
                        arrayOf(MediaStore.Audio.Media.ALBUM_ID),
                        null, null, null
                )?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use null
                    val albumId = cursor.getLong(0)
                    val albumArtUri = ContentUris.withAppendedId(
                            Uri.parse("content://media/external/audio/albumart"), albumId)
                    context.contentResolver.openInputStream(albumArtUri)?.use {
                        BitmapFactory.decodeStream(it)
                    }
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    /** Size asked of `loadThumbnail`; the caller shrinks further to the queue's own thumbnail size. */
    private const val THUMBNAIL_REQUEST_PX = 512

    /**
     * Downloads a remote cover once and reuses it from disk afterwards, through
     * [RemoteArtworkCache]. That cache used to be [ShortcutArtworkStore] - both are plain
     * URL-keyed PNG caches - until the difference in what they *hold* proved to matter more than
     * the shape they share; [RemoteArtworkCache] records why.
     */
    private suspend fun remoteArtwork(context: Context, uri: Uri, targetPx: Int): Bitmap? {
        // The rewritten URL is also the cache key, so a queue that later asks for a larger size
        // fetches it rather than reusing the smaller cached copy under the original address.
        val key = sizedArtworkUrl(uri.toString(), targetPx)
        RemoteArtworkCache.get(context, key)?.let { cached ->
            BitmapFactory.decodeByteArray(cached, 0, cached.size)?.let { return it }
        }
        return withContext(Dispatchers.IO) {
            val bytes = download(key) ?: return@withContext null
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: return@withContext null
            RemoteArtworkCache.put(context, key, bytes)
            bitmap
        }
    }

    private fun download(url: String): ByteArray? = try {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            instanceFollowRedirects = true
            // Image CDNs (Google's lh3.googleusercontent.com among them, which is where a
            // YouTube-Music-client cover URL points) can answer differently - or refuse - without
            // a User-Agent, and the default Java one is not always accepted.
            setRequestProperty("User-Agent", "Svartifoss-queue-artwork")
            // Same defence ApkDownloader documents: a pooled keep-alive connection can silently
            // truncate a redirected response on Android, and a truncated image simply fails to
            // decode - which would look exactly like "this cover doesn't work".
            setRequestProperty("Connection", "close")
        }
        try {
            if (connection.responseCode !in 200..299) {
                Timber.d("Queue cover HTTP %d for %s", connection.responseCode, url)
                null
            } else {
                connection.inputStream.use { input ->
                    val buffer = ByteArrayOutputStream()
                    val chunk = ByteArray(16 * 1024)
                    var total = 0
                    while (true) {
                        val read = input.read(chunk)
                        if (read <= 0) break
                        total += read
                        // A queue cover that large is not a cover; stop rather than buffer it.
                        if (total > MAX_REMOTE_BYTES) return@use null
                        buffer.write(chunk, 0, read)
                    }
                    buffer.toByteArray()
                }
            }
        } finally {
            connection.disconnect()
        }
    } catch (e: Exception) {
        Timber.d(e, "Queue cover download failed")
        null
    }
}
