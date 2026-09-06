---
title: Repository Map
tags:
  - svartifoss/codebase
  - repository
summary: A directory-by-directory guide to the Svartifoss repository.
---

# Repository Map

## Root layout

```text
Svartifoss/
├── mobile/                 phone Android application
├── wear/                   Wear OS Android application
├── common/                 shared Android library and protobuf schemas
├── wearutils/              Git submodule/library
├── docs/                   GitHub Pages, privacy, plans, public catalogue, admin UI
├── firebase/               Firestore emulator test suite
├── .github/
│   ├── workflows/          community-theme publisher workflow
│   └── community-theme-publisher/  trusted Node publisher and tests
├── fastlane/               retained store metadata, not current distribution
├── licenses/               per-font license texts
├── icons/                  raw icon design references
├── wearmediatemplate/      Wear media design references
├── archived/               parked historical artifacts
├── gradle/                 Gradle wrapper
├── build.gradle            root plugin/repository/signing behavior
├── settings.gradle         module inclusion and version catalogs
├── libs.toml               dependency version catalogue
├── firestore.rules         deployed community-data security boundary
├── firebase.json           Firebase emulator/deployment configuration
├── README.md               public project landing document
├── CHANGELOG.md            user-facing release history
├── CLAUDE.md               architectural invariants and implementation rationale
└── COPYING                 GPL-3.0 license
```

## Module source convention

Production code and resources follow `MODULE/src/main/{java,res,proto,assets}`. JVM tests follow `MODULE/src/test/java`. There is currently no established instrumented/UI test suite.

`common/src/main/proto/` contains seven proto2 schemas. Generated sources are build output and should not be hand-edited.

## Shipping infrastructure outside Gradle

Three areas are not Android build modules but are part of the shipped community-theme system:

- `firestore.rules` and `firebase.json`;
- `firebase/`, the Node/emulator rules suite;
- `.github/community-theme-publisher/` and its workflow.

Likewise, `docs/` is not merely prose. It is the GitHub Pages root, contains the static public theme catalogue and moderation page, and hosts the published privacy policy.

## Reference and historical material

- `icons/` holds raw Material Symbols and other source references used before conversion to Android drawables.
- `wearmediatemplate/` preserves Google's Wear media-control design guidance.
- `licenses/` is a shipping compliance input for bundled fonts.
- `fastlane/` and `docs/play-console-*.md` are retained material from a shelved store attempt.
- `archived/` and local checkouts such as `echo/`, `retromusic/`, and `redesign/` are not production build inputs.

Do not infer current behavior from root APKs, screenshots, draft announcements, or scratch files. Consult [Source guide](../00-orientation/source-guide.md).

## Local-only requirements

- `local.properties` supplies the Android SDK path and is ignored.
- `mobile/google-services.json` is ignored but required by the phone module's Google Services plugin.
- `keystore.properties` and the release keystore are private signing material; never publish or document their values.
- `wearutils/` must be initialized as a Git submodule before Gradle sync/build.

## Root build behavior

`settings.gradle` includes `mobile`, `wear`, `wearutils`, and `common`, and loads version catalogs from root `libs.toml` plus `wearutils/libs.toml`. The root `build.gradle` provides plugins, repositories, dependency-update policy, and the unusual shared signing override described in [Releases and signing](../04-development/releases-and-signing.md).

## Related notes

- [Getting started](../04-development/getting-started.md)
- [Existing documentation](../05-reference/existing-documentation.md)
- [Source-of-truth matrix](../05-reference/source-of-truth-matrix.md)

