# Changelog

## 2.0

The app is renamed **Svartifoss** (formerly Music Center for Wear / Lyra
Player). Alongside the rebrand, this is the largest feature release since
the 1.12 Wear OS modernization: a Compose-based watch UI overhaul, several
new assignable actions, glanceable watch-face/Tile surfaces, and playlist
shortcuts.

### Rebrand

- App renamed to **Svartifoss**; package IDs renamed
  `com.matejdro.wearmusiccenter` → `com.svartifoss.snfell` (mobile + wear)
  and `com.matejdro.common` → `com.svartifoss.snfell.common`.
- Version bumped to **2.0** (versionCode: mobile 28 → 29, wear 134 → 135).
- Wear `minSdk` raised 25 → 26 (required by the Tiles APIs used below).

### New actions (assignable to any button/gesture)

- **Play/Pause toggle**, **Stop**, **Restart track**, **Mute toggle** — four
  new one-tap actions for single-button watches.
- **Repeat-one** — direct one-tap on/off toggle for track looping, separate
  from the existing cycling `RepeatAction`.
- **Search** — opens voice/keyboard input on the watch and resolves the
  query against the playing app's `MediaBrowserService`; past searches are
  kept in a **search history** list that can be replayed or deleted from the
  watch.
- **Playlist shortcuts** — name + deep-link shortcuts managed on the phone
  (with an optional shuffle flag), reachable as a watch list or assigned
  directly to a button/gesture.
- **Play liked songs** / **Play liked songs, shuffled** — one-tap links into
  YouTube Music's "Liked Music".
- Recently-played history is now persisted to disk instead of living only in
  `MusicService` memory, so it survives service restarts.

### Wear glanceable surfaces

- **Media Tile**: ProtoLayout tile showing track/artist with
  prev/play-pause/next, tap to open the app.
- **Queue-preview Tile**: shows the next queued track, tap to skip
  (optional, toggled from the phone).
- **Watch-face complication**: current album art / title-artist, tap opens
  the app; supports short text, long text, small image and photo image
  complication types.
- **Rotary-crown seek**: optional setting where turning the crown scrubs the
  timeline instead of changing volume (debounced before hitting the Data
  Layer).

### Wear UI overhaul

- Full-screen Compose **menu** (actions menu and phone-pushed custom lists,
  including in-place delete of search-history entries) replacing the legacy
  `WearableDrawerLayout` drawer.
- Configurable **mini-buttons row** (up to 3 slots) and a **quick-actions
  panel** (3 round buttons + 1 long row), both assignable through the
  existing button/action pipeline, with per-item styling (curve, glass /
  solid / transparent background, neutral / album / custom color, offset).
- **Swipe gestures generalized**: up/down/left can each be assigned any
  action (right stays reserved for the system dismiss gesture).
- **Long-press the center screen** to open the queue directly; a new
  first-run overlay hints at the available gestures.
- New **screen theme** options (default/minimal/compact/cinema), a
  dynamic-vs-static accent toggle, album-art fade transition, album-art
  style (cover/blur/black-and-white/blur+bw/hidden) with blur radius and dim
  strength, ambient opacity, rotary dead-zone, and volume/seek overlay
  timeout — all new settings synced from phone to watch.
- Shared `WatchTheme`/chrome (curved clock, curved scroll indicator, loading
  spinner) reused across the queue and new menu screens; the phone
  connection is now kept alive for the duration of any full-screen watch
  activity (menu, queue) instead of just the main screen.
- Portuguese (Brazil) localization added for the wear and common modules.

### Mobile UI

- New **Guide** tab with a Wear OS usage walkthrough.
- New left navigation drawer with app/author info; toolbar title alignment
  fixed.
- Built-in icon picker grid for custom action icons; color-swatch preference
  rows for accent and custom colors, backed by a single source of truth for
  the live accent color ("Lyra" settings/color-picker/player redesign).

### Bug fixes

- **Legacy drawer queue could still appear**: the guard only checked for an
  `activeQueueItemId`, which many apps never set on Android 10+, letting the
  old drawer queue slip through instead of the new `QueueActivity`. Now also
  blocked by list type (PLAYLIST/HISTORY).
- **Watch round-screen clipping in light mode**: the now-playing clock
  circle was cut off at the bezel edge; background drawable and
  button-config vertical spacing corrected.

## 1.12

Wear OS modernization initiative — bringing the watch app up to current
platform standards (foundation, system integration, native surfaces) and
starting the move to Jetpack Compose. Full roadmap in
`docs/wear-modernization-plan.md`.

### Phase 0 — Foundation (wear)

- Target SDK bumped 30 → 34, with the platform changes that become enforced
  at that target handled so behavior is unchanged on current devices:
  explicit `android:exported` on the launcher activity + Data Layer listener
  services (Android 12), and a `mediaPlayback` `foregroundServiceType` +
  permission on `WatchMusicService` (Android 14).
- Re-enabled the `ExpiredTargetSdkVersion` lint (its suppression is now
  obsolete).
- Migrated the deprecated `AmbientModeSupport` to `AmbientLifecycleObserver`.
- Removed the dead legacy `support.wearable` `ConfirmationActivity` manifest
  entry (the androidx one was already in use).
- Declares + requests `POST_NOTIFICATIONS` (Android 13) so the foreground
  notification keeps showing.

### Phase 1 — System media integration (wear)

- New watch-side **MediaSession proxy** (`WatchMediaSession`): mirrors the
  phone's now-playing state (title/artist/art/position/playback + remote
  volume) and forwards transport controls back to the phone. The phone's
  playback now appears in and is controllable from the system **Media
  Controls** app and the Wear OS media surfaces — no app UI rewrite required.
- MediaSession flags and `setSessionActivity` now set so the Wear OS recents
  screen shows the currently playing track name under the app name.
- New watch→phone skip-next / skip-previous command channel (previously only
  toggle/seek/volume/quick-action existed).
- `WatchMusicService`'s foreground notification is now a MediaStyle
  notification bound to the session.

### Performance (wear)

- Cut control latency: every watch→phone command was re-resolving the phone
  node via a `getConnectedNodes()` round-trip on each press. The node id is
  now cached and reused, so button presses reach the phone noticeably faster.

### Queue redesign (wear)

- Introduced **Jetpack Compose for Wear OS** into the module (first Compose
  here; pilot for the broader UI modernization).
- Fully replaced the legacy `WearableDrawerLayout` queue with a new
  **`QueueActivity`** hosting a Compose `QueueScreen`:
  - `ScalingLazyColumn` of dark glass pills; the now-playing entry is
    highlighted with the album's lightened (pastel) accent colour.
  - Animated three-bar **equalizer** next to the playing track.
  - **Marquee** scrolling for long titles inside the pill.
  - Clock rendered as **`CurvedText`** along the top bezel, matching the Wear
    OS style; it fades out as the user scrolls down.
  - Thin curved **scroll indicator** on the right bezel — fixed thumb size
    (no erratic resize with the rotary crown), auto-hides 1.2 s after
    scrolling stops.
  - **Swipe-to-dismiss** closes only the queue (reveals the now-playing
    screen underneath); the system window animation is suppressed so the
    Compose transition plays cleanly without a double-close flash.
  - Google Sans used throughout to match the rest of the watch UI.
- Artist name on the now-playing screen and in the quick-actions panel now
  uses a HSL-lightened version of the album accent (dark colours, e.g. deep
  purple, become a readable pastel; black text always used in the queue).

### Bug fixes (mobile + wear)

- **Shuffle button always appeared active**: apps that never set their shuffle
  mode report `SHUFFLE_MODE_INVALID (-1)` which is not `SHUFFLE_MODE_NONE
  (0)`, so the comparison wrongly treated them as "shuffling". Fixed by
  checking for the explicitly-ON states (`ALL` / `GROUP`) instead.
- **Repeat button skipped "repeat one" on some apps** (e.g. Retro Music):
  `REPEAT_MODE_GROUP` is semantically "repeat all" but was falling through to
  the `else → NONE` branch in `RepeatAction`, bypassing repeat-one. Fixed.
- **Album art missing on Retro Music and other apps**: many apps on Android
  10+ provide art as a `content://` URI rather than a raw `Bitmap` to reduce
  memory usage. Added URI fallback (`ALBUM_ART_URI` / `ART_URI` /
  `DISPLAY_ICON_URI`) with synchronous `ContentResolver` loading (network
  URIs are skipped to avoid blocking the main thread).
- **Like / favourite button not reflecting state on watch after toggling**:
  some apps don't immediately re-publish their playback state after handling a
  custom like action. A forced re-read of the state is now scheduled 500 ms
  after every like action so the watch button updates even in that case.

## 1.11

Dark "glass/acrylic" redesign of the watch UI, plus new playback features.

### Visual redesign (wear)

- New typeface (Google Sans) applied across the watch UI.
- Dark, minimalist "acrylic/glass" visual style replacing the old flat
  Material look (new `glass_card_background`, `glass_circle_background`,
  `queue_pill_background` drawables, shared glass color tokens).
- Redesigned circular volume control: left-edge vertical arc matching the
  stock Wear OS look, thicker stroke to match the new outline icons.
- New outline icons for volume up/down and the like button, redrawn as
  simple stroked shapes instead of outlining the old filled icon paths
  (which produced a messy/illegible result).
- Ambient (always-on display) mode improvements: blurred album art behind
  the clock instead of a flat dim, no black vignette, artist name shown in
  plain bold (no outline effect) while the title keeps its outlined look.
- Smart shrink-to-fit text sizing for long titles/artists (word-aware,
  falls back to marquee only when a title genuinely can't fit).
- Notification popup and queue/history list restyled to the new glass look.

### Seek bar

- New circular drag-to-seek progress bar around the now-playing screen,
  with a live time-remaining overlay while dragging.
- Position is interpolated locally between updates so the ring moves
  smoothly without spamming the phone connection.

### Like / shuffle / repeat

- New "Like" action: looks for a like/favorite custom action exposed by
  the currently playing app's media session (works with apps like YouTube
  Music and Retro Music that expose one).
- New "Shuffle" and "Repeat" actions, reading/writing real shuffle and
  repeat-mode state through the AndroidX media-compat layer (the bare
  framework `MediaController` API has no concept of either).
- Real shuffle/repeat state is now synced from the phone to the watch and
  reflected live on the quick-actions panel below.

### Quick-actions panel

- Double-tapping the center play/pause button opens a new panel with
  Like / Shuffle / Repeat buttons plus an "Up Next" shortcut into the
  queue - matching the stock Wear OS player's quick panel. Single-tap
  still toggles play/pause as before.
- Shows the current track's title/artist above the buttons.
- Shuffle/repeat buttons highlight with a color pulled from the album
  art when active; all three buttons flash that color on press.
- "Up Next" opens the real queue (regardless of the swipe-up preference)
  and previews the next track's name when a real queue is available.

### Queue / playback history

- New local play-history fallback: when the playing app doesn't expose a
  real skippable queue (common on Android 10+), the watch now shows a
  list of recently played tracks instead of an unhelpful error.
- Queue/history rows redesigned to match the stock Wear OS queue look:
  no album art thumbnails, title + artist on separate single (non-
  wrapping) lines, pill-shaped rows.
- Removed the old per-item dimming and circular curving effect from this
  list - it was designed for the old single-line icon rows and looked
  dated and "bent" against the new taller pill rows.

### Other fixes found along the way

- Fixed `OpenPlaylistAction` being mis-bound to the wrong (no-op) handler.
- Fixed the seek bar freezing/snapping back during a drag (a leftover
  position animator kept running underneath the touch).
- Fixed the seek bar losing an in-progress drag whenever the finger
  passed near a quadrant icon.
- Fixed cross-device position drift by converting the phone's
  `elapsedRealtime`-based playback position to a wall-clock timestamp
  before sending it to the watch.
- Fixed center-tap play/pause toggling losing touch events to the
  quadrant layer underneath it once double-tap detection was added.

## Earlier versions

See the GitHub release history for changes before 1.11.
