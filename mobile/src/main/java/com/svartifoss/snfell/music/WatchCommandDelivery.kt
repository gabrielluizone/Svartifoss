package com.svartifoss.snfell.music

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import androidx.core.content.ContextCompat
import com.google.android.gms.wearable.MessageEvent
import com.svartifoss.snfell.common.CommPaths
import timber.log.Timber

/** Process-owned so listener races and service recreation share the same delivery history. */
internal object WatchCommandDelivery {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val inbox = WatchCommandInbox()

    fun receive(context: Context, event: MessageEvent) {
        val command = WatchCommand(event.sourceNodeId, event.requestId, event.path,
                event.data?.copyOf() ?: ByteArray(0))
        val appContext = context.applicationContext
        if (Looper.myLooper() == Looper.getMainLooper()) {
            deliver(appContext, command)
        } else {
            mainHandler.post { deliver(appContext, command) }
        }
    }

    @MainThread
    fun beginStartup(receiver: (WatchCommand) -> Unit) = inbox.beginStartup(receiver)

    @MainThread
    fun attach(receiver: (WatchCommand) -> Unit) = inbox.attach(receiver)

    @MainThread
    fun detach(receiver: (WatchCommand) -> Unit) = inbox.detach(receiver)

    private fun deliver(context: Context, command: WatchCommand) {
        val canStartService = command.path != CommPaths.MESSAGE_WATCH_CLOSED &&
                command.path != CommPaths.MESSAGE_WATCH_CLOSED_MANUALLY &&
                command.path != CommPaths.MESSAGE_ACK
        inbox.receive(command, canStartService) {
            try {
                ContextCompat.startForegroundService(context,
                        Intent(context, MusicService::class.java)
                                .setAction(MusicService.ACTION_START_FROM_WATCH))
                true
            } catch (e: IllegalStateException) {
                // Android may reject an FGS start while fully backgrounded. MessageClient does
                // not replay the command, and replaying a discrete action ourselves is unsafe.
                Timber.w(e, "Could not start MusicService for %s", command.path)
                false
            }
        }
    }
}
