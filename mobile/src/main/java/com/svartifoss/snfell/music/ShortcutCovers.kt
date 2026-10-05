package com.svartifoss.snfell.music

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

/**
 * The one answer to "what picture does this streaming destination have", for every place that
 * lists one as a destination: the watch's actions menu, the quick panel's rows, the shortcut list
 * and the shortcut's own screen, and their counterparts on the phone.
 *
 * In order, best first:
 * 1. the cover fetched for this exact link (the opt-in artwork lookup);
 * 2. for a [StreamingCollection], the service's own artwork for it, fetched under the same opt-in;
 * 3. for a collection, its drawn cover ([ShortcutCoverArt.collection]);
 * 4. otherwise the service's drawn cover with the player's mark on it ([ShortcutCoverArt.service]).
 *
 * Buttons deliberately do not use this. A round mini button or quick-panel slot holds a glyph, and
 * a square cover shrunk into one reads as a sticker - so `PhoneAction.defaultIcon` stays the glyph
 * there and only the listed form (`PhoneAction.listCover`) is a cover.
 */
object ShortcutCovers {

    /** [link]'s cover, or null when there is neither a picture nor an installed app to draw. */
    fun forLink(context: Context, link: String): Bitmap? {
        ShortcutArtworkStore.get(context, link)?.let(::decode)?.let { return it }
        StreamingCollection.forLink(link)?.let { return forCollection(context, it) }
        val mark = ShortcutAppMark.forLink(context, link) ?: return null
        val service = StreamingShortcutLinks.detect(link)
        val markKey = service.packageName ?: ShortcutAppMark.defaultHandler(context, link).orEmpty()
        return ShortcutCoverArt.service(service, mark, markKey)
    }

    /** [collection]'s official artwork when it was fetched, its drawn cover otherwise. */
    fun forCollection(context: Context, collection: StreamingCollection): Bitmap =
            ShortcutArtworkStore.get(context, collection.storeKey)?.let(::decode)
                    ?: ShortcutCoverArt.collection(context, collection)

    /**
     * [link]'s cover as PNG bytes, for the shortcut list the watch receives. A fetched picture is
     * sent as the bytes already on disk rather than decoded and encoded again.
     */
    fun pngForLink(context: Context, link: String): ByteArray? {
        ShortcutArtworkStore.get(context, link)?.let { return it }
        StreamingCollection.forLink(link)?.let { collection ->
            ShortcutArtworkStore.get(context, collection.storeKey)?.let { return it }
        }
        return forLink(context, link)?.let(::encode)
    }

    private fun decode(png: ByteArray): Bitmap? = try {
        BitmapFactory.decodeByteArray(png, 0, png.size)
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
