package com.svartifoss.snfell

import android.app.Application
import android.content.pm.ApplicationInfo
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.svartifoss.snfell.common.CenterLongPressAction
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.di.DaggerAppComponent
import com.svartifoss.snfell.logging.CrashlyticsExceptionWearHandler
import com.svartifoss.snfell.logging.CrashReporting
import com.svartifoss.snfell.notifications.AnnouncementNotifications
import com.svartifoss.snfell.logging.TimberCrashlytics
import com.matejdro.wearutils.logging.FileLogger
import dagger.android.AndroidInjector
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import com.svartifoss.snfell.view.watchface.theme.WatchThemeRepository
import com.svartifoss.snfell.view.settings.AppLanguage
import pl.tajchert.exceptionwear.ExceptionDataListenerService
import timber.log.Timber
import javax.inject.Inject


class WearMusicCenter : Application(), HasAndroidInjector {
    @Inject
    lateinit var androidInjector: DispatchingAndroidInjector<Any>

    private lateinit var watchPreferenceSync: WatchPreferenceSyncCoordinator

    override fun onCreate() {
        DaggerAppComponent.builder()
                .application(this)
                .build()
                .inject(this)

        super.onCreate()

        CrashReporting.initialize(this)
        AnnouncementNotifications.initialize(this)

        Timber.setAppTag("WearMusicCenter")

        val isDebuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        Timber.plant(Timber.AndroidDebugTree(isDebuggable))

        if (!isDebuggable) {
            Timber.plant(TimberCrashlytics())
            ExceptionDataListenerService.setHandler(CrashlyticsExceptionWearHandler())
        }

        val fileLogger = FileLogger.getInstance(this)
        fileLogger.activate()
        Timber.plant(fileLogger)

        repairCenterLongPressPreference()
        // The on-watch face picker reads the theme library from a synced preference that only the
        // library's own save path writes, so a library created before that key existed never
        // reached the watch. Same once-per-process repair as the preference snapshot re-publish.
        try {
            WatchThemeRepository(this).publishAvailableThemes()
        } catch (e: RuntimeException) {
            Timber.w(e, "Could not publish the custom theme list to the watch")
        }
        applyThemeFromPreferences()
        // Before any activity is created, so the first screen is already in the chosen language.
        AppLanguage.applyStored(this)

        // Application lifetime, rather than a Settings Fragment lifecycle, owns phone -> watch
        // preference delivery. This also performs one startup repair sync for a stale watch.
        watchPreferenceSync = WatchPreferenceSyncCoordinator(this).also { it.start() }
    }

    /**
     * Undoes the short-lived migration that adopted the legacy long-press-for-queue boolean.
     *
     * That migration wrote `wear_center_long_press = "queue"` for anyone who had the old switch on.
     * The resolution rule has since changed (see [CenterLongPressAction.resolve]) so the gesture
     * defaults to the face picker regardless, but the value it *wrote* is an explicit choice and
     * would keep winning - leaving exactly the users it was meant to protect stuck with the queue.
     *
     * One-shot, and narrow: it only clears the key when it holds precisely the value the migration
     * produced alongside the legacy boolean that produced it, so a later deliberate choice of
     * "Open the queue" is never undone. The flag is local bookkeeping and stays out of
     * [MiscPreferences.EXPORTABLE] - a restored backup should not re-run it.
     */
    private fun repairCenterLongPressPreference() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (prefs.getBoolean(CENTER_LONG_PRESS_REPAIRED, false)) return
        val migrated = prefs.getString(MiscPreferences.WEAR_CENTER_LONG_PRESS.key, null) ==
                CenterLongPressAction.VALUE_QUEUE &&
                prefs.getBoolean(MiscPreferences.WEAR_CENTER_LONG_PRESS_QUEUE.key, false)
        prefs.edit().apply {
            putBoolean(CENTER_LONG_PRESS_REPAIRED, true)
            if (migrated) remove(MiscPreferences.WEAR_CENTER_LONG_PRESS.key)
        }.apply()
    }

    private fun applyThemeFromPreferences() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val mode = when (prefs.getString("app_theme", "system")) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    override fun androidInjector(): AndroidInjector<Any> = androidInjector

    private companion object {
        const val CENTER_LONG_PRESS_REPAIRED = "center_long_press_repaired"
    }
}
