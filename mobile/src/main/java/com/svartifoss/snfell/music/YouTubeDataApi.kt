package com.svartifoss.snfell.music

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.preference.PreferenceManager
import com.svartifoss.snfell.BuildConfig
import com.svartifoss.snfell.common.MiscPreferences
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale

/**
 * The one way this app calls the YouTube Data API: the key, and the request that carries it.
 *
 * Two callers share it - the cover lookup for a track without artwork ([YouTubeThumbnailFetcher])
 * and the song list of a saved playlist ([ShortcutTracksResponder]) - and they must agree on both
 * halves. The key is the build's own, unless the user typed one in. Every request carries this
 * app's package name and signing certificate, because the key is restricted to the Android app in
 * Google Cloud and answers 403 to anything else (the restriction has to list every certificate
 * that can sign a build, Play App Signing's included).
 *
 * The key travels in the query string, as Google documents, and that URL is never logged.
 */
internal object YouTubeDataApi {

    private const val CONNECT_TIMEOUT_MS = 12_000
    private const val READ_TIMEOUT_MS = 12_000

    /** An answer: the HTTP status, and the body when the status was 200. */
    class Response(val code: Int, val body: String?)

    /** The user's own key when one was typed in, otherwise the one the build carries. */
    fun apiKey(context: Context): String {
        val userKey = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(MiscPreferences.YOUTUBE_DATA_API_KEY.key, "")
                ?.trim()
                .orEmpty()
        return userKey.ifBlank { BuildConfig.YOUTUBE_DATA_API_KEY.trim() }
    }

    fun isConfigured(context: Context): Boolean = apiKey(context).isNotEmpty()

    /**
     * GETs [endpoint] with [params] and the key. Null when there is no key or the request never
     * got an answer (no connection, a timeout); otherwise the status, with the body on a 200.
     * Call off the main thread.
     */
    fun get(context: Context, endpoint: String, params: Map<String, String>, userAgent: String): Response? {
        val key = apiKey(context)
        if (key.isEmpty()) return null
        val query = (params + ("key" to key)).entries.joinToString("&") { (name, value) ->
            URLEncoder.encode(name, "UTF-8") + "=" + URLEncoder.encode(value, "UTF-8")
        }
        return try {
            val connection = URL("$endpoint?$query").openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", userAgent)
                connection.setRequestProperty("Connection", "close")
                connection.setRequestProperty("X-Android-Package", context.packageName)
                signingCertificateSha1(context)?.let {
                    connection.setRequestProperty("X-Android-Cert", it)
                }
                val code = connection.responseCode
                if (code != HttpURLConnection.HTTP_OK) {
                    Timber.w("YouTube Data API request failed with HTTP %d", code)
                    return Response(code, null)
                }
                Response(code, connection.inputStream.bufferedReader().use { it.readText() })
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Timber.w(e, "YouTube Data API request failed")
            null
        }
    }

    /** The header format is uppercase SHA-1 hex without colons or a prefix. */
    private fun signingCertificateSha1(context: Context): String? = try {
        val packageManager = context.packageManager
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        }
        val signature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures?.firstOrNull()
        } ?: return null
        val digest = MessageDigest.getInstance("SHA-1").digest(signature.toByteArray())
        buildString(digest.size * 2) {
            digest.forEach { append("%02X".format(Locale.US, it.toInt() and 0xFF)) }
        }
    } catch (e: Exception) {
        Timber.w(e, "Could not determine the app signing certificate")
        null
    }
}
