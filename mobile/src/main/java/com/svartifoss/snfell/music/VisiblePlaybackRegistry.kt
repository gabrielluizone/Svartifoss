package com.svartifoss.snfell.music

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import java.util.UUID

/**
 * Hands a planned launch from `MusicService` to the `StreamingPlaybackActivity` the watch opens.
 *
 * The watch can only ever open a browsable view link on the phone (`RemoteActivityHelper`), and a
 * background service cannot start an activity at all, so the launches that make an app *play* - an
 * intent action other than VIEW, or one an app accepts only from another app - have to be started
 * by something in the foreground. That is the activity, and this is how it learns what to start:
 * the link the watch opens carries nothing but an unguessable, single-use token, and the launch
 * itself never leaves this process.
 *
 * That shape is a security property, not a convenience. The activity is exported and browsable, so
 * any web page can open `svartifoss://play-shortcut`; if the link carried the action and its target
 * the activity would launch whatever a page asked for, under this app's identity (an "intent
 * redirection" hole). A token a page cannot know reaches nothing.
 *
 * Time matters too. A launch that starts playback is only made while the request is fresh
 * ([AUTOPLAY_WINDOW_MS]): a phone locked in a pocket shows the activity only when it is unlocked,
 * and music starting minutes after a tap on the wrist would be a surprise, not a feature. A stale
 * request still opens the content ([NAVIGATION_WINDOW_MS]) - exactly what a shortcut always did.
 *
 * Process-scoped and in memory only. Times are `SystemClock.elapsedRealtime()`, passed in so the
 * rules are testable (`VisiblePlaybackRegistryTest`).
 */
object VisiblePlaybackRegistry {
    const val URI_SCHEME = "svartifoss"
    const val URI_HOST = "play-shortcut"
    const val TOKEN_PARAMETER = "t"

    /** A request this old may still start playback when the activity finally runs. */
    const val AUTOPLAY_WINDOW_MS = 60_000L

    /** After the autoplay window, and until this, the activity only opens the content. */
    const val NAVIGATION_WINDOW_MS = 10L * 60 * 1000

    /** Requests kept at once: a burst of picks on the wrist leaves only the newest few claimable. */
    private const val MAX_PENDING = 4

    /**
     * What the activity ended up starting: [launch] null if nothing started, [autoplay] true only
     * when the playing technique itself was started on a fresh request. [fresh] says whether the
     * request was still inside the autoplay window, which together with [autoplay] tells "the
     * technique could not even be started" (fresh, not autoplay) apart from "it was too late to
     * try" (not fresh).
     */
    data class Outcome(val launch: VisibleLaunch?, val autoplay: Boolean, val fresh: Boolean)

    /** One registered request, as held by the code waiting on it. */
    class Handle internal constructor(
            val token: String,
            internal val play: VisibleLaunch,
            internal val navigation: VisibleLaunch?,
            internal val createdAtMs: Long
    ) {
        internal val outcome = CompletableDeferred<Outcome>()
        val uri: String get() = uriFor(token)
    }

    /** What the activity should try, in order; [autoplay] false means the request was stale. */
    class Claim internal constructor(
            val launches: List<VisibleLaunch>,
            val autoplay: Boolean,
            internal val handle: Handle
    )

    private val pending = LinkedHashMap<String, Handle>()

    @Synchronized
    fun register(
            play: VisibleLaunch,
            navigation: VisibleLaunch?,
            nowMs: Long,
            token: String = UUID.randomUUID().toString()
    ): Handle {
        val handle = Handle(token, play, navigation, nowMs)
        pending[token] = handle
        while (pending.size > MAX_PENDING) {
            val oldest = pending.keys.first()
            pending.remove(oldest)?.outcome?.cancel()
        }
        return handle
    }

    fun uriFor(token: String): String = "$URI_SCHEME://$URI_HOST?$TOKEN_PARAMETER=$token"

    /** Takes the request out (a token works once), or null when it is unknown or has expired. */
    @Synchronized
    fun claim(token: String, nowMs: Long): Claim? {
        val handle = pending.remove(token) ?: return null
        val age = (nowMs - handle.createdAtMs).coerceAtLeast(0L)
        return when {
            age <= AUTOPLAY_WINDOW_MS ->
                Claim(listOfNotNull(handle.play, handle.navigation), autoplay = true, handle = handle)
            age <= NAVIGATION_WINDOW_MS && handle.navigation != null ->
                Claim(listOf(handle.navigation), autoplay = false, handle = handle)
            else -> {
                handle.outcome.cancel()
                null
            }
        }
    }

    /** Called by the activity once it has tried [Claim.launches]; [started] null if none started. */
    fun reportLaunched(claim: Claim, started: VisibleLaunch?) {
        claim.handle.outcome.complete(Outcome(started,
                autoplay = claim.autoplay && started == claim.handle.play, fresh = claim.autoplay))
    }

    /**
     * Waits for the activity to run [handle]; null if the request was superseded or expired.
     * The caller's own cancellation is never swallowed.
     */
    suspend fun awaitOutcome(handle: Handle): Outcome? = try {
        handle.outcome.await()
    } catch (e: CancellationException) {
        if (handle.outcome.isCancelled && currentCoroutineContext().isActive) null else throw e
    }

    /** A request that was superseded can no longer be claimed. */
    @Synchronized
    fun cancel(handle: Handle) {
        pending.remove(handle.token)
        handle.outcome.cancel()
    }

    /** For tests: whether [token] could still be claimed. */
    @Synchronized
    internal fun isPending(token: String): Boolean = token in pending
}
