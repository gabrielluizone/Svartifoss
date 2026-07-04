package com.svartifoss.snfell.config

import android.content.Context
import com.svartifoss.snfell.actions.appplay.AppPlayAction
import com.svartifoss.snfell.actions.appplay.AppPlayPickerAction
import com.svartifoss.snfell.actions.playback.PauseAction
import com.svartifoss.snfell.actions.playback.PlayAction
import com.svartifoss.snfell.actions.playback.SkipToNextAction
import com.svartifoss.snfell.actions.playback.SkipToPrevAction
import com.svartifoss.snfell.actions.volume.DecreaseVolumeAction
import com.svartifoss.snfell.actions.volume.IncreaseVolumeAction
import com.svartifoss.snfell.common.ScreenQuadrant
import com.svartifoss.snfell.common.buttonconfig.ButtonInfo
import com.svartifoss.snfell.common.buttonconfig.GESTURE_DOUBLE_TAP
import com.svartifoss.snfell.common.buttonconfig.GESTURE_SINGLE_TAP
import com.svartifoss.snfell.common.buttonconfig.SpecialButtonCodes
import com.svartifoss.snfell.config.actionlist.ActionList
import javax.inject.Inject

class DefaultConfigGenerator @Inject constructor(private val context : Context,
                                                 private val watchInfoProvider: WatchInfoProvider) {

    fun generateDefaultButtons(actionConfig: ActionConfig) {
        val playingConfig = actionConfig.getPlayingConfig()
        val stoppedConfig = actionConfig.getStoppedConfig()

        playingConfig.saveButtonAction(
                ButtonInfo(false, ScreenQuadrant.TOP, GESTURE_SINGLE_TAP),
                IncreaseVolumeAction(context))
        playingConfig.saveButtonAction(
                ButtonInfo(false, ScreenQuadrant.BOTTOM, GESTURE_SINGLE_TAP),
                DecreaseVolumeAction(context))

        playingConfig.saveButtonAction(
                ButtonInfo(false, ScreenQuadrant.LEFT, GESTURE_SINGLE_TAP),
                PauseAction(context))
        playingConfig.saveButtonAction(
                ButtonInfo(false, ScreenQuadrant.LEFT, GESTURE_DOUBLE_TAP),
                SkipToPrevAction(context))

        playingConfig.saveButtonAction(
                ButtonInfo(false, ScreenQuadrant.RIGHT, GESTURE_SINGLE_TAP),
                SkipToNextAction(context))

        stoppedConfig.saveButtonAction(
                ButtonInfo(false, ScreenQuadrant.LEFT, GESTURE_SINGLE_TAP),
                PlayAction(context))

        val musicApps = AppPlayPickerAction.getAllMusicApps(context)
        musicApps
                .take(3)
                .forEachIndexed { index, componentName ->
                    stoppedConfig.saveButtonAction(
                            ButtonInfo(false, ScreenQuadrant.TOP + index, GESTURE_SINGLE_TAP),
                            AppPlayAction(context, componentName))

                }

        val watchInfo = watchInfoProvider.value
        val buttons = watchInfo?.watchInfo?.buttonsList ?: emptyList()
        val numPhysicalButtons = buttons.size

        if (numPhysicalButtons > 1) {
            val lastButton = numPhysicalButtons - 1
            // Last button is usually the bottom one - assign play action to it
            playingConfig.saveButtonAction(
                    ButtonInfo(true, lastButton, GESTURE_SINGLE_TAP),
                    PauseAction(context))
            stoppedConfig.saveButtonAction(
                    ButtonInfo(true, lastButton, GESTURE_SINGLE_TAP),
                    PlayAction(context))

            // Assign "next track" action to the first button
            playingConfig.saveButtonAction(
                    ButtonInfo(true, 0, GESTURE_SINGLE_TAP),
                    SkipToNextAction(context))
        } else if (numPhysicalButtons > 0) {
            // We only have one button. Assign play/pause to it.
            playingConfig.saveButtonAction(
                    ButtonInfo(true, 0, GESTURE_SINGLE_TAP),
                    PauseAction(context))
            stoppedConfig.saveButtonAction(
                    ButtonInfo(true, 0, GESTURE_SINGLE_TAP),
                    PlayAction(context))
        }

        if (buttons.any { it.code == SpecialButtonCodes.TURN_ROTARY_CW}) {
            playingConfig.saveButtonAction(
                    ButtonInfo(true, SpecialButtonCodes.TURN_ROTARY_CW, GESTURE_SINGLE_TAP),
                    IncreaseVolumeAction(context))
            playingConfig.saveButtonAction(
                    ButtonInfo(true, SpecialButtonCodes.TURN_ROTARY_CCW, GESTURE_SINGLE_TAP),
                    DecreaseVolumeAction(context))

        }

    }

    fun generateDefaultActionList(actionList: ActionList) {
        actionList.actions =
                AppPlayPickerAction.getAllMusicApps(context)
                        .map { AppPlayAction(context, it) }
    }
}