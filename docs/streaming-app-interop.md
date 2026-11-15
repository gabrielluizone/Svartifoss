# Streaming app interoperability: ground rules and playbook

How Svartifoss talks to Spotify, SoundCloud, YouTube Music and the other streaming apps on the phone, what it may and may not do to learn how they behave, and what to build next. Written 2026-10-24, after the report that picking a saved Spotify or SoundCloud playlist on the watch opened the app without playing it.

**Status.** The hand-off described in section 4 is implemented, unit-tested, and was tested the same day on a Pixel 3 (Android 10) with Spotify 9.1.92 on a Free account, SoundCloud 2026.10.14 and Amazon Music 26.36.1. The results are in section 5.6 and changed the conclusions: SoundCloud's short share links now play silently, while this Spotify refuses every outside request to play something specific.

**Update, 2026-11-11.** Three more causes of a shortcut that does not play were found and fixed, this time on an Android 14 emulator with a stand-in player (sections 3 and 5.7): the phone pressed play on the track an app came up holding, it took an app that only buffered for one that played, and the first command to reach a freshly started process had its work cancelled. None is about any one app, and none changes the conclusion about Spotify on a free account, which still plays nothing it is asked for from outside. The watch's own Spotify and YouTube Music apps were tried as a way round that (section 5.8); they are not one.

This is engineering guidance, not legal advice. Where it cites a law or a contract it quotes the text; for a decision with real money or a takedown behind it, ask a lawyer.

---

## 1. Ground rules

### The one-line version

**Use what an app declares to Android, never what you dig out of its code.** Every app tells the operating system, in its manifest, which intents it accepts, which services it exports and which links it opens. Any app can read those declarations through `PackageManager`, anyone can print them with `adb shell dumpsys package <package>`, and using them is what they are for. The code behind them is someone else's property under a contract that forbids opening it.

### Why the line sits there

Five separate bodies of rules apply, and each draws the line in the same place.

**Copyright protects code, not interfaces or ideas.** Brazilian law excludes "ideas, normative procedures, systems, methods" from protection (Lei 9.610/98, art. 8, I), and says that similarity between programs forced by their functional characteristics is not infringement (Lei 9.609/98, art. 6, III). Knowing that Spotify accepts a certain intent is not a protected thing; copying Spotify's code would be. Brazil, unlike the EU (Directive 2009/24/EC, arts. 5(3), 6 and 8), has no express statutory right to *decompile* for interoperability, which matters for the next point.

**The apps' terms forbid decompiling, and forbid publishing what it yields.** Spotify's User Guidelines (same text in the Brazilian and US versions, read 2026-10-24) prohibit "reverse-engineering, decompiling, disassembling, modifying, or creating derivative works, except where such restriction is expressly prohibited by applicable law", and add that where the law does allow decompiling for interoperability, the information obtained "may not be disclosed or communicated without Spotify's prior written consent to any third party". An open-source repository is disclosure to everyone. So even a lawful decompilation could not feed code into Svartifoss. Other services' terms commonly carry clauses of the same kind; read the specific app's before touching it. Breaching them invites account termination and a civil claim, and Brazilian software law also makes violating a program's copyright a crime (Lei 9.609/98, art. 12), so decompiling without a clear statutory exception is not a risk worth taking. "I can't be sued" means staying out of it.

**Never defeat a technical measure.** Brazilian copyright law sanctions defeating the technical devices that protect works (Lei 9.610/98, art. 107), as the DMCA (§1201) does in the US, and where no statute reaches a particular check, defeating it is still exactly what the apps' terms forbid. In this app that means: never pretend to be another package or carry another app's signature to get past an allowlist (Spotify's and YouTube Music's media browsers refuse unknown clients on purpose), never touch DRM, never reimplement a private protocol such as Spotify Connect's. **A refusal is an answer.** Asking the app's *other* public entry points, under Svartifoss's own name, is not a workaround; asking the same one in disguise is.

**Trademarks: say what it works with, don't wear their badge.** Naming Spotify to say a shortcut opens in Spotify is ordinary descriptive use, the way an accessory maker may name the product its accessory is for (Lei 9.279/96, art. 132, II, "desde que obedecidas as práticas leais de concorrência"). Using a logo in a way that suggests endorsement is not, and Spotify's guidelines name "using the Spotify logo without permission" as misrepresenting affiliation. See section 6.8 for the one place this touches the app today.

**Google Play's rules agree.** The Device and Network Abuse policy forbids accessing other apps "in an unauthorized manner"; using exported intents and the media session the user granted notification access for is the authorized manner. Play also flags *intent redirection* (an exported component that launches whatever an incoming intent tells it to), which is why the hand-off in section 4 carries a token and nothing else.

### Traffic lights

| | What | Examples in Svartifoss |
|---|---|---|
| **Green** | Android platform contracts the app implements for everyone | `MediaSession` transport controls (with notification access), `MediaBrowserService`/`MediaLibraryService` when the app accepts the connection, `ACTION_VIEW` deep links, `MEDIA_PLAY_FROM_SEARCH`, media keys through `AudioManager` |
| **Green** | Reading public declarations | `PackageManager.queryIntentActivities`, `dumpsys package`, `dumpsys media_session`, the developer report in Settings (section 5.1) |
| **Green** | A service's documented web APIs, under their terms | oEmbed for names and covers, the YouTube Data API key flow |
| **Green** | AOSP source code | It is open source; section 6.1 relies on it |
| **Yellow** | Public entry points with undocumented behaviour | Spotify's `:play` URI suffix, SoundCloud's exported "Play Likes" shortcut action. Use only if the installed app declares them, verify at runtime, fall back silently (section 4 does all three) |
| **Yellow** | Watching an app's behaviour on your own phone | `logcat`, what a session publishes, black-box tests of declared intents |
| **Red** | Decompiling an app's code (DEX, native libraries) to find hidden behaviour | Anything learned that way must never reach this repository |
| **Red** | Impersonation | Fake package names, borrowed signatures, spoofed `EXTRA_REFERRER` |
| **Red** | Private protocols, scraping, unofficial APIs | Spotify Connect, YouTube Music's internal API, scraping web players |
| **Red** | Redistributing their software | Committing APKs or decompiled output to this repository |

### What this means for REA and the APK folder

REA (Reverse Engineer Anything) is a capable tool, and the earlier competitor report in `Svartifoss-5/Analise-REA/` used it to decompile the Wear OS builds of Google Media Controls, Samsung's controller, YouTube Music and Spotify. Under the terms quoted above, decompiling Spotify was outside what its terms allow, and Brazilian law does not clearly override that; the practical risk of a private analysis that nobody publishes is low, but the decompiled output in `evidencias/` should stay off every shared drive and be deleted once it is no longer needed, and nothing derived from it may enter this codebase.

For this work only **manifests** were read: `aapt2 dump xmltree` on the `AndroidManifest.xml` of the APKs placed in `rea-apps/`, no DEX code opened. That is the same information `dumpsys package` prints from an installed app, which is the recommended way to get it from now on: it needs no download at all. Two housekeeping changes came out of it:

- `rea-apps/`, `*.apkm`, `*.xapk` and `*.apks` are now in `.gitignore`. `*.apk` already was, but APKMirror bundles are `.apkm`, so `git add -A` would have published Spotify's and SoundCloud's apps from a GPL repository, the one clearly unlawful step in this whole subject.
- Of the "Android" APKs in that folder, only Spotify's is the phone app. SoundCloud's is the **Android TV** build, YouTube Music's the **Android Automotive** build and Amazon's the **Fire TV** build ("bueller"). Their manifests are indications, not facts about the phone apps Svartifoss talks to.

---

## 2. What each app declares

From the manifests in `rea-apps/` (versions as named), read 2026-10-24. Only what bears on starting playback from outside.

### Spotify (phone), 9.1.94.1912

`com.spotify.music.SpotifyMainActivity` declares, among many others:

- `VIEW` on `spotify:` with `DEFAULT`, `BROWSABLE` and `CAR_MODE`: the documented deep link. Spotify's content-linking guide documents opening its content this way and asks callers to set `EXTRA_REFERRER` to their package. It says nothing about playback.
- `VIEW` on `https://open.spotify.com/{album,artist,playlist,track,collection,show,episode,...}` (verified App Links).
- **`android.media.action.MEDIA_PLAY_FROM_SEARCH` with `DEFAULT`, and a second filter with `DEFAULT` + `BROWSABLE` + data scheme `spotify`.** This is the finding that matters: Android's "play this" voice contract, accepted with Spotify's own URI as data, and marked browsable, which means Spotify accepts it even from web pages. A third-party app is squarely inside the audience Spotify opened it to.
- `android.intent.action.SEARCH`.

Services and receivers:

- `com.spotify.mediabrowserservice.mediabrowserservice.SpotifyMediaBrowserService`: legacy `MediaBrowserService`.
- `com.spotify.mediabrowserservice.medialibraryservice.SpotifyMediaLibraryService`: legacy `MediaBrowserService` **and** Media3 `MediaLibraryService`. Two browser services, each answering connections by its own rules.
- `com.spotify.mediasession.mediasession.receiver.MediaButtonReceiver` (`MEDIA_BUTTON`).
- An Android Auto car-app service, a `MediaRoute2ProviderService` (Spotify Connect devices in the system output switcher) and a `TRANSFER_MEDIA` activity.

Behaviour reported by users rather than declared, from the Spotify Community (threads from 2015 to 2019, so possibly stale):

- `VIEW` of `spotify:user:spotify:playlist:<id>:play` started the playlist, "even when Spotify is offline". The `:play` suffix is not documented by Spotify.
- `MEDIA_PLAY_FROM_SEARCH` with a playlist URI stopped autoplaying playlists in Spotify 8.4.83, while albums kept working.
- A play-from-search with the screen off opened Spotify on an empty page.

What Svartifoss already knew from experience: Spotify refuses unknown media-browser clients, and a `VIEW` of a content URI only navigates.

### SoundCloud: Android TV build 2026.10.19 (stands in for the phone build until checked)

- `com.soundcloud.android.ui.main.MainActivity` exports actions including `com.soundcloud.android.action.SHORTCUT_PLAY_LIKES` (the launcher shortcut "Play Likes"), `SHORTCUT_SEARCH`, `PERFORM_SEARCH`, `STREAM`, `COLLECTION`, `android.intent.action.SEARCH` and **`MEDIA_PLAY_FROM_SEARCH`** (`DEFAULT` only, so not browsable).
- `ResolveActivity` opens `soundcloud://` and the `soundcloud.com` hosts.
- `TvMediaService`: legacy `MediaBrowserService`; the Wear build instead has a Media3 `WearPlaybackService` (see `player-integration-notes.md`).

Known from Svartifoss's own testing: SoundCloud's session plays a track URI and silently ignores a playlist or Likes URI.

### YouTube Music (Automotive and Wear builds)

`com.google.android.apps.youtube.music.mediabrowser.MusicBrowserService` (legacy browser + `MEDIA_BUTTON`). On the phone, YouTube Music already plays shortcuts through its media browser, including with the screen off, so nothing here changes for it. (No longer true of every version: 8.30.54 refuses Svartifoss's browser, see 5.9.)

### Amazon Music (Fire TV and Wear builds)

`VIEW` on `amznmp3://` and on the `music.amazon.*` hosts, and a legacy `MediaBrowserService`. `StreamingShortcutLinks` recognises the scheme `amzn-music:`, which neither of these builds declares.

### The Wear OS builds (manifests read 2026-11-11)

From the APKs in `rea-apps/Wear-Apps/`, manifests only, and for Spotify also `dumpsys package` on a TicWatch Pro 5 (Wear OS 4), where 9.1.98.620 declares the same.

| App | Links it opens | Media browser | Other surfaces |
|---|---|---|---|
| Spotify 9.1.94 | `VIEW` on `spotify:` (browsable). Its launcher filter also carries `com.google.android.wearable.action.MEDIA_CONTROLS` | `WearMediaBrowserService`, legacy, exported | a complication (`SHORT_TEXT`, `SMALL_IMAGE`, `ICON`), two Tiles, a Glance Wear widget that allows several instances, Health Services |
| YouTube Music 9.42.42 | `VIEW` on `https://music.youtube.com/…` through an alias, and `com.google.android.youtube.music.action.navigate` with the `APP_MUSIC` category | `MusicBrowserService`, legacy | a Tile, a Glance Wear widget (disabled in the manifest) |
| SoundCloud 2026.01.14 | `VIEW` on `soundcloud://soundcloud` | a Media3 `WearPlaybackService` (library and session) | none |
| Amazon Music 26.3.0 | none | not exported | a Data Layer listener on `/wearos` and `/wearos/playbackState`: it has a tethered mode of its own |
| Google Media Controls 1.8.86, the system's | `com.google.android.wearable.action.MEDIA_CONTROLS`, and the output switcher on `com.android.settings.panel.action.MEDIA_OUTPUT` | `AndroidSessionAdvertisingService`, a Media3 session service behind the permission `com.google.android.wearable.media.sessions.permission.BIND_THIRD_PARTY_MEDIA_CONTROLS`, which is declared with the *normal* protection level | a complication (`LARGE_IMAGE`, `SMALL_IMAGE`, `LONG_TEXT`), and a `ThirdPartyMediaControlService` that is disabled |

Set against Svartifoss's own watch app: every complication type and Tile these declare has its counterpart already (album art as `SMALL_IMAGE`, `PHOTO_IMAGE` and `LONG_TEXT`; play and pause as `RANGED_VALUE`, `SHORT_TEXT` and `ICON`; three Tiles). What it lacks is a Glance Wear widget, which still needs SDK 37 and alpha libraries, and the two leads in sections 6.9 and 6.10.

### Confirmed on a phone (Pixel 3, Android 10, 2026-10-24, `dumpsys package`)

- **Spotify 9.1.92.3058:** both `MEDIA_PLAY_FROM_SEARCH` filters (one with `BROWSABLE` and the `spotify` scheme), `SpotifyMediaBrowserService` and the Media3 `MediaLibraryService`, as in the APK above. Its live session advertised `PLAY_FROM_URI`, `PLAY_FROM_SEARCH` and `PREPARE_FROM_URI`, but not `SEEK_TO` or `SKIP_TO_PREVIOUS`, which is what a Spotify Free account looks like.
- **SoundCloud 2026.10.14 (phone):** `SHORTCUT_PLAY_LIKES`, `MEDIA_PLAY_FROM_SEARCH` and a legacy `MediaBrowserService`, so the TV build was a fair stand-in.
- **Amazon Music 26.36.1 (phone):** `MEDIA_PLAY_FROM_SEARCH`, a legacy browser and a Media3 session service, and the schemes `amzn` and `amznmp3`, **not** `amzn-music`. A saved `amzn-music:` link is classified as Amazon by Svartifoss but opens nothing on this phone; share links (`https://music.amazon.*`) are unaffected. Since 2026-11-11 `StreamingShortcutLinks.detect` recognises `amznmp3:` as well (and not the generic `amzn:`, which other Amazon apps may claim); `amzn-music:` stays recognised for links that were saved with it.

---

## 3. Why the shortcut opened without playing

`MusicService.playDeepLink` tries three things in order (see CLAUDE.md, *Actions system*):

1. **The media session.** If the app already has a session, `playFromUri` on it, then check that something new is playing. Spotify not running means no session, so this is skipped.
2. **The media browser.** Bind the app's `MediaBrowserService` and play from there: the Android Auto path, the only one that works with the screen off. Spotify refuses unknown clients, so this fails at the connection.
3. **The visible open.** The watch opens the link on the phone (`RemoteActivityHelper`, the only launch a watch can make on a phone in the background), then the phone keeps asking the new session to play. But a `VIEW` of a Spotify playlist navigates and does not play, the retries either find no active session or are ignored, and the final "press play" is deliberately withheld when the app was holding some other track (it would resume the wrong music).

So the user saw Spotify open on the right playlist with nothing playing, and SoundCloud the same. The missing piece was never a hidden API: it was that the only launch the watch can make (a `VIEW`) is the one launch that does not mean *play*, and the launch that does (`MEDIA_PLAY_FROM_SEARCH`, or an app's own "play" action) has to be started by something in the foreground on the phone.

A second, separate bug was found on the way. When the watch searches for a song in an app whose library cannot be searched (Spotify again), the phone fired the voice-search intent from its background service. Since Android 10 a background activity start is dropped without an error, so the call "succeeded", the two silent routes after it never ran, and nothing played with the phone in a pocket. The intent is now used only while one of Svartifoss's screens is showing; otherwise the session's own `playFromSearch` (which Spotify honours) runs.

### Three more, found on an emulator (2026-11-11)

All three were in Svartifoss, none in any player, and each failed without an error anywhere. Section 5.7 has the runs.

1. **Play was pressed on the wrong track.** After a plain open (step 3 without the hand-off) the phone re-sent the request every 200 ms for 3.2 s, then pressed play if "the app had no track before the link opened". An app that was not running has no track before, and comes up holding the one it played last, so the press started that: the right page on screen and the wrong music playing. The hand-off already knew this (it never pressed play after a cold start); the plain open, which is where a Spotify shortcut ends once both hand-off techniques are benched, did not. The loop also stopped for good after 3.2 s, so an app whose session came up later than that was never asked at all. Both visible routes are now followed by one policy, `VisibleOpenFollowUp`: the request goes out once the session has settled (up to three times, 1.5 s apart), and play is pressed only on content that *replaced* the first track the session showed.
2. **Buffering was taken for playing.** Step 2 (the media browser) returned success the moment the session became active, and active included buffering and connecting. An app that accepts the connection, buffers while it tries the address and gives up was therefore "played", the watch was told there was nothing to open, and the shortcut ended with nothing playing and nothing on screen. Only `STATE_PLAYING` counts now; a session that is busy is waited on (and not sent the command again meanwhile), up to 3 s past the usual 5 s.
3. **The first command into a cold process was cancelled.** When the phone's process starts on a watch command, the notification listener the system rebinds at that moment asks for a start of `MusicService` too, and usually gets in first. That start is not from the watch, so its branch called `stopSelf()`; both starts are delivered back to back, so the stop landed right after the watch's start had run and taken the command, and the shortcut's whole attempt was cancelled with the service. It is `stopSelf(startId)` now, which stops only if no newer start is waiting. A phone that kills background apps is in this state every time the app has been idle, which makes this the likeliest of the three to be behind "the first tap does nothing".

---

## 4. The hand-off (implemented)

The watch keeps making the only launch it can make, a browsable link; the phone answers with a link to **itself**, and that tiny activity, being in the foreground, starts the launch that plays.

```
watch: tap a Spotify playlist
  -> phone: steps 1 and 2 fail as before
  -> phone: VisiblePlaybackPlan lists what Spotify declares:   MEDIA_PLAY_FROM_SEARCH spotify:playlist:<id>
                                                                VIEW spotify:playlist:<id>:play
           VisiblePlaybackMemory picks one (what worked on this phone, else the best untried)
           VisiblePlaybackRegistry stores it under a random one-time token
  -> verdict to the watch: com.svartifoss.snfell|svartifoss://play-shortcut?t=<token>
  -> watch opens it (unchanged watch code)
  -> phone: StreamingPlaybackActivity (invisible, one frame) claims the token and starts the launch
  -> phone: MusicService checks for 12 s that the app is playing something new, records the result
```

The pieces, all in `mobile/src/main/java/com/svartifoss/snfell/`:

- `music/VisiblePlaybackPlan.kt` (pure): which launches can make a shortcut play, best first. Spotify, official app only: `MEDIA_PLAY_FROM_SEARCH` with the URI, then `VIEW` with `:play`. SoundCloud, the account's own `/you/likes` only: the exported "Play Likes" action. A SoundCloud set or track, and an artist in any app: `MEDIA_PLAY_FROM_SEARCH` by name with Android's media-focus extras (a close match, the same trade the media browser route already makes). Never a name search for a precise playlist elsewhere, where a stranger's playlist of the same name would play confidently and be wrong, and never for a personal collection.
- `music/VisibleLaunchIntents.kt`: builds the real `Intent`, always package-targeted, with `EXTRA_REFERRER` as Spotify's guide asks, and checks the installed app declares it before anything is tried.
- `music/VisiblePlaybackMemory.kt` (pure + a private preferences file): per app and kind of content, keeps the technique that played and benches one that did not (one strike for an untested technique, two for a proven one, retried after a week because apps update). If all are benched, the shortcut just opens, as before.
- `music/VisiblePlaybackRegistry.kt`: the token store. Single use, at most four pending, and **time-boxed**: within 60 s the launch may start playback, after that and up to 10 minutes it only opens the content (a phone unlocked long after the tap must not start music by surprise), after that it is gone.
- `view/handoff/StreamingPlaybackActivity.kt`: exported, browsable, translucent, `noHistory`. Reads the token and nothing else from the link; `StreamingHandoffContractTest` fails the build if it ever reads an extra, a parameter or builds its own intent, because that would turn it into an intent-redirection hole any web page could use.
- `MusicService`: `prepareVisiblePlayback`, `playThroughHandoff`, `followVisibleOpen`. When Svartifoss is itself on screen it also starts the activity directly (a shortcut started from the phone's own UI has no watch to open it); the token makes the second open a no-op. Success means the app's session reached `STATE_PLAYING` (not `CONNECTING` or `BUFFERING`, which a cold app publishes whatever it was asked; see 5.6).
- `music/VisibleOpenFollowUp.kt` (pure, since 2026-11-11): what is done while the app comes up, after the hand-off's launch and after a plain open alike. The session is asked for the content once it has been settled for a second, at most three times; a busy session is left alone; and play is pressed only on content that replaced the first track the session showed, 1.5 s after it appeared, because that first track is as likely the one the app restored as the one that was asked for.

Also in this change:

- **SoundCloud short links play by name.** A share-sheet link (`on.soundcloud.com/…`) classifies as `UNKNOWN` until opened, which used to skip every silent route. `playDeepLink` now treats it like an artist (`preferSearch`): the saved name, cleaned by `StreamingShortcutLinks.searchableName` (which drops the " - SoundCloud" share sheets append), goes to the session's and the library's `playFromSearch`, and to the name search in the hand-off. Section 5.6: this is what played silently.
- **Every browser service is tried.** `MediaBrowserSearch.findBrowserServices` ranks all of an app's browser services (Spotify has two) and `connectFirst` uses the first that accepts, for playback, search and library browsing alike. If Spotify's Media3 service accepts a generic client where the legacy one refuses, Spotify shortcuts would start silently with the screen off, the best outcome of all; nobody knows yet, and trying costs one fast refusal.
- **The search bug in section 3** is fixed in `MusicService.playFromSearch` (`appIsVisible`).
- **The developer report** (Settings → Data & support → Developer → MediaSession explorer) now lists, per installed streaming app, its browser services, which play techniques it declares, and what each has done on this phone.

The watch needed no change: its `PhoneUriOpener` opens whatever `package|uri` the verdict names, and an older watch build behaves the same.

Since 2026-11-11 the watch also says so. A verdict that carries a link means the phone could not start the music silently, and what follows needs the phone in hand: it must be unlocked for the link to open at all, and an app that only shows the content has to be pressed play there. The watch used to open the link and say nothing, so a shortcut that ended on a locked phone's screen looked like one that did nothing. `OpenOnPhoneNotice` now shows the system's "Continue on your phone" confirmation (the one **Open on phone** already used, same words) when the link arrives, from whichever of the app's screens is in front; not for a pick made from the Tile, which leaves none in front. This half is unit-tested and compiled, and has not been seen on a watch yet.

---

## 5. Verifying it

### 5.1 On the phone, no computer

Settings → Data & support → tap the version seven times → Developer → **MediaSession explorer**. Below the active sessions it now has a "Streaming apps" section; with Spotify installed it should read something like:

```
com.spotify.music
  Browser services: SpotifyMediaBrowserService, SpotifyMediaLibraryService
  Play techniques declared: uri_search, view_autoplay, name_search
  Seen on this phone for playlist: uri_search failed x1, view_autoplay played
```

The last line appears after the first few shortcuts and is the answer to "which technique works with this Spotify".

### 5.2 Read-only checks over adb

These change nothing on the phone:

```sh
adb shell dumpsys package com.spotify.music | grep -B2 -A6 MEDIA_PLAY_FROM_SEARCH
adb shell dumpsys package com.soundcloud.android | grep -E "SHORTCUT_PLAY_LIKES|MEDIA_PLAY_FROM_SEARCH|MediaBrowserService|media3"
adb shell dumpsys package com.amazon.mp3 | grep -E "scheme|MediaBrowserService"
adb shell dumpsys media_session | grep -A12 "package=com.spotify.music"
# debug build only (release drops these lines); the app logs under one tag, class in brackets
adb logcat -s WearMusicCenter | grep -iE "playDeepLink|hand-off"
```

### 5.3 Black-box tests (these start music: ask before running them on someone's phone)

Each is a declared, public entry point; run with the screen unlocked and watch whether the app starts playing (`dumpsys media_session`, `state=3`).

```sh
# Spotify: play-from-search with the URI (technique uri_search)
adb shell am start -a android.media.action.MEDIA_PLAY_FROM_SEARCH -c android.intent.category.BROWSABLE \
  -d "spotify:playlist:37i9dQZF1DXcBWIGoYBM5M" -p com.spotify.music
# Spotify: the :play suffix (technique view_autoplay)
adb shell am start -a android.intent.action.VIEW -c android.intent.category.BROWSABLE \
  -d "spotify:playlist:37i9dQZF1DXcBWIGoYBM5M:play" -p com.spotify.music
# SoundCloud: Play Likes (technique app_action)
adb shell am start -a com.soundcloud.android.action.SHORTCUT_PLAY_LIKES -p com.soundcloud.android
# Any app: an artist by name (technique name_search)
adb shell am start -a android.media.action.MEDIA_PLAY_FROM_SEARCH -p com.spotify.music \
  --es query "Daft Punk" --es android.intent.extra.focus vnd.android.cursor.item/artist \
  --es android.intent.extra.artist "Daft Punk"
```

Run each twice: once with the app force-stopped (`am force-stop`) and once with it paused on another track. Note Free and Premium Spotify accounts may differ (Free plays most playlists shuffled).

### 5.4 End to end from the watch

1. `adb shell am force-stop com.spotify.music`, phone unlocked.
2. On the watch, pick a saved Spotify playlist and press Play.
3. Expected: Spotify comes to the front and starts that playlist within a few seconds; the debug log shows `step 3 (visible) through the hand-off with PLAY_FROM_SEARCH_URI`, `Streaming hand-off started`, and `played=true`.
4. Repeat with the phone locked: nothing happens until it is unlocked; unlocked within a minute it plays, unlocked later it only opens the playlist.
5. Repeat with SoundCloud Likes, a SoundCloud set, and an artist shortcut.

### 5.5 Open questions

- How Spotify **Premium** behaves: the autoplay-link reports are from Premium accounts, and every test below was on Free.
- How `RemoteActivityHelper` behaves on a locked phone: queued until unlock (what the 60 s window assumes) or dropped. Not tested: the test phone has a secure lock screen.

### 5.6 Results on a real phone (2026-10-24)

Pixel 3, Android 10; Spotify 9.1.92.3058 on a **Free** account; SoundCloud 2026.10.14; Amazon Music 26.36.1. Every shortcut was started from the watch through the Shortcuts Tile's trampoline (`ShortcutLaunchActivity`, which sends the same `CustomListItemAction` as the actions menu), with the target app force-stopped first unless noted, and judged by the app's own media session reaching `STATE_PLAYING`.

| Shortcut | Route that ran | Result |
|---|---|---|
| Spotify track, album, playlist | step 2 refused (`No root for client com.svartifoss.snfell`), hand-off with `MEDIA_PLAY_FROM_SEARCH` + URI, then with `VIEW …:play` | **Not playing** with either: Spotify opens the right page and settles paused on the track it restores |
| Spotify Liked Songs | hand-off, `MEDIA_PLAY_FROM_SEARCH` + `spotify:collection:tracks` | Not playing |
| Spotify album, app already running and paused | step 1 `playFromUri` on its session ("accepted", ignored), also with the shuffle mode set first | Not playing |
| Spotify, any of the above sent straight from `adb shell am start` | `VIEW …:play`, `MEDIA_PLAY_FROM_SEARCH` with the URI, and by name with the media-focus extras; app cold and warm | Not playing, in all six |
| Spotify, session `playFromSearch` | the watch's recent-tracks replay | Not playing |
| SoundCloud short link to a set (`on.soundcloud.com`) | step 2: SoundCloud's library **accepted** the connection, `playFromSearch` with the name | **Played** in about 3 s, the best match for the name |
| SoundCloud short link to a track | step 2, same | **Played the exact track**, silently |
| SoundCloud Likes (`/you/likes`) | step 2 (connected, `playFromUri` ignored), hand-off with `SHORTCUT_PLAY_LIKES` | Opened the Likes page, **not playing**, cold and warm |
| Amazon Music album link with `do=play` | step 2 refused (`No root for client`), plain open | Not playing; the watch's own open landed on Android's "Open with" chooser (the `https` link's domain is not verified on that phone) |

What the numbers teach:

- **Only `STATE_PLAYING` is evidence.** A cold app publishes `STATE_CONNECTING` within a second whatever it was asked (Spotify does while restoring its last track, then settles on paused). The first version of `awaitHandoffPlayback` counted that as playing and reported four false successes, one of which then taught the memory a technique that does not work. It now waits for `STATE_PLAYING`.
- **This Spotify answers outside requests with navigation only.** Its library refuses Svartifoss by name, its session ignores `playFromUri` and `playFromSearch`, and its activity ignores both play intents, cold and warm. The memory benches both techniques after one failure each, so a Spotify shortcut costs one extra second the first time and then opens the right page, as before. Section 6.2 is the only clean route left.
- **SoundCloud's library accepts Svartifoss**, which no other big app tested here does. That is what made its short links work silently, and it makes 6.3 (finding Likes in the library and playing it by the app's own media id) concrete.
- **A one-time link was claimed by the phone itself** in every hand-off: on Android 10 the service's own `startActivity` went through (the app had a task in Recents, an exemption Android 10 still had), and the watch's later open found the token used, exactly as designed.

### 5.7 On an emulator, with a stand-in player (2026-11-11)

The test phone is Android 10, and half of what goes wrong for other people only exists from Android 12. So this pass ran on an emulator: Android 14 (API 34, `google_apis`, x86_64), the phone app's debug build from the working tree, the app sent to the background, no real player installed.

Two pieces make that possible, and neither ships.

- **A stand-in player** (`rea-apps/mock-player/`, local and gitignored like the APKs beside it; our own code, nobody else's). A 17 KB app built under the package name of the player it stands in for (`build.sh deezer.android.app`), with a media session, a browser service and the link filters, whose behaviour is set by a broadcast: whether the browser accepts an unknown client, what `playFromUri`/`playFromSearch`/`playFromMediaId` do (honour, ignore, or buffer and give up), which actions the session advertises, whether it comes up holding a restored track, how late its session appears, and whether a link navigates, loads the content paused, or plays. It is installed on an emulator only, never beside the real app, and it stands in for an app's *declared* behaviour; what a real app does is still only learned from that app (section 5.6).
- **A simulated watch** (`mobile/src/debug/.../SimulatedWatchCommandReceiver`, debug builds only, behind the shell-only `DUMP` permission). An emulator's phone image has no Wear API (`ApiException 17`), so nothing could ever pick a shortcut there. `adb shell am broadcast` hands the receiver a path and a payload and it enters at `WatchCommandDelivery.receive`, the call the manifest listener makes, so everything after it is the real code. The verdict cannot reach a watch; it is logged (`Shortcut verdict for request …`), and the test then opens that link with `am start`, as the watch's `RemoteActivityHelper` would.

`rea-apps/mock-player/rig/run.sh <serial> <package> <link> <seconds between verdict and open> <seconds to watch> key value …` does one whole run and prints the ladder's log next to the stand-in's.

| Stand-in behaves like | Before | After |
|---|---|---|
| Cold app; the link only navigates; the session takes the request without advertising it | The request sent 8 times in one second, then `Deep link navigated without playing; pressing play`: **the restored track started** | 3 requests 1.5 s apart, no press, left paused on its restored track, `played=false` |
| The same, but the app honours the request and its session comes up 5 s after the pick | Not run. By the code: nothing sent after 3.2 s, then the blind press | Asked once the session had settled (6.8 s in): **the requested content played** |
| A link that plays by itself | Not run | Nothing requested, `played=true` |
| Cold app that shows its restored track, then loads the content paused | Not run | Play pressed 1.5 s after the new content replaced the restored track: played |
| Warm app, paused on its old track; the link loads the content paused | Not run | Play pressed on the new content: played |
| Warm app, paused; the link only navigates | Not run | Left paused |
| Spotify stand-in that ignores every outside request (what Spotify Free did in 5.6), picked three times | Not run | Hand-off with `uri_search`, then with `view_autoplay`, then `every play technique failed lately … plain open`; three session requests each time, play never pressed, restored track left paused |
| Spotify stand-in whose session honours the link although opening it only navigates | Not run | Played after one request |
| Spotify stand-in that honours the play-from-search launch | Not run | Played, nothing requested |
| Accepts the browser, buffers on the request, gives up | `played via MediaBrowserService (step 2)`, verdict "handled": **nothing played and nothing was opened** | Step 2 `did not play`, the link is opened, the follow-up runs |
| First command into a cold process while the notification listener reconnects (right after a reinstall) | `Service stopped` a tenth of a second into the ladder, no verdict, nothing played, **3 of 3** (the stand-in would have played) | Played, 3 of 3; and once more after `kill -9` of the phone app with the command sent at once |

Read with these limits. The stand-in proves what Svartifoss does with a given behaviour, not that any real app behaves that way. The last row's "before" needed the reinstall to line the two starts up; on a real phone the same line-up comes from the process being started by a watch message while the system still owes the listener a rebind, which was reproduced only through `kill -9`, and only on the fixed build. And one thing the emulator did settle about Android 14: with the notification listener bound, the phone app's start of `MusicService` from the background is logged `Background started FGS: Allowed [… uidState: BFGS …]`, so on stock Android a refused start needs the listener unbound (the default once auto-start and session-driven quick actions are off) or a vendor's restriction.

The stand-in's tracks carry a cover on purpose: a track without one makes the phone look a cover up online, and the first few runs spent YouTube Data API quota that way before the stand-in was given one.

### 5.8 The watch's own apps (2026-11-11)

If Spotify on the phone plays nothing it is asked for, could the Spotify app *on the watch* be asked instead, since it can start a playlist on the phone itself? Tried on a TicWatch Pro 5 (Wear OS 4), watch muted, with Spotify 9.1.98.620 and YouTube Music 9.44.41 as installed there.

| What was tried | Result |
|---|---|
| `VIEW` of `spotify:playlist:…`, `spotify:album:…`, `spotify:track:…`, `spotify:artist:…`, `spotify:collection:tracks` and `spotify:search:…` to the watch's Spotify, cold and running | The app opens, or stays, on its player ("Select something to play"). It does not go to the content, and nothing plays |
| A connection to its `WearMediaBrowserService` from a throwaway probe app, under the probe's own name | **Accepted** (root `wear`), but loading the root's children fails, and the session it hands out (`PLAY`, `PLAY_PAUSE`, `PLAY_FROM_URI` advertised) did nothing with `playFromUri` for a playlist, `playFromSearch` or `playFromMediaId` in 10 to 16 s |
| `VIEW` of `https://music.youtube.com/playlist?list=LM` to the watch's YouTube Music | Its account picker: the app is not signed in on that watch. Not taken further |

So the watch's own apps are not a way to start something on the phone. The probe was removed afterwards and both apps were stopped.

### 5.9 Listing a playlist's tracks, and YouTube Music's browser (2026-11-14)

The question: can the watch's shortcut screen list a saved playlist's songs and play the one picked? Pixel 3, Android 10, YouTube Music 8.30.54 from the Play Store (installed 2026-10-25, updated the morning of the test), the phone app's play debug build 93, commands sent through the simulated watch of 5.7 on the real phone. Positions count from 1.

| What was tried | Result |
|---|---|
| YouTube Data API `playlistItems.list` (1 quota unit per call, 50 items a page) on the owner's own playlist, someone else's, an `OLAK5uy_` album and a short `PL…` id | All four listed: 74 items (two pages), 46, 11 and 12. Each item has its title, video id, privacy and thumbnails, and the channel: `<artist> - Topic` for YouTube Music's own tracks, the uploader otherwise (so a fan's upload reads as its raw video title and the uploader's channel name). One unlisted video was listed like the rest |
| The same call for `LM` (Liked Music) | HTTP 404 `playlistNotFound`: an API key cannot read it |
| A pick of `watch?v=<5th item>&list=<playlist>`, YouTube Music playing something else | Step 1 `playFromUri`, verified, about 2 s; verdict "handled". It started on the 5th item, and its queue followed the playlist in order (the queue entries seen were items 5 to 25; the session queue held 25 entries) |
| The same with YouTube Music force-stopped, for the 10th item | Step 2 **refused** (`onConnectFailed` for `MusicBrowserService`). Step 3's follow-up played the 10th item through the session the bind had woken, about 8 s in, with the launcher still in front. But the verdict carried the link, so a real watch would also have shown "Continue on your phone" and opened YouTube Music on the phone |
| Browse library, at the root | `onConnectFailed` again, so the library cannot be listed, nor Liked Music or private playlists through it |

What it changes:

- **This YouTube Music refuses Svartifoss's browser**, which contradicts what section 2 records. A cold YouTube Music shortcut now plays only through the visible rung's follow-up, and the watch is told to open the phone anyway. Whether newer versions accept again is not known; re-test when the app updates.
- **A track inside a playlist is one link.** `watch?v=X&list=P` starts at X and continues with the playlist, so a track list needs no queue manipulation, only the ids the Data API returns.
- **For YouTube Music the Data API is the only source of a track list.** Public and unlisted playlists and albums work; Liked Music and private playlists would need the user's Google sign-in.

Five quota units were spent.

Built on these results the same day: the shortcut screen's song list (`ShortcutTracksResponder` on the phone, `ShortcutTracks` on the watch), and the rule that asks a woken session before the watch is told to open the phone (`VisibleOpenFollowUp.triesWokenSession`). On the Pixel 3 and the TicWatch Pro 5, opening the owner's 74-song playlist on the watch listed all of it (two pages, two quota units) under the screen's buttons, titles with the artist's name and no "- Topic". A pick from that list was not repeated on the devices (the phone was in use); it sends the very link the warm and cold rows above played.

---

## 6. What to build next

Ranked by value to someone using the watch, all green or yellow under section 1.

### 6.1 Associate the watch as a companion device

The largest reliability gain available, and documented. An app with a `CompanionDeviceManager` association that declares `REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND` (a normal permission) may start foreground services from the background on Android 12+, per the Android 12 foreground-service page. That is exactly the refusal behind "the watch does nothing until I unlock my phone" (CLAUDE.md, *Watch command dispatch*): every command that still needs `MusicService` would just work. In AOSP `main`, the same association also exempts the app from background activity-start limits (`BackgroundActivityStartController`, the "Companion App" verdict), which would let the phone start the hand-off launch itself with no watch round trip; that one is not in the public guide, so treat it as a bonus to verify, not a contract. `REQUEST_COMPANION_RUN_IN_BACKGROUND` additionally puts the app on the power allowlist while the watch is present (AOSP `CompanionExemptionProcessor`), the cure for OEM battery killers.

What 5.7 adds: on Android 14 the start is allowed whenever the notification listener is bound, so the association matters for installs where it is not (auto-start off, quick panel on manual), and for vendors that restrict background starts regardless. A shell can create an association for testing (`adb shell cmd companiondevice associate <user> <package> <MAC>`), which makes the exemption checkable on the same emulator before any pairing screen is built.

Sketch: an optional "Pair the watch for reliable control" row; `AssociationRequest` with a `BluetoothDeviceFilter` matching the paired watch (name from the Wearable `Node`, or the bonded device's address), single device, **no device profile** (the watch profile grants a role with far more than this needs); keep the association id; degrade to today's behaviour without it. Self-managed associations are not an option (`REQUEST_COMPANION_SELF_MANAGED` is `signature|privileged`). Needs an Android 12+ phone to test; the Pixel 3 rig is Android 10.

### 6.2 Ask before bypassing: get allowlisted

After section 5.6 this is the **only** clean route to Spotify (and Amazon Music) actually playing a shortcut: both refuse Svartifoss's connection to their media library by name and ignore every other outside play request. Ask them to accept Svartifoss's package and signing certificate the way they accept Android Auto and their other partners; write to their partner/developer contacts with both. Zero legal risk, and a yes is worth more than every technique in section 4.

### 6.3 Walk, then play, for SoundCloud's Likes

SoundCloud's library accepts Svartifoss (section 5.6), so its Likes can be found in the app's own tree and played by the media id SoundCloud hands out, the rule `player-integration-notes.md` sets. That is exact and silent, where the Play Likes action only opens the page. The same holds for a saved set: walking to it beats the name search the short links use now.

### 6.4 Move to Media3 controllers

Svartifoss speaks the legacy compat API. A Media3 `MediaBrowser`/`MediaController` sees a session's actual `availableSessionCommands` instead of the advertised bitmask (`MediaSessionCapabilities` exists because that bitmask lies), and can use `setMediaItems` with `RequestMetadata.mediaUri`, the Media3 form of play-from-URI. Medium effort, and it is where every current app is going.

### 6.5 Show the connection check to everyone

The developer report from section 4 could become a user-facing "Check my music apps" screen: for each installed app, whether the watch can play shortcuts silently, only by opening the app, or not at all, with the fix (grant notification access, open the app once, etc.). Bug reports would arrive with the answer attached.

### 6.6 Phone search through the hand-off

Watch search for an app with no searchable library still ends in a voice-search intent that only works with Svartifoss on screen. The same token hand-off could carry it: the phone returns a hand-off link, the watch opens it. Needs a verdict message for search, which does not exist yet.

### 6.7 Re-check the closed Spotify questions now and then

`spotify-integration-assessment.md` closed the Web API and App Remote SDK routes because of Spotify's quota rules (development mode capped, extended quota for organisations only since 15 May 2025). Nothing here reopens them; re-read Spotify's terms yearly.

### 6.8 Trademark hygiene

The watch draws bundled service marks (`wear/src/main/res/drawable/service_mark_*`, from `icons/icons_4/`, including Spotify's logo) on the shortcut screen's **Open on phone** pill when no glyph has been learned yet. It is arguably descriptive, but it is the one place the app ships another company's logo. The glyph the phone already learns from each app's own notification (`AppGlyphStore`) is drawn by that app on the user's own phone and is the safer source; a neutral glyph is the safest fallback. A product decision, not urgent.

### 6.9 Wear OS 7's own "remote media" hook

Documented, and the one thing the newer Wear apps point at that Svartifoss does not have. The Wear SDK's `com.google.wear.services.media.MediaManager` (added in API level 37) defines `ACTION_REMOTE_MEDIA_ACTIVITY` = `com.google.wear.services.media.action.REMOTE_MEDIA_ACTIVITY`: "An intent action declared by media applications that can handle Remote media sessions. The activity handling this intent would be associated with the media session fetched from the phone to the watch." With it come `getMediaAutoLaunchState()` ("whether their application will be auto-launched when a media session starts on the connected phone. When enabled by the user, the activity associated with the remote session will be launched") and `showMediaAutoLaunchSettings()`. Handling the phone's media on the watch is what Svartifoss is, and its own auto-start has to ask the watch to open an activity from the background, which Wear OS may refuse; the platform's version would not have that problem.

What the page does not say is how a session is matched to an app: by package (Spotify on the phone to Spotify on the watch, which would leave Svartifoss out) or by the user's choice in those settings. Today's Spotify build declares the older, undocumented `com.google.android.wearable.action.MEDIA_CONTROLS` on its launcher activity instead, as Google's own Media Controls does. Declaring either filter on Svartifoss's player costs one manifest entry, and neither should ship unseen: it needs a Wear OS 7 watch or emulator image to see what the system then does.

### 6.10 The system's bridge as a second channel

Google's Media Controls for Wear declares `AndroidSessionAdvertisingService`, a Media3 `MediaSessionService`, exported behind `com.google.android.wearable.media.sessions.permission.BIND_THIRD_PARTY_MEDIA_CONTROLS`, a permission it defines with the normal protection level, a label and a description: the shape of something meant for other apps to hold. By its name and that app's job, the session is the phone's media as Wear OS itself bridges it. If it accepts a third-party controller, Svartifoss's watch app could keep play, pause, skip and the track's name through the system's channel whenever its own phone service cannot be reached or started, which is the whole class of "the watch does nothing until I unlock my phone".

Yellow under section 1: a declared entry point with a standard protocol and undocumented behaviour. To check on a Wear OS 5+ watch: `adb shell dumpsys package com.google.android.wearable.media.sessions | grep -B3 -A6 AndroidSessionAdvertising`, then a controller built from `SessionToken(context, ComponentName(...))` by an app that declares the permission, under its own name, and see whether the connection is accepted and what the session publishes while the phone plays. Not checked: the test watch runs Wear OS 4 and its wireless debugging dropped during this pass.

### 6.11 Deezer's autoplay parameter

A 2016 Tasker forum thread reports that `?autoplay=true` on a `deezer://www.deezer.com/…` link starts playback. Deezer's developer guidelines document no deep links at all (read 2026-11-11), so this is yellow and unverified: it belongs on the visible open's link for Deezer only after one test on a phone with Deezer installed, where the follow-up in section 4 already tells whether it played.

---

## Sources

- Android: [MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH](https://developer.android.com/reference/android/provider/MediaStore#INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH), [background activity starts](https://developer.android.com/guide/components/activities/background-starts), [Android 12 foreground-service restrictions](https://developer.android.com/about/versions/12/foreground-services), [RemoteActivityHelper](https://developer.android.com/reference/androidx/wear/remote/interactions/RemoteActivityHelper).
- AOSP `main`: `services/core/java/com/android/server/wm/BackgroundActivityStartController.java`, `services/companion/java/com/android/server/companion/CompanionExemptionProcessor.java`, `core/res/AndroidManifest.xml` (permission protection levels).
- Spotify: [content linking guide](https://developer.spotify.com/documentation/android/tutorials/content-linking), [User Guidelines](https://www.spotify.com/br-pt/legal/user-guidelines/), Spotify Community threads ["Tasker cannot start a Playlist anymore"](https://community.spotify.com/t5/Android/Tasker-cannot-start-a-Playlist-anymore-Send-intent-does-not-wrok/td-p/4633926) and ["Start shuffled Playlist with Android-Intent"](https://community.spotify.com/t5/Android/Start-shuffled-Playlist-with-Android-Intent/td-p/1210490).
- Wear OS: [MediaManager](https://developer.android.com/reference/com/google/wear/services/media/MediaManager) (Wear SDK, API level 37). Android: [background start restrictions for foreground services](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start), whose exemption list has the Companion Device Manager and no entry for the Wearable Data Layer.
- Brazilian law: Lei 9.609/98 (software), Lei 9.610/98 (copyright), Lei 9.279/96 (industrial property).
- Manifests: the APKs in `rea-apps/` (gitignored), decoded with `aapt2 dump xmltree`, no code opened.
