---
title: Runtime Lifecycle and Surfaces
tags:
  - svartifoss/architecture
  - lifecycle
  - services
summary: Foreground-service lifetime, process wake-up, shutdown, Tiles, complication, and update receivers.
---

# Runtime Lifecycle and Surfaces

## Phone service lifetime

`MusicService` is a foreground service. Every `startForegroundService` path must be answered by foreground promotion at the start of `onStartCommand`, including branches that soon stop. It owns media observation, state transmission, most command handling, queue/list responses, and several update triggers.

The persistent notification exposes **Stop** and **Force stop**. Both inform the watch because its process and ongoing-activity state would otherwise outlive the phone service. Force stop explicitly removes ordinary service notifications, requests notification-listener unbinding, sends the watch command on a process-level scope, waits for completion within a hard timeout, and then kills the process.

The standing notification-access error is intentionally spared: stopping due to missing access must not remove the user's route back to the setting.

## Watch service lifetime

`WatchMusicService` is a foreground `LifecycleService` that owns `PhoneConnection`, `WatchMediaSession`, and ongoing activity integration. The pure `resolveServiceHold` policy has three states:

- **ACTIVE:** UI open or music actively playing;
- **PAUSED_TRACK:** a paused but recoverable track holds the surface for a configurable period;
- **IDLE:** no reason to retain the service.

Full-screen watch activities bind through `UiOpenServiceConnection`, because opening an opaque Compose activity stops the now-playing activity beneath it; lifecycle cannot assume that `MainActivity` alone represents “UI open.”

`WatchAppShutdown` uses a non-replaying listener list. A replaying event container could close the app again the next time the user opens it.

## Wake-up listeners

Runtime listeners exist only while a process is alive. Manifest-registered services repair that gap:

- `MusicStateListenerService` receives durable music state, refreshes Tiles/complication, and revives a dead watch service if playback is active.
- `ConfigListenerService` receives action config and menu changes while the UI is idle, then posts frozen DataItems into the runtime providers.
- `PreferencesReceiver` and `PreferenceMessageReceiver` cover durable and immediate settings.
- `IdleMessageListener` receives wake-worthy commands under `/IdleMessages/`.

## Tiles and complication

The media Tile shows track information, transport, and relative ±10-second seek. The shortcuts Tile renders the durable shortcut list as tappable chips. A Tile click can only start an activity, so `ShortcutLaunchActivity` is an invisible trampoline that performs the same remote-URI/action protocol as the watch menu.

`AlbumArtComplicationDataSourceService` reads the durable music DataItem directly. These surfaces cannot depend on an in-memory `PhoneConnection` that may have timed out.

## Update components

The phone updater downloads and validates its own APK, then writes it through a `PackageInstaller` session. The watch's `ApkReceiverService` receives a channel stream, verifies the archive belongs to `com.svartifoss.snfell`, and commits a session. `InstallResultReceiver` turns required user confirmation into a notification because a background service cannot launch the confirmation screen directly.

## Lifecycle rule of thumb

If a task's successful action closes the activity or service that launched it, a lifecycle-bound coroutine is probably the wrong owner. Deep-link verdicts, install statistics, watch shutdown, and similar terminal sends use process-level or `NonCancellable` execution with explicit bounds.

## Source anchors

- `mobile/.../music/MusicService.kt`
- `wear/.../communication/WatchMusicService.kt`
- `wear/.../communication/MusicStateListenerService.kt`
- `wear/.../communication/ConfigListenerService.kt`
- `wear/.../tile/`
- `wear/.../complication/AlbumArtComplicationDataSourceService.kt`
- `mobile/.../update/` and `wear/.../update/`

## Related notes

- [Phone-watch communication](phone-watch-communication.md)
- [Entry points](../03-codebase/entry-points.md)
- [Observability and debugging](../04-development/observability-and-debugging.md)

