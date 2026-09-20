package com.svartifoss.snfell.common

/**
 * Where the quick actions panel's three round buttons come from - the decoded form of
 * [MiscPreferences.WEAR_QUICK_PANEL_SOURCE].
 *
 * [SESSION] takes them from the playing app's own notification / media session, so the buttons the
 * user assigned to slots are never consulted at all. The phone's Actions tab reads this to stop
 * offering slots that would do nothing, and an unrecognised value resolves to [MANUAL] because that
 * is the preference's default - the value can arrive from a backup or a newer build, and a stray
 * string must not silently hide a configuration that is in fact live.
 */
object QuickPanelSource {
    const val MANUAL = "manual"
    const val SESSION = "session"

    /** True when the slots the user assigns are what the panel shows. */
    fun usesConfiguredButtons(value: String?): Boolean = value != SESSION
}
