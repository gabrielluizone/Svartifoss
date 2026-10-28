package com.svartifoss.snfell.view.handoff

import android.app.Activity
import android.os.Bundle
import android.os.SystemClock
import com.svartifoss.snfell.music.VisibleLaunchIntents
import com.svartifoss.snfell.music.VisiblePlaybackRegistry
import timber.log.Timber

/**
 * Starts a streaming shortcut on the phone the way that makes it *play*, when the watch has to open
 * it visibly.
 *
 * An invisible, translucent activity that lives for one frame, the same shape as
 * `AudioOutputSwitcherActivity`: the watch opens it with `RemoteActivityHelper` (the only kind of
 * launch a watch can make on the phone, a browsable view link), and from inside it this app is in
 * the foreground, which is what starting another app's activity requires. What it starts was
 * planned by `MusicService` and is looked up by the link's one-time token in
 * [VisiblePlaybackRegistry]; the link itself names nothing, so a page that opens this link reaches
 * nothing (see the registry for why that matters).
 *
 * It tries the planned launches in order - the technique that plays, then the plain open the
 * shortcut always had - and reports which one started, so the service can check whether music
 * actually began and remember the answer.
 */
class StreamingPlaybackActivity : Activity() {

    private var handled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A recreated instance must not start the shortcut a second time.
        handled = savedInstanceState?.getBoolean(STATE_HANDLED) == true
    }

    override fun onResume() {
        super.onResume()
        if (!handled) {
            handled = true
            handOff()
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_HANDLED, handled)
    }

    private fun handOff() {
        // Only the token is read from the link, never an action, a package or an extra.
        val link = intent?.data
        val token = link?.takeIf {
            it.scheme == VisiblePlaybackRegistry.URI_SCHEME && it.host == VisiblePlaybackRegistry.URI_HOST
        }?.getQueryParameter(VisiblePlaybackRegistry.TOKEN_PARAMETER)
        val claim = token?.let { VisiblePlaybackRegistry.claim(it, SystemClock.elapsedRealtime()) }
        if (claim == null) {
            // Already used, superseded by a newer pick, expired, or not from this app at all.
            Timber.i("Streaming hand-off opened with no live request")
            return
        }
        val started = claim.launches.firstOrNull { VisibleLaunchIntents.start(this, it) }
        Timber.i("Streaming hand-off started %s (autoplay=%s)",
                started?.technique ?: started?.action, claim.autoplay)
        VisiblePlaybackRegistry.reportLaunched(claim, started)
    }

    private companion object {
        const val STATE_HANDLED = "handled"
    }
}
