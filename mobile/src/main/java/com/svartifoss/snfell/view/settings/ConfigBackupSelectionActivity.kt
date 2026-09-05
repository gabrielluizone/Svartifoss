package com.svartifoss.snfell.view.settings

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.svartifoss.snfell.R
import com.svartifoss.snfell.config.ConfigBackup
import com.svartifoss.snfell.config.ConfigBackupSection
import com.svartifoss.snfell.view.LyraAccent
import java.io.IOException
import timber.log.Timber

/**
 * Lets the user choose which persisted parts of Svartifoss should be written to a backup file.
 *
 * This is a separate screen so the list remains readable as the backup grows. Every item is
 * selected initially: choosing Export without changing anything still produces a complete backup.
 */
class ConfigBackupSelectionActivity : AppCompatActivity() {

    companion object {
        private const val STATE_SELECTED_SECTIONS = "selectedBackupSections"
    }

    private val selectedSections = LinkedHashSet<ConfigBackupSection>()
    private val checkboxes = LinkedHashMap<ConfigBackupSection, CheckBox>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config_backup_selection)

        restoreSelection(savedInstanceState)
        stylePrimaryButton(findViewById(R.id.button_export))

        val accent = LyraAccent.resolve(this)
        findViewById<ImageButton>(R.id.button_back).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.button_select_all).apply {
            setTextColor(accent)
            setOnClickListener { setAllSelected(true) }
        }
        findViewById<MaterialButton>(R.id.button_clear_all).apply {
            setTextColor(accent)
            setOnClickListener { setAllSelected(false) }
        }
        findViewById<MaterialButton>(R.id.button_export).setOnClickListener {
            if (selectedSections.isEmpty()) {
                Toast.makeText(this, R.string.backup_selection_empty, Toast.LENGTH_SHORT).show()
            } else {
                exportLauncher.launch("svartifoss_config.json")
            }
        }

        val container = findViewById<LinearLayout>(R.id.section_container)
        ConfigBackupSection.values().forEach { section ->
            val item = LayoutInflater.from(this)
                    .inflate(R.layout.item_config_backup_section, container, false)
                    as MaterialCardView
            val checkBox = item.findViewById<CheckBox>(R.id.section_checkbox)
            item.findViewById<TextView>(R.id.section_title).setText(titleFor(section))
            item.findViewById<TextView>(R.id.section_description).setText(descriptionFor(section))
            checkBox.buttonTintList = ColorStateList.valueOf(LyraAccent.resolve(this))
            checkBox.isChecked = section in selectedSections
            checkBox.setOnCheckedChangeListener { _, checked ->
                if (checked) selectedSections.add(section) else selectedSections.remove(section)
            }
            item.setOnClickListener { checkBox.isChecked = !checkBox.isChecked }
            checkboxes[section] = checkBox
            container.addView(item)
        }
    }

    private val exportLauncher = registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(::exportTo) }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putStringArrayList(
                STATE_SELECTED_SECTIONS,
                ArrayList(selectedSections.map { it.id }))
        super.onSaveInstanceState(outState)
    }

    private fun restoreSelection(savedInstanceState: Bundle?) {
        val savedIds = savedInstanceState?.getStringArrayList(STATE_SELECTED_SECTIONS)
        if (savedIds == null) {
            selectedSections.addAll(ConfigBackupSection.ALL)
        } else {
            savedIds.mapNotNull { ConfigBackupSection.fromId(it) }
                    .forEach(selectedSections::add)
        }
    }

    private fun setAllSelected(checked: Boolean) {
        if (checked) selectedSections.addAll(ConfigBackupSection.ALL)
        else selectedSections.clear()
        checkboxes.values.forEach { it.isChecked = checked }
    }

    private fun exportTo(uri: android.net.Uri) {
        try {
            val json = ConfigBackup.export(
                    this,
                    PreferenceManager.getDefaultSharedPreferences(this),
                    selectedSections)
            contentResolver.openOutputStream(uri)?.use { output ->
                output.write(json.toString(2).toByteArray(Charsets.UTF_8))
            } ?: throw IOException("Could not open output stream")

            Toast.makeText(this, R.string.export_config_done, Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK)
            finish()
        } catch (e: Exception) {
            Timber.e(e, "Config export failed")
            val reason = e.message
            val message = if (reason.isNullOrBlank()) {
                getString(R.string.export_config_failed)
            } else {
                getString(R.string.export_config_failed_detail, reason)
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun stylePrimaryButton(button: MaterialButton) {
        val accent = LyraAccent.resolve(this)
        button.backgroundTintList = ColorStateList.valueOf(accent)
        button.setTextColor(LyraAccent.foregroundFor(accent))
        button.iconTint = ColorStateList.valueOf(LyraAccent.foregroundFor(accent))
    }

    private fun titleFor(section: ConfigBackupSection): Int = when (section) {
        ConfigBackupSection.BUTTONS -> R.string.backup_section_buttons
        ConfigBackupSection.ACTIONS -> R.string.backup_section_actions
        ConfigBackupSection.APP_SETTINGS -> R.string.backup_section_app_settings
        ConfigBackupSection.WATCH_APPEARANCE -> R.string.backup_section_watch_appearance
        ConfigBackupSection.PLAYLIST_SHORTCUTS -> R.string.backup_section_playlist_shortcuts
        ConfigBackupSection.HISTORY -> R.string.backup_section_history
        ConfigBackupSection.ICONS -> R.string.backup_section_icons
        ConfigBackupSection.PRIVACY -> R.string.backup_section_privacy
        ConfigBackupSection.LOCAL_APP_STATE -> R.string.backup_section_local_app_state
        ConfigBackupSection.AUXILIARY_DATA -> R.string.backup_section_auxiliary_data
    }

    private fun descriptionFor(section: ConfigBackupSection): Int = when (section) {
        ConfigBackupSection.BUTTONS -> R.string.backup_section_buttons_description
        ConfigBackupSection.ACTIONS -> R.string.backup_section_actions_description
        ConfigBackupSection.APP_SETTINGS -> R.string.backup_section_app_settings_description
        ConfigBackupSection.WATCH_APPEARANCE -> R.string.backup_section_watch_appearance_description
        ConfigBackupSection.PLAYLIST_SHORTCUTS -> R.string.backup_section_playlist_shortcuts_description
        ConfigBackupSection.HISTORY -> R.string.backup_section_history_description
        ConfigBackupSection.ICONS -> R.string.backup_section_icons_description
        ConfigBackupSection.PRIVACY -> R.string.backup_section_privacy_description
        ConfigBackupSection.LOCAL_APP_STATE -> R.string.backup_section_local_app_state_description
        ConfigBackupSection.AUXILIARY_DATA -> R.string.backup_section_auxiliary_data_description
    }
}
