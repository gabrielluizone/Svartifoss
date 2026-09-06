package com.svartifoss.snfell.watch.communication

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.watch.config.PreferencesBus
import com.svartifoss.snfell.watch.theme.UserFont
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/**
 * Receives the typeface the user imported on their phone ([CommPaths.DATA_USER_FONT]).
 *
 * Manifest-registered for the same reason [ConfigListenerService] is: a font imported while the
 * watch app is closed - which is the ordinary case, since the import happens on the phone - has no
 * other delivery. `PhoneConnection`'s runtime listener is alive only while a screen is open, so
 * without this the font would arrive the *second* time the player was opened and the first would
 * render in the previous family.
 *
 * The asset is read off the main thread. `getFdForAsset` opens a channel to the phone and blocks
 * until the bytes are there; a font is up to two megabytes over Bluetooth, so doing that on the
 * callback thread would hold a Play Services binder thread for seconds at a time.
 */
class UserFontListenerService : WearableListenerService() {

    /**
     * Process-scoped rather than tied to this service.
     *
     * `WearableListenerService` is torn down as soon as `onDataChanged` returns, and the read is
     * launched from inside it - a scope owned by the service would be cancelled at exactly the
     * moment the transfer got going. The same reasoning `MusicService.SHUTDOWN_SCOPE` records for
     * its shutdown notification.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        super.onDataChanged(dataEvents)

        val item = dataEvents
                .filter { it.type == DataEvent.TYPE_CHANGED }
                .lastOrNull { it.dataItem.uri.path == CommPaths.DATA_USER_FONT }
                ?.dataItem
                ?.freeze()
                ?: return

        val fingerprint = item.data?.toString(Charsets.UTF_8).orEmpty()
        val asset = item.assets[CommPaths.ASSET_USER_FONT]
        if (asset == null) {
            // No asset on the item means the user cleared their font, which is a real instruction
            // and not an empty delivery - a watch already rendering with it has to stop.
            if (UserFont.clear(this)) {
                Timber.d("The phone cleared its imported font")
                republish()
            }
            return
        }
        if (UserFont.holds(fingerprint)) return

        val context = applicationContext
        scope.launch {
            val bytes = try {
                Wearable.getDataClient(context).getFdForAsset(asset).await().inputStream
                        .use { it.readBytes() }
            } catch (e: Exception) {
                Timber.w(e, "Could not read the imported font from the phone")
                return@launch
            }
            if (UserFont.store(context, bytes, fingerprint)) republish()
        }
    }

    /**
     * Nudges anything already on screen to re-resolve its fonts.
     *
     * The face reads its appearance when the preferences bus emits and deliberately does not reload
     * in `onStart`, so a font that lands while the player is open would otherwise sit on disk
     * unused until something else changed. Re-posting the same preferences is the existing signal
     * for "your appearance inputs moved", which the on-watch face picker already uses for its own
     * local write.
     */
    private fun republish() {
        PreferencesBus.value?.let { PreferencesBus.postValue(it) }
    }
}
