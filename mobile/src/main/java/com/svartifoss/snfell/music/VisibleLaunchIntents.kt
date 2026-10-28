package com.svartifoss.snfell.music

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import timber.log.Timber

/**
 * Turns a [VisibleLaunch] into the `Intent` that is started, in one place, so the service's check
 * ("does the installed app declare this?") and the activity's launch can never describe two
 * different intents.
 */
object VisibleLaunchIntents {

    fun toIntent(launch: VisibleLaunch, referrerPackage: String): Intent =
            Intent(launch.action).apply {
                launch.data?.let { data = Uri.parse(it) }
                // Always explicit about the app: a play request must reach the app the shortcut is
                // for, never whichever other app also claims the action.
                setPackage(launch.targetPackage)
                launch.categories.forEach(::addCategory)
                launch.extras.forEach { (key, value) -> putExtra(key, value) }
                // Who is asking. Spotify's content-linking guide asks callers for exactly this, and
                // it is the standard way for any app to know a launch came from another app.
                putExtra(Intent.EXTRA_REFERRER, Uri.parse("android-app://$referrerPackage"))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

    /**
     * True when the installed target declares an exported activity for [launch] - read from the
     * app's manifest through PackageManager, the same public declaration `adb shell dumpsys
     * package` prints. A technique an app does not declare is never attempted.
     */
    fun isDeclared(context: Context, launch: VisibleLaunch): Boolean = try {
        context.packageManager.queryIntentActivities(
                toIntent(launch, context.packageName), PackageManager.MATCH_DEFAULT_ONLY)
                .any { it.activityInfo.exported && it.activityInfo.enabled }
    } catch (e: RuntimeException) {
        false
    }

    /** Starts [launch] if its app declares it; false when it does not or Android refused it. */
    fun start(context: Context, launch: VisibleLaunch): Boolean {
        if (!isDeclared(context, launch)) return false
        return try {
            context.startActivity(toIntent(launch, context.packageName))
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            Timber.w(e, "%s refused %s", launch.targetPackage, launch.action)
            false
        }
    }
}
