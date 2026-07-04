package com.svartifoss.snfell.config

import com.svartifoss.snfell.config.actionlist.ActionList
import com.svartifoss.snfell.config.buttons.ButtonConfig

interface ActionConfig {
    fun getPlayingConfig(): ButtonConfig
    fun getStoppedConfig(): ButtonConfig
    fun getActionList(): ActionList
}