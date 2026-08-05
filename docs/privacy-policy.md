# Privacy Policy for Svartifoss

**Last updated: 23-07-2026**

Svartifoss ("the app", "we", "our") is a Wear OS companion app that lets a
paired watch control music playback on your phone. This policy explains what
information the app accesses, what it stores, and what — if anything — leaves
your device.

The short version: **Svartifoss does not have user accounts, does not run its
own servers, and does not sell or share your data with advertisers.** The
phone and watch talk to each other directly over your local Bluetooth/Wi-Fi
connection. Optional update checks contact GitHub, and the phone app includes
Google Firebase Crashlytics and Analytics for diagnostics, plus Firebase
Cloud Messaging for occasional developer announcements. Crash reporting and
announcement notifications are both enabled by default and can each be
disabled at any time — see [Crash reports](#crash-reports) and
[Announcement notifications](#announcement-notifications) below. A separate,
**off-by-default** setting can also fetch cover art for your saved playlist
shortcuts directly from the streaming service — see
[Streaming shortcut artwork](#streaming-shortcut-artwork-optional) below.

## What the app needs access to, and why

### Notification access / media session (Android's "Notification access" permission)

Svartifoss requests **notification access** so it can read which app is
currently playing music and its media-session metadata (track title, artist,
album art, playback position, shuffle/repeat state). If you select **From
current media app** for the quick-actions panel, it also reads up to four
action labels, icons and local button intents from that app's active media
notification. The labels/icons are mirrored only to your paired watch; the
button intents stay on the phone and are invoked only when you tap the matching
watch button.

This permission is **not** used to read the content of your messages, emails,
or unrelated notifications. Svartifoss does not store, transmit to a server or
log such content.

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
| Foreground service / "Post notifications"                    | Keeps the phone↔watch connection alive while music plays, shown as a persistent notification (required by Android for background media apps). Also required to show the optional developer announcement notifications described below.                                                 |
| Photos                                                       | Only used if you explicitly choose to save the current album art to your device's photo gallery. Nothing is saved unless you tap that option.                                                  |
| Vibrate                                                      | Haptic feedback on the watch when you press a button, if you enable that setting.                                                                                                              |
| Run Tasker tasks                                             | Only relevant if you have the separate Tasker app installed and choose to bind a Tasker task to a button. Svartifoss does not read Tasker's data — it only triggers a task you've configured. |
| Music and audio (Android 13+) / Storage (older versions)     | Only used to read album covers for entries in the playback queue, when the music app publishes them as references into your music library rather than as images. Requested from Settings → Apps, never at startup, and only ever read locally — nothing is uploaded. Decline it and the queue simply shows blank thumbnails. On very old Android versions the same legacy Storage permission also covers saving the current album art to your gallery, which only happens if you tap that option. |
| Internet                                                     | Used for optional update checks, the Firebase diagnostics described below, and — only if you turn them on — fetching shortcut artwork from the streaming service and downloading queue covers that the music app published as links. Core playback mirroring and control work locally between your two devices.                         |

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

## Crash reports

Svartifoss uses **Firebase Crashlytics** (a Google service) to receive crash
reports when the app misbehaves, so we can find and fix bugs. A crash report
can include: a stack trace, the Android version and device model, and short
diagnostic log lines the app already writes internally while running — these
log lines occasionally include the name of the track or app that was playing
at the time, since that's part of normal app operation, but never your
personal messages, contacts, or notification content from other apps.

Crash reporting is **enabled by default**. You can disable it under
**Settings → Data & support → Privacy → Send crash reports**. Disabling the
setting stops Svartifoss from adding custom Crashlytics logs or non-fatal
reports and deletes reports still queued on the device without sending them.
Automatic Crashlytics upload remains disabled at all times; when this setting
is enabled, Svartifoss explicitly sends finalized reports only after reading
your choice at startup. It cannot recall a report that had already been
uploaded before you opted out.

This sends data to Google's Firebase infrastructure, governed by
[Google's Privacy Policy](https://policies.google.com/privacy) and
[Firebase's data processing terms](https://firebase.google.com/support/privacy).
We do not have access to unrelated data Google may hold through its other
services.

## Announcement notifications

Svartifoss uses **Firebase Cloud Messaging** (a Google service) to let the
developer send occasional push notifications — for example, about a new
release or an important notice. There is no Svartifoss account and no server
of ours involved: every installation subscribes to a single shared FCM topic,
and a notification sent to that topic reaches every subscribed device. We do
not receive or hold a list of users, devices, or install identifiers through
this feature — Google's Firebase infrastructure routes the message to
subscribed devices without exposing that list to us.

A notification may include a title, a short message, and an optional link
that opens when you tap it (for example, to a release page). Svartifoss does
not use this channel for advertising, and does not send anything through it
based on your listening activity, location, or any other on-device data —
whatever gets sent is written by the developer at the time it's sent.

Announcement notifications are **enabled by default**. You can disable them
under **Settings → Data & support → Privacy → Announcement notifications**.
Disabling unsubscribes this installation from the topic; a message sent while
you're unsubscribed will not reach this device. Android's own notification
permission still applies on top of this setting.

This uses Google's Firebase infrastructure, governed by
[Google's Privacy Policy](https://policies.google.com/privacy) and
[Firebase's data processing terms](https://firebase.google.com/support/privacy).

## Streaming shortcut artwork (optional)

If you save a playlist/track shortcut and turn on **Fetch shortcut artwork
online** (Settings → Apps → Music apps & services — **off by default**),
Svartifoss sends that shortcut's public share link to the corresponding
streaming service's own public **oEmbed** endpoint (Spotify, YouTube,
SoundCloud, or Deezer) to download a cover thumbnail, shown on the phone,
the watch menu, and any button you assign it to. Each thumbnail is fetched
once and cached on-device.

Only the link itself — already a public share URL you chose to save — is
sent; no account, API key, or other personal data is attached. This request
goes directly to the streaming service, not through any server of ours, and
is governed by that service's own privacy policy, not this one. Apple
Music, Amazon Music, and Tidal have no public oEmbed endpoint, so shortcuts
to those services always fall back to a generic app icon instead.

## Playback queue covers (optional)

Music apps publish the cover for each queue entry in one of two ways, and
neither is read unless you opt in.

**From your library.** Local players (Retro Music and similar) reference a
cover already stored on your device. Reading those needs Android's music and
audio permission, requested from the "Queue covers from your library" row in
Settings → Apps. This is a purely local read — no network access is involved
and nothing about your library is uploaded, transmitted to the watch beyond
the covers for the up-to-20 queue entries being displayed, or shared with
anyone. Declining leaves the queue working with blank thumbnails.

**From the internet.** Streaming clients publish a cover URL instead of an
image. **Fetch queue covers online** (Settings → Apps, and the Watch face
tab's Panels section) downloads each such cover once and caches it on the
phone. Only the cover URL the music app itself published is requested; no
account, API key, or other personal data is attached, and the request goes
directly to whatever host that app pointed at, not through any server of
ours.

This one is **on by default**, unlike the optional shortcut artwork above.
The reason is that a streaming app's queue has no other cover source at all,
so leaving it off produced a queue of permanently blank rows rather than a
degraded one. Turning it off is a single switch, and with it off remote
covers are skipped entirely and those entries show blank thumbnails — the
queue itself, and everything else in the app, keeps working offline.

## Usage diagnostics

The phone app also includes **Google Analytics for Firebase**. It collects
Firebase's standard automatic app/session events and technical context such as
app version, device/OS class and an installation-scoped identifier. Svartifoss
does not attach an account, name, email, media metadata or message content to
these events and does not log custom Analytics events. This service is governed
by [Google's Privacy Policy](https://policies.google.com/privacy) and
[Firebase's privacy information](https://firebase.google.com/support/privacy).
The crash-reporting switch described above controls Crashlytics reports; it
does not control these separate automatic Analytics events.

## What we don't do

- We don't require or offer any account/login.
- We don't run our own backend server that your data passes through.
- We don't sell, rent, or share your data with advertisers or data brokers.
- We don't show ads.
- We don't read the content of unrelated notifications. For the current media
  app, only playback metadata and — when explicitly selected — its media action
  labels/icons are used.

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
