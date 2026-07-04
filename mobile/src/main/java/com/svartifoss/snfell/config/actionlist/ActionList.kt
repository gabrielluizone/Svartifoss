package com.svartifoss.snfell.config.actionlist

import com.svartifoss.snfell.actions.PhoneAction

interface ActionList {
    var actions: List<PhoneAction>

    fun commit()
}