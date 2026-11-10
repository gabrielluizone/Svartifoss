package com.svartifoss.snfell.common

import java.security.MessageDigest

/**
 * The key a media action's icon travels under, and the asset it is attached as - see
 * `MediaAction.iconKey` in music.proto.
 *
 * A content digest, so the phone needs no table of which icons the watch already holds: the same
 * bytes always make the same key, the watch keeps what it has received by key, and the Data Layer
 * stores assets by content too, so attaching an unchanged icon to the next state costs nothing on
 * the link. Sixty-four bits of SHA-256: a playing app has a handful of icons, and two of them
 * colliding is not a case worth a longer name.
 *
 * Pure and free of `android.*`.
 */
object MediaActionIcons {

    private const val KEY_BYTES = 8
    private val HEX = "0123456789abcdef".toCharArray()

    fun keyFor(png: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(png)
        val key = CharArray(KEY_BYTES * 2)
        for (i in 0 until KEY_BYTES) {
            val byte = digest[i].toInt() and 0xFF
            key[i * 2] = HEX[byte ushr 4]
            key[i * 2 + 1] = HEX[byte and 0x0F]
        }
        return String(key)
    }

    fun assetName(key: String): String = CommPaths.ASSET_ACTION_ICON_PREFIX + key
}
