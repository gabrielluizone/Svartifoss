package com.svartifoss.snfell.watch.config

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.Wearable
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.proto.WatchList
import com.svartifoss.snfell.watch.communication.getIcon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class WatchActionMenuProvider(context: Context, coroutineScope: CoroutineScope, private val rawData: LiveData<DataItem>) {
    private val dataClient = Wearable.getDataClient(context)
    val config = MutableLiveData<List<ButtonAction>>()

    private val dataObserver = Observer<DataItem?> { dataItem ->
        if (dataItem == null) {
            return@Observer
        }

        coroutineScope.launch {
            @Suppress("BlockingMethodInNonBlockingContext")
            val listProto = WatchList.parseFrom(dataItem.data)

            val actions = listProto.actionsList.withIndex().map {
                val iconKey = CommPaths.ASSET_BUTTON_ICON_PREFIX + it.index
                val icon = dataClient.getIcon(
                        dataItem,
                        iconKey,
                        it.value.actionKey
                )

                ButtonAction(it.value.actionKey,
                        icon,
                        it.value.actionTitle)
            }.toList()

            config.postValue(actions)
        }
    }

    init {
        rawData.observeForever(dataObserver)
    }

    /** Stops observing the phone's action-menu LiveData. The owning ViewModel must call this when
     *  it is cleared: [rawData] lives in the @Singleton PhoneConnection, so an un-removed
     *  observeForever would keep this provider (and its decoded icon bitmaps) alive for the whole
     *  process, one leaked copy per ViewModel recreation. */
    fun destroy() {
        rawData.removeObserver(dataObserver)
    }
}
