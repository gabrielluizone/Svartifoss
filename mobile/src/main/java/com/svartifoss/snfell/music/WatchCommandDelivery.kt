package com.svartifoss.snfell.music

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.MainThread
import androidx.core.content.ContextCompat
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.protobuf.InvalidProtocolBufferException
import com.svartifoss.snfell.WearMusicCenter
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.common.buttonconfig.ButtonInfo
import com.svartifoss.snfell.proto.WatchActions
import timber.log.Timber

/** Process-owned so listener races and service recreation share the same delivery history. */
internal object WatchCommandDelivery {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val inbox = WatchCommandInbox()
    private val wakeThrottle = ServiceWakeThrottle(clock = SystemClock::elapsedRealtime)

    fun receive(context: Context, event: MessageEvent) {
        if (event.path == CommPaths.MESSAGE_PINCH_CALIBRATION_RESULT) {
            PinchCalibrationReceiver.receive(context.applicationContext, event)
            return
        }
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
                command.path != CommPaths.MESSAGE_ACK &&
                // A question about state only the running service holds. With no service there is
                // no timer to report, and starting one just to say so would be refused on a locked
                // phone and then shown on the watch as "phone unavailable" - an error about a
                // panel that was merely opened.
                command.path != CommPaths.MESSAGE_REQUEST_SLEEP_TIMER
        inbox.receive(command, canStartService, { runDirect(context, it) }) {
            try {
                ContextCompat.startForegroundService(context,
                        Intent(context, MusicService::class.java)
                                .setAction(MusicService.ACTION_START_FROM_WATCH))
                true
            } catch (e: IllegalStateException) {
                // Android may reject an FGS start while fully backgrounded. MessageClient does
                // not replay the command, and replaying a discrete action ourselves is unsafe.
                Timber.w(e, "Could not start MusicService for %s", command.path)
                reportNotExecuted(context, command)
                false
            }
        }
    }

    /**
     * Carries out a play/pause, skip or volume command without the service, and says whether it did.
     *
     * Reached only when the service is not ready to take the command - while it is, the service
     * runs it, with everything it knows (the seek it must mask, the state it must send back). That
     * is exactly the situation in which Android 12+ refuses to start it: a locked phone whose music
     * was paused long enough ago for the service to have stopped. The command then needs nothing
     * the service holds, only a session to aim at, so it is not worth losing.
     *
     * A command that is not one of these, or that cannot be run because notification access is
     * missing, returns false and takes the ordinary route.
     */
    private fun runDirect(context: Context, command: WatchCommand): Boolean {
        val control = DirectTransportControl(context)
        val direct = (if (command.path == CommPaths.MESSAGE_EXECUTE_ACTION) {
            buttonActionCommand(context, control, command)
        } else {
            DirectTransportCommands.fromMessage(command.path, command.data)
        }) ?: return false

        return when (control.execute(direct)) {
            DirectTransportControl.Outcome.DONE -> {
                Timber.d("Ran %s without the service", direct)
                wakeService(context)
                true
            }
            DirectTransportControl.Outcome.FAILED -> {
                // Whether the player heard it is unknown, and re-running it through the service
                // could do it twice. The watch is told instead, as for any command that was lost.
                reportNotExecuted(context, command)
                true
            }
            DirectTransportControl.Outcome.UNAVAILABLE -> false
        }
    }

    /**
     * What a watch button means to the player, if its assigned action is a transport one.
     *
     * A button arrives as `MESSAGE_EXECUTE_ACTION` carrying only *which* button, and the meaning
     * lives in the phone's own configuration, which is chosen by whether anything is playing - the
     * same rule the service applies with its tracked session.
     */
    private fun buttonActionCommand(
            context: Context,
            control: DirectTransportControl,
            command: WatchCommand
    ): DirectTransportCommand? {
        val config = (context.applicationContext as? WearMusicCenter)?.globalConfig?.get()
                ?: return null
        val button = try {
            ButtonInfo(WatchActions.ProtoButtonInfo.parseFrom(command.data))
        } catch (e: InvalidProtocolBufferException) {
            return null
        }
        val playing = control.isAnythingPlaying() ?: return null
        val buttons = if (playing) config.getPlayingConfig() else config.getStoppedConfig()
        return buttons.getScreenAction(button)?.let { DirectTransportControl.fromAction(it) }
    }

    /**
     * Asks the service to come up after a command has already been carried out without it, so the
     * watch hears what happened. Nothing is queued: the command is done, and giving it to the
     * service as well would run it twice. When Android refuses the start - the very reason this
     * path exists - the press has still worked, so there is nothing to report.
     */
    private fun wakeService(context: Context) {
        if (!wakeThrottle.tryAcquire()) return
        try {
            ContextCompat.startForegroundService(context,
                    Intent(context, MusicService::class.java)
                            .setAction(MusicService.ACTION_START_FROM_WATCH))
        } catch (e: RuntimeException) {
            Timber.d(e, "MusicService stays down after a direct command")
        }
    }

    /**
     * Tells the watch that the command it just sent was dropped.
     *
     * Without this the refusal above is invisible from the other device - the send succeeded, so
     * the watch shows the skip or the pause it optimistically applied and goes on predicting from
     * it, which is the "the watch thinks it is working but the phone does nothing until I unlock
     * it" report. Best-effort by nature: if the reply cannot be delivered either, the watch is
     * left exactly where it was, which is no worse than before.
     */
    private fun reportNotExecuted(context: Context, command: WatchCommand) {
        Wearable.getMessageClient(context)
                .sendMessage(command.sourceNodeId, CommPaths.MESSAGE_COMMAND_NOT_EXECUTED,
                        ByteArray(0))
                .addOnFailureListener { Timber.w(it, "Could not tell the watch about the drop") }
    }
}
