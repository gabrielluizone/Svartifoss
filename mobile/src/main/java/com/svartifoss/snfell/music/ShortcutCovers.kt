package com.svartifoss.snfell.music

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.SampledBitmaps
import java.io.ByteArrayOutputStream
import java.util.EnumMap

/**
 * The one answer to "what picture does this streaming destination have", for every place that
 * lists one as a destination: the watch's actions menu, the quick panel's rows, the shortcut list
 * and the shortcut's own screen, and their counterparts on the phone.
 *
 * In order, best first:
 * 1. for a [StreamingCollection] that ships its cover inside the app, that cover ([bundledResource]);
 * 2. the cover fetched for this exact link (the opt-in artwork lookup);
 * 3. for a collection, the service's own artwork for it, fetched under the same opt-in;
 * 4. for a collection, its drawn cover ([ShortcutCoverArt.collection]);
 * 5. otherwise the service's drawn cover with the player's mark on it ([ShortcutCoverArt.service]).
 *
 * The bundled cover comes first because it is the collection's *chosen* picture, not a stand-in for
 * a missing one: it needs no network and no setting, and a copy an earlier build fetched for the
 * same collection must not outrank it.
 *
 * Buttons deliberately do not use this. A round mini button or quick-panel slot holds a glyph, and
 * a square cover shrunk into one reads as a sticker - so `PhoneAction.defaultIcon` stays the glyph
 * there and only the listed form (`PhoneAction.listCover`) is a cover.
 */
object ShortcutCovers {

    /** [link]'s cover, or null when there is neither a picture nor an installed app to draw. */
    fun forLink(context: Context, link: String): Bitmap? {
        val collection = StreamingCollection.forLink(link)
        collection?.let { bundledFor(context, it) }?.let { return it }
        ShortcutArtworkStore.get(context, link)?.let(::decode)?.let { return it }
        collection?.let { return forCollection(context, it) }
        val mark = ShortcutAppMark.forLink(context, link) ?: return null
        val service = StreamingShortcutLinks.detect(link)
        val markKey = service.packageName ?: ShortcutAppMark.defaultHandler(context, link).orEmpty()
        return ShortcutCoverArt.service(service, mark, markKey)
    }

    /**
     * [collection]'s bundled cover when it has one, else its official artwork when that was
     * fetched, else its drawn cover.
     */
    fun forCollection(context: Context, collection: StreamingCollection): Bitmap =
            bundledFor(context, collection)
                    ?: ShortcutArtworkStore.get(context, collection.storeKey)?.let(::decode)
                    ?: ShortcutCoverArt.collection(context, collection)

    /**
     * [link]'s cover as PNG bytes, for the shortcut list the watch receives. A fetched picture is
     * sent as the bytes already on disk rather than decoded and encoded again.
     */
    fun pngForLink(context: Context, link: String): ByteArray? {
        val collection = StreamingCollection.forLink(link)
        collection?.let { bundledFor(context, it) }?.let { return encode(it) }
        ShortcutArtworkStore.get(context, link)?.let { return it }
        collection?.let { ShortcutArtworkStore.get(context, it.storeKey) }?.let { return it }
        return forLink(context, link)?.let(::encode)
    }

    /**
     * The drawable that is [collection]'s cover, for the collections that ship one inside the app
     * rather than drawing it ([ShortcutCoverArt]) or fetching it ([ShortcutArtworkFetcher]).
     *
     * YouTube Music's Liked Music is the one: its cover is a picture the owner chose, so it is
     * bundled - which is also what makes it work with the online artwork switch off, as it is by
     * default. Changing the picture means bumping [BUNDLED_REVISION], or phones that already sent
     * the old one to the watch never send the new one.
     */
    internal fun bundledResource(collection: StreamingCollection): Int? = when (collection) {
        StreamingCollection.YOUTUBE_MUSIC_LIKED -> R.drawable.ytmusic_liked_songs_old
        else -> null
    }

    /**
     * The bundled cover at the size every cover here is drawn at, decoded once: list rows ask for
     * a cover on every bind. Null when the collection ships none, or when decoding failed - the
     * caller then falls through to the next source, ending at the drawn cover.
     */
    private fun bundledFor(context: Context, collection: StreamingCollection): Bitmap? {
        val resource = bundledResource(collection) ?: return null
        synchronized(bundled) { bundled[collection]?.let { return it } }
        val picture = decodeBundled(context, resource) ?: return null
        synchronized(bundled) { bundled[collection] = picture }
        return picture
    }

    private val bundled = EnumMap<StreamingCollection, Bitmap>(StreamingCollection::class.java)

    private fun decodeBundled(context: Context, resource: Int): Bitmap? = try {
        // No density scaling (the resource lives in drawable-nodpi) and half the pixels on the way
        // in: the picture is ~740 px and the cover is drawn at ShortcutCoverArt.SIZE_PX.
        val options = BitmapFactory.Options().apply {
            inScaled = false
            inSampleSize = 2
        }
        BitmapFactory.decodeResource(context.resources, resource, options)?.let { decoded ->
            val side = ShortcutCoverArt.SIZE_PX
            if (decoded.width == side && decoded.height == side) {
                decoded
            } else {
                Bitmap.createScaledBitmap(decoded, side, side, true).also {
                    if (it !== decoded) decoded.recycle()
                }
            }
        }
    } catch (_: RuntimeException) {
        null
    }

    /**
     * Whether a cover that ships inside the app changed since this phone last sent its lists to the
     * watch. The action list is re-sent only when its metadata or assets are missing, never because
     * a picture's bytes changed, so without this a watch that already holds the old cover would keep
     * it until the menu happened to be edited.
     */
    fun bundledCoversNeedRepublish(context: Context): Boolean =
            state(context).getInt(KEY_PUBLISHED_REVISION, 0) < BUNDLED_REVISION

    /** Records that the lists went out with the current bundled covers. */
    fun markBundledCoversRepublished(context: Context) {
        state(context).edit().putInt(KEY_PUBLISHED_REVISION, BUNDLED_REVISION).apply()
    }

    /** Its own file: the default preferences are mirrored, filtered, to the watch and backed up. */
    private fun state(context: Context) =
            context.applicationContext.getSharedPreferences(STATE_FILE, Context.MODE_PRIVATE)

    /** Bumped whenever a picture [bundledResource] names changes. */
    private const val BUNDLED_REVISION = 1
    private const val STATE_FILE = "shortcut_cover_state"
    private const val KEY_PUBLISHED_REVISION = "bundled_cover_revision_published"

    private fun decode(png: ByteArray): Bitmap? = try {
        SampledBitmaps.decode(png, ShortcutArtworkFetcher.MAX_THUMBNAIL_PX)
    } catch (_: RuntimeException) {
        null
    }

    /** Keyed by the drawn cover itself, which [ShortcutCoverArt] already caches: several
     *  shortcuts to one service share one cover, and the list is re-sent on every phone start. */
    private val encoded = java.util.WeakHashMap<Bitmap, ByteArray>()

    private fun encode(bitmap: Bitmap): ByteArray {
        synchronized(encoded) { encoded[bitmap]?.let { return it } }
        val bytes = ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        }
        synchronized(encoded) { encoded[bitmap] = bytes }
        return bytes
    }
}
