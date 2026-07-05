# Play Console — Data Safety section

Answers for **App content → Data safety**, based only on what's actually in
this codebase (see `mobile/build.gradle` for the Firebase dependencies,
`TimberCrashlytics.kt` / `CrashlyticsExceptionWearHandler.kt` for how crash
data flows, and `NotificationService.kt` / `ActiveMediaSessionProvider.kt`
for what the notification-access permission is actually used for).

**Caveat:** Play Console's exact question wording and category list changes
over time and I can't browse it live from here — treat this as the accurate
*content* to report, but double-check the precise checkboxes against what
the form shows you when you fill it in. Google/Firebase also publish their
own mapping guide ("Data safety section on Google Play and Firebase") that's
worth a quick look before submitting, since Crashlytics/Analytics are the
one area here with real third-party data flow.

---

## Does your app collect or share any of the required user data types?

**Yes** — solely because of Firebase Crashlytics (crash reporting) and
Firebase Analytics (basic, non-custom usage signals). Everything else the
app touches (media metadata, button configs, search history, playlist
shortcuts) stays on-device or goes only to the user's own paired watch over
the local Wearable Data Layer connection — never to a server you operate.

## Data types to report

| Category | Type | Collected? | Shared? | Purpose | Notes |
|---|---|---|---|---|---|
| App activity | App interactions | Yes | With Google (Firebase Analytics) | Analytics | Automatically collected by the Firebase Analytics SDK; no custom events are logged in the app's own code. |
| App info and performance | Crash logs | Yes | With Google (Firebase Crashlytics) | Crash/bug reporting | Stack traces + short diagnostic log lines the app already writes while running. These occasionally include the name of the track/app that was playing at crash time. |
| App info and performance | Diagnostics | Yes | With Google (Firebase Crashlytics) | Crash/bug reporting | Device model, OS version, standard crash-context data. |
| Device or other IDs | Device or other IDs | Yes | With Google (Firebase) | Analytics, crash correlation | The Firebase installation ID used internally by Crashlytics/Analytics to group events/crashes - not a personal identifier, no account is tied to it. |

**Everything else in Play's standard list — Location, Personal info,
Financial info, Health and fitness, Messages, Photos/videos, Audio files,
Files and docs, Calendar, Contacts, Web browsing — is NOT collected.**

A few of these deserve a specific note since the app touches adjacent
permissions:

- **Messages / notifications**: the app requests notification access, but
  only to call `MediaSessionManager.getActiveSessions()` (media-session
  metadata). It does not read, store, or transmit notification content, so
  this should **not** be marked as "Messages" data collection.
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
| Do you provide a way for users to request data deletion? | There's no user account in Svartifoss to tie a deletion request to. If Play Console requires an answer here, the honest one is that there's no in-app deletion mechanism, since no personal/account data is collected in the first place — only anonymous crash/analytics telemetry via Firebase's own infrastructure. |
| Is data collection required or optional? | None of it gates any app feature - the app works identically whether or not Firebase's background telemetry succeeds. |
| Do you use the data for advertising or sell it? | No. No ads SDK is present, and nothing is sold. |

## Committed vs. actual behavior — one thing to verify yourself

Firebase Analytics is included as a dependency but the app's own code never
calls `logEvent(...)` — its default automatic collection (session start,
app open, etc.) is what applies. If you'd rather not report Analytics data
at all, the cleanest fix is removing the dependency (`libs.google.firebase.analytics`
in `mobile/build.gradle`) if it isn't actually providing value to you today
— that would shrink both this form and the privacy policy's "Firebase"
section. Let me know if you want me to check whether anything relies on it
before removing it.
