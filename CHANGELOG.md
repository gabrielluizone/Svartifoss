# Changelog

## Unreleased

These changes are still in development and are not part of a published release.

### Phone experience

- **Create and save your own watch themes**: the existing designs are now
  available as base layouts for reusable custom profiles. Pick Classic,
  Expressive, Poster, Studio, Material or another available layout, give the
  theme a name, then combine its typography, colors, artwork blur, dim and
  shading, AOD, progress, panels and mini-button appearance in the same live
  six-section editor. The new theme manager can apply, customize, duplicate,
  rename and delete profiles, clearly identifies the active theme and returns
  a newly created profile directly to its editor.
- **Layouts and backgrounds are independent**: every player layout can now use
  the artwork treatment originally authored for Classic, Expressive, Material,
  Poster, Studio, Vinyl, Halo, Aurora, Spectrum or Eclipse. Choosing a
  background no longer swaps the clock, metadata or control geometry, and each
  built-in/custom theme remembers its own selection and blur strength.
- **Independent, portable theme profiles**: editing a saved theme no longer
  changes the appearance stored for its base layout. The complete profile
  library stays on the phone while only one validated, atomic active snapshot
  is synchronized to Wear, keeping the payload bounded. Profiles, names and
  active selection are included in schema-3 configuration backups; legacy
  backups safely return to a built-in layout without corrupting an existing
  profile. Color resets are materialized explicitly so stale values cannot
  remain on a disconnected/reconnected watch.
- **Per-layout appearance**: every setting on the Watch tab (colors, overlays,
  always-on, panels, mini buttons) is now remembered *per layout*. Configure a
  layout the way you want, switch to another, and each keeps its own look; the
  live preview follows the selected layout. Existing setups carry over as each
  layout's starting point.
- **Truthful watch preview for the curated layouts**: the Watch tab miniature
  now reproduces what the watch actually renders — Aurora shows its real glass
  card (metadata inside, play focus on the corner) instead of a preview-only
  design, and every curated backdrop uses the same scrim math as the watch.

- **Archived options (developer mode)**: a new "Show archived options" switch
  in the developer section reveals watch layouts and settings archived because
  of known issues. The Vinyl, Halo, Aurora, Eclipse and Spectrum layouts are
  archived for now — a currently selected archived layout keeps working and
  stays listed.
- **Streaming shortcuts rebuilt for multiple services**: the shortcuts list and
  Add/Edit screen have been redesigned with clearer service and content labels,
  live link inspection, share/clipboard input, drag reordering, Open now, Copy
  link and Undo after deletion. Spotify, YouTube Music, Apple Music, TIDAL,
  Deezer, Amazon Music and SoundCloud links are recognized locally as tracks,
  playlists, albums, artists, shows, episodes or mixes. Text spacing was refined,
  the single BETA badge replaces the duplicated title suffix, all decorative
  marks follow the live accent, and filled actions choose black or white content
  automatically for contrast.
- **Streaming content inside Actions**: saved tracks, albums, mixes and
  playlists can now be assigned directly to the watch Actions menu and Quick
  Actions, alongside apps and normal commands. The Watch → Panels page also
  includes a live shortcut count and a direct link to the existing editor so
  this feature is easier to discover without creating a duplicate library.
- **Faster Streaming shortcuts on the watch**: shortcuts now use their own
  persistent Wear data cache, independent from queue and search responses. The
  watch renders the screen immediately from local data instead of waiting for a
  phone/Bluetooth round trip whenever it is opened.
- **More reliable streaming playback**: playable links first try the active
  app's MediaSession commands and then fall back to a correctly typed deep link.
  Spotify web links are converted to matching track, playlist, album, artist,
  show or episode app URIs instead of being treated as one generic playlist.
- **Modernized navigation icons**: Watch and Controls now use dedicated watch
  vibration and gamepad icons, and Buy Me a Coffee uses the supplied coffee
  artwork instead of the generic lightning bolt.

### Watch experience

- **Colors that match the layout**: on the album-accent layouts (Expressive
  and the curated collection) the quick panel, volume, seek and queue surfaces
  now default to the album accent to match the player, instead of a neutral
  grey — still overridable per layout.
- **Wide, compact Up Next on the always-on display**: Expressive and Material
  AOD layouts use 88% of the available width while clamping the row to 44–52 dp,
  and the row now sits higher in the usable lower band without touching the
  center transport controls. It can
  show the next track's artwork, title and artist and remains intentionally
  non-interactive while ambient.
- **Up Next no longer depends on opening Quick Actions**: queue data is
  loaded from the local Wear cache at startup, then refreshed and retained when
  playback or the current track changes. The AOD is populated without first
  opening the quick-actions panel and is not erased merely because ambient mode
  started.
- **Better metadata placement**: title and artist on the interactive Expressive
  and Material players sit comfortably between the clock and playback controls.
  Poster and Studio AOD remove the central play/pause circle and center the song
  title with the artist immediately below it.
- **Material track time restored**: the Material player now shows elapsed and
  total time whenever Track time is enabled, using the same transport-relative
  placement and mini-button clearance as Expressive; the phone preview mirrors
  that placement.
- **Artwork in Up Next and queue**: the quick-actions preview, ambient Up Next
  pill and playback queue show a compact cover thumbnail whenever the media app
  supplies one, while preserving the text-only layout when artwork is missing.
- **Expressive side buttons follow your Controls**: the previous/next buttons
  on the Expressive layout now run whatever you assigned to the left/right
  screen quadrants (and show that action's icon), falling back to
  previous/next when a quadrant is unassigned.
- **Overlays fully cover the screen**: the volume/seek/quick-panel backdrop no
  longer lets the clock, album art or side buttons bleed through near the
  edges.
- **Consistent top clock**: the Compose layouts (Expressive and the curated
  collection) now show the same straight top-center clock as Classic instead
  of a curved one hugging the bezel.
- **Poster/Studio layout stability**: the track title always occupies exactly
  two lines so the centered block no longer jumps between tracks, and the
  track time anchors to the bottom of the screen when no progress indicator
  sits above it instead of floating mid-air.
- **Art fade fix**: smooth artwork transitions now apply to layouts that draw
  the cover themselves (Poster full-bleed, Vinyl's label, Halo's disc) — they
  used to swap instantly, making the preference appear broken on Poster.
- **Background dim and fade now work on every layout**: the curated layouts
  (Vinyl, Poster, Studio, Halo, Aurora, Spectrum) scale their own backdrop
  treatment with the "Dim album art" preference — each adapted to its style —
  instead of ignoring it; smooth art transitions apply everywhere art shows.
  Eclipse stays deliberately pure black. Defaults reproduce the previous look.
- **Tap feedback on curated layouts**: tapping the play focus of a curated
  layout now flashes the same expanding ring the Classic layout shows.
- **Instant seek/volume backdrop blur**: the blurred overlay backdrop is
  applied immediately when the overlay opens instead of fading in.
- **Quick Actions behaves like the queue**: the panel uses the same round-screen
  row scaling and fixed curved bezel indicator, tighter app/playlist spacing,
  stable-height media pills widened to the Up Next row, and correct centering
  when an app exposes fewer than three actions. A back swipe or a tap outside
  an action dismisses only the panel instead of closing the app.
- **Reliable Quick Actions icons**: full-colour launcher icons are preserved,
  monochrome vectors receive the correct surface tint, and transparent or
  corrupt media-app artwork falls back to a visible semantic glyph.

- **Clearer navigation and grouping**: the separate playing and stopped setup
  screens now live together under Controls, with swipeable "Music playing" and
  "No playback" tabs. Watch settings are grouped into Player, Background,
  Colors, Always-on, Panels and Mini buttons, while Settings is split into
  General, Behavior, Automation, Apps and Data & support. YouTube Music
  playlist shortcuts now live in Apps, leaving room for future Spotify and
  other streaming-service integrations.
- **Larger, responsive watch preview**: the preview gives the watch more room,
  adapts to the connected watch's shape and proportions, and no longer competes
  with explanatory copy beside it.
- **Contextual live preview**: changing a setting can now show the relevant
  watch surface immediately — including the player, ambient display, volume,
  seek, quick actions, queue and mini buttons — using live media information
  and the configured colors and icons whenever they are available.
- **Improved Actions editor**: the quick-actions panel and watch menu are
  clearly separated, the screen shows the configured action count and an empty
  state, and menu actions can be reordered by drag, keyboard or accessibility
  actions. Those configured rows now continue below Up Next as scrollable Quick
  Actions, including apps and saved streaming content.

- **Expressive seek setting**: the Expressive layout's touch-seek option (central
  ring, edge ring, or off) is now adjustable from the Watch tab instead of being
  fixed.
- **Notification popup settings restored**: the option to show a popup when
  Vibration Center for Wear vibrates the watch, and its auto-close timeout, are
  configurable again under Settings → Behavior.

### Watch customization

- **Expanded typeface library**: title and artist text on every player and AOD
  layout can use one of 18 typefaces, including rounded, light, ultra-thin,
  heavy, small-caps, casual, serif mono and multiple condensed styles alongside
  the existing families. Typewriter is archived and appears only when Show
  archived options is enabled in developer mode.

- **Rebuilt player layouts**: Vinyl, Poster, Studio and Halo were redesigned
  from the ground up for a watch-sized screen. Aurora, Eclipse AMOLED and
  Spectrum join them as new layouts alongside Classic and Expressive.
- **Artwork-driven designs**: the curated layouts use colors extracted from the
  current album art for their gradients, progress treatments and accents, with
  dedicated ambient-display variants and a true-black presentation for Eclipse
  AMOLED.
- **Mini buttons no longer break layouts**: Compose players place shortcuts in
  a compact lower-bezel row, remove conflicting lower chrome and make only the
  small collision-specific adjustments needed by Halo/Aurora. Neutral buttons
  inherit a layout-aware surface instead of one generic glass pill.
- **Simplified control styles**: the picker now exposes the four reliable
  choices — Balanced, Minimal, Compact and High contrast — and migrates legacy
  values safely. Player icon visibility remains configurable on Classic, Poster
  and Studio, while Material and Expressive retain their essential playback
  controls.
- **Layout-aware settings**: options that a selected player or always-on layout
  cannot render are hidden instead of appearing to be broken. Clock, track time,
  progress color, dynamic accent and blur changes now update consistently across
  the watch and its phone preview.
- **Independent metadata visibility**: song title and artist can each be hidden
  on every interactive and always-on player layout. Playback and error status
  messages remain visible even when metadata is disabled.
- **Universal edge progress and seek**: every layout can show or hide the edge
  progress ring and independently enable touch scrubbing on it. Expressive's
  central scrubber is suppressed only while edge touch seeking is enabled; a
  display-only edge ring can coexist without creating competing touch targets.
- **New Material and White pills**: volume and seek overlays gain Material pill
  and White pill options, both with consistent dark, high-contrast text.
- **Theme-aware overlay backdrops**: volume, seek and quick-actions surfaces now
  use blur, solid color or gradients appropriate to their selected visual
  style instead of always placing the same dark translucent blur underneath.
- **Independent color treatment per surface**: artist text, progress/seek,
  volume and Quick Actions can each follow the watch treatment or independently
  use Normal, Desaturated or Expressive color. Normal mode has a selectable
  custom color, and the same result is reflected in the phone preview and watch.
- **Distinct panel layouts with immediate sync**: volume, seek and Quick Actions
  now have genuinely different geometry options, not only different colors or
  bar skins. Layout and color changes are synchronized to the watch immediately
  instead of appearing only after another preference changes.
- **Cleaner Poster and Studio layouts**: the play button circle was removed from
  the center of the Poster, Studio and Halo faces so album artwork is no longer
  obscured. Poster and Studio now display the track title and artist name
  centered on the screen, filling the space previously occupied by the button.
- **Roomier Classic mini buttons**: Classic distributes its mini buttons across
  the available lower space instead of leaving them visibly cramped together.

### Controls and quick actions

- **Real app-provided quick actions**: up to three primary buttons mirror the
  current media notification's compact actions, labels and icons and execute the
  original notification buttons on the phone. MediaSession custom actions
  remain a fallback when notification metadata is unavailable.
- **Scrollable, watch-sized Quick Actions**: the panel restores large action
  bubbles and a full-width Up Next row, supports touch and crown scrolling, and
  can continue with configured watch-menu actions such as streaming shortcuts
  below the primary media controls. Items scale and fade near the round bezel,
  a curved auto-hiding indicator replaces the straight scrollbar, and the
  redundant panel clock has been removed.
- **Back closes Quick Actions, not the app**: the standard Wear swipe-to-dismiss
  gesture is handled by the panel itself, restoring the player underneath
  instead of finishing the main watch activity.
- **Correct action count for each media app**: compact notification metadata is
  respected instead of filling missing slots with unrelated configured actions.
  Apps such as Spotify that expose two controls now show two centered buttons
  with no phantom third pill, while three-action apps keep a balanced row.
- **Cleaner control setup**: touch zones, swipe gestures and mini buttons use a
  more compact layout. Gesture and mini-button captions identify only their
  position, without repeating long assigned-action names below each icon.
- **Useful setup while disconnected**: touch zones, gestures and mini buttons
  remain configurable without a connected watch. The physical-buttons area
  explains whether the watch is disconnected or simply has no compatible
  buttons.
- **Accurate mini-button preview**: saving a shortcut refreshes the preview with
  the actual configured icon, including custom action icons.
- **Crash-reporting choice**: Firebase Crashlytics reports remain enabled by
  default, but can now be disabled under Data & support → Privacy. Opting out
  stops the app's custom crash logs and removes queued unsent reports. Automatic
  upload remains disabled; finalized reports are sent manually only after the
  app has read the current privacy choice.

### Developer and support

- **Useful on-watch developer overlays**: developer mode can draw layout bounds
  and show live player diagnostics such as face, theme, playback state,
  position, duration, active overlay, color treatment and action count directly
  on the watch.
- **Correct support archive name**: Get support now exports `logs.zip` instead
  of `logs.logs_zip`.

### Fixes

- **No false disconnected flash in Settings**: the app now waits for the first
  watch connection snapshot instead of briefly showing an incorrect state when
  Settings opens.
- **Stable, swipeable tabs**: section labels no longer jump vertically while
  settling, and pages can be changed with a horizontal drag — including drags
  that begin over the watch preview.
- **Smoother preview blur**: the phone preview's background blur no longer uses
  an overly coarse intermediate image that made it look pixelated compared with
  the watch.
- **No phantom play button in the Poster/Studio preview**: the phone preview
  drew a center play/pause button on these two layouts even though the watch
  never renders one there (tapping still toggles playback); the preview's
  Studio progress ring is also repositioned toward the bottom edge to match
  where the watch actually draws it.
- **Safer watch geometry**: the new player layouts account for mini-button
  offsets and screen shape so titles, progress and playback controls are less
  likely to overlap or be clipped.
- **Seekable Spectrum**: playback and timeline targets no longer overlap; the
  bars seek across their full width, expose an accessibility adjustment action,
  and preserve configured swipe gestures.
- **More reliable contrast**: dynamic tab, navigation and text colors keep a
  readable contrast against album-derived backgrounds.
- **Album art blur memory leak fixed (pre-Android 12 watches)**: on watches
  running Wear OS older than 3 (Android 11 and below), the software blur path
  allocated a new bitmap on every track change without recycling the previous
  one, progressively consuming memory until the system triggered GC pauses or an
  OOM kill. Each blurred bitmap is now explicitly recycled before its replacement
  is created.
- **Blur radius slider now has a visible effect on older watches**: the legacy
  software blur's downscale range was so narrow (50–58% of source size) that
  moving the radius slider produced almost no visible change. The range now spans
  18–58%, so the full slider travel delivers a meaningfully different blur
  strength from subtle to heavy.
- **Blur edge artifacts eliminated (Android 12+ watches)**: the GPU blur
  (`RenderEffect`) was configured with `CLAMP` tile mode, which extends edge
  pixels outward and produces colored streaks at the screen border when blur
  radius is high. Switched to `DECAL`, which fills out-of-bounds areas with
  transparent instead.
- **Overlay tint now matches the player**: the volume, seek and quick-actions
  backdrops tinted from the album art used a narrower, more desaturated color
  band than the player layouts, so an overlay opened over a layout looked like a
  different, mismatched color. They now share the layouts' color band.
- **No blur "jump" when opening an overlay**: when the background was already
  blurred, opening a volume/seek/quick-actions overlay could make the blur pop to
  a different strength (the two used independent blur amounts). The overlay now
  reuses the background's blur when it is already blurred, and fades in instead of
  snapping, so the transition is smooth.
- **No stale color on track change**: album-tinted layouts and open overlays no
  longer show a new cover with the previous track's color for a frame — the cover
  and its extracted color are now applied together.
- **Steadier accent color**: a small, bright detail in a cover (a logo, a
  reflection) no longer hijacks the whole accent when the rest of the artwork is
  a different color; the dominant color is used unless the vivid one covers a
  meaningful share of the art.
- **Control style no longer changes the seek backdrop**: the seek overlay's
  background followed the selected control style, unlike the volume and
  quick-actions surfaces. It now behaves like them, independent of the control
  style.
- **Preview blur matches the watch**: the phone preview's background blur was far
  weaker than the watch's at the same radius; the slider now previews the same
  strength you get on the watch.
- **Quick Actions icons no longer garbled with some apps**: notification action
  icons delivered as tinted/monochrome or launcher-style adaptive-icon
  drawables (as YouTube Music's are) rendered corrupted on the watch. They are
  now rasterized in a way that accounts for both cases.
- **Dislike no longer crowds out useful shortcuts**: a thumbs-down/dislike
  notification action could take the quick-actions panel's wide slot ahead of
  more useful actions like skip or repeat. Lower-value actions like dislike are
  now deprioritized when a slot is chosen.
- **Expressive's redundant bottom row removed**: the queue/volume/menu trio
  that used to sit at the bottom of the Expressive layout was removed — mini
  buttons already cover the same shortcuts. Configured mini buttons and the
  layout's essential transport icons remain available under every supported
  control style.
- **More reliable mini buttons on every layout**: on Vinyl, Poster, Studio,
  Halo, Aurora, Eclipse, Spectrum and Expressive, mini buttons could settle at
  the wrong height after changing the shortcut offset, and Expressive's own
  track-time text could overlap them. Both now react to the row's real
  position instead of an approximation, including when the progress bar is
  shown or hidden.
- **Tighter text spacing on curated layouts**: title and artist text on Vinyl,
  Poster, Studio, Halo, Eclipse, Aurora and Spectrum had noticeably larger
  line spacing than intended. Spacing is tight again, matching the original
  design.
- **Consistent track-time display across layouts**: the "1:23 / 3:45" readout
  on curated layouts was smaller, dimmer and hidden whenever mini buttons were
  configured. It now matches Classic's size and full opacity, and stays
  visible under the same conditions Classic uses.
- **Tap feedback on every layout**: tapping the screen outside of a button only
  showed a ripple animation on the Classic layout. Every other layout now
  shows the same feedback.

## 2.2.2

### Updates

- **Update the phone from inside the app**: when a new release is out, the
  Updates screen now has an "Update phone" button that downloads the phone APK
  and opens the installer — no more hunting for the file in the browser. (Needs
  the one-time "install unknown apps" permission, same as installing any
  sideloaded app.)
- **Redesigned Updates screen**: a status band tells you at a glance whether
  you're up to date, the phone and watch versions sit on their own card, and
  the release notes ("What's new") show right there — now rendered as
  formatted text instead of raw Markdown, with tighter spacing and icons that
  actually line up with their labels.
- **"Check for updates now" moved to the top of Settings** (its own category,
  above everything else) instead of being buried near the bottom.
- **Update-available icon next to Help**: a green icon appears in the toolbar
  whenever a checked release is newer than what's installed, and opens the
  Updates screen when tapped — so a dismissed or missed update notification
  isn't the only way to notice one is waiting.

### Watch appearance

- **New "Expressive pill" seek & volume style**: a colorful tonal pill in the
  album accent color with dark text on top (the system media player's
  tonal-pair look), instead of white text on dark glass. Applies to both the
  scrub-time readout while seeking and the volume-percentage readout while
  adjusting volume — that setting now styles both together.
- **New "Groove" volume style**: a recessed dark channel with a slim bright
  accent core, joining the existing volume-overlay styles.

### Fixes

- **"Update phone" failing to install ("problem parsing the package")**: the
  downloaded APK could come back truncated (a known Android networking bug
  with the redirect GitHub release-asset links go through), which was then
  silently handed to the installer instead of being caught. The download is
  now verified against the expected size before installing, and a bad
  download is deleted and reported so the button can be retried instead of
  failing confusingly.
- **Watch crash on Wear OS 5**: the watch app could crash on Wear OS 5 (e.g.
  Xiaomi Watch 2) while refreshing the media Tile / complication — the tiles
  library reads a system setting the platform now blocks for apps targeting
  API 35. These refreshes are best-effort again and can no longer bring the
  app down.
- **Finger scrolling in the watch menu with "Always select center action" on**:
  the menu could only be scrolled with the rotary crown, not by dragging,
  because that mode covered the list with an invisible tap target. Tapping now
  confirms the centered row without blocking scroll, so finger and crown both
  work.
- **Config backups restore across Android versions**: exported config/backup
  files are stored in a version-independent format and rebuilt on the importing
  device, so a backup made on one Android version restores correctly on
  another (the old format could fail to decode across OS versions). Existing
  backups still import.
- **Less crash-report noise**: expected, already-handled situations — a config
  snapshot that can't be decoded on a given Android version and falls back to
  defaults, routine coroutine cancellation, "phone not currently connected" —
  no longer get logged as crashes.

## 2.2.1

### Two new faces (Beta)

- **Vinyl (Beta)**: the album art becomes a slowly spinning record — groove
  rings, accent label and spindle hole — with the playback progress as an
  accent arc around the record's rim and glass prev/next buttons beside it.
  Tap the record to play/pause (it shows a play badge and stops spinning
  while paused); double-tap and long-press keep their usual quick-panel /
  queue roles.
- **Poster (Beta)**: a flat, typography-first look — a deep tonal backdrop
  derived from the album's accent, big two-line title with the artist in
  small caps, squared transport buttons and a straight progress bar with the
  track time under it.
- Both faces bring their own always-on display variant (thin outlines over
  black, burn-in safe), honor all the AOD element toggles/colors, show up in
  the phone's live Watch-face preview, and keep the mini buttons and quick
  panel exactly where the other faces have them. They're marked Beta:
  expect small visual adjustments based on feedback.

### New styles

- **Progress ring styles** (classic face / expressive "edge" seek): Solid,
  Dashed, Dots, Hairline, and Comet — a tail that fades in toward a bright
  head dot. Seeking works identically on all of them.
- **Three new volume overlay styles**: Segments (level-meter tick blocks),
  Aurora (multi-hue gradient) and Ink (wide translucent halo with a solid
  core).
- **Seek time styles** for the readout shown while scrubbing: Plain, Glass
  pill, Giant, or Position / total stacked.

### Fixes

- **Fixed the edge progress ring reappearing on the Expressive face after
  every always-on-display round trip**, even with the central or hidden
  seek mode selected: the ambient-exit restore ran while the system still
  reported ambient mode, so the face-specific cleanup silently no-oped.
- **The AOD title now follows the always-on display color mode**: with
  "album accent" (or a custom color) selected, the track title tints along
  with the outlines on every AOD style — it used to stay white.

### Other

- **The Tile (widget on Wear OS 7) gained a −10s / +10s seek row** under the
  prev/play/next buttons (thanks to the Pixel Watch 3 feedback!). The jump
  is resolved against the phone's live playback position, so it stays
  accurate even when the Tile's snapshot is stale.
- The persistent "controls active" notification now offers two explicit
  actions: **Stop** (same as tapping it, ends the controls) and **Force
  stop** (fully kills the app process and detaches the notification
  listener until the next reboot or access toggle).
- The watch's recents/launcher chip and the album-art complication now
  separate title and artist with "•" instead of "—".
- Firebase Analytics is back (anonymous usage stats, auto-collected events
  only) to help development — the app is sideload-only, so this and crash
  reporting are the only signals of how features are actually used. The
  README/landing-page privacy notes were updated to match.

## 2.2

### Updates without a cable

- **Update notifications on the phone**: Svartifoss now checks its GitHub
  releases about once a day (whenever music plays or the app is opened) and
  posts a notification when a newer version is out. Configurable in Settings
  under the new **Updates** section, including an opt-in to also be notified
  about **pre-releases**. No account, no extra permissions — a single small
  request to the GitHub API.
- **Update the watch from the phone, over Bluetooth**: the new update screen
  (tap the notification, or Settings → "Check for updates now") has an
  **Update watch** button that downloads the new watch APK and streams it
  straight to the watch — a notification appears on the watch, one tap opens
  the system install prompt, and that's it. No more ADB or Wear Installer
  for updates. First time only: allow "install unknown apps" for Svartifoss
  in the watch settings when prompted. (Requires both apps on 2.2+; this
  first jump onto 2.2 still needs the old sideload route.)
- The update screen also shows the installed phone *and* watch versions side
  by side (the watch now reports its version to the phone).

### Fixes

- **Fixed a crash when opening the Watch tab on recent Android versions**
  (`BadParcelableException` in the mini-button preview). Saved button configs
  are raw Android parcels, whose format isn't stable across Android versions
  — a config seeded or imported from a backup made on a different version
  could blow up the Watch tab on every open (reported on Android 15). All
  config reads are now hardened: an unreadable config is quarantined and the
  app falls back to defaults instead of crashing, config *imports* are
  validated up front so an incompatible backup is rejected with an error
  instead of silently planting a broken file, and config writes are atomic
  so a mid-write kill can no longer corrupt them.
- **Fixed the watch app closing right after launch (and refusing to open)**
  on some watches: the startup "is the phone app installed?" check could
  crash the app when Play Services threw, or silently close it into the
  install notice on a flaky first lookup. The check no longer takes the app
  down — only a *confirmed* "phone app missing" answer shows the notice.

## 2.1.1

### Watch

- **Configurable always-on display (AOD)**: a new "Always-on display" section
  in the phone's Watch face tab controls how ambient mode looks — pick the
  **style** (Follow face, Classic, Expressive, or Minimal) and toggle the
  **album art** (off = pure black background, a real battery saver on AMOLED),
  the **clock**, and the **track title/artist** individually. The existing
  ambient album-art opacity setting moved into the same section.
- **Expressive face now has its own AOD**: ambient mode no longer snaps the
  Expressive face back to the classic look. The new expressive AOD keeps the
  same layout — title/artist, prev/cookie/next, contour progress ring, and
  the queue/volume/menu pill trio — but rendered as thin outlines over black
  (the Wear OS 6 system media controls' AOD look): no fills, no animations,
  no marquee, burn-in protected by the same pixel jiggle, and deliberately
  few lit pixels so hours of AOD stay cheap on battery.
- **AOD outline color, brightness, and per-element toggles**: the outlines
  can stay white or take the album color (or a custom color — both lifted
  automatically so dark accents stay readable on black), an AOD brightness
  setting (20–100%) dims everything for extra battery savings, and the
  transport buttons, progress ring, and bottom pill trio can each be shown
  or hidden individually. Because the expressive AOD keeps the exact button
  positions of the awake face, the tap that wakes the watch lands with your
  finger already on the button you were aiming at.

### Phone

- Fixed the phone often failing to detect that the watch app was installed,
  incorrectly showing an "Install Svartifoss on your watch" prompt even when
  it was already there and working. The check ran on a long-deprecated,
  blocking Play Services API that frequently never completed on current Play
  Services builds. Also fixed that prompt's button opening a dead Play Store
  page on the watch (the app isn't on the Play Store) — it now opens the
  GitHub releases page instead, matching the equivalent fix already shipped
  on the watch side.

## 2.1

### Watch

- **New Expressive now-playing face**: alongside the classic layout, a new
  face mirrors the Material 3 Expressive system media controls — a soft
  "cookie" play/pause button (morphs to a circle when paused) wrapped in a
  progress ring that follows its scalloped contour, large round prev/next
  buttons in the album accent's tonal colors, an accent-tinted/vignetted
  album backdrop, and a queue/volume/menu glass trio at the bottom whenever
  no mini buttons are configured. Switch faces from Settings → Screen face;
  every gesture, button, overlay, and the quick panel behaves identically on
  both, and ambient mode always falls back to the classic, burn-in-safe look.
- **Expressive face touch seek**: a new setting picks how touch-seeking works
  on the Expressive face — drag the **Central ring** around the cookie button
  (with a live time readout), show the **Edge ring** (the classic bezel seek
  ring), or **None** (leave seeking to the rotary crown).
- **13 new overlay & queue styles**: the volume overlay, quick-actions panel,
  and queue screen each get an independent style picker (Settings →
  Screen face → Overlays & queue) — Glass, Minimal (AMOLED), Material, Tonal,
  Neon, Light, Gradient, Mono, Outline, Duotone, High Contrast, Terminal, and
  Frost.
- Eliminated watch UI freezes on track change: album-art and custom-list icon
  decoding, the complication's cover re-encoding, and the media session's
  cover transfer were all blocking the watch's single main thread on every
  music-state push — they now run off it, and an unchanged cover is skipped
  instead of redecoded.
- Fixed the watch's shuffle/repeat/like state rings getting stuck after a
  state update was wrongly deduplicated away, fixed a possible crash from a
  volume divide-by-zero, fixed the back key swallowing the configured
  back/dismiss action on watches that deliver it as a key event, fixed a
  config-backup import never actually reaching the watch, and fixed a few
  memory/listener leaks.

### Phone

- **New Watch tab**, in the slot the Guide used to occupy: a dedicated visual
  customization screen for the watch's now-playing screen, with a **live
  miniature preview** — face (Classic/Expressive), screen theme, album
  background (style/blur/dim), colors (dynamic accent, artist/progress color
  sources), and mini-button appearance/offset — previewed exactly as it will
  render on the watch and synced there on every change. While music plays on
  the phone, the miniature mirrors the **actual current track** (real album
  art, title, artist, the accent extracted from that art, and live playback —
  the progress ring and track time advance in real time). These appearance
  settings moved out of Settings, which now keeps only behavior options.
- **Guide moved to the toolbar**: the usage guide no longer takes up a
  bottom-nav tab slot — it now opens from a help button in the toolbar,
  freeing that slot for the new Watch tab above.
- The app is now fully **navigable without a paired watch**: every screen
  (Watch, Playing/Stopped controls, Actions, Settings) is reachable even with
  nothing connected — only the physical-buttons section of the button-config
  screens needs a live watch, and it already hides itself gracefully when
  there's no data. A dismissable banner in Settings clarifies that watch
  settings still save and sync even without a paired watch.
- **Portuguese (Brazil) translation completed for the phone app.** The watch
  app and shared module were already fully translated; the phone app's ~320
  strings and option lists (themes, screen styles, etc.) are now translated
  too, so the whole app is consistent in pt-BR.
- **Support the project**: a link to [Buy Me a Coffee](https://buymeacoffee.com/gabrielsvafoss)
  in the app's navigation drawer (swipe in from the left edge), for anyone
  who wants to help out. The app stays fully free either way — nothing is
  gated behind it.
- The **Overlays & queue** style pickers (volume, quick panel, queue screen)
  moved up to right after **Screen face** on the Watch tab, closer to where
  they belong given how much they change the look.
- Fixed the "No watch connected" banner in Settings flashing on and back off
  immediately when a watch actually is connected.
- Fixed the Watch tab's live preview briefly showing the default accent
  color before switching to the real album-art color when a track loads.

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
