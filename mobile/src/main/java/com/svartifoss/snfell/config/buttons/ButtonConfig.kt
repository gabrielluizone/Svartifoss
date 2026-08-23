package com.svartifoss.snfell.config.buttons

import android.content.Context
import com.svartifoss.snfell.actions.NullAction
import com.svartifoss.snfell.actions.PhoneAction
import com.svartifoss.snfell.common.buttonconfig.ButtonInfo

interface ButtonConfig {
    fun saveButtonAction(buttonInfo: ButtonInfo, action : PhoneAction?)
    fun getScreenAction(buttonInfo: ButtonInfo) : PhoneAction?
    fun getAllActions() : Collection<Map.Entry<ButtonInfo, PhoneAction>>

    fun commit()

    /** Re-send the current assignments to the watch without writing them to disk - see
     *  [com.svartifoss.snfell.config.actionlist.ActionList.retransmit]. */
    fun retransmit()
}

fun ButtonConfig.getScreenActionFallback(context: Context, buttonInfo: ButtonInfo): PhoneAction {
    val action = getScreenAction(buttonInfo)
    return action ?: NullAction(context)
}