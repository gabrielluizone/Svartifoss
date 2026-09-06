---
title: Change Playbooks
aliases:
  - Engineering Checklists
tags:
  - svartifoss/development
  - checklists
summary: Practical cross-file checklists for common Svartifoss changes.
---

# Change Playbooks

Read the relevant section of root `CLAUDE.md` before using these abbreviated checklists. Search for an existing sibling feature and its tests; source evolves faster than any checklist.

## Add a phone-executed action

- Create the `PhoneAction` subtype under `mobile/.../actions/`.
- Add/configure its `ActionHandler<T>`.
- Bind the handler in `mobile/.../di/ActionHandlersModule.kt`.
- Expose it through the appropriate picker list (`RootActionList`, `PlaybackActionList`, `VolumeActionList`, or another category).
- Add stable shared identity/icon metadata if the watch must recognize it.
- Verify bundle serialization, title/icon/tint semantics, and remote URI behavior.
- Add focused policy/handler tests and check both playing/stopped contexts.

## Add or change an input

- Reserve a stable, non-colliding code in `common`; never reuse an existing value.
- Define supported gesture kinds explicitly.
- Add phone configuration UI and watch input capture.
- Ensure playing/stopped providers can both address it.
- Report hardware/platform availability when the input is optional.
- Preserve right-swipe dismiss and existing Compose/View dispatch boundaries.

## Add a watch-facing behavior preference

- Add a typed `PreferenceDefinition` in `MiscPreferences` with safe unknown/default behavior.
- Include it in `EXPORTABLE`.
- Add phone XML/editor UI, section catalog, search routing/dependencies, and translations.
- Read/apply it on the watch.
- Confirm backup and synchronization semantics.
- Add pure/default and resource reachability tests.

## Add a per-face appearance preference

Do everything above, plus:

- add the key to `FaceScopedPreferences.SCOPED_KEYS`;
- ensure both string/boolean/integer resolution paths honor per-face defaults;
- add the phone preview read and drawing behavior;
- add watch renderer/state behavior across applicable faces and AOD if relevant;
- update `SCOPED_DEFINITIONS` indirectly through `EXPORTABLE` membership;
- update community constraints, numeric ranges/value vocabulary, shared defaults fixture, publisher schema, and moderator schema;
- update contextual editor/search routing and visibility rules;
- run scoping, registry, vocabulary, numeric-range, preview, and UI contract tests.

## Add a now-playing face

- Add a stable key to `ThemeAppearance.ALLOWED_BASE_FACES`; decide whether it is archived.
- Implement interactive and ambient rendering through the `NowPlayingFace` contract.
- Wire the key into `MainActivity` face selection/construction.
- Add phone picker labels/values, theme name mapping, AOD visibility, and full preview rendering.
- Define per-face defaults rather than hardcoding exceptions at draw sites.
- Decide mini-button hosting/placement, edge ring, artwork, queue-art prefetch, control-style applicability, and center gesture behavior.
- Update Firestore/public-theme face allowlists and constraints.
- Run preview/AOD/title/artist/scoped-default tests.
- Update public face counts/names only after checking archived versus normally offered status.

## Add a protobuf field

- Add a new field number; never renumber or change the meaning/type of an old one.
- Prefer `optional` with an explicit safe default.
- Define old-sender/new-receiver and new-sender/old-receiver behavior.
- Keep legacy fields if released peers still depend on them.
- Update both producers and consumers and add mixed-absence tests.
- If the field is a semantic enum, use explicit stable codes rather than ordinal position.

## Add a Data Layer path

- Add it only in `CommPaths.kt`.
- Choose Message, DataItem, asset, or channel based on immediacy, durability, size, and wake-up needs.
- Wire runtime and/or manifest listeners deliberately.
- Define idempotence, ordering, duplicate, timeout, and process-death behavior.
- Keep transient UI replies under `/Messages`; reserve `/IdleMessages` for work worth waking a process.
- Test package/signing assumptions on a real phone-watch pair.

## Extend a community-theme setting or value

- Update Kotlin preference/scoping/default authority.
- Update `community-theme-constraints.json`.
- Update `.github/community-theme-publisher/schema.mjs`.
- Update `docs/admin/theme-profile-schema.mjs`.
- Update `community-theme-defaults.properties` where relevant.
- Update numeric ranges or layer grammar in every mirror.
- Run Android parity tests and publisher tests; run Firebase tests when rule validation changes.
- Confirm the canonical digest remains identical between Kotlin and Node.

## Add a language or picker value

Follow [Localization](localization.md). In particular, add resources to all three modules and keep every localized `entries` array at exactly the same indices as the unlocalized `entryValues` array.

## Add a network path or permission

- Establish the user action/default and graceful-offline behavior.
- Keep application network work on the phone unless there is a documented exception.
- Add timeouts, byte/type validation, redirects, cache bounds, and cancellation ownership.
- Update the maintained Markdown and HTML privacy policies and relevant Data Safety notes.
- Ensure an optional telemetry/statistics write cannot make the primary user action fail.

## Add a cache or persistent store

- Classify it as authoritative user data, learned persistent data, or disposable cache.
- Choose `filesDir`, preferences, or `cacheDir` accordingly.
- Define keying, invalidation, eviction, corruption recovery, and concurrency.
- Make an explicit backup/import decision and update caps/stores if included.
- Keep unrelated ownership classes in separate directories.

## Related notes

- [Architecture invariants](architecture-invariants.md)
- [Testing strategy](testing-strategy.md)
- [Source-of-truth matrix](../05-reference/source-of-truth-matrix.md)

