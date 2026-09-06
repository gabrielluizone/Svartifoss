---
title: Common and WearUtils
aliases:
  - Shared Core
tags:
  - svartifoss/codebase
  - shared-core
summary: The common Android library, protobuf build, shared policy catalog, resources, and WearUtils submodule.
---

# Common and WearUtils

## `common/`

`common` is an Android library targeting API 30 with minimum API 21 and compiling against 36. It is linked into both APKs and uses the protobuf lite runtime. Its Android manifest is intentionally empty; the module supplies code, resources, and generated data models rather than standalone components.

### Protocol and model

- `CommPaths.kt` is the path/capability registry.
- `src/main/proto/*.proto` generates `com.svartifoss.snfell.proto` lite classes.
- `actions/StandardActions.kt` and `StandardIcons.kt` expose stable shared action identity.
- `buttonconfig/` and pseudo-input objects define stable button/gesture addresses.
- `CustomLists.kt`, `LyricsStatus.kt`, and `TrackMetadataFields.kt` define content contracts.

### Preferences and themes

- `MiscPreferences.kt` is the typed watch-facing preference registry.
- `FaceScopedPreferences.kt` owns scope formation, defaults, and resolution.
- `ThemeAppearance.kt` owns recognized/archived faces and appearance context.
- `CommunityThemeSubmissionPolicy.kt`, `CommunityThemeScreenshots.kt`, and the constraints asset define safe public profile behavior.

### Shared deterministic policy

The module is the preferred home for small, pure decisions whose duplication would let phone and watch disagree:

- position estimation and synchronization;
- queue paging and media ID encoding;
- locale normalization;
- idle/rotary/center-long-press policies;
- album accent selection, harmony, modifiers, and contrast;
- typography, text effects, overflow, and music glyph parsing;
- background layers, shading, cover shapes, panel backdrops, and seek markers;
- layout geometry for round screens, faces, and mini buttons.

Some helpers still use Android drawing types because all consumers are Android. Where Compose needs a shared native pattern, it reaches the same `android.graphics.Canvas` implementation through `nativeCanvas` instead of reauthoring geometry.

### Shared resources

`common/src/main/res/` supplies drawables, animation resources, and strings used by both apps. Every selectable font remains duplicated in `mobile` and `wear` because each APK loads its own resource; corresponding licenses live at repository root.

### Test philosophy

The common test suite is broad because subtle fallback decisions are extracted away from Android components. It also contains registry parity tests such as `ScopedAppearanceRegistryTest` and community defaults/digest tests.

## `wearutils/`

`wearutils` is a checked-out Git submodule pointing to the project's WearUtils fork. It has its own `libs.toml` and retains `com.matejdro.wearutils.*` packages. It provides reusable cross-device facilities including preference transfer, wearable helpers, logging, task integration, and companion-app support.

Initialize it with:

```sh
git submodule update --init
```

Treat changes inside it as submodule work with an independent history. A parent-repository commit records the submodule SHA, not the full inner diff.

## When code belongs in common

Move a decision to `common` when all of these are true:

- phone and watch must interpret the same persisted/wire value;
- the logic can be expressed without a module-specific UI dependency;
- disagreement would fail silently or visually;
- a focused JVM test can describe the fallback table.

Keep actual drawing local when Canvas, Views, and Compose genuinely require different implementations; share geometry, vocabulary, and resolved primitives instead.

## Source anchors

- `common/build.gradle`
- `common/src/main/java/com/svartifoss/snfell/common/`
- `common/src/main/proto/`
- `common/src/test/java/com/svartifoss/snfell/common/`
- `.gitmodules`
- `wearutils/`

## Related notes

- [Preferences and state sync](../02-architecture/preferences-and-state-sync.md)
- [Protobuf models](../05-reference/protobuf-models.md)
- [Testing strategy](../04-development/testing-strategy.md)

