package com.svartifoss.snfell.music

/**
 * The covers recently encoded for the watch, so a picture it already holds is not sent again under
 * different bytes.
 *
 * The Data Layer addresses an asset by a digest of its bytes, and only transfers a digest the other
 * side does not have. That makes an unchanged cover free on every later state - but only while the
 * bytes really are identical, and the same cover reaches `MusicService` by two routes that never
 * produce identical bytes (see [CoverSignature]): the copy sent ahead as a neighbouring track's
 * cover comes from the queue row, the copy sent when that track starts comes from the player's own
 * bitmap. Measured on 2026-11-11 with YouTube Music, every cover sent ahead crossed Bluetooth a
 * second time when its track began (35 to 57 KB each time), and pictures were 89% of everything
 * this app sent to the watch (3.69 MB of 4.14 MB since the phone's last boot).
 *
 * So before a cover is encoded, the caller asks here whether the watch was already sent this
 * picture **for this track**, and reuses those bytes when it was. Both conditions are required:
 *
 * - **the same track**, by title, the identity the watch itself matches a neighbouring cover on.
 *   A fingerprint alone cannot tell two black sleeves with different small lettering apart, and
 *   the cost of that mistake is the wrong cover on the wrist for a whole track.
 * - **the same picture**, by [CoverSignature.isSamePictureAs], which also requires the same encoded
 *   size. `encodeArtworkForWatch` never enlarges a source, so a small picture is a small encoding
 *   and can never stand in for a full-size one - a sharper cover always replaces a softer one.
 *
 * A miss costs exactly what it always did: the cover is encoded and sent.
 *
 * It also works in the other direction: once a track has played, its cover becomes the neighbour
 * *behind*, re-resolved from the queue row - which for a track that was never sent ahead (the first
 * of a session, or one reached by jumping through the queue) was a second copy of its own.
 *
 * Holds encoded bytes, so it is kept small - the playing cover, the neighbours either side, and a
 * few just left behind. Pure and free of `android.*`; every method is synchronized, because the
 * neighbouring covers are encoded concurrently.
 */
class WatchCoverLedger(private val capacity: Int = CAPACITY) {

    /** Bytes the watch already holds for a picture, and how close that picture is to the one asked about. */
    class Held(val bytes: ByteArray, val distance: CoverSignature.Distance)

    private class Entry(val title: String, val signature: CoverSignature, val bytes: ByteArray)

    /** Oldest first; a hit moves its entry to the end. */
    private val entries = ArrayList<Entry>()

    /**
     * The encoding already sent for [title]'s picture, or null when there is none close enough to
     * [signature] - see the class comment for what "close enough" requires.
     */
    @Synchronized
    fun find(title: String, signature: CoverSignature): Held? {
        val wanted = normalize(title) ?: return null
        var best: Entry? = null
        var bestDistance: CoverSignature.Distance? = null
        for (entry in entries) {
            if (entry.title != wanted || !entry.signature.isSamePictureAs(signature)) continue
            val distance = entry.signature.distanceTo(signature)
            if (bestDistance == null || distance.mean < bestDistance.mean) {
                best = entry
                bestDistance = distance
            }
        }
        val found = best ?: return null
        entries.remove(found)
        entries.add(found)
        return Held(found.bytes, bestDistance!!)
    }

    /** Records that [bytes] is what [title]'s picture, fingerprinted as [signature], was encoded to. */
    @Synchronized
    fun remember(title: String, signature: CoverSignature, bytes: ByteArray) {
        val key = normalize(title) ?: return
        // One encoding per picture per track: a second entry for the same picture could only ever
        // lose to the first in find, while still spending a slot and its bytes.
        entries.removeAll { it.title == key && it.signature.isSamePictureAs(signature) }
        entries.add(Entry(key, signature, bytes))
        while (entries.size > capacity) entries.removeAt(0)
    }

    /** How many encodings are held - for tests and the log. */
    @Synchronized
    fun size(): Int = entries.size

    @Synchronized
    fun clear() = entries.clear()

    private fun normalize(title: String): String? = title.trim().lowercase().takeIf { it.isNotEmpty() }

    companion object {
        /** The playing cover, two neighbours either side, and three just left behind. */
        const val CAPACITY = 8
    }
}
