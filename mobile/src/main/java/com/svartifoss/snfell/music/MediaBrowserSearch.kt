package com.svartifoss.snfell.music

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Searches a music app's MediaBrowserService for tracks matching a query - the same mechanism
 * Android Auto and Assistant use for voice search, and far more widely and reliably implemented
 * by music apps (including YouTube Music) than `MediaSession.playFromSearch`, which many apps
 * silently ignore.
 */
object MediaBrowserSearch {
    private const val TIMEOUT_MS = 6000L

    /**
     * Null means the app has no browsable library at all (no MediaBrowserService found, or
     * connecting/searching timed out) - as opposed to an empty list, which means the app was
     * reached but genuinely found no matches.
     */
    suspend fun search(context: Context, packageName: String, query: String): List<MediaBrowserCompat.MediaItem>? {
        val serviceComponent = findBrowserService(context, packageName) ?: return null

        return withTimeoutOrNull(TIMEOUT_MS) {
            val browser = connect(context, serviceComponent) ?: return@withTimeoutOrNull null
            try {
                searchOn(browser, query)
            } finally {
                browser.disconnect()
            }
        }
    }

    private fun findBrowserService(context: Context, packageName: String): ComponentName? {
        val intent = Intent("android.media.browse.MediaBrowserService").setPackage(packageName)
        val resolveInfo = context.packageManager.queryIntentServices(intent, 0).firstOrNull()
                ?: return null
        return ComponentName(resolveInfo.serviceInfo.packageName, resolveInfo.serviceInfo.name)
    }

    private suspend fun connect(context: Context, component: ComponentName): MediaBrowserCompat? =
            suspendCancellableCoroutine { continuation ->
                lateinit var browser: MediaBrowserCompat
                browser = MediaBrowserCompat(
                        context,
                        component,
                        object : MediaBrowserCompat.ConnectionCallback() {
                            override fun onConnected() {
                                if (continuation.isActive) continuation.resume(browser)
                            }

                            override fun onConnectionSuspended() {
                                if (continuation.isActive) continuation.resume(null)
                            }

                            override fun onConnectionFailed() {
                                if (continuation.isActive) continuation.resume(null)
                            }
                        },
                        null
                )
                continuation.invokeOnCancellation { browser.disconnect() }
                browser.connect()
            }

    private suspend fun searchOn(browser: MediaBrowserCompat, query: String): List<MediaBrowserCompat.MediaItem> =
            suspendCancellableCoroutine { continuation ->
                browser.search(query, null, object : MediaBrowserCompat.SearchCallback() {
                    override fun onSearchResult(
                            query: String,
                            extras: Bundle?,
                            items: MutableList<MediaBrowserCompat.MediaItem>
                    ) {
                        if (continuation.isActive) continuation.resume(items)
                    }

                    override fun onError(query: String, extras: Bundle?) {
                        if (continuation.isActive) continuation.resume(emptyList())
                    }
                })
            }
}
