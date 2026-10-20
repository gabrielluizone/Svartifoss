package com.svartifoss.snfell.view.output

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.media.MediaRouter2
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.annotation.RequiresApi
import com.svartifoss.snfell.music.DirectTransportControl
import timber.log.Timber

/**
 * Shows the phone's audio-output switcher, opened from the watch ("Audio output on phone").
 *
 * An invisible, translucent activity that exists for one frame: the watch opens it with
 * `RemoteActivityHelper` (see `StandardActions.ACTION_OPEN_PHONE_AUDIO_OUTPUT`), which is the one
 * way a screen can appear on a phone whose app is in the background, and from inside it the app is
 * in the foreground - which the switcher API requires. It tries [AudioOutputRoute.forSdk] in order
 * and finishes as soon as one opened; the switcher itself is System UI's or Settings', so it stays
 * up after this is gone.
 *
 * Choosing where the *phone* plays from the *watch*, in the watch's own interface, would need the
 * phone to route another app's audio, which Android grants only to a companion app holding
 * `MEDIA_ROUTING_CONTROL` (Android 15) or to the system. This opens the phone's own list instead,
 * which every version allows.
 *
 * Exported because the companion app is what starts it. Anything else that opens the link gets the
 * same harmless result: the output switcher on screen.
 */
class AudioOutputSwitcherActivity : Activity() {

    private var opened = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A recreated instance (rotation, process restore) must not open the switcher a second time.
        opened = savedInstanceState?.getBoolean(STATE_OPENED) == true
    }

    override fun onResume() {
        super.onResume()
        if (opened) {
            finish()
            return
        }
        opened = true
        val targetPackage = DirectTransportControl(this).targetPackage()
        val route = AudioOutputRoute.forSdk(Build.VERSION.SDK_INT).firstOrNull { open(it, targetPackage) }
        Timber.i("Audio output switcher for %s opened by %s", targetPackage, route)
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_OPENED, opened)
    }

    private fun open(route: AudioOutputRoute, targetPackage: String?): Boolean = try {
        when (route) {
            AudioOutputRoute.SYSTEM_SWITCHER ->
                Build.VERSION.SDK_INT >= 34 && showSystemSwitcher()
            AudioOutputRoute.MEDIA_OUTPUT_DIALOG -> sendToSystem(Intent(ACTION_MEDIA_OUTPUT_DIALOG)
                    .setPackage(SYSTEM_UI_PACKAGE)
                    .putExtra(EXTRA_DIALOG_PACKAGE, targetPackage ?: packageName))
            AudioOutputRoute.MEDIA_OUTPUT_PANEL -> startSystemActivity(Intent(ACTION_MEDIA_OUTPUT_PANEL)
                    .putExtra(EXTRA_PANEL_PACKAGE, targetPackage ?: packageName))
            AudioOutputRoute.VOLUME_PANEL -> Build.VERSION.SDK_INT >= 29 &&
                    startSystemActivity(Intent(Settings.Panel.ACTION_VOLUME))
            AudioOutputRoute.BLUETOOTH_SETTINGS ->
                startSystemActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }
    } catch (e: RuntimeException) {
        // SecurityException from a panel that turned out to be guarded, ActivityNotFound from one
        // a vendor removed: either way, the next route.
        Timber.w(e, "Audio output route %s failed", route)
        false
    }

    @RequiresApi(34)
    private fun showSystemSwitcher(): Boolean = MediaRouter2.getInstance(this).showSystemOutputSwitcher()

    /** Only a receiver that belongs to the system: the action is a plain string anyone could claim. */
    private fun sendToSystem(intent: Intent): Boolean {
        val receivers = packageManager.queryBroadcastReceivers(intent, 0)
        if (receivers.none { it.activityInfo?.applicationInfo?.isSystem() == true }) return false
        sendBroadcast(intent)
        return true
    }

    /** The same rule for activities, so a third-party app answering the action is never opened. */
    private fun startSystemActivity(intent: Intent): Boolean {
        val activity = packageManager.queryIntentActivities(intent, 0)
                .firstOrNull { it.activityInfo?.applicationInfo?.isSystem() == true }?.activityInfo
                ?: return false
        if (!activity.permission.isNullOrEmpty() &&
                checkSelfPermission(activity.permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return false
        }
        return try {
            startActivity(intent.setClassName(activity.packageName, activity.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    private fun ApplicationInfo.isSystem(): Boolean = (flags and ApplicationInfo.FLAG_SYSTEM) != 0

    private companion object {
        const val STATE_OPENED = "opened"
        const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        const val ACTION_MEDIA_OUTPUT_DIALOG = "com.android.systemui.action.LAUNCH_MEDIA_OUTPUT_DIALOG"
        const val EXTRA_DIALOG_PACKAGE = "package_name"
        const val ACTION_MEDIA_OUTPUT_PANEL = "com.android.settings.panel.action.MEDIA_OUTPUT"
        const val EXTRA_PANEL_PACKAGE = "com.android.settings.panel.extra.PACKAGE_NAME"
    }
}
