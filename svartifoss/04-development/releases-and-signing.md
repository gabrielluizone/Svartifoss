---
title: Releases and Signing
tags:
  - svartifoss/development
  - releases
  - signing
summary: The two-APK versioning, certificate, asset naming, and built-in update contract.
---

# Releases and Signing

Svartifoss ships two sideloaded APKs as one distributed application. Release correctness includes version metadata, package identity, signing identity, asset names, changelog/public copy, and mixed phone/watch compatibility.

## Current source snapshot

At the vault snapshot, module files declare:

| Module | `versionCode` | `versionName` |
| --- | ---: | --- |
| `mobile` | 61 | 3.3 |
| `wear` | 161 | 3.3 |

The codes are intentionally independent and must each increase monotonically. Their gaps include history from the shelved Play Console attempt. The shared name should remain aligned. The checked-out tree also contains unreleased work and may be ahead of the most recent Git tag; do not infer release status solely from `versionName`.

## Signing shape

Both module release build types point at `signingConfigs.debug`. The root `build.gradle` optionally rewrites that configuration from ignored `keystore.properties` when private release credentials are present. As a result:

- with the private file, debug and release variants use the long-lived project key and can update an installed project build in place;
- without it, builds fall back to the ordinary debug key, including release variants;
- both APKs must resolve to the same certificate.

This is unusual but intentional. Do not “clean up” the release signing configuration without understanding the update and Data Layer consequences.

Never commit the keystore, `keystore.properties`, passwords, aliases, certificates containing private material, or generated credentials.

## Release checklist

1. Review root `CLAUDE.md` and close the intended `CHANGELOG.md` section.
2. Increase each module's `versionCode` monotonically.
3. Set the same `versionName` in both modules; use a suffix such as `-betaN` for prereleases.
4. Run the affected tests, all JVM tests, and required Node suites.
5. Build both release APKs:

   ```sh
   ./gradlew :mobile:assembleRelease :wear:assembleRelease
   ```

6. Inspect package name, version, and certificate for each APK with Android build tools such as `apkanalyzer`/`aapt` and `apksigner verify --print-certs`.
7. Confirm the certificates match each other and the installed release lineage.
8. Exercise phone↔watch connection and update/install flows on devices.
9. Publish a GitHub release tagged with the shared version, marking prereleases appropriately.
10. Attach assets with the exact names:

    - `mobile-release.apk`
    - `wear-release.apk`

The updater matches those filenames exactly.

## Version comparison

`UpdateChecker.isNewer` handles semantic prerelease precedence: for example `3.1-beta1 < 3.1-beta2 < 3.1`. Very old clients predating that fix may need a numerically higher release to escape their old comparison behavior.

## Built-in updater contract

The phone queries GitHub Releases, validates downloaded APK length/package, and uses `PackageInstaller`. A watch update is downloaded on the phone, streamed through `/Channel/WearApk`, validated again on the watch, then committed through the watch's installer. User confirmation is surfaced by notification when required.

The watch reports its version through optional `WatchInfo` fields so the phone can decide whether the watch asset is newer. Absence is valid for an older watch build.

## Distribution truth

GitHub Releases is the current channel. The F-Droid repository is retired; Play Console and Fastlane content is retained planning material. Publishing a release is the moment existing clients can discover it, so asset/signing mistakes are immediately user-visible.

## Related notes

- [Trust, privacy, and distribution](../01-product/trust-privacy-and-distribution.md)
- [Runtime lifecycle and surfaces](../02-architecture/runtime-lifecycle-and-surfaces.md)
- [Existing documentation](../05-reference/existing-documentation.md)

