---
title: Face Catalog
aliases:
  - Now-Playing Face Registry
tags:
  - svartifoss/reference
  - wear-ui
summary: The recognized face IDs, current default catalogue, archived compatibility renderers, and architectural traits.
---

# Face Catalog

These are in-app now-playing layouts—not Wear OS system watch faces. The authoritative key set is `ThemeAppearance.ALLOWED_BASE_FACES`; `ArchivedFaces.KEYS` controls normal visibility.

## Normally offered faces

| Key | Name | Architectural character |
| --- | --- | --- |
| `classic` | Classic | original View-based presentation inside the host activity |
| `expressive` | Expressive | Material 3 Expressive-inspired cookie control and contour progress |
| `poster` | Poster | full-bleed artwork-led curated layout |
| `studio` | Studio | curated artwork/typography composition |
| `material` | Material | tonal capsule/surface treatment |
| `immersive` | Immersive | artwork-forward, Spotify-style composition |
| `carousel` | Carousel | current queue cover centered with neighboring covers peeking in |
| `chat` | Chat | current track styled as a message and voice-note waveform; hosts its action row |
| `split` | Split | upper artwork and lower album-colored notification-style panel |
| `note` | Note | minimal cover disc plus one `Artist: Title` sentence |
| `verse` | Verse | previous/current/next synced lyric lines on the player |
| `metadata` | Metadata | compact record/technical/playback detail table |
| `ribbon` | Ribbon | central portrait cover framed by queue-art rails |
| `frame` | Frame | rounded tonal card with artist, title, and wide cover crop |

## Archived compatibility faces

| Key | Name | Preserved characteristic |
| --- | --- | --- |
| `vinyl` | Vinyl | record-inspired curated composition |
| `halo` | Halo | halo/glow artwork treatment |
| `aurora` | Aurora | album-colored atmospheric treatment |
| `eclipse` | Eclipse | true-black AMOLED-oriented face |
| `spectrum` | Spectrum | animated per-track bar field |
| `depth` | Depth | three-layer parallax composition |

Archived faces remain valid identifiers so older stored scopes, imports, and profiles can be interpreted. A developer option can reveal them, but normal public selection excludes them.

## Shared interaction contract

Curated Compose faces generally expose a central play/pause focus, center double tap for quick actions, and a configurable long press. Previous/next/menu/volume remain assignable through mini buttons and gestures unless a face deliberately authors those controls.

Input remains in `MainActivity`; a face consumes state and emits callbacks. A face's AOD variant reuses its geometry while removing animation and using ambient-specific state.

## Special registries

- `ThemeAppearance.QUEUE_ART_FACES`: Carousel and Ribbon need enlarged queue artwork/prefetch.
- `FaceScopedPreferences` per-face defaults: tune standard chrome and background for compositions.
- `MiniButtonPlacement.isHostedByFace`: Chat hosts the configured row inside its composition.
- `PlayerEditorModel` applicability sets: hide controls that a face cannot render honestly.
- `ArchivedFaces.KEYS`: one visibility authority used by phone/watch selectors.

## Adding or retiring a face

Use [Change playbooks](../04-development/change-playbooks.md#add-a-now-playing-face). Registration alone is insufficient: watch construction, ambient rendering, phone miniature, picker arrays, theme name/public constraints, scoped defaults, search visibility, and public copy must agree.

## Related notes

- [Watch UI and appearance](../02-architecture/watch-ui-and-appearance.md)
- [Wear module](../03-codebase/wear-module.md)
- [Preferences and state sync](../02-architecture/preferences-and-state-sync.md)

