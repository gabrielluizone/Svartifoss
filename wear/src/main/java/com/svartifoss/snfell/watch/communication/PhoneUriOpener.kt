package com.svartifoss.snfell.watch.communication

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.wear.remote.interactions.RemoteActivityHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import timber.log.Timber

/**
 * Opens a streaming shortcut's URI on the paired phone - but only once the phone has said it could
 * not start playback any other way.
 *
 * Why the watch does the opening at all: modern Android bars the phone's playback service from
 * starting an Activity from the background, and [RemoteActivityHelper] is the sanctioned bridge
 * that gets around it. Why it is *deferred*: the watch used to fire that open in the same breath as
 * sending the action, so `MusicService.playDeepLink`'s silent ladder - the direct transport command,
 * then the MediaBrowser route that works with the screen off - ran while the target app was already
 * being brought to the foreground anyway. With the phone locked in a pocket that is precisely the
 * outcome the ladder exists to avoid, and it made the invisible routes look broken even when they
 * worked: something always appeared on the phone.
 *
 * So the order is inverted. [requestOpenAfterPhoneTries] registers the URI and waits; the phone
 * reports back over [CommPaths.MESSAGE_DEEP_LINK_VERDICT] and only a verdict carrying a URI opens
 * anything. This is also the single implementation of the open itself, which the now-playing
 * screen, the Tile trampoline and the idle listener previously each kept a copy of.
 */
object PhoneUriOpener {

    /**
     * Backstop for a verdict that never arrives - a lost message, or the phone dying mid-ladder.
     *
     * Deliberately far longer than the phone's worst-case ladder (the URI verification plus a
     * browser connect and its strategies, ~16s) so it can never race a verdict that is merely
     * slow and open the app on top of playback that was about to start. It is not the mechanism
     * for the normal case; a phone that reaches any terminal point always answers.
     */
    private const val VERDICT_BACKSTOP_MS = 30_000L

    private val UNSAFE_SCHEMES = setOf("content", "data", "file", "intent", "javascript")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * The URI the phone is currently trying to play, or null when nothing is outstanding.
     *
     * Atomic because the two halves run on different threads, which is not obvious from the call
     * sites: [requestOpenAfterPhoneTries] is always reached from the UI thread (the menu, a button,
     * the Tile trampoline), while [onVerdict] arrives on a binder thread - Play Services dispatches
     * `WearableListenerService.onMessageReceived` off the main thread. A plain field gave the binder
     * thread no guarantee of ever observing the UI thread's write, so a verdict could be dropped as
     * "nothing pending" and the backstop would then open the app anyway - reinstating, half a minute
     * late, exactly the foreground open this class exists to prevent.
     *
     * Claiming the request with `getAndSet`/`compareAndSet` rather than reading-then-clearing also
     * makes the verdict and the backstop mutually exclusive, so a verdict landing just as the timer
     * fires cannot open the same link twice.
     */
    private val pending = AtomicReference<String?>(null)

    /** Written from the UI thread, cancelled from either it or the binder thread. */
    @Volatile
    private var backstop: Job? = null

    /**
     * Records that [rawUri] has been handed to the phone, to be opened only if the phone reports
     * back that it could not play it.
     *
     * Replacing an outstanding request is intentional: a second pick supersedes the first, and
     * leaving the old one armed would open a shortcut the user has already moved on from.
     */
    fun requestOpenAfterPhoneTries(context: Context, rawUri: String) {
        val appContext = context.applicationContext
        pending.set(rawUri)
        backstop?.cancel()
        backstop = scope.launch {
            delay(VERDICT_BACKSTOP_MS)
            // Only fires if this exact request is still the outstanding one; a verdict (or a newer
            // pick) will have claimed it first.
            if (pending.compareAndSet(rawUri, null)) {
                Timber.w("No deep-link verdict from the phone; opening %s anyway", rawUri)
                openNow(appContext, rawUri)
            }
        }
    }

    /**
     * Applies the phone's verdict: an empty [payload] means playback started and nothing should be
     * opened, anything else is the URI to open.
     *
     * A verdict with no request outstanding is ignored rather than acted on - `playDeepLink` also
     * runs for shortcuts started from the phone's own UI, and those must never reach out and open
     * something on behalf of a watch that asked for nothing.
     */
    fun onVerdict(context: Context, payload: ByteArray?) {
        if (pending.getAndSet(null) == null) {
            Timber.d("Deep-link verdict with nothing pending; ignoring")
            return
        }
        backstop?.cancel()
        backstop = null

        val uri = payload?.toString(Charsets.UTF_8)?.takeIf { it.isNotBlank() }
        if (uri == null) {
            Timber.d("Phone played the shortcut itself; not opening anything")
            return
        }
        val appContext = context.applicationContext
        scope.launch { openNow(appContext, uri) }
    }

    /**
     * Opens [rawUri] on the phone now, without waiting for anything.
     *
     * [rawUri] is the `targetPackage|uri` form produced by `PlayPlaylistShortcutAction.remoteUri`,
     * or a bare URI. Unsafe schemes are refused here rather than at each call site, since this is
     * a request to launch something on a *different* device from data that travelled over the
     * Data Layer.
     */
    suspend fun openNow(context: Context, rawUri: String) {
        val parts = rawUri.split('|', limit = 2)
        val (targetPackage, uriString) =
                if (parts.size == 2) parts[0] to parts[1] else null to rawUri
        val uri = runCatching { Uri.parse(uriString.trim()) }.getOrNull() ?: return
        val scheme = uri.scheme?.lowercase(Locale.US).orEmpty()
        if (scheme.isBlank() || scheme in UNSAFE_SCHEMES) {
            Timber.e("Refusing unsafe streaming shortcut URI scheme: %s", scheme)
            return
        }
        if ((scheme == "http" || scheme == "https") && uri.host.isNullOrBlank()) {
            Timber.e("Refusing malformed streaming shortcut web URI")
            return
        }

        val phoneIntent = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
        if (!targetPackage.isNullOrBlank()) {
            phoneIntent.setPackage(targetPackage)
        }
        val helper = RemoteActivityHelper(context, ContextCompat.getMainExecutor(context))
        runCatching {
            helper.startRemoteActivity(phoneIntent).await()
        }.onFailure { error ->
            Timber.e(error, "Could not request streaming shortcut on paired phone")
        }
    }
}
