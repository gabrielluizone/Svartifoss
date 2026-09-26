package com.svartifoss.snfell.watch

import android.content.pm.ApplicationInfo
import android.preference.PreferenceManager
import com.svartifoss.snfell.common.MatejdroArtistAutosizeMigration
import com.svartifoss.snfell.watch.config.PreferencesBus
import com.svartifoss.snfell.watch.theme.UserFont
import com.matejdro.wearutils.logging.FileLogger
import com.svartifoss.snfell.common.logging.ReleaseLogcatTree
import com.svartifoss.snfell.watch.util.ErrorsOnlyExceptionWearTree
import dagger.hilt.android.HiltAndroidApp
import pl.tajchert.exceptionwear.ExceptionWear
import timber.log.Timber


@HiltAndroidApp
class WearMusicCenter : android.app.Application() {
    override fun onCreate() {
        super.onCreate()


        val isDebuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        Timber.setAppTag("WearMusicCenter")
        // In release, logcat keeps warnings and errors only - and ReleaseLogcatTree skips the work
        // of formatting the rest, which the plain AndroidDebugTree(false) did before dropping it.
        Timber.plant(if (isDebuggable) Timber.AndroidDebugTree(true) else ReleaseLogcatTree())

        if (!isDebuggable) {
            ExceptionWear.initialize(this)
            Timber.plant(ErrorsOnlyExceptionWearTree(this))
        }

        val fileLogger = FileLogger.getInstance(this)
        fileLogger.activate()
        Timber.plant(fileLogger)

        // Seeded before anything resolves a face: watchFontFamily takes no Context, so the
        // imported typeface is unreachable until this holder has one. A face composed before this
        // ran would silently fall back to the default family.
        UserFont.initialize(this)

        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        // The watch may be installed independently while developing, so repair its local copy as
        // well as the phone's source copy before any screen resolves the active face.
        MatejdroArtistAutosizeMigration.repair(preferences)
        PreferencesBus.value = preferences
    }
}
