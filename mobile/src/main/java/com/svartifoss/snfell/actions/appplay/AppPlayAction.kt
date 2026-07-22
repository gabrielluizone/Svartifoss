package com.svartifoss.snfell.actions.appplay

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.PersistableBundle
import android.view.InputDevice
import android.view.KeyEvent
import androidx.appcompat.content.res.AppCompatResources
import com.svartifoss.snfell.R
import com.svartifoss.snfell.actions.ActionHandler
import com.svartifoss.snfell.actions.PhoneAction
import com.svartifoss.snfell.actions.SelectableAction
import com.svartifoss.snfell.music.MusicService
import com.svartifoss.snfell.music.isPlaying
import kotlinx.coroutines.delay
import timber.log.Timber
import javax.inject.Inject

class AppPlayAction : SelectableAction {
    companion object {
        const val KEY_PACKAGE_NAME = "PACKAGE_NAME"
        const val KEY_CLASS_NAME = "COMPONENT_NAME"
    }

    private lateinit var receiverComponent: ComponentName

    constructor(context: Context, receiverComponent: ComponentName) : super(context) {
        this.receiverComponent = receiverComponent
    }

    constructor(context: Context, bundle: PersistableBundle) : super(context, bundle) {
        this.receiverComponent = ComponentName(
                bundle.getString(KEY_PACKAGE_NAME)!!,
                bundle.getString(KEY_CLASS_NAME)!!
        )
    }

    private val lazyName by lazy {
        val appName: String = try {
            val appInfo = context.packageManager.getApplicationInfo(receiverComponent.packageName, 0)
            context.packageManager.getApplicationLabel(appInfo).toString()
        } catch (ignored: PackageManager.NameNotFoundException) {
            AppCompatResources.getDrawable(context, android.R.drawable.sym_def_app_icon)
            context.getString(R.string.uninstalled_app)
        }

        context.getString(R.string.play_app, appName)
    }

    override fun writeToBundle(bundle: PersistableBundle) {
        super.writeToBundle(bundle)

        bundle.putString(KEY_PACKAGE_NAME, receiverComponent.packageName)
        bundle.putString(KEY_CLASS_NAME, receiverComponent.className)
    }

    override fun retrieveTitle(): String = lazyName
    override val defaultIconTintable: Boolean
        get() = false
    override val defaultIcon: Drawable
        get() = try {
            context.packageManager.getApplicationIcon(receiverComponent.packageName)
        } catch (ignored: PackageManager.NameNotFoundException) {
            AppCompatResources.getDrawable(context, android.R.drawable.sym_def_app_icon)!!
        }

    override fun isEqualToAction(other: PhoneAction): Boolean {
        other as AppPlayAction
        return super.isEqualToAction(other) && this.receiverComponent == other.receiverComponent
    }

    class Handler @Inject constructor(
            private val service: MusicService
    ) : ActionHandler<AppPlayAction> {
        override suspend fun handleAction(action: AppPlayAction) {
            val receiverComponent = action.receiverComponent

            // Some apps (Spotify for example) seems to obey commands
            // sent from AVRCP device more than others. If there is AVRCP bluetooth device
            // available, use it
            var pickedDeviceID = -1
            for (deviceId in InputDevice.getDeviceIds()) {
                val device = InputDevice.getDevice(deviceId)

                if (device?.name == "AVRCP") {
                    pickedDeviceID = deviceId
                }
            }

            val androidContext = service

            dispatchUpDownEvents(pickedDeviceID, receiverComponent)
            delay(2000)

            if (service.currentMediaController?.packageName == receiverComponent.packageName &&
                    service.currentMediaController?.isPlaying() == true) {
                return
            }

            // Media is still not playing.
            // Some players do not handle background starts very well. Start their UI activity first.
            val launcherActivity = androidContext.packageManager.getLaunchIntentForPackage(receiverComponent.packageName)
            if (launcherActivity != null) {
                // Starting another app's UI from this background service is subject to
                // background activity-launch limits (Android 10+, tightened on Android 14) and
                // usually just no-ops; some OEMs throw instead. Guard so a refusal doesn't crash
                // the action - the MediaBrowser/key-event path below is the reliable fallback.
                try {
                    androidContext.startActivity(launcherActivity)
                } catch (e: Exception) {
                    Timber.w(e, "Could not launch ${receiverComponent.packageName} UI from background")
                }
                delay(500)

                if (service.currentMediaController?.packageName == receiverComponent.packageName &&
                        service.currentMediaController?.isPlaying() == true) {
                    // In the time it took for activity to launch, media has already started playing. Nothing to do.
                    return
                }

                dispatchUpDownEvents(pickedDeviceID, receiverComponent)
                delay(500)
            }

            if (service.currentMediaController?.packageName == receiverComponent.packageName &&
                    service.currentMediaController?.isPlaying() == true) {
                return
            }

            // Media is still not playing.
            // Maybe UI activity took some time to restart?
            // Retry sending play command one last time after 3 second delay
            delay(3000)
            dispatchUpDownEvents(pickedDeviceID, receiverComponent)
        }

        private fun dispatchUpDownEvents(
                pickedDeviceID: Int,
                receiverComponent: ComponentName,
                eventTime: Long = System.currentTimeMillis()
        ) {
            dispatchKeyEvent(
                    KeyEvent(0, eventTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY, 0, 0, pickedDeviceID, 0),
                    receiverComponent
            )
            dispatchKeyEvent(
                    KeyEvent(0, eventTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY, 0, 0, pickedDeviceID, 0),
                    receiverComponent
            )
        }

        private fun dispatchKeyEvent(keyEvent: KeyEvent, receiverComponent: ComponentName) {
            val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
            intent.putExtra(Intent.EXTRA_KEY_EVENT, keyEvent)
            intent.component = receiverComponent

            service.sendBroadcast(intent)
        }
    }
}
