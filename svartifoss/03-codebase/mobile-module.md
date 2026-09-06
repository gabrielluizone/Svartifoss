---
title: Mobile Module
aliases:
  - Phone App Code
tags:
  - svartifoss/codebase
  - android-phone
summary: The phone application's architecture, packages, UI, services, and major repositories.
---

# Mobile Module

`mobile/` is the phone application and authority side of Svartifoss. Its package root is `com.svartifoss.snfell`—there is no `.mobile` segment. It targets Android API 36 with minimum API 23 and uses Dagger 2 with kapt.

## Application and services

| Class | Role |
| --- | --- |
| `WearMusicCenter` | application initialization, Dagger root, logging/privacy gates, repairs, process-wide preference sync, theme-list publication |
| `NotificationService` | Android `NotificationListenerService`; media/action discovery, auto-start logic, notification interop |
| `WatchListenerService` | manifest message ingress from the watch; starts/forwards to `MusicService` |
| `MusicService` | foreground runtime hub for state, media control, lists, requests, and watch communication |

These four files sit directly under `mobile/src/main/java/com/svartifoss/snfell/` except `MusicService`, which is in `music/`.

## Dependency injection

Phone DI is manual Dagger/dagger-android, not Hilt:

- `di/AppComponent.kt` is the root component.
- `AppModule.kt`, `MainActivityModule.kt`, and `MainInjectorsModule.kt` contribute application and Android injection bindings.
- `MusicServiceSubComponent.kt` scopes service dependencies.
- `ActionHandlersModule.kt` maps action types to handlers.
- `GlobalConfig.kt` and `LocalActivityConfig.kt` distinguish persisted/global and activity-local configuration.
- `InjectableViewModelFactory.kt` bridges Dagger into Android ViewModels.

Do not copy Hilt conventions from the watch module into this graph without an explicit migration.

## Phone UI

`view/mainactivity/MainActivity.kt` is a single AppCompat activity shell. It owns:

- bottom navigation among Watch, Controls, Actions, and Settings;
- toolbar, help/search/update actions, and a small about drawer;
- mini player bound to the active session;
- notification-access prompt and persistent banner;
- shared FAB dispatch to the visible fragment;
- settings-search result routing.

Major UI packages:

| Package | Responsibility |
| --- | --- |
| `view/watchface/` | Watch tab, Canvas preview, contextual appearance editors, search routing |
| `view/watchface/theme/` | local theme library, community gallery, identity, submit/report/install/account flows |
| `view/buttonconfig/` | Controls tabs, button/gesture editor, action picker |
| `view/actionlist/` | watch action-menu editor |
| `view/settings/` | sectioned preferences, search index, app language, shortcuts, backup UI, custom controls |
| `view/mainactivity/` | shell, tutorial/help, mini-player queue adapter |

Phone UI is primarily Views, fragments, XML preferences, and custom Canvas drawing. The internal visual design name is **Lyra**; `LyraAccent`, `LyraDialogStyling`, and `LyraPreferenceUi` centralize dynamic accent behavior.

## Actions

`actions/` defines serialized `PhoneAction` subtypes and picker groups. Subpackages cover playback, volume, Tasker, and app launch. Each executable type normally has an `ActionHandler<T>` bound in `ActionHandlersModule` and appears in an appropriate action list.

Watch-screen actions still have phone-side types because configurations and menu lists are authored on the phone; the watch recognizes selected stable action identifiers and performs them locally.

## Media integration

The `music/` package contains:

- active-session selection and capability policy;
- browser playback, library browsing, and search;
- queue building and artwork resolution;
- streaming shortcut parsing, storage, art, and launch ladder;
- lyrics and metadata lookup;
- search/track history;
- the central `MusicService`.

The `notifications/` package extracts media notification actions, stores persistent source glyphs, sends FCM announcements, and preserves an AIDL bridge for Wear Vibration Center.

## Persistence and configuration

`config/` owns button/action models, disk serialization, transmission, watch-info consumption, defaults, custom icons, and backup. Opaque action config files use guarded Parcel serialization; visual/local theme profiles use validated JSON.

## Updates and diagnostics

- `update/` checks GitHub Releases, downloads/validates APKs, installs the phone APK, and streams a watch APK.
- `logging/` connects Timber, Crashlytics, user privacy choice, and forwarded watch exceptions.
- Firebase Authentication/Firestore live almost entirely behind community-theme operations; Analytics, Crashlytics, Messaging, and Google Services are configured in this module.

## Manifest-level integrations

The phone manifest declares notification listener, wearable listener, foreground media service, FCM service, installer receiver, share target for playlist shortcuts, settings/theme activities, FileProvider for non-APK application uses, and permissions for media reads, notifications, internet, install packages, Tasker, and notification interop.

## Source anchors

- `mobile/build.gradle`
- `mobile/src/main/AndroidManifest.xml`
- `mobile/src/main/java/com/svartifoss/snfell/WearMusicCenter.kt`
- `mobile/src/main/java/com/svartifoss/snfell/music/MusicService.kt`
- `mobile/src/main/java/com/svartifoss/snfell/view/mainactivity/MainActivity.kt`
- `mobile/src/main/java/com/svartifoss/snfell/di/AppComponent.kt`

## Related notes

- [Entry points](entry-points.md)
- [Playback and media sessions](../02-architecture/playback-and-media-sessions.md)
- [Actions and input](../02-architecture/actions-and-input.md)

