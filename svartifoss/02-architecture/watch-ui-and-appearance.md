---
title: Watch UI and Appearance
aliases:
  - Appearance Architecture
  - Face Architecture
tags:
  - svartifoss/architecture
  - wear-ui
  - appearance
summary: How now-playing faces, shared styling policy, phone previews, panels, and ambient presentation fit together.
---

# Watch UI and Appearance

## Hybrid UI architecture

The watch's now-playing host is a mature View-based `MainActivity`. It owns lifecycle, ambient mode, physical/touch/rotary input, overlays, the classic face, mini buttons, and the shared `ComposeView`. Newer now-playing faces are Compose functions selected inside that host. Queue, menu, lyrics, face picker, volume, and progress are separate activities, mostly Compose.

This arrangement separates **input and host lifecycle** from **face composition**. A face receives `NowPlayingFaceState` and emits events; it does not take over device communication or physical-input dispatch.

## Face registry

`ThemeAppearance.ALLOWED_BASE_FACES` recognizes twenty stable renderer IDs:

`classic`, `expressive`, `vinyl`, `poster`, `studio`, `halo`, `aurora`, `eclipse`, `spectrum`, `material`, `immersive`, `depth`, `carousel`, `chat`, `split`, `note`, `verse`, `metadata`, `ribbon`, and `frame`.

Six—Vinyl, Halo, Aurora, Eclipse, Spectrum, and Depth—are archived and hidden from ordinary selection. They remain recognized for saved preferences, imports, published data compatibility, and the developer option that reveals archived choices. The normal catalogue therefore offers fourteen current faces from this source snapshot.

## Composition versus treatment

> [!abstract] Design rule
> A face owns where content goes. Shared appearance policy owns how reusable content is treated.

A Compose face should normally consume:

- `PlayerBackgroundTreatment` for the configured background stack;
- shared title and artist typography helpers;
- resolved title, artist, progress, and palette colors;
- shared mini-button surfaces and placement when it does not host them itself;
- `NowPlayingFaceState.ambient` and ambient-specific artwork/state in AOD.

Exceptions such as Split's opaque two-tone composition, Note's persistent disc, Chat's waveform/action row, or Expressive's authored ring are deliberate and documented because each exception can bypass a user setting.

## Shared appearance engine

`common` owns deterministic vocabulary and decisions that must match on both devices:

- per-face preference resolution and defaults;
- surface palette treatments, harmony, tone modifiers, hue shift, and flattening;
- album accent selection and adaptive text contrast;
- typography specs, title overflow mode, text case, shadow, outline, and backdrop;
- face geometry and round-screen text width;
- cover shapes, mini-button surfaces, panel backdrops, seek marker policy;
- artwork filtering, blur, border trim, and background layer parsing.

The watch and phone still have separate renderers—Compose/View versus Canvas—but they consume the same primitives and geometry.

## Background stack

`wear_background_layers` is an ordered, face-scoped composition of up to eight layers:

- `WASH`: an authored `PlayerBackgroundStyle`;
- `SHADE`: a legibility overlay with strength and color mode;
- `FLOOR`: an accent glow pooled near the lower edge.

Its three storage states matter:

- absent or invalid means “use the legacy implicit stack”;
- `"1"` is an explicit empty stack—bare artwork;
- an explicit valid stack replaces all legacy layers as one composition.

The parser rejects a malformed explicit value as a whole. Silently dropping one unknown layer would render a different theme than the one saved.

## Phone preview

`WatchPreviewView` is a Canvas reimplementation of the wrist presentation because `mobile` cannot depend on `wear`. It has contextual preview surfaces for player, AOD, volume, progress/seek, quick panel, queue, and mini buttons. The Watch tab can feed it real current title/artist/artwork and configured mini-button icons, with sample content as fallback.

This duplication is a major drift risk. Shared policy moves to `common`; `WatchPreviewParityTest` then checks registry coverage, preference reads, drawing reachability, geometry reuse, and selected ambient contracts.

## Always-on display

AOD can follow the chosen Compose face, use classic, or use the independent Chrono face. Ambient rendering is outline-oriented, animation-free, brightness-scaled, and subject to burn-in position movement. It has its own art, track-info, clock, transport, progress, pills, color, and intensity controls.

Ambient faces must use ambient artwork fields rather than the interactive `albumArt`, and must honor the track-info gate. Touch is not delivered to apps while ambient; buttons and rotary input may still be meaningful.

## Secondary surfaces

Queue/menu/lyrics use shared Compose chrome such as curved clock, scroll indicators, and the equalizer-bars loading vocabulary. Dedicated volume and progress activities reuse panel appearance and a process cache of the album palette to avoid an initial fallback-color flash.

## Source anchors

- `wear/.../view/MainActivity.kt`
- `wear/.../view/face/NowPlayingFace.kt`
- `wear/.../view/face/FaceChrome.kt`
- `common/.../ThemeAppearance.kt`
- `common/.../FaceScopedPreferences.kt`
- `common/.../BackgroundLayerStack.kt`
- `mobile/.../view/watchface/WatchPreviewView.kt`

## Related notes

- [Preferences and state sync](preferences-and-state-sync.md)
- [Common and WearUtils](../03-codebase/common-and-wearutils.md)
- [Testing strategy](../04-development/testing-strategy.md)
- [Face catalog](../05-reference/face-catalog.md)

