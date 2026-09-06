---
title: Source Index
aliases:
  - File Index
tags:
  - svartifoss/reference
  - codebase
summary: A concern-oriented index of the most useful source files and directories.
---

# Source Index

Paths are relative to the repository root.

## Cross-device contracts

| Concern | Source |
| --- | --- |
| Data Layer paths | `common/src/main/java/com/svartifoss/snfell/common/CommPaths.kt` |
| Wire schemas | `common/src/main/proto/` |
| Typed preferences | `common/src/main/java/com/svartifoss/snfell/common/MiscPreferences.kt` |
| Face scopes/defaults | `common/src/main/java/com/svartifoss/snfell/common/FaceScopedPreferences.kt` |
| Face/context registry | `common/src/main/java/com/svartifoss/snfell/common/ThemeAppearance.kt` |
| Immediate preference encoding | `common/src/main/java/com/svartifoss/snfell/common/WatchPreferenceMessage.kt` |
| Stable action IDs/icons | `common/src/main/java/com/svartifoss/snfell/common/actions/` |
| Button identity | `common/src/main/java/com/svartifoss/snfell/common/buttonconfig/` and pseudo-input objects in `common` |

## Phone runtime

| Concern | Source |
| --- | --- |
| Application initialization | `mobile/src/main/java/com/svartifoss/snfell/WearMusicCenter.kt` |
| Watch message ingress | `mobile/src/main/java/com/svartifoss/snfell/WatchListenerService.kt` |
| Notification/session ingress | `mobile/src/main/java/com/svartifoss/snfell/NotificationService.kt` |
| Media/action hub | `mobile/src/main/java/com/svartifoss/snfell/music/MusicService.kt` |
| Session selection | `mobile/src/main/java/com/svartifoss/snfell/music/ActiveMediaSessionProvider.kt` |
| Preference publishing | `mobile/src/main/java/com/svartifoss/snfell/WatchPreferenceSyncCoordinator.kt` |
| Dagger graph | `mobile/src/main/java/com/svartifoss/snfell/di/` |

## Watch runtime

| Concern | Source |
| --- | --- |
| Application initialization | `wear/src/main/java/com/svartifoss/snfell/watch/WearMusicCenter.kt` |
| Data Layer hub | `wear/src/main/java/com/svartifoss/snfell/watch/communication/PhoneConnection.kt` |
| Foreground service | `wear/src/main/java/com/svartifoss/snfell/watch/communication/WatchMusicService.kt` |
| Proxy media session | `wear/src/main/java/com/svartifoss/snfell/watch/communication/WatchMediaSession.kt` |
| Playback anchor | `wear/src/main/java/com/svartifoss/snfell/watch/communication/PlaybackClock.kt` |
| UI state/dispatch | `wear/src/main/java/com/svartifoss/snfell/watch/view/MusicViewModel.kt` |
| Player host/input/AOD | `wear/src/main/java/com/svartifoss/snfell/watch/view/MainActivity.kt` |
| Manifest communication receivers | `wear/src/main/java/com/svartifoss/snfell/watch/communication/` |

## Phone UI and configuration

| Concern | Source |
| --- | --- |
| Activity shell/navigation | `mobile/src/main/java/com/svartifoss/snfell/view/mainactivity/MainActivity.kt` |
| Controls and action picker | `mobile/src/main/java/com/svartifoss/snfell/view/buttonconfig/` |
| Action menu editor | `mobile/src/main/java/com/svartifoss/snfell/view/actionlist/` |
| General settings/search | `mobile/src/main/java/com/svartifoss/snfell/view/settings/` |
| Watch editor/preview | `mobile/src/main/java/com/svartifoss/snfell/view/watchface/` |
| Preference XML | `mobile/src/main/res/xml/settings.xml`, `mobile/src/main/res/xml/watch_face_settings.xml` |
| Config models/transmission | `mobile/src/main/java/com/svartifoss/snfell/config/` |
| Backup/import | `mobile/src/main/java/com/svartifoss/snfell/config/ConfigBackup.kt` |

## Watch UI

| Concern | Source |
| --- | --- |
| Face contract/chrome | `wear/src/main/java/com/svartifoss/snfell/watch/view/face/NowPlayingFace.kt`, `wear/src/main/java/com/svartifoss/snfell/watch/view/face/FaceChrome.kt` |
| Face implementations | `wear/src/main/java/com/svartifoss/snfell/watch/view/face/` |
| Queue | `wear/src/main/java/com/svartifoss/snfell/watch/view/queue/` |
| Menu | `wear/src/main/java/com/svartifoss/snfell/watch/view/menu/` |
| Lyrics | `wear/src/main/java/com/svartifoss/snfell/watch/view/lyrics/` |
| Volume/progress panels | `wear/src/main/java/com/svartifoss/snfell/watch/view/volume/`, `wear/src/main/java/com/svartifoss/snfell/watch/view/progress/`, `wear/src/main/java/com/svartifoss/snfell/watch/view/panel/` |
| Face picker | `wear/src/main/java/com/svartifoss/snfell/watch/view/facepicker/` |
| Tiles | `wear/src/main/java/com/svartifoss/snfell/watch/tile/` |
| Complication | `wear/src/main/java/com/svartifoss/snfell/watch/complication/` |

## Media content

| Concern | Source |
| --- | --- |
| Queue action | `mobile/src/main/java/com/svartifoss/snfell/actions/OpenPlaylistAction.kt` |
| Queue art | `mobile/src/main/java/com/svartifoss/snfell/music/QueueArtworkResolver.kt` |
| Browser playback/search/library | `mobile/src/main/java/com/svartifoss/snfell/music/MediaBrowserPlayback.kt`, `mobile/src/main/java/com/svartifoss/snfell/music/MediaBrowserSearch.kt`, `mobile/src/main/java/com/svartifoss/snfell/music/MediaBrowserLibrary.kt` |
| Streaming link recognition | `mobile/src/main/java/com/svartifoss/snfell/music/StreamingShortcutLinks.kt` |
| Lyrics | `mobile/src/main/java/com/svartifoss/snfell/music/LyricsFetcher.kt`, `mobile/src/main/java/com/svartifoss/snfell/music/LyricsRepository.kt` |
| Metadata | `mobile/src/main/java/com/svartifoss/snfell/music/TrackMetadataReader.kt`, `mobile/src/main/java/com/svartifoss/snfell/music/MusicBrainzMetadata.kt` |
| Notification media actions | `mobile/src/main/java/com/svartifoss/snfell/notifications/MediaNotificationActions.kt` |

## Appearance

| Concern | Source |
| --- | --- |
| Phone miniature | `mobile/src/main/java/com/svartifoss/snfell/view/watchface/WatchPreviewView.kt` |
| Background layers | `common/src/main/java/com/svartifoss/snfell/common/BackgroundLayerStack.kt` |
| Background vocabulary | `common/src/main/java/com/svartifoss/snfell/common/PlayerBackgroundStyle.kt` |
| Palette/color | `common/src/main/java/com/svartifoss/snfell/common/SurfacePaletteResolver.kt`, `common/src/main/java/com/svartifoss/snfell/common/ColorHarmony.kt`, `common/src/main/java/com/svartifoss/snfell/common/AlbumAccentSelection.kt`, `common/src/main/java/com/svartifoss/snfell/common/AdaptiveTextContrast.kt` |
| Typography | `common/src/main/java/com/svartifoss/snfell/common/WatchTypography.kt` plus text-effect helpers |
| Shared geometry | `common/src/main/java/com/svartifoss/snfell/common/FaceGeometry.kt` |

## Community themes

| Concern | Source |
| --- | --- |
| Android client | `mobile/src/main/java/com/svartifoss/snfell/view/watchface/theme/` |
| Local theme repository | `mobile/src/main/java/com/svartifoss/snfell/view/watchface/theme/WatchThemeRepository.kt` |
| Public catalogue client | `mobile/src/main/java/com/svartifoss/snfell/view/watchface/theme/OnlineThemesRepository.kt` |
| Shared safe policy | `common/src/main/java/com/svartifoss/snfell/common/CommunityThemeSubmissionPolicy.kt` |
| Constraints | `common/src/main/assets/community-theme-constraints.json` |
| Firestore rules | `firestore.rules` |
| Rules tests | `firebase/` |
| Publisher | `.github/community-theme-publisher/` |
| Moderator page | `docs/admin/` |
| Public files | `docs/themes/` |

## Build, test, and release

| Concern | Source |
| --- | --- |
| Module graph | `settings.gradle` |
| Root behavior/signing override | `build.gradle` |
| Dependency versions | `libs.toml` |
| Module build | `mobile/build.gradle`, `wear/build.gradle`, `common/build.gradle` |
| Android components | `mobile/src/main/AndroidManifest.xml`, `wear/src/main/AndroidManifest.xml` |
| Release history | `CHANGELOG.md` |
| Architectural guidance | `CLAUDE.md` and `AGENTS.md` |

## Related notes

- [Codebase map](../03-codebase/codebase-map.md)
- [Entry points](../03-codebase/entry-points.md)
- [Source-of-truth matrix](source-of-truth-matrix.md)
