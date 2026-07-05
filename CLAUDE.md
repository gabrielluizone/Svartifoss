# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Svartifoss — an Android app that lets a paired Wear OS watch control music playback on the phone (and on the watch itself), with customizable physical buttons / digital crown / gestures. Distributed as a sideloaded APK (not on Play Store).

## Module layout

This is a multi-module Gradle (Groovy DSL) Android project, configured in `settings.gradle` / `build.gradle` / `libs.toml` (version catalog):

- `mobile/` — the phone app. Reads the currently playing media session, executes actions (play/pause/skip/volume/Tasker tasks/open other apps), and syncs config/state to the watch over the Wearable Data Layer API. Uses **Dagger 2** (`di/AppComponent.kt`, manual `dagger-android` injection) for DI.
- `wear/` — the watch app. Renders the on-watch UI (now-playing screen, action menu, volume bar), receives button/crown/gesture input, and talks to the phone. Uses **Hilt** for DI (different DI framework than `mobile/` — don't assume Dagger conventions carry over).
- `common/` — shared code linked into both `mobile` and `wear`: communication path constants (`CommPaths.kt`), action/button-config models, protobuf schemas (`src/main/proto/*.proto` — actions, music, watch, notifications, custom lists), shared views/drawables.
- `wearutils/` — a **git submodule** (https://github.com/matejdro/WearUtils) with its own `libs.toml`. If this directory is empty, run `git submodule update --init` before building — Android Studio/Gradle sync will fail otherwise.

### Phone ⟷ watch communication

All communication goes through the Google Play Services **Wearable Data Layer API** (`MessageClient`/`DataClient`), with paths centralized in `common/.../CommPaths.kt`. Key entry points:
- Phone side: `WatchListenerService` (`WearableListenerService`) receives messages and forwards to `MusicService`; `WatchInfoProvider`/`ButtonConfigTransmitter`/`ActionListTransmitter` push config to the watch.
- Watch side: `PhoneConnection`, `WatchMusicService`, `PreferencesReceiver`, `IdleMessageListener` handle the corresponding receiving/sending logic.
- Payloads for structured data (action lists, button configs, watch info) are serialized with **protobuf** using the schemas in `common/src/main/proto/`.
- Watch-facing settings are declared in `common/.../MiscPreferences.kt`, edited in the phone's settings UI, and synced phone → watch (received by `PreferencesReceiver`). Add new watch-behavior toggles there, not in watch-local prefs.

### Watch-side data flow

`PhoneConnection` (Hilt `@Singleton`) listens to `DataClient` updates and exposes `LiveData` properties (`musicState`, `rawPlaybackConfig`, `rawStoppedConfig`, `customList`, etc.). The two separate configs — playing vs. stopped — let users assign different button actions depending on whether music is currently playing.

`MusicViewModel` (`@HiltViewModel`) consumes `PhoneConnection` LiveData and drives `MainActivity`. It owns `WatchActionConfigProvider` (one for each config) which decodes protobuf action configs from the phone into `ButtonAction` objects keyed by `ButtonInfo`.

`WatchMusicService` is a foreground `LifecycleService` that stays alive while music is playing or the UI is open, keeping `PhoneConnection` active. It shuts down after an idle timeout when neither condition holds. Every full-screen watch activity (now-playing, queue, menu) binds a `UiOpenServiceConnection` to hold the service while it's in the foreground — the Compose windows are opaque, so `MainActivity` underneath them gets stopped and can't be the one keeping the service alive.

`WatchMediaSession` (`watch/communication/WatchMediaSession.kt`) is a `MediaSessionCompat` proxy: it mirrors phone playback state (metadata, playback state, album art, volume) received from `PhoneConnection`, and forwards transport controls (play/pause toggle, skip, seek, volume) back to the phone over the Data Layer. It is owned and lifecycle-managed by `WatchMusicService`. This is the architectural keystone that enables Wear OS system media surfaces, Tiles, and Complications to see and control the phone's music.

### Watch input handling

`MainActivity` handles five input categories:

1. **Screen gestures** — `FourWayTouchLayout` (from `common/`) divides the screen into quadrants; taps in each quadrant are reported as a `ScreenQuadrant` gesture mapped through button config.
2. **Full-screen swipes** — up/down/left swipes on the now-playing screen are pseudo-buttons (`SwipeGesture` in `common/`, buttonCodes chosen outside the quadrant range) flowing through the same `ButtonInfo`/config pipeline as quadrant taps. Right swipe is deliberately never intercepted — it's the system-wide Wear OS dismiss gesture.
3. **Physical stem buttons** — `StemButtonsManager` (`wear/.../view/StemButtonsManager.kt`) translates raw `KeyEvent` sequences into single-tap, double-tap, or long-press gestures per configured button. Includes an ambient-mode phantom-click workaround.
4. **Rotary encoder / digital crown** — handled via `RotaryEncoderHelper` directly in `MainActivity`. On devices with discrete rotary input, crown turns are simulated as button presses (`SpecialButtonCodes.TURN_ROTARY_CW/CCW`) through `StemButtonsManager`, so they're user-configurable like any button. Otherwise, continuous scroll deltas change volume — or scrub the playback timeline instead when the `MiscPreferences.ROTARY_SEEK` preference is on — subject to a configurable deadzone and sensitivity.
5. **On-screen mini buttons** — up to three *visible* configurable buttons (`ScreenButtons` in `common/`, codes 7–9, tap + long-press) rendered as a fixed overlay near the bottom of the now-playing screen; ordered after `center_tap_zone` in the layout so they win touch dispatch over the center play/pause zone and `FourWayTouchLayout`. Row offset is tunable via `MiscPreferences.WEAR_SCREEN_BUTTONS_OFFSET` (phone developer settings).

Resolved actions don't always round-trip to the phone: `MusicViewModel.executeActionOnWatch` intercepts actions that make sense locally (opening the menu/queue, opening search input, volume UI) and only sends the rest to the phone for execution.

### Phone app UI

The phone UI is a single activity (`mobile/.../view/mainactivity/MainActivity.kt`) that swaps fragments via a bottom navigation bar: `TutorialFragment` (Guide), `ButtonConfigFragment` (two instances — playing vs. stopped config), `ActionListFragment`, and `MiscSettingsFragment`. A left navigation drawer holds only app/about info (version, author), not navigation. The activity also owns cross-fragment chrome: the toolbar, a mini player (driven by a `MediaController` bound to the active media session, with queue and detail dialogs), and a shared FAB whose click is dispatched to the current fragment via the `FabFragment` interface.

The redesigned phone theme is internally named **Lyra** (resource names like `dialog_bg_lyra`, `lyra_*` selectors). The accent color can be static, user-picked, or dynamically extracted from album art; `view/LyraAccent.kt` is the single source of truth for resolving the currently displayed accent outside `MainActivity` (standalone dialog activities use it to match what's on screen).

### Actions system (mobile)

Button presses/gestures map to `PhoneAction` subtypes (`mobile/.../actions/`), each with a corresponding `ActionHandler<T>` implementation (playback, volume, app-launch, Tasker, open menu/playlist, etc.). New actions typically need: an action class, a handler, a binding in `di/ActionHandlersModule.kt`, and an entry in the relevant action list (`RootActionList`, `PlaybackActionList`, `VolumeActionList`).

### Custom lists (watch menu content)

Several watch-facing features are phone-side SharedPreferences storages (`mobile/.../music/*Storage.kt` — search history, playlist shortcuts, track history) delivered to the watch as **custom lists**; the list types (live queue, recently-played fallback for apps that hide their queue, search results, playlist shortcuts, search history) are defined in `common/.../CustomLists.kt`, and selection/deletion round-trips through `MusicService.onCustomMenuItemPresed` and `CommPaths.MESSAGE_DELETE_CUSTOM_LIST_ITEM`. Watch-initiated search (`SearchAction`) opens voice/keyboard input on the watch; the query returns via `CommPaths.MESSAGE_PLAY_FROM_SEARCH` and is resolved with `MediaBrowserSearch` (MediaBrowserService library search — the Android Auto/Assistant mechanism, far more reliably implemented by music apps than `MediaSession.playFromSearch`).

## Build & test commands

Standard Gradle/Android workflow (run from repo root):

```
./gradlew assembleDebug              # build phone app debug APK
./gradlew :mobile:assembleDebug      # build only the mobile module
./gradlew :wear:assembleDebug        # build only the wear module
./gradlew test                       # run all JVM unit tests
./gradlew :mobile:testDebugUnitTest --tests "*.StreamIntegerTest"   # single test class
./gradlew :wear:testDebugUnitTest --tests "*.StemButtonsManagerTest"
./gradlew lint                       # Android lint across modules
```

Unit tests live under `mobile/src/test` and `wear/src/test` (JUnit 4). `wear/build.gradle` uses the `unmock` plugin to run certain Android-framework-dependent tests on the JVM instead of mocking — see the `unMock { ... }` block when adding tests that touch `android.*` classes in `wear`.

There is no instrumented/UI test setup in this repo currently.

## Signing

Release signing config is pulled from an optional `keystore.properties` file at the repo root (not checked in) — see the `afterEvaluate` block in the root `build.gradle`. Builds without it fall back to the debug signing config.

## Toolchain

- Kotlin (see `libs.toml` for the pinned version), Java/Kotlin target 21.
- `compileSdk 36`. Mobile: `minSdkVersion 23`, `targetSdkVersion 30`. Wear: `minSdkVersion 26`, `targetSdkVersion 34`.
- Dependency versions are centralized in `libs.toml` (root) and `wearutils/libs.toml` (submodule), referenced via Gradle version catalogs (`libs`, `wearUtilsLibs`).

## Package naming gotchas

The app was renamed from Music Center for Wear (fork of matejdro/WearMusicCenter): all code now lives under `com.svartifoss.snfell` / `com.svartifoss.snfell.common`. Older docs/commits referencing `com.matejdro.wearmusiccenter` mean the same code.

- Despite living in `wear/`, the watch-side code is under the package `com.svartifoss.snfell.watch.*` (not `.wear.*`) - that's the Gradle `namespace`, used for generated R/BuildConfig classes only. All watch-side classes (`PhoneConnection`, `WatchMusicService`, `MainActivity`, etc.) are in subdirectories named `watch/communication/`, `watch/view/`, `watch/model/`, `watch/config/`, `watch/util/`.
- The Gradle **`applicationId`** (the Play Store package identity) differs between modules: `mobile/` is `com.svartifoss.snfell`, `wear/` is `com.svartifoss.wrfell` - they're published as two separate Play Store listings, which can't share a package name. `MainActivity.openWatchPlayStorePage()` (mobile) hardcodes the wear app's id to open its Play Store listing from the phone; keep that constant in sync with `wear/build.gradle`'s `applicationId` if it ever changes.
- `mobile/` still contains one deliberately un-renamed package: `com.matejdro.wearvibrationcenter.notificationprovider` — the AIDL/interop contract with the separate Wear Vibration Center app; renaming it would break that integration.
- The `wearutils/` submodule keeps its own `com.matejdro.wearutils.*` packages.

## Modernization roadmap

`docs/wear-modernization-plan.md` contains the full phased modernization strategy: Foundation (targetSdk 30→34, deprecated API cleanup) → MediaSession mirror (watch-side `MediaSession` that forwards controls to the phone) → Tiles & Complications → in-UI navigation. Consult this doc before making architectural decisions in `wear/`.

**Current state (branch `wear-phase-1-mediasession`):** `WatchMediaSession` is implemented and active, and Phase 3 surfaces exist: Tiles (`watch/tile/MediaTileService.kt`, `QueuePreviewTileService.kt`, built with `androidx.wear.protolayout`/`androidx.wear.tiles`) and a complication data source (`watch/complication/AlbumArtComplicationDataSourceService.kt`, `androidx.wear.watchface` complications). Because these run outside the main app UI lifecycle, they fetch music state directly over the Data Layer via `CommPaths` rather than through `PhoneConnection`. Jetpack Compose for Wear OS (`wear.compose.material3`) is enabled in `wear/build.gradle` and used for `QueueActivity`/`QueueScreen` (`watch/view/queue/`) and `MenuActivity`/`MenuScreen` (`watch/view/menu/` — the actions/custom-list menu, which replaced the old `WearableDrawerLayout` bottom drawer; `MenuActivity` is a pure picker that returns the selection for `MainActivity` to execute). Shared Compose chrome (curved clock, scroll indicator, spinner) lives in `watch/view/compose/WatchScreenChrome.kt`; design constants for all three UI stacks live in `watch/theme/WatchTheme.kt`. New watch UI work should prefer Compose; the legacy View-based `MainActivity` (now-playing screen) remains.
