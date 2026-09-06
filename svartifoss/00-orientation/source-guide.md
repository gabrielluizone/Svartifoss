---
title: Source Guide
aliases:
  - Evidence Guide
tags:
  - svartifoss/orientation
  - documentation
summary: How to distinguish executable authorities, maintained documentation, design records, and historical drafts.
---

# Source Guide

Svartifoss has unusually rich repository guidance, but not every document describes the same moment in the product's life. This hierarchy keeps public explanations grounded.

## Authority order

1. **Executable code and tests** are the authority for current behavior.
2. **`CLAUDE.md`** records architectural invariants and the reasons behind many non-obvious implementations.
3. **Maintained public documents**—`README.md`, `CHANGELOG.md`, `docs/index.html`, and the privacy policy—describe releases and public commitments.
4. **Design records** explain why a feature took its current shape, even when their filenames still say “plan.”
5. **Draft store material and archived files** are historical context, not current distribution policy.

When two sources disagree, first ask whether one describes a release and the other the current working tree. The current checkout may be ahead of the latest released APK.

## Current authorities by subject

| Subject | Primary authority |
| --- | --- |
| Module structure and build metadata | `settings.gradle`, root and module `build.gradle`, `libs.toml` |
| Phone↔watch path names | `common/src/main/java/com/svartifoss/snfell/common/CommPaths.kt` |
| Wire schemas | `common/src/main/proto/*.proto` |
| Watch-facing preferences | `MiscPreferences.kt` and `FaceScopedPreferences.kt` |
| Built-in face registry | `ThemeAppearance.ALLOWED_BASE_FACES` |
| Android components and permissions | each module's `AndroidManifest.xml` |
| Public theme schema | Kotlin constraints, checked-in JSON asset, publisher schema, moderator schema, and parity tests together |
| Player compatibility observations | `docs/player-integration-notes.md` |
| Privacy commitment | `docs/privacy-policy.md` plus its manually synchronized HTML twin |
| Release history | `CHANGELOG.md` and module version fields |

The more detailed [Source-of-truth matrix](../05-reference/source-of-truth-matrix.md) names mirrors and guard tests.

## Document status

- `docs/online-themes-plan.md` is a design record for a feature that shipped, not an unimplemented proposal. Theme updates remain unbuilt.
- `docs/theme-screenshots-plan.md` amends the earlier community-theme preview decision and describes the shipped author-photo path.
- `docs/wear-modernization-plan.md` began as a roadmap; the media-session mirror, Tiles, complication, and Compose screens it proposes now exist.
- `docs/play-console-*.md` files are retained drafts from a shelved store attempt.
- `fastlane/` contains store-listing metadata for a possible future attempt; it is not evidence that Svartifoss is currently on the Play Store.
- The retired `docs/fdroid/` repository must not be treated as a current channel.

## Public-writing rule

Prefer stable truths and name volatile values as snapshots. For example, “the face registry is defined in `ThemeAppearance`” ages better than repeating a count without also naming the registry. Exact counts, version codes, SDK levels, quotas, and defaults should be checked against code before publication.

## Related notes

- [Existing documentation](../05-reference/existing-documentation.md)
- [Source-of-truth matrix](../05-reference/source-of-truth-matrix.md)
- [Documentation maintenance](../04-development/documentation-maintenance.md)

