# Play Console — Data Safety section

Answers for **App content → Data safety**, based only on what's actually in
this codebase (see `mobile/build.gradle` for the Firebase dependencies,
`TimberCrashlytics.kt` / `CrashlyticsExceptionWearHandler.kt` for how crash
data flows, and `NotificationService.kt` / `ActiveMediaSessionProvider.kt`
for what the notification-access permission is actually used for).

Play Console's exact question wording and category list changes over time, so
double-check the precise checkboxes against the current form and Firebase's
[Android disclosure guide](https://firebase.google.com/docs/android/play-data-disclosure)
before submitting.

The phone app includes both Firebase Crashlytics and Firebase Analytics. It
does not log custom Analytics events, but the Analytics SDK still collects its
standard automatic app/session events.

---

## Does your app collect or share any of the required user data types?

**Yes** — because of Firebase Crashlytics (crash reporting) and the standard
automatic collection performed by Firebase Analytics.
Everything else the app touches (media metadata, button configs, search
history, playlist shortcuts) stays on-device or goes only to the user's own
paired watch over the local Wearable Data Layer connection — never to a
server you operate.

## Data types to report

| Category | Type | Collected? | Shared? | Purpose | Notes |
|---|---|---|---|---|---|
| App info and performance | Crash logs | Yes | With Google (Firebase Crashlytics) | Crash/bug reporting | Stack traces + short diagnostic log lines the app already writes while running. These occasionally include the name of the track/app that was playing at crash time. |
| App info and performance | Diagnostics | Yes | With Google (Firebase Crashlytics) | Crash/bug reporting | Device model, OS version, standard crash-context data. |
| Device or other IDs | Device or other IDs | Yes | With Google (Firebase Crashlytics) | Crash correlation | The Firebase installation ID used internally by Crashlytics to group crash reports and compute the crash-free-users rate - not a personal identifier, no account is tied to it. |
| App activity | App interactions | Yes | With Google (Firebase Analytics) | Analytics | Automatically collected app lifecycle, screen-view and session events; Svartifoss does not add custom events. |
| Location | Approximate location | Yes | With Google (Firebase Analytics) | Analytics | Coarse location derived by Google from a masked IP address; Svartifoss never requests GPS location. |
| Device or other IDs | Device or other IDs | Yes | With Google (Firebase Analytics) | Analytics | Per-installation app-instance ID and SDK-supported device identifiers used to compute usage metrics. No Svartifoss account or user ID is attached. |

**Everything else in Play's standard list — Personal info,
Financial info, Health and fitness, Messages, Photos/videos, Audio files,
Files and docs, Calendar, Contacts, Web browsing — is NOT collected.**

A few of these deserve a specific note since the app touches adjacent
permissions:

- **Messages / notifications**: the app requests notification access for
  media-session metadata and, when the user selects "From current media app",
  the active media notification's action labels/icons. Those controls travel
  only to the paired watch and are not developer collection, so this should
  **not** be marked as "Messages" data collection.
- **Photos**: the app can save the current album art to the device's photo
  gallery, but only when the user explicitly taps that option, and the file
  never leaves the device. This is a local write the user initiates, not
  data the developer collects — do **not** mark "Photos and videos" as
  collected.
- **Search history / playlist shortcuts / recently-played tracks**: stored
  only in the app's private local storage. Not collected by the developer,
  so not reportable here either.

## Standard follow-up questions

| Question | Answer |
|---|---|
| Is all data encrypted in transit? | Yes — the Firebase SDKs use HTTPS. |
| Do you provide a way for users to request data deletion? | There's no user account in Svartifoss to tie a deletion request to. If Play Console requires an answer here, the honest one is that there's no in-app deletion mechanism, since no personal/account data is collected in the first place — only installation-scoped diagnostics and usage data via Firebase's infrastructure. |
| Is data collection required or optional? | Crashlytics is optional: "Send crash reports" is enabled by default but can be disabled in-app. Crashlytics automatic upload remains off; queued reports are sent manually only after the app reads this choice. Firebase Analytics automatic collection is not currently exposed as a user setting. Neither gates a core app feature. |
| Do you use the data for advertising or sell it? | No. No ads SDK is present, and nothing is sold. |
