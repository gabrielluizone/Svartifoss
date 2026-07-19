package com.svartifoss.snfell.common.actions

object StandardActions {
    const val ACTION_PLAY = "com.svartifoss.snfell.actions.playback.PlayAction"
    const val ACTION_PAUSE = "com.svartifoss.snfell.actions.playback.PauseAction"
    const val ACTION_SKIP_TO_PREV = "com.svartifoss.snfell.actions.playback.SkipToPrevAction"
    const val ACTION_SKIP_TO_NEXT = "com.svartifoss.snfell.actions.playback.SkipToNextAction"
    const val ACTION_VOLUME_UP = "com.svartifoss.snfell.actions.volume.IncreaseVolumeAction"
    const val ACTION_VOLUME_DOWN = "com.svartifoss.snfell.actions.volume.DecreaseVolumeAction"
    const val ACTION_OPEN_MENU = "com.svartifoss.snfell.actions.OpenMenuAction"
    const val ACTION_SKIP_30_SECONDS = "com.svartifoss.snfell.actions.playback.SkipThirtySecondsAction"
    const val ACTION_REVERSE_30_SECONDS = "com.svartifoss.snfell.actions.playback.ReverseThirtySecondsAction"
    const val ACTION_PLAY_PAUSE = "com.svartifoss.snfell.actions.playback.PlayPauseToggleAction"
    const val ACTION_STOP = "com.svartifoss.snfell.actions.playback.StopAction"
    const val ACTION_RESTART = "com.svartifoss.snfell.actions.playback.RestartTrackAction"
    const val ACTION_MUTE = "com.svartifoss.snfell.actions.volume.MuteToggleAction"
    const val ACTION_SEARCH = "com.svartifoss.snfell.actions.SearchAction"
    const val ACTION_LIKE = "com.svartifoss.snfell.actions.playback.LikeAction"
    const val ACTION_SHUFFLE = "com.svartifoss.snfell.actions.playback.ShuffleAction"
    const val ACTION_REPEAT = "com.svartifoss.snfell.actions.playback.RepeatAction"
    const val ACTION_REPEAT_ONE = "com.svartifoss.snfell.actions.playback.RepeatOneAction"

    /** Not watch-local like the others above in the sense of never reaching the phone - it still
     *  requests a fresh queue from the phone - but the watch intercepts it to launch its own
     *  QueueActivity screen directly instead of running OpenPlaylistAction's normal round trip
     *  (which would instead push a plain custom-list popup). */
    const val ACTION_OPEN_PLAYLIST_MENU = "com.svartifoss.snfell.actions.OpenPlaylistAction"
    /** Opens the persistent Streaming shortcuts cache locally, then refreshes it from the phone. */
    const val ACTION_OPEN_STREAMING_SHORTCUTS =
            "com.svartifoss.snfell.actions.OpenPlaylistShortcutsAction"
}
