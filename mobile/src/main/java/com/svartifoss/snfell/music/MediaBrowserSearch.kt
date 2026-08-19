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

    /**
     * Service actions that identify a bindable media browser, most specific first.
     *
     * The legacy action stays first so an app that publishes one keeps resolving exactly as it
     * always did - the Media3 entries only ever come into play where the old query found nothing.
     *
     * Those two exist because **a Media3 app is not required to advertise the legacy action at
     * all**, and the current generation of apps doesn't. SoundCloud is the case that exposed this:
     * its service declares `MediaLibraryService` and `MediaSessionService` and nothing else, so a
     * query for the legacy action returned no results and every browser-backed feature - search,
     * library browsing, and both background-playback routes - silently concluded the app had no
     * library and gave up. That is one missing intent filter costing four features, in an app that
     * implements all of them.
     *
     * Binding still happens through [MediaBrowserCompat], which is fine and is the whole point:
     * it binds the **explicit** component with the legacy action, and explicit binds don't consult
     * intent filters. Media3's `MediaSessionService.onBind` answers that action with its legacy
     * browser binder, so an old-style client talks to a modern service unchanged.
     *
     * `MediaSessionService` is included even though it carries no library: connecting to one still
     * wakes the app and yields its session, which is exactly what the play-with-the-screen-off path
     * needs. Browsing such a service simply comes back empty, which callers already treat as "no
     * library here".
     */
    private val BROWSER_SERVICE_ACTIONS = listOf(
            "android.media.browse.MediaBrowserService",
            "androidx.media3.session.MediaLibraryService",
            "androidx.media3.session.MediaSessionService"
    )

    internal fun findBrowserService(context: Context, packageName: String): ComponentName? {
        for (action in BROWSER_SERVICE_ACTIONS) {
            val intent = Intent(action).setPackage(packageName)
            val resolveInfo = context.packageManager
                    .queryIntentServices(intent, 0)
                    .firstOrNull()
                    ?: continue
            return ComponentName(resolveInfo.serviceInfo.packageName, resolveInfo.serviceInfo.name)
        }
        return null
    }

    /** The action list, for the test that pins its order. */
    internal fun browserServiceActions(): List<String> = BROWSER_SERVICE_ACTIONS

    internal suspend fun connect(context: Context, component: ComponentName): MediaBrowserCompat? =
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
