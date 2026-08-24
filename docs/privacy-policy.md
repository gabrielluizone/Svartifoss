# Privacy Policy for Svartifoss

**Last updated: 24-08-2026**

Svartifoss ("the app", "we", "our") is a Wear OS companion app that lets a
paired watch control music playback on your phone. This policy explains what
information the app accesses, what it stores, and what — if anything — leaves
your device.

The short version: **Svartifoss does not require an account for playback,
watch control, or Community-theme browsing; does not operate its own backend
server; and does not sell or share your data with advertisers.** The phone and
watch talk to each other directly over your local Bluetooth/Wi-Fi connection.
Optional update checks and the opt-in Community themes gallery contact GitHub
Pages. If you explicitly submit one of your themes, Google Firebase
Authentication processes the Google credential needed to identify that
submission and Google Cloud Firestore keeps a private moderation record; your
Google account name and email are not published. The phone app also includes
Google Firebase Crashlytics and
Analytics for diagnostics, plus Firebase Cloud Messaging for occasional
developer announcements. Crash reporting and announcement notifications are
both enabled by default and can each be disabled at any time — see [Crash
reports](#crash-reports) and [Announcement
notifications](#announcement-notifications) below. A separate,
**off-by-default** setting can also fetch cover art for your saved playlist
shortcuts directly from the streaming service — see [Streaming shortcut
artwork](#streaming-shortcut-artwork-optional) below.

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
| Internet                                                     | Used for optional update checks, the opt-in Community themes gallery, and — only after you explicitly choose to submit a theme — Google Sign-In/Firebase Authentication and Firestore. It is also used for the Firebase diagnostics described below, looking up song lyrics when you open the lyrics screen on the watch, and — only if you turn them on — fetching shortcut artwork from the streaming service and downloading queue covers that the music app published as links. Core playback mirroring and control work locally between your two devices.                         |

## What's stored locally on your phone

The following normally stays only in the app's private storage on your phone.
An exception is a theme you expressly submit to the Community-themes moderation
queue, described below.

- Your button/gesture configuration (what each button, tap zone, or gesture
  does).
- Your search history and playlist shortcuts, if you use those features.
- A short local history of recently played tracks, used as a fallback when an
  app doesn't expose a real playback queue.
- Your private theme library and other app preferences (theme, colors,
  timeouts, etc.). A profile leaves the device only if you select it for an
  explicit Community-theme submission.
- A disposable cache of the public Community themes catalogue and theme files after you open that gallery. It contains only the public JSON served from this project's GitHub Pages site, is not included in configuration backups, and can be cleared with Android's app-cache controls.

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

## Community themes

### Browsing and installing

Opening **Community themes** from the Watch themes screen requests a public
catalogue and, as needed for a preview or installation, public theme JSON files
from this project's [GitHub Pages](https://gabrielluizone.github.io/Svartifoss/)
site. Nothing is requested until you open that screen. Browsing does not start
Google Sign-In or write to Firebase, and does not send your playback, library,
saved themes, account details, or a device identifier. The request identifies
the app only with a generic Svartifoss user-agent, while GitHub necessarily
receives ordinary web-request information such as your IP address under its own
privacy terms.

The app renders every gallery preview locally using its bundled sample track,
never the song you are currently listening to. A theme is only added to **My
themes** after you tap **Add and apply**. The downloaded catalogue/profile files
are cached in the app's disposable cache for faster later browsing; they are
public content and are not part of a configuration backup.

### Submitting a theme

Submitting is separate from browsing. It starts only after you choose **Submit
to community** for a private, user-owned saved theme and then tap **Sign in and
submit**. Before opening Google Sign-In, the app creates and checks a fresh
public profile locally. The current preflight requires at least **12** setting
values to differ from the selected base face; this is an eligibility filter,
not a promise that the theme will be approved.

At that explicit point, Firebase Authentication processes the Google
credential and account information needed to authenticate you, then issues a
Firebase Auth UID. Svartifoss stores that UID — rather than your Google account
name or email — in the Firestore submission document as the ownership
identifier (`ownerUid`). It does not show your Google account name or email to
gallery visitors or publish them. Instead, you choose the public theme name and
author pseudonym yourself.

Firestore receives one immutable **pending** submission record. Its submitted
content includes the fresh public theme ID; the complete typed profile JSON and
its settings digest; the public theme name and author pseudonym; the base face,
revision, client version, and Firestore server timestamp. It also includes a
fixed-sample 200×200 WebP review preview rendered from Svartifoss's built-in
sample track, not your current playback, album art, or media metadata. A
configured moderator can later add only an approval or rejection decision,
their reviewer UID, and a server timestamp; neither the author nor a moderator
can edit the submitted theme content through the app.

Firestore also keeps a separate private quota record tied to your Firebase Auth
UID. It records the submission count, most recent submission ID, and server
timestamp solely to enforce no more than one Community-theme submission per
account every 24 hours. It is not public.

Google processes Authentication and Firestore data under
[Google's Privacy Policy](https://policies.google.com/privacy) and
[Firebase's data processing terms](https://firebase.google.com/support/privacy).

### Moderation, publication, and removal requests

The pending record is private to its owner and configured moderators. Approval
does not make a theme public immediately. When an approved submission is
published, the trusted publisher validates it again and commits the public
profile JSON, public theme name, and author pseudonym to this project's Git
repository; GitHub Pages then serves those public files in the catalogue. The
Firebase Auth UID and Google account name/email are not copied to the public
repository or catalogue.

Version 3.3 does not implement likes, comments, theme-update submissions, a
submission-history screen, or self-service deletion of a Firebase Auth record
or submitted theme. Uninstalling the app removes its local data but does not
retract a submission already stored in Firestore.

To request a takedown of a pending submission, removal of a current public
listing, or help identifying a community-theme record, email the address in
[Contact](#contact). Include the public theme name, pseudonym, and approximate
submission date if you can. Requests are handled manually. Removing a current
listing cannot erase information already committed to Git history or copied
into forks, clones, caches, or third-party archives.

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

The same switch also governs one request outside the queue. Some streaming
apps hand out a small thumbnail for the track *currently playing* — sized for
their own phone notification, sometimes only 100 pixels square — while the
same track information carries the address of the full-size cover. Because
the watch scales that image up to fill its screen, the small one arrives
visibly blurry. When this setting is on, and only when the supplied image is
smaller than the watch actually needs, the larger copy is fetched from that
same address and cached alongside the queue covers. It is the identical kind
of request, to the address the music app itself published, with nothing
attached and no server of ours involved. With the setting off, the small
image is used as-is.

## Track details

The **Metadata** watch face shows what the playing track actually is: album,
position on the record, credits, year, and for a track stored on your phone the
format, bitrate, sample rate, channels and file size.

Almost all of that needs no network at all. The album, artist, credits, genre,
year and track position come from the tags the playing app already publishes
about the current song, and the file details are read from the file on your
phone — which is why they appear only for local tracks, and only when the media
access described above has been granted. Nothing about either leaves your
devices.

One optional part does use the internet. **Look up track details online**
(Settings → Apps → Music apps & services) sends the **track name and the artist
name** to [MusicBrainz](https://musicbrainz.org), a free public music database,
to fill in details the playing app did not publish — the ISRC, the record label,
the release date and MusicBrainz's own catalogue ids. Nothing else is attached:
no identifier, no account, no listening history, and no record of which app was
playing. The request goes directly to MusicBrainz, not through any server of
ours, and is governed by that service's own terms rather than this policy.

This is **off by default**, unlike the lyrics lookup, and the difference is
deliberate: a lyric has no second source, while this one only ever *adds* rows
to a table that already stands on your player's own tags. Leaving it off costs a
few lines rather than the feature. Nothing is sent unless you both switch it on
**and** select the Metadata face — no other face requests it. Results are held
in memory only for as long as the phone app is running and are never written to
disk.

## Song lyrics

The watch has two lyrics surfaces: a screen you can put on any button, gesture
or menu slot, and the **Verse** watch face, which shows the line being sung on
the main player screen. Either one asks the **phone** to look the words up —
the watch never makes the request itself, since a watch paired over Bluetooth
has no internet connection of its own.

The phone sends the **track name, the artist name and the track's length** to
[LRCLIB](https://lrclib.net), a free public lyrics database, which matches on
exactly those three things and needs no account or API key. Nothing else is
attached: no identifier, no account, no listening history, and no record of
which app was playing. The request goes directly to LRCLIB, not through any
server of ours, and is governed by that service's own terms rather than this
policy.

Nothing is requested unless one of those two surfaces is in use. Opening the
lyrics screen looks up the current track; selecting the Verse face looks up each
track while that face stays selected, since its whole purpose is to follow the
words. Choose any other face and never open the lyrics screen, and no lookup
ever happens. Lyrics that come back are held **in memory only** for as long as
the phone app is running and are never written to disk; closing the app forgets
them.

This is **on by default**, for the same reason the queue covers above are: a
lyric has no second source, so switching it off does not degrade the screen, it
empties it. **Look up lyrics online** (Settings → Apps → Music apps & services)
turns it off, and both surfaces then simply report that lookups are
disabled. Everything else in the app keeps working offline.

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

- We don't require an account to use playback controls or browse Community
  themes. An optional Google sign-in is offered only after an explicit
  Community-theme submission action.
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

Questions about this policy or your data, including a Community-theme takedown
or removal request, can be sent to: **gabrielsvafoss@gmail.com**
