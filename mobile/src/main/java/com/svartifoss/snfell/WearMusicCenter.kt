package com.svartifoss.snfell

import android.app.Application
import android.content.pm.ApplicationInfo
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.svartifoss.snfell.common.CenterLongPressAction
import com.svartifoss.snfell.common.AlbumArtSource
import com.svartifoss.snfell.common.FaceScopedPreferences
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.common.MatejdroArtistAutosizeMigration
import com.svartifoss.snfell.di.DaggerAppComponent
import com.svartifoss.snfell.logging.CrashlyticsExceptionWearHandler
import com.svartifoss.snfell.logging.CrashReporting
import com.svartifoss.snfell.notifications.AnnouncementNotifications
import com.svartifoss.snfell.logging.TimberCrashlytics
import com.svartifoss.snfell.music.PlaylistShortcutStorage
import com.svartifoss.snfell.music.ShortcutArtworkStore
import com.matejdro.wearutils.logging.FileLogger
import dagger.android.AndroidInjector
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import com.svartifoss.snfell.view.watchface.theme.WatchThemeRepository
import com.svartifoss.snfell.view.settings.AppLanguage
import com.svartifoss.snfell.view.settings.UserFontStore
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
        migrateRetiredArtistPhotoStyle()
        // Builds that first introduced the artist-behaviour picker could materialize its generic
        // `static` default inside Matejdro's scope. Repair it before the process-lifetime sync
        // starts, otherwise that stale explicit value keeps overriding the face's smart default
        // on the watch even after an update.
        MatejdroArtistAutosizeMigration.repair(
                PreferenceManager.getDefaultSharedPreferences(this))
        repairShortcutArtworkStore()
        // The on-watch face picker reads the theme library from a synced preference that only the
        // library's own save path writes, so a library created before that key existed never
        // reached the watch. Same once-per-process repair as the preference snapshot re-publish.
        try {
            WatchThemeRepository(this).publishAvailableThemes()
        } catch (e: RuntimeException) {
            Timber.w(e, "Could not publish the custom theme list to the watch")
        }
        // Same once-per-process republish, for the same reason: a font imported while the watch was
        // unreachable - or by a build from before this path existed - has no other occasion to be
        // sent. It is free when nothing changed, since the DataItem's payload is the font's own
        // fingerprint and an identical item is never re-delivered.
        try {
            UserFontStore.publishToWatch(this)
        } catch (e: RuntimeException) {
            Timber.w(e, "Could not publish the imported font to the watch")
        }
        applyThemeFromPreferences()
        // Before any activity is created, so the first screen is already in the chosen language.
        AppLanguage.applyStored(this)

        // Application lifetime, rather than a Settings Fragment lifecycle, owns phone -> watch
        // preference delivery. This also performs one startup repair sync for a stale watch.
        watchPreferenceSync = WatchPreferenceSyncCoordinator(this).also { it.start() }
    }

    /**
     * Drops the queue covers that the shortcut-thumbnail store accumulated while the two shared
     * one folder (see [com.svartifoss.snfell.music.RemoteArtworkCache]).
     *
     * Queue covers now have a cache of their own, so nothing writes foreign files there any more -
     * but an install that opened a few streaming queues before this build already has hundreds of
     * them on disk, which is enough to push the shortcut asset store past the backup's per-store
     * cap and fail the whole export. Nothing prunes them on its own: `retainOnly` runs only when
     * the shortcut library is edited, so a user who never touches that screen would stay unable to
     * back up.
     *
     * This is exactly the same prune the shortcut screen already performs, so it can only ever
     * remove a thumbnail no saved shortcut refers to, and a thumbnail lost to a mistake here is
     * re-downloadable. Off the main thread because it is a folder listing plus up to a few hundred
     * deletes, and the flag is written before the work rather than after it: a prune interrupted
     * by the process dying leaves the rest to the shortcut screen's own `retainOnly`, which is a
     * far better outcome than re-listing the folder on every launch forever. The flag is a
     * completed migration, so it belongs to the backup's selectable local-app-state section
     * ([com.svartifoss.snfell.config.ConfigBackup]) rather than travelling with app settings.
     */
    private fun repairShortcutArtworkStore() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (prefs.getBoolean(SHORTCUT_ARTWORK_REPAIRED, false)) return
        prefs.edit().putBoolean(SHORTCUT_ARTWORK_REPAIRED, true).apply()
        Thread {
            try {
                ShortcutArtworkStore.retainOnly(
                        this, PlaylistShortcutStorage.load(this).map { it.link })
            } catch (e: RuntimeException) {
                Timber.w(e, "Could not prune the shortcut artwork store")
            }
        }.apply { isDaemon = true }.start()
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
    /**
     * Rewrites the retired `artist_photo` background style as the source it became.
     *
     * The value was a [PlayerBackgroundStyle] for one development cycle before the picture's
     * *source* was split from its *treatment*. Nothing released ever wrote it, but a device that
     * ran a development build did - and a stored value the style enum no longer knows leaves the
     * settings row showing a raw string, its picker with nothing selected, and the face quietly
     * drawing the album cover with no way for the user to see why.
     *
     * Face-scoped, so it can be stored under any number of `album_art_style@<face>` keys plus the
     * active-theme scope; the sweep covers all of them at once rather than guessing which face was
     * in use. [AlbumArtSource.migrate] does the translating, and it is lossless: the pair it writes
     * renders exactly what the old value rendered.
     *
     * Not one-shot-flagged, unlike the repairs above, and deliberately: this one is idempotent by
     * construction (it only ever matches a value it then removes), so it costs one preference scan
     * on a cold start and can never fire twice on the same key. A flag would only add a second
     * thing that must stay right.
     */
    private fun migrateRetiredArtistPhotoStyle() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val styleKey = MiscPreferences.ALBUM_ART_STYLE.key
        val sourceKey = MiscPreferences.WEAR_ALBUM_ART_SOURCE.key

        val stale = try {
            prefs.all.keys.filter {
                it == styleKey || it.startsWith("$styleKey${FaceScopedPreferences.SCOPE_SEPARATOR}")
            }
        } catch (e: RuntimeException) {
            Timber.w(e, "Could not read preferences to migrate the retired artwork style")
            return
        }
        if (stale.isEmpty()) return

        val editor = prefs.edit()
        var changed = false
        stale.forEach { key ->
            val current = try {
                prefs.getString(key, null)
            } catch (_: ClassCastException) {
                null
            }
            val migrated = AlbumArtSource.migrate(current) ?: return@forEach
            // The scope travels with the key, so the source lands on the same face the style was
            // stored for - writing the bare source key would move every other face onto the
            // artist picture.
            val scope = key.removePrefix(styleKey)
            editor.putString(key, migrated.first)
            editor.putString(sourceKey + scope, migrated.second.preferenceValue)
            changed = true
        }
        if (changed) {
            editor.apply()
            Timber.i("Migrated the retired artist_photo artwork style to the source setting")
        }
    }

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
        const val SHORTCUT_ARTWORK_REPAIRED = "shortcut_artwork_store_repaired"
    }
}
