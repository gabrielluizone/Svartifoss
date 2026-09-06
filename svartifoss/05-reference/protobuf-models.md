---
title: Protobuf Models
aliases:
  - Wire Model Index
tags:
  - svartifoss/reference
  - protobuf
summary: The proto2 schema inventory, field intent, and mixed-version compatibility rules.
---

# Protobuf Models

All seven schemas live in `common/src/main/proto/`, use proto2, generate multiple Java-lite classes in `com.svartifoss.snfell.proto`, and are exported by `common` through `protobuf-javalite`.

## Schema index

### `actions.proto`

`WatchActions` carries a collection of nested `ProtoButtonInfo` mappings plus the phone's volume step. Each mapping can include physical/pseudo button identity, gesture, stable action key, resolved title, icon tintability, and optional remote URI.

`WatchList` carries action-menu entries with title, stable key, tintability, remote URI, and explicit cover-art identity. Icon bytes travel as DataItem assets rather than protobuf fields.

### `customList.proto`

`CustomList` identifies the list and revision timestamp, contains entry ID/title/subtitle rows, can mark the active entry, and reports total count for cumulative paging. `CustomListItemAction` returns list and entry IDs for selection/deletion.

Queue/library entry IDs contain an internal encoding defined in shared Kotlin. Treat the entire string as opaque outside that helper.

### `lyrics.proto`

`LyricsRequest` names title, artist, and duration. `LyricsResponse` echoes them, reports a stable status code, and contains raw synced LRC or plain text. Echoing identity lets the watch reject a response that crossed a skip.

### `metadata.proto`

`TrackMetadata` is an on-demand, mostly optional snapshot. It can include:

- core/release/credit tags from the player;
- source app and free description;
- local-file technical details;
- optional MusicBrainz identifiers/enrichment;
- URI/file name and playback/output/download state;
- an `enriched` marker for the optional second response.

Absent means unknown, not an empty display row. It is deliberately separate from frequently sent `MusicState`.

### `music.proto`

`MusicState` is the central mirror: title, artist, playing/error, position/duration/speed, volume, seekability, shuffle/repeat/liked state, artwork-pending flag, notification/session media actions, source-icon type, monotonic sequence, and sample age.

`MediaAction` carries an opaque execution ID, optional label/icon PNG, and locale-independent semantic presentation hint. The semantic is never an execution token.

`PlaybackSync` echoes the watch token and returns session/position/speed/age plus track identity for race rejection.

### `notifications.proto`

`Notification` is a small legacy/shared popup model containing title, optional description, and time.

### `watch.proto`

`WatchInfo` reports roundness, density, display dimensions, timestamp, app version, hand-gesture availability code, and repeated hardware buttons with stable code/label/long-press support. Physical-button images are attached assets.

## Compatibility discipline

1. Never reuse, renumber, or change the meaning/type of a published field.
2. Add new behavior with optional fields and safe absence defaults.
3. Retain old fields during mixed-version transitions. `positionUpdateTime` remains beside `positionAgeMs` for older peers.
4. Use explicit codes, not enum ordinals; the APKs update independently.
5. Do not serialize Android-only objects such as `PendingIntent` or `Bitmap`. Keep ownership on the phone and send opaque IDs/assets/bytes.
6. Echo request identity when a delayed answer would be dangerous for a new track.
7. Keep high-volume or on-demand data out of `MusicState`.

## Generated-code workflow

Edit only `.proto` files. Gradle's protobuf plugin runs protoc and the lite generator. A schema change should compile both APKs and be reviewed against old-sender/new-receiver and new-sender/old-receiver cases.

## Related notes

- [Communication contracts](communication-contracts.md)
- [Playback and media sessions](../02-architecture/playback-and-media-sessions.md)
- [Change playbooks](../04-development/change-playbooks.md)

