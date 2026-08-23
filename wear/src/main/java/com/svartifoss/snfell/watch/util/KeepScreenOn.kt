package com.svartifoss.snfell.watch.util

import android.app.Activity
import android.view.WindowManager
import androidx.preference.PreferenceManager
import com.svartifoss.snfell.common.FaceScopedPreferences
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.common.ThemeAppearance

/**
 * Applies [MiscPreferences.WEAR_KEEP_SCREEN_ON] to the player.
 *
 * **Face-scoped**: whether a screen should be held awake is a property of the composition in front
 * of you, not of the watch. A face you read wants it; the one you glance at on the way past does
 * not, and paying the battery cost on every face to have it on one is not a trade anyone would
 * pick. It is resolved through the active face like any other appearance key.
 *
 * The queue and the menu deliberately do not call this: they belong to no face, so there is no
 * per-face value for them to read, and they are choosers you pass through rather than screens you
 * sit and read.
 *
 * Re-applied rather than set once: the preference is owned by the phone and arrives over the Data
 * Layer, so it can flip - or the face under it can change - while a screen is already open.
 *
 * It clears the flag as well as setting it - an activity that only ever added it would keep the
 * screen pinned on for the rest of its life after the user switched the preference back off.
 *
 * The limit of what this can do is the same one the preference's own summary states: it defeats the
 * **inactivity timeout**, and nothing more. Lowering the wrist is a system gesture that no window
 * flag overrides.
 *
 * The lyrics screen deliberately does not call this - it holds the screen on unconditionally, since
 * following a lyric involves touching nothing and the timeout is guaranteed wrong there.
 */
fun Activity.applyKeepScreenOnPreference() {
    val prefs = PreferenceManager.getDefaultSharedPreferences(this)
    val enabled = FaceScopedPreferences.getBoolean(
            prefs, MiscPreferences.WEAR_KEEP_SCREEN_ON, ThemeAppearance.resolve(prefs))

    if (enabled) {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
