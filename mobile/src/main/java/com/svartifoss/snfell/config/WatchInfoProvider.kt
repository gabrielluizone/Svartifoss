package com.svartifoss.snfell.config

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.lifecycle.LiveData
import com.google.android.gms.wearable.*
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.proto.WatchInfo
import com.svartifoss.snfell.util.launchWithPlayServicesErrorHandling
import com.matejdro.wearutils.messages.getByteArrayAsset
import com.matejdro.wearutils.miscutils.BitmapUtils
import dagger.Reusable
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@Reusable
class WatchInfoProvider @Inject constructor(private val context: Context) :
        // Do not publish a synthetic initial null. A connected watch's cached DataItem arrives
        // moments later; emitting null first made Settings briefly claim no watch was connected.
        LiveData<WatchInfoWithIcons?>(), DataClient.OnDataChangedListener {

    private val dataClient = Wearable.getDataClient(context)

    /** False while the Data Layer's first snapshot is still being queried. Consumers must not
     * treat [value] == null as a disconnected watch until this becomes true. */
    @Volatile
    var hasResolvedInitialValue: Boolean = false
        private set

    private var coroutineScope = CoroutineScope(Job())

    private fun parseDataItem(dataItem: DataItem?) {
        hasResolvedInitialValue = true
        if (dataItem == null) {
            this.postValue(null)
            return
        }

        val watchInfo = WatchInfo.parseFrom(dataItem.data)

        // Presence is already known from the DataItem; icon assets are optional decoration and
        // can take noticeably longer to download. Publish the watch immediately (reusing any
        // cached icons) so Settings never interprets "icons still loading" as "disconnected".
        postValue(WatchInfoWithIcons(watchInfo, value?.icons ?: emptyMap()))

        val frozenDataItem = dataItem.freeze()
        coroutineScope.launchWithPlayServicesErrorHandling(context) {
            val icons = frozenDataItem.assets.keys
                    .filter { it.startsWith(CommPaths.ASSET_WATCH_INFO_BUTTON_PREFIX) }
                    .mapNotNull { assetPrefix ->
                        val buttonCode = assetPrefix.removePrefix(CommPaths.ASSET_WATCH_INFO_BUTTON_PREFIX + "/").toIntOrNull()
                                ?: return@mapNotNull null

                        val buttonAssetName =
                                CommPaths.ASSET_WATCH_INFO_BUTTON_PREFIX + "/" + buttonCode

                        val asset = frozenDataItem.assets[buttonAssetName] ?: return@mapNotNull null

                        val iconBytes = dataClient.getByteArrayAsset(asset)
                        val icon = BitmapUtils.deserialize(iconBytes)

                        buttonCode to BitmapDrawable(this@WatchInfoProvider.context.resources, icon)
                    }
                    .toMap()

            postValue(WatchInfoWithIcons(watchInfo, icons))
        }
    }

    private fun retrieveCurrentValue() {
        coroutineScope.launchWithPlayServicesErrorHandling(context) {
            val items = dataClient.getDataItems(
                    Uri.parse("wear://*" + CommPaths.DATA_WATCH_INFO)
            ).await()

            val latestWatchData = items.maxByOrNull { WatchInfo.parseFrom(it.data).time }
            parseDataItem(latestWatchData)
            items.release()
        }
    }

    private fun registerListener() {
        try {
            dataClient.addListener(
                    this,
                    Uri.parse("wear://*" + CommPaths.DATA_WATCH_INFO),
                    DataClient.FILTER_LITERAL
            )
        } catch (e: Exception) {
            timber.log.Timber.w(e, "Failed to register Wearable data client listener")
        }
    }

    override fun onDataChanged(dataBuffer: DataEventBuffer) {
        dataBuffer
                .filter { it.type == DataEvent.TYPE_CHANGED }
                .map { it.dataItem }
                .firstOrNull()?.also(this::parseDataItem)

        dataBuffer.release()
    }

    override fun onInactive() {
        coroutineScope.cancel()
        try {
            dataClient.removeListener(this)
        } catch (e: Exception) {
            timber.log.Timber.w(e, "Failed to remove Wearable data client listener")
        }
    }

    override fun onActive() {
        coroutineScope = CoroutineScope(Job())

        retrieveCurrentValue()
        registerListener()
    }
}
