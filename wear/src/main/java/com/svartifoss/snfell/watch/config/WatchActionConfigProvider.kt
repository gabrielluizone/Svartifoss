package com.svartifoss.snfell.watch.config

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.view.KeyEvent
import androidx.collection.SimpleArrayMap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.Wearable
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.common.buttonconfig.ButtonInfo
import com.svartifoss.snfell.common.buttonconfig.GESTURE_LONG_TAP
import com.svartifoss.snfell.proto.WatchActions
import com.svartifoss.snfell.watch.communication.getIcon
import kotlinx.coroutines.*

class WatchActionConfigProvider(context: Context, scope: CoroutineScope, private val rawConfigData: LiveData<DataItem>) {
    private val dataClient = Wearable.getDataClient(context)

    val updateListener = MutableLiveData<WatchActionConfigProvider>()

    private var configMap =
            SimpleArrayMap<ButtonInfo, ButtonAction>()

    var volumeStep = 0.1f
    private var decodeJob: Job? = null

    fun getAction(buttonInfo: ButtonInfo): ButtonAction? {
        return (configMap.get(buttonInfo) ?: configMap.get(buttonInfo.getLegacyButtonInfo()))
    }

    fun isActionActive(buttonInfo: ButtonInfo): Boolean {
        if (buttonInfo.physicalButton &&
                buttonInfo.buttonCode == KeyEvent.KEYCODE_BACK &&
                buttonInfo.gesture == GESTURE_LONG_TAP) {
            return false
        }

        return configMap.containsKey(buttonInfo) || configMap.containsKey(buttonInfo.getLegacyButtonInfo())
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private val rawConfigObserver = Observer<DataItem?> {
        decodeJob?.cancel()

        if (it == null) {
            return@Observer
        }

        val dataItem = it

        decodeJob = scope.launch {
            val newConfigMap =
                    SimpleArrayMap<ButtonInfo, ButtonAction>()

            val actions = WatchActions.parseFrom(it.data)

            for (action in actions.actionsList) {
                val buttonInfo = ButtonInfo(action)

                val iconKey = CommPaths.ASSET_BUTTON_ICON_PREFIX + buttonInfo.getKey()
                val key = action.actionKey

                val icon = dataClient.getIcon(
                        dataItem,
                        iconKey,
                        key
                )

                // Payloads written before iconTintable existed can still be classified reliably:
                // locally-resolved standard vectors have no asset; transferred bitmaps do.
                val usesLocalTemplate = icon !is BitmapDrawable
                val iconTintable = if (usesLocalTemplate) {
                    true
                } else if (action.hasIconTintable()) {
                    action.iconTintable
                } else {
                    false
                }
                val title = if (action.hasActionTitle()) {
                    action.actionTitle.takeIf { it.isNotBlank() }
                } else {
                    null
                }
                val remoteUri = action.remoteUri.takeIf { action.hasRemoteUri() && it.isNotBlank() }
                newConfigMap.put(
                        buttonInfo,
                        ButtonAction(key, icon, title, iconTintable, remoteUri)
                )
            }

            ensureActive()
            // Keep the working assignments available while replacement icons load, then swap
            // the map and its volume policy together.
            volumeStep = actions.volumeStep
            configMap = newConfigMap
            updateListener.value = this@WatchActionConfigProvider
        }
    }

    init {
        rawConfigData.observeForever(rawConfigObserver)
        updateListener.value = this
    }

    /** Stops observing the phone's config LiveData. The owning ViewModel must call this when it
     *  is cleared: [rawConfigData] lives in the @Singleton PhoneConnection, so an un-removed
     *  observeForever would keep this provider (and every decoded icon bitmap in [configMap])
     *  alive for the whole process, one leaked copy per ViewModel recreation. */
    fun destroy() {
        decodeJob?.cancel()
        rawConfigData.removeObserver(rawConfigObserver)
    }
}
