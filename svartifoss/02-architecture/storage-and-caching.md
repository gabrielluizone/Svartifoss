---
title: Storage and Caching
tags:
  - svartifoss/architecture
  - persistence
summary: Where configuration, user data, themes, assets, and transient network results live.
---

# Storage and Caching

Svartifoss distinguishes **user-owned persistent data**, **authoritative configuration**, **durable cross-device projections**, and **disposable caches**. Confusing these lifetimes can break backup, exhaust payload limits, or erase data unexpectedly.

## Storage map

| Data | Phone storage | Watch projection | Lifetime |
| --- | --- | --- | --- |
| General/watch preferences | default `SharedPreferences` | filtered watch `SharedPreferences` via `/Settings` | authoritative user configuration |
| Per-face appearance | scoped keys in default preferences | active scope plus bounded inactive cache | authoritative configuration |
| Local theme library | separate named preferences as JSON | only available-theme index plus active materialized snapshot | user-owned |
| Button configurations | marshalled `PersistableBundle` files | `/Actions/Playback` and `/Actions/Stopped` | user-owned configuration |
| Action menu | marshalled bundle file | `/ActionList` | user-owned configuration |
| Playlist shortcuts | JSON in preferences plus optional artwork files | durable shortcut DataItem | user-owned |
| Search and track history | JSON in preferences | custom lists on demand | user-owned, deletable |
| Community catalogue | cache directory with ETag metadata | none | disposable but offline-friendly |
| Queue remote artwork | dedicated bounded cache directory | DataItem assets in current list | disposable |
| Shortcut artwork | separate persistent asset store | shortcut assets | user-associated and backed up |
| Lyrics | memory only | current response | transient; failures not cached |
| App notification glyphs | persistent package→PNG cache | embedded in transmitted actions/state | learned reusable asset |
| Current album/palette | memory/process cache | current DataItem and local cache | transient |

## Bundle files

Button/action config files use raw `Parcel.marshall()` bytes. Parcel format is not stable across Android releases and may fail lazily when nested values are first accessed. `BundleFileSerialization.readFromFile` therefore forces unparcelling inside a guard, quarantines incompatible files as `.corrupt`, and returns a safe fallback. Writes use a temporary file and atomic rename.

Backups validate every opaque blob before writing any part of an import. Theme-library import is also all-or-nothing.

## Backup

`ConfigBackup` exports:

- both button configurations and the action list;
- `MiscPreferences.EXPORTABLE` preferences;
- the local watch-theme library;
- saved shortcuts, search history, and track history;
- bounded assets associated with supported stores.

A new configuration file or persistent user-data store needs an explicit backup decision. A cache under `filesDir` is not automatically harmless: backup enumeration can count it toward caps or include it unintentionally. Unbounded disposable caches belong under `cacheDir` and need eviction.

## Cache isolation

Shortcut covers and queue covers are both keyed by remote URL, but their ownership is opposite. Shortcut covers belong to saved user entries and survive backup; queue covers are an unbounded transient stream. Sharing a directory let shortcut cleanup erase queue data and let queue accumulation abort backups. They now have separate stores.

The app-glyph cache persists because launcher rows need a media app's monochrome notification glyph precisely when that app has no live notification. A generation counter triggers config retransmission when new glyph knowledge changes rasterized action icons.

## Community cache safety

The gallery uses ETag-conditional fetches. A malformed network response never overwrites the last valid disk copy. Profile URLs inside an index are not trusted; the client derives the profile path from the validated UUID.

## Source anchors

- `mobile/.../util/BundleFileSerialization.kt`
- `mobile/.../config/ConfigBackup.kt`
- `mobile/.../music/PlaylistShortcutStorage.kt`
- `mobile/.../music/ShortcutArtworkStore.kt`
- `mobile/.../music/RemoteArtworkCache.kt`
- `mobile/.../notifications/AppGlyphStore.kt`
- `mobile/.../view/watchface/theme/WatchThemeRepository.kt`
- `mobile/.../view/watchface/theme/OnlineThemesRepository.kt`

## Related notes

- [Preferences and state sync](preferences-and-state-sync.md)
- [Content features](content-features.md)
- [Community themes](community-themes.md)
- [Change playbooks](../04-development/change-playbooks.md)

