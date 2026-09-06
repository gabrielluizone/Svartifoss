---
title: Feature Map
tags:
  - svartifoss/product
  - features
summary: A capability map of the watch, phone, shared system, and optional online features.
---

# Feature Map

## On the watch

### Playback and navigation

- Play, pause, toggle, stop, previous, next, restart, seek, fast-forward, rewind, playback speed, volume, mute, shuffle, repeat, repeat-one, and player-specific like/favorite actions where supported.
- A now-playing experience with a compatibility registry of twenty face renderers. Six legacy faces are archived and hidden by default; the registry preserves them for old preferences and developer access.
- Dedicated queue, menu, lyrics, volume, progress, and face-picker activities.
- Predictive playback position with periodic round-trip correction, so progress and synced lyrics do not depend on matching phone and watch clocks.

### Inputs

- Four screen quadrants.
- Full-screen swipes up, down, and left; right swipe remains the Wear OS dismiss gesture.
- Physical stem buttons with single, double, and long press where supported.
- Digital crown or touch-bezel rotary input for volume, seek, configurable discrete actions, or off.
- Up to three visible mini buttons plus three round quick-panel slots and one long row.
- Configurable center tap and center long-press behavior.
- Wear OS primary one-handed gesture—currently double pinch on supported hardware—when the platform reports it available.

### Content and surfaces

- Live playback queue with paging and per-row artwork when the player exposes one; recent history as an honest fallback.
- Voice or keyboard search, search history, playlist shortcuts, and media-library browsing through a player's `MediaBrowserService` when available.
- Synced lyrics from LRCLIB on demand and a metadata face/screen with player tags, local-file details, playback route, and optional MusicBrainz enrichment.
- A media Tile, a playlist-shortcuts Tile, and an album-art/title complication.
- An on-watch picker for built-in faces and saved custom themes.

## On the phone

### Configuration

- Separate controls for “music playing” and “no playback,” presented as states of one Controls feature.
- An action picker with playback, volume, watch-screen, find-music, streaming-shortcut, app-launch, and optional Tasker actions.
- An action-menu editor for commands that do not need a dedicated input binding.
- Settings organized into General, Behavior/watch, Automation, Apps, and Data & support sections.
- Search across both general settings and the large Watch appearance surface.

### Appearance

- A live watch preview that can use current phone playback or sample media.
- Per-face appearance storage, contextual editors for typography, color, panels, player, and ordered background layers.
- Named local themes that can be saved, applied, duplicated, renamed, and deleted.
- Independent treatment of face composition, album artwork, color harmony, typography, controls, progress, overlays, and always-on display.

### Data and maintenance

- Configuration export/import, including button configs, action list, exportable watch settings, theme library, shortcuts, search history, and track history.
- Built-in update checks, phone APK installation, and watch APK transfer over the Data Layer.
- Optional diagnostics, announcement notifications, and watch-log forwarding.

## Community themes

- Account-free browsing from a static GitHub Pages catalogue.
- Search, base-face filters, author filters, sorting, local previews, installation, and removal.
- Private per-account reactions with silent anonymous authentication when needed.
- Google identity only for authors submitting a theme.
- Moderated publication through Firestore intake, a trusted GitHub Actions publisher, and committed static JSON.
- Optional author-supplied watch photo, constrained, stripped of EXIF by re-encoding, and reviewed before publication.

## Player-dependent capabilities

| Capability | What the media app must expose |
| --- | --- |
| Basic transport | A usable Android media session |
| Queue | A session queue, possibly on a sibling session |
| Library browsing | A discoverable `MediaBrowserService` tree |
| Search | Media-browser or media-session search support |
| Like/custom commands | Notification or session custom actions |
| Rich metadata | Published metadata tags and/or a locally readable media file |
| Precise shortcut playback | Direct URI/search/browser support; otherwise a visible app fallback |

## Related notes

- [Controls and actions](../02-architecture/actions-and-input.md)
- [Playback and media sessions](../02-architecture/playback-and-media-sessions.md)
- [Watch UI and appearance](../02-architecture/watch-ui-and-appearance.md)
- [Community themes](../02-architecture/community-themes.md)

