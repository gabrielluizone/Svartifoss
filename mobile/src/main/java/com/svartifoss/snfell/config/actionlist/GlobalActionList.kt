package com.svartifoss.snfell.config.actionlist

import android.content.Context
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.svartifoss.snfell.actions.PhoneAction
import com.svartifoss.snfell.config.ConfigSyncQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import timber.log.Timber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GlobalActionList @Inject constructor(actionListTransmitterFactory: ActionListTransmitterFactory,
                                           private val diskStorage: DiskActionListStorage,
                                           private val context: Context) : ActionList {
    override var actions: List<PhoneAction> = emptyList()


    private val transmitter: ActionListTransmitter

    init {
        diskStorage.loadActions(this)

        transmitter = actionListTransmitterFactory.create(this)
    }

    private val syncQueue = ConfigSyncQueue(
            CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            onFailure = {
                if (it is GooglePlayServicesRepairableException) {
                    GoogleApiAvailability.getInstance().showErrorNotification(context, it.connectionStatusCode)
                }
                Timber.w(it, "Could not synchronize action list")
            }) { saveToDisk ->
        val snapshot = actions.toList()
        withContext(Dispatchers.Default) {
            if (saveToDisk) diskStorage.saveActions(snapshot)
            transmitter.sendConfigToWatch(snapshot)
        }
    }

    override fun retransmit() = syncQueue.request(saveToDisk = false)

    override fun commit() = syncQueue.request(saveToDisk = true)
}
