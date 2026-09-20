package com.svartifoss.snfell.config

import com.svartifoss.snfell.actions.PhoneAction
import com.svartifoss.snfell.common.actions.StandardIcons

/**
 * Whether an action's icon has to travel to the watch, or whether the watch can draw it locally.
 *
 * The watch ships the same vector drawables the phone does and resolves one per *action key*, so
 * for most actions the phone sends nothing and saves a bitmap's worth of Bluetooth on every config
 * push. That optimisation is only sound while the key really does imply the drawable.
 *
 * It stopped being sound the moment an action's icon began depending on its own parameters:
 * `SetRepeatModeAction` draws the numbered repeat-one glyph for one of its three modes and the
 * plain one for the other two, while the watch's map has a single entry for the key and would draw
 * the plain glyph for all three. That is the whole of the reported "same setting, different icon".
 *
 * Expressed as a comparison rather than as a list of exceptional keys, so the next parameterised
 * action is covered by declaring [PhoneAction.defaultIconRes] and nothing else - and so the two
 * modes whose icon *is* the local vector keep skipping the transfer, which naming the key outright
 * would have stopped them doing.
 */
fun actionKeyOf(action: PhoneAction): String =
        action.javaClass.canonicalName ?: action.javaClass.name

fun needsTransmittedIcon(action: PhoneAction, actionKey: String): Boolean =
        action.customIconUri != null ||
                !StandardIcons.canUseLocalIcon(actionKey, action.defaultIconRes)
