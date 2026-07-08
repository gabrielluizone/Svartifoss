package com.svartifoss.snfell.view.watchface

import android.content.SharedPreferences
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.Preference
import com.svartifoss.snfell.R
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.util.launchWithPlayServicesErrorHandling
import com.svartifoss.snfell.view.settings.HexColorDotPreference
import com.svartifoss.snfell.view.settings.parseHexOrDefault
import com.svartifoss.snfell.view.settings.showLyraColorPickerDialog
import com.svartifoss.snfell.view.settings.tintOpenLyraPreferenceDialog
import com.matejdro.wearutils.preferences.compat.PreferenceFragmentCompatEx
import com.matejdro.wearutils.preferencesync.PreferencePusher

/**
 * The preference list half of the "Watch face" tab (see [WatchFaceFragment]): every setting
 * that changes how the watch's now-playing screen looks, all previewed live by the
 * [WatchPreviewView] docked above. The keys here were moved out of MiscSettingsFragment's
 * settings.xml - behavior settings (gestures, crown, automation, ...) stayed there.
 */
class WatchFacePrefsFragment : PreferenceFragmentCompatEx(),
        SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.watch_face_settings)

        initBlurRadiusDependency()
        initAccentColorTarget(
                modeKey = "wear_artist_color_mode",
                customColorKey = "wear_artist_custom_color",
                desaturatedKey = "wear_artist_desaturated",
                customColorDescription = R.string.setting_wear_artist_custom_color_description
        )
        initAccentColorTarget(
                modeKey = "wear_progress_color_mode",
                customColorKey = "wear_progress_custom_color",
                desaturatedKey = "wear_progress_desaturated",
                customColorDescription = R.string.setting_wear_progress_custom_color_description
        )
        initScreenButtonAppearance()
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        super.onDisplayPreferenceDialog(preference)
        // Preference dialogs inflate with the static theme colors; once the dialog is up,
        // re-tint it with the accent currently on screen.
        view?.post { tintOpenLyraPreferenceDialog() }
    }

    override fun onStart() {
        super.onStart()
        preferenceManager.sharedPreferences!!.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onStop() {
        super.onStop()
        preferenceManager.sharedPreferences!!.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        pushPreferencesToWatch()
    }

    private fun pushPreferencesToWatch() {
        lifecycleScope.launchWithPlayServicesErrorHandling(requireContext().applicationContext) {
            PreferencePusher.pushPreferences(
                    requireContext().applicationContext,
                    preferenceManager.sharedPreferences!!,
                    CommPaths.PREFERENCES_PREFIX,
                    // Urgent, same as MiscSettingsFragment: the user is watching the live
                    // preview and expects the watch to match it right away.
                    true
            )
        }
    }

    private fun initBlurRadiusDependency() {
        updateBlurRadiusEnabled()
        findPreference<ListPreference>("album_art_style")?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    // The listener fires before the new value persists, so pass it along
                    // instead of re-reading the (still old) preference.
                    updateBlurRadiusEnabled(newValue as? String)
                    true
                }
    }

    private fun updateBlurRadiusEnabled(overrideValue: String? = null) {
        val value = overrideValue ?: findPreference<ListPreference>("album_art_style")?.value
        val blurStyle = value == "blur" || value == "blur_bw"
        findPreference<Preference>("album_art_blur_radius")?.isEnabled = blurStyle
    }

    /** Custom color picker wiring plus enabling/disabling the color-mode-dependent rows, for
     *  one artist/progress color target. */
    private fun initAccentColorTarget(
            modeKey: String,
            customColorKey: String,
            desaturatedKey: String,
            customColorDescription: Int
    ) {
        val colorPref = findPreference<Preference>(customColorKey)
        updateAccentColorTargetSummary(colorPref, customColorKey, customColorDescription)
        colorPref?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            val prefs = preferenceManager.sharedPreferences
                    ?: return@OnPreferenceClickListener true
            showLyraColorPickerDialog(
                    initialColor = parseHexOrDefault(prefs.getString(customColorKey, null)),
                    onReset = {
                        prefs.edit().remove(customColorKey).apply()
                        updateAccentColorTargetSummary(colorPref, customColorKey, customColorDescription)
                    },
                    onApply = { hex ->
                        prefs.edit().putString(customColorKey, hex).apply()
                        updateAccentColorTargetSummary(colorPref, customColorKey, customColorDescription)
                    }
            )
            true
        }

        updateAccentColorTargetDependencies(modeKey, customColorKey, desaturatedKey)
        findPreference<ListPreference>(modeKey)?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    updateAccentColorTargetDependencies(modeKey, customColorKey, desaturatedKey, newValue as? String)
                    true
                }
    }

    private fun updateAccentColorTargetSummary(pref: Preference?, customColorKey: String, descriptionRes: Int) {
        pref ?: return
        val saved = preferenceManager.sharedPreferences?.getString(customColorKey, null)
        pref.summary = if (saved != null) {
            getString(R.string.color_picker_current, saved)
        } else {
            getString(descriptionRes)
        }
        (pref as? HexColorDotPreference)?.refreshDot()
    }

    private fun updateAccentColorTargetDependencies(
            modeKey: String,
            customColorKey: String,
            desaturatedKey: String,
            overrideMode: String? = null
    ) {
        val mode = overrideMode ?: findPreference<ListPreference>(modeKey)?.value
        findPreference<Preference>(customColorKey)?.isEnabled = mode == "custom"
        findPreference<Preference>(desaturatedKey)?.isEnabled = mode == "album"
    }

    /** Mini-button appearance: custom color picker wiring plus the color-mode dependencies. */
    private fun initScreenButtonAppearance() {
        val colorPref = findPreference<Preference>("screen_buttons_custom_color")
        updateScreenButtonColorSummary(colorPref)
        colorPref?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            val prefs = preferenceManager.sharedPreferences
                    ?: return@OnPreferenceClickListener true
            showLyraColorPickerDialog(
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
}
