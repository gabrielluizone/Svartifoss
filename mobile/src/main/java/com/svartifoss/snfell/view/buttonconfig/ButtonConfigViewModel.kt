package com.svartifoss.snfell.view.buttonconfig

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.svartifoss.snfell.config.ActionConfig
import com.svartifoss.snfell.config.WatchInfoProvider
import com.svartifoss.snfell.config.buttons.ButtonConfig
import com.svartifoss.snfell.di.LocalActivityConfig
import javax.inject.Inject
import javax.inject.Named

class ButtonConfigViewModel @Inject constructor(@Named(ARG_DISPLAY_PLAYBACK_ACTIONS) setsPlaybackActions: Boolean,
                                                val watchInfoProvider: WatchInfoProvider,
                                                @LocalActivityConfig val buttonConfigProvider: ActionConfig) : ViewModel() {

    val buttonConfig = MutableLiveData<ButtonConfig>()

    init {
        val config = if (setsPlaybackActions) {
            buttonConfigProvider.getPlayingConfig()
        } else {
            buttonConfigProvider.getStoppedConfig()
        }

        buttonConfig.value = config
    }

    fun commitConfig() {
        buttonConfig.value?.commit()
        buttonConfig.value = buttonConfig.value
    }

    companion object {
        const val ARG_DISPLAY_PLAYBACK_ACTIONS = "DisplayPlaybackActions"
    }
}
