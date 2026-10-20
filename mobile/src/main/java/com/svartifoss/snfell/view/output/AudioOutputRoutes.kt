package com.svartifoss.snfell.view.output

/**
 * Which of the phone's own screens can show the audio-output switcher, best first, by Android
 * version - pure, so the order is pinned without a phone.
 *
 * Android has never had one public way to open it. Each version offers something different, and
 * the ones in between are not public at all, so [AudioOutputSwitcherActivity] tries these in order
 * and stops at the first that opens:
 *
 * - [SYSTEM_SWITCHER]: `MediaRouter2.showSystemOutputSwitcher()`, the documented call, from
 *   Android 14. It only works while the caller is in the foreground, which is why it runs from an
 *   activity the watch has just opened rather than from the service.
 * - [MEDIA_OUTPUT_DIALOG]: System UI's own media-output dialog on Android 12 and 13, reached by
 *   the broadcast the Jetpack `mediarouter` library also sends; asked for by package so it switches
 *   the app that is playing, not this one.
 * - [MEDIA_OUTPUT_PANEL]: the Settings panel that held the same list on Android 11. On Android 10
 *   it exists but needs a system permission (checked on the test phone), so it is not offered there.
 * - [VOLUME_PANEL]: the public volume panel from Android 10, whose first row says where media is
 *   playing and opens the list from there.
 * - [BLUETOOTH_SETTINGS]: on every version, the honest last resort - where the device a person is
 *   after would be connected anyway.
 */
enum class AudioOutputRoute {
    SYSTEM_SWITCHER,
    MEDIA_OUTPUT_DIALOG,
    MEDIA_OUTPUT_PANEL,
    VOLUME_PANEL,
    BLUETOOTH_SETTINGS;

    companion object {
        fun forSdk(sdkInt: Int): List<AudioOutputRoute> = buildList {
            if (sdkInt >= 34) add(SYSTEM_SWITCHER)
            if (sdkInt in 31..33) add(MEDIA_OUTPUT_DIALOG)
            if (sdkInt == 30) add(MEDIA_OUTPUT_PANEL)
            if (sdkInt >= 29) add(VOLUME_PANEL)
            add(BLUETOOTH_SETTINGS)
        }
    }
}
