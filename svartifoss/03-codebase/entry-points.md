---
title: Entry Points
tags:
  - svartifoss/codebase
  - reference
summary: Android component entry points and the shortest source route for common events.
---

# Entry Points

## Phone Android components

| Event | First component | Continue into |
| --- | --- | --- |
| Phone app launch | `view/mainactivity/MainActivity` | fragments, `MainActivityViewModel`, session mini player |
| Process initialization | `WearMusicCenter` | Dagger, preference sync, diagnostics/privacy, repairs |
| Notification posted/removed | `NotificationService` | active-session/media-action tracking, `MusicService` |
| Watch message arrives | `WatchListenerService` | `MusicService.onMessageReceived` |
| Foreground media runtime starts | `music/MusicService` | controller observation, state transmit, command switch |
| FCM announcement arrives | `notifications/AnnouncementMessagingService` | local notification rendering |
| Shared link enters app | `view/settings/PlaylistShortcutsActivity` | streaming-link inspection/storage |
| Update screen opens | `update/UpdateActivity` | `UpdateChecker`, installers, `WatchApkPusher` |
| Phone package replaced | `update/PhoneInstallResultReceiver` | install cleanup/status |

## Watch Android components

| Event | First component | Continue into |
| --- | --- | --- |
| Watch app launch | `view/MainActivity` | `MusicViewModel`, `PhoneConnection`, active face |
| Process initialization | `watch/WearMusicCenter` | Hilt and watch setup |
| Runtime service starts | `communication/WatchMusicService` | `PhoneConnection`, `WatchMediaSession`, ongoing activity |
| Music DataItem changes while idle | `communication/MusicStateListenerService` | surfaces refresh, possible service revival |
| Action config changes while idle | `communication/ConfigListenerService` | raw config LiveData/providers |
| Durable preferences arrive | `communication/PreferencesReceiver` | local preference application |
| Fast preferences arrive | `communication/PreferenceMessageReceiver` | sequence-aware additive application |
| Wake-worthy phone message | `communication/IdleMessageListener` | open/search/lyrics/start/stop/deep-link verdict |
| Watch APK channel opens | `update/ApkReceiverService` | validation and PackageInstaller |
| Tile update/action | `tile/MediaTileService` or `ShortcutsTileService` | direct DataItem read/message; click trampoline |
| Complication request | `complication/AlbumArtComplicationDataSourceService` | direct music DataItem read |

## Common runtime questions

### “Why did this setting not reach the watch?”

Inspect, in order:

1. `MiscPreferences` definition and `EXPORTABLE` membership;
2. `FaceScopedPreferences` membership/resolution if visual;
3. phone `WatchPreferenceSyncCoordinator` selection/filtering/logs;
4. `WatchPreferenceMessage` encoding;
5. watch preference receivers and `PreferencesBus`;
6. the renderer's actual read.

### “Why did a watch action do nothing?”

Inspect:

1. active playing/stopped `WatchActionConfigProvider`;
2. `MusicViewModel.executeActionOnWatch` local intercept;
3. `PhoneConnection` send path;
4. phone `WatchListenerService` and `MusicService` message switch;
5. action-handler binding;
6. target media app's actual capability/fallback behavior.

### “Why is the phone preview different?”

Compare shared resolver inputs first, then `WatchPreviewView.readPreferenceSnapshot`, the watch renderer, and relevant parity tests. Avoid correcting the preview with a one-off literal if the underlying decision belongs in `common`.

### “Why are Tiles or the complication stale?”

Inspect the durable `/Music/State` item, `MusicStateListenerService`, `GlanceableSurfaces`, and each surface's direct DataItem read. Do not assume the foreground watch service was running.

### “Why did a queue row not play?”

Trace `OpenPlaylistAction` → `MusicService.resolveQueueSource` → encoded `QueueEntry` → watch selection send → `onQueueEntrySelected` verification/fallback. Confirm the command targets the controller that published the queue.

## Full component authority

The manifests remain the exact list of exported/registered Android entry points:

- `mobile/src/main/AndroidManifest.xml`
- `wear/src/main/AndroidManifest.xml`

## Related notes

- [Mobile module](mobile-module.md)
- [Wear module](wear-module.md)
- [Observability and debugging](../04-development/observability-and-debugging.md)

