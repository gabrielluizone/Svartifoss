package com.svartifoss.snfell.actions.volume

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.PersistableBundle
import androidx.appcompat.content.res.AppCompatResources
import com.svartifoss.snfell.R
import com.svartifoss.snfell.actions.ActionHandler
import com.svartifoss.snfell.actions.SelectableAction
import com.svartifoss.snfell.music.MusicService
import com.svartifoss.snfell.view.output.AudioOutputSwitcherActivity
import javax.inject.Inject
import timber.log.Timber

/**
 * "Audio output on phone": opens the phone's own list of where it plays - speaker, headphones, a
 * cast device - so the output can be changed without taking the phone out.
 *
 * The watch carries it out itself (see `StandardActions.ACTION_OPEN_PHONE_AUDIO_OUTPUT`): it opens
 * [AudioOutputSwitcherActivity] on the phone through the companion app, the one route a screen
 * has onto a phone whose app is in the background.
 *
 * This handler only runs for a watch build that predates that, and can only *try*: an activity
 * started from a background service is refused on Android 10 and later unless the app happens to
 * be on screen. A refusal is logged and nothing else happens - the press then does nothing, which
 * is what it did on that watch before the action existed.
 */
class OpenPhoneAudioOutputAction : SelectableAction {
    constructor(context: Context) : super(context)
    constructor(context: Context, bundle: PersistableBundle) : super(context, bundle)

    override fun retrieveTitle(): String = context.getString(R.string.action_open_phone_audio_output)
    override val defaultIcon: Drawable
        get() = AppCompatResources.getDrawable(
                context, com.svartifoss.snfell.common.R.drawable.action_audio_output)!!

    class Handler @Inject constructor(private val service: MusicService) :
            ActionHandler<OpenPhoneAudioOutputAction> {
        override suspend fun handleAction(action: OpenPhoneAudioOutputAction) {
            try {
                service.startActivity(Intent(service, AudioOutputSwitcherActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: RuntimeException) {
                Timber.w(e, "Could not open the audio output switcher from the service")
            }
        }
    }
}
