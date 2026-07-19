package com.svartifoss.snfell.common

interface CommPaths {
    companion object {
        const val PHONE_APP_CAPABILITY = "MusicCenterPhone"
        const val WATCH_APP_CAPABILITY = "MusicCenterWatch"

        const val DATA_MUSIC_STATE = "/Music/State"
        const val DATA_WATCH_INFO = "/WatchInfo"
        const val ASSET_WATCH_INFO_BUTTON_PREFIX = "/WatchInfo/Button"

        const val DATA_NOTIFICATION = "/Notification"
        const val ASSET_NOTIFICATION_BACKGROUND = "/Notification/Background"


        const val MESSAGES_PREFIX = "wear://*/Messages/"
        const val MESSAGE_WATCH_OPENED = "/Messages/WatchOpened"
        const val MESSAGE_WATCH_CLOSED = "/Messages/WatchClosed"
        const val MESSAGE_WATCH_CLOSED_MANUALLY = "/Messages/WatchClosedManually"
        const val MESSAGE_ACK = "/Messages/ACK"
        const val MESSAGE_CHANGE_VOLUME = "/Messages/SetVolume"
        const val MESSAGE_SEEK_TO = "/Messages/SeekTo"
        // Watch -> phone: seek by a signed delta (ms, as a big-endian long payload) relative to
        // the session's LIVE position - senders like the Tile only hold a stale snapshot, so
        // the phone resolves the actual target. Used by the Tile's -10s/+10s buttons.
        const val MESSAGE_SEEK_RELATIVE = "/Messages/SeekRelative"
        const val MESSAGE_TOGGLE_PLAY_PAUSE = "/Messages/TogglePlayPause"
        const val MESSAGE_SKIP_NEXT = "/Messages/SkipNext"
        const val MESSAGE_SKIP_PREVIOUS = "/Messages/SkipPrevious"
        const val MESSAGE_QUICK_ACTION = "/Messages/QuickAction"
        const val MESSAGE_EXECUTE_ACTION = "/Messages/Action"
        const val MESSAGE_EXECUTE_MENU_ACTION = "/Messages/MenuAction"
        const val MESSAGE_SEND_LOGS = "/SendLogs"
        const val MESSAGE_OPEN_APP = "/IdleMessages/OpenApp"
        const val MESSAGE_START_SERVICE = "/IdleMessages/StartService"
        const val MESSAGE_CUSTOM_LIST_ITEM_SELECTED = "/Messages/CustomListItemSelected"
        // Watch -> phone: delete one entry from a watch-managed deletable custom list (currently
        // just search history). Reuses CustomListItemAction (listId + entryId), same as selection.
        const val MESSAGE_DELETE_CUSTOM_LIST_ITEM = "/Messages/CustomListItemDeleted"
        const val MESSAGE_OPEN_PLAYBACK_QUEUE = "/Messages/OpenPlaybackQueue"
        const val MESSAGE_PLAY_FROM_SEARCH = "/Messages/PlayFromSearch"
        // Phone -> watch. Must live under /IdleMessages so IdleMessageListener's manifest
        // path filter matches it even when the watch UI is not running.
        const val MESSAGE_OPEN_VOICE_SEARCH = "/IdleMessages/OpenVoiceSearch"

        const val CHANNEL_LOGS = "/Channel/Logs"

        // Phone -> watch: streams a newer wear APK for on-watch install (self-update without
        // ADB/Wear Installer). Received by the watch's ApkReceiverService manifest listener.
        const val CHANNEL_WEAR_APK = "/Channel/WearApk"

        const val ASSET_ALBUM_ART = "AlbumArt"

        const val DATA_ACTION_CONFIG_PREFIX = "/Actions"
        const val DATA_LIST_ITEMS = "/ActionList"

        const val DATA_PLAYING_ACTION_CONFIG = DATA_ACTION_CONFIG_PREFIX + "/Playback"
        const val DATA_STOPPING_ACTION_CONFIG = DATA_ACTION_CONFIG_PREFIX + "/Stopped"
        const val ASSET_BUTTON_ICON_PREFIX = "/Button_Icon_"

        const val DATA_CUSTOM_LIST = "/CustomList/List"
        // Persistent, independently cached streaming-shortcut list. It must not share
        // DATA_CUSTOM_LIST: queue/search pushes frequently replace that transient DataItem.
        const val DATA_STREAMING_SHORTCUTS = "/CustomList/StreamingShortcuts"

        const val PREFERENCES_PREFIX = "/Settings"
    }
}
