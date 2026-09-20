package com.svartifoss.snfell.watch.communication

import com.google.android.gms.wearable.MessageEvent
import com.svartifoss.snfell.common.CommPaths
import com.matejdro.wearutils.logging.WearLogRequestReceiver
import timber.log.Timber

class MusicCenterLogRequestReceiver : WearLogRequestReceiver() {
    override fun getLogsChannelPath(): String = CommPaths.CHANNEL_LOGS

    /**
     * The request arrives while nothing of this app is on screen - that is the whole point of
     * collecting logs remotely - and the transmitter it starts is a foreground service, which
     * Android 12+ refuses to start from a background process. Receiving a Data Layer message
     * carries no exemption, so the refusal is an ordinary outcome here, not a defect. Left
     * uncaught it crashed the process the phone was trying to collect logs from.
     */
    override fun onMessageReceived(messageEvent: MessageEvent) {
        try {
            super.onMessageReceived(messageEvent)
        } catch (e: IllegalStateException) {
            Timber.w(e, "Log transmission refused: the watch app may not start a service now")
        }
    }
}
