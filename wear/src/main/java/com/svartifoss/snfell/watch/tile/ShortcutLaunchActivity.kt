package com.svartifoss.snfell.watch.tile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.google.android.gms.wearable.Wearable
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.common.CustomLists
import com.svartifoss.snfell.proto.CustomListItemAction
import com.matejdro.wearutils.messages.sendMessageToNearestClient
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import java.util.Locale
import timber.log.Timber

/**
 * Invisible bridge activity launched by a [ShortcutsTileService] chip. A Tile click can only launch
 * an Activity (not send Data Layer messages), so this performs the same two steps the now-playing
 * menu does when a shortcut is picked (MusicViewModel.executeItemFromCustomMenu):
 *
 *  1. Open the shortcut's URI on the phone through the Wear phone bridge - the phone-side playback
 *     service is blocked from starting an Activity by modern background-start rules.
 *  2. Ask the phone to actually play it via [CommPaths.MESSAGE_CUSTOM_LIST_ITEM_SELECTED].
 *
 * It shows no UI and finishes as soon as both are dispatched.
 */
class ShortcutLaunchActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val entryId = intent?.getStringExtra(EXTRA_ENTRY_ID)?.takeIf { it.isNotBlank() }
        if (entryId == null) {
            finish()
            return
        }

        lifecycleScope.launch {
            try {
                openUriOnPhone(entryId)
                playOnPhone(entryId)
            } catch (e: Exception) {
                Timber.e(e, "Could not launch streaming shortcut from the Tile")
            } finally {
                finish()
            }
        }
    }

    /** Mirrors MainActivity.openUriOnPhone: reject unsafe schemes, then open through the bridge. */
    private suspend fun openUriOnPhone(rawUri: String) {
        val parts = rawUri.split('|', limit = 2)
        val (targetPackage, uriString) = if (parts.size == 2) parts[0] to parts[1] else null to rawUri
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
        val remoteActivityHelper = RemoteActivityHelper(this, ContextCompat.getMainExecutor(this))
        runCatching {
            remoteActivityHelper.startRemoteActivity(phoneIntent).await()
        }.onFailure { error ->
            Timber.e(error, "Could not request streaming shortcut on paired phone")
        }
    }

    private suspend fun playOnPhone(entryId: String) {
        val payload = CustomListItemAction.newBuilder()
            .setListId(CustomLists.PLAYLIST_SHORTCUTS)
            .setEntryId(entryId)
            .build()
            .toByteArray()
        Wearable.getMessageClient(this).sendMessageToNearestClient(
            Wearable.getNodeClient(this),
            CommPaths.MESSAGE_CUSTOM_LIST_ITEM_SELECTED,
            payload
        )
    }

    companion object {
        const val EXTRA_ENTRY_ID = "com.svartifoss.snfell.watch.tile.EXTRA_ENTRY_ID"
        private val UNSAFE_SCHEMES = setOf("content", "data", "file", "intent", "javascript")
    }
}
