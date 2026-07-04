package com.svartifoss.snfell.view.settings

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.DialogFragment
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
import com.svartifoss.snfell.util.launchWithPlayServicesErrorHandling
import com.svartifoss.snfell.view.TitledActivity
import com.matejdro.wearutils.logging.LogRetrievalTask
import com.matejdro.wearutils.preferences.compat.PreferenceFragmentCompatEx
import com.matejdro.wearutils.preferences.definition.Preferences
import com.matejdro.wearutils.preferencesync.PreferencePusher
import de.psdev.licensesdialog.LicensesDialog
import org.json.JSONObject
import timber.log.Timber


class MiscSettingsFragment : PreferenceFragmentCompatEx(), SharedPreferences.OnSharedPreferenceChangeListener {
    companion object {
        private const val PREF_DEV_MODE = "developer_mode_enabled"
        private const val DEV_CLICKS_REQUIRED = 7
    }

    private var versionClickCount = 0
    private var devModeEnabled = false

    private val exportConfigLauncher = registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { exportConfigTo(it) } }

    private val importConfigLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { importConfigFrom(it) } }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.settings)

        devModeEnabled = preferenceManager.sharedPreferences?.getBoolean(PREF_DEV_MODE, false) == true

        initAppearanceSection()
        initMusicScreenSection()
        initScreenButtonAppearance()
        initAutomationSection()
        initBackupSection()
        initAboutSection()
        initDevSection()
        updateDevModeVisibility()
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        super.onDisplayPreferenceDialog(preference)
        // Preference dialogs (Theme, Album art style, Screen theme, ...) inflate with the
        // static theme colors; once the dialog is up, re-tint it with the accent currently
        // on screen.
        view?.post { tintOpenPreferenceDialog() }
    }

    private fun tintOpenPreferenceDialog(attempt: Int = 0) {
        // The dialog fragment show() is an async commit - force it through so the dialog
        // exists on the very first attempt instead of racing it.
        if (attempt == 0) {
            try {
                parentFragmentManager.executePendingTransactions()
            } catch (ignored: IllegalStateException) {
            }
        }

        // Standard preferences use androidx's tag, PreferenceFragmentCompatEx's custom
        // dialogs use its own legacy tag - check both.
        val dialogFragment = (parentFragmentManager.findFragmentByTag(
                "androidx.preference.PreferenceFragment.DIALOG")
                ?: parentFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG))
                as? DialogFragment
        val dialog = dialogFragment?.dialog as? AlertDialog

        // Even forced, the dialog can lag a frame behind (its window shows after onStart) -
        // retry over the next frames rather than silently leaving the whole dialog, radio
        // marks included, in the static theme color.
        if (dialog == null || !dialog.isShowing) {
            if (attempt < 20) {
                view?.postDelayed({ tintOpenPreferenceDialog(attempt + 1) }, 50L)
            }
            return
        }

        // The live activity accent (dynamic album-art color included) - LyraAccent.resolve
        // reads a persisted snapshot that can lag behind what's actually on screen.
        val accent = (activity as? com.svartifoss.snfell.view.mainactivity.MainActivity)
                ?.currentAccentColor()
                ?: com.svartifoss.snfell.view.LyraAccent.resolve(requireContext())
        val secondary = androidx.core.content.ContextCompat.getColor(
                requireContext(), R.color.lyra_text_secondary)

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(accent)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(secondary)
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(secondary)

        // Single/multi-choice rows are CheckedTextViews whose radio/check mark comes tinted
        // with the static colorControlActivated; re-tint them, including rows (re)bound
        // later while scrolling.
        val listView = dialog.listView ?: return
        val markTint = android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(accent, secondary)
        )
        fun tintRow(row: android.view.View) {
            val checkedText = row as? android.widget.CheckedTextView ?: return
            checkedText.checkMarkTintList = markTint
            // Some AppCompat versions render the radio as a compound drawable instead of the
            // check mark - tint both so the accent applies regardless.
            androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(checkedText, markTint)
        }
        for (i in 0 until listView.childCount) {
            tintRow(listView.getChildAt(i))
        }
        listView.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
            override fun onChildViewAdded(parent: android.view.View, child: android.view.View) =
                    tintRow(child)

            override fun onChildViewRemoved(parent: android.view.View, child: android.view.View) = Unit
        })
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

        showColorPickerDialog(
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

    private fun parseHexOrDefault(hex: String?): Int = if (hex != null) {
        try { Color.parseColor(hex) } catch (e: Exception) { 0xFF86A69D.toInt() }
    } else 0xFF86A69D.toInt()

    /** Shared HSV color picker dialog (accent color, mini buttons color, ...): live preview
     *  swatch on top, Reset/Cancel/Apply buttons tinted with the runtime accent. */
    private fun showColorPickerDialog(initialColor: Int, onReset: () -> Unit, onApply: (String) -> Unit) {
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density

        // Live preview swatch
        val previewSwatch = android.view.View(ctx).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp * 8f
                setColor(initialColor)
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (dp * 44f).toInt()
            ).also { it.bottomMargin = (dp * 12f).toInt() }
        }

        val picker = HSVColorPickerView(ctx).apply {
            setColor(initialColor)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (dp * 260f).toInt()
            )
        }

        var selectedColor = initialColor
        picker.onColorChanged = { color ->
            selectedColor = color
            (previewSwatch.background as? GradientDrawable)?.setColor(color)
        }

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (dp * 20f).toInt()
            setPadding(pad, (dp * 12f).toInt(), pad, (dp * 8f).toInt())
            addView(previewSwatch)
            addView(picker)
        }

        val dialog = AlertDialog.Builder(ctx)
            .setTitle(R.string.color_picker_title)
            .setView(root)
            .setNeutralButton(R.string.color_picker_reset) { _, _ -> onReset() }
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.color_picker_apply) { _, _ ->
                onApply(String.format("#%06X", 0xFFFFFF and selectedColor))
            }
            .show()

        // Same runtime-accent treatment the preference dialogs get in
        // tintOpenPreferenceDialog - this dialog is built directly, so tint it here.
        val accent = (activity as? com.svartifoss.snfell.view.mainactivity.MainActivity)
                ?.currentAccentColor()
                ?: com.svartifoss.snfell.view.LyraAccent.resolve(ctx)
        val secondary = androidx.core.content.ContextCompat.getColor(
                ctx, R.color.lyra_text_secondary)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(accent)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(secondary)
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(secondary)
    }

    /** Mini-button appearance prefs (Wear experience): custom color picker wiring plus
     *  enabling/disabling the color-mode-dependent rows. */
    private fun initScreenButtonAppearance() {
        val colorPref = findPreference<Preference>("screen_buttons_custom_color")
        updateScreenButtonColorSummary(colorPref)
        colorPref?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            val prefs = preferenceManager.sharedPreferences
                    ?: return@OnPreferenceClickListener true
            showColorPickerDialog(
                    initialColor = parseHexOrDefault(prefs.getString("screen_buttons_custom_color", null)),
                    onReset = {
                        prefs.edit().remove("screen_buttons_custom_color").apply()
                        updateScreenButtonColorSummary(colorPref)
                    },
                    onApply = { hex ->
                        prefs.edit().putString("screen_buttons_custom_color", hex).apply()
                        updateScreenButtonColorSummary(colorPref)
                    }
            )
            true
        }

        updateScreenButtonColorDependencies()
        findPreference<ListPreference>("screen_buttons_color_mode")?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                updateScreenButtonColorDependencies(newValue as? String)
                true
            }
    }

    private fun updateScreenButtonColorSummary(pref: Preference?) {
        pref ?: return
        val saved = preferenceManager.sharedPreferences?.getString("screen_buttons_custom_color", null)
        pref.summary = if (saved != null) {
            getString(R.string.color_picker_current, saved)
        } else {
            getString(R.string.setting_screen_buttons_custom_color_description)
        }
        (pref as? HexColorDotPreference)?.refreshDot()
    }

    private fun updateScreenButtonColorDependencies(overrideMode: String? = null) {
        val mode = overrideMode ?: findPreference<ListPreference>("screen_buttons_color_mode")?.value
        findPreference<Preference>("screen_buttons_custom_color")?.isEnabled = mode == "custom"
        findPreference<Preference>("screen_buttons_desaturated")?.isEnabled = mode == "album"
    }

    private fun applyTheme(value: String) {
        val mode = when (value) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    private fun initMusicScreenSection() {
        updateBlurRadiusEnabled()

        findPreference<ListPreference>("album_art_style")?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                // The listener fires before the new value persists, so pass it along instead
                // of re-reading the (still old) preference.
                updateBlurRadiusEnabled(newValue as? String)
                true
            }
    }

    private fun updateBlurRadiusEnabled(overrideValue: String? = null) {
        val value = overrideValue ?: findPreference<ListPreference>("album_art_style")?.value
        val blurStyle = value == "blur" || value == "blur_bw"
        findPreference<Preference>("album_art_blur_radius")?.isEnabled = blurStyle
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

            AlertDialog.Builder(requireContext())
                .setTitle(R.string.import_config_done_title)
                .setMessage(R.string.import_config_done_message)
                .setCancelable(false)
                .setPositiveButton(R.string.action_restart_now) { _, _ -> restartApp() }
                .show()
        } catch (e: Exception) {
            Timber.e(e, "Config import failed")
            Toast.makeText(requireContext(), R.string.import_config_failed, Toast.LENGTH_LONG).show()
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
                "apps@matejdro.com",
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
                    false
            )
        }
    }
}
