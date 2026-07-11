package com.svartifoss.snfell.view.settings

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
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


class MiscSettingsFragment : PreferenceFragmentCompatEx(), SharedPreferences.OnSharedPreferenceChangeListener {
    companion object {
        private const val PREF_DEV_MODE = "developer_mode_enabled"
        private const val DEV_CLICKS_REQUIRED = 7
    }

    private var versionClickCount = 0
    private var devModeEnabled = false

    @Inject
    lateinit var watchInfoProvider: WatchInfoProvider

    private val exportConfigLauncher = registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { exportConfigTo(it) } }

    private val importConfigLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { importConfigFrom(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
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
        pendingNoWatchBannerJob?.cancel()
        if (watchInfo != null) {
            findPreference<Preference>("no_watch_banner")?.isVisible = false
        } else {
            // WatchInfoProvider's LiveData starts out null and only resolves asynchronously
            // (it queries the Data Layer on first observe), so a connected watch still causes
            // one initial null emission - showing the banner immediately made it flash on and
            // right back off on every visit. Debounce so it only shows if still disconnected
            // after a beat.
            pendingNoWatchBannerJob = lifecycleScope.launch {
                delay(600)
                findPreference<Preference>("no_watch_banner")?.isVisible = true
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.settings)

        devModeEnabled = preferenceManager.sharedPreferences?.getBoolean(PREF_DEV_MODE, false) == true

        initAppearanceSection()
        initAutomationSection()
        initBackupSection()
        initAboutSection()
        initDevSection()
        updateDevModeVisibility()
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

        val accentPref = findPreference<Preference>("custom_accent_color")
        updateAccentColorSummary(accentPref)
        accentPref?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            showAccentColorPicker(accentPref)
            true
        }

        findPreference<Preference>("playlist_shortcuts")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                startActivity(
                    android.content.Intent(requireContext(), PlaylistShortcutsActivity::class.java)
                )
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

    private fun initAutomationSection() {
        migrateOldAutoStartSetting()

        findPreference<Preference>("auto_start_apps_blacklist")?.isEnabled =
                Preferences.getEnum(preferenceManager.sharedPreferences, MiscPreferences.AUTO_START_MODE) != AutoStartMode.OFF

        findPreference<Preference>("auto_start_mode")!!.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    newValue as String
                    val mode = enumValueOf<AutoStartMode>(newValue)
                    findPreference<Preference>("auto_start_apps_blacklist")?.isEnabled = mode != AutoStartMode.OFF

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        if (newValue != AutoStartMode.OFF) {
                            android.service.notification.NotificationListenerService.requestRebind(
                                    ComponentName(requireContext(), NotificationService::class.java)
                            )
                        } else {
                            val serviceStopIntent = Intent(requireContext(), NotificationService::class.java)
                            serviceStopIntent.action = NotificationService.ACTION_UNBIND_SERVICE
                            requireContext().startService(serviceStopIntent)
                        }
                    }
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

        findPreference<Preference>("licenses")!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                LicensesDialog.Builder(activity)
                        .setNotices(R.raw.notices)
                        .setIncludeOwnLicense(true)
                        .build()
                        .show()
                true
            }
    }

    private fun initDevSection() {
        val buildPref = findPreference<Preference>("dev_build_info")
        buildPref?.summary = buildShortBuildSummary()
        buildPref?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            showBuildInfoDialog()
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

    private fun showBuildInfoDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dev_build_info_dialog_title)
            .setMessage(buildFullDebugInfo().trim())
            .setPositiveButton(android.R.string.ok, null)
            .show()
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
        findPreference<PreferenceCategory>("cat_developer")?.isVisible = devModeEnabled
    }

    private fun showEasterEgg() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.easter_egg_title)
            .setMessage(R.string.easter_egg_message)
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
        if (parentFragmentManager.findFragmentById(R.id.fragment_container) !== this) return

        (activity as? TitledActivity)?.updateActivityTitle(getString(R.string.action_settings))
        preferenceManager.sharedPreferences!!.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onStop() {
        super.onStop()
        preferenceManager.sharedPreferences!!.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "app_theme") return
        pushPreferencesToWatch()
    }

    private fun pushPreferencesToWatch() {
        lifecycleScope.launchWithPlayServicesErrorHandling(requireContext().applicationContext) {
            PreferencePusher.pushPreferences(
                    requireContext().applicationContext,
                    preferenceManager.sharedPreferences!!,
                    CommPaths.PREFERENCES_PREFIX,
                    // Urgent: the user just toggled this in settings and expects the watch to
                    // reflect it now. Non-urgent DataItems get batched and could otherwise take
                    // minutes to sync (until unrelated urgent traffic flushed them) - which is
                    // exactly why a changed watch setting like the album-art blur appeared to
                    // apply only after tapping/turning the watch.
                    true
            )
        }
    }
}
