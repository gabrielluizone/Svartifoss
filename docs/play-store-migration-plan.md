# Play Store paid release — migration plan

Status: **the flavor split is implemented and verified.** Build config, source-set moves, the
`UpdateGateway` seam, manifests, `cat_updates` hidden + dropped from `SettingsSearchIndex` on the
`play` flavor, the `FlavorSelfUpdateIsolationTest` guard, `serve_apk.py` and the `CLAUDE.md` /
`AGENTS.md` build docs. All four assemble variants build; unit tests green on both flavors; the
`play` merged manifest (mobile + wear) carries no `REQUEST_INSTALL_PACKAGES` or self-update
components.

Still to do: the `release` signingConfig question (see *Signing* below — the current
`afterEvaluate` hack already signs `bundlePlayRelease` with `release.keystore`, so this is
cleanup, not a blocker), and the **docs/copy pass** for the paid listing — README line ~178
("not on the Play Store"), `docs/index.html` (badge, meta, install section), `fastlane/`
`full_description.txt`, `docs/play-console-*.md`, and the in-app strings `drawer_support_summary`
/ `no_watch_app_description`. That pass is deliberately held until the listing is close, so the
public copy does not describe a store page that does not exist yet.

## Decisions locked

- **One paid Play Console app**, package `com.svartifoss.snfell` unchanged. The
  phone and Wear artifacts ship inside that single app (App Bundle with a Wear
  module, or multi-APK), so they share the package name **and** the Play App
  Signing key — the configuration the Data Layer API needs. The
  `com.svartifoss.wrfell` split that broke phone↔watch comms is **not**
  happening; that was a different package.
- One purchase covers both devices (a single Play listing has one price; you
  cannot charge phone and watch separately without separate listings, which
  means separate packages, which breaks the Data Layer).
- **GitHub keeps free, prebuilt APK releases** on the Releases page, plus the
  in-app self-updater, for people who install from there.
- The **Play artifact must not contain the self-update capability** — Google
  Play's *Device and Network Abuse* policy forbids an app updating itself by
  any route other than Play.

## Approach: product flavors, not a branch

A release branch means two divergent histories forever and cherry-picking every
fix between them. Product flavors keep one codebase, and the Play artifact
**physically lacks** the update code and the `REQUEST_INSTALL_PACKAGES`
permission — which is the clean answer at review: nothing to "disable", nothing
that can be toggled back on.

```groovy
// mobile/build.gradle and wear/build.gradle, inside android { }
flavorDimensions "distribution"
productFlavors {
    github { dimension "distribution"; isDefault true }   // current behaviour
    play   { dimension "distribution" }                    // Play-safe subset
}
```

## What moves into `src/github/`

### mobile/

| Item | From | To |
|---|---|---|
| `update/` package (`UpdateActivity`, `UpdateChecker`, `UpdateNotifier`, `ApkDownloader`, `PhoneApkInstaller`, `PhoneInstallResultReceiver`, `WatchApkPusher`) | `src/main/java/.../update/` | `src/github/java/.../update/` |
| `<uses-permission REQUEST_INSTALL_PACKAGES>` | `src/main/AndroidManifest.xml:25` | `src/github/AndroidManifest.xml` |
| `<activity .update.UpdateActivity>` | `src/main/AndroidManifest.xml:230` | `src/github/AndroidManifest.xml` |
| `<receiver .update.PhoneInstallResultReceiver>` (`MY_PACKAGE_REPLACED`) | `src/main/AndroidManifest.xml:254` | `src/github/AndroidManifest.xml` |
| `menu_update_available` item | `menu/toolbar_main.xml` | keep, or move to `src/github/res/menu/` |

### wear/

| Item | From | To |
|---|---|---|
| `watch/update/` (`ApkReceiverService`, `InstallResultReceiver`) | `src/main/java/.../watch/update/` | `src/github/java/.../watch/update/` |
| `<uses-permission REQUEST_INSTALL_PACKAGES>` | `src/main/AndroidManifest.xml:23` | `src/github/AndroidManifest.xml` |
| `<service .watch.update.ApkReceiverService>` + its `CHANNEL_EVENT` filter | `src/main/AndroidManifest.xml:183` | `src/github/AndroidManifest.xml` |
| `<receiver .watch.update.InstallResultReceiver>` + `MY_PACKAGE_REPLACED` | `src/main/AndroidManifest.xml:197` | `src/github/AndroidManifest.xml` |

`ApkReceiverService` is the **only** manifest `CHANNEL_EVENT` listener on the
watch (log forwarding uses `LogTransmitter`, a different mechanism), so the
filter moves cleanly.

Add `tools:node="remove"` for `REQUEST_INSTALL_PACKAGES` in
`src/play/AndroidManifest.xml` in both modules as belt-and-suspenders against a
transitive dependency re-declaring it.

## Call-site seams (code in `src/main` that references the updater)

1. `MusicService.kt:469` — `UpdateChecker.maybeCheckInBackground(this)`
2. `MainActivity.kt:213` — `UpdateChecker.maybeCheckInBackground(this)`
3. `MainActivity.kt:219` — `UpdateChecker.consumePostUpdateWelcome(this)`
4. `MainActivity.kt:436` — `menu_update_available` visibility via `UpdateChecker.hasPendingUpdate(this)`
5. `MiscSettingsFragment.kt` — Updates category / manual "check now"
6. `OnlineThemesActivity.kt` — imports `UpdateChecker` (confirm why; likely the same pending-update banner)

Introduce a thin interface in `src/main`:

```kotlin
interface UpdateGateway {
    fun maybeCheckInBackground(context: Context)
    fun consumePostUpdateWelcome(context: Context): Boolean
    fun hasPendingUpdate(context: Context): Boolean
    fun openUpdateScreen(context: Context)
}
```

- `src/github`: `GithubUpdateGateway` delegates to the real `UpdateChecker` / `UpdateActivity`.
- `src/play`: no-op — `consumePostUpdateWelcome` → `false`, `hasPendingUpdate` → `false`, `openUpdateScreen` unreachable.
- Resolve through a `flavor`-specific `object UpdateGatewayProvider` (one file per flavor source set), not `BuildConfig.FLAVOR` branching in `src/main`.

## Settings — `cat_updates` (`settings.xml:40`, keys `update_check_enabled`, `update_include_prereleases`)

Keep the XML (translations, and `SettingsSearchIndex` parses XML directly). In the `play` flavor:

- Hide `cat_updates` in `MiscSettingsFragment` via the existing `SettingsCatalog` category-hiding path, guarded on flavor.
- Exclude `cat_updates` and its rows from `SettingsSearchIndex` on the `play` flavor, or `SettingsSearchRoutingTest` fails (a row must be reachable, and here it must be *absent*). Add a flavor-conditional skip.
- `menu_update_available` is already gated on `hasPendingUpdate` (→ `false` on play). Optionally drop the menu item from the play `menu` resource for cleanliness.

## Signing — lead with this

**Provide the existing `release.keystore` as the Play _app signing key_** (Play
lets you upload your own on first setup), not just as an upload key. Then:

- Play-signed installs carry the **same** signature as current sideloaded
  installs → existing users update in place from sideload → Play instead of
  having to uninstall/reinstall (which would lose data unless they first run the
  `ConfigBackup` export).
- Future GitHub sideload releases, signed with the same key, stay mutually
  update-compatible with Play installs.
- Phone and watch artifacts in the one Play app share that key → Data Layer
  capability matching keeps working.

Trade-off: providing your own app signing key means you carry the rotation risk
Google would otherwise hold. For an app with an existing sideloaded base **and**
a parallel sideload channel, signature continuity is worth it.

If instead Play generates the key: every current user must uninstall/reinstall,
sideload and Play installs permanently diverge, and an FCM announcement plus a
migration note (export config first) become mandatory.

Also: add a real `release` `signingConfig` for the Play `bundleRelease` path
rather than the root `afterEvaluate` "rewrite debug in place" hack (that hack
stays fine for local sideload testing).

## Build & release outputs

- **github**: `assembleGithubRelease` → keep the asset names `UpdateChecker`
  matches (`mobile-release.apk` / `wear-release.apk`) via `archivesBaseName` or
  a rename step, or update `UpdateChecker`'s exact-name match if the flavor
  changes them. Attach to the GitHub Release.
- **play**: `bundlePlayRelease` → `.aab` for the single Play app (phone + wear
  module).
- Update `serve_apk.py` and the "Releases & release docs" section of
  `CLAUDE.md` for two channels.

## Tests

- `./gradlew test` expands to per-flavor tasks (`testGithubDebugUnitTest`,
  `testPlayDebugUnitTest`). Keep `test` running everything, or document the task
  names. Verify the `mobile/build.gradle` test-input declarations (wear face
  dir, community-theme constraints asset) and the `res/` parity tests resolve
  under a flavored variant path.
- New structural guard: the `play` merged manifest has no
  `REQUEST_INSTALL_PACKAGES` and no `.update.` / `.watch.update.` components
  (same spirit as the existing `res/` invariant tests).

## Docs & user-facing copy (two channels now)

- `README.md`: install section → "Play Store (paid, auto-updates)" **and**
  "GitHub Releases (free) / build from source"; fix line 175 ("The app is not
  on the Play Store…").
- `docs/index.html`: hero badge (drop "sideload-only"), the two `<meta>`
  descriptions, install section gains a Play button while keeping the free
  GitHub path visible.
- `fastlane/metadata/android/en-US/full_description.txt`: "free and open source"
  → "open source (GPLv3); paid on Play, free from GitHub"; rewrite the
  "sideloadable … rather than F-Droid" paragraph.
- `docs/play-console-store-listing.md`, `-wear-store-listing.md`,
  `play-console-data-safety.md`, `play-console-wear-data-safety.md`,
  `play-console-notification-access-declaration.md`: review and bring current —
  the notification-access declaration and Data Safety form are required for the
  paid listing.
- In-app strings: `drawer_support_summary` (both flavors),
  `no_watch_app_description` (watch is now installable from Play but still needs
  the phone app — reword), the `open_play_store` path becomes live.
- `LICENSING.md` / `docs/privacy-policy.*` / `docs/privacy-languages.js`: the
  "any price on a store listing" line now has a real free counterpart — state
  it plainly ("free from GitHub; paid on Play for automatic updates").
- `CHANGELOG.md`: headline entry for the flavor split + Play release.

## Play policy checklist (paid + this config)

- Self-update absent from the `play` artifact — handled by the flavor.
- Free APKs on GitHub Releases are **not** "another app store", so the DDA
  clause against undercutting Play does not apply. (F-Droid would; not in use.)
- Notification-access (`BIND_NOTIFICATION_LISTENER_SERVICE`): in-app prominent
  disclosure + the Play Console permissions declaration.
- Data Safety form: Firebase Crashlytics / Analytics / FCM + the community-theme
  Firestore writes.
- Foreground service: confirm `MusicService` declares `foregroundServiceType`
  (`mediaPlayback`) and the Play Console foreground-service declaration is
  filled — required at `targetSdk` 34+.
- GPLv3 source offer: the privacy policy already links the GitHub source; keep
  that link in the Play listing too.

## GPLv3 note

Selling on Play while the same version is free on GitHub is fully GPLv3-consistent
— the licence governs freedom, not price, and a buyer may redistribute the APK.
The free GitHub channel makes the "free/libre; pay for convenience" framing in
the privacy policy and `LICENSING.md` accurate rather than aspirational.
