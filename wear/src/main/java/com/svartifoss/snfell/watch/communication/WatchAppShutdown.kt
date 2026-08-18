package com.svartifoss.snfell.watch.communication

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import timber.log.Timber
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Shuts the watch app down because the phone said so (see `CommPaths.MESSAGE_STOP_WATCH_APP`).
 *
 * The two halves of the app are separate processes on separate devices, so "Stop" on the phone's
 * notification never reached the watch: the phone's service went away and the watch kept its
 * ongoing-activity chip and a proxy media session pointed at nothing. This is the receiving end.
 *
 * **A plain listener list rather than a `LiveData`,** which is the obvious shape and the wrong one.
 * A singleton `LiveData` replays its last value to every new observer, so one shutdown would close
 * the app again the next time it was opened - and `SingleLiveEvent` only trades that for the
 * opposite failure, holding the event until an observer is STARTED and closing the app in the
 * user's face when they next bring it up. A shutdown is only meaningful at the moment it arrives:
 * whoever is listening then should act, and an app with nothing open needs no listener at all,
 * which is the state a phone-initiated stop usually finds.
 */
object WatchAppShutdown {

    /**
     * How long the process is given to close its screens before a forced shutdown kills it.
     *
     * Long enough for the posted `finishAffinity()` to run so the app is not seen vanishing
     * mid-frame, short enough that it still reads as "force". Mirrors the phone's own
     * `FORCE_STOP_KILL_DELAY_MS`.
     */
    private const val KILL_DELAY_MS = 400L

    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    /**
     * Stops the service and asks any open screen to close.
     *
     * The service stop is unconditional and comes first: it owns the ongoing-activity chip and the
     * `WatchMediaSession` proxy, so it is the part still visible on the watch face after the UI is
     * gone. Stopping a service that is not running is a no-op, so this is safe on an idle watch.
     *
     * @param force kill the process afterwards, mirroring the phone's "Force stop". Without it this
     *   is the ordinary "Stop": the app is torn down but the process is left to be reclaimed
     *   normally, so the system can start it cleanly again the next time playback does.
     */
    fun shutdown(context: Context, force: Boolean) {
        Timber.i("Shutting the watch app down at the phone's request (force=%b)", force)

        try {
            context.stopService(Intent(context, WatchMusicService::class.java))
        } catch (e: RuntimeException) {
            // Never worth crashing over: the kill below (or the process simply being idle) reaches
            // the same end state, and this listener must not die before it gets there.
            Timber.w(e, "Could not stop WatchMusicService")
        }

        val main = Handler(Looper.getMainLooper())
        main.post { listeners.forEach { it() } }

        if (force) {
            main.postDelayed({
                Timber.i("Force stop: killing the watch process")
                android.os.Process.killProcess(android.os.Process.myPid())
            }, KILL_DELAY_MS)
        }
    }

    /**
     * Wires [activity] to close itself and everything above it while it is alive.
     *
     * Only the player calls this. Registering every screen would be redundant - they are all in the
     * one task `finishAffinity` empties - and would also mean whichever screen is on top finishing
     * first, the one order that leaves the player briefly visible again on the way out.
     */
    fun closeOn(activity: Activity, owner: LifecycleOwner) {
        val listener = {
            // Not finish(): the player is the bottom of the stack, and a queue or picker layered
            // over it would otherwise survive and become the whole app.
            activity.finishAffinity()
        }
        listeners.add(listener)
        owner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                listeners.remove(listener)
            }
        })
    }
}
