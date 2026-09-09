package com.svartifoss.snfell.view.mainactivity

import android.app.Activity
import android.content.Intent
import android.net.Uri
import com.google.android.wearable.intent.RemoteIntent
import timber.log.Timber

/**
 * Where the "watch app is missing" dialog sends someone, in the Play build.
 *
 * Phone and watch are one Play listing sharing an `applicationId`, so Play delivers the watch app
 * to the paired watch on its own and there is nothing to download by hand. The dialog exists only
 * because that delivery is not instant, which makes the useful action "open this same listing in
 * the Play Store **on the watch**" - the one route that can hurry it along.
 *
 * That is exactly what `wearutils`'s `WearCompanionPhoneActivity.openWatchPlayStorePage` already
 * did before the app left the Play Store; this restores it rather than reimplementing it
 * differently. `market://details?id=<phone package>` resolves to the right page precisely because
 * the two apps share the id.
 *
 * Sending someone outside Play for an APK - what the `github` flavour's counterpart does - is
 * forbidden by Play's Device and Network Abuse policy, which is why this is split by flavour at
 * all rather than branched at runtime. See docs/play-store-migration-plan.md.
 */
object WatchAppInstallRoute {

    fun open(activity: Activity) {
        val listing = Intent(Intent.ACTION_VIEW).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            data = Uri.parse("market://details?id=${activity.packageName}")
        }
        try {
            RemoteIntent.startRemoteActivity(activity, listing, null)
        } catch (e: Exception) {
            Timber.e(e, "Could not open the watch's Play Store listing")
        }
    }
}
