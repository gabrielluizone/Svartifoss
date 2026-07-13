package com.svartifoss.snfell.config

import android.content.Context
import android.preference.PreferenceManager
import androidx.lifecycle.Observer
import com.svartifoss.snfell.R
import com.svartifoss.snfell.config.actionlist.ActionList
import com.svartifoss.snfell.config.actionlist.GlobalActionList
import com.svartifoss.snfell.config.buttons.ButtonConfig
import com.svartifoss.snfell.config.buttons.GlobalButtonConfigFactory
import dagger.Lazy
import org.json.JSONObject
import timber.log.Timber
import java.io.File

class GlobalActionConfig(private val context: Context,
                         private val watchInfoProvider: WatchInfoProvider,
                         private val defaultConfigGenerator: DefaultConfigGenerator,
                         actionListConfigLazy: Lazy<GlobalActionList>,
                         globalButtonConfigFactory: GlobalButtonConfigFactory) : ActionConfig {

    private val playingConfig by lazy { globalButtonConfigFactory.create(true) }
    private val stoppedConfig by lazy { globalButtonConfigFactory.create(false) }
    private val actionListConfig by lazy { actionListConfigLazy.get() }

    override fun getPlayingConfig(): ButtonConfig = playingConfig
    override fun getStoppedConfig(): ButtonConfig = stoppedConfig
    override fun getActionList(): ActionList = actionListConfig

    private fun doesConfigExist(): Boolean =
            File(context.filesDir, "action_config_playing").exists() &&
                    File(context.filesDir, "action_config_stopped").exists() &&
                    File(context.filesDir, "actions_list").exists()

    private val defaultConfigCreatorListener = object : Observer<WatchInfoWithIcons?> {
        override fun onChanged(t: WatchInfoWithIcons?) {
            watchInfoProvider.removeObserver(this)
            defaultConfigGenerator.generateDefaultButtons(this@GlobalActionConfig)
            defaultConfigGenerator.generateDefaultActionList(getActionList())

            playingConfig.commit()
            stoppedConfig.commit()
            actionListConfig.commit()
        }
    }

    /**
     * Seeds a fresh install with the bundled [R.raw.default_config] snapshot (the same format
     * [ConfigBackup] exports/imports) instead of [defaultConfigGenerator]'s auto-detected
     * defaults - this app is a personal sideload, not a general Play Store release, so "default"
     * means "my own saved setup" rather than a generic first-run guess.
     *
     * Writes straight to the on-disk config files ([ConfigBackup.import]) before [playingConfig] /
     * [stoppedConfig] / [actionListConfig] are ever lazily created, so - unlike a manual import
     * from Settings - no app restart is needed: their own `init` blocks load these files the first
     * time anything calls [getPlayingConfig] / [getStoppedConfig] / [getActionList].
     */
    private fun seedBundledDefaultConfig() {
        try {
            val json = context.resources.openRawResource(R.raw.default_config).use {
                JSONObject(it.readBytes().decodeToString())
            }
            ConfigBackup.import(context, PreferenceManager.getDefaultSharedPreferences(context), json)
        } catch (e: Exception) {
            // Expected on devices whose Android version can't decode the bundled parcel snapshot -
            // the fallback below fully handles it, so log at WARN (no Crashlytics issue) not ERROR.
            Timber.w(e, "Failed seeding the bundled default config - falling back to auto-detected defaults")
            watchInfoProvider.observeForever(defaultConfigCreatorListener)
        }
    }

    init {
        if (!doesConfigExist()) {
            seedBundledDefaultConfig()
        }
    }

}
