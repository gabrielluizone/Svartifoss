# Privacy Policy for Svartifoss

**Last updated: 01-09-2026**

Svartifoss ("the app", "we", "our") is a Wear OS companion app that lets a
paired watch control music playback on your phone. This policy explains what
information the app accesses, what it stores, and what — if anything — leaves
your device.

The short version: **Svartifoss does not require an account for playback,
watch control, or Community-theme browsing; does not operate its own backend
server; and does not sell or share your data with advertisers.** The phone and
watch talk to each other directly over your local Bluetooth/Wi-Fi connection.
Optional update checks and the opt-in Community themes gallery contact GitHub
Pages. If you explicitly submit one of your themes, tap **Like** on a
published Community theme, or select its private **Liked** filter, Google
Firebase Authentication can process the Google credential needed for that
action and Google Cloud Firestore keeps the corresponding private record; your
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
| Internet                                                     | Used for optional update checks, the opt-in Community themes gallery, and — only after you explicitly choose to submit a theme, like one, or select the private Liked filter — Google Sign-In/Firebase Authentication and Firestore. It is also used for the Firebase diagnostics described below, looking up song lyrics when you open the lyrics screen on the watch, and — only if you turn them on — fetching shortcut artwork from the streaming service and downloading queue covers that the music app published as links. Core playback mirroring and control work locally between your two devices.                         |

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
site. Nothing is requested until you open that screen. Searching, filtering by
base layout, and ordering by newest or most liked happen locally over that
downloaded public catalogue. Browsing does not start Google Sign-In or write to
Firebase, and does not send your playback, library, saved themes, account
details, or a device identifier. The request identifies the app only with a
generic Svartifoss user-agent, while GitHub necessarily receives ordinary
web-request information such as your IP address under its own privacy terms.

A card opens a detail page before a theme is added to **My themes**. Gallery
cards remain synthetic, and the detail page renders its Player, always-on
display, Volume, Progress, Quick panel, and Queue previews locally from the
profile with sample labels, timing, and queue data. The detail preview may show
only the current album cover already held in memory on your phone. It does not
use the current title, artist, playback position, queue, or other live media
metadata; the cover is never uploaded or transmitted.

**Svartifoss never captures anything from your watch.** It does not request,
take, or trigger a screenshot on the watch, and the watch never sends the app a
picture of its own screen.

Some themes carry **one photograph published by their author**: a screenshot
that author took on their own watch and chose to attach when submitting. Where
one exists, the detail page's Player shows it instead of the generated preview,
labelled as the author's watch, with a control to switch back to the generated
one. That control is remembered, and turning it off stops the app requesting
these images at all rather than merely hiding them. The image is a public file
served from the same GitHub Pages site as the rest of the catalogue, requested
only when you open a detail page for a theme that has one, and cached in the
app's disposable cache. The downloaded catalogue, profile and image files are
public content and are not part of a configuration backup.

### Likes

Liking a published Community theme is optional and starts only when you tap its
**Like** button. If you are not already authenticated, that explicit tap is
what offers Google Sign-In; simply browsing, searching, filtering by base
layout, sorting, or opening a detail page does not open a sign-in prompt. The
separate **Liked** filter is also explicit: selecting it can offer Google
Sign-In and then reads only that person's reactions for IDs already in the
downloaded public catalogue. A person who is already signed in may have only
their own reaction read when a detail page opens so the button can show its
current state.

Each like is a private per-account document at
`communityThemeLikes/<theme ID>/voters/<Firebase Auth UID>`. It contains only a
schema version and a Firestore server timestamp; it does not contain your
Google name or email. Firestore rules permit you to create, read, or delete
only your own reaction. They do not permit any client to list voters, read
another person's reaction, update a reaction, or write a public count.

The **Liked** filter does not list a voter collection or discover theme IDs
through Firebase. It checks only the current Firebase Auth UID's document at
the known private-reaction path for each ID already public in the downloaded
catalogue.

The public catalogue shows only a trusted aggregate count. The GitHub publisher
counts the private reactions and later writes the total into the static
catalogue, so a like or unlike can take until the next publication that
rewrites it — and at most about a week — to appear there or affect the **most
liked** ordering. Your own reaction is shown immediately on your own device.
The public count never identifies who reacted.

### Submitting a theme

Submitting is separate from browsing. You can explicitly connect Google first
under **Settings → General → Community themes → Community account**, or
choose **Submit to community** for a private, user-owned saved theme and tap
**Sign in and submit**. Firebase keeps that connection on this device, so later
submissions reuse it without showing the account picker again. Before sending a
theme, the app creates and checks a fresh public profile locally. The current
preflight requires at least **12** setting values to differ from the selected
base face; this is an eligibility filter, not a promise that the theme will be
approved.

At that explicit point, Firebase Authentication processes the Google
credential and account information needed to authenticate you, then issues a
Firebase Auth UID. Svartifoss stores that UID — rather than your Google account
name or email — in the Firestore submission document as the ownership
identifier (`ownerUid`). It does not show your Google account name or email to
gallery visitors or publish them. Instead, you choose the public theme name and
either an author pseudonym or the public credit **Anonymous** yourself.

Firestore receives one immutable **pending** submission record. Its submitted
content includes the fresh public theme ID; the complete typed profile JSON and
its settings digest; the public theme name and selected public author credit; the base face,
revision, client version, and Firestore server timestamp. It also includes a
fixed-sample 200×200 WebP review preview rendered from Svartifoss's built-in
sample track, not your current playback, album art, or media metadata. A
configured moderator can later add only an approval or rejection decision,
their reviewer UID, and a server timestamp; neither the author nor a moderator
can edit the submitted theme content through the app.

#### Attaching a photograph of your watch (optional)

The submission screen lets you attach **one** image, of the Player screen, from
your phone's photo picker. It is entirely optional: attaching nothing is the
normal case, and a theme submitted without one shows only the generated preview.
Svartifoss does not take the picture — you choose an existing image, and the
app never reads your gallery beyond the single file you hand it through the
system picker, which needs no storage permission.

What you attach is published, and the submission screen says so before the
picker opens. The image is cropped to a square, resized to at most 450×450 and
re-encoded as WebP on your phone; re-encoding removes any camera or location
metadata the original file carried. It is then stored in Firestore alongside the
submission, shown to a moderator, and — if approved — committed to this
project's public Git repository and served from GitHub Pages. **Everything
visible in it becomes public permanently**, including the album cover, track
name, artist and clock time that happen to be on the watch screen, and it
remains in the repository's history even if the theme is later removed. A
moderator may approve a theme and refuse its photograph, in which case only the
theme is published.

Firestore also keeps a separate private quota record tied to your Firebase Auth
UID. It records the submission count, most recent submission ID, and the
timestamp needed to enforce at most **ten** Community-theme submissions in a
24-hour period. It is not public.

Google processes Authentication and Firestore data under
[Google's Privacy Policy](https://policies.google.com/privacy) and
[Firebase's data processing terms](https://firebase.google.com/support/privacy).

### Moderation and publication

The pending record is private to its owner and configured moderators. Approval
does not make a theme public immediately. When an approved submission is
published, the trusted publisher validates it again and commits the public
profile JSON, public theme name, selected public author credit, and any approved
author photograph to this project's Git
repository; GitHub Pages then serves those public files in the catalogue. The
Firebase Auth UID and Google account name/email are not copied to the public
repository or catalogue.

Version 3.3 does not implement comments or theme-update submissions.

**My submissions** (Community account → See my submissions, or the history
button in the community gallery) reads back the submissions this account has
sent, with the status of each: waiting for review, approved, published,
rejected, or being removed. For a published theme it also shows the public like
count already in the downloaded catalogue; no per-theme figure is requested
from Firebase.

From that screen you can **remove any of your own themes** without deleting
your account. It is a request rather than an instant delete, for the same
reason account deletion is: the catalogue is a set of files in this project's
Git repository, so the trusted publisher removes the file, the catalogue entry
and the theme's likes on its next run, normally within about a day, and then
deletes the submission record. Anyone who already installed the theme keeps
their own local copy. It reads only records whose
owner is your own Firebase Auth UID; Firestore rules refuse anyone else's. It
deliberately does not show who reviewed a submission — the reviewer's identity
is kept in a separate moderator-only record precisely so that showing you your
own outcomes never reveals it.

### Deleting your community account

**Settings → General → Community themes → Community account → Delete
account** deletes the account itself. Before it is submitted, the app asks the
one question it cannot answer for you — what should happen to the themes you
have already published:

- **Keep my published themes.** They stay in the public catalogue under the
  author credit they were published with, and the record linking them to your
  account is stripped of your Firebase Auth UID.
- **Delete my published themes too.** Their public profile files and catalogue
  entries are removed from this project's Git repository on the next publication
  run, together with every like recorded against them.

Under either choice, the deletion removes your Firebase Authentication
identity, your private submission-quota record, every private like document
created by that account, and any submission of yours that is not public — a
theme still waiting for review, or one that was rejected, is deleted in both
cases, because there is nothing published to keep.

The request is carried out by the same trusted publisher that publishes themes,
on its next scheduled run, normally within about a day. The app records only
your decision; it never deletes the public catalogue or the identity by itself,
because doing only the half an app can reach would destroy the account while
leaving its published content behind. For the same reason the request cannot be
edited or withdrawn from the app once confirmed, and the screen says so before
asking.

An account that exists only for likes — the silent anonymous one described
above — can be deleted the same way. It owns no themes, so it is not asked the
question; its identity and its private like documents are removed.

Two limits are worth stating plainly. Removing a public listing cannot erase
information already committed to Git history or copied into forks, clones,
caches, or third-party archives, and anyone who already installed one of your
themes keeps their own local copy on their own phone. **Remove from this
device**, listed beside it, is a different action: it only signs out locally and
deletes nothing. Uninstalling the app also removes local data but does not
retract a submission or a private reaction already stored in Firestore.

You can remove a single reaction at any time by tapping **Unlike** on that
theme's detail page, for as long as the app still holds the identifier that
created it. For a takedown request about someone else's theme, or help
identifying a community-theme record, email the address in [Contact](#contact);
those requests are handled manually.

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
anyone. The same covers are what the phone's own playback-queue sheet draws;
that one stays on the phone and is never transmitted anywhere. Declining
leaves both queues working with blank thumbnails.

**From the internet.** Streaming clients publish a cover URL instead of an
image. **Fetch queue covers online** (Settings → Apps, and the Watch face
tab's Panels section) downloads each such cover once and caches it on the
phone, for the queue on the watch and the phone's own queue sheet alike. Only the cover URL the music app itself published is requested; no
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

- We don't require an account to use playback controls, browse Community
  themes, or like them. An optional Google sign-in is offered only when you
  explicitly submit a Community theme.
- We don't run our own backend server that your data passes through.
- We don't sell, rent, or share your data with advertisers or data brokers.
- We don't show ads.
- We don't read the content of unrelated notifications. For the current media
  app, only playback metadata and — when explicitly selected — its media action
  labels/icons are used.
- We don't capture your watch's screen. The only images Svartifoss ever
  publishes are ones you picked yourself and attached to a Community theme
  submission.

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
