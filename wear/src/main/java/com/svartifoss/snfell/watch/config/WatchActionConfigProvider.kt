package com.svartifoss.snfell.watch.config

import android.content.Context
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
        configMap.clear()

        if (it == null) {
            return@Observer
        }

        val dataItem = it

        scope.launch {
            val newConfigMap =
                    SimpleArrayMap<ButtonInfo, ButtonAction>()

            val actions = WatchActions.parseFrom(it.data)
            volumeStep = actions.volumeStep

            for (action in actions.actionsList) {
                val buttonInfo = ButtonInfo(action)

                val iconKey = CommPaths.ASSET_BUTTON_ICON_PREFIX + buttonInfo.getKey()
                val key = action.actionKey

                val icon = dataClient.getIcon(
                        dataItem,
                        iconKey,
                        key
                )

                newConfigMap.put(buttonInfo, ButtonAction(key, icon))
            }

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
        rawConfigData.removeObserver(rawConfigObserver)
    }
}
