package com.svartifoss.snfell.actions.playback

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.PersistableBundle
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import androidx.appcompat.content.res.AppCompatResources
import com.svartifoss.snfell.R
import com.svartifoss.snfell.actions.ActionHandler
import com.svartifoss.snfell.actions.PhoneAction
import com.svartifoss.snfell.actions.SelectableAction
import com.svartifoss.snfell.music.MusicService
import java.math.BigDecimal
import javax.inject.Inject

internal fun normalizePlaybackSpeed(value: Float): Float =
        if (value.isFinite()) value.coerceIn(MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED)
        else DEFAULT_PLAYBACK_SPEED

internal fun formatPlaybackSpeed(value: Float): String = BigDecimal
        .valueOf(normalizePlaybackSpeed(value).toDouble())
        .stripTrailingZeros()
        .toPlainString()

/** Sets an absolute playback speed, unlike the progress screen's cycling control. */
class SetPlaybackSpeedAction : SelectableAction {
    var speed: Float = DEFAULT_PLAYBACK_SPEED
        private set

    constructor(context: Context, speed: Float) : super(context) {
        this.speed = normalizePlaybackSpeed(speed)
    }

    constructor(context: Context, bundle: PersistableBundle) : super(context, bundle) {
        speed = normalizePlaybackSpeed(
                bundle.getDouble(KEY_SPEED, DEFAULT_PLAYBACK_SPEED.toDouble()).toFloat())
    }

    override fun retrieveTitle(): String = context.getString(
            R.string.action_set_playback_speed, formatPlaybackSpeed(speed))

    override val defaultIcon: Drawable
        get() = AppCompatResources.getDrawable(
                context, com.svartifoss.snfell.common.R.drawable.action_speed)!!

    override fun writeToBundle(bundle: PersistableBundle) {
        super.writeToBundle(bundle)
        bundle.putDouble(KEY_SPEED, speed.toDouble())
    }

    override fun isEqualToAction(other: PhoneAction): Boolean {
        other as SetPlaybackSpeedAction
        return super.isEqualToAction(other) && speed == other.speed
    }

    class Handler @Inject constructor(private val service: MusicService) :
            ActionHandler<SetPlaybackSpeedAction> {
        override suspend fun handleAction(action: SetPlaybackSpeedAction) {
            val controller = service.currentMediaController ?: return
            val compatController = MediaControllerCompat(
                    service,
                    MediaSessionCompat.Token.fromToken(controller.sessionToken))
            // MediaControllerCompat routes this through the support protocol below API 29;
            // calling framework TransportControls.setPlaybackSpeed there would crash.
            compatController.transportControls.setPlaybackSpeed(action.speed)
        }
    }

    private companion object {
        const val KEY_SPEED = "PLAYBACK_SPEED"
    }
}

internal const val MIN_PLAYBACK_SPEED = 0.5f
internal const val MAX_PLAYBACK_SPEED = 2.0f
internal const val DEFAULT_PLAYBACK_SPEED = 1.0f
