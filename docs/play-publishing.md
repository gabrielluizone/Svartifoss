# Publishing to Google Play from the command line

Uploading a bundle through the Play Console by hand is the one step of a release that cannot be
scripted from the browser side, so this repo carries [gradle-play-publisher][gpp] instead. Once the
credentials below exist on a machine, a release is two commands and no file ever leaves the terminal.

Everything here is **opt-in per machine**, exactly like the release signing in the root
`build.gradle`. With no `play-publisher.json` at the repo root the plugin is never applied, no
publish tasks exist, and the build is byte for byte the one a fresh clone runs. Nothing about CI or
about somebody else's checkout changes because this file is documented.

[gpp]: https://github.com/Triple-T/gradle-play-publisher

## One-time setup

The credentials are a Google Play **service account key**. Creating one spans two consoles and takes
about ten minutes; it is done once per developer, not once per release.

1. **Play Console** → *Settings* → *API access*. Link a Google Cloud project if the page asks.
2. Choose **Create new service account**. The link opens the Google Cloud Console.
3. **Cloud Console** → *IAM & Admin* → *Service accounts* → **Create service account**. A name such
   as `play-publisher` is enough, and it needs **no** role at the GCP level — its permissions come
   from the Play Console, not from IAM.
4. Open the new account → **Keys** → *Add key* → *Create new key* → **JSON**. The file downloads.
5. Back in **Play Console** → *API access*, the account now appears under *Service accounts* →
   **Grant access**.
6. Restrict it to the Svartifoss app and grant *View app information*, *Upload and release to
   testing tracks* and, if command-line production releases are wanted, *Release to production*.
   **Save.**

Play takes a few minutes to propagate the grant; a `403` on the first attempt usually means it has
not landed yet rather than that something is wrong.

Then put the downloaded file at the repo root as **`play-publisher.json`**. It is gitignored beside
`keystore.properties`, and it deserves the same care: anyone holding it can publish to the live
listing.

## Publishing

```sh
# Build the bundle and upload it, in one command, per module.
./gradlew :mobile:publishPlayReleaseBundle
./gradlew :wear:publishPlayReleaseBundle

# Upload a bundle that is already built, without compiling again.
./gradlew :wear:publishPlayReleaseBundle \
    --artifact-dir=wear/build/outputs/bundle/playRelease
```

Two defaults are deliberately the cautious ones, and both are overridable:

| Property | Default | What it does |
| --- | --- | --- |
| `-PplayTrack` | `internal` | The track to upload to. Internal testing reaches a named list of testers and nobody else. Use `beta`, `alpha` or `production` for the others. |
| `-PplayStatus` | `draft` | The upload appears in the Console and waits for a human to roll it out. `completed` releases it immediately. |

```sh
./gradlew :mobile:publishPlayReleaseBundle -PplayTrack=beta
./gradlew :mobile:publishPlayReleaseBundle -PplayTrack=production -PplayStatus=completed
```

The defaults are chosen for what cannot be undone: Play burns a `versionCode` permanently and a
release that has reached users cannot be recalled, so a mistyped command should not be able to put
anything in front of anybody.

## Things specific to this app

**The watch has its own tracks.** Phone and watch are one Play listing sharing one `applicationId`
(see *Package naming gotchas* in `CLAUDE.md`), but Wear OS is a separate **form factor** in the Play
Console with tracks of its own. The Play Developer API addresses those with a `wear:` prefix, so
`wear/build.gradle` adds it: `-PplayTrack=beta` sends the phone bundle to `beta` and the watch
bundle to `wear:beta`, and the prefix cannot be forgotten at the command line. Passing
`-PplayTrack=wear:beta` explicitly also works and is not doubled.

**Only the `play` flavor is publishable.** The `github` flavor is the sideload build and carries the
in-app self-updater that Google Play forbids, so its variants are disabled in `playConfigs` rather
than left as tasks that would be a mistake to run. There is no `publishGithubReleaseBundle`.

**A `versionCode` already on Play stops the command.** `resolutionStrategy` is `FAIL`, so a repeated
code is reported rather than quietly ignored. Bump both `mobile/build.gradle` and `wear/build.gradle`
after every release-bound build — see the release checklist in `CLAUDE.md`.

**The plugin can publish the store listing too**, from `fastlane/metadata/android/` restructured into
its own `src/main/play/` layout, but that is not set up here: the listing texts in `fastlane/` are
kept in the Fastlane layout and `publishPlayListing` would find nothing to send. Only bundles go
through this path today.

## If it fails

- `403` right after granting access — propagation; wait a few minutes.
- `The caller does not have permission` — the grant in step 6 is missing the track it is being asked
  to publish to, or is scoped to a different app.
- `APK specifies a version code that has already been used` — bump the `versionCode`; Play never
  releases one back.
- `Your edit was in an unexpected state` — an edit was left open in the Console. Discard the draft
  release there and run the command again.
