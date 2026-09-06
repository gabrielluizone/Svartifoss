package com.svartifoss.snfell.update

import android.content.Context
import android.content.Intent

/**
 * Flavor seam for the in-app self-updater. `src/main` code (MusicService, MainActivity,
 * MiscSettingsFragment) only ever talks to this object, never to [UpdateChecker] /
 * [UpdateActivity] directly - those exist only in the `github` source set, because Google Play
 * forbids an app updating itself outside Play. The `play` flavor provides a no-op twin of this
 * file. See docs/play-store-migration-plan.md.
 *
 * This is the `github` (sideload) implementation: it delegates straight to [UpdateChecker].
 */
object UpdateGateway {

    /** Whether this build carries the GitHub-releases self-updater at all. */
    const val SUPPORTS_SELF_UPDATE = true

    suspend fun maybeCheckInBackground(context: Context) =
            UpdateChecker.maybeCheckInBackground(context)

    fun consumePostUpdateWelcome(context: Context): Boolean =
            UpdateChecker.consumePostUpdateWelcome(context)

    fun hasPendingUpdate(context: Context): Boolean =
            UpdateChecker.hasPendingUpdate(context)

    fun openUpdateScreen(context: Context) {
        context.startActivity(Intent(context, UpdateActivity::class.java).apply {
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
