# Player integration notes

What specific music apps actually implement, read from their source. The point is to replace
trial-and-error in `playDeepLink` / `MediaBrowserPlayback` / `MediaBrowserLibrary` with informed
expectations about which commands a given player honours.

Sources: Retro Music 3.4.850 (local copy under the gitignored `retromusic/`), Echo
(`brahmkshatriya/echo`, `app/src/main/java/dev/brahmkshatriya/echo/playback/AndroidAutoCallback.kt`),
InnerTune (`z-huang/InnerTune`, branch `dev`,
`app/src/main/java/com/zionhuang/music/playback/MediaLibrarySessionCallback.kt`). Read 2026-08-07;
all three are moving targets, so re-check before relying on a detail.

## The single most important finding

**A media id is only meaningful to the app that produced it.** Every app inspects the id's shape and
resolves it against its own tree:

- Retro Music's browser session does `Integer.parseInt(mediaId)` — the id *is* a numeric song row id.
- Echo only accepts ids prefixed `auto/` (`AndroidAutoCallback.onSetMediaItems`).
- InnerTune splits on `/` and switches on the first segment (`song`, `artist`, `album`, …).

So there is no such thing as constructing a media id for an app. The only ids worth sending are ones
that app just handed us through its own browse or search results. This is why library browsing is
built as walk-then-play rather than as a lookup, and why passing a *browsable* node id to a play
command is not just useless but can crash the target (Retro Music's `parseInt` on a folder id).

## Per app

### Retro Music

Two sessions, and they have different capabilities — the multi-session problem the queue code
already documents, showing up again for playback.

| | Playback session (`MediaSessionCallback.kt`) | `WearBrowserService` session |
|---|---|---|
| Declared actions | PLAY, PAUSE, PLAY_PAUSE, SKIP_NEXT, SKIP_PREV, STOP, SEEK_TO | — |
| `onPlayFromMediaId` | **not implemented** | implemented (numeric song id) |
| `onPlayFromSearch` / `onPlayFromUri` | **not implemented** | not implemented |

Consequence: playing a browsed item on Retro Music only works when the command goes to the *browser
service's* session, never to the tracked playback one. `MediaBrowserPlayback` already does the right
thing here — it builds its controller from `browser.sessionToken`. Issuing `playFromMediaId` on the
tracked controller (what search-result selection used to do) could never have worked on this app.

Its declared action set is also worth remembering when adding transport features: no
`ACTION_SET_PLAYBACK_SPEED`, no `ACTION_PLAY_FROM_*`. Anything beyond the seven listed above must
degrade silently.

### Echo

Media3 `MediaLibraryService`. Implements `onGetLibraryRoot`, `onGetChildren`, `onGetSearchResult`,
`onSetMediaItems`.

**It does not implement `onSearch`.** In Media3 those are two different callbacks: `onSearch` is the
request, `onGetSearchResult` returns the rows, and the base class's `onSearch` answers
`ERROR_NOT_SUPPORTED`. A legacy `MediaBrowserCompat.search()` — which is what `MediaBrowserSearch`
issues — has to go through `onSearch` first. So search against Echo is expected to come back empty
even though the app clearly can search.

That is a strong candidate explanation for "search on the watch finds nothing / plays nothing" on
this family of apps, and it is *not* something a different command on our side fixes: browsing is
the working path there.

### InnerTune

Media3 `MediaLibraryService`. Implements `onConnect` (building an explicit
`availableSessionCommands` set), `onCustomCommand`, `onGetLibraryRoot`, `onGetChildren`, `onGetItem`,
`onSetMediaItems`.

Implements **neither** `onSearch` nor `onGetSearchResult` — no library search at all over this
contract. Browsing works; searching does not.

`onSetMediaItems` resolves `song/<id>`, `artist/<artistId>/<songId>`, … from its own database, and
notably expands the selection into a real queue (all songs, positioned at the picked one) rather
than playing a single track. Good behaviour to expect from Media3 clients generally.

## What this means for Svartifoss

1. **Browsing is the reliable path; search is not.** Two of the three apps examined cannot answer a
   library search over the MediaBrowser contract at all. Presenting browse as the primary way to
   start something (rather than as a companion to search) matches what players actually support.
2. **Never send a constructed media id.** Only ids that came back from that same app's browse or
   search results.
3. **Never send a browsable id to a play command.** Beyond doing nothing, it can throw inside the
   target app.
4. **Route play commands through the browser connection, not the tracked controller.** Retro Music
   is the proof case: the capability lives on a different session entirely.
5. **Read `PlaybackState.actions` before offering a transport feature.** Retro Music advertises
   seven actions and nothing else; a playback-speed or play-from-search feature has to check and
   degrade rather than assume. `MediaSessionCapabilities` does this, and encodes the one subtlety
   that matters: a session with *no published state* has not refused anything, so unknown must read
   as "try anyway" - otherwise every wake-then-play path dies against an app that was just bound.
   Applied so far to the play-from-search fallback; the natural next users are playback speed and
   anything else that would silently do nothing on a player that never implemented it.

### Symfonium

Symfonium exposes a public broadcast API in addition to its MediaSession. The receiver is
`app.symfonium.remote.api.ApiReceiver`; `app.symfonium.api.MEDIA_START` accepts a song name and
artist, and `QUEUE=1` inserts it after the current item while `QUEUE=2` appends it to the end of
the queue. The API is documented by Symfonium at
<https://docs.symfonium.app/wiki/other/symfonium-api-allow-control-from-other-apps-like-tasker/>.

This is the reliable route for the watch's "add search result to queue" action. The standard
MediaSession queue is optional and can be hidden by a Symfonium setting, so a browser `mediaId`
must not be treated as a portable playlist or filesystem identifier. The app therefore keeps the
normal tap as immediate playback and uses a Symfonium-specific adapter for the long-press queue
action.

The same adapter now exposes two configured actions in the phone action picker when Symfonium is
installed: force a provider sync (the provider id comes from Symfonium's provider settings) and
start a song, playlist, album, artist, genre, mix, or internet radio. The latter can choose normal
playback, play-next, append-to-queue, resume and shuffle. These actions stay phone-side because
the public API is a best-effort broadcast with no result callback; the watch only carries the
serialized action configuration.

`MusicState.sourceAppName` is separate from `MusicState.artist`. The watch uses it only for the
artist line when a player publishes no artist, so a player such as Symfonium or YouTube Music does
not leave an empty metadata slot while lyrics, history and metadata lookups still receive the raw
artist value.

### SoundCloud

Closed source, so this is read from the shipped manifest and dex rather than from code: the Wear OS
build `2026.01.14-wear-beta` (versionCode 338064, package `com.soundcloud.android`, a `wear_standalone`
APK from APKMirror). Read 2026-08-18.

`com.soundcloud.android.wear.app.service.WearPlaybackService` is `exported="true"` and declares two
intent filters — `androidx.media3.session.MediaLibraryService` and
`androidx.media3.session.MediaSessionService` — **and no legacy
`android.media.browse.MediaBrowserService` filter at all.** The dex references `onGetLibraryRoot`,
`onGetChildren`, `onGetItem` and `onSearch`, so the library callback is genuinely implemented, not a
stub, and there are `Likes` / `stream` / `history` strings consistent with a real browse tree.

That missing legacy filter is the whole finding, and it cost four features at once. Service
*discovery* goes through `queryIntentServices`, which matches on intent filters, so querying only
the legacy action returned nothing for SoundCloud and `findBrowserService` reported "no browsable
library" — taking search, library browsing and both `MediaBrowserPlayback` routes down with it, in
an app that implements all of them. `MediaBrowserSearch.BROWSER_SERVICE_ACTIONS` now queries the two
Media3 actions as well, legacy first so nothing that already resolved changes.

Binding deliberately still goes through `MediaBrowserCompat`: it binds the *explicit* component with
the legacy action, explicit binds ignore intent filters, and Media3's `MediaSessionService.onBind`
answers that action with its legacy browser binder. So the old client talks to the modern service
unchanged — no Media3 dependency is needed on our side.

Two cautions. This was read from the **watch** APK while `MusicService` talks to the **phone** app;
the phone build is very likely the same Media3 stack but that is inference, and
`adb shell dumpsys package com.soundcloud.android | grep -iE "media3|MediaBrowserService"` settles it
on a real device. And the generalisation matters more than SoundCloud: Media3 is the current
standard and a Media3 app is under no obligation to advertise the legacy action, so any app of this
generation was invisible to all four features for the same reason.

Deep links seen in the same dex (watch app): `soundcloud://soundcloud//player`, `//collections`,
`//collection?id={id}&name={name}`, `//mediaItem?id={id}&collectionId={collectionId}`, `//settings`,
`//volume`. `users/me/likes/tracks` also appears, but that is their OAuth HTTP API, not something a
remote can call. None of these are a substitute for a browsed id — the rule at the top of this file
still holds, so "play my likes" should come from walking the library, not from a constructed id.

### Spotify

Closed source, and its terms forbid decompiling it, so this is only what its phone manifest
**declares** (9.1.94.1912, read 2026-10-24 with `aapt2 dump xmltree`; `adb shell dumpsys package
com.spotify.music` prints the same from an installed copy). The full write-up, with the legal
reasoning, is `streaming-app-interop.md`.

- Two browser services: `SpotifyMediaBrowserService` (legacy) and `SpotifyMediaLibraryService`
  (legacy **and** Media3). Svartifoss now tries both (`MediaBrowserSearch.findBrowserServices`);
  whether the Media3 one accepts a client the legacy one refuses is not yet known.
- `SpotifyMainActivity` accepts `android.media.action.MEDIA_PLAY_FROM_SEARCH` twice: plain, and with
  the `spotify:` scheme as data plus `BROWSABLE`. That is the public "play this URI" entry point the
  visible rung of `playDeepLink` now uses through the hand-off activity.
- `VIEW` of `spotify:` URIs is documented by Spotify and only navigates; its users report that a
  `:play` suffix (`spotify:playlist:<id>:play`) starts playback, which the hand-off keeps as a second
  technique.

Behaviour observed on a phone (9.1.92.3058, **Free** account, Android 10, 2026-10-24; full table in
`streaming-app-interop.md` §5.6): its browser logs `No root for client com.svartifoss.snfell`, its
session ignores `playFromUri` and `playFromSearch` from Svartifoss, and neither play intent starts
anything, cold or warm: every one of them only opens the right page. Premium untested.

Same day, two more apps the shortcuts reach: **SoundCloud 2026.10.14** (phone) *accepts* Svartifoss's
browser connection and plays a `playFromSearch` through it silently, while its exported
`SHORTCUT_PLAY_LIKES` only opens the Likes page when another app sends it; **Amazon Music 26.36.1**
refuses the browser (`No root for client`) from `com.amazon.mp3.auto.AutoMusicService`, and its
link's `do=play` parameter starts nothing.

## Not investigated

Apple Music and Amazon Music are closed source, so none of the above can be established for them by
reading code. Whether they expose any public control API on Android is unverified — do not assume
either way without checking their declarations (`dumpsys package`). Spotify publishes an App Remote
SDK that would answer the "artist page does not play" case properly, at the cost of a registered
client id, a Premium requirement for playback control, and a proprietary dependency in an otherwise
GPLv3 codebase (see `spotify-integration-assessment.md`).
