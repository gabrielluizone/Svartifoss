package com.svartifoss.snfell.view.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
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
import com.svartifoss.snfell.common.model.AutoStartMode
import com.svartifoss.snfell.config.ConfigBackup
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
import com.matejdro.wearutils.logging.LogRetrievalTask
import com.matejdro.wearutils.preferences.compat.PreferenceFragmentCompatEx
import com.matejdro.wearutils.preferences.definition.Preferences
import com.matejdro.wearutils.preferencesync.PreferencePusher
import com.google.android.gms.wearable.Wearable
import dagger.android.support.AndroidSupportInjection
import de.psdev.licensesdialog.LicensesDialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
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

        private const val DEVELOPER_GITHUB_URL = "https://github.com/gabrielluizone"

        fun newInstance(section: String) = MiscSettingsFragment().apply {
            arguments = Bundle().apply { putString(ARG_SECTION, section) }
        }
    }

    private var versionClickCount = 0
    private var devModeEnabled = false
    private var section = SECTION_GENERAL

    @Inject
    lateinit var watchInfoProvider: WatchInfoProvider

    private val exportConfigLauncher = registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { exportConfigTo(it) } }

    private val importConfigLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { importConfigFrom(it) } }

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
        initNavigationLinks()
        initAppsSection()
        initAutomationSection()
        initBackupSection()
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

        val visibleCategories = when (section) {
            SECTION_WATCH -> setOf("cat_gestures", "cat_action_list", "cat_notifications")
            SECTION_AUTOMATION -> setOf("cat_automation")
            SECTION_APPS -> setOf("cat_apps")
            SECTION_DATA -> setOf("cat_backup", "cat_privacy", "cat_about")
            else -> setOf("cat_updates", "cat_appearance")
        }

        listOf(
            "cat_updates",
            "cat_appearance",
            "cat_gestures",
            "cat_action_list",
            "cat_notifications",
            "cat_automation",
            "cat_apps",
            "cat_backup",
            "cat_privacy",
            "cat_about"
        ).forEach { key ->
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
                exportConfigLauncher.launch("svartifoss_config.json")
                true
            }

        findPreference<Preference>("importConfig")!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                importConfigLauncher.launch(arrayOf("application/json", "*/*"))
                true
            }
    }

    private fun exportConfigTo(uri: Uri) {
        try {
            val json = ConfigBackup.export(requireContext(), preferenceManager.sharedPreferences!!)
            requireContext().contentResolver.openOutputStream(uri)?.use { out ->
                out.write(json.toString(2).toByteArray(Charsets.UTF_8))
            } ?: throw java.io.IOException("Could not open output stream")

            Toast.makeText(requireContext(), R.string.export_config_done, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Timber.e(e, "Config export failed")
            Toast.makeText(requireContext(), R.string.export_config_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun importConfigFrom(uri: Uri) {
        try {
            val text = requireContext().contentResolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            } ?: throw java.io.IOException("Could not open input stream")

            ConfigBackup.import(requireContext(), preferenceManager.sharedPreferences!!, JSONObject(text))
        } catch (e: Exception) {
            Timber.e(e, "Config import failed")
            Toast.makeText(requireContext(), R.string.import_config_failed, Toast.LENGTH_LONG).show()
            return
        }

        // The button/action-list transmitters only push to the watch when their DataItem is
        // missing (ButtonConfigTransmitter/ActionListTransmitter.resendIfNeeded). After an import
        // the items still hold the OLD config, so the watch would keep the old buttons until each
        // config was hand-edited and re-saved. Clear them so the post-restart transmitters re-push
        // the freshly imported config. Wait for the clear to finish before offering the restart so
        // it isn't cut short by restartApp()'s process exit; a missing/disconnected watch no-ops.
        val appContext = requireContext().applicationContext
        lifecycleScope.launch {
            try {
                clearWatchConfigDataItems(appContext)
            } catch (e: Exception) {
                Timber.w(e, "Could not clear watch config DataItems after import")
            }

            AlertDialog.Builder(requireContext())
                .setTitle(R.string.import_config_done_title)
                .setMessage(R.string.import_config_done_message)
                .setCancelable(false)
                .setPositiveButton(R.string.action_restart_now) { _, _ -> restartApp() }
                .show()
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
                AlertDialog.Builder(requireContext())
                        .setTitle(R.string.about_developer_dialog_title)
                        .setMessage(R.string.about_developer_dialog_message)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
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

        findPreference<Preference>("dev_disable_mode")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                disableDevMode()
                true
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
                "gabrielluizone@gmail.com",
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
    }

    override fun onStop() {
        pendingNoWatchBannerJob?.cancel()
        super.onStop()
    }
}
