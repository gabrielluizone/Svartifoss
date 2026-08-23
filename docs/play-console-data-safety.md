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

The phone app includes Firebase Crashlytics, Firebase Analytics, and Firebase
Cloud Messaging. It does not log custom Analytics events, but the Analytics
SDK still collects its standard automatic app/session events. Cloud Messaging
is topic-based only (see `AnnouncementNotifications.kt` /
`AnnouncementMessagingService.kt`) - there is no server of ours and no
per-user targeting, registration, or token storage. Separately, an
off-by-default setting (`streaming_shortcut_artwork`, see
`ShortcutArtworkFetcher.kt`) sends a saved shortcut's public share link
directly to that streaming service's own oEmbed endpoint (Spotify, YouTube,
SoundCloud, Deezer) to fetch a cover thumbnail - no Firebase/Google
involvement in that request. An on-by-default setting (`lyrics_enabled`, see
`LyricsFetcher.kt`) sends the playing track's name, artist and length to
LRCLIB when - and only when - the user opens the watch's lyrics screen or
selects the lyric-following Verse watch face; again
no Firebase/Google involvement, no account and no identifier.

---

## Does your app collect or share any of the required user data types?

**Yes** — because of Firebase Crashlytics (crash reporting), the standard
automatic collection performed by Firebase Analytics, the queue-cover fetch
(on by default, one switch to disable), the lyrics lookup (on by default, one
switch, and never fired unless the user opens the lyrics screen) and (only if
the user opts in) the shortcut-artwork fetch to the streaming service's own
oEmbed endpoint. Everything else the app touches (media metadata, button configs,
search history, playlist shortcuts) stays on-device or goes only to the
user's own paired watch over the local Wearable Data Layer connection —
never to a server you operate.

## Data types to report

| Category | Type | Collected? | Shared? | Purpose | Notes |
|---|---|---|---|---|---|
| App info and performance | Crash logs | Yes | With Google (Firebase Crashlytics) | Crash/bug reporting | Stack traces + short diagnostic log lines the app already writes while running. These occasionally include the name of the track/app that was playing at crash time. |
| App info and performance | Diagnostics | Yes | With Google (Firebase Crashlytics) | Crash/bug reporting | Device model, OS version, standard crash-context data. |
| Device or other IDs | Device or other IDs | Yes | With Google (Firebase Crashlytics) | Crash correlation | The Firebase installation ID used internally by Crashlytics to group crash reports and compute the crash-free-users rate - not a personal identifier, no account is tied to it. |
| App activity | App interactions | Yes | With Google (Firebase Analytics) | Analytics | Automatically collected app lifecycle, screen-view and session events; Svartifoss does not add custom events. |
| Location | Approximate location | Yes | With Google (Firebase Analytics) | Analytics | Coarse location derived by Google from a masked IP address; Svartifoss never requests GPS location. |
| Device or other IDs | Device or other IDs | Yes | With Google (Firebase Analytics) | Analytics | Per-installation app-instance ID and SDK-supported device identifiers used to compute usage metrics. No Svartifoss account or user ID is attached. |
| Device or other IDs | Device or other IDs | Yes | With Google (Firebase Cloud Messaging) | Push notification delivery | The install's FCM registration token, held by Google's messaging infrastructure to route messages to a shared topic. Svartifoss never receives, stores, or sees this token itself - there is no server of ours to receive it. |
| App activity | Other user-generated content | Only if the user enables it | With the streaming service (Spotify/YouTube/SoundCloud/Deezer) | Fetching a cover thumbnail for a saved shortcut | Off by default ("Fetch shortcut artwork online"). Only the already-public share link the user saved is sent, directly to that service's own oEmbed endpoint - no account, API key, or Svartifoss/Google identifier is attached. |
| App activity | Other user-generated content | Yes, unless the user turns it off | With whatever host the playing music app published | Downloading a cover for a playback-queue entry | **On by default** ("Fetch queue covers online", see `QueueArtworkResolver.kt`) - a streaming app's queue exposes no other cover source, so off meant permanently blank rows. Only the cover URL the music app itself put on the queue entry is requested, and only for entries currently being shown on the watch - no account, API key, or Svartifoss/Google identifier is attached. A single switch (Settings → Apps, or Watch face → Panels) disables it. |
| App activity | Other user-generated content | Yes, unless the user turns it off | With LRCLIB (lrclib.net) | Looking up the lyrics for the playing track | **On by default** ("Look up lyrics online", see `LyricsFetcher.kt`), but nothing is ever sent unless the user opens the watch's lyrics screen or selects the Verse watch face - no background lookups on any other face. Only the track name, artist name and track length are sent; LRCLIB matches on exactly those and needs no account or API key, so no account, API key, or Svartifoss/Google identifier is attached. Results are held in memory only and never written to disk. |
| App activity | Other user-generated content | Yes, only if the user turns it on | With MusicBrainz (musicbrainz.org) | Filling in track details the playing app did not publish (ISRC, label, release date) | **Off by default** ("Look up track details online", see `MusicBrainzMetadata.kt`). Nothing is sent unless the user both enables it and selects the Metadata watch face. Only the track name and artist name are sent; MusicBrainz needs no account or API key, so no account, API key or Svartifoss/Google identifier is attached. Results are held in memory only and never written to disk. |

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
- **Audio files / Music**: the app requests `READ_MEDIA_AUDIO` (Android 13+)
  purely to decode album covers that a local music player referenced from the
  device's own media library for the queue entries currently shown on the
  watch. It never reads audio content, never enumerates the library, and
  nothing leaves the device, so this should **not** be marked as "Audio
  files" data collection. The permission is requested in context from
  Settings → Apps and the queue works without it.
- **Search history / playlist shortcuts / recently-played tracks**: stored
  only in the app's private local storage. Not collected by the developer,
  so not reportable here either.

## Standard follow-up questions

| Question | Answer |
|---|---|
| Is all data encrypted in transit? | Yes — the Firebase SDKs use HTTPS. |
| Do you provide a way for users to request data deletion? | There's no user account in Svartifoss to tie a deletion request to. If Play Console requires an answer here, the honest one is that there's no in-app deletion mechanism, since no personal/account data is collected in the first place — only installation-scoped diagnostics and usage data via Firebase's infrastructure. |
| Is data collection required or optional? | Crashlytics is optional: "Send crash reports" is enabled by default but can be disabled in-app. Crashlytics automatic upload remains off; queued reports are sent manually only after the app reads this choice. Cloud Messaging is likewise optional: "Announcement notifications" is enabled by default and, when disabled, unsubscribes the install from the shared topic. Firebase Analytics automatic collection is not currently exposed as a user setting. None of the three gate a core app feature. |
| Do you use the data for advertising or sell it? | No. No ads SDK is present, and nothing is sold. |
