package com.svartifoss.snfell.watch

import android.content.pm.ApplicationInfo
import android.preference.PreferenceManager
import com.svartifoss.snfell.common.MatejdroArtistAutosizeMigration
import com.svartifoss.snfell.watch.config.PreferencesBus
import com.svartifoss.snfell.watch.theme.UserFont
import com.matejdro.wearutils.logging.FileLogger
import com.matejdro.wearutils.logging.TimberExceptionWear
import dagger.hilt.android.HiltAndroidApp
import pl.tajchert.exceptionwear.ExceptionWear
import timber.log.Timber


@HiltAndroidApp
class WearMusicCenter : android.app.Application() {
    override fun onCreate() {
        super.onCreate()


        val isDebuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        Timber.setAppTag("WearMusicCenter")
        Timber.plant(Timber.AndroidDebugTree(isDebuggable))

        if (!isDebuggable) {
            ExceptionWear.initialize(this)
            Timber.plant(TimberExceptionWear(this))
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
