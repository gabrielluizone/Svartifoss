# Streaming app interoperability: ground rules and playbook

How Svartifoss talks to Spotify, SoundCloud, YouTube Music and the other streaming apps on the phone, what it may and may not do to learn how they behave, and what to build next. Written 2026-10-24, after the report that picking a saved Spotify or SoundCloud playlist on the watch opened the app without playing it.

**Status.** The hand-off described in section 4 is implemented, unit-tested, and was tested the same day on a Pixel 3 (Android 10) with Spotify 9.1.92 on a Free account, SoundCloud 2026.10.14 and Amazon Music 26.36.1. The results are in section 5.6 and changed the conclusions: SoundCloud's short share links now play silently, while this Spotify refuses every outside request to play something specific.

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

`com.google.android.apps.youtube.music.mediabrowser.MusicBrowserService` (legacy browser + `MEDIA_BUTTON`). On the phone, YouTube Music already plays shortcuts through its media browser, including with the screen off, so nothing here changes for it.

### Amazon Music (Fire TV and Wear builds)

`VIEW` on `amznmp3://` and on the `music.amazon.*` hosts, and a legacy `MediaBrowserService`. `StreamingShortcutLinks` recognises the scheme `amzn-music:`, which neither of these builds declares.

### Confirmed on a phone (Pixel 3, Android 10, 2026-10-24, `dumpsys package`)

- **Spotify 9.1.92.3058:** both `MEDIA_PLAY_FROM_SEARCH` filters (one with `BROWSABLE` and the `spotify` scheme), `SpotifyMediaBrowserService` and the Media3 `MediaLibraryService`, as in the APK above. Its live session advertised `PLAY_FROM_URI`, `PLAY_FROM_SEARCH` and `PREPARE_FROM_URI`, but not `SEEK_TO` or `SKIP_TO_PREVIOUS`, which is what a Spotify Free account looks like.
- **SoundCloud 2026.10.14 (phone):** `SHORTCUT_PLAY_LIKES`, `MEDIA_PLAY_FROM_SEARCH` and a legacy `MediaBrowserService`, so the TV build was a fair stand-in.
- **Amazon Music 26.36.1 (phone):** `MEDIA_PLAY_FROM_SEARCH`, a legacy browser and a Media3 session service, and the schemes `amzn` and `amznmp3`, **not** `amzn-music`. A saved `amzn-music:` link is classified as Amazon by Svartifoss but opens nothing on this phone; share links (`https://music.amazon.*`) are unaffected. Worth fixing in `StreamingShortcutLinks.detect`, recognising `amznmp3:` (and not the generic `amzn:`, which other Amazon apps may claim).

---

## 3. Why the shortcut opened without playing

`MusicService.playDeepLink` tries three things in order (see CLAUDE.md, *Actions system*):

1. **The media session.** If the app already has a session, `playFromUri` on it, then check that something new is playing. Spotify not running means no session, so this is skipped.
2. **The media browser.** Bind the app's `MediaBrowserService` and play from there: the Android Auto path, the only one that works with the screen off. Spotify refuses unknown clients, so this fails at the connection.
3. **The visible open.** The watch opens the link on the phone (`RemoteActivityHelper`, the only launch a watch can make on a phone in the background), then the phone keeps asking the new session to play. But a `VIEW` of a Spotify playlist navigates and does not play, the retries either find no active session or are ignored, and the final "press play" is deliberately withheld when the app was holding some other track (it would resume the wrong music).

So the user saw Spotify open on the right playlist with nothing playing, and SoundCloud the same. The missing piece was never a hidden API: it was that the only launch the watch can make (a `VIEW`) is the one launch that does not mean *play*, and the launch that does (`MEDIA_PLAY_FROM_SEARCH`, or an app's own "play" action) has to be started by something in the foreground on the phone.

A second, separate bug was found on the way. When the watch searches for a song in an app whose library cannot be searched (Spotify again), the phone fired the voice-search intent from its background service. Since Android 10 a background activity start is dropped without an error, so the call "succeeded", the two silent routes after it never ran, and nothing played with the phone in a pocket. The intent is now used only while one of Svartifoss's screens is showing; otherwise the session's own `playFromSearch` (which Spotify honours) runs.

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
- `MusicService`: `prepareVisiblePlayback`, `playThroughHandoff`, `awaitHandoffPlayback`. When Svartifoss is itself on screen it also starts the activity directly (a shortcut started from the phone's own UI has no watch to open it); the token makes the second open a no-op. Success means the app's session reached `STATE_PLAYING` (not `CONNECTING` or `BUFFERING`, which a cold app publishes whatever it was asked; see 5.6). A loaded-but-paused new item is pressed play after 4 s, but never after a cold start, where the app comes up holding whatever it played last.

Also in this change:

- **SoundCloud short links play by name.** A share-sheet link (`on.soundcloud.com/…`) classifies as `UNKNOWN` until opened, which used to skip every silent route. `playDeepLink` now treats it like an artist (`preferSearch`): the saved name, cleaned by `StreamingShortcutLinks.searchableName` (which drops the " - SoundCloud" share sheets append), goes to the session's and the library's `playFromSearch`, and to the name search in the hand-off. Section 5.6: this is what played silently.
- **Every browser service is tried.** `MediaBrowserSearch.findBrowserServices` ranks all of an app's browser services (Spotify has two) and `connectFirst` uses the first that accepts, for playback, search and library browsing alike. If Spotify's Media3 service accepts a generic client where the legacy one refuses, Spotify shortcuts would start silently with the screen off, the best outcome of all; nobody knows yet, and trying costs one fast refusal.
- **The search bug in section 3** is fixed in `MusicService.playFromSearch` (`appIsVisible`).
- **The developer report** (Settings → Data & support → Developer → MediaSession explorer) now lists, per installed streaming app, its browser services, which play techniques it declares, and what each has done on this phone.

The watch needed no change: its `PhoneUriOpener` opens whatever `package|uri` the verdict names, and an older watch build behaves the same.

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

---

## 6. What to build next

Ranked by value to someone using the watch, all green or yellow under section 1.

### 6.1 Associate the watch as a companion device

The largest reliability gain available, and documented. An app with a `CompanionDeviceManager` association that declares `REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND` (a normal permission) may start foreground services from the background on Android 12+, per the Android 12 foreground-service page. That is exactly the refusal behind "the watch does nothing until I unlock my phone" (CLAUDE.md, *Watch command dispatch*): every command that still needs `MusicService` would just work. In AOSP `main`, the same association also exempts the app from background activity-start limits (`BackgroundActivityStartController`, the "Companion App" verdict), which would let the phone start the hand-off launch itself with no watch round trip; that one is not in the public guide, so treat it as a bonus to verify, not a contract. `REQUEST_COMPANION_RUN_IN_BACKGROUND` additionally puts the app on the power allowlist while the watch is present (AOSP `CompanionExemptionProcessor`), the cure for OEM battery killers.

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

---

## Sources

- Android: [MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH](https://developer.android.com/reference/android/provider/MediaStore#INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH), [background activity starts](https://developer.android.com/guide/components/activities/background-starts), [Android 12 foreground-service restrictions](https://developer.android.com/about/versions/12/foreground-services), [RemoteActivityHelper](https://developer.android.com/reference/androidx/wear/remote/interactions/RemoteActivityHelper).
- AOSP `main`: `services/core/java/com/android/server/wm/BackgroundActivityStartController.java`, `services/companion/java/com/android/server/companion/CompanionExemptionProcessor.java`, `core/res/AndroidManifest.xml` (permission protection levels).
- Spotify: [content linking guide](https://developer.spotify.com/documentation/android/tutorials/content-linking), [User Guidelines](https://www.spotify.com/br-pt/legal/user-guidelines/), Spotify Community threads ["Tasker cannot start a Playlist anymore"](https://community.spotify.com/t5/Android/Tasker-cannot-start-a-Playlist-anymore-Send-intent-does-not-wrok/td-p/4633926) and ["Start shuffled Playlist with Android-Intent"](https://community.spotify.com/t5/Android/Start-shuffled-Playlist-with-Android-Intent/td-p/1210490).
- Brazilian law: Lei 9.609/98 (software), Lei 9.610/98 (copyright), Lei 9.279/96 (industrial property).
- Manifests: the APKs in `rea-apps/` (gitignored), decoded with `aapt2 dump xmltree`, no code opened.
