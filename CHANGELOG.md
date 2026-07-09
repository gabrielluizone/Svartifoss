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
- Selectable **screen face**: besides the classic now-playing layout (edge
  seek ring + gesture icons), a new **Expressive** face mirroring the
  Material 3 Expressive system media controls — a soft "cookie" play/pause
  button (morphs to a circle when paused) wrapped in a progress ring that
  follows the cookie's scalloped contour (with thumb dot and M3 gaps), large
  round prev/next buttons in the album accent's tonal container colors, the
  album art darkened by an accent tint plus a radial black vignette, a curved
  clock, and a default queue/volume/menu glass trio at the bottom whenever no
  mini buttons are configured. Picked on the phone (Settings → Screen face);
  all gestures, buttons, overlays and the quick panel work identically on
  both faces, and ambient mode always falls back to the burn-in-safe classic
  look.
- **Expressive face touch seek**: a new *Expressive seek* setting
  (Settings → Screen face) chooses how to scrub by touch on the Expressive
  face — **Central ring** (drag the progress ring around the cookie button,
  with a live time readout), **Edge ring** (also show the classic bezel seek
  ring), or **None** (leave seeking to the rotary crown). Previewed in the
  phone's live miniature.
- **Selectable overlay & queue styles**: the volume overlay, the quick-actions
  panel and the queue screen each get their own style picker (Settings →
  Screen face → Overlays & queue) with thirteen looks — **Glass** (the frosted
  default), **Minimal (AMOLED)** (pure black, hairline accents), **Material**
  (solid dark cards, MD2), **Tonal** (album-accent-tinted containers, matching
  the Expressive face), **Neon** (glowing accent outlines), **Light** (a
  light-theme with dark text), **Gradient** (album-accent gradient fills),
  **Mono** (neutral greyscale), **Outline** (thick cartoon outlines),
  **Duotone** (accent + complementary hue), **High Contrast** (bold black/white
  for accessibility), **Terminal** (sharp-cornered green CRT), and **Frost**
  (light translucent panels).
- New **screen theme** options (default/minimal/compact/cinema), album-art
  fade transition, album-art style (cover/blur/black-and-white/blur+bw/hidden)
  with blur radius and dim strength, ambient opacity, rotary dead-zone, and
  volume/seek overlay timeout — all new settings synced from phone to watch.
- **Configurable title text behavior**: the now-playing title's shrink/wrap/
  scroll mix is now an explicit, exclusive choice — Automatic (the previous
  combined behavior), Scroll (marquee), Wrap to two lines, or Shrink to fit.
- **Independent artist text and progress bar colors**: previously both
  always followed the same album-derived accent as the icons/mini-buttons.
  Each can now be set separately to neutral (static theme accent),
  album-derived (optionally desaturated) or a fixed custom color.
- Shared `WatchTheme`/chrome (curved clock, curved scroll indicator, loading
  spinner) reused across the queue and new menu screens; the phone
  connection is now kept alive for the duration of any full-screen watch
  activity (menu, queue) instead of just the main screen.
- Portuguese (Brazil) localization added for the wear and common modules.

### Mobile UI

- New **Watch** tab (in the slot the Guide briefly occupied): visual
  customization of the watch's now-playing screen with a **live miniature
  preview** — face (Classic/Expressive), screen theme, album background
  (style/blur/dim), colors from the music (dynamic accent, artist/progress
  color sources), and mini-button appearance/offset, all previewed exactly
  as they will render on the watch and synced to it on every change. While
  music plays on the phone, the miniature shows the **actual current track**
  (album art, title, artist, the accent extracted from that art, and live
  playback: the progress ring and track time advance in real time, and
  pausing morphs the preview exactly like the real face); otherwise it falls
  back to a built-in sample. These settings moved out of
  the Settings tab, which now keeps behavior-only options.
- The Wear OS usage **guide** moved from a bottom-nav tab to a help button
  on the toolbar (opens its own screen).
- New left navigation drawer with app/author info; toolbar title alignment
  fixed.
- Built-in icon picker grid for custom action icons; color-swatch preference
  rows for accent and custom colors, backed by a single source of truth for
  the live accent color ("Lyra" settings/color-picker/player redesign).

### Fresh-install defaults

- New installs now seed the button configs, action list and watch-behavior
  settings from a bundled default configuration (the same format used by
  Export/Import Config) instead of a generic auto-detected guess, so the
  app starts in a known-good, ready-to-use state.

### Bug fixes

- **Legacy drawer queue could still appear**: the guard only checked for an
  `activeQueueItemId`, which many apps never set on Android 10+, letting the
  old drawer queue slip through instead of the new `QueueActivity`. Now also
  blocked by list type (PLAYLIST/HISTORY).
- **Watch round-screen clipping in light mode**: the now-playing clock
  circle was cut off at the bezel edge; background drawable and
  button-config vertical spacing corrected.
- **Queue screen stuttered while scrolling**: each row's rounded-corner clip
  forced an offscreen `saveLayer` per row on every scroll frame; switched to
  drawing the rounded background directly. The now-playing equalizer and
  marquee title animations are also frozen while the list is actively
  scrolling, and the queue header no longer recomposes on every per-second
  position tick.
- **Current track name missing from the Wear OS recents card**: the app's
  task label wasn't being updated, so the app switcher only ever showed
  "Svartifoss" instead of the playing track.
- **Watch-face complication cover flashing back to the placeholder icon**:
  a complication refresh could land before the phone's album-art asset
  finished transferring over the Data Layer; the last successfully
  rendered cover is now cached and reused in that case instead of
  regressing to the placeholder.
- **Equalizer icon inconsistent across surfaces**: the static notification/
  ambient equalizer glyph used different bar geometry than the animated
  "Up Next" icon and the queue's now-playing indicator; redrawn to match.
- **Saved button configs silently failing to load in release builds**: the
  proguard keep rule protecting `PhoneAction`'s reflection-based
  deserialization constructor still targeted the pre-rename package
  (`com.matejdro.wearmusiccenter`), so R8 shrinking stripped that
  constructor from every action class in release builds - any saved
  button config (including a fresh install's defaults) silently loaded
  empty. Fixed the rule to the current package.
- **Watch UI freezing on track changes (including inside the queue screen)**:
  several pieces of heavy work ran on the watch's main thread every time the
  phone pushed new music state - decoding the album-art bitmap (and custom
  list icons), the watch-face complication re-encoding its cover cache as a
  max-quality PNG, and the media session re-sending the full cover bitmap
  across binder on every state update (every volume step and seek included).
  All bitmap decode/encode now runs on background threads, an unchanged cover
  is recognized by its Data Layer asset id and skipped outright, and the
  media-session metadata is only re-sent when something in it changed. A
  track change also no longer runs the whole UI update pass twice (the
  state-only put and the follow-up state+cover put delivered the same state
  to every screen twice).
- **Progress ring reset animation restored**: the ring once again sweeps
  back smoothly on a track change instead of snapping to zero - the snap
  had been introduced on the mistaken theory that the sweep was the
  perceived lag.
- **Settings and button/menu config changed on the phone only reached the
  watch after interacting with it**: a watch setting (e.g. album-art blur), a
  button mapping, or the action-menu list edited on the phone would apply on
  the watch only after tapping the screen or turning the crown. Root cause: the
  phone pushed those DataItems to the Data Layer *non-urgently*, so the system
  batched them for power and could delay the sync by minutes - until unrelated
  urgent traffic (a control message sent by interacting with the watch) flushed
  the queue. Music state and notifications were already sent urgent; the
  button-config, action-list and preference pushes now are too, so they sync
  immediately. Complementing this, a manifest-registered `ConfigListenerService`
  applies an incoming button/menu-config change to an already-open now-playing
  screen even when the phone connection had gone idle, so it lands with no
  interaction needed.

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
