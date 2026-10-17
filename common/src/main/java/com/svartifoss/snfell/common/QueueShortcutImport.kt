package com.svartifoss.snfell.common

/** Intent contract shared by the Wear queue and the phone shortcut editor. */
object QueueShortcutImport {
    const val ACTION = "com.svartifoss.snfell.action.ADD_QUEUE_SHORTCUT"
    const val EXTRA_NAME = "QueueShortcutName"
    const val EXTRA_SUBTITLE = "QueueShortcutSubtitle"
    const val EXTRA_LINK = "QueueShortcutLink"

    /** Explicit phone activity used by the Wear queue request. */
    const val PHONE_ACTIVITY = "com.svartifoss.snfell.view.settings.PlaylistShortcutsActivity"
}
