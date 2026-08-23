package com.svartifoss.snfell.common

interface CommPaths {
    companion object {
        const val PHONE_APP_CAPABILITY = "MusicCenterPhone"
        const val WATCH_APP_CAPABILITY = "MusicCenterWatch"

        const val DATA_MUSIC_STATE = "/Music/State"

        /**
         * Phone -> watch: the same `MusicState` as [DATA_MUSIC_STATE], sent as a message so it
         * arrives *now*.
         *
         * Two transports for one payload, the shape [MESSAGE_APPLY_PREFERENCES] already uses and
         * for the same reason. The DataItem stays the durable source of truth - it survives the
         * watch sleeping, it carries the album-art and source-icon assets, and it is what a
         * freshly started watch seeds itself from. What it is not is prompt: it goes through Play
         * Services' replication layer, and even flagged urgent that costs enough that a track
         * changed on the phone visibly lagged on the wrist.
         *
         * That lag was not only cosmetic. The watch predicts the playback position from the last
         * sample it was sent (see PlaybackPositionEstimate), and the transport time between the
         * phone taking that sample and the watch receiving it is the one part of the delay the
         * prediction cannot account for - so a slow DataItem put a lasting offset on the position
         * itself, which is what made synced lyrics run behind the song.
         *
         * Carries no assets: a message cannot hold them, and the cover legitimately takes longer
         * than the text. The watch applies whichever arrives first and discards the other by
         * content, so the two never fight.
         */
        const val MESSAGE_MUSIC_STATE = "/Messages/MusicState"
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

        /**
         * Watch -> phone: fetch the lyrics for the track named in the `LyricsRequest` payload.
         *
         * The **phone** does the network call, not the watch, and that is not an implementation
         * detail: a Bluetooth-only watch has no route to the internet of its own, so a watch-side
         * fetch would work on LTE models and silently fail on every other one. It also keeps this
         * app's whole network surface on the phone, where the privacy docs describe it.
         */
        const val MESSAGE_REQUEST_LYRICS = "/Messages/RequestLyrics"

        /**
         * Watch -> phone: everything known about the track named in the `TrackMetadata` payload.
         *
         * Asked for rather than pushed, and that is the whole cost model of the metadata face: a
         * watch showing any other face never sends this, so the phone never reads a file, never
         * queries anything, and the twenty-odd fields never cross Bluetooth. Same shape as
         * [MESSAGE_REQUEST_LYRICS], and for the same reason - a surface nobody has open should not
         * be paid for.
         */
        const val MESSAGE_REQUEST_TRACK_METADATA = "/Messages/RequestTrackMetadata"

        /**
         * Phone -> watch: the answer, as a serialized `TrackMetadata`.
         *
         * Sent **twice** for one request when the optional online lookup is on: once immediately
         * with what the phone already knows, and again with `enriched` set once the lookup returns.
         * The screen is therefore never waiting on a network call to draw - which is the rule this
         * whole path is built around.
         *
         * `/Messages`, not `/IdleMessages`, for the reason [MESSAGE_LYRICS_RESULT] documents: this
         * is useless to a watch whose UI is not running, so it reaches `PhoneConnection`'s runtime
         * listener and nothing else.
         */
        const val MESSAGE_TRACK_METADATA = "/Messages/TrackMetadata"

        /**
         * Watch -> phone: "where is playback actually at?". Payload is an 8-byte token, the
         * watch's own monotonic clock reading at send time.
         *
         * The correction half of the watch's position prediction. The phone deliberately does not
         * retransmit a `MusicState` for a position that only moved on its own (see
         * `MusicService.equalsIgnoringTime`), which is right - it would be constant Bluetooth
         * traffic to say nothing - but it leaves the watch extrapolating unattended for a whole
         * track. Whatever error the first sample carried then lasts the whole song, and on the
         * lyrics screen a second of error is the difference between the right line and the wrong
         * one.
         *
         * Asked for rather than pushed, because only the watch knows when it needs one: it is the
         * side that holds the estimate, and the round trip it starts here is what lets it measure
         * the transport delay instead of assuming it away.
         */
        const val MESSAGE_REQUEST_PLAYBACK_SYNC = "/Messages/RequestPlaybackSync"

        /**
         * Phone -> watch: the answer, as a serialized `PlaybackSync`.
         *
         * `/Messages`, not `/IdleMessages`, for the same reason [MESSAGE_LYRICS_RESULT] is: this
         * payload corrects a running estimate, so it is worthless to a watch whose UI is not up,
         * and routing it through the manifest listener would wake the process to discard it. It
         * reaches `PhoneConnection`'s runtime listener, which is alive exactly while something is
         * predicting a position.
         */
        const val MESSAGE_PLAYBACK_SYNC = "/Messages/PlaybackSync"

        /**
         * Phone -> watch: the answer, as a serialized `LyricsResponse`.
         *
         * A message rather than a DataItem, for once. Lyrics are transient - fetched when a screen
         * opens and worthless afterwards - so the durability that makes DataItems right for
         * settings and the queue is exactly wrong here: the payload would sit in the Data Layer
         * store being replayed on every reconnect. It also has to arrive *now*, while the user is
         * looking at the screen, which is the same reason MESSAGE_APPLY_PREFERENCES exists beside
         * the /Settings DataItem.
         *
         * Deliberately **not** under /IdleMessages, unlike the other phone -> watch messages here.
         * That prefix exists to wake a watch whose UI is not running, and this payload is useless
         * to a watch with no lyrics screen open - routing it there would spin up the process to
         * throw the answer away. /Messages has no manifest listener on the watch, so it reaches
         * only PhoneConnection's runtime listener, which is alive exactly when the screen is.
         */
        const val MESSAGE_LYRICS_RESULT = "/Messages/LyricsResult"

        /**
         * Watch -> phone: the user picked a now-playing face from the on-watch picker. Payload is
         * the face key as UTF-8 (see [ThemeAppearance.ALLOWED_BASE_FACES]).
         *
         * It has to travel to the phone rather than staying local because preferences are
         * phone-owned and synced one way: `WatchPreferenceSyncCoordinator` re-publishes the whole
         * snapshot once per process start, so a watch-local write would be silently reverted the
         * next time the phone's process came up. The phone persists it and the normal sync brings
         * it straight back, which is also what keeps the phone's own picker and preview honest.
         */
        const val MESSAGE_SET_SCREEN_FACE = "/Messages/SetScreenFace"
        // Phone -> watch. Must live under /IdleMessages so IdleMessageListener's manifest
        // path filter matches it even when the watch UI is not running.
        const val MESSAGE_OPEN_VOICE_SEARCH = "/IdleMessages/OpenVoiceSearch"

        /**
         * Phone -> watch: open the lyrics screen.
         *
         * The counterpart of [MESSAGE_OPEN_VOICE_SEARCH], and for the same reason: the watch
         * normally intercepts the "open lyrics" action locally and never sends it anywhere, so this
         * only carries the paths that do execute on the phone. Same /IdleMessages prefix, since
         * those paths are exactly the ones where the watch UI may not be up.
         */
        const val MESSAGE_OPEN_LYRICS = "/IdleMessages/OpenLyrics"

        /**
         * Phone -> watch: the user tapped "Stop" on the phone's persistent notification.
         *
         * Stopping "the app" has always meant only the phone half, which leaves the watch holding
         * an ongoing-activity chip and a proxy media session for a phone service that no longer
         * exists. The watch tears down [WatchMusicService] and closes any open screen.
         *
         * Same /IdleMessages prefix and the same reason as [MESSAGE_OPEN_VOICE_SEARCH]: this has to
         * arrive when the watch UI is *not* running, which is the usual state when someone stops
         * the app from their phone.
         */
        const val MESSAGE_STOP_WATCH_APP = "/IdleMessages/StopApp"

        /**
         * Phone -> watch: the user tapped "Force stop".
         *
         * As on the phone, this is deliberately not an orderly shutdown - the watch process is
         * killed outright once the service is down, so nothing is left to be revived by a pending
         * binding. Anything that must happen before the app dies has to happen on receipt, not in
         * a teardown callback that may never run.
         */
        const val MESSAGE_FORCE_STOP_WATCH_APP = "/IdleMessages/ForceStopApp"

        /**
         * Phone -> watch: the verdict on a streaming shortcut the watch just asked the phone to
         * play. Payload is UTF-8: **empty** means "playback started, do not open anything";
         * anything else is the `targetPackage|uri` the watch should open on the phone.
         *
         * This exists because the visible open is the *last* resort, and only the watch can
         * perform it - `RemoteActivityHelper` is the sanctioned bridge, while the phone's own
         * playback service is barred from starting an Activity by background-start rules. The
         * watch used to fire that open unconditionally, in parallel with sending the action, so
         * `MusicService.playDeepLink`'s whole silent ladder (direct command, then the
         * MediaBrowser route) ran while the app was already being brought to the foreground
         * anyway. With the screen locked that is exactly the wrong outcome: the point of the
         * browser route is that nothing visible happens at all.
         *
         * Same /IdleMessages prefix and reason as [MESSAGE_OPEN_VOICE_SEARCH] - the verdict has to
         * arrive whether or not the watch UI is still up, since the menu closes on selection.
         *
         * A verdict is sent at every terminal point of `playDeepLink`, success included, so the
         * watch can stop waiting rather than sit on a timeout. It is still only advisory: a watch
         * that never hears one falls back to opening on its own (see `PhoneUriOpener`), which is
         * what keeps a new watch working against a phone build from before this path existed.
         */
        const val MESSAGE_DEEP_LINK_VERDICT = "/IdleMessages/DeepLinkVerdict"

        /** Immediate MessageClient delivery of a preference snapshot (see WatchPreferenceMessage),
         *  complementing the durable /Settings DataItem. Own prefix so a dedicated manifest
         *  listener wakes the watch for it without entangling the idle-message handler. */
        const val MESSAGE_APPLY_PREFERENCES = "/PreferencesSync/Apply"

        const val CHANNEL_LOGS = "/Channel/Logs"

        // Phone -> watch: streams a newer wear APK for on-watch install (self-update without
        // ADB/Wear Installer). Received by the watch's ApkReceiverService manifest listener.
        const val CHANNEL_WEAR_APK = "/Channel/WearApk"

        const val ASSET_ALBUM_ART = "AlbumArt"

        /** Icon of the app currently playing, rasterized on the phone and attached to the music
         *  state so faces can show it next to the artist (the Data Layer dedupes it while the
         *  source app is unchanged). */
        const val ASSET_SOURCE_ICON = "SourceAppIcon"

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
