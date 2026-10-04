package com.svartifoss.snfell.watch.communication

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.svartifoss.snfell.common.ShortcutRequestTracker
import com.svartifoss.snfell.proto.StreamingShortcutVerdict
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale
import java.util.UUID

/** Wait for the phone's playback attempt, then use the Wear bridge if it needs a visible open. */
object PhoneUriOpener {
    private const val VERDICT_BACKSTOP_MS = 30_000L
    private val UNSAFE_SCHEMES = setOf("content", "data", "file", "intent", "javascript")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val requests = ShortcutRequestTracker()
    private var backstop: Job? = null

    /** Called on main before sending the command. Every press, even of the same URI, gets an id. */
    fun requestOpenAfterPhoneTries(context: Context, rawUri: String, openOnly: Boolean = false): String {
        val appContext = context.applicationContext
        val request = requests.begin(UUID.randomUUID().toString(), rawUri, allowLegacy = !openOnly)
        backstop?.cancel()
        backstop = scope.launch {
            // An older phone cannot resolve an open-only request. Keep the cached route as a
            // bounded fallback without accidentally asking that older phone to start playback.
            delay(if (openOnly) 5000L else VERDICT_BACKSTOP_MS)
            if (requests.take(request.id) != null) {
                Timber.w("No shortcut verdict from phone; using cached destination")
                openNow(appContext, rawUri)
            }
        }
        return request.id
    }

    /** Binder callbacks are marshalled to main with the timer and UI request registration. */
    fun onVerdict(context: Context, payload: ByteArray?, correlated: Boolean = false) {
        val appContext = context.applicationContext
        scope.launch {
            val verdict = if (correlated) {
                try {
                    StreamingShortcutVerdict.parseFrom(payload ?: return@launch)
                } catch (e: com.google.protobuf.InvalidProtocolBufferException) {
                    Timber.w(e, "Invalid streaming shortcut verdict")
                    return@launch
                }
            } else null
            if (requests.take(verdict?.requestId) == null) {
                Timber.d("Ignoring obsolete streaming verdict %s", verdict?.requestId)
                return@launch
            }
            Timber.d("Accepted streaming verdict %s", verdict?.requestId)
            backstop?.cancel()
            backstop = null
            val uri = if (verdict != null) verdict.openUri
                else payload?.toString(Charsets.UTF_8)
            if (!uri.isNullOrBlank()) openNow(appContext, uri)
        }
    }

    /** Package targeting can fail after an uninstall; retry through Android's web-link handler. */
    suspend fun openNow(context: Context, rawUri: String) {
        val parts = rawUri.split('|', limit = 2)
        val targetPackage = if (parts.size == 2) parts[0] else null
        val uriString = if (parts.size == 2) parts[1] else rawUri
        val uri = runCatching { Uri.parse(uriString.trim()) }.getOrNull() ?: return
        val scheme = uri.scheme?.lowercase(Locale.US).orEmpty()
        if (scheme.isBlank() || scheme in UNSAFE_SCHEMES) return
        if ((scheme == "http" || scheme == "https") && uri.host.isNullOrBlank()) return

        val helper = RemoteActivityHelper(context.applicationContext, ContextCompat.getMainExecutor(context))
        suspend fun open(link: Uri, target: String?): Boolean = try {
            helper.startRemoteActivity(Intent(Intent.ACTION_VIEW, link)
                    .addCategory(Intent.CATEGORY_BROWSABLE).setPackage(target)).await()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Could not open streaming shortcut on phone")
            false
        }
        if (open(uri, targetPackage)) return
        if (!targetPackage.isNullOrBlank()) {
            val fallback = if (scheme == "spotify") {
                Uri.parse("https://open.spotify.com/" + uri.schemeSpecificPart.replace(':', '/'))
            } else uri
            open(fallback, null)
        }
    }
}
