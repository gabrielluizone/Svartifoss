package com.svartifoss.snfell.watch.communication

import com.svartifoss.snfell.common.CommPaths
import com.matejdro.wearutils.logging.WearLogRequestReceiver

class MusicCenterLogRequestReceiver : WearLogRequestReceiver() {
    override fun getLogsChannelPath(): String = CommPaths.CHANNEL_LOGS
}
