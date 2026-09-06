---
title: Testing Strategy
tags:
  - svartifoss/development
  - testing
summary: Test layers, pure-policy conventions, structural parity tests, external Node suites, and verification scope.
---

# Testing Strategy

Svartifoss relies primarily on JVM unit tests and source/resource contract tests. There is currently no established instrumented/UI test suite, and the community-theme publishing workflow does not build or test the Android applications.

## Test layers

| Layer | Location | Purpose |
| --- | --- | --- |
| Common JVM tests | `common/src/test/java` | shared parsers, policies, fallback tables, geometry, preference resolution, theme canonicalization |
| Phone JVM tests | `mobile/src/test/java` | media policies, storage, actions, settings resources, preview parity, community client rules |
| Watch JVM tests | `wear/src/test/java` | input/lifecycle/prediction/UI source contracts; Android-dependent tests use Unmock where configured |
| Firestore emulator tests | `firebase/` | deployed security-rule behavior and atomic community flows |
| Publisher Node tests | `.github/community-theme-publisher/` | publication, schema parity, digests, withdrawals, counts, screenshots |

## Preferred unit-test shape

When an Android bug reduces to a decision table, extract that decision into a pure function and pin it directly. Examples include:

- playback position estimate and sync correction;
- paused-service hold and idle behavior;
- rotary, center-long-press, and search-routing fallbacks;
- queue/library ID parsing and paging;
- lyrics parsing;
- palette, contrast, title overflow, and mini-button rules;
- background stack parsing;
- theme digest and submission policy.

This tests the judgment without constructing a service, activity, media session, or rendering tree.

## Structural contract tests

Some correctness exists only across files. Tests therefore read source, XML, JSON, or another module's files to enforce relationships:

- every Watch appearance preference is properly scoped or explicitly global;
- every scoped key is exportable and capturable;
- settings categories are reachable and assigned to sections;
- translated picker arrays remain index-aligned;
- community vocabulary/defaults agree across Kotlin and JavaScript;
- phone preview dispatch covers watch faces/AOD and actually reads visual keys;
- face renderers use the standard title/artist/effect helpers;
- ambient renderers use ambient state correctly;
- theme UI uses established dialogs/loading indicators and avoids removed destructive actions.

`mobile/build.gradle` declares cross-module source/resource/schema files as test inputs. Without those declarations Gradle can mark a parity test up-to-date after the external file changed.

## Commands

Run the narrow owner first:

```sh
./gradlew :common:testDebugUnitTest --tests "*.PlaybackSyncPolicyTest"
./gradlew :mobile:testDebugUnitTest --tests "*.WatchPreviewParityTest"
./gradlew :wear:testDebugUnitTest --tests "*.AmbientFaceContractTest"
```

Then run all JVM tests:

```sh
./gradlew test
```

For community infrastructure:

```sh
npm ci --prefix firebase
npm test --prefix firebase

npm ci --prefix .github/community-theme-publisher
npm test --prefix .github/community-theme-publisher
```

Firebase emulator tests require Node 20+ and JDK 21.

## Choosing verification by change

| Change | Minimum focused verification |
| --- | --- |
| shared policy/parser | its common test class, then common module suite |
| phone-only behavior | relevant mobile tests, then mobile suite |
| watch input/UI policy | relevant wear tests, then wear suite |
| preference/appearance | common registries + mobile resource/preview parity + affected wear tests |
| protobuf or Data Layer | both app module compilation/tests and mixed-version reasoning |
| community schema/rules | common/mobile parity tests, Firebase emulator suite, publisher suite |
| localization picker | translated-array and settings catalog tests |
| release/signing | release builds plus certificate/package verification on both APKs |

## Manual checks

Device testing remains necessary for Bluetooth latency, doze, process death, ambient mode, physical/crown input, notification access, PackageInstaller confirmation, and third-party media-app behavior. Record the phone model/API, watch model/API, media app/version, playback state, and whether either process started cold.

## Lint and CI caveats

Android lint has unrelated backlog; compare against baseline. The one repository workflow is for community-theme publication and is not an app CI gate. Contributors must run the relevant Gradle and Node suites explicitly.

## Related notes

- [Architecture invariants](architecture-invariants.md)
- [Change playbooks](change-playbooks.md)
- [Observability and debugging](observability-and-debugging.md)

