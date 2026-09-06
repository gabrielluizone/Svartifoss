---
title: Source-of-Truth Matrix
aliases:
  - Authority Matrix
tags:
  - svartifoss/reference
  - invariants
summary: Primary authorities, required mirrors, and guard tests for distributed Svartifoss contracts.
---

# Source-of-Truth Matrix

| Contract | Primary authority | Mirrors/consumers that must agree | Representative guard |
| --- | --- | --- | --- |
| Data Layer paths | `common/.../CommPaths.kt` | phone/watch senders, runtime and manifest receivers | compile + end-to-end checks |
| Wire fields | `common/src/main/proto/*.proto` | both generated consumers | focused model tests and mixed-version review |
| Application identity | module `applicationId` and shared release key | both APKs, update validation | APK metadata/certificate verification |
| Watch preferences | `MiscPreferences` definitions and `EXPORTABLE` | phone XML/editors, coordinator, watch readers, backup | scoped/exportable/resource tests |
| Face scope | `FaceScopedPreferences` | phone data store/preview, watch resolver, theme capture | `AppearancePreferenceScopingTest`, `ScopedAppearanceRegistryTest`, scoped-default tests |
| Face registry | `ThemeAppearance.ALLOWED_BASE_FACES` + `ArchivedFaces.KEYS` | watch constructor/picker, phone arrays/preview/themes, Firestore constraints, public copy | preview/rules/catalog tests plus manual prose review |
| Appearance geometry | `common/.../FaceGeometry.kt` | Compose/View faces and phone Canvas preview | `WatchPreviewParityTest` |
| Palette/treatment policy | shared resolvers/enums in `common` | watch host/faces, preview, editor swatches | focused common and rendering-contract tests |
| Background stack | `BackgroundLayerStack.kt` and shared vocabularies | four renderers, editor, public theme schema | parser/vocabulary/parity tests |
| Settings navigation | `SettingsCatalog.kt` plus preference XML | section fragments and search index/routing | `SettingsCatalogTest`, routing tests |
| Localized picker mapping | default `entryValues` index | every translated `entries` array | `TranslatedArrayAlignmentTest` |
| Font catalogue | watch font resolver + resource fonts | phone `WatchFontCatalog`, arrays, both APK resources, `licenses/` | font license/metrics and vocabulary tests |
| Public theme keys/types | scoped definitions + constraints JSON | Kotlin parser, publisher schema, moderator schema | defaults/vocabulary/numeric parity tests |
| Public theme digest | `CommunityThemeSubmissionPolicy` canonical format | publisher Node implementation | Kotlin/Node shared fixture tests |
| Theme screenshots | `CommunityThemeScreenshots.kt` contract | Firestore rules, Android encoder, publisher | `CommunityThemeScreenshotContractTest`, Node/rules tests |
| Community security | `firestore.rules` | client batches, moderator UI, publisher finalization | Firebase emulator suite |
| Public catalogue output | trusted publisher | `docs/themes/`, Android catalogue parser | publisher tests + strict client parser |
| Privacy behavior | application implementation | Markdown source, HTML published mirror, Data Safety drafts | manual review; dates/text must stay aligned |
| Media compatibility observations | current player source/device testing | `docs/player-integration-notes.md` | repeat against named app/version |
| Release discoverability | GitHub release tag/assets | `UpdateChecker`, exact filenames | staged release/update test |

## Reading the matrix

“Primary authority” does not mean other files can be generated automatically. Several mirrors are in different languages or UI frameworks and must be updated manually. Guard tests reduce drift but do not remove the need to understand the relationship.

The most dangerous failures are those with no exception: a setting that saves and previews but never reaches the watch, a face that compiles but ignores one control, a theme rejected by Firestore after passing phone UI, or a phone/watch pair that silently cannot discover each other.

## Related notes

- [Architecture invariants](../04-development/architecture-invariants.md)
- [Change playbooks](../04-development/change-playbooks.md)
- [Testing strategy](../04-development/testing-strategy.md)

