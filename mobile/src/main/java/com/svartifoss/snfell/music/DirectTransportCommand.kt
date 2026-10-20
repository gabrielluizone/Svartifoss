package com.svartifoss.snfell.music

import android.media.AudioManager
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.common.util.FloatPacker
import java.nio.ByteBuffer

/**
 * A watch command that can be carried out on a media session without [MusicService].
 *
 * The service is the only thing that can run most commands, and it cannot be started from a locked
 * phone on Android 12+, which is why a press made then used to be dropped. These few need nothing
 * the service holds: a session to aim at and a transport call on it. Anything else (the queue,
 * search, shortcuts, seeking) still needs the service and keeps going through it.
 */
internal sealed interface DirectTransportCommand {
    /** `MESSAGE_TOGGLE_PLAY_PAUSE`: pause if the session is playing, play otherwise. */
    data object TogglePlayPause : DirectTransportCommand

    /**
     * A media key sent to the session, which is how a button mapped to Play, Pause or Play/pause
     * has always reached the player. [fallbackToSystem] hands the key to the audio framework when
     * no session exists, so the most recent media app resumes - the same thing the phone's own
     * play button does. Pause has no such fallback: there is nothing to pause.
     */
    data class MediaKey(val keyCode: Int, val fallbackToSystem: Boolean) : DirectTransportCommand

    data object SkipNext : DirectTransportCommand
    data object SkipPrevious : DirectTransportCommand

    /** One native volume step, [AudioManager.ADJUST_LOWER] or [AudioManager.ADJUST_RAISE]. */
    data class AdjustVolume(val direction: Int) : DirectTransportCommand

    /** An absolute level as a fraction of the session's maximum, 0..1. */
    data class SetVolume(val fraction: Float) : DirectTransportCommand
}

internal object DirectTransportCommands {
    /**
     * The command a watch message asks for, or null when it is not one of the direct ones or its
     * payload is unusable. Null always means "leave it to the service", never "drop it" - a
     * payload this refuses is exactly what the service would have refused too.
     */
    fun fromMessage(path: String, data: ByteArray?): DirectTransportCommand? = when (path) {
        CommPaths.MESSAGE_TOGGLE_PLAY_PAUSE -> DirectTransportCommand.TogglePlayPause
        CommPaths.MESSAGE_SKIP_NEXT -> DirectTransportCommand.SkipNext
        CommPaths.MESSAGE_SKIP_PREVIOUS -> DirectTransportCommand.SkipPrevious
        CommPaths.MESSAGE_ADJUST_VOLUME -> data
                ?.takeIf { it.size >= Int.SIZE_BYTES }
                ?.let { ByteBuffer.wrap(it).int }
                ?.takeIf { it == AudioManager.ADJUST_LOWER || it == AudioManager.ADJUST_RAISE }
                ?.let(DirectTransportCommand::AdjustVolume)
        CommPaths.MESSAGE_CHANGE_VOLUME -> data
                ?.takeIf { it.size >= Float.SIZE_BYTES }
                ?.let(FloatPacker::unpackFloat)
                ?.takeUnless { it.isNaN() }
                ?.let { DirectTransportCommand.SetVolume(it.coerceIn(0f, 1f)) }
        else -> null
    }
}

/** Which session a command is aimed at when [MusicService] is not there to have tracked one. */
internal object DirectTransportTarget {
    /**
     * The playing session, else the first one.
     *
     * [sessions] arrives in the system's own priority order, most recently active first, which is
     * the order media keys are routed in. So with nothing playing this lands on the session the
     * user last had going - what the service's retained paused session amounts to, without needing
     * to have been watching it.
     */
    fun <T> pick(sessions: List<T>, isPlaying: (T) -> Boolean): T? =
            sessions.firstOrNull(isPlaying) ?: sessions.firstOrNull()
}

/**
 * Spaces out attempts to bring the service up after a command has already been carried out.
 *
 * The attempt is only so the watch hears what happened, and a refused one will not succeed a
 * moment later; a crown turned through forty volume steps would otherwise throw and log forty
 * exceptions a second for one answer.
 */
internal class ServiceWakeThrottle(
        private val minIntervalMs: Long = DEFAULT_INTERVAL_MS,
        private val clock: () -> Long
) {
    private var lastAttemptMs = Long.MIN_VALUE

    /** True when an attempt may be made now, which also counts it as made. */
    fun tryAcquire(): Boolean {
        val now = clock()
        if (lastAttemptMs != Long.MIN_VALUE && now - lastAttemptMs < minIntervalMs) return false
        lastAttemptMs = now
        return true
    }

    companion object {
        const val DEFAULT_INTERVAL_MS = 3_000L
    }
}
