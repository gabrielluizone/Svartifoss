# Privacy Policy for Svartifoss

**Last updated: 05-07-2026**

Svartifoss ("the app", "we", "our") is a Wear OS companion app that lets a
paired watch control music playback on your phone. This policy explains what
information the app accesses, what it stores, and what — if anything — leaves
your device.

The short version: **Svartifoss does not have user accounts, does not run its
own servers, and does not sell or share your data with advertisers.** The
phone and watch talk to each other directly over your local Bluetooth/Wi-Fi
connection. The only data that leaves your device goes to Google's Firebase
service, solely to help us fix crashes — see [Crash reports and basic usage
analytics](#crash-reports-and-basic-usage-analytics) below.

## What the app needs access to, and why

### Notification access / media session (Android's "Notification access" permission)

Svartifoss requests **notification access** so it can read which app is
currently playing music and its metadata (track title, artist, album art,
playback position, shuffle/repeat state). This is the only reason the
permission is requested — it is **not** used to read the content of your
messages, emails, or any other app's notifications. The app can technically
see notification data because that's how Android exposes the "what's playing"
information from other music apps, but Svartifoss only acts on media-session
data and does not store, transmit, or log the content of unrelated
notifications.

### Phone ⟷ watch communication

All communication between the phone app and the watch app happens over the
Wearable Data Layer API — a local, direct connection between your own two
paired devices (via Bluetooth or local Wi-Fi). This does not go through any
server we operate, and does not require an internet connection or a Google
account beyond what your device already needs to have its watch paired at
all.

### Other permissions

| Permission                                                   | What it's for                                                                                                                                                                                  |
| ------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Foreground service / "Post notifications"                    | Keeps the phone↔watch connection alive while music plays, shown as a persistent notification (required by Android for background media apps).                                                 |
| Storage (legacy, only on very old Android versions) / Photos | Only used if you explicitly choose to save the current album art to your device's photo gallery. Nothing is saved unless you tap that option.                                                  |
| Vibrate                                                      | Haptic feedback on the watch when you press a button, if you enable that setting.                                                                                                              |
| Run Tasker tasks                                             | Only relevant if you have the separate Tasker app installed and choose to bind a Tasker task to a button. Svartifoss does not read Tasker's data — it only triggers a task you've configured. |
| Internet                                                     | Used only by the crash-reporting/analytics library described below — the core app features (reading and mirroring music playback) work entirely offline between your two devices.             |

## What's stored locally on your phone

The following stays only in the app's private storage on your phone. It is
never uploaded anywhere by us:

- Your button/gesture configuration (what each button, tap zone, or gesture
  does).
- Your search history and playlist shortcuts, if you use those features.
- A short local history of recently played tracks, used as a fallback when an
  app doesn't expose a real playback queue.
- Your app preferences (theme, colors, timeouts, etc.).

Uninstalling the app removes all of this data. If you use the app's
Export Config feature, that file is saved wherever you choose to save it
(e.g. your own device storage or cloud drive) — that's your copy, under your
control, and we never see it.

## Crash reports and basic usage analytics

Svartifoss uses **Firebase Crashlytics** (a Google service) to receive crash
reports when the app misbehaves, so we can find and fix bugs. A crash report
can include: a stack trace, the Android version and device model, and short
diagnostic log lines the app already writes internally while running — these
log lines occasionally include the name of the track or app that was playing
at the time, since that's part of normal app operation, but never your
personal messages, contacts, or notification content from other apps.

The app also includes **Firebase Analytics**, which may automatically collect
basic, non-identifying usage signals (like app opens) using Google's default
SDK behavior. We do not add any custom tracking events ourselves.

Both of these send data to Google's Firebase infrastructure, governed by
[Google&#39;s Privacy Policy](https://policies.google.com/privacy) and
[Firebase&#39;s data processing terms](https://firebase.google.com/support/privacy).
We do not have access to any other data Google collects about your device
through its own services.

## What we don't do

- We don't require or offer any account/login.
- We don't run our own backend server that your data passes through.
- We don't sell, rent, or share your data with advertisers or data brokers.
- We don't show ads.
- We don't read the content of notifications from apps other than the
  currently-playing media app's playback metadata.

## Children's privacy

Svartifoss is not directed at children and does not knowingly collect
information from children.

## Open source

Svartifoss is free/open-source software (GPLv3). Anyone can inspect the full
source code — including everything described in this policy — at:
[https://github.com/gabrielluizone/Svartifoss](https://github.com/gabrielluizone/Svartifoss)

## Changes to this policy

If this policy changes, the updated version will be posted at this same
location with a new "Last updated" date.

## Contact

Questions about this policy or your data can be sent to:
**gabrielluizone@gmail.com**
