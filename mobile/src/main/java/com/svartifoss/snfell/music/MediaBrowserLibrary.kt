package com.svartifoss.snfell.music

import android.content.Context
import android.support.v4.media.MediaBrowserCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Reads one page of a music app's browsable library - the same MediaBrowserService tree Android
 * Auto shows, and the only library contract music apps implement with any consistency.
 *
 * Deliberately a sibling of [MediaBrowserSearch] rather than an extension of it: both bind the same
 * service the same way (and share [MediaBrowserSearch.findBrowserService]/`connect`), but searching
 * is a single stateless query while browsing is a walk the caller drives one level at a time.
 *
 * Every call connects and disconnects. Holding the browser open between pages would be faster, but
 * this runs from a service that can be killed at any moment, and a leaked binding to another app's
 * service is worse than reconnecting - which is cheap, since the app is already awake and playing.
 */
object MediaBrowserLibrary {
    private const val TIMEOUT_MS = 6000L

    /** A page of children plus the root id the walk started from. */
    data class Page(val rootId: String, val children: List<MediaBrowserCompat.MediaItem>)

    /**
     * Loads [parentId]'s children, or the library root when [parentId] is null.
     *
     * Null means the app exposes no browsable library at all (no MediaBrowserService, or the
     * connection timed out) - distinct from a Page with an empty child list, which means the app
     * answered and that node is genuinely empty.
     */
    suspend fun browse(context: Context, packageName: String, parentId: String?): Page? {
        val component = MediaBrowserSearch.findBrowserService(context, packageName) ?: return null

        return withTimeoutOrNull(TIMEOUT_MS) {
            val browser = MediaBrowserSearch.connect(context, component) ?: return@withTimeoutOrNull null
            try {
                // getRoot() is only valid while connected, and it is also the id to browse when the
                // caller asked for the top of the tree.
                val rootId = browser.root
                Page(rootId, childrenOf(browser, parentId ?: rootId))
            } finally {
                browser.disconnect()
            }
        }
    }

    private suspend fun childrenOf(
            browser: MediaBrowserCompat,
            parentId: String
    ): List<MediaBrowserCompat.MediaItem> = suspendCancellableCoroutine { continuation ->
        // subscribe() can call back more than once (apps push updates as their library loads), so
        // every path guards on isActive and unsubscribes - resuming a continuation twice throws.
        val callback = object : MediaBrowserCompat.SubscriptionCallback() {
            override fun onChildrenLoaded(
                    parentId: String,
                    children: MutableList<MediaBrowserCompat.MediaItem>
            ) {
                if (continuation.isActive) {
                    browser.unsubscribe(parentId)
                    continuation.resume(children)
                }
            }

            override fun onError(parentId: String) {
                if (continuation.isActive) {
                    browser.unsubscribe(parentId)
                    continuation.resume(emptyList())
                }
            }
        }
        continuation.invokeOnCancellation { browser.unsubscribe(parentId) }
        browser.subscribe(parentId, callback)
    }
}
