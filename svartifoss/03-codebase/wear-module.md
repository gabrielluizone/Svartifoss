---
title: Wear Module
aliases:
  - Watch App Code
tags:
  - svartifoss/codebase
  - wear-os
summary: The Wear OS application's architecture, Hilt graph, UI stacks, communication, input, and system surfaces.
---

# Wear Module

`wear/` is the Wear OS application. Source packages live under `com.svartifoss.snfell.watch.*`; its application ID still matches the phone's `com.svartifoss.snfell`. It compiles against API 36.1, targets 35, supports API 26+, and uses Hilt with kapt.

## Application and runtime

- `watch/WearMusicCenter.kt` is the Hilt application and watch-side initialization root.
- `communication/PhoneConnection.kt` is the singleton Data Layer hub and exposes observable state.
- `view/MusicViewModel.kt` combines connection state, active action configuration, playback prediction, and UI events.
- `communication/WatchMusicService.kt` is the foreground lifecycle service that owns the proxy media session and keeps the connection active when required.
- `view/MainActivity.kt` is the legacy View host for now-playing, input, overlays, ambient mode, classic rendering, and Compose-face selection.

Hilt is annotation-driven; there is no watch `di/` package equivalent to the phone's Dagger graph.

## Package map

| Package | Responsibility |
| --- | --- |
| `communication/` | Data Layer runtime, manifest receivers, proxy media session, playback clock, watch info, shutdown, remote URI opening |
| `config/` | decoded `ButtonAction`s, playing/stopped providers, action-menu provider, preference event bus |
| `view/` | host activity, legacy controls, input managers, drawable/view renderers, prediction policy |
| `view/face/` | Compose face contract, shared face chrome, current face implementations and AOD variants |
| `view/queue/`, `menu/`, `lyrics/`, `volume/`, `progress/`, `facepicker/` | self-contained watch activities, screens, and ViewModels |
| `view/panel/` | reusable panel appearance/scaffold/readout and album palette cache |
| `view/compose/` | shared Compose screen chrome, loading bars, music-note text |
| `tile/` | media and shortcut Tiles plus click trampoline |
| `complication/` | album-art/title data source |
| `input/` | platform primary-hand-gesture subscription |
| `update/` | incoming APK channel and package installer result |
| `theme/` | album palette and font/theme constants |
| `util/` | locale wrapping, clock, coroutine helpers, keep-screen-on behavior, action titles |

## UI split

The root player is mixed View/Compose:

- Classic remains implemented by host Views.
- Compose faces render in one `ComposeView` but leave gestures and overlays to the host.
- `NowPlayingFaceState` is the state-in contract; callbacks are the events-out contract.
- Secondary activities use Compose and a shared curved-screen visual vocabulary.

This is intentional incremental modernization. New isolated screens and face work should prefer Compose; host responsibilities should not be silently duplicated inside a face.

## Input handling

`MainActivity` coordinates:

- `FourWayTouchLayout` quadrants and `ScreenSwipeResolver`;
- `StemButtonsManager` for physical key sequences;
- `RotaryEncoderHelper` and seek/volume policies;
- visible mini buttons and quick-panel pseudo buttons;
- center tap/double/long gestures;
- `DoublePinchGestureController` on supported Wear OS/hardware.

`ClaimedGestureHost` protects dispatch between Compose/child content and the outer detector. Right swipe is left for system dismiss.

## Communication and offline projection

`PhoneConnection` seeds itself from durable DataItems, receives fast messages while active, decodes assets/lists/config, sends commands, and exposes both raw DataItems and modeled LiveData. Manifest listener services cover changes while that runtime listener is absent.

Local configuration providers observe forever by design: they are singleton/process components that need to update an open player when a manifest listener posts frozen state.

## System surfaces

- `WatchMediaSession` exposes phone playback to Wear OS.
- `MediaTileService` renders track/transport/relative seek.
- `ShortcutsTileService` reads the durable shortcuts list.
- `AlbumArtComplicationDataSourceService` reads the durable music state.
- `GlanceableSurfaces` coordinates update requests.

These surfaces read the Data Layer directly because the foreground service may be idle.

## Source anchors

- `wear/build.gradle`
- `wear/src/main/AndroidManifest.xml`
- `wear/src/main/java/com/svartifoss/snfell/watch/WearMusicCenter.kt`
- `wear/src/main/java/com/svartifoss/snfell/watch/communication/PhoneConnection.kt`
- `wear/src/main/java/com/svartifoss/snfell/watch/view/MainActivity.kt`
- `wear/src/main/java/com/svartifoss/snfell/watch/view/MusicViewModel.kt`

## Related notes

- [Entry points](entry-points.md)
- [Watch UI and appearance](../02-architecture/watch-ui-and-appearance.md)
- [Runtime lifecycle and surfaces](../02-architecture/runtime-lifecycle-and-surfaces.md)

