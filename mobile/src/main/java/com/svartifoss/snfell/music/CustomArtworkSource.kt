package com.svartifoss.snfell.music

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import com.matejdro.wearutils.preferences.definition.PreferenceDefinition
import androidx.preference.PreferenceManager
import com.svartifoss.snfell.common.AlbumArtSource
import com.svartifoss.snfell.common.MiscPreferences
import timber.log.Timber
import kotlin.random.Random

/**
 * Resolves the two picture sources that come from the user's own storage rather than from a lookup:
 * [AlbumArtSource.CUSTOM_IMAGE] (one chosen picture) and [AlbumArtSource.CUSTOM_FOLDER] (a chosen
 * folder, one of its pictures per track).
 *
 * ## It feeds the existing backdrop pipeline, and adds nothing to it
 *
 * The result goes out as `CommPaths.ASSET_BACKDROP_ART` - the same asset the artist photograph and
 * the online cover already use - so the watch needs no change at all: every background style,
 * artwork filter, shading layer and accent floor applies, and the album-accent palette resolves
 * from the picture actually on screen. That is the whole reason the source axis was split out of
 * the treatment axis, and it is why a chosen photograph tints the clock and the progress ring
 * exactly as a sleeve would.
 *
 * ## Decoding is bounds-first, always
 *
 * Everything else that reaches this asset is a thumbnail-sized download. These are photographs out
 * of somebody's gallery, where twelve megapixels is ordinary and decoding one at full size costs
 * roughly 48 MB of heap for a picture that is about to be scaled to 512px on its longest side. So
 * the bounds are read first and `inSampleSize` set from them, which caps the decode near the size
 * actually wanted.
 *
 * ## What it does not do
 *
 * No network, ever, and no dependence on [MiscPreferences.ONLINE_ARTWORK_ENABLED] - that switch
 * governs leaving the device, and these sources never do. It also never reads outside the URIs the
 * user granted through the system picker.
 */
object CustomArtworkSource {

    /**
     * How long an enumerated folder listing is trusted, in milliseconds.
     *
     * Listing a document tree is a `ContentProvider` query, and this runs once per track change on
     * a service that is also assembling a state payload, so re-enumerating every time would put a
     * cross-process query on a hot path to answer a question whose answer almost never changes.
     * Five minutes is short enough that pictures added to the folder show up in the same listening
     * session and long enough that an album playing through costs one query.
     */
    private const val FOLDER_LISTING_TTL_MS = 5 * 60 * 1000L

    /**
     * Bound on how many pictures are remembered from a folder.
     *
     * A tree URI can name a directory with thousands of files, and the listing is held in memory
     * for the TTL above. The cap is not about the random draw - which is uniform over whatever is
     * listed - but about not holding an unbounded list of strings in a foreground service for a
     * feature whose visible behaviour is identical at 500 pictures and at 50,000.
     */
    private const val MAX_FOLDER_ENTRIES = 500

    /** Cached bytes for the single-image source, keyed by the URI they came from. */
    private var cachedImageUri: String? = null
    private var cachedImageBytes: ByteArray? = null

    /** Cached folder listing, its tree URI, and when it was taken. */
    private var cachedFolderUri: String? = null
    private var cachedFolderEntries: List<Uri> = emptyList()
    private var cachedFolderListedAt = 0L

    /**
     * The picture [source] should draw for the track identified by [trackKey], or null.
     *
     * Null means "there is nothing to draw" and the caller falls back to whatever the player
     * published - covering an unset picker, a revoked grant, a deleted file and an empty folder,
     * deliberately without distinguishing them, since the screen does the same thing in every case.
     *
     * [trackKey] is what makes the folder source re-roll: the same key returns the same picture, so
     * a state rebuild mid-track (a pause, a volume nudge) does not swap the backdrop under the user,
     * while a new track draws again. Blocking on I/O; call it off the main thread.
     */
    fun artworkFor(context: Context, source: AlbumArtSource, trackKey: String): ByteArray? =
            when (source) {
                AlbumArtSource.CUSTOM_IMAGE -> chosenImage(context)
                AlbumArtSource.CUSTOM_FOLDER -> imageFromFolder(context, trackKey)
                else -> null
            }

    /**
     * The picture to show in the phone's watch preview for [source].
     *
     * Deliberately not a random draw for the folder source: the miniature is a still image that
     * redraws on every slider move and every picker change, so re-rolling would make the background
     * flicker between photographs while somebody adjusts a completely unrelated setting - which
     * reads as a glitch rather than as the feature. The first picture in the folder is a stable,
     * honest sample of what the wrist will show.
     *
     * Blocking on I/O; call it off the main thread.
     */
    fun previewArtwork(context: Context, source: AlbumArtSource): ByteArray? = when (source) {
        AlbumArtSource.CUSTOM_IMAGE -> chosenImage(context)
        AlbumArtSource.CUSTOM_FOLDER ->
            preference(context, MiscPreferences.CUSTOM_ALBUM_ART_FOLDER)
                    ?.let { folderEntries(context, it) }
                    ?.firstOrNull()
                    ?.let { decode(context, it) }
        else -> null
    }

    /** True when [source] is one this object resolves and the user has actually picked something
     *  for it - the check `MusicService` uses to decide whether to attempt a resolve at all. */
    fun isConfigured(context: Context, source: AlbumArtSource): Boolean = when (source) {
        AlbumArtSource.CUSTOM_IMAGE -> preference(context, MiscPreferences.CUSTOM_ALBUM_ART_IMAGE) != null
        AlbumArtSource.CUSTOM_FOLDER -> preference(context, MiscPreferences.CUSTOM_ALBUM_ART_FOLDER) != null
        else -> false
    }

    /** Drops every cached listing and picture. Called when the picked URIs change, since the
     *  caches are keyed by URI and would otherwise keep serving the previous choice until the
     *  folder TTL lapsed. */
    fun invalidate() {
        cachedImageUri = null
        cachedImageBytes = null
        cachedFolderUri = null
        cachedFolderEntries = emptyList()
        cachedFolderListedAt = 0L
    }

    private fun chosenImage(context: Context): ByteArray? {
        val uri = preference(context, MiscPreferences.CUSTOM_ALBUM_ART_IMAGE) ?: return null
        cachedImageBytes?.takeIf { cachedImageUri == uri }?.let { return it }
        val decoded = decode(context, Uri.parse(uri))
        // A failed decode is cached as an absence too. Without that, a picture the user has since
        // deleted is re-attempted on every single track change, each attempt a provider round trip
        // that is already known to fail.
        cachedImageUri = uri
        cachedImageBytes = decoded
        return decoded
    }

    private fun imageFromFolder(context: Context, trackKey: String): ByteArray? {
        val tree = preference(context, MiscPreferences.CUSTOM_ALBUM_ART_FOLDER) ?: return null
        val entries = folderEntries(context, tree)
        if (entries.isEmpty()) return null

        // Seeded from the track rather than drawn freshly, so the same track keeps the same picture
        // for as long as it plays. `MusicService` rebuilds its state for any change at all - a
        // pause, a seek, a volume step - and an unseeded draw would flick the backdrop to a
        // different photograph on each one.
        val index = Random(trackKey.hashCode().toLong()).nextInt(entries.size)
        val chosen = entries[index]
        decode(context, chosen)?.let { return it }

        // One retry with a different picture: the common failure here is a single unreadable or
        // unsupported file in an otherwise good folder, and giving up on it would make that one
        // file break every track whose key happens to select it.
        val fallback = entries[(index + 1) % entries.size]
        return if (fallback == chosen) null else decode(context, fallback)
    }

    private fun folderEntries(context: Context, tree: String): List<Uri> {
        val now = System.currentTimeMillis()
        if (cachedFolderUri == tree && now - cachedFolderListedAt < FOLDER_LISTING_TTL_MS) {
            return cachedFolderEntries
        }
        val entries = listPictures(context, tree)
        cachedFolderUri = tree
        cachedFolderEntries = entries
        cachedFolderListedAt = now
        return entries
    }

    /**
     * Every picture directly inside the picked folder.
     *
     * Deliberately not recursive. A tree URI can root a directory of directories, and walking it
     * would turn one query into an unbounded number of them on a service's track-change path - and
     * "pictures from this folder" is also what the user was choosing when they picked one.
     *
     * Sorted by document id so the listing is stable between enumerations. That matters because the
     * random draw indexes into it by a seed derived from the track: an unstable order would give
     * the same track a different picture after every re-listing, which is exactly the flicker the
     * seed exists to prevent.
     */
    private fun listPictures(context: Context, tree: String): List<Uri> {
        val treeUri = try {
            Uri.parse(tree)
        } catch (e: Exception) {
            Timber.w(e, "Custom artwork folder URI could not be parsed")
            return emptyList()
        }
        val childrenUri = try {
            DocumentsContract.buildChildDocumentsUriUsingTree(
                    treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        } catch (e: Exception) {
            Timber.w(e, "Custom artwork folder is not a document tree")
            return emptyList()
        }

        val found = mutableListOf<Pair<String, Uri>>()
        try {
            context.contentResolver.query(
                    childrenUri,
                    arrayOf(
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_MIME_TYPE),
                    null, null, null)?.use { cursor ->
                while (cursor.moveToNext() && found.size < MAX_FOLDER_ENTRIES) {
                    val documentId = cursor.getString(0) ?: continue
                    val mimeType = cursor.getString(1).orEmpty()
                    if (!mimeType.startsWith("image/")) continue
                    found += documentId to
                            DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                }
            }
        } catch (e: Exception) {
            // A revoked grant surfaces here as a SecurityException, and a provider that has gone
            // away as something else again. None of them is worth distinguishing: the folder is
            // unreadable and the caller falls back to the player's own cover.
            Timber.w(e, "Could not list the custom artwork folder")
            return emptyList()
        }
        Timber.d("Custom artwork folder listed %d picture(s)", found.size)
        return found.sortedBy { it.first }.map { it.second }
    }

    /**
     * Reads [uri] into backdrop-sized JPEG bytes, or null if it cannot be read or is not an image.
     *
     * Two passes over the stream, because `inJustDecodeBounds` consumes it: the first learns the
     * dimensions, the second decodes subsampled. Opening twice is cheaper by a wide margin than
     * decoding a phone photograph at full resolution.
     */
    private fun decode(context: Context, uri: Uri): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            } ?: return null
        } catch (e: Exception) {
            Timber.d(e, "Custom artwork could not be measured")
            return null
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
        }
        val bitmap = try {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        } catch (e: Exception) {
            Timber.d(e, "Custom artwork could not be decoded")
            null
        } catch (e: OutOfMemoryError) {
            // Subsampling makes this unlikely rather than impossible - a single enormous picture
            // can still lose the race against whatever else the service is holding - and it must
            // not take the media service down over a background image.
            Timber.w(e, "Custom artwork was too large to decode")
            null
        } ?: return null

        return OnlineArtworkFetcher.encodeForBackdrop(bitmap)
    }

    /**
     * The power-of-two subsampling factor that keeps the decode at or just above the size wanted.
     *
     * Halving while both dimensions stay at least twice [OnlineArtworkFetcher.MAX_ARTWORK_PX] is
     * the standard contract `inSampleSize` documents: it never lands below the target, so the
     * scale-down in `encodeForBackdrop` still has real pixels to work from and the result is not a
     * blur of an already-shrunken decode.
     */
    private fun sampleSizeFor(width: Int, height: Int): Int {
        var sample = 1
        var currentWidth = width
        var currentHeight = height
        while (currentWidth / 2 >= OnlineArtworkFetcher.MAX_ARTWORK_PX &&
                currentHeight / 2 >= OnlineArtworkFetcher.MAX_ARTWORK_PX) {
            currentWidth /= 2
            currentHeight /= 2
            sample *= 2
        }
        return sample
    }

    private fun preference(
            context: Context,
            definition: PreferenceDefinition<String>
    ): String? = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            .getString(definition.key, definition.defaultValue)
            ?.takeIf { it.isNotBlank() }
}
