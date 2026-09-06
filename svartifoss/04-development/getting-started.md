---
title: Getting Started
aliases:
  - Build Setup
tags:
  - svartifoss/development
  - build
summary: Prerequisites, initial checkout setup, common Gradle commands, and local APK serving.
---

# Getting Started

## Prerequisites

- JDK 21
- Android SDK with the compile SDK/build tools required by the module Gradle files
- Git with submodule support
- Node.js 20 or newer for Firebase and community-publisher tests
- A phone and Wear OS watch or corresponding emulators for end-to-end behavior

Create root `local.properties` with the SDK path, typically:

```properties
sdk.dir=/absolute/path/to/android-sdk
```

The file is local and ignored. The phone applies Google Services and Firebase plugins; a valid ignored `mobile/google-services.json` is also required for a fresh full build. Obtain project configuration through the maintainer's private setup process—do not invent or commit one.

## Initialize the repository

```sh
git submodule update --init
```

This populates `wearutils/`. Gradle sync can fail in misleading ways when the directory exists but the submodule contents are absent.

## Build

```sh
./gradlew assembleDebug
./gradlew :mobile:assembleDebug
./gradlew :wear:assembleDebug
```

The first builds all debug APKs; the next two isolate one application.

## Test

```sh
./gradlew test
./gradlew :common:testDebugUnitTest
./gradlew :mobile:testDebugUnitTest
./gradlew :wear:testDebugUnitTest
```

Target a single class with Gradle's test filter:

```sh
./gradlew :common:testDebugUnitTest --tests "*.LyricsParserTest"
```

The separate Node suites are documented in [Testing strategy](testing-strategy.md).

## Lint

```sh
./gradlew lint
```

The repository has a known Android lint backlog and the task is not currently a clean gate. Compare a failure with the baseline branch before attributing it to your change. Structural JVM tests intentionally cover several contracts that lint would otherwise help catch.

## Install and serve APKs

Use ordinary `adb install`/Android Studio deployment when devices are connected. The root helper can also serve assembled debug/release APKs and AABs over the local network:

```sh
python3 serve_apk.py
```

It listens on port `8760`; build first, then open the machine's local-network address from the target device. Do not expose that development server to an untrusted network.

## First source files to read

1. root `CLAUDE.md` for architectural invariants;
2. [System architecture](../02-architecture/system-architecture.md);
3. the module note for [mobile](../03-codebase/mobile-module.md), [wear](../03-codebase/wear-module.md), or [common](../03-codebase/common-and-wearutils.md);
4. the relevant focused tests.

## Toolchain snapshot

At this vault snapshot, root `libs.toml` and wrapper/build files specify Kotlin 2.2.21, Android Gradle Plugin 8.13.1, Gradle 9.2.1, Dagger/Hilt 2.57.2, protobuf 3.25.5, and Java/Kotlin target 21. Treat these as time-stamped values; check source before upgrading or publishing them elsewhere.

## Related notes

- [Repository map](../03-codebase/repository-map.md)
- [Testing strategy](testing-strategy.md)
- [Releases and signing](releases-and-signing.md)

