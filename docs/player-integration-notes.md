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

## Not investigated

Spotify, Apple Music and Amazon Music are closed source, so none of the above can be established for
them by reading. Spotify publishes an App Remote SDK that would answer the "artist page does not
play" case properly, at the cost of a registered client id, a Premium requirement for playback
control, and a proprietary dependency that conflicts with the self-hosted F-Droid repo. Whether
Apple Music or Amazon Music expose any public control API on Android is unverified — do not assume
either way without checking.
