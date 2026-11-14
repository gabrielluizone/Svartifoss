package com.svartifoss.snfell.notifications

/**
 * Whether a media notification can be taken for the session the phone is driving.
 *
 * The exact test is the session token, and it is the first thing that wins - but it is not a test
 * this phone can always pass. On the Pixel 3 in the test rig (Android 10) the token inside YouTube
 * Music's notification and the one `getActiveSessions` returns for the very same session did not
 * compare equal. The registry then held the notification, `actionsForSession` found "0 for this
 * session", and the quick panel fell back to the session's custom actions
 * (docs/watch-sync-battery-investigation.md, finding 7).
 *
 * What a mismatch cannot mean is a *different* session when the package only has one. Two tokens
 * that differ are evidence of two sessions only if the app owns two; with a single session the
 * notification can only be that session's. [onlySessionOfPackage] carries that fact in, and it is
 * the whole of the fix: nothing is guessed when an app really does run several sessions at once
 * (Retro Music's separate browser session), which keeps the original rule - "do not borrow a
 * notification that positively identifies a different session" - intact.
 */
internal fun <T : Any> notificationMayBelongToSession(
        notificationToken: T?,
        sessionToken: T?,
        onlySessionOfPackage: Boolean
): Boolean = sessionToken == null ||
        notificationToken == null ||
        notificationToken == sessionToken ||
        onlySessionOfPackage

/**
 * The newest of [candidates] (one package's notifications) that can be taken for the session.
 *
 * An exact token match always wins, whatever else is stored. Failing that, the newest one that
 * [notificationMayBelongToSession] allows - a notification that carries no token at all (some OEM
 * and legacy players), or the package's only one when the package has a single session.
 */
internal fun <N, T : Any> pickNotificationForSession(
        candidates: List<N>,
        sessionToken: T?,
        onlySessionOfPackage: Boolean,
        tokenOf: (N) -> T?,
        postedAtOf: (N) -> Long
): N? {
    val exact = sessionToken?.let { token ->
        candidates.filter { tokenOf(it) == token }.maxByOrNull(postedAtOf)
    }
    if (exact != null) return exact
    return candidates
            .filter { notificationMayBelongToSession(tokenOf(it), sessionToken, onlySessionOfPackage) }
            .maxByOrNull(postedAtOf)
}

/**
 * The PNGs already drawn for an app's icon resources, so a state is not redrawn from scratch.
 *
 * Rasterizing an icon is the expensive part of a quick-panel action: several renders of the same
 * drawable through different Contexts, a pixel scan of each, a crop and a PNG encode. The session
 * custom-action path used to repeat all of it on **every** state build, and a render that drew
 * nothing was never remembered, so a failing icon was paid for again on every state. A finished
 * render is kept for as long as the entry lives; a failed one is remembered for
 * [retryFailedAfterMs] only, because "nothing drew" has been transient before: one phone logged 0
 * icons at the first state and 3 a few minutes later, same app. That one was AppCompat's vector
 * check failing on the other app's Context until our own had passed it (see
 * `remoteResourceCandidates`, which no longer uses AppCompat), but a permanent no would stick the
 * panel on generic glyphs for the process's life whatever the next cause is.
 *
 * Keys carry the package's update time, so an app update that reuses a resource id for a different
 * picture is not served the old one.
 */
internal class RenderedIconCache(
        private val maxEntries: Int,
        private val retryFailedAfterMs: Long,
        private val clock: () -> Long
) {
    private val rendered = object : LinkedHashMap<String, ByteArray>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?): Boolean =
                size > maxEntries
    }
    private val failedAt = HashMap<String, Long>()

    /** The cached PNG for [key], `null` when it is known not to render (recently), or [render]'s
     *  answer, which is then remembered. [render] runs outside the lock: it is the slow part. */
    fun getOrRender(key: String, render: () -> ByteArray?): ByteArray? {
        synchronized(this) {
            rendered[key]?.let { return it }
            val failed = failedAt[key]
            if (failed != null && clock() - failed in 0 until retryFailedAfterMs) return null
        }
        val png = render()
        synchronized(this) {
            if (png != null) {
                rendered[key] = png
                failedAt.remove(key)
            } else {
                // Bounded the same way: a package with thousands of resource ids cannot grow this
                // without limit, and an old failure that is dropped only costs one more attempt.
                if (failedAt.size >= maxEntries * 4) failedAt.clear()
                failedAt[key] = clock()
            }
        }
        return png
    }

    @Synchronized
    fun clear() {
        rendered.clear()
        failedAt.clear()
    }
}
