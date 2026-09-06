---
title: Content Features
aliases:
  - Queue Search Lyrics and Metadata
tags:
  - svartifoss/architecture
  - media-content
summary: Cross-device architecture for queues, browsing, search, streaming shortcuts, lyrics, and track metadata.
---

# Content Features

Svartifoss uses a generic **custom-list** protocol for watch content while keeping expensive or transient details on demand. The phone performs media-app and network work; the watch supplies the user-facing query and presents the result.

## Queue and history

`OpenPlaylistAction` asks `MusicService.resolvePlaybackQueue()` for the best source:

1. the selected session's queue;
2. a sibling session for the same package that publishes a queue;
3. recent track history when no queue exists.

Queue entries encode both the queue ID and media ID. Selection returns to the exact controller that issued the queue. Svartifoss tries `skipToQueueItem`, verifies playback identity after a delay, and may fall back to playing the app-issued media ID through its browser service.

Pages are cumulative prefixes: normally 20 entries at first, up to 200. The watch requests a larger limit; the phone replaces the DataItem with that larger prefix and reports the total count. Autonomous refreshes reuse the last requested limit so an open 100-row queue does not collapse to 20 on the next track.

Artwork follows a cheapest-first resolver:

`iconBitmap → local icon URI → description artwork URI → MediaStore → remote HTTP(S) → current-track cover fallback`.

Remote requests are concurrent, bounded, resized, cached separately from user-owned shortcut assets, and passed through border trimming.

## Search and library

Search input uses voice or keyboard on the watch, returns to the phone, and prefers the current app's `MediaBrowserService` search path. Search history is stored on the phone and synchronized as a custom list.

Library browsing walks one `MediaBrowserService` page at a time. Encoded entry IDs distinguish browsable, playable, and parent rows. A media ID is opaque and only meaningful to the app that issued it; Svartifoss never invents one or sends a browsable ID to a playback command.

## Streaming shortcuts

Saved links recognize YouTube Music, Spotify, Deezer, TIDAL, Apple Music, Amazon Music, SoundCloud, Qobuz, Bandcamp, Audiomack, Mixcloud, Pandora, and a generic fallback. They are link integrations, not service-account APIs. Recognition supplies the service label, Android package target, and local content classification; it does not grant Svartifoss access to an account or catalogue.

The action picker also exposes the few account-wide routes that do not require a user-specific ID: YouTube Music Liked Music (normal and shuffled), Spotify Liked Songs, SoundCloud Likes, and Deezer Flow. Other personal libraries must be added through their own share links because inventing a provider-specific account URL would be unreliable.

Playback uses a ladder:

1. issue `playFromUri` or an appropriate search against any active session for the target package and verify it changed playback;
2. connect to the app's media-browser service and try URI/search playback without opening an activity;
3. ask the watch to open the deep link on the phone, then nudge the warmed session.

The watch waits for `MESSAGE_DEEP_LINK_VERDICT` before visible opening. A backstop handles a lost reply, but normal success and failure paths send explicit verdicts.

Watch rows encode visible fallbacks as `targetPackage|uri`. The phone unwraps that transport envelope before passing the URI into `playDeepLink`; a bare link, or a link containing a pipe without a valid Android package prefix, remains untouched.

Optional shortcut thumbnails come from public oEmbed data, are cached as user-associated assets, travel with the shortcut list, and are included in backup rules. This store must remain separate from disposable queue artwork.

## Lyrics

The watch requests lyrics only when a lyrics surface or the Verse face needs them. The phone queries LRCLIB by title, artist, and duration; results are cached in memory only and failures are not cached. Raw LRC is parsed on the watch by the shared `LyricsParser`.

The response echoes track identity and distinguishes synced, plain, none, failed, and disabled states. A response for a previous track is dropped. Tapping a line re-anchors the watch clock and sends an absolute seek without trusting unreliable capability flags.

## Track metadata

Metadata is excluded from frequently pushed `MusicState`. The Metadata face asks for it per track:

1. immediate tags from `MediaMetadata` and playback state;
2. local-file bitrate/sample rate/channels/size when the URI and permission allow it;
3. an optional later MusicBrainz enrichment response.

The phone can answer twice. `enriched` distinguishes the second response, and track identity prevents cross-track races. Network URIs are displayed but never opened by the file metadata reader.

## Source anchors

- `common/.../CustomLists.kt`
- `mobile/.../actions/OpenPlaylistAction.kt`
- `mobile/.../music/MediaBrowserLibrary.kt`
- `mobile/.../music/MediaBrowserSearch.kt`
- `mobile/.../music/StreamingShortcutLinks.kt`
- `mobile/.../music/QueueArtworkResolver.kt`
- `mobile/.../music/LyricsRepository.kt`
- `mobile/.../music/TrackMetadataReader.kt`
- `wear/.../view/queue/`
- `wear/.../view/lyrics/`
- `wear/.../view/metadata/MetadataFeed.kt`

## Related notes

- [Playback and media sessions](playback-and-media-sessions.md)
- [Storage and caching](storage-and-caching.md)
- [External integrations](../05-reference/external-integrations.md)
