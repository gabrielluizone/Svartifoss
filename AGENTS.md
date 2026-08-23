# Repository Guidelines

## Project Structure & Module Organization

Svartifoss is a Groovy-Gradle Android project split into four modules. `mobile/` contains the phone app and uses Dagger 2; `wear/` contains the Wear OS app and uses Hilt. Shared Kotlin, Android resources, and protobuf schemas live in `common/`, while `wearutils/` is a Git submodule. Production code and resources follow `MODULE/src/main/{java,res,proto}`; JVM tests belong in `MODULE/src/test/java`. Store metadata is under `fastlane/`, documentation under `docs/`, and raw design references under `icons/` and `wearmediatemplate/`. Read `CLAUDE.md` before changing communication, preferences, playback, or watch UI behavior; it documents important architectural invariants.

## Build, Test, and Development Commands

Use JDK 21 and configure the Android SDK through root-level `local.properties`.

- `git submodule update --init` initializes `wearutils/` before the first build.
- `./gradlew assembleDebug` builds all debug APKs.
- `./gradlew :mobile:assembleDebug` or `./gradlew :wear:assembleDebug` builds one app.
- `./gradlew test` runs all JVM unit tests.
- `./gradlew :common:testDebugUnitTest` runs a module's tests; add `--tests "*.LyricsParserTest"` to target one class.
- `./gradlew lint` runs Android lint, but the repository has a known backlog. Compare failures with `master` before attributing them to a change.

## Coding Style & Naming Conventions

Follow existing Kotlin/Android style: four-space indentation, braces on the declaration line, `PascalCase` types, `camelCase` functions/properties, and `UPPER_SNAKE_CASE` constants. Keep package names under `com.svartifoss.snfell`. Name resources in lowercase `snake_case`. Prefer small pure helpers in `common/` when phone and watch must behave identically. Centralize Data Layer paths in `CommPaths.kt` and schemas in `common/src/main/proto/`. Both APKs must retain the same `applicationId` and signing key.

## Testing Guidelines

Tests use JUnit 4, with coroutine test utilities where needed. Name files `FeatureTest.kt` and test behavior at the narrowest owning module. Add regression tests for pure policies, parsers, serialization, preference defaults, and shared rendering calculations. Run the affected module suite first, then `./gradlew test` before submitting.

## Commit & Pull Request Guidelines

History favors imperative, scoped subjects such as `fix: order pre-release versions` and `docs: refresh README`, while releases use `Release 3.1: ...`. Keep commits focused and explain the user-visible outcome. Pull requests should include a concise problem/solution description, linked issues, test commands and results, and screenshots or recordings for phone/watch UI changes. Call out protobuf, preference-sync, signing, localization, or minimum-API impacts explicitly.
