# Changelog

## 3.0

A major update: streaming shortcuts that actually start playback (including in the background), the new minimalist Immersive layout, free bundled fonts, optional online shortcut artwork and the playing-app icon, a redesigned numeric/colored shading system, and many watch-app quality-of-life fixes.

### Localization

- **11 new languages**: German, Spanish, Italian, Dutch, Russian, Greek, Romanian, Indonesian, Persian, Simplified Chinese, and European Portuguese (in addition to existing English and Brazilian Portuguese). Covers both the phone and watch apps, plus the shared strings between them. Switch under Settings → Language, or via the system's per-app language picker on Android 13+.

### Performance

- **Faster watch play/pause.** The phone now calls the media session's play/pause transport control directly instead of routing a media-button key event through the session's button handler — one hop less, so the watch button reacts more like the system media controls do.
- **Settings apply to the watch immediately.** A setting changed on the phone is now delivered to a connected watch over the low-latency message channel as well as the durable data sync, so it applies at once instead of lagging until the watch next wakes or is touched.

### Controls

- **The center tap is now visible and configurable.** Tapping the middle of the now-playing screen has always toggled play/pause, but nothing on screen showed it and it couldn't be reassigned. The Controls tab (both Music playing and No playback pages) now shows a round button in the center of the watch preview for it — defaulting to Play/Pause, and reassignable to any action like a quadrant. Double-tap (quick panel) and long-press (queue) are unchanged.
- **Where the quick panel comes from is now explained.** The gesture picker for the new center button notes that double-tap still opens it regardless of what you assign to single tap, and the Watch face tab's Quick actions setting gets a note under it saying how to actually open the panel on the watch.

### Quick panel

- **Rebuilt quick panel layouts.** The old "Actions first" and "Compact deck" options were only reorderings of the metadata-first stack and looked almost identical to it; both are gone. In their place are four genuinely different compositions: **Arc** (the slots bow along the round screen's lower bezel), **Hero** (one dominant primary slot with the others stepped back), **Grid** (a dense two-column launcher, no metadata) and **Labelled rows** (each action as a full-width row with its name). If you had one of the removed options selected, the panel falls back to Metadata first.
- **Five reduced panel styles**: **Ghost** (no container at all - only an active slot gets a fill), **Mist** (a barely-there translucent surface), **Ink** (an accent rule under each slot), **Dot** (a small accent marker) and **Slab** (one flat neutral tone with a tight corner). Touch targets are unchanged in every case.
- **New opt-in: use a shortcut's cover as its quick-panel row background.** When Queue style is a Cover variant, a shortcut with a fetched cover (track, playlist, artist…) can now fill its action row with that art, the same treatment Up Next gets - off by default, under Watch face → Quick actions. Shortcuts without a fetched cover (just the app's launcher icon) are never affected, fixing the previous behaviour where any full-colour icon - including a plain app icon - got stretched across the row whenever Cover was selected.
- **Sharper shortcut covers.** Fetched thumbnails were capped at 256px and stretched to fill a much wider pill, which looked pixelated - the cache now stores up to 480px, and what's sent to the watch is sized to that watch's actual screen width instead of a fixed guess.
- **"Reload covers" button in Streaming shortcuts.** Force-refetches every saved shortcut's cover (shown next to the screen title, only when online covers are enabled) - picks up a changed remote cover, or the higher resolution above for thumbnails cached before it increased, since a cover is otherwise only ever fetched once and then reused.

### Watch surfaces

- **Color treatment pickers now show what each option actually looks like.** The Watch color treatment dialog (and its four per-surface overrides) used to be a plain text list you had to pick blind and check the preview to understand - each row now shows three small swatches previewing that option's real primary/secondary/tertiary colors, computed from the current album art (or the fixed color you've picked, for Normal).
- **Title text behavior now applies on Poster and Studio.** Both hardcoded their title to exactly two lines regardless of the setting, to avoid their centered layout shifting as the title grew or shrank - they now honour marquee/wrap/shrink/smart like every other face, the same trade-off those already accept.
- **Two more Title text behavior modes: wrap to three lines, and wrap to five.** The previous "Wrap" tier (two lines) stays; these give a long title more room before it ellipsizes, on every face.
- **New Title text behavior: Static.** A single line at a fixed size, ellipsized if too long - never shrinks like "Shrink to fit" and never scrolls like "Scroll (marquee)".
- **New opt-in: flash a quadrant icon on tap, on every face.** When a corner zone's action fires, its icon can now briefly flash to full brightness before settling back to normal - off by default, under Watch face → Player. Most useful on the Hidden screen theme, or with Show player controls off, where the icon is otherwise invisible and gave no confirmation of which action just ran: in both cases the icon now reveals itself just long enough for the flash, then hides again. Curated Compose faces (Vinyl, Poster, Halo…) have no persistent quadrant icon at all, so there the toggle instead makes the existing touch-point ripple bigger, brighter and longer-lived. Independent of the existing scale-bounce pulse, which is unaffected. Doesn't apply to the center play/pause zone, which already has its own tap feedback.
- **New: a fading "comet" trail confirms a swipe gesture.** If the active screen (playing or paused - checked independently) has at least one swipe direction assigned to an action, performing that swipe now draws a brief fading trail along the finger's path before it fades out - the same confirmation the quadrant flash gives a tap, for the one gesture that has no icon of its own. Works on every face; nothing to turn on, it's purely driven by whether a swipe is actually assigned.
- **Show player controls now applies to Poster and Studio.** Both faces never drew a play/pause glyph at all, so the setting had no visible effect there; they now show it like every other curated face, gated the same way.
- **New Album art style: Square, in three corner styles** (sharp, soft, rounded). Fits the (typically square) cover uncropped inside the round screen instead of center-cropping it to fill the circle, with the corners always filled by a blurred copy of the same artwork so there's never a black gap - available on every face, next to Cover/Blur/Black & white. Fixed the inset's scale being computed against the whole screen instead of the smaller square it's actually clipped to, which was silently zooming into the center of the cover by about 40% - it's now genuinely uncropped, and any cover that isn't already square is letterboxed within the square (blur showing through the gap) rather than cropped to fill it. The blurred backdrop now reuses the exact same GPU blur (or legacy software fallback) as the existing Blur style, instead of a separate, much weaker approximation that also had a real bitmap-recycling bug and could crash the watch after selecting Square.
- **Five new gradient Album art styles**: **Corona** (a soft color ring hugging the rim, the cover's center left untouched), **Dusk** (a single fade that darkens only toward the bottom), **Bloom** (three small, contained color glows tucked off-center), **Horizon** (the lightest-touch option in the set - only the very bottom band darkens) and **Ember** (one small, warm glow in a single corner, no scrim at all elsewhere) - join Poster/Studio/Vinyl/Halo/Aurora/Spectrum as authored background treatments selectable on any face. Corona/Dusk/Bloom were originally shipped far heavier - full-bleed opaque scrims under saturated color washes that buried the cover entirely - and got scaled back before release to actually leave the artwork visible, in the same spirit as Horizon/Ember.
- **The playing app's icon now shows on the always-on display**, next to the artist, as a monochrome glyph flattened to the AOD colour.
- **Simpler mini-buttons & gestures visibility.** The two per-layout controls are now a single option each — Always / Only while playing / Only while paused / Never — matching the Track-time option, instead of separate playing/paused switches.
- **New "Chrono" always-on style** (a large centred clock with the track title beneath) replaces the old "Minimal", which read almost identically to Classic.
- **Up Next pill on the player.** A new option shows the Up Next pill at the bottom of the now-playing screen, filling the space the mini-buttons row would take (it appears only while that row isn't showing). The pill on the player now honours the same background-style choice as the one in the quick-actions panel — including a fully **transparent** option that shows just the text — and the track time above it shifts up so the pill never covers it.
- **Up Next pill on every always-on face**, not just Expressive/Material.
- **Dozens of new action icons** to choose from when changing an action's icon.
- **Customisable clock.** The always-shown clock now follows your chosen font, and a new Watch face → Clock section adds a colour (white, dynamic — black or white by the brightness of the artwork *directly under the clock*, album accent, or a custom colour) and an opacity control. Previously it was a fixed semi-transparent white in Google Sans.
- **Up Next pill background.** The Up Next pill in the quick-actions panel gets its own background style, independent of the panel style: follow-panel (default), album accent, translucent, white, frosted white, black, or dynamic album tone. Text/icon colour auto-contrasts.
- **Title text behaviour now applies on the Immersive face** (marquee / wrap / shrink-to-fit) — it was the one layout still forcing single-line ellipsis.
- **Streaming-shortcut covers are round in the actions menu.** A shortcut's fetched artwork now gets the same circular clip + crop as the shortcuts list, instead of showing as a raw square when the shortcut is used as a menu action.
- **The app icon no longer shows wrongly in the Classic always-on display** (it was appearing accent-tinted and far from the artist name; it's now reliably hidden in ambient, as intended).
- **The AMOLED and Immersive always-on displays** no longer draw a static play/pause glyph in their centre.
- **The media Tile follows the album colour.** Its play/pause and open-app buttons were a fixed green; they now take the accent extracted from the current cover (the same colour the now-playing faces use), with the icon auto-contrasting, and fall back to the default accent when nothing is playing.
- **New Shortcuts Tile.** A second Tile lists your saved streaming shortcuts as tappable chips — one tap opens and plays it on the phone, the same way the watch menu does, with no need to open the app first. Add it from the watch's Tile carousel alongside the media Tile.
- **Text-selection handles follow the accent colour** in the theme editor and shortcut dialogs, instead of staying the default sage green.
- **Confirm dialogs follow the accent colour.** The Reset / Apply-to-all-faces confirmation buttons were stuck on the static theme green; they now match the accent currently on screen (including the album-dynamic accent), like the other Lyra dialogs.
- **Fixed odd letter spacing** in the theme-name box.
- **Watch-tab preview fixes.** The Immersive face previewed with no text at all (its bottom-grounded title/artist/time were missing); the always-on preview now centres the metadata and drops the centre play/pause for Eclipse and Immersive, and uses the same queue glyph the watch does — all matching what the watch actually draws. The Poster and Studio previews also no longer leave a large gap between the title and artist when the title is a single line — the preview reserves the real number of title lines, like the watch.


- **Eight more seek & volume readout styles**, mostly minimal: Micro, Shadow (no chrome, drop shadow for legibility over artwork), Underline, Hairline outline, Giant album colour, Mono, Tonal dark and Terminal.
- **More volume layouts**: the edge arc can now sit on the left (tall or normal), right, top or bottom of the bezel, or wrap the whole dial as a full ring, alongside the existing centre halo and horizontal meter. Every variant fills in the direction that matches its position, and works with all existing arc styles.
- **Seek ring thickness**: the edge ring can be thin, normal or thick, independently of its progress-ring style.
- **Up Next on the always-on display** is no longer limited to the Expressive and Material styles - every visual always-on face can show the pill.
- **Per-layout mini buttons and gestures toggles, split by playback state.** Watch face → Mini buttons has "Show mini buttons while playing" / "while paused" switches, and a new Screen gestures section has the same pair for the four screen-corner taps and swipe-up/down/left. All four are per-layout and per-state: e.g. hide the mini-button row on one face while a track plays but keep it when paused, without touching any other face and without losing your button/gesture assignments (they still apply everywhere else). Off means the row reclaims its space and the gestures do nothing; centre tap (play/pause) and the quick-actions double-tap are always unaffected.

### App

- **Language setting**: choose English or Português (Brasil) instead of following the device language. The choice syncs to the watch, so both apps stay in one language rather than each following its own device.
- **Contact the developer** from Settings → About.
- **Announcement notifications**: occasional pushes from the developer (new releases, important notices), delivered via Firebase Cloud Messaging with no account or server of ours — every install just subscribes to one shared topic. Enabled by default; turn it off any time under Settings → Data & support → Privacy.
- **New opt-in: hide the mini player.** The persistent now-playing bar docked above the bottom navigation can now be turned off under Settings → General → Appearance, for anyone who'd rather reclaim that space. Enabled by default; phone-only, so it doesn't sync to the watch or travel with a config backup.

### Watch layouts and fonts

- **New Immersive layout (Spotify-style)**: a minimalist face where the full-bleed cover fills the screen, the clock sits at the top and only the track title, artist and time sit at the bottom. It shows the playing app's icon next to the artist (see below) and leaves previous/next/volume/menu to the mini buttons and gestures. Its text stays grounded at the bottom of the screen; mini buttons simply draw over it rather than pushing it up into the middle of the layout.
- **Show the playing app's icon**: the icon of the app currently playing appears next to the artist on every layout, mirroring how a service badge looks on built-in players. It uses the icon from the app's own media notification - the branded glyph you see in the status bar - rather than its launcher icon, and is sized to each layout's artist typography. It's on by default and can be turned off (when off, the icon is never sent to the watch).
- **Three new bundled fonts**: Poppins and Montserrat (clean geometric sans) and Marcellus (an elegant humanist roman) join the font picker - all free, redistributable (OFL) typefaces.
- **Cleaner always-on display**: the Immersive and Eclipse (AMOLED) AODs now match the Poster/Studio centered style, and the Minimal AOD is genuinely stripped back (title + clock only) so it reads clearly different from Classic.
- **New "Cover" list styles**: each entry's own cover art fills the whole pill behind its title, mirroring the Wear OS media-template browse lists. One switch (Queue style) applies it everywhere a pill has artwork: the queue, the actions and playlist-shortcut menus, the quick panel's Up Next row and its shortcut rows. Four variations to pick from - **Cover** (full-bleed), **Cover Blur** (soft backdrop with the sharp thumbnail kept), **Cover Tonal** (washed in the album accent instead of neutral black) and **Cover Square** (squared-off corners). Entries with no artwork of their own keep the pill of whichever theme you're using, so lists always remain readable.
- **List pill size**: a new setting sets the height of the full-width rows - Compact, Normal, Tall or Extra tall - across the queue, the menus and the quick panel. It works with every list style, not just the cover ones, so a Tonal or Glass queue can be roomy too.
- **Even list pills**: quick-panel and queue rows are now all the same height. Entries with a two-line label (Up Next, tracks with an artist) used to come out taller than the single-line action rows, which made the lists look ragged.

### Phone experience

- **Watch settings apply without a wake-up gesture**: phone-to-watch preference synchronization now belongs to the application process instead of whichever Settings screen happens to be open. Edits are coalesced, sent urgently and ordered so an older snapshot cannot overwrite a newer one; identical refreshes, removed values and string-set settings are also delivered reliably. The watch keeps observing changes while paused or in AOD, so the active layout is re-rendered without waiting for a tap or crown interaction.
- **Create and save your own watch themes**: the existing designs are now available as base layouts for reusable custom profiles. Pick Classic, Expressive, Poster, Studio, Material or another available layout, give the theme a name, then combine its typography, colors, artwork blur, dim and shading, AOD, progress, panels and mini-button appearance in the same live six-section editor. The new theme manager can apply, customize, duplicate, rename and delete profiles, clearly identifies the active theme and returns a newly created profile directly to its editor.
- **Layouts and backgrounds are independent**: every player layout can now use the artwork treatment originally authored for Classic, Expressive, Material, Poster, Studio, Vinyl, Halo, Aurora, Spectrum or Eclipse. Choosing a background no longer swaps the clock, metadata or control geometry, and each built-in/custom theme remembers its own selection and blur strength.
- **Independent, portable theme profiles**: editing a saved theme no longer changes the appearance stored for its base layout. The complete profile library stays on the phone while only one validated, atomic active snapshot is synchronized to Wear, keeping the payload bounded. Profiles, names and active selection are included in schema-3 configuration backups; legacy backups safely return to a built-in layout without corrupting an existing profile. Color resets are materialized explicitly so stale values cannot remain on a disconnected/reconnected watch.
- **Per-layout appearance**: every setting on the Watch tab (colors, overlays, always-on, panels, mini buttons) is now remembered *per layout*. Configure a layout the way you want, switch to another, and each keeps its own look; the live preview follows the selected layout. Existing setups carry over as each layout's starting point.
- **Truthful watch preview for the curated layouts**: the Watch tab miniature now reproduces what the watch actually renders — Aurora shows its real glass card (metadata inside, play focus on the corner) instead of a preview-only design, and every curated backdrop uses the same scrim math as the watch.
- **Finer shading control**: the shading strength is now a direct percentage (0–150 %, so you can go stronger than before) instead of three fixed levels, and the shading colour can follow the album accent, a desaturated tone or a custom colour rather than always being black. Background styles such as Expressive keep their designed look regardless of the "Dim album art" toggle, and a new "Apply this look to all layouts" action copies the current appearance onto every layout at once.
- **Everything in one backup**: configuration backups now also include your saved streaming shortcuts and your search and track history, restored together with the rest and re-synced to the watch. Older backups keep importing unchanged.

- **Archived options (developer mode)**: a new "Show archived options" switch in the developer section reveals watch layouts and settings archived because of known issues. The Vinyl, Halo, Aurora, Eclipse and Spectrum layouts are archived for now — a currently selected archived layout keeps working and stays listed.
- **Streaming shortcuts rebuilt for multiple services**: the shortcuts list and Add/Edit screen have been redesigned with clearer service and content labels, live link inspection, share/clipboard input, drag reordering, Open now, Copy link and Undo after deletion. Spotify, YouTube Music, Apple Music, TIDAL, Deezer, Amazon Music and SoundCloud links are recognized locally as tracks, playlists, albums, artists, shows, episodes or mixes. Text spacing was refined, the single BETA badge replaces the duplicated title suffix, all decorative marks follow the live accent, and filled actions choose black or white content automatically for contrast. Add shortcut and Paste link now share the same filled pill geometry; pick mode uses the shorter “Choose shortcut” title and Paste link now has a neutral grey treatment instead of the old green accent.
- **Streaming content inside Actions**: saved tracks, albums, mixes and playlists can now be assigned directly to the watch Actions menu and Quick Actions, alongside apps and normal commands. Pick Action now shows the installed destination music app's full-colour icon for each saved shortcut, with the generic playlist glyph only as a fallback. The Watch → Panels page also includes a live shortcut count and a direct link to the existing editor so this feature is easier to discover without creating a duplicate library.
- **Faster Streaming shortcuts on the watch**: shortcuts now use their own persistent Wear data cache, independent from queue and search responses. The watch renders the screen immediately from local data instead of waiting for a phone/Bluetooth round trip whenever it is opened.
- **More reliable streaming playback**: playable links first try the active app's MediaSession commands and then fall back to a correctly typed deep link. Spotify web links are converted to matching track, playlist, album, artist, show or episode app URIs instead of being treated as one generic playlist. Watch taps now carry the saved URI with the configured action and use the AndroidX Wear remote-activity bridge to open it on the paired phone, avoiding modern Android background-launch restrictions.
- **Modernized navigation icons**: Watch and Controls now use dedicated watch vibration and gamepad icons, and Buy Me a Coffee uses the supplied coffee artwork instead of the generic lightning bolt.
- **Cleaner icon picker**: the unrelated Haibane Renmei artwork is no longer offered as a configurable action icon.

### Watch experience

- **Colors that match the layout**: on the album-accent layouts (Expressive and the curated collection) the quick panel, volume, seek and queue surfaces now default to the album accent to match the player, instead of a neutral grey — still overridable per layout.
- **Wide, compact Up Next on the always-on display**: Expressive and Material AOD layouts use 88% of the available width while clamping the row to 44–52 dp, and the row now sits higher in the usable lower band without touching the center transport controls. It shows a low-emission monochrome queue glyph, title and artist, deliberately omits album artwork to save energy, and remains intentionally non-interactive while ambient.
- **Up Next no longer depends on opening Quick Actions**: queue data is loaded from the local Wear cache at startup, then refreshed and retained when playback or the current track changes. The AOD is populated without first opening the quick-actions panel and is not erased merely because ambient mode started.
- **Better metadata placement**: title and artist on the interactive Expressive and Material players sit comfortably between the clock and playback controls. Poster and Studio AOD remove the central play/pause circle and center the song title with the artist immediately below it.
- **Material track time restored**: the Material player now shows elapsed and total time whenever Track time is enabled, using the same transport-relative placement and mini-button clearance as Expressive; the phone preview mirrors that placement.
- **Artwork in interactive Up Next and queue**: Quick Actions and the playback queue show a compact cover whenever the media app supplies one, while keeping a text-only fallback. Covers are constrained to 30 dp inside the established row height so they can no longer stretch or distort their pills.
- **Expressive side buttons follow your Controls**: the previous/next buttons on the Expressive layout now run whatever you assigned to the left/right screen quadrants (and show that action's icon), falling back to previous/next when a quadrant is unassigned.
- **Overlays fully cover the screen**: the volume/seek/quick-panel backdrop no longer lets the clock, album art or side buttons bleed through near the edges.
- **Consistent top clock**: the Compose layouts (Expressive and the curated collection) now show the same straight top-center clock as Classic instead of a curved one hugging the bezel.
- **Poster/Studio layout stability**: the track title always occupies exactly two lines so the centered block no longer jumps between tracks, and the track time anchors to the bottom of the screen when no progress indicator sits above it instead of floating mid-air.
- **Art fade fix**: smooth artwork transitions now apply to layouts that draw the cover themselves (Poster full-bleed, Vinyl's label, Halo's disc) — they used to swap instantly, making the preference appear broken on Poster.
- **Background dim and fade now work on every layout**: the curated layouts (Vinyl, Poster, Studio, Halo, Aurora, Spectrum) scale their own backdrop treatment with the "Dim album art" preference — each adapted to its style — instead of ignoring it; smooth art transitions apply everywhere art shows. Eclipse stays deliberately pure black. Defaults reproduce the previous look.
- **Tap feedback on curated layouts**: tapping the play focus of a curated layout now flashes the same expanding ring the Classic layout shows.
- **Instant seek/volume backdrop blur**: the blurred overlay backdrop is applied immediately when the overlay opens instead of fading in.
- **Quick Actions behaves like the queue**: the panel uses the same round-screen row scaling and fixed curved bezel indicator, tighter app/playlist spacing, stable-height media pills widened to the Up Next row, and correct centering when an app exposes fewer than three actions. Metadata now follows the same top keyline as Expressive/Material and the three primary pills sit closer to the screen center. Its indicator now lives in a viewport overlay, so the full track stays attached to the circular bezel while the thumb follows the list; touch and crown motion use the same scale, alpha and easing response as the queue. A back swipe or a tap outside an action dismisses only the panel instead of closing the app.
- **Reliable Quick Actions icons**: full-colour launcher icons are preserved, monochrome vectors receive the correct surface tint, and transparent or corrupt media-app artwork falls back to a visible semantic glyph.
- **Quick panel style expansion**: Glass White, Glass Tonal and Outline Glass White join the panel picker with matching watch/phone previews. The original Outline stroke is now a thin keyline instead of a heavy border.
- **Queue styles restored per theme**: the queue resolves its style from the active built-in layout or custom-theme snapshot, so changing Queue style no longer appears to stop working after artwork thumbnails are added. Cover thumbnails now follow each style's geometry as well, from circular Glass/Tonal artwork through rounded Material cards to square Terminal artwork.
- **Cleaner empty playback state**: “Nothing playing” and its phone hint use tighter margins without extra font padding on the small Wear screen.

- **Clearer navigation and grouping**: the separate playing and stopped setup screens now live together under Controls, with swipeable "Music playing" and "No playback" tabs. Watch settings are grouped into Player, Background, Colors, Always-on, Panels and Mini buttons, while Settings is split into General, Behavior, Automation, Apps and Data & support. YouTube Music playlist shortcuts now live in Apps, leaving room for future Spotify and other streaming-service integrations.
- **Larger, responsive watch preview**: the preview gives the watch more room, adapts to the connected watch's shape and proportions, and no longer competes with explanatory copy beside it.
- **Contextual live preview**: changing a setting can now show the relevant watch surface immediately — including the player, ambient display, volume, seek, quick actions, queue and mini buttons — using live media information and the configured colors and icons whenever they are available.
- **Improved Actions editor**: the quick-actions panel and watch menu are clearly separated, the screen shows the configured action count and an empty state, and menu actions can be reordered by drag, keyboard or accessibility actions. Those configured rows now continue below Up Next as scrollable Quick Actions, including apps and saved streaming content.

- **Expressive seek setting**: the Expressive layout's touch-seek option (central ring, edge ring, or off) is now adjustable from the Watch tab instead of being fixed.
- **Notification popup settings restored**: the option to show a popup when Vibration Center for Wear vibrates the watch, and its auto-close timeout, are configurable again under Settings → Behavior.

### Watch customization

- **Expanded typeface library**: title and artist text on every player and AOD layout can use one of 18 typefaces, including rounded, light, ultra-thin, heavy, small-caps, casual, serif mono and multiple condensed styles alongside the existing families. Typewriter is archived and appears only when Show archived options is enabled in developer mode.

- **Rebuilt player layouts**: Vinyl, Poster, Studio and Halo were redesigned from the ground up for a watch-sized screen. Aurora, Eclipse AMOLED and Spectrum join them as new layouts alongside Classic and Expressive.
- **Artwork-driven designs**: the curated layouts use colors extracted from the current album art for their gradients, progress treatments and accents, with dedicated ambient-display variants and a true-black presentation for Eclipse AMOLED.
- **Mini buttons no longer break layouts**: Compose players place shortcuts in a compact lower-bezel row, remove conflicting lower chrome and make only the small collision-specific adjustments needed by Halo/Aurora. Neutral buttons inherit a layout-aware surface instead of one generic glass pill. Buttons configured for the paused state remain visible for a loaded paused track and disappear only on the truly empty playback screen or in AOD/overlays.
- **Simplified control styles**: the picker now exposes the four reliable choices — Balanced, Minimal, Compact and High contrast — and migrates legacy values safely. Player icon visibility remains configurable on Classic, Poster and Studio, while Material and Expressive retain their essential playback controls.
- **Layout-aware settings**: options that a selected player or always-on layout cannot render are hidden instead of appearing to be broken. Clock, track time, progress color, dynamic accent and blur changes now update consistently across the watch and its phone preview.
- **Independent metadata visibility**: song title and artist can each be hidden on every interactive and always-on player layout. Playback and error status messages remain visible even when metadata is disabled.
- **Universal edge progress and seek**: every layout can show or hide the edge progress ring and independently enable touch scrubbing on it. Expressive's central scrubber is suppressed only while edge touch seeking is enabled; a display-only edge ring can coexist without creating competing touch targets.
- **New Material and White pills**: volume and seek overlays gain Material pill and White pill options, both with consistent dark, high-contrast text.
- **Theme-aware overlay backdrops**: volume, seek and quick-actions surfaces now use blur, solid color or gradients appropriate to their selected visual style instead of always placing the same dark translucent blur underneath.
- **Independent color treatment per surface**: artist text, progress/seek, volume and Quick Actions can each follow the watch treatment or independently use Normal, Desaturated or Expressive color. Normal mode has a selectable custom color, and the same result is reflected in the phone preview and watch.
- **Distinct panel layouts with immediate sync**: volume, seek and Quick Actions now have genuinely different geometry options, not only different colors or bar skins. Layout and color changes are synchronized to the watch immediately instead of appearing only after another preference changes.
- **Cleaner Poster and Studio layouts**: the play button circle was removed from the center of the Poster, Studio and Halo faces so album artwork is no longer obscured. Poster and Studio now display the track title and artist name centered on the screen, filling the space previously occupied by the button.
- **Roomier Classic mini buttons**: Classic distributes its mini buttons across the available lower space instead of leaving them visibly cramped together.

### Controls and quick actions

- **Real app-provided quick actions**: up to three primary buttons mirror the current media notification's compact actions, labels and icons and execute the original notification buttons on the phone. MediaSession custom actions remain a fallback when notification metadata is unavailable.
- **Scrollable, watch-sized Quick Actions**: the panel restores large action bubbles and a full-width Up Next row, supports touch and crown scrolling, and can continue with configured watch-menu actions such as streaming shortcuts below the primary media controls. Action rows now match the Menu screen's soft-cornered pill style and the full Up Next width instead of imitating the queue's scaling list; a curved auto-hiding indicator replaces the straight scrollbar, and the redundant panel clock has been removed.
- **Back closes Quick Actions, not the app**: the standard Wear swipe-to-dismiss gesture is handled by the panel itself, restoring the player underneath instead of finishing the main watch activity.
- **Correct action count for each media app**: compact notification metadata is respected instead of filling missing slots with unrelated configured actions. Apps such as Spotify that expose two controls now show two centered buttons with no phantom third pill, while three-action apps keep a balanced row.
- **Cleaner control setup**: touch zones, swipe gestures and mini buttons use a more compact layout. Gesture and mini-button captions identify only their position, without repeating long assigned-action names below each icon.
- **Useful setup while disconnected**: touch zones, gestures and mini buttons remain configurable without a connected watch. The physical-buttons area explains whether the watch is disconnected or simply has no compatible buttons.
- **Accurate mini-button preview**: saving a shortcut refreshes the preview with the actual configured icon, including custom action icons.
- **Streaming shortcuts actually start playing**: instead of only opening a link, the phone now drives playback through progressively stronger contracts — an already-running session, the app's background media browser (so YouTube Music shortcuts and Liked Music play even with the screen off), then a visible fallback. Artist links play the artist by name (the one command both YouTube Music and Spotify honour) rather than only opening the profile page, and there is a one-tap "Play liked songs" for Spotify alongside the YouTube Music one.
- **Optional online shortcut artwork**: an off-by-default toggle downloads a thumbnail once for each saved shortcut from the service's public preview data (Spotify, YouTube, SoundCloud, Deezer) and shows it in the phone list, on the watch menu and on assigned buttons. It is the only online access besides the update check, and only the public share link is sent.
- **Editing a shortcut updates everywhere**: renaming or repointing a saved shortcut now updates every button, gesture, quick-panel slot and Actions-list entry already assigned to it, on the phone and the watch.
- **Tap to resume on "Nothing playing"**: tapping the idle watch screen sends a play command that resumes the last media app, like the phone's own play button.
- **Closes itself when idle**: the watch app now leaves the "Nothing playing" screen on its own after about a minute (new toggle, on by default; separate from the existing paused-track close timeout).
- **Crash-reporting choice**: Firebase Crashlytics reports remain enabled by default, but can now be disabled under Data & support → Privacy. Opting out stops the app's custom crash logs and removes queued unsent reports. Automatic upload remains disabled; finalized reports are sent manually only after the app has read the current privacy choice.

### Developer and support

- **Useful on-watch developer overlays**: developer mode can draw layout bounds and show live player diagnostics such as face, theme, playback state, position, duration, active overlay, color treatment and action count directly on the watch.
- **Correct support archive name**: Get support now exports `logs.zip` instead of `logs.logs_zip`.

### Fresh-install defaults

- **Fresh installs now start from a curated setup instead of an auto-detected guess.** The bundled first-run configuration — button/gesture mappings for both playback states, the action menu, appearance for every watch face, and the "Minimal" custom theme — now ships as a complete, portable snapshot. This also fixes a long-standing fragility: the previous bundled configuration used raw base64 Parcel bytes, whose layout isn't stable across Android versions, and could silently fail to seed on some devices; the new snapshot uses the portable format already relied on elsewhere.
- **Dynamic accent color, and its desaturated variant, are now on by default** in the phone app - the interface's accent color follows the current album art out of the box instead of requiring an opt-in toggle. Still fully optional: turn it off in Settings for a fixed or custom accent instead.

### Fixes

- **No false disconnected flash in Settings**: the app now waits for the first watch connection snapshot instead of briefly showing an incorrect state when Settings opens.
- **Stable, swipeable tabs**: section labels no longer jump vertically while settling, and pages can be changed with a horizontal drag — including drags that begin over the watch preview.
- **Smoother preview blur**: the phone preview's background blur no longer uses an overly coarse intermediate image that made it look pixelated compared with the watch.
- **No phantom play button in the Poster/Studio preview**: the phone preview drew a center play/pause button on these two layouts even though the watch never renders one there (tapping still toggles playback); the preview's Studio progress ring is also repositioned toward the bottom edge to match where the watch actually draws it.
- **Safer watch geometry**: the new player layouts account for mini-button offsets and screen shape so titles, progress and playback controls are less likely to overlap or be clipped.
- **Seekable Spectrum**: playback and timeline targets no longer overlap; the bars seek across their full width, expose an accessibility adjustment action, and preserve configured swipe gestures.
- **More reliable contrast**: dynamic tab, navigation and text colors keep a readable contrast against album-derived backgrounds.
- **Album art blur memory leak fixed (pre-Android 12 watches)**: on watches running Wear OS older than 3 (Android 11 and below), the software blur path allocated a new bitmap on every track change without recycling the previous one, progressively consuming memory until the system triggered GC pauses or an OOM kill. Each blurred bitmap is now explicitly recycled before its replacement is created.
- **Blur radius slider now has a visible effect on older watches**: the legacy software blur's downscale range was so narrow (50–58% of source size) that moving the radius slider produced almost no visible change. The range now spans 18–58%, so the full slider travel delivers a meaningfully different blur strength from subtle to heavy.
- **Blur edge artifacts eliminated (Android 12+ watches)**: the GPU blur (`RenderEffect`) was configured with `CLAMP` tile mode, which extends edge pixels outward and produces colored streaks at the screen border when blur radius is high. Switched to `DECAL`, which fills out-of-bounds areas with transparent instead.
- **Overlay tint now matches the player**: the volume, seek and quick-actions backdrops tinted from the album art used a narrower, more desaturated color band than the player layouts, so an overlay opened over a layout looked like a different, mismatched color. They now share the layouts' color band.
- **No blur "jump" when opening an overlay**: when the background was already blurred, opening a volume/seek/quick-actions overlay could make the blur pop to a different strength (the two used independent blur amounts). The overlay now reuses the background's blur when it is already blurred, and fades in instead of snapping, so the transition is smooth.
- **No stale color on track change**: album-tinted layouts and open overlays no longer show a new cover with the previous track's color for a frame — the cover and its extracted color are now applied together.
- **Steadier accent color**: a small, bright detail in a cover (a logo, a reflection) no longer hijacks the whole accent when the rest of the artwork is a different color; the dominant color is used unless the vivid one covers a meaningful share of the art.
- **Control style no longer changes the seek backdrop**: the seek overlay's background followed the selected control style, unlike the volume and quick-actions surfaces. It now behaves like them, independent of the control style.
- **Preview blur matches the watch**: the phone preview's background blur was far weaker than the watch's at the same radius; the slider now previews the same strength you get on the watch.
- **Quick Actions icons no longer garbled with some apps**: notification action icons delivered as tinted/monochrome or launcher-style adaptive-icon drawables (as YouTube Music's are) rendered corrupted on the watch. They are now rasterized in a way that accounts for both cases.
- **Dislike no longer crowds out useful shortcuts**: a thumbs-down/dislike notification action could take the quick-actions panel's wide slot ahead of more useful actions like skip or repeat. Lower-value actions like dislike are now deprioritized when a slot is chosen.
- **Expressive's redundant bottom row removed**: the queue/volume/menu trio that used to sit at the bottom of the Expressive layout was removed — mini buttons already cover the same shortcuts. Configured mini buttons and the layout's essential transport icons remain available under every supported control style.
- **More reliable mini buttons on every layout**: on Vinyl, Poster, Studio, Halo, Aurora, Eclipse, Spectrum and Expressive, mini buttons could settle at the wrong height after changing the shortcut offset, and Expressive's own track-time text could overlap them. Both now react to the row's real position instead of an approximation, including when the progress bar is shown or hidden.
- **Tighter text spacing on curated layouts**: title and artist text on Vinyl, Poster, Studio, Halo, Eclipse, Aurora and Spectrum had noticeably larger line spacing than intended. Spacing is tight again, matching the original design.
- **Consistent track-time display across layouts**: the "1:23 / 3:45" readout on curated layouts was smaller, dimmer and hidden whenever mini buttons were configured. It now matches Classic's size and full opacity, and stays visible under the same conditions Classic uses.
- **Tap feedback on every layout**: tapping the screen outside of a button only showed a ripple animation on the Classic layout. Every other layout now shows the same feedback.
- **Dozens of the new action icons rendered as a black glyph on a white square.** Their converted artwork kept a leftover full-tile background path; it's now stripped, and the queue-music icon that was missing from the picker is included.
- **The Chrono always-on face was redesigned.** It now shows a large clock with a small track title and the artist (with the app glyph) beneath — instead of a Classic-sized giant title, no artist and no app icon.
- **The Classic source-app icon follows the artist colour and isn't skewed.** It was tinted before the artist colour was applied (so it lagged a track behind) and forced into a square that distorted non-square icons; both are fixed.
- **Mini buttons no longer flash clipped when they appear**, then snap into place — the row stays hidden until it's positioned.
- **Reset this layout.** Watch face → Layout has a button that restores the current face's appearance to its defaults (and restores the previously-inaccessible "Apply this look to all faces" action).
- **Reset all faces to default.** A companion button next to "Reset this layout" restores every built-in face's appearance at once — the recovery path when the built-in themes have been changed and you want the original defaults back. It also switches off any active custom theme, while keeping your saved custom themes, behaviour settings and chosen face.
- **A malformed translated summary no longer crashes the Watch face tab.** Numeric settings use their own summary as a format string, so a single stray `%` in any translation crashed the whole screen while it was being inflated - before anything was shown. It now falls back to the unformatted text instead.
- **Devices without Wear OS support are told so.** On a phone whose Play Services has no Wear OS component, the app used to retry syncing forever and report "No watch connected", which no amount of pairing could fix. It now detects the condition, stops retrying, and explains what is actually wrong.
- **Artist shortcuts play from the watch menu.** Picking an artist shortcut from the watch's shortcut list opened the artist page without starting playback, while the same shortcut assigned to a button worked. The saved name is now passed along on both paths - it is what an artist actually plays from, since an artist URI only navigates.
- **Guide tab icons now follow the accent color** instead of always showing the static default green/sage.
- **"Show playing app icon" is now per-face**, like every other Watch face tab option. It used to gate whether the phone sent the icon to the watch at all - one global on/off no matter which face was active. The phone now decides whether to send it based on whichever face is actually active, so it can be on for one face and off for another.
- **Quick panel action icons no longer get stretched into a cover-art background.** When Queue style was set to a Cover variant (blur/tonal/square), a shortcut or action row with its own real icon (e.g. a YT Music or Spotify shortcut) had that icon blown up to fill the whole pill, the same treatment meant for the Up Next row's actual track artwork. Those rows now always keep a normal round icon; only Up Next (real cover art) honours the Cover style.
- **The Action editor's name box now follows the accent color.** Its cursor, selection handles and box outline were stuck on the static theme green, the same class of bug already fixed for the shortcut editor and theme name box - now it uses the same LyraAccent + boxStrokeColor pairing.
- **Expressive color treatment now actually delivers a multi-color palette.** Its secondary/tertiary colors were picked from the album's most-populated raw pixel swatches, which are frequently near-duplicate shades of the same dominant tone - whenever that happened, Expressive silently fell back to a single-hue derivation, making it indistinguishable from Normal far more often than a genuinely monochromatic cover would justify. It now prefers Palette's named tonal swatches (Vibrant, Muted, Light/Dark Vibrant, Light/Dark Muted), which are chosen specifically to be distinct from each other, before falling back to the raw ranking. Desaturated inherits the same underlying colors (just softened), so this fixes it too. Applies everywhere an accent is extracted from the cover: the player, the phone preview and the queue screen.
- **"Progress ring style" no longer disappears just because the edge ring's resting visibility is off.** The style picker (Watch face → Panels) was hidden whenever Watch face → Player → "Show edge progress arc" was off, but the ring still briefly appears whenever the user drags to seek - so there was no way to choose what that reveal looked like. It now stays available whenever the ring can appear at all, either because it's always visible or because drag-to-seek is enabled.
- **Square album art style no longer arrives pre-cropped on the watch.** The phone resizes every cover to the watch's (square) display before sending it, to save on the Bluetooth transfer - fine for every other style, but it defeated Square's entire point of showing the cover uncropped: a wide or tall cover had already lost its non-square edges before the watch's own letterboxing ever ran. The pre-transmit resize now preserves the original aspect ratio when Square is the active style, instead of center-cropping it to a square like every other style does.
- **The "Svartifoss active" notification now uses the app's own accent color** instead of the system's default grey.
- **New: a settings shortcut to manage the "Svartifoss active" notification.** Opens that notification's own Android channel settings directly - mute it, hide it from the lock screen, or otherwise adjust it. It can't be turned off entirely from inside the app, since Android requires an ongoing notification for as long as the background service that talks to the watch is running - but everything else about it is now one tap away, and it's isolated to its own notification channel, so adjusting it can't affect update, error or announcement notifications.
- **Fixed a watch state replay bug.** While the watch was disconnected or asleep, Google Play Services could queue up several updates to the same state (skipping tracks repeatedly, or hopping through themes/settings on the phone) and replay all of them on reconnect; the watch marched through every queued update one by one instead of jumping straight to the latest — visible as playback "catching up" through each intermediate track, or the watch face flickering through each intermediate theme, once you touched it. The watch now stamps each update with a running sequence and discards the older, replayed ones, so it lands on the final state directly, even when the queued updates arrive spread across several deliveries - covering watch appearance/theme changes as well as now-playing state.
- **Studio theme's default color treatment is now Expressive**, matching the other album-accent faces, instead of Normal.
- **Expressive's clock color now defaults to White**, matching its sibling album-accent faces, instead of Dynamic.
- **The character counter in "Create a new theme"/"Rename theme" now follows the accent color** instead of staying Material's default green.
- **Fixed text sitting off its radio dot in list-choice settings dialogs** (e.g. Track time display). The bundled Google Sans font's extra line padding pushed the label out of line with the platform radio indicator; every such dialog now renders on the same centerline.
- **Fixed "Update phone" potentially failing with "There was a problem parsing the package."** The download-size check added in 2.2.2 only caught a truncated transfer; content corrupted in transit without changing length (a re-encoding proxy, a bit flip) still reached the system installer as that raw, unlocalized failure. The downloaded update is now verified as a genuine, correctly-packaged APK for this app before it's ever handed off, and installed through the same robust PackageInstaller session already used for watch updates instead of a plain "open this file" request.
- **Users updating from a version older than 3.0 now see a one-time reminder to reset watch appearance.** Older versions shared one appearance setting across every watch face; updating in place could leave those old values bleeding into the per-face defaults 3.0 introduced, looking like a broken or mismatched style on faces never explicitly customized. The first launch after such an update now recommends resetting all faces to their new defaults, or clearing the app's data for a fully fresh start.

## 2.2.2

### Updates

- **Update the phone from inside the app**: when a new release is out, the Updates screen now has an "Update phone" button that downloads the phone APK and opens the installer — no more hunting for the file in the browser. (Needs the one-time "install unknown apps" permission, same as installing any sideloaded app.)
- **Redesigned Updates screen**: a status band tells you at a glance whether you're up to date, the phone and watch versions sit on their own card, and the release notes ("What's new") show right there — now rendered as formatted text instead of raw Markdown, with tighter spacing and icons that actually line up with their labels.
- **"Check for updates now" moved to the top of Settings** (its own category, above everything else) instead of being buried near the bottom.
- **Update-available icon next to Help**: a green icon appears in the toolbar whenever a checked release is newer than what's installed, and opens the Updates screen when tapped — so a dismissed or missed update notification isn't the only way to notice one is waiting.

### Watch appearance

- **New "Expressive pill" seek & volume style**: a colorful tonal pill in the album accent color with dark text on top (the system media player's tonal-pair look), instead of white text on dark glass. Applies to both the scrub-time readout while seeking and the volume-percentage readout while adjusting volume — that setting now styles both together.
- **New "Groove" volume style**: a recessed dark channel with a slim bright accent core, joining the existing volume-overlay styles.

### Fixes

- **"Update phone" failing to install ("problem parsing the package")**: the downloaded APK could come back truncated (a known Android networking bug with the redirect GitHub release-asset links go through), which was then silently handed to the installer instead of being caught. The download is now verified against the expected size before installing, and a bad download is deleted and reported so the button can be retried instead of failing confusingly.
- **Watch crash on Wear OS 5**: the watch app could crash on Wear OS 5 (e.g. Xiaomi Watch 2) while refreshing the media Tile / complication — the tiles library reads a system setting the platform now blocks for apps targeting API 35. These refreshes are best-effort again and can no longer bring the app down.
- **Finger scrolling in the watch menu with "Always select center action" on**: the menu could only be scrolled with the rotary crown, not by dragging, because that mode covered the list with an invisible tap target. Tapping now confirms the centered row without blocking scroll, so finger and crown both work.
- **Config backups restore across Android versions**: exported config/backup files are stored in a version-independent format and rebuilt on the importing device, so a backup made on one Android version restores correctly on another (the old format could fail to decode across OS versions). Existing backups still import.
- **Less crash-report noise**: expected, already-handled situations — a config snapshot that can't be decoded on a given Android version and falls back to defaults, routine coroutine cancellation, "phone not currently connected" — no longer get logged as crashes.

## 2.2.1

### Two new faces (Beta)

- **Vinyl (Beta)**: the album art becomes a slowly spinning record — groove rings, accent label and spindle hole — with the playback progress as an accent arc around the record's rim and glass prev/next buttons beside it. Tap the record to play/pause (it shows a play badge and stops spinning while paused); double-tap and long-press keep their usual quick-panel / queue roles.
- **Poster (Beta)**: a flat, typography-first look — a deep tonal backdrop derived from the album's accent, big two-line title with the artist in small caps, squared transport buttons and a straight progress bar with the track time under it.
- Both faces bring their own always-on display variant (thin outlines over black, burn-in safe), honor all the AOD element toggles/colors, show up in the phone's live Watch-face preview, and keep the mini buttons and quick panel exactly where the other faces have them. They're marked Beta: expect small visual adjustments based on feedback.

### New styles

- **Progress ring styles** (classic face / expressive "edge" seek): Solid, Dashed, Dots, Hairline, and Comet — a tail that fades in toward a bright head dot. Seeking works identically on all of them.
- **Three new volume overlay styles**: Segments (level-meter tick blocks), Aurora (multi-hue gradient) and Ink (wide translucent halo with a solid core).
- **Seek time styles** for the readout shown while scrubbing: Plain, Glass pill, Giant, or Position / total stacked.

### Fixes

- **Fixed the edge progress ring reappearing on the Expressive face after every always-on-display round trip**, even with the central or hidden seek mode selected: the ambient-exit restore ran while the system still reported ambient mode, so the face-specific cleanup silently no-oped.
- **The AOD title now follows the always-on display color mode**: with "album accent" (or a custom color) selected, the track title tints along with the outlines on every AOD style — it used to stay white.

### Other

- **The Tile (widget on Wear OS 7) gained a −10s / +10s seek row** under the prev/play/next buttons (thanks to the Pixel Watch 3 feedback!). The jump is resolved against the phone's live playback position, so it stays accurate even when the Tile's snapshot is stale.
- The persistent "controls active" notification now offers two explicit actions: **Stop** (same as tapping it, ends the controls) and **Force stop** (fully kills the app process and detaches the notification listener until the next reboot or access toggle).
- The watch's recents/launcher chip and the album-art complication now separate title and artist with "•" instead of "—".
- Firebase Analytics is back (anonymous usage stats, auto-collected events only) to help development — the app is sideload-only, so this and crash reporting are the only signals of how features are actually used. The README/landing-page privacy notes were updated to match.

## 2.2

### Updates without a cable

- **Update notifications on the phone**: Svartifoss now checks its GitHub releases about once a day (whenever music plays or the app is opened) and posts a notification when a newer version is out. Configurable in Settings under the new **Updates** section, including an opt-in to also be notified about **pre-releases**. No account, no extra permissions — a single small request to the GitHub API.
- **Update the watch from the phone, over Bluetooth**: the new update screen (tap the notification, or Settings → "Check for updates now") has an **Update watch** button that downloads the new watch APK and streams it straight to the watch — a notification appears on the watch, one tap opens the system install prompt, and that's it. No more ADB or Wear Installer for updates. First time only: allow "install unknown apps" for Svartifoss in the watch settings when prompted. (Requires both apps on 2.2+; this first jump onto 2.2 still needs the old sideload route.)
- The update screen also shows the installed phone *and* watch versions side by side (the watch now reports its version to the phone).

### Fixes

- **Fixed a crash when opening the Watch tab on recent Android versions** (`BadParcelableException` in the mini-button preview). Saved button configs are raw Android parcels, whose format isn't stable across Android versions — a config seeded or imported from a backup made on a different version could blow up the Watch tab on every open (reported on Android 15). All config reads are now hardened: an unreadable config is quarantined and the app falls back to defaults instead of crashing, config *imports* are validated up front so an incompatible backup is rejected with an error instead of silently planting a broken file, and config writes are atomic so a mid-write kill can no longer corrupt them.
- **Fixed the watch app closing right after launch (and refusing to open)** on some watches: the startup "is the phone app installed?" check could crash the app when Play Services threw, or silently close it into the install notice on a flaky first lookup. The check no longer takes the app down — only a *confirmed* "phone app missing" answer shows the notice.

## 2.1.1

### Watch

- **Configurable always-on display (AOD)**: a new "Always-on display" section in the phone's Watch face tab controls how ambient mode looks — pick the **style** (Follow face, Classic, Expressive, or Minimal) and toggle the **album art** (off = pure black background, a real battery saver on AMOLED), the **clock**, and the **track title/artist** individually. The existing ambient album-art opacity setting moved into the same section.
- **Expressive face now has its own AOD**: ambient mode no longer snaps the Expressive face back to the classic look. The new expressive AOD keeps the same layout — title/artist, prev/cookie/next, contour progress ring, and the queue/volume/menu pill trio — but rendered as thin outlines over black (the Wear OS 6 system media controls' AOD look): no fills, no animations, no marquee, burn-in protected by the same pixel jiggle, and deliberately few lit pixels so hours of AOD stay cheap on battery.
- **AOD outline color, brightness, and per-element toggles**: the outlines can stay white or take the album color (or a custom color — both lifted automatically so dark accents stay readable on black), an AOD brightness setting (20–100%) dims everything for extra battery savings, and the transport buttons, progress ring, and bottom pill trio can each be shown or hidden individually. Because the expressive AOD keeps the exact button positions of the awake face, the tap that wakes the watch lands with your finger already on the button you were aiming at.

### Phone

- Fixed the phone often failing to detect that the watch app was installed, incorrectly showing an "Install Svartifoss on your watch" prompt even when it was already there and working. The check ran on a long-deprecated, blocking Play Services API that frequently never completed on current Play Services builds. Also fixed that prompt's button opening a dead Play Store page on the watch (the app isn't on the Play Store) — it now opens the GitHub releases page instead, matching the equivalent fix already shipped on the watch side.

## 2.1

### Watch

- **New Expressive now-playing face**: alongside the classic layout, a new face mirrors the Material 3 Expressive system media controls — a soft "cookie" play/pause button (morphs to a circle when paused) wrapped in a progress ring that follows its scalloped contour, large round prev/next buttons in the album accent's tonal colors, an accent-tinted/vignetted album backdrop, and a queue/volume/menu glass trio at the bottom whenever no mini buttons are configured. Switch faces from Settings → Screen face; every gesture, button, overlay, and the quick panel behaves identically on both, and ambient mode always falls back to the classic, burn-in-safe look.
- **Expressive face touch seek**: a new setting picks how touch-seeking works on the Expressive face — drag the **Central ring** around the cookie button (with a live time readout), show the **Edge ring** (the classic bezel seek ring), or **None** (leave seeking to the rotary crown).
- **13 new overlay & queue styles**: the volume overlay, quick-actions panel, and queue screen each get an independent style picker (Settings → Screen face → Overlays & queue) — Glass, Minimal (AMOLED), Material, Tonal, Neon, Light, Gradient, Mono, Outline, Duotone, High Contrast, Terminal, and Frost.
- Eliminated watch UI freezes on track change: album-art and custom-list icon decoding, the complication's cover re-encoding, and the media session's cover transfer were all blocking the watch's single main thread on every music-state push — they now run off it, and an unchanged cover is skipped instead of redecoded.
- Fixed the watch's shuffle/repeat/like state rings getting stuck after a state update was wrongly deduplicated away, fixed a possible crash from a volume divide-by-zero, fixed the back key swallowing the configured back/dismiss action on watches that deliver it as a key event, fixed a config-backup import never actually reaching the watch, and fixed a few memory/listener leaks.

### Phone

- **New Watch tab**, in the slot the Guide used to occupy: a dedicated visual customization screen for the watch's now-playing screen, with a **live miniature preview** — face (Classic/Expressive), screen theme, album background (style/blur/dim), colors (dynamic accent, artist/progress color sources), and mini-button appearance/offset — previewed exactly as it will render on the watch and synced there on every change. While music plays on the phone, the miniature mirrors the **actual current track** (real album art, title, artist, the accent extracted from that art, and live playback — the progress ring and track time advance in real time). These appearance settings moved out of Settings, which now keeps only behavior options.
- **Guide moved to the toolbar**: the usage guide no longer takes up a bottom-nav tab slot — it now opens from a help button in the toolbar, freeing that slot for the new Watch tab above.
- The app is now fully **navigable without a paired watch**: every screen (Watch, Playing/Stopped controls, Actions, Settings) is reachable even with nothing connected — only the physical-buttons section of the button-config screens needs a live watch, and it already hides itself gracefully when there's no data. A dismissable banner in Settings clarifies that watch settings still save and sync even without a paired watch.
- **Portuguese (Brazil) translation completed for the phone app.** The watch app and shared module were already fully translated; the phone app's ~320 strings and option lists (themes, screen styles, etc.) are now translated too, so the whole app is consistent in pt-BR.
- **Support the project**: a link to [Buy Me a Coffee](https://buymeacoffee.com/gabrielsvafoss) in the app's navigation drawer (swipe in from the left edge), for anyone who wants to help out. The app stays fully free either way — nothing is gated behind it.
- The **Overlays & queue** style pickers (volume, quick panel, queue screen) moved up to right after **Screen face** on the Watch tab, closer to where they belong given how much they change the look.
- Fixed the "No watch connected" banner in Settings flashing on and back off immediately when a watch actually is connected.
- Fixed the Watch tab's live preview briefly showing the default accent color before switching to the real album-art color when a track loads.

## 2.0

The app is renamed **Svartifoss** (formerly Music Center for Wear / Lyra Player). Alongside the rebrand, this is the largest feature release since the 1.12 Wear OS modernization: a Compose-based watch UI overhaul, several new assignable actions, glanceable watch-face/Tile surfaces, and playlist shortcuts.

### Rebrand

- App renamed to **Svartifoss**; package IDs renamed `com.matejdro.wearmusiccenter` → `com.svartifoss.snfell` (mobile + wear) and `com.matejdro.common` → `com.svartifoss.snfell.common`.
- Version bumped to **2.0** (versionCode: mobile 28 → 29, wear 134 → 135).
- Wear `minSdk` raised 25 → 26 (required by the Tiles APIs used below).

### New actions (assignable to any button/gesture)

- **Play/Pause toggle**, **Stop**, **Restart track**, **Mute toggle** — four new one-tap actions for single-button watches.
- **Repeat-one** — direct one-tap on/off toggle for track looping, separate from the existing cycling `RepeatAction`.
- **Search** — opens voice/keyboard input on the watch and resolves the query against the playing app's `MediaBrowserService`; past searches are kept in a **search history** list that can be replayed or deleted from the watch.
- **Playlist shortcuts** — name + deep-link shortcuts managed on the phone (with an optional shuffle flag), reachable as a watch list or assigned directly to a button/gesture.
- **Play liked songs** / **Play liked songs, shuffled** — one-tap links into YouTube Music's "Liked Music".
- Recently-played history is now persisted to disk instead of living only in `MusicService` memory, so it survives service restarts.

### Wear glanceable surfaces

- **Media Tile**: ProtoLayout tile showing track/artist with prev/play-pause/next, tap to open the app.
- **Queue-preview Tile**: shows the next queued track, tap to skip (optional, toggled from the phone).
- **Watch-face complication**: current album art / title-artist, tap opens the app; supports short text, long text, small image and photo image complication types.
- **Rotary-crown seek**: optional setting where turning the crown scrubs the timeline instead of changing volume (debounced before hitting the Data Layer).

### Wear UI overhaul

- Full-screen Compose **menu** (actions menu and phone-pushed custom lists, including in-place delete of search-history entries) replacing the legacy `WearableDrawerLayout` drawer.
- Configurable **mini-buttons row** (up to 3 slots) and a **quick-actions panel** (3 round buttons + 1 long row), both assignable through the existing button/action pipeline, with per-item styling (curve, glass / solid / transparent background, neutral / album / custom color, offset).
- **Swipe gestures generalized**: up/down/left can each be assigned any action (right stays reserved for the system dismiss gesture).
- **Long-press the center screen** to open the queue directly; a new first-run overlay hints at the available gestures.
- New **screen theme** options (default/minimal/compact/cinema), album-art fade transition, album-art style (cover/blur/black-and-white/blur+bw/hidden) with blur radius and dim strength, ambient opacity, rotary dead-zone, and volume/seek overlay timeout — all new settings synced from phone to watch.
- **Configurable title text behavior**: the now-playing title's shrink/wrap/ scroll mix is now an explicit, exclusive choice — Automatic (the previous combined behavior), Scroll (marquee), Wrap to two lines, or Shrink to fit.
- **Independent artist text and progress bar colors**: previously both always followed the same album-derived accent as the icons/mini-buttons. Each can now be set separately to neutral (static theme accent), album-derived (optionally desaturated) or a fixed custom color.
- Shared `WatchTheme`/chrome (curved clock, curved scroll indicator, loading spinner) reused across the queue and new menu screens; the phone connection is now kept alive for the duration of any full-screen watch activity (menu, queue) instead of just the main screen.
- Portuguese (Brazil) localization added for the wear and common modules.

### Mobile UI

- New left navigation drawer with app/author info; toolbar title alignment fixed.
- Built-in icon picker grid for custom action icons; color-swatch preference rows for accent and custom colors, backed by a single source of truth for the live accent color ("Lyra" settings/color-picker/player redesign).

### Fresh-install defaults

- New installs now seed the button configs, action list and watch-behavior settings from a bundled default configuration (the same format used by Export/Import Config) instead of a generic auto-detected guess, so the app starts in a known-good, ready-to-use state.

### Bug fixes

- **Legacy drawer queue could still appear**: the guard only checked for an `activeQueueItemId`, which many apps never set on Android 10+, letting the old drawer queue slip through instead of the new `QueueActivity`. Now also blocked by list type (PLAYLIST/HISTORY).
- **Watch round-screen clipping in light mode**: the now-playing clock circle was cut off at the bezel edge; background drawable and button-config vertical spacing corrected.
- **Queue screen stuttered while scrolling**: each row's rounded-corner clip forced an offscreen `saveLayer` per row on every scroll frame; switched to drawing the rounded background directly. The now-playing equalizer and marquee title animations are also frozen while the list is actively scrolling, and the queue header no longer recomposes on every per-second position tick.
- **Current track name missing from the Wear OS recents card**: the app's task label wasn't being updated, so the app switcher only ever showed "Svartifoss" instead of the playing track.
- **Watch-face complication cover flashing back to the placeholder icon**: a complication refresh could land before the phone's album-art asset finished transferring over the Data Layer; the last successfully rendered cover is now cached and reused in that case instead of regressing to the placeholder.
- **Equalizer icon inconsistent across surfaces**: the static notification/ ambient equalizer glyph used different bar geometry than the animated "Up Next" icon and the queue's now-playing indicator; redrawn to match.
- **Saved button configs silently failing to load in release builds**: the proguard keep rule protecting `PhoneAction`'s reflection-based deserialization constructor still targeted the pre-rename package (`com.matejdro.wearmusiccenter`), so R8 shrinking stripped that constructor from every action class in release builds - any saved button config (including a fresh install's defaults) silently loaded empty. Fixed the rule to the current package.
- **Watch UI freezing on track changes (including inside the queue screen)**: several pieces of heavy work ran on the watch's main thread every time the phone pushed new music state - decoding the album-art bitmap (and custom list icons), the watch-face complication re-encoding its cover cache as a max-quality PNG, and the media session re-sending the full cover bitmap across binder on every state update (every volume step and seek included). All bitmap decode/encode now runs on background threads, an unchanged cover is recognized by its Data Layer asset id and skipped outright, and the media-session metadata is only re-sent when something in it changed. A track change also no longer runs the whole UI update pass twice (the state-only put and the follow-up state+cover put delivered the same state to every screen twice).
- **Progress ring reset animation restored**: the ring once again sweeps back smoothly on a track change instead of snapping to zero - the snap had been introduced on the mistaken theory that the sweep was the perceived lag.
- **Settings and button/menu config changed on the phone only reached the watch after interacting with it**: a watch setting (e.g. album-art blur), a button mapping, or the action-menu list edited on the phone would apply on the watch only after tapping the screen or turning the crown. Root cause: the phone pushed those DataItems to the Data Layer *non-urgently*, so the system batched them for power and could delay the sync by minutes - until unrelated urgent traffic (a control message sent by interacting with the watch) flushed the queue. Music state and notifications were already sent urgent; the button-config, action-list and preference pushes now are too, so they sync immediately. Complementing this, a manifest-registered `ConfigListenerService` applies an incoming button/menu-config change to an already-open now-playing screen even when the phone connection had gone idle, so it lands with no interaction needed.

## 1.12

Wear OS modernization initiative — bringing the watch app up to current platform standards (foundation, system integration, native surfaces) and starting the move to Jetpack Compose. Full roadmap in `docs/wear-modernization-plan.md`.

### Phase 0 — Foundation (wear)

- Target SDK bumped 30 → 34, with the platform changes that become enforced at that target handled so behavior is unchanged on current devices: explicit `android:exported` on the launcher activity + Data Layer listener services (Android 12), and a `mediaPlayback` `foregroundServiceType` + permission on `WatchMusicService` (Android 14).
- Re-enabled the `ExpiredTargetSdkVersion` lint (its suppression is now obsolete).
- Migrated the deprecated `AmbientModeSupport` to `AmbientLifecycleObserver`.
- Removed the dead legacy `support.wearable` `ConfirmationActivity` manifest entry (the androidx one was already in use).
- Declares + requests `POST_NOTIFICATIONS` (Android 13) so the foreground notification keeps showing.

### Phase 1 — System media integration (wear)

- New watch-side **MediaSession proxy** (`WatchMediaSession`): mirrors the phone's now-playing state (title/artist/art/position/playback + remote volume) and forwards transport controls back to the phone. The phone's playback now appears in and is controllable from the system **Media Controls** app and the Wear OS media surfaces — no app UI rewrite required.
- MediaSession flags and `setSessionActivity` now set so the Wear OS recents screen shows the currently playing track name under the app name.
- New watch→phone skip-next / skip-previous command channel (previously only toggle/seek/volume/quick-action existed).
- `WatchMusicService`'s foreground notification is now a MediaStyle notification bound to the session.

### Performance (wear)

- Cut control latency: every watch→phone command was re-resolving the phone node via a `getConnectedNodes()` round-trip on each press. The node id is now cached and reused, so button presses reach the phone noticeably faster.

### Queue redesign (wear)

- Introduced **Jetpack Compose for Wear OS** into the module (first Compose here; pilot for the broader UI modernization).
- Fully replaced the legacy `WearableDrawerLayout` queue with a new **`QueueActivity`** hosting a Compose `QueueScreen`:
  - `ScalingLazyColumn` of dark glass pills; the now-playing entry is highlighted with the album's lightened (pastel) accent colour.
  - Animated three-bar **equalizer** next to the playing track.
  - **Marquee** scrolling for long titles inside the pill.
  - Clock rendered as **`CurvedText`** along the top bezel, matching the Wear OS style; it fades out as the user scrolls down.
  - Thin curved **scroll indicator** on the right bezel — fixed thumb size (no erratic resize with the rotary crown), auto-hides 1.2 s after scrolling stops.
  - **Swipe-to-dismiss** closes only the queue (reveals the now-playing screen underneath); the system window animation is suppressed so the Compose transition plays cleanly without a double-close flash.
  - Google Sans used throughout to match the rest of the watch UI.
- Artist name on the now-playing screen and in the quick-actions panel now uses a HSL-lightened version of the album accent (dark colours, e.g. deep purple, become a readable pastel; black text always used in the queue).

### Bug fixes (mobile + wear)

- **Shuffle button always appeared active**: apps that never set their shuffle mode report `SHUFFLE_MODE_INVALID (-1)` which is not `SHUFFLE_MODE_NONE (0)`, so the comparison wrongly treated them as "shuffling". Fixed by checking for the explicitly-ON states (`ALL` / `GROUP`) instead.
- **Repeat button skipped "repeat one" on some apps** (e.g. Retro Music): `REPEAT_MODE_GROUP` is semantically "repeat all" but was falling through to the `else → NONE` branch in `RepeatAction`, bypassing repeat-one. Fixed.
- **Album art missing on Retro Music and other apps**: many apps on Android 10+ provide art as a `content://` URI rather than a raw `Bitmap` to reduce memory usage. Added URI fallback (`ALBUM_ART_URI` / `ART_URI` / `DISPLAY_ICON_URI`) with synchronous `ContentResolver` loading (network URIs are skipped to avoid blocking the main thread).
- **Like / favourite button not reflecting state on watch after toggling**: some apps don't immediately re-publish their playback state after handling a custom like action. A forced re-read of the state is now scheduled 500 ms after every like action so the watch button updates even in that case.

## 1.11

Dark "glass/acrylic" redesign of the watch UI, plus new playback features.

### Visual redesign (wear)

- New typeface (Google Sans) applied across the watch UI.
- Dark, minimalist "acrylic/glass" visual style replacing the old flat Material look (new `glass_card_background`, `glass_circle_background`, `queue_pill_background` drawables, shared glass color tokens).
- Redesigned circular volume control: left-edge vertical arc matching the stock Wear OS look, thicker stroke to match the new outline icons.
- New outline icons for volume up/down and the like button, redrawn as simple stroked shapes instead of outlining the old filled icon paths (which produced a messy/illegible result).
- Ambient (always-on display) mode improvements: blurred album art behind the clock instead of a flat dim, no black vignette, artist name shown in plain bold (no outline effect) while the title keeps its outlined look.
- Smart shrink-to-fit text sizing for long titles/artists (word-aware, falls back to marquee only when a title genuinely can't fit).
- Notification popup and queue/history list restyled to the new glass look.

### Seek bar

- New circular drag-to-seek progress bar around the now-playing screen, with a live time-remaining overlay while dragging.
- Position is interpolated locally between updates so the ring moves smoothly without spamming the phone connection.

### Like / shuffle / repeat

- New "Like" action: looks for a like/favorite custom action exposed by the currently playing app's media session (works with apps like YouTube Music and Retro Music that expose one).
- New "Shuffle" and "Repeat" actions, reading/writing real shuffle and repeat-mode state through the AndroidX media-compat layer (the bare framework `MediaController` API has no concept of either).
- Real shuffle/repeat state is now synced from the phone to the watch and reflected live on the quick-actions panel below.

### Quick-actions panel

- Double-tapping the center play/pause button opens a new panel with Like / Shuffle / Repeat buttons plus an "Up Next" shortcut into the queue - matching the stock Wear OS player's quick panel. Single-tap still toggles play/pause as before.
- Shows the current track's title/artist above the buttons.
- Shuffle/repeat buttons highlight with a color pulled from the album art when active; all three buttons flash that color on press.
- "Up Next" opens the real queue (regardless of the swipe-up preference) and previews the next track's name when a real queue is available.

### Queue / playback history

- New local play-history fallback: when the playing app doesn't expose a real skippable queue (common on Android 10+), the watch now shows a list of recently played tracks instead of an unhelpful error.
- Queue/history rows redesigned to match the stock Wear OS queue look: no album art thumbnails, title + artist on separate single (non- wrapping) lines, pill-shaped rows.
- Removed the old per-item dimming and circular curving effect from this list - it was designed for the old single-line icon rows and looked dated and "bent" against the new taller pill rows.

### Other fixes found along the way

- Fixed `OpenPlaylistAction` being mis-bound to the wrong (no-op) handler.
- Fixed the seek bar freezing/snapping back during a drag (a leftover position animator kept running underneath the touch).
- Fixed the seek bar losing an in-progress drag whenever the finger passed near a quadrant icon.
- Fixed cross-device position drift by converting the phone's `elapsedRealtime`-based playback position to a wall-clock timestamp before sending it to the watch.
- Fixed center-tap play/pause toggling losing touch events to the quadrant layer underneath it once double-tap detection was added.

## Earlier versions

See the GitHub release history for changes before 1.11.
