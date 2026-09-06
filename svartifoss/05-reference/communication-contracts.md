---
title: Communication Contracts
aliases:
  - Data Layer Path Index
tags:
  - svartifoss/reference
  - data-layer
summary: An indexed view of the centralized Data Layer capabilities, DataItems, messages, assets, and channels.
---

# Communication Contracts

The source authority is `common/src/main/java/com/svartifoss/snfell/common/CommPaths.kt`. Directions below describe application intent; always verify producer and receiver when changing one.

## Capabilities

| Constant | Value | Meaning |
| --- | --- | --- |
| `PHONE_APP_CAPABILITY` | `MusicCenterPhone` | discover a connected phone node |
| `WATCH_APP_CAPABILITY` | `MusicCenterWatch` | discover a connected watch node |

These legacy names remain live protocol identifiers.

## Durable DataItems

| Path | Direction | Content / main consumer |
| --- | --- | --- |
| `/Music/State` | phone → watch | `MusicState`, album-art and source-icon assets; UI, proxy session, Tiles, complication |
| `/WatchInfo` | watch → phone | watch display, buttons, version, hand-gesture availability |
| `/Notification` | phone → watch | structured notification popup data |
| `/Actions/Playback` | phone → watch | playing-state `WatchActions` and optional icons |
| `/Actions/Stopped` | phone → watch | no-playback `WatchActions` and optional icons |
| `/ActionList` | phone → watch | full `WatchList` action menu and optional icons |
| `/CustomList/List` | phone → watch | replaceable queue/search/history/library `CustomList` |
| `/CustomList/StreamingShortcuts` | phone → watch | independently durable playlist-shortcut `CustomList` |
| `/Settings` | phone → watch | filtered WearUtils preference snapshot and key inventory |

## Runtime messages

### Watch → phone

| Path | Payload | Purpose |
| --- | --- | --- |
| `/Messages/WatchOpened` | empty | announce an open UI; request/retain current state |
| `/Messages/WatchClosed` | empty | automatic close/lifecycle signal |
| `/Messages/WatchClosedManually` | empty | explicit user close |
| `/Messages/ACK` | empty | acknowledge phone/watch handshake work |
| `/Messages/SetVolume` | packed float | set absolute media volume |
| `/Messages/SeekTo` | big-endian 64-bit integer | seek to absolute milliseconds |
| `/Messages/SeekRelative` | big-endian signed 64-bit integer | seek relative to the phone's live position |
| `/Messages/SetPlaybackSpeed` | packed float | set absolute playback speed multiplier |
| `/Messages/TogglePlayPause` | empty | toggle playback |
| `/Messages/SkipNext` | empty | next track |
| `/Messages/SkipPrevious` | empty | previous track |
| `/Messages/QuickAction` | UTF-8 name/token | execute a quick media action |
| `/Messages/Action` | serialized `WatchActions.ProtoButtonInfo` | execute configured button action |
| `/Messages/MenuAction` | big-endian 32-bit index | execute action-menu entry |
| `/Messages/CustomListItemSelected` | `CustomListItemAction` | select queue/search/library/shortcut/history entry |
| `/Messages/CustomListItemDeleted` | `CustomListItemAction` | delete a supported list entry, currently search history |
| `/Messages/OpenPlaybackQueue` | optional 32-bit requested limit | request cumulative queue page |
| `/Messages/PlayFromSearch` | UTF-8 query | ask the active media app to search/play |
| `/Messages/RequestLyrics` | `LyricsRequest` | request lyrics for exact track identity |
| `/Messages/RequestTrackMetadata` | `TrackMetadata` identity fields | request on-demand details |
| `/Messages/RequestPlaybackSync` | 64-bit watch monotonic token | request a corrected position sample |
| `/Messages/SetScreenFace` | UTF-8 face key or `custom:<id>` | make phone authority persist/apply wrist selection |

### Phone → watch, running consumer

| Path | Payload | Purpose |
| --- | --- | --- |
| `/Messages/MusicState` | `MusicState`, no Data Layer assets | immediate twin of durable media state |
| `/Messages/LyricsResult` | `LyricsResponse` | transient on-demand lyric result |
| `/Messages/TrackMetadata` | `TrackMetadata` | immediate and optional enriched result |
| `/Messages/PlaybackSync` | `PlaybackSync` | echoed-token position correction |

These stay under `/Messages/` so they reach `PhoneConnection` only while a useful runtime consumer exists. They must not wake an idle watch merely to be discarded.

### Phone → watch, wake-worthy

| Path | Payload | Purpose |
| --- | --- | --- |
| `/IdleMessages/OpenApp` | empty | open watch application |
| `/IdleMessages/StartService` | empty | start watch media service |
| `/IdleMessages/OpenVoiceSearch` | empty | open watch search input |
| `/IdleMessages/OpenLyrics` | empty | open lyrics screen from a phone-executed action path |
| `/IdleMessages/StopApp` | empty | orderly watch shutdown following phone Stop |
| `/IdleMessages/ForceStopApp` | empty | service teardown and watch process kill |
| `/IdleMessages/DeepLinkVerdict` | empty on success; otherwise `targetPackage|uri` | decide whether the watch must visibly open a phone app |

### Dedicated messages

| Path | Direction | Purpose |
| --- | --- | --- |
| `/PreferencesSync/Apply` | phone → watch | sequenced immediate preference snapshot |
| `/SendLogs` | phone → watch | request watch logs; receiver is manifest-reachable |

## Channels

| Path | Direction | Purpose |
| --- | --- | --- |
| `/Channel/Logs` | watch → phone | stream diagnostic log content |
| `/Channel/WearApk` | phone → watch | stream a validated candidate watch APK |

## Asset keys

| Key/prefix | Attached to | Content |
| --- | --- | --- |
| `AlbumArt` | `/Music/State` | current album artwork |
| `SourceAppIcon` | `/Music/State` | playing app notification glyph or launcher icon |
| `/WatchInfo/Button/<code>` | `/WatchInfo` | optional physical-button image |
| `/Button_Icon_<identity>` | action config/list | custom/raster action icon |
| `/Notification/Background` | `/Notification` | optional notification background |
| indexed list asset keys | custom-list DataItem | queue/shortcut/list thumbnails; producer and decoder agree by index |

## Receivers and durability

| Receiver | Process state | Paths |
| --- | --- | --- |
| watch `PhoneConnection` | runtime active | ordinary `/Messages/*` and DataItem updates |
| watch `IdleMessageListener` | can be awakened | `/IdleMessages/*` |
| watch `PreferenceMessageReceiver` | can be awakened | `/PreferencesSync/Apply` |
| watch `PreferencesReceiver` | can be awakened | `/Settings` DataItem changes |
| watch `MusicStateListenerService` | can be awakened | `/Music/State` DataItem changes |
| watch `ConfigListenerService` | can be awakened | `/Actions/*`, `/ActionList` changes |
| phone `WatchListenerService` | can be awakened | watch-originated messages |
| phone `WatchInfoProvider` | active observer | `/WatchInfo` DataItems |

## Protocol rules

- Treat paths, direction, payload endianness, and stable IDs as one contract.
- A message has no asset support and no durable replay guarantee.
- A DataItem can lag and replay; sequence or content checks are required for ordered state.
- Freeze DataItems retained beyond callback lifetime.
- Bound payload/image/list sizes below Data Layer limits.
- Keep same package and signing identity on both APKs.

## Related notes

- [Phone-watch communication](../02-architecture/phone-watch-communication.md)
- [Protobuf models](protobuf-models.md)
- [Architecture invariants](../04-development/architecture-invariants.md)
