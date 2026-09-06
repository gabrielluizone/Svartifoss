package com.svartifoss.snfell.view.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.text.format.Formatter
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import com.svartifoss.snfell.NotificationService
import android.content.pm.ApplicationInfo
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.common.RotaryAction
import com.svartifoss.snfell.common.model.AutoStartMode
import com.svartifoss.snfell.config.ConfigBackup
import com.svartifoss.snfell.config.ConfigBackupSection
import com.svartifoss.snfell.config.DefaultConfigExport
import com.svartifoss.snfell.update.UpdateActivity
import com.svartifoss.snfell.config.WatchInfoProvider
import com.svartifoss.snfell.config.WatchInfoWithIcons
import com.svartifoss.snfell.music.MusicService
import com.svartifoss.snfell.music.PlaylistShortcutStorage
import com.svartifoss.snfell.music.QueueArtworkResolver
import com.svartifoss.snfell.music.StreamingService
import com.svartifoss.snfell.music.StreamingShortcutLinks
import com.svartifoss.snfell.util.WearableAvailability
import com.svartifoss.snfell.util.launchWithPlayServicesErrorHandling
import com.svartifoss.snfell.view.TitledActivity
import com.svartifoss.snfell.view.applyLyraDialogStyling
import com.svartifoss.snfell.view.settings.dev.buildAppearanceResolutionReport
import com.svartifoss.snfell.view.settings.dev.buildDataLayerReport
import com.svartifoss.snfell.view.settings.dev.buildMediaSessionsReport
import com.svartifoss.snfell.view.settings.dev.buildPhoneLogReport
import com.svartifoss.snfell.view.settings.dev.buildThemeSubmissionPreflightReport
import com.svartifoss.snfell.view.settings.dev.buildWatchSnapshotReport
import com.svartifoss.snfell.view.settings.dev.showDevReportDialog
import com.svartifoss.snfell.view.watchface.theme.CommunityThemeAccountActivity
import com.svartifoss.snfell.view.watchface.theme.CommunityThemeAccountRepository
import com.svartifoss.snfell.view.watchface.theme.CommunityThemeAccountState
import com.matejdro.wearutils.logging.LogRetrievalTask
import com.matejdro.wearutils.preferences.compat.PreferenceFragmentCompatEx
import com.matejdro.wearutils.preferences.definition.Preferences
import com.matejdro.wearutils.preferencesync.PreferencePusher
import com.google.android.gms.wearable.Wearable
import dagger.android.support.AndroidSupportInjection
import de.psdev.licensesdialog.LicensesDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import timber.log.Timber
import javax.inject.Inject


class MiscSettingsFragment : PreferenceFragmentCompatEx() {
    companion object {
        private const val PREF_DEV_MODE = "developer_mode_enabled"
        private const val DEV_CLICKS_REQUIRED = 7

        const val SECTION_GENERAL = "general"
        const val SECTION_WATCH = "watch"
        const val SECTION_AUTOMATION = "automation"
        const val SECTION_APPS = "apps"
        const val SECTION_DATA = "data"
        private const val ARG_SECTION = "settingsSection"
        private const val ARG_HIGHLIGHT_KEY = "settingsHighlightKey"

        private const val DEVELOPER_GITHUB_URL = "https://github.com/gabrielluizone"
        private const val PRIVACY_POLICY_URL =
                "https://gabrielluizone.github.io/Svartifoss/privacy-policy.html"

        /** [highlightKey] scrolls the page to that preference once laid out - set only by the
         *  settings search, so a result lands on the row itself rather than at the top of a page
         *  the user then has to scan. */
        fun newInstance(section: String, highlightKey: String? = null) = MiscSettingsFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_SECTION, section)
                putString(ARG_HIGHLIGHT_KEY, highlightKey)
            }
        }
    }

    private var versionClickCount = 0
    private var devModeEnabled = false
    private var section = SECTION_GENERAL

    @Inject
    lateinit var watchInfoProvider: WatchInfoProvider

    private val importConfigLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { importConfigFrom(it) } }

    /** Developer-only: writes the document that ships as `res/raw/default_config.json`. Kept apart
     *  from the ordinary backup export, which lives on its own selection screen, because the two
     *  produce the same format and only this one is safe to put inside an APK - see
     *  [DefaultConfigExport]. */
    private val exportDefaultConfigLauncher = registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { exportDefaultConfigTo(it) } }

    /** Media access for local-library queue covers - see [QueueArtworkResolver]. Requested from
     *  its own preference row rather than at startup, so the grant dialog appears with the reason
     *  already on screen instead of unprompted on first launch. */
    private val mediaPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
    ) { granted ->
        refreshQueueArtworkSummary()
        val context = context ?: return@registerForActivityResult
        Toast.makeText(
                context,
                if (granted) R.string.setting_queue_local_artwork_granted
                else R.string.setting_queue_local_artwork_denied,
                Toast.LENGTH_LONG
        ).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        section = arguments?.getString(ARG_SECTION) ?: SECTION_GENERAL
        AndroidSupportInjection.inject(this)
        super.onCreate(savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        watchInfoProvider.observe(viewLifecycleOwner, noWatchBannerObserver)
        consumeHighlightKey()
    }

    /**
     * Scrolls to the preference a search result pointed at, then clears the argument.
     *
     * Clearing matters: the argument would otherwise survive into every later recreation of this
     * page, so rotating the phone after scrolling elsewhere would yank the list back to a row the
     * user had already moved on from. Posted to the list because the categories are only made
     * visible in onCreatePreferences, and scrolling to a row that is still GONE does nothing.
     */
    private fun consumeHighlightKey() {
        val key = arguments?.getString(ARG_HIGHLIGHT_KEY) ?: return
        arguments?.remove(ARG_HIGHLIGHT_KEY)
        listView?.post {
            if (!isAdded) return@post
            findPreference<Preference>(key)?.let(::scrollToAndPulsePreference)
        }
    }

    private var pendingNoWatchBannerJob: Job? = null

    // Every setting on this screen still works and gets saved without a paired watch - it just
    // won't visibly apply on a watch until one connects. This banner only sets that expectation,
    // it never disables anything (see the discussion in MainActivity about not gating navigation
    // on watch presence).
    private val noWatchBannerObserver = Observer<WatchInfoWithIcons?> { watchInfo ->
        updateNoWatchBanner(watchInfo)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.settings)

        devModeEnabled = preferenceManager.sharedPreferences?.getBoolean(PREF_DEV_MODE, false) == true

        initAppearanceSection()
        migrateRotarySeekSetting()
        initNavigationLinks()
        initAppsSection()
        initAutomationSection()
        initBackupSection()
        initCommunityThemesSection()
        initAboutSection()
        initDevSection()
        applySectionVisibility()
    }

    private fun initNavigationLinks() {
        findPreference<Preference>("swipe_gestures_hint")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                (activity as? com.svartifoss.snfell.view.mainactivity.MainActivity)?.openControls()
                true
            }
    }

    fun showSection(newSection: String) {
        section = newSection
        applySectionVisibility()
        updateNoWatchBanner(watchInfoProvider.value)
        listView?.scrollToPosition(0)
    }

    private fun applySectionVisibility() {
        if (preferenceScreen == null) return

        // Section -> categories and the full category list both live in SettingsCatalog, shared
        // with the settings search so a result can be navigated to the page it is actually on.
        val visibleCategories = SettingsCatalog.SETTINGS_SECTIONS[section]
            ?: SettingsCatalog.SETTINGS_SECTIONS.getValue(SECTION_GENERAL)

        SettingsCatalog.SETTINGS_CATEGORIES.forEach { key ->
            findPreference<PreferenceCategory>(key)?.isVisible = key in visibleCategories
        }
        updateDevModeVisibility()
    }

    private fun updateNoWatchBanner(watchInfo: WatchInfoWithIcons?) {
        pendingNoWatchBannerJob?.cancel()
        val banner = findPreference<Preference>("no_watch_banner") ?: return
        val relevantSection = section == SECTION_WATCH || section == SECTION_AUTOMATION
        banner.isVisible = false
        if (watchInfo == null && relevantSection && watchInfoProvider.hasResolvedInitialValue) {
            // WatchInfoProvider emits an initial null while querying the Data Layer. A short
            // debounce avoids flashing the disconnected notice for connected watches.
            pendingNoWatchBannerJob = lifecycleScope.launch {
                delay(600)
                val show = watchInfoProvider.value == null &&
                        watchInfoProvider.hasResolvedInitialValue &&
                        (section == SECTION_WATCH || section == SECTION_AUTOMATION)
                if (show) {
                    // "No watch connected" is misleading on a device that has no Data Layer at
                    // all: no amount of pairing will help, and the settings below genuinely will
                    // never apply. Say which of the two situations it actually is.
                    val hasDataLayer = WearableAvailability.isAvailable(requireContext())
                    banner.setTitle(
                            if (hasDataLayer) R.string.setting_no_watch_banner
                            else R.string.setting_no_wearable_api_banner)
                    banner.setSummary(
                            if (hasDataLayer) R.string.setting_no_watch_banner_description
                            else R.string.setting_no_wearable_api_banner_description)
                }
                banner.isVisible = show
            }
        }
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        super.onDisplayPreferenceDialog(preference)
        // Preference dialogs (Theme, ...) inflate with the static theme colors; once the
        // dialog is up, re-tint it with the accent currently on screen.
        view?.post { tintOpenLyraPreferenceDialog() }
    }

    private fun initAppearanceSection() {
        findPreference<ListPreference>("app_theme")?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                applyTheme(newValue as String)
                true
            }

        findPreference<ListPreference>("app_language")?.let { languagePref ->
            languagePref.summary = "%s"
            languagePref.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    // The preference is written by the framework right after this returns true,
                    // and WatchPreferenceSyncCoordinator picks the change up from there and pushes
                    // it - nothing here needs to talk to the watch itself.
                    AppLanguage.apply(newValue as String)
                    Toast.makeText(
                            requireContext(),
                            R.string.language_change_watch_notice,
                            Toast.LENGTH_LONG
                    ).show()
                    true
                }
        }

        val accentPref = findPreference<Preference>("custom_accent_color")
        updateAccentColorSummary(accentPref)
        accentPref?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            showAccentColorPicker(accentPref)
            true
        }

    }

    private fun updateAccentColorSummary(pref: Preference?) {
        pref ?: return
        val saved = preferenceManager.sharedPreferences?.getString("custom_accent_color", null)
        pref.summary = if (saved != null) {
            getString(R.string.color_picker_current, saved)
        } else {
            getString(R.string.setting_custom_accent_color_description)
        }
        (pref as? ColorDotPreference)?.refreshDot()
    }

    private fun showAccentColorPicker(pref: Preference?) {
        val prefs = preferenceManager.sharedPreferences ?: return

        showLyraColorPickerDialog(
                initialColor = parseHexOrDefault(prefs.getString("custom_accent_color", null)),
                onReset = {
                    prefs.edit().remove("custom_accent_color").apply()
                    updateAccentColorSummary(pref)
                    (activity as? com.svartifoss.snfell.view.mainactivity.MainActivity)
                        ?.onCustomAccentColorChanged(null)
                },
                onApply = { hex ->
                    prefs.edit().putString("custom_accent_color", hex).apply()
                    updateAccentColorSummary(pref)
                    (activity as? com.svartifoss.snfell.view.mainactivity.MainActivity)
                        ?.onCustomAccentColorChanged(hex)
                }
        )
    }

    private fun applyTheme(value: String) {
        val mode = when (value) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    /** Phone-side app capabilities live together here: streaming link routing, shortcut import,
     * notification-listener access and the per-music-app automatic-launch filter. */
    /** Reflects whether media access is currently granted, so the row states the actual situation
     *  instead of always inviting a grant that may already have happened. */
    private fun refreshQueueArtworkSummary() {
        val preference = findPreference<Preference>("queue_media_permission") ?: return
        val context = context ?: return
        preference.setSummary(
                if (QueueArtworkResolver.hasMediaPermission(context)) {
                    R.string.setting_queue_local_artwork_granted
                } else {
                    R.string.setting_queue_local_artwork_description
                })
    }

    private fun openAppDetailsSettings() {
        try {
            startActivity(Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", requireContext().packageName, null)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(requireContext(),
                    R.string.setting_notification_access_unavailable,
                    Toast.LENGTH_LONG).show()
        }
    }

    private fun initAppsSection() {
        migrateStreamingOpenMode()

        findPreference<Preference>("playlist_shortcuts")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                startActivity(Intent(requireContext(), PlaylistShortcutsActivity::class.java))
                true
            }

        findPreference<Preference>("queue_media_permission")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                if (QueueArtworkResolver.hasMediaPermission(requireContext())) {
                    // Already granted - the only way back from here is the system app settings,
                    // since a granted runtime permission cannot be revoked by the app itself.
                    openAppDetailsSettings()
                } else {
                    mediaPermissionLauncher.launch(QueueArtworkResolver.MEDIA_PERMISSION)
                }
                true
            }
        refreshQueueArtworkSummary()

        findPreference<Preference>("notification_access")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                try {
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(
                            requireContext(),
                            R.string.setting_notification_access_unavailable,
                            Toast.LENGTH_LONG
                    ).show()
                } catch (_: SecurityException) {
                    Toast.makeText(
                            requireContext(),
                            R.string.setting_notification_access_unavailable,
                            Toast.LENGTH_LONG
                    ).show()
                }
                true
            }

        findPreference<Preference>("persistent_notification_settings")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                // The "Svartifoss active" notification can't be turned off from inside the app -
                // it belongs to MusicService, a foreground service, and Android requires an
                // ongoing notification for as long as one is running. This deep-links to that
                // channel's own system settings page instead, same pattern as notification_access
                // above, so the user can still mute/hide it themselves. Per-channel settings only
                // exist on API 26+; older versions fall back to the app's notification settings.
                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
                            .putExtra(Settings.EXTRA_CHANNEL_ID, MusicService.KEY_NOTIFICATION_CHANNEL)
                } else {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
                }
                try {
                    startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(
                            requireContext(),
                            R.string.setting_notification_access_unavailable,
                            Toast.LENGTH_LONG
                    ).show()
                }
                true
            }

        refreshAppsSection()
    }

    /** Converts the former boolean into the richer three-way routing preference once. */
    private fun migrateStreamingOpenMode() {
        val sharedPreferences = preferenceManager.sharedPreferences ?: return
        if (sharedPreferences.contains(StreamingShortcutLinks.OPEN_MODE_KEY)) return

        val mode = if (sharedPreferences.getBoolean(
                        StreamingShortcutLinks.PREFER_INSTALLED_APP_KEY,
                        true
                )) {
            StreamingShortcutLinks.OPEN_MODE_APP
        } else {
            StreamingShortcutLinks.OPEN_MODE_DEFAULT
        }
        findPreference<ListPreference>(StreamingShortcutLinks.OPEN_MODE_KEY)?.value = mode
        sharedPreferences.edit()
                .remove(StreamingShortcutLinks.PREFER_INSTALLED_APP_KEY)
                .apply()
    }

    /** Summaries are live because the user may install an app or grant notification access while
     * this fragment is in the background. Package visibility is covered by manifest queries for
     * every service listed in [StreamingService]. */
    private fun refreshAppsSection() {
        if (preferenceScreen == null || !isAdded) return

        // The grant can also be changed from Android's own app settings, so re-read it on every
        // return to this screen rather than only after our own request completes.
        refreshQueueArtworkSummary()

        val supportedServices = StreamingService.values().filter { it.packageName != null }
        val installedServices = supportedServices.filter { service ->
            val packageName = service.packageName ?: return@filter false
            try {
                requireContext().packageManager.getPackageInfo(packageName, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            } catch (_: SecurityException) {
                false
            }
        }
        findPreference<Preference>("apps_integrations_info")?.summary =
                if (installedServices.isEmpty()) {
                    getString(R.string.setting_apps_integrations_none)
                } else {
                    getString(
                            R.string.setting_apps_integrations_installed,
                            installedServices.size,
                            supportedServices.size,
                            installedServices.joinToString(", ") { streamingServiceName(it) }
                    )
                }

        val shortcutCount = PlaylistShortcutStorage.load(requireContext()).size
        findPreference<Preference>("playlist_shortcuts")?.summary =
                resources.getQuantityString(
                        R.plurals.setting_playlist_shortcuts_count,
                        shortcutCount,
                        shortcutCount
                )

        findPreference<Preference>("notification_access")?.setSummary(
                if (NotificationService.isEnabled(requireContext())) {
                    R.string.setting_notification_access_enabled
                } else {
                    R.string.setting_notification_access_disabled
                }
        )
    }

    private fun streamingServiceName(service: StreamingService): String = getString(
            when (service) {
                StreamingService.YOUTUBE_MUSIC -> R.string.playlist_source_yt_music
                StreamingService.SPOTIFY -> R.string.playlist_source_spotify
                StreamingService.DEEZER -> R.string.playlist_source_deezer
                StreamingService.TIDAL -> R.string.playlist_source_tidal
                StreamingService.APPLE_MUSIC -> R.string.playlist_source_apple_music
                StreamingService.AMAZON_MUSIC -> R.string.playlist_source_amazon_music
                StreamingService.SOUNDCLOUD -> R.string.playlist_source_soundcloud
                StreamingService.QOBUZ -> R.string.playlist_source_qobuz
                StreamingService.BANDCAMP -> R.string.playlist_source_bandcamp
                StreamingService.AUDIOMACK -> R.string.playlist_source_audiomack
                StreamingService.MIXCLOUD -> R.string.playlist_source_mixcloud
                StreamingService.PANDORA -> R.string.playlist_source_pandora
                StreamingService.GENERIC -> R.string.playlist_source_link
            }
    )

    private fun initAutomationSection() {
        migrateOldAutoStartSetting()

        findPreference<Preference>("auto_start_apps_blacklist")?.isEnabled =
                Preferences.getEnum(preferenceManager.sharedPreferences, MiscPreferences.AUTO_START_MODE) != AutoStartMode.OFF

        findPreference<Preference>("auto_start_mode")!!.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    newValue as String
                    val mode = enumValueOf<AutoStartMode>(newValue)
                    findPreference<Preference>("auto_start_apps_blacklist")?.isEnabled = mode != AutoStartMode.OFF

                    NotificationService.updateQuickActionsBinding(
                            requireContext().applicationContext,
                            autoStartEnabledOverride = mode != AutoStartMode.OFF
                    )
                    true
                }
    }

    /**
     * Seeds the three-way rotary preference from the legacy `rotary_seek` boolean the first time
     * this section is opened. [RotaryAction.resolve] already falls back to the boolean at read
     * time, so this is purely so the picker shows the behaviour actually in force instead of its
     * XML default. The legacy key is deliberately left in place: the watch resolves through the
     * same fallback, and deleting it would flip an out-of-date watch back to volume until the new
     * key synced across.
     */
    private fun migrateRotarySeekSetting() {
        val preferences = preferenceManager.sharedPreferences!!
        if (preferences.contains("wear_rotary_action")) {
            return
        }
        val resolved = RotaryAction.resolve(
                null, Preferences.getBoolean(preferences, MiscPreferences.ROTARY_SEEK))
        findPreference<ListPreference>("wear_rotary_action")?.value = resolved.preferenceValue
    }

    private fun migrateOldAutoStartSetting() {
        val preferences = preferenceManager.sharedPreferences!!
        if (preferences.contains("auto_start")) {
            val legacyAutoStart = Preferences.getBoolean(preferences, MiscPreferences.AUTO_START)
            val autoStartMode = if (legacyAutoStart) AutoStartMode.OPEN_APP else AutoStartMode.OFF
            findPreference<ListPreference>("auto_start_mode")?.value = autoStartMode.name
            preferences.edit().remove("auto_start").apply()
        }
    }

    private fun initBackupSection() {
        findPreference<Preference>("exportConfig")!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                startActivity(Intent(requireContext(), ConfigBackupSelectionActivity::class.java))
                true
            }

        findPreference<Preference>("importConfig")!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                importConfigLauncher.launch(arrayOf("application/json", "*/*"))
                true
            }
    }

    /** Community identity is phone-local and never a watch setting, so it belongs in Data & support. */
    private fun initCommunityThemesSection() {
        findPreference<Preference>("community_theme_account")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                startActivity(Intent(requireContext(), CommunityThemeAccountActivity::class.java))
                true
            }
        refreshCommunityThemeAccountSummary()
    }

    /** Reflects the Firebase session when returning from the Community account screen. */
    private fun refreshCommunityThemeAccountSummary() {
        val preference = findPreference<Preference>("community_theme_account") ?: return
        preference.setSummary(when (CommunityThemeAccountRepository().state()) {
            CommunityThemeAccountState.GOOGLE ->
                R.string.community_theme_account_settings_google
            CommunityThemeAccountState.ANONYMOUS_LIKES ->
                R.string.community_theme_account_settings_anonymous
            CommunityThemeAccountState.SIGNED_OUT ->
                R.string.community_theme_account_settings_signed_out
        })
    }

    private fun importConfigFrom(uri: Uri) {
        val importedSections: Set<ConfigBackupSection>
        try {
            val text = requireContext().contentResolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            } ?: throw java.io.IOException("Could not open input stream")

            importedSections = ConfigBackup.import(
                    requireContext(), preferenceManager.sharedPreferences!!, JSONObject(text))
        } catch (e: Exception) {
            Timber.e(e, "Config import failed")
            Toast.makeText(requireContext(), R.string.import_config_failed, Toast.LENGTH_LONG).show()
            return
        }

        // The button/action-list transmitters only push to the watch when their DataItem is
        // missing (ButtonConfigTransmitter/ActionListTransmitter.resendIfNeeded). If either of
        // those sections was imported, the items still hold the OLD config, so the watch would
        // keep the old buttons until each config was hand-edited and re-saved. Clear them so the
        // post-restart transmitters re-push the freshly imported config. Wait for the clear to
        // finish before offering the restart so it isn't cut short by restartApp()'s process exit;
        // a missing/disconnected watch no-ops.
        val appContext = requireContext().applicationContext
        lifecycleScope.launch {
            if (ConfigBackupSection.BUTTONS in importedSections ||
                    ConfigBackupSection.ACTIONS in importedSections) {
                try {
                    clearWatchConfigDataItems(appContext)
                } catch (e: Exception) {
                    Timber.w(e, "Could not clear watch config DataItems after import")
                }
            }

            AlertDialog.Builder(requireContext())
                .setTitle(R.string.import_config_done_title)
                .setMessage(R.string.import_config_done_message)
                .setCancelable(false)
                .setPositiveButton(R.string.action_restart_now) { _, _ -> restartApp() }
                .show()
                .applyLyraDialogStyling(accent = lyraRuntimeAccent())
        }
    }

    /** Removes the playing/stopped button configs and the action list from the watch so the
     *  transmitters re-send the imported config on next launch (see [importConfigFrom]). */
    private suspend fun clearWatchConfigDataItems(context: Context) {
        val dataClient = Wearable.getDataClient(context)
        for (path in listOf(
                CommPaths.DATA_PLAYING_ACTION_CONFIG,
                CommPaths.DATA_STOPPING_ACTION_CONFIG,
                CommPaths.DATA_LIST_ITEMS
        )) {
            dataClient.deleteDataItems(Uri.parse("wear://*$path")).await()
        }
    }

    private fun restartApp() {
        val appContext = requireContext().applicationContext
        val intent = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        appContext.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }

    private fun initAboutSection() {
        findPreference<Preference>("update_check_now")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                startActivity(Intent(requireContext(), UpdateActivity::class.java))
                true
            }

        findPreference<Preference>("privacy_policy")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)))
                true
            }

        findPreference<Preference>("supportButton")!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                sendLogs()
                true
            }

        val versionPref = findPreference<Preference>("version")!!
        try {
            versionPref.summary =
                requireActivity().packageManager.getPackageInfo(requireActivity().packageName, 0).versionName
        } catch (ignored: PackageManager.NameNotFoundException) {}

        versionPref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            handleVersionClick()
            true
        }

        findPreference<Preference>("contactDeveloper")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                contactDeveloper()
                true
            }

        findPreference<Preference>("licenses")!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                LicensesDialog.Builder(activity)
                        .setNotices(R.raw.notices)
                        .setIncludeOwnLicense(true)
                        .build()
                        .show()
                true
            }

        findPreference<Preference>("aboutDeveloper")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                // setCustomTitle rather than setTitle + setIcon: the platform's icon slot is a
                // fixed 32dp badge beside the text, and this is a portrait introducing a name.
                val builder = AlertDialog.Builder(requireContext())
                val titleView = LayoutInflater.from(builder.context)
                        .inflate(R.layout.dialog_title_developer, null)
                titleView.findViewById<ImageView>(R.id.developer_avatar)
                        .setImageDrawable(developerAvatar(builder.context))
                builder.setCustomTitle(titleView)
                        .setMessage(R.string.about_developer_dialog_message)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                        .applyLyraDialogStyling(accent = lyraRuntimeAccent())
                true
            }

        findPreference<Preference>("developerGithub")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DEVELOPER_GITHUB_URL)))
                true
            }
    }

    /**
     * Opens the user's mail app pre-addressed to the developer, with the app version already in the
     * subject so a support mail arrives with the one fact that is always needed.
     *
     * ACTION_SENDTO + a `mailto:` URI rather than ACTION_SEND: it is the only form that resolves to
     * mail clients *only*, so the chooser can't offer to "share" the message to unrelated apps. On
     * a phone with no mail client at all the address is put on the clipboard instead, so the
     * preference is never a dead end.
     */
    private fun contactDeveloper() {
        val address = getString(R.string.contact_developer_email)
        val version = try {
            requireActivity().packageManager
                    .getPackageInfo(requireActivity().packageName, 0).versionName ?: ""
        } catch (ignored: PackageManager.NameNotFoundException) {
            ""
        }

        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$address")).apply {
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.contact_developer_subject, version))
        }

        try {
            startActivity(Intent.createChooser(intent, getString(R.string.contact_developer_chooser)))
        } catch (ignored: ActivityNotFoundException) {
            val clipboard = requireContext()
                    .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(address, address))
            Toast.makeText(
                    requireContext(),
                    R.string.contact_developer_no_client,
                    Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun initDevSection() {
        val buildPref = findPreference<Preference>("dev_build_info")
        buildPref?.summary = buildShortBuildSummary()
        buildPref?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            showEasterEgg()
            true
        }

        findPreference<Preference>("dev_sync_watch")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                forceSyncWatchSettings()
                true
            }

        findPreference<Preference>("dev_copy_debug_info")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                copyDebugInfoToClipboard()
                true
            }

        findPreference<Preference>("dev_open_update_screen")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                startActivity(Intent(requireContext(), UpdateActivity::class.java))
                true
            }

        findPreference<Preference>("dev_export_defaults")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                exportDefaultConfigLauncher.launch(DefaultConfigExport.FILE_NAME)
                true
            }

        findPreference<Preference>("dev_disable_mode")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                disableDevMode()
                true
            }

        findPreference<Preference>("dev_watch_snapshot_inspector")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                showDevReportDialog(
                        requireContext(), getString(R.string.dev_watch_snapshot_inspector),
                        buildWatchSnapshotReport(requireContext()))
                true
            }

        findPreference<Preference>("dev_data_layer_inspector")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                val title = getString(R.string.dev_data_layer_inspector)
                lifecycleScope.launch {
                    val report = buildDataLayerReport(requireContext(), watchInfoProvider.value)
                    showDevReportDialog(requireContext(), title, report)
                }
                true
            }

        findPreference<Preference>("dev_media_sessions_inspector")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                showDevReportDialog(
                        requireContext(), getString(R.string.dev_media_sessions_inspector),
                        buildMediaSessionsReport(requireContext()))
                true
            }

        findPreference<Preference>("dev_view_phone_log")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                val title = getString(R.string.dev_view_phone_log)
                val context = requireContext().applicationContext
                lifecycleScope.launch {
                    val report = withContext(Dispatchers.IO) { buildPhoneLogReport(context) }
                    showDevReportDialog(requireContext(), title, report)
                }
                true
            }

        findPreference<Preference>("dev_theme_submission_preflight")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                val title = getString(R.string.dev_theme_submission_preflight)
                val context = requireContext().applicationContext
                lifecycleScope.launch {
                    val report = withContext(Dispatchers.IO) {
                        buildThemeSubmissionPreflightReport(context)
                    }
                    showDevReportDialog(requireContext(), title, report)
                }
                true
            }

        findPreference<Preference>("dev_appearance_resolution_inspector")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                showDevReportDialog(
                        requireContext(), getString(R.string.dev_appearance_resolution_inspector),
                        buildAppearanceResolutionReport(requireContext()))
                true
            }
    }

    /**
     * Writes the defaults snapshot and reports its size.
     *
     * The size is on screen because this file is compiled into the APK: the icon stores travel
     * with it, so a library of fetched shortcut artwork can turn a few kilobytes of settings into
     * several megabytes of release, and nothing else would say so until the build was measured.
     */
    private fun exportDefaultConfigTo(uri: Uri) {
        val context = context ?: return
        try {
            val preferences = preferenceManager.sharedPreferences
                    ?: throw IOException("No preference store")
            val json = DefaultConfigExport.build(context, preferences)
            val bytes = json.toString(2).toByteArray(Charsets.UTF_8)
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: throw IOException("Could not open output stream")
            Toast.makeText(
                    context,
                    getString(
                            R.string.dev_export_defaults_done,
                            Formatter.formatShortFileSize(context, bytes.size.toLong())),
                    Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Timber.e(e, "Default config export failed")
            val reason = e.message
            Toast.makeText(
                    context,
                    if (reason.isNullOrBlank()) {
                        getString(R.string.export_config_failed)
                    } else {
                        getString(R.string.export_config_failed_detail, reason)
                    },
                    Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun disableDevMode() {
        devModeEnabled = false
        versionClickCount = 0
        preferenceManager.sharedPreferences?.edit()?.putBoolean(PREF_DEV_MODE, false)?.apply()
        updateDevModeVisibility()
        Toast.makeText(requireContext(), R.string.dev_mode_disabled, Toast.LENGTH_LONG).show()
    }

    private fun buildShortBuildSummary(): String {
        val versionName = try {
            requireContext().packageManager
                .getPackageInfo(requireContext().packageName, 0).versionName
        } catch (_: PackageManager.NameNotFoundException) {
            "?"
        }
        val buildType = if (isDebugBuild()) "debug" else "release"
        return "v$versionName · $buildType · API ${Build.VERSION.SDK_INT}"
    }

    private fun buildFullDebugInfo(): String = buildString {
        val packageInfo = try {
            requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
        appendLine("Svartifoss ${packageInfo?.versionName ?: "?"} (${formatVersionCode(packageInfo)})")
        appendLine("Build type: ${if (isDebugBuild()) "debug" else "release"}")
        appendLine("Package: ${requireContext().packageName}")
        appendLine("Android SDK: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.BRAND} ${Build.MODEL}")
        appendLine("Developer mode: $devModeEnabled")
    }

    private fun isDebugBuild(): Boolean {
        return (requireContext().applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    private fun formatVersionCode(packageInfo: android.content.pm.PackageInfo?): String {
        packageInfo ?: return "?"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode.toString()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toString()
        }
    }

    private fun copyDebugInfoToClipboard() {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Svartifoss debug info", buildFullDebugInfo().trim()))
        Toast.makeText(requireContext(), R.string.dev_copy_debug_info_done, Toast.LENGTH_SHORT).show()
    }

    private fun forceSyncWatchSettings() {
        lifecycleScope.launchWithPlayServicesErrorHandling(requireContext().applicationContext) {
            PreferencePusher.pushPreferences(
                requireContext().applicationContext,
                preferenceManager.sharedPreferences!!,
                CommPaths.PREFERENCES_PREFIX,
                true
            )
            Toast.makeText(requireContext(), R.string.dev_sync_watch_done, Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleVersionClick() {
        if (devModeEnabled) {
            showEasterEgg()
            return
        }

        versionClickCount++
        val remaining = DEV_CLICKS_REQUIRED - versionClickCount

        when {
            versionClickCount >= DEV_CLICKS_REQUIRED -> {
                devModeEnabled = true
                preferenceManager.sharedPreferences?.edit()?.putBoolean(PREF_DEV_MODE, true)?.apply()
                updateDevModeVisibility()
                Toast.makeText(requireContext(), R.string.dev_mode_unlocked, Toast.LENGTH_LONG).show()
                versionClickCount = 0
            }
            remaining <= 3 -> {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.dev_mode_clicks_remaining, remaining),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun updateDevModeVisibility() {
        findPreference<PreferenceCategory>("cat_developer")?.isVisible =
            devModeEnabled && section == SECTION_DATA
    }

    private fun showEasterEgg() {
        val view = layoutInflater.inflate(R.layout.dialog_svartifoss_easter_egg, null, false)
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.easter_egg_title)
            .setView(view)
            .setPositiveButton(R.string.easter_egg_ok, null)
            .show()
    }

    private fun sendLogs() {
        LogRetrievalTask(activity,
                CommPaths.MESSAGE_SEND_LOGS,
                "gabrielsvafoss@gmail.com",
                "com.svartifoss.snfell.logs").execute(null as Void?)
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (permissions.isNotEmpty() &&
                permissions[0] == Manifest.permission.WRITE_EXTERNAL_STORAGE &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            sendLogs()
        }
    }

    override fun onStart() {
        super.onStart()
        if (parentFragmentManager.findFragmentById(R.id.fragment_container) === this) {
            (activity as? TitledActivity)?.updateActivityTitle(getString(R.string.action_settings))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshAppsSection()
        refreshCommunityThemeAccountSummary()
    }

    override fun onStop() {
        pendingNoWatchBannerJob?.cancel()
        super.onStop()
    }
}
