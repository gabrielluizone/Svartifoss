package com.svartifoss.snfell.update

import android.content.Context

/**
 * Play Store build: there is no in-app self-updater. Google Play's Device and Network Abuse
 * policy forbids an app updating itself by any route other than Play, so the `update/` package
 * (UpdateChecker, UpdateActivity, the APK downloader/installer, WatchApkPusher) and the
 * REQUEST_INSTALL_PACKAGES permission are simply absent from this flavor - Play delivers updates.
 *
 * Every method here is the inert counterpart of the `github` implementation. Callers in `src/main`
 * gate on [SUPPORTS_SELF_UPDATE] where they would otherwise show an update affordance.
 * See docs/play-store-migration-plan.md.
 */
object UpdateGateway {

    const val SUPPORTS_SELF_UPDATE = false

    @Suppress("UNUSED_PARAMETER")
    suspend fun maybeCheckInBackground(context: Context) = Unit

    @Suppress("UNUSED_PARAMETER")
    fun consumePostUpdateWelcome(context: Context): Boolean = false

    @Suppress("UNUSED_PARAMETER")
    fun hasPendingUpdate(context: Context): Boolean = false

    @Suppress("UNUSED_PARAMETER")
    fun openUpdateScreen(context: Context) = Unit
}
