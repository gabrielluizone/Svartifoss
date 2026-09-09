package com.svartifoss.snfell.view.mainactivity

import android.app.Activity
import android.content.Intent
import android.net.Uri
import timber.log.Timber

/**
 * Where the "watch app is missing" dialog sends someone, in the GitHub sideload build.
 *
 * The watch app is fetched by hand here, so the button opens the releases page **on the phone** -
 * that is where the APK is downloaded and where Wear Installer runs. The `wearutils` base class
 * would instead remote-open a `market://` page on the watch, which is a dead end for this build.
 *
 * The `play` flavour's counterpart does the opposite, and the matching dialog text lives in each
 * flavour's own resource folder, so neither artifact carries the other's instructions.
 * See docs/play-store-migration-plan.md.
 */
object WatchAppInstallRoute {

    private const val RELEASES_URL = "https://github.com/gabrielluizone/Svartifoss/releases"

    fun open(activity: Activity) {
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_URL)))
        } catch (e: Exception) {
            Timber.e(e, "Could not open the releases page")
        }
    }
}
