---
title: Preference Domains
aliases:
  - Preference Registry Index
tags:
  - svartifoss/reference
  - preferences
summary: The major preference categories and the distinction between global, scoped, exportable, local, and retired values.
---

# Preference Domains

The primary typed registry is `common/src/main/java/com/svartifoss/snfell/common/MiscPreferences.kt`. This note groups the system conceptually; it does not repeat every key.

## Classification axes

| Axis | Values | Why it matters |
| --- | --- | --- |
| Ownership | phone-owned, watch-local | whether authoritative sync may replace it |
| Reach | phone-only, watch-facing | whether it belongs in a Data Layer snapshot |
| Scope | global behavior, per-face appearance, custom-active snapshot | how storage keys and fallbacks resolve |
| Persistence | user configuration, user data, transient cache | backup and cleanup policy |
| Compatibility | active, legacy alias, retired-but-recognized | whether an old theme/config must still parse |

`EXPORTABLE` means more than “included in a JSON export”: it is the principal registry used by watch synchronization and backup. `FaceScopedPreferences.SCOPED_KEYS` identifies appearance values stored per face. Their intersection, `SCOPED_DEFINITIONS`, is what a theme can fully capture.

## Major watch-facing domains

### Identity and language

- application language;
- selected base face and active custom-theme metadata;
- available custom-theme summary for the wrist picker.

### Behavior and lifecycle

- paused-track hold, idle close, keep-screen-on;
- idle button/auto-open behavior;
- notification timeout and popup behavior;
- rotary action, deadzone, sensitivity;
- center long press and other input behavior;
- auto-start exclusions and watch diagnostics.

Most are global, but keep-screen-on is face-scoped because battery behavior follows the active composition.

### Appearance

- face structure and screen/control theme;
- album-art filter/style, blur, opacity, shading, and ordered background layers;
- album accent source, color treatment/harmony/modifier/hue, surface overrides;
- title, artist, clock, lyrics, and track-time typography and text effects;
- player controls, progress/ring/seek marker;
- mini-button placement/surface;
- quick panel, queue, volume, and progress surface styles;
- AOD style, elements, art treatment, color, and intensity;
- face-specific choices such as Split panel or Carousel/Note cover shape.

These are generally scoped. Some values that appear in the Watch tab are deliberately global because they control phone-side acquisition rather than face rendering—for example remote queue artwork.

### Content and optional integrations

- lyrics lookup and metadata enrichment;
- remote queue artwork and local media permission state;
- streaming-shortcut artwork;
- update checks/prerelease channel;
- crash reporting, announcements, analytics/privacy choices.

Phone-only data such as saved shortcuts, histories, gallery settings, and cache metadata may live in preferences but must not be allowed into the watch snapshot merely because they share the file.

## Scoped resolution

Built-in scope precedence:

`explicit scoped → authored face default → compatible legacy global → definition default`.

Custom context uses a complete `custom_active` snapshot and safe definitions; it does not inherit arbitrary recipient-local styling. Incomplete/schema-mismatched custom metadata falls back to a valid built-in context.

## Retired and legacy keys

A key can stop having a UI row and still remain recognized because old backups or published themes contain it. `WEAR_SCREEN_BUTTONS_OFFSET` is a representative retired appearance key: nothing current reads it for placement, but deleting it from the public parser would reject otherwise valid older profiles.

Historical storage names may also remain for lossless migration even when the user-facing concept has broadened. Treat key renaming as a data migration, not cleanup.

## Phone-local and watch-local exceptions

Watch face-recency timestamps remain watch-local and outside `EXPORTABLE`; they describe how one wrist orders its picker, not the theme. Phone gallery screenshot-display preference is phone-local. The durable preference receiver removes only keys previously identified as phone-owned, so genuine watch-local keys survive sync.

## Related notes

- [Preferences and state sync](../02-architecture/preferences-and-state-sync.md)
- [Storage and caching](../02-architecture/storage-and-caching.md)
- [Source-of-truth matrix](source-of-truth-matrix.md)

