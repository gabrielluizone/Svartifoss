# Watch tab redesign plan

> **Status: proposed on 2026-11-12. Nothing in this document is implemented.**
>
> Written against `master` at `c2ac853`: six `Publish approved community themes` commits on top of
> Release 4.3.1 (`2fb4ab7`, phone 92 / watch 192). The checkout it was written in matches
> `origin/master`, but the owner's own machine may hold commits this one does not, so Phase 0
> re-checks every structural fact below before any code changes. Screenshots in `docs/images/`
> predate the current tab and are not used as evidence anywhere in this document. This file is both the design record and the execution plan for the implementing agent.
> `docs/ux-information-architecture.md` stays the compatibility contract and is amended in Phase 6.

## 0. How to use this document

For the agent executing it:

1. Read section 6 (the contract) before touching code. It lists what this redesign must not change.
2. Work one phase at a time, in order. Every phase leaves the app shippable. End each phase with the
   gate in section 12, then stop and report to the owner: there is no instrumented test setup, so
   the owner checks each phase on a phone and a watch before the next one starts.
3. Everything is referenced by symbol or file name, never by line number. The tree has moved since
   this was written.
4. Where the tree disagrees with this document, the tree wins for facts and the document wins for
   intent. Write the difference into the phase report; do not bend the tree to match the document.
5. Section 10 lists the decisions that belong to the owner. Each has a default. Use the default
   unless the owner has answered.
6. When a phase lands, update the status block at the top of this file.

## 1. Goal

The owner's problem statement: a person who has just installed the app knows nothing about it, and
the Watch tab is complicated for that person. A tutorial is explicitly not the answer.

The goal is that someone who has never seen the app can do four things from the first screen of the
Watch tab without reading help:

1. see what their watch will look like;
2. switch to a different look by pointing at a picture of it;
3. make the handful of adjustments most people make;
4. see where everything else lives.

An experienced user must lose nothing.

Checkable outcomes:

- The first screen of the tab is a short overview, not an editor page.
- Choosing a layout, a background, an always-on style or a panel style never depends on reading a
  list of invented names. Today those lists hold 15 layouts (22 with the archived ones shown), 71
  backgrounds, 19 always-on styles and about 30 styles for each panel.
- All 211 existing settings stay reachable with the same key, default, value encoding and scope,
  and settings search still lands on each of them.
- No preference key is added, renamed or removed. Nothing outside `mobile/` changes except docs.

Out of scope: a tutorial, coach marks or an onboarding wizard; what settings exist or how they are
stored; the Controls, Actions and Settings tabs; the watch app; Compose on the phone; restructuring
`WatchFacePrefsFragment` or `WatchPreviewView` beyond what a phase needs.

## 2. Where the tab stands today

Every fact in this section was read from the source tree. The screenshots in `docs/images/` predate
the current tab; Phase 0 re-captures whatever a decision needs.

### 2.1 Structure

`WatchFaceFragment` lays out, top to bottom (`fragment_watch_face.xml`, with a `layout-land` twin
that carries the same ids):

1. `watch_preview`: the live `WatchPreviewView`. Tapping it opens `WatchPreviewFullScreen`.
2. `watch_theme_card`: the active theme's name and "Built-in theme · X layout", opening
   `WatchThemesActivity`.
3. `watch_tabs`: a scrollable `TabLayout` with seven peers.
4. `watch_pager`: a `ViewPager2` of seven `WatchFacePrefsFragment` instances.

Every page inflates the whole of `res/xml/watch_face_settings.xml` and shows one contextual editor
surface. The legacy rows stay inflated and hidden: they own storage, validation, dialogs and search
metadata. `SettingsCatalog.WATCH_SECTIONS` decides which categories a page shows.

| Page (section key) | Editor surface | Model | Legacy rows behind it |
| --- | --- | --- | --- |
| Player (`style`) | `player_editor_surface` | `PlayerEditorModel` | 37 |
| Background (`background`) | `background_editor_surface` | `BackgroundEditorModel` | 15 |
| Colors (`colors`) | `color_editor_surface` | `ColorEditorModel` | 29 |
| Text (`typography`) | `typography_editor_surface` | `TypographyEditorModel` | 82 |
| Always-on (`aod`) | `aod_editor_surface` | `AodEditorModel` | 14 |
| Panels (`panels`) | `panel_editor_surface` | `PanelEditorModel` | 27 |
| Mini buttons (`miniButtons`) | `mini_button_editor_surface` | `MiniButtonEditorModel` | 7 |

That is 35 categories and 218 rows: 211 settings and 7 editor surfaces.

A few pickers behind those pages already show what they offer: Typeface (142 entries) draws each
name in its own font, the colour treatment shows swatches, the play and pause pickers show glyphs.
The style pickers are names only. Counted after `AppearanceOptionCatalog` and `PanelOptionCatalog`
merge their additions, and before archived values are hidden:

| Picker | Choices |
| --- | --- |
| Layout (`wear_screen_face`) | 22 registered, 7 archived, so 15 offered |
| Album art style | 71 |
| Always-on style | 19 |
| Progress ring style | 21 |
| Volume style / layout | 28 / 18 |
| Seek style / layout | 31 / 15 |
| Quick-panel style | 30 |
| Queue style | 29 |
| Panel backdrop | 65 |
| Mini-button shape | 22 |

A fresh install is seeded from `res/raw/default_config.json`: the Studio layout is active as a
built-in theme, and the library holds one saved theme, "Minimal" (Poster layout).

### 2.2 What a new user meets

**P1. The first screen is an editor, and the one decision that matters most is buried in it.**
The tab opens on the Player page. Choosing the layout, which decides everything else, is a row
called "Base layout" that opens a text list of names. The other route is the theme card, which
opens `WatchThemesActivity`: a list whose rows all keep the layout's palette glyph, because
`ThemeHolder` never binds `theme_icon`. Either way a layout can only be judged by applying it. The
app already has a picture-first browser, the community gallery's grid of live previews, but the
built-in layouts and the user's own themes never get it.

**P2. Every style choice is made blind.** 71 backgrounds with names such as Velvet, Fjord, Ink wash
and Nocturne. 31 seek styles. 65 panel backdrops. The preview shows a choice only once it is picked,
so comparing two styles means opening the list, picking, and opening it again.

**P3. Seven peer tabs in one scrollable rail.** Player, Background, Colors, Text, Always-on, Panels
and Mini buttons are seven `TabLayout` tabs in `tabMode="scrollable"` (`fragment_watch_face.xml`).
How many show without scrolling depends on the device width and the translated labels, so Phase 0
measures it on the owner's phone instead of assuming it. Each section already has a translated one-line
description and an icon (`WatchFaceFragment.sections`, `watch_section_*_description`), but only the
title is used. `SectionNavigationAdapter`, written to show them, is never constructed.

**P4. The editor gets what is left under the preview.** The toolbar, the preview, the theme card and
the tab rail sit above it; the mini player and the bottom navigation sit below. How much height is
left depends on the device and on whether the mini player is showing, so Phase 0 measures it.

**P5. Nothing says what an edit applies to.** Appearance is stored per layout
(`FaceScopedPreferences`), so a font chosen on Studio is not on Vinyl. That is the right behaviour,
and the screen never states it. The only hint is the card's subtitle.

**P6. One thing, several words.** The same concept is "Base layout" (the row), "layout" (the reset
and the card subtitle), "theme" (the card and the library), "Player appearance" (the card heading),
"Watch face" (the title of the watch's own picker). The toolbar says "Watch appearance", the tab says "Watch", the
first page says "Player".

**P7. One element, three pages.** The title's visibility, font and effects are on Text, its colour
on Colors, its alignment on Player. The clock: Player, Text, Colors. The progress ring: Player,
Colors, Panels. The pages are organised by property, which suits someone styling a whole theme and
not someone who wants to change "that clock".

**P8. The preview changes subject without saying so.** It switches to the volume overlay, the queue
or the always-on screen according to the control in focus (`surfaceForPreference`). That is useful
and unlabelled.

The per-page editors are not the problem. They were already rebuilt from flat preference lists into
contextual surfaces, and this plan keeps them. What is missing is a level above them.

## 3. Design

### 3.1 Principles

1. **Choose before tuning.** The first thing on the tab is picking a look, from pictures.
2. **Show, do not explain.** Thumbnails rendered by the real preview, swatches, fonts drawn in
   themselves, a caption on the preview. No coach marks, no first-run dialog.
3. **Three levels, no modes.** Home holds the essentials. Each area is one tap away. Inside an area
   the rare controls fold away. There is no global Simple/Advanced switch.
4. **Say what is being edited.** The active theme's name is on screen wherever something is edited.
5. **Nothing moves in storage.** A visual move is not a data migration (the rule
   `docs/ux-information-architecture.md` already sets). Every new control is a view over an existing
   `Preference`, the arrangement the seven editors already use.
6. **Reuse before adding.** Existing strings (each new one costs 44 translations), existing models,
   `EditorSettingRow`, `editor_group_card`, `item_settings_section`, and the preview's own ability
   to render a profile or a candidate value off screen.

### 3.2 The model the user is given

- A **theme** is a complete look. Themes are built-in (one per layout), saved ("My themes") or from
  the community.
- **Home** is the Watch tab's first screen: the watch, the themes, a few adjustments, and the list
  of areas.
- An **area** is one of the seven existing pages.

Code terms stay as they are: a theme is an `AppearanceContext` (`BuiltIn` face scope or `Custom`
profile), a layout is a face key in `wear_screen_face`, an area is a section, the thing the preview
is showing is a `PreviewSurface`.

## 4. Target experience

### 4.1 Home

```
┌────────────────────────────────────────────┐
│ ≡  Watch appearance            search bag ?│  MainActivity toolbar, unchanged
├────────────────────────────────────────────┤
│                 ╭────────╮                 │
│                 │ watch  │                 │  WatchPreviewView, pinned as today
│                 │preview │                 │
│                 ╰────────╯                 │
│                   Studio                   │  caption: theme name
├────────────────────────────────────────────┤  everything below scrolls
│ Watch themes               Manage themes › │
│ ╭────╮ ╭────╮ ╭────╮ ╭────╮ ╭──            │  thumbnails drawn by the real preview,
│ │    │ │    │ │    │ │ ✓  │ │              │  with the cover that is playing
│ ╰────╯ ╰────╯ ╰────╯ ╰────╯ ╰──            │  order: My themes, built-in, Community
│ Minimal Classic Express. Studio            │
│                                            │
│ ┌ QUICK ADJUSTMENTS ─────────────────────┐ │  Phase 3
│ │ Changes are saved to Studio            │ │
│ │ Watch color treatment     Expressive › │ │
│ │ Typeface                 Google Sans › │ │
│ │ Album art style          Blurred art › │ │
│ │ Always-on style          Follow face › │ │
│ │ ON SCREEN                              │ │
│ │ (Title) (Artist) (App icon) (Clock)    │ │
│ │ (Progress) (Controls)                  │ │
│ └────────────────────────────────────────┘ │
│ ┌ CUSTOMIZE ─────────────────────────────┐ │
│ │ ◐ Player                             › │ │  icon, title and the description
│ │   Choose the player layout and how…    │ │  every locale already translates
│ │ ▣ Background                         › │ │
│ │ ◉ Colors                             › │ │
│ │ T Text                               › │ │
│ │ ☾ Always-on                          › │ │
│ │ ▤ Panels                             › │ │
│ │ ⋯ Mini buttons                       › │ │
│ └────────────────────────────────────────┘ │
│   Reset this layout                        │
├────────────────────────────────────────────┤
│ mini player (while music plays)            │
│ Watch     Controls     Actions    Settings │
└────────────────────────────────────────────┘
```

Behaviour:

- **Theme strip.** Saved themes first, then built-in layouts in registry order, then one tile that
  opens the community gallery. Archived layouts follow the rule every other list uses: hidden unless
  "Show archived options" is on or the layout is the one applied. A tap applies the theme at once,
  as a row in `WatchThemesActivity` does, and shows a snackbar with **Undo**. The applied tile has
  an accent ring and a check. On open, the strip scrolls only as far as needed to show the applied
  tile. "Manage themes" opens `WatchThemesActivity`.
- **Quick adjustments.** Four rows that open the real picker of an existing setting, and a group of
  on/off chips for the elements on the player. A chip the current layout cannot honour is not drawn.
  The line under the heading names the theme being edited.
- **Customize.** The seven areas, each with its icon and description. A tap opens that area.
- **Reset this layout.** The existing action with its existing confirmation.

Home has no tab rail.

### 4.2 An area

```
│                 ╭────────╮                 │
│                 │preview │                 │
│                 ╰────────╯                 │
│               Studio · Volume              │  caption names the surface when it is not the player
├────────────────────────────────────────────┤
│ ←  PLAYER  BACKGROUND  COLORS  TEXT  ALWA… │  ← returns Home; the rail is kept for lateral moves
│ Style volume, seek, quick panel and queue… │  Phase 4: the section's one-line description
├────────────────────────────────────────────┤
│  the existing editor page, unchanged       │
```

- System Back and the arrow both return Home.
- The theme card is gone from this level: the caption carries the theme's name, and the editor gains
  the card's height.
- Swiping between areas works as today.
- A settings-search result opens the matching area directly, scrolled and pulsed as today. Back
  from there goes Home.

### 4.3 Visual pickers

```
┌ Album art style ───────────────────────────┐
│ ╭────╮  ╭────╮  ╭────╮                     │  each cell is the user's own watch
│ │    │  │ ✓  │  │    │                     │  with that one value changed
│ ╰────╯  ╰────╯  ╰────╯                     │
│ Cover   Blurred  Poster                    │
│ ╭────╮  ╭────╮  ╭────╮                     │
│   …                                        │
└────────────────────────────────────────────┘
```

Opened wherever the text list opened before. A tap commits and closes, as a list item did.

### 4.4 Touch the preview (Phase 5, optional)

Tapping the title on the preview opens a short sheet for the title: show or hide, typeface, size,
colour, and links to its full controls on Text and Colors. The same for artist, clock, track time,
progress, mini buttons, app icon and background. A row of element chips under the preview offers the
same sheets without aiming at a small picture, which is also the accessible route.

## 5. Architecture

### 5.1 Two modes in `WatchFaceFragment`

`WatchFaceFragment` keeps the preview and gains two sibling containers below it:

- `watch_home_container`: one child `WatchFacePrefsFragment` created with a new `SECTION_HOME`.
- `watch_editor_container`: the Home arrow, `watch_tabs` and `watch_pager`.

Mode is a visibility switch between the two, plus an `OnBackPressedCallback` enabled only in editor
mode. The pager's adapter is attached the first time editor mode is entered (or when state is
restored into it), so opening the tab still creates a single preference fragment.
Attaching the adapter late has to keep the saved-state handling `ViewPager2` gives it today, which
the rotation check in Phase 1 covers.

Both the Home fragment and the pager pages are direct children of `WatchFaceFragment`, so the
existing `parentFragment as? WatchFaceFragment` calls (`notifyPreviewInteraction`,
`onPanelEditorTarget`, `currentAlbumAccents`) keep working. This is why the pager is not moved into
an intermediate fragment.

Initial mode and section are decided by one pure function, `WatchTabNavigation.resolve(...)`:
restored state wins, then a search target (always editor mode), then what this process last showed,
then Home.

### 5.2 Home is a section of the existing preference screen

Home needs the real `Preference` objects: its rows open their dialogs, and those dialogs carry the
archived-option filters, the imported-font entries, the swatch feed and the validation that
`wirePreferences()` sets up. So Home is built the way the seven editors are:

- a new first category in `watch_face_settings.xml`, `cat_wf_home_editor`, holding three surface
  rows: `home_themes_surface`, `home_quick_surface` (Phase 3) and `home_areas_surface`;
- each a `TaggedEditorPreference` subclass with its own layout;
- `SettingsCatalog.WATCH_SECTIONS[SECTION_HOME] = setOf("cat_wf_home_editor")`, and the category
  added to `WATCH_CATEGORIES`.

Three rows and not one, because a `TaggedEditorPreference` rebuilds its whole view on `refresh()`.
With one surface, every chip toggle would rebind the theme strip and lose its scroll position.
Separate rows refresh separately: the strip on theme, library and artwork changes, the quick card on
preference changes, the areas list never.

The strip's adapter is held by the fragment, not created in the bind, and the bind re-attaches it
and restores the layout manager's state.

New Home code goes in a new file as `internal` extension functions on `WatchFacePrefsFragment`
(`WatchFacePrefsFragmentHome.kt`), not into the 5,600-line fragment. The helpers it needs
(`openPreferenceDialog`, `openColorPicker`, `choiceLabel`, `readStringPreference`, `editorPalette`,
`editorRowColors`, `inflateEditorGroupCard`, `newEditorChip`, `tintEditorChipGroup`,
`notifyPreviewInteraction`, `EditorPalette`) are widened from `private` to `internal`. No existing
editor is restructured.

### 5.3 Thumbnails come from the real preview

`WatchLookThumbnailer` owns one `WatchPreviewView` that is never attached to a window, and renders
it into bitmaps. The pattern is `SubmitCommunityThemeActivity.renderModerationPreview`: measure,
layout, `draw(Canvas(bitmap))`.

Two entry points, matching two things the preview can already do:

- `renderLook(profile)` uses `setThemeProfile(profile, useLocalArtwork = true)`: a whole theme, on
  the cover that is playing, with the sample title. Used by the theme strip and the library.
- `renderChoice(key, value)` uses `showPreference(key, value)`: the active theme with one candidate
  value, on the surface that key belongs to. This is the candidate mechanism the editors already
  use to paint a picked value on the docked preview before it is persisted, so a thumbnail and the
  result of picking it come from the same code. Used by the visual pickers. Follow it with
  `refresh()` so no candidate is left on the shared renderer.

One renderer instance means the cover's palette extraction and blur are computed once and shared by
every thumbnail. Rendering stays on the main thread, one bitmap per posted message, into an
`LruCache` sized in bytes. One render size is used for every consumer and scaled down by the
`ImageView`, so the renderer is laid out once.

A built-in layout becomes a profile through a new non-persisting
`WatchThemeRepository.previewProfile(prefs, context)`, a public wrapper over the existing private
`captureSettings`. The active theme is rendered through the same call with the active context, so
every tile in the strip shows the same sample title. A built-in tile shows that layout with the values
the user already has for it, which is what applying it gives.

Thumbnails exist only in memory. They are never written to disk or uploaded, and the moderation
preview path is not touched, so the privacy policy's statement about synthetic previews holds.

### 5.4 State that is only about this screen

Remembered mode and section stay where `lastSelectedSection` already is: static fields, for the
life of the process. If a later phase needs something that survives a restart (which "More options"
groups are open), it goes in a new named preferences file, `watch_tab_ui`. Never the default file:
that one is filtered into the watch snapshot and exported by backup. `ConfigBackup` lists its named
stores explicitly, so a new file is not swept in.

## 6. Contract

These hold in every phase. A change that needs to break one is out of scope: stop and ask.

1. **No preference changes.** No key added, removed or renamed in `MiscPreferences`,
   `FaceScopedPreferences.SCOPED_KEYS` or `EXPORTABLE`. No default, value encoding or scope changed.
   Nothing new is written to the default preferences file.
2. **`mobile/` only.** No edits under `common/`, `wear/`, `wearutils/`, `firebase/`,
   `.github/community-theme-publisher/`, `docs/themes/` or `docs/admin/`. The community-theme
   contract is not involved because no scoped key and no value vocabulary moves. The two Node suites
   are therefore not gates for this work.
3. **The legacy rows stay.** Every row in `watch_face_settings.xml` stays inflated with its key,
   title, summary and dialog. New controls call into them (`openPreferenceDialog`, or
   `callChangeListener` followed by setting the value, as `showCoverShapeDialog` does). Nothing
   writes around `FaceScopedPreferenceDataStore`.
4. **Search keeps its tuple.** A result is still `(tab, section, preferenceKey)`, the section is
   still one of the seven existing keys, and every row keeps exactly one owning section
   (`SettingsSearchRoutingTest`). `SECTION_HOME` owns no searchable row.
5. **The catalogue stays whole.** Every category in the XML is in `WATCH_CATEGORIES` and reachable
   from a section (`SettingsCatalogTest`).
6. **Applicability comes from the models.** A control is offered only where the layout, or the
   ambient style, can use it, and that answer is read from the owning model (`appliesToFace`,
   `isRevealed`, `appliesToStyle`). No new copy of a face list.
7. **Capture before switching themes.** Any code path that applies another theme calls
   `WatchThemeRepository.captureActive(prefs)` first. Edits to an active saved theme live in the
   `custom_active` scope until captured; applying another profile over them discards them.
   `WatchThemesActivity` does this in `onCreate` and `onResume`; the Home strip must do it itself.
8. **House rules still apply.** Dialogs are `AlertDialog.Builder` with `applyLyraDialogStyling`,
   never `MaterialAlertDialogBuilder`. An indeterminate wait is `MusicLoadingBarsView`, never a
   stock `ProgressBar`. A surface that tints itself sets `R.id.tag_handles_accent_locally` and
   tints from `editorPalette()`. A bitmap decode goes through `SampledBitmaps`.
9. **No new dependencies, no Compose on the phone.** Material Components already provide every
   widget used here.
10. **Every user-facing string exists in all 44 translated folders** before a phase is called done.
    See section 8.

## 7. Phases

Sizes are relative: S is a contained change, L touches several files and needs the most device time.

### Phase 0. Reconcile and baseline (S)

The owner's tree holds commits this plan has not seen.

1. Work on a new branch cut from the owner's current local HEAD. Do not rebase, reset or pull over
   unpushed work.
2. Confirm the build prerequisites the repository guide lists (`mobile/google-services.json`, the
   `wearutils` submodule, `JAVA_HOME`).
3. Re-run the inventory in Appendix A and compare with section 2.1. Then confirm these symbols still
   exist with the roles this plan gives them: `WatchFaceFragment.sections`,
   `WatchFacePrefsFragment.SECTION_*`, `applySectionVisibility`, `openPreferenceDialog`,
   `onDisplayPreferenceDialog`, `showLyraChoiceDialog`, `TaggedEditorPreference`,
   `EditorSettingRow`, `SettingsCatalog.WATCH_SECTIONS`, `SettingsSearchIndex.sectionTitle`,
   `WatchPreviewView.setThemeProfile`, `showPreference`, `PreviewSurface`,
   `WatchThemeRepository.captureActive`, `applyProfile`, `applyBuiltIn`, `captureSettings`.
4. Run the baseline gate from section 12 and record the result. A test that already fails is noted,
   not fixed here.
5. If a phone is attached over adb, capture the current Watch tab (each page, the themes library,
   one list picker) for the before and after comparison.
6. Report anything that changes this plan: a page added or removed, an editor already reshaped, a
   Home-like screen already started.

Done when: the report exists and the owner has confirmed the plan still fits the tree.

### Phase 1. Home: theme strip, areas, two modes (L)

The structural change. The seven editor pages are not edited.

**Layouts**

- `fragment_watch_face.xml` and `layout-land/fragment_watch_face.xml`, in step, with identical ids:
  - make `watch_preview_panel` a vertical stack of `watch_preview` and a new one-line, ellipsized
    `watch_preview_caption`;
  - remove `watch_theme_card` and its contents;
  - add `watch_home_container`;
  - wrap the rail and pager in `watch_editor_container` (initially gone), with a 48dp
    `watch_editor_home` image button before `watch_tabs`. Content description `@string/back`. The
    app ships four right-to-left locales and `ic_arrow_left` is not auto-mirrored, so give the
    button a mirrored drawable.
- `pref_home_themes.xml`: a header row (title `watch_themes_title`, text button
  `watch_theme_manage`) and a horizontal `RecyclerView`.
- `item_watch_look.xml`: an 84dp thumbnail with an applied ring, and a label of at most two lines.
- `pref_home_areas.xml`: a card headed by an alias of `watch_theme_customize`, an empty container
  for the seven area rows, and the reset row.

**Code**

- `WatchFacePrefsFragment`: add `SECTION_HOME = "home"`. `applySectionVisibility` needs no new
  clause, because the Home section's set contains only its own category.
- `watch_face_settings.xml`: add `cat_wf_home_editor` first, with `home_themes_surface` and
  `home_areas_surface`.
- `SettingsCatalog`: register the section and the category. `SettingsSearchIndex.sectionTitle`: add
  a branch for `SECTION_HOME`, since its `else` throws.
- New pure files under `view/watchface/home/`:
  - `WatchTabNavigation`: the mode enum and `resolve(...)` from 5.1.
  - `WatchLooks`: `sealed interface WatchLook` (built-in, custom, community link) and
    `build(profiles, allowedFaces, archivedFaces, showArchived, active)` returning the ordered list
    with exactly one applied entry.
  - `PreviewCaption`: `PreviewSurface` to label resource, exhaustive, and the rule that the player
    surface adds no suffix. Labels: `watch_section_style`, `watch_section_aod`,
    `watch_element_volume`, `watch_element_progress`, `watch_element_quick_panel`,
    `watch_element_queue`, `watch_element_lyrics`, `watch_section_mini_buttons`. The joined form
    uses the existing `watch_theme_custom_subtitle_authored` (`%1$s · %2$s`).
- `WatchLookThumbnailer` and `WatchLooksAdapter` as in 5.3.
- `WatchThemeRepository.previewProfile(prefs, context)`: non-persisting, no library write.
- `WatchPreviewView`: report surface changes. Make the private `surface` field notify an
  `onSurfaceChanged` listener when its value changes; every setter already writes through it.
- `WatchFaceFragment`:
  - mode switching, the back callback, lazy pager attach, the static last mode;
  - a single `activeSection` used by `onWatchPreferenceInteraction` and `onPanelEditorTarget`
    (`SECTION_HOME` in Home mode);
  - `updateThemeCard()` becomes `updateCaption()`; `onThemeLibraryChanged()` also refreshes the
    strip;
  - `openThemeLibrary()` extracted from the card's click (it keeps the `captureActive` call), and
    `openCommunityGallery()` reaching the launcher `MainActivity` already has, so the result
    refreshes this tab;
  - `applyLook(look)`: `captureActive`, then `applyProfile` or `applyBuiltIn`, then the snackbar
    with `watch_theme_apply_success` and an Undo action (`quick_panel_editor_undo`) that re-applies
    the previous look. Announce the change for accessibility, as the library does;
  - feed the thumbnailer the cover (`pushNowPlaying`) and the device shape (`setDeviceProfile`),
    and invalidate the active look's thumbnail, debounced, from the existing `prefListener`;
  - the preview's horizontal swipe keeps switching areas in editor mode and does nothing on Home.
- `WatchFacePrefsFragmentHome.kt`: `bindHomeThemes` and `bindHomeAreas`. The area rows are inflated
  from `item_settings_section.xml` with the titles, descriptions and icons `WatchFaceFragment`
  already declares. The reset row is `EditorSettingRow.bindLink` over the `reset_appearance`
  preference and invokes its click listener, as the Player editor's reset button does.

**Tests**

- `WatchTabNavigationTest`: restored state wins; a search target forces editor mode; a fresh process
  opens Home; an unknown section falls back to Home.
- `WatchLooksTest`: saved themes lead; archived layouts are hidden unless applied or revealed; the
  community tile is last; exactly one entry is applied.
- `PreviewCaptionTest`: every `PreviewSurface` has a label; the player surface adds none.
- `res/WatchTabStructureTest`: both `fragment_watch_face.xml` files declare the same ids;
  `cat_wf_home_editor` is the first category in the XML; no Home layout contains a `ProgressBar`.
- Registries: wherever the seven existing `*_editor_surface` keys are listed under
  `mobile/src/test` (`SettingsSearchRoutingTest.NON_SEARCH_ROWS`,
  `AppearancePreferenceScopingTest`, `WatchPreviewParityTest`), add the Home surface keys. Each of
  those tests fails with a message naming the key when one is missed.

**Done when**

- Opening the tab shows Home: preview, caption, theme strip with real thumbnails, seven areas.
- A tile applies its theme on the phone and the watch, and Undo restores the previous one, including
  a saved theme with edits made a moment earlier.
- Each area opens the page it opened before, unchanged. Back and the arrow return Home. Rotation
  keeps the mode and the page.
- A search result still lands on its row.
- Opening the tab is not slower than before the change (time `WatchFaceFragment.onViewCreated` to
  first frame before and after, on the same phone).

### Phase 2. Visual pickers (M)

Do this before the quick adjustments: those rows open these pickers, and a "simple" row that opens
71 names is not simple.

- `VisualChoiceCatalog` (pure): the keys whose list dialog becomes a grid. Start with
  `wear_screen_face`, `album_art_style`, `wear_aod_style`, `wear_progress_style`,
  `wear_volume_style`, `wear_volume_layout`, `wear_seek_style`, `wear_seek_layout`,
  `wear_quick_panel_style`, `wear_queue_style`, `screen_buttons_shape`, `screen_buttons_bg_style`.
  Not in it: any picker that is already visual (`DescribedListPreference`,
  `ColorTreatmentPreference`, `TransportGlyphPreference`, `AppIconStylePreference`) and the cover
  shape pickers, which have their own dialog with "Hidden" as its first entry.
- `WatchFacePrefsFragment.onDisplayPreferenceDialog`: for a `ListPreference` whose key is in the
  catalogue, show the grid dialog and return. It reads `preference.entries` and `entryValues` after
  `applyArchivedOptionFilters()` has run, so archived options behave as they do in the list.
- The grid dialog: `AlertDialog.Builder`, a `RecyclerView` with a three-column grid, cells rendered
  on bind through `WatchLookThumbnailer.renderChoice(key, value)`, a ring on the selected cell, the
  entry label under each. Commit through `callChangeListener` and the preference's own `value`.
  Choice thumbnails are cached for the lifetime of the dialog only.
- `WatchThemesActivity`: replace the generic `theme_icon` with the theme's thumbnail, from its own
  `WatchLookThumbnailer`.
- Stretch, once the simple keys work: the Background editor's layer style pickers. They call
  `showLyraChoiceDialog` directly, so they are not reached by the interception. The candidate is the
  encoded stack with that one layer's style replaced, rendered through
  `renderChoice(wear_background_layers, encoded)`.

**Tests**

- `VisualChoiceCatalogTest`: every key is a plain `ListPreference` row in `watch_face_settings.xml`
  and none is one of the excluded classes.
- A pure test for the candidate-stack builder if the stretch item is done.

**Done when**

- Each listed picker shows thumbnails that match what the preview shows after picking.
- A layout picked while a saved theme is active changes that theme's layout, as the list did.
- The 71-style grid scrolls without visible stutter on the owner's phone.
- The themes library shows a thumbnail per row.

### Phase 3. Quick adjustments (M)

- `pref_home_quick.xml` and the `home_quick_surface` row, between the strip and the areas.
- `WatchHomeModel` (pure):
  - rows: `wear_color_treatment`, `wear_font`, `album_art_style`, `wear_aod_style`. Titles and
    current values come from the real preferences, as on the Player page. When the treatment is
    Normal, the row is followed by the `wear_normal_color` swatch, the pairing the Colors page uses,
    through `openColorPicker`.
  - chips: `wear_show_track_title`, `wear_show_track_artist`, `wear_show_source_icon`,
    `always_show_time`, `wear_edge_progress_visible`, `wear_classic_icons_visible`. Short labels
    from existing strings (`category_wf_typography_title`, `category_wf_typography_artist`,
    `category_wf_typography_icon`, `category_wf_clock`, `watch_element_progress`,
    `controls_header`); the preference's full title is the content description, the arrangement
    `AodSettingSpec.labelRes` documents.
  - `appliesToFace` delegates to the owning model. Only the controls chip is gated today
    (`PlayerEditorModel.PLAYER_CONTROLS_FACES`).
- Heading: new string `watch_home_quick_title`. The line under it: new string
  `watch_home_scope_note` with the theme's name. The chip group's heading: the existing
  `player_section_on_screen`.
- Rows call `openPreferenceDialog(key)`, so they get the visual pickers of Phase 2 and report the
  interaction to the preview. Chips commit through the preference
  (`callChangeListener`, then `isChecked`), then refresh only this surface.
- Mini buttons are deliberately not here. Their visibility is a four-way choice, and the row draws
  nothing until actions are assigned on the Controls tab; a control with no visible effect on a
  screen meant to be obvious would be the wrong first impression. The Mini buttons area keeps both
  the choice and the link to Controls.

A note for whoever reads `PlayerSlot`'s documentation: the Player page dropped its "On screen" chips
because a chip has no room for the sentence that explains a setting. That reasoning stands. These
chips are a summary one level above; each mirrors a row that still carries its description on its
own page.

**Tests**

- `WatchHomeModelTest`: every key is declared in `watch_face_settings.xml`; every key belongs to
  exactly one of the seven editor models; every chip has a label; `appliesToFace` agrees with the
  owning model for every registered layout.

**Done when**

- Each row opens the same picker as its page and updates the preview and the watch.
- Chips follow the layout: the controls chip is absent where the layout draws no controls.
- The scope line names the active theme and follows a theme change.
- Both new strings are present in all 44 translated folders.

### Phase 4. Calmer editor pages (S)

Small on purpose. The pages are already contextual; what they lack is an introduction and a way to
put rarely used groups out of the way.

- One line under the tab rail in editor mode showing the selected section's description, updated in
  `onPageSelected`. The Settings tab already does this (`settings_section_description`).
- Text: the shadow, outline and backdrop controls of the selected target fold behind one
  "More options" row (`quick_panel_editor_more`). Open by default when any of the three is switched
  on for that target.
- Colors: the accent source, tone and hue controls of the global card fold behind the same row. Open
  by default when any of them differs from its definition default.
- A search result that targets a folded control opens the fold before pulsing it
  (`TypographyEditorModel.searchTargetFor`, `ColorEditorModel.searchTargetFor`).
- The fold state is kept per fragment instance and in saved state. Nothing is persisted.
- Background's layer list is not folded: the shading that keeps text readable lives there.

**Tests**

- Extend `TypographyEditorModelTest` and `ColorEditorModelTest` with the pure rule that decides
  whether a fold starts open, and with the set of controls each fold holds.

**Done when**

- Both folds open and close, start open when their content is in use, and open for a search result.
- The description line follows the selected page, including when the page is changed by swiping.

### Phase 5. Touch the preview to edit (L, optional)

Decide after living with phases 1 to 4. This is the answer to P7 that does not move a single setting
between pages.

- `PreviewHitMap` (pure): records a rectangle per element during a draw, and answers which element
  is under a point (the smallest containing rectangle wins; otherwise the background).
- `WatchPreviewView` records into it at the functions every layout already draws through:
  `drawTitlePlan`, `drawArtistLine`, `drawTrackTimeText`, `drawSmallClock` and `drawFaceClock`,
  `drawEdgeSeekRing`, `drawMiniButtons` and `drawChatMiniButtons`, `drawSourceGlyph`. No per-layout
  code. Recording is off for the off-screen renderer.
- `WatchElementModel` (pure): element to the short list of existing keys its sheet shows, and the
  areas it links to. Title: `wear_show_track_title`, `wear_title_font`, `wear_title_font_scale`,
  `wear_title_color_mode` and its custom colour; links to Text and Colors. The other elements follow
  the same shape from the existing models.
- The sheet is built by the visible `WatchFacePrefsFragment` from `EditorSettingRow`s over the real
  preferences. Its links call the same route a search result takes.
- A row of element chips under the caption opens the same sheets.
- The tap that opens the full-screen preview moves to a small expand button on the preview's corner.
  This changes an existing gesture and needs the owner's agreement (decision D5).

**Tests**

- `PreviewHitMapTest`; `WatchElementModelTest` (every key exists and is owned by one editor model;
  every link is a real section).

**Done when**

- Tapping each element on at least Classic, Studio, Split and Chat opens the right sheet.
- The chips reach every sheet without touching the preview.

### Phase 6. Documentation and release (S)

- `docs/ux-information-architecture.md`: the Watch section gains the Home level; the search contract
  paragraph notes that a result opens editor mode.
- `CLAUDE.md`, *Phone app UI*: describe Home, the two modes, the thumbnailer, the visual pickers,
  and the new tests, in the style of the surrounding text. Check `AGENTS.md` still agrees.
- `README.md` and `docs/index.html`: the paragraph that describes the Watch tab.
- `CHANGELOG.md`, and the Play release notes in both modules when the release is cut.
- Screenshots to retake, since they predate the current tab: the
  `gallery-phone-*-tab-*.jpg` set, `phone-watch-tab.jpg`, `phone-watch-tab-styles.jpg`,
  `phone-themes.jpg`, `phone-themes-library.jpg`, and the style-list shots
  (`phone-albumart-style.jpg`, `phone-aod-style.jpg`, `phone-panel-style.jpg`,
  `phone-queue-styles.jpg`, `phone-seek-styles.jpg`, `phone-volume-style2.jpg`).
- Remove `SectionNavigationAdapter` if it is still unused. Keep `SectionNavigationItem`.
- Optional, no new string: a "No watch connected" line (`setting_no_watch_banner`) at the top of
  Home while `MainActivity.connectedWatchInfo()` reports none, so a person understands why the wrist
  is not following.

## 8. Strings

Each new user-facing string must be added to `mobile/src/main/res/values/` and to all 44 translated
folders, six of which use the BCP-47 form (`values-b+id`, `values-b+fil`, `values-b+he`,
`values-b+zh+Hans`, `values-b+zh+Hant`, `values-b+ckb`). Translate properly, using the words that
locale already uses for "theme" and "layout" in its `watch_themes_strings.xml`. Do not paste English.

The whole plan needs two:

| Name | English | Phase |
| --- | --- | --- |
| `watch_home_quick_title` | Quick adjustments | 3 |
| `watch_home_scope_note` | Changes are saved to %1$s | 3 |

Everything else is an existing string. Where a new name reads better at the call site, declare it
`translatable="false"` pointing at the existing one, as the `player_section_*` aliases do:

| Use | Existing string |
| --- | --- |
| Strip heading | `watch_themes_title` |
| Strip action | `watch_theme_manage` |
| Community tile | `online_themes_title` |
| Applied snackbar | `watch_theme_apply_success` |
| Undo | `quick_panel_editor_undo` |
| Tile descriptions | `watch_theme_row_description`, `watch_theme_row_description_applied` |
| Areas heading | `watch_theme_customize` |
| Area titles and descriptions | `watch_section_*`, `watch_section_*_description` |
| Home arrow | `back` |
| Chip group heading | `player_section_on_screen` |
| Fold row | `quick_panel_editor_more` |
| Caption join | `watch_theme_custom_subtitle_authored` |

A missing translation does not fail a debug build. It fails `lintVital` during a release assemble,
so check with the loop in section 12 before calling a phase done.

No existing string's text changes in this plan. Renames are decision D6.

## 9. Risks

| Risk | Containment |
| --- | --- |
| The tree differs from what this plan describes | Phase 0, and references by symbol |
| Home adds a heavy preference fragment | The pager attaches lazily, so the tab opens with one instance; measured in Phase 1 |
| The strip loses its position or flickers on refresh | Three separate surface rows; the adapter lives in the fragment, not the bind |
| Off-screen rendering of a 14,700-line view is slow or wrong | One shared renderer, one bitmap per message, an existing precedent; the owner checks a fling in Phase 2 |
| Switching theme from the strip drops unsaved edits | Contract rule 7, and the Undo check in Phase 1 |
| Two layout files drift | `WatchTabStructureTest` compares their ids |
| A registry is missed when rows are added | The four structural tests name the missing key |
| Power users lose one-tap access to their page | The last mode and page are remembered for the process; the rail stays in editor mode |
| Right-to-left layouts | Mirrored arrow; the strip and rail follow layout direction; the owner checks one RTL locale |
| Screenshots and docs go stale | Phase 6 lists them |

## 10. Decisions for the owner

Defaults apply when there is no answer.

- **D1. Home as a level, or Home as a first tab.** Default: a level, as drawn. The alternative keeps
  the rail visible everywhere and adds a "Start" page before Player. It is cheaper and leaves the
  seven-tab rail on the first screen, which is the thing a new user is confronted by. The Home
  surfaces are identical either way; only `WatchFaceFragment`'s navigation differs, so this can be
  changed late.
- **D2. Where the tab opens when the user comes back to it.** Default: where it was left, within the
  same process; Home on a fresh process. Alternative: always Home.
- **D3. Thumbnails on the playing cover or on the sample art.** Default: the playing cover, falling
  back to the sample when nothing plays. It shows what each layout does with the user's own music.
- **D4. What folds in Phase 4.** Default: text effects, and the three colour fine-tuning controls.
- **D5. Phase 5's gesture change.** Default: Phase 5 is not started without a yes.
- **D6. Renames.** Default: none. Candidates if wanted later, each costing 44 translations:
  "Base layout" to "Layout"; one word for the tab and the toolbar.
- **D7. The first tab the app opens on.** It is Controls today (`MainActivity`). The Watch tab,
  once it opens on Home, is the friendlier first screen. Default: unchanged; outside this plan.

## 11. Alternatives considered

- **A global Simple/Advanced switch.** A mode hides things without saying where they went, doubles
  what has to be checked, and makes a search result land on a hidden row. Local folds do the same
  job where the user is looking.
- **A first-run wizard or coach marks.** Excluded by the owner, and it would explain the screen
  instead of fixing it.
- **Reorganising the seven pages by element.** It would break the search tuple, the seven models and
  their tests for a benefit Phase 5 delivers without moving anything.
- **Rebuilding the tab in Compose.** The phone has no Compose, the preview is a View, and the
  dialogs belong to the preference framework.
- **Live `WatchPreviewView`s as strip tiles**, as the community cards use. Each instance would
  extract the cover's palette and blur it again. One renderer and cached bitmaps cost less and
  scroll as plain images.

## 12. Verification

Gate for every phase:

```
./gradlew :mobile:testGithubDebugUnitTest :mobile:testPlayDebugUnitTest :common:testDebugUnitTest
./gradlew :mobile:assembleGithubDebug :mobile:assemblePlayDebug
```

Before a release, `./gradlew test` and a release assemble, which is where `lintVital` runs. `lint`
itself is not a usable gate in this repository.

After a phase that adds strings, every name must be reported by 45 files:

```
for name in watch_home_quick_title watch_home_scope_note; do
  printf '%s: ' "$name"
  grep -l "name=\"$name\"" mobile/src/main/res/values*/*.xml | wc -l
done
```

Contract checks, expected to print nothing:

```
git diff --stat <phase-base> -- common wear wearutils firebase .github docs/themes docs/admin
git diff <phase-base> -- common/src/main/java/com/svartifoss/snfell/common/MiscPreferences.kt
```

Device checks for the owner after each phase, on a phone with a paired watch:

1. Open the Watch tab cold and warm. Home appears; nothing flashes.
2. Apply a built-in theme and a saved theme from the strip. The watch follows. Undo each.
3. Edit a saved theme, switch to another from the strip, switch back. The edit is still there.
4. Open each area, change one setting, go Home, reopen. The value held and the watch shows it.
5. Rotate in Home and in an area. Search for a setting in each of three areas.
6. Dark and light phone theme; a right-to-left language; a large font scale; TalkBack across Home.
7. From Phase 2: each visual picker against the preview after picking; fling the background grid.
8. Play music, change track, pause: preview, caption and thumbnails follow.

## Appendix A. Inventory script

Prints the categories and rows of the Watch settings screen. Run from the repository root.

```
python3 - <<'EOF'
import xml.etree.ElementTree as ET
A = '{http://schemas.android.com/apk/res/android}'
root = ET.parse('mobile/src/main/res/xml/watch_face_settings.xml').getroot()
total = 0
for cat in root:
    rows = list(cat)
    total += len(rows)
    print(cat.get(A + 'key'), len(rows))
    for row in rows:
        print('   ', row.tag.split('.')[-1], row.get(A + 'key'))
print('rows:', total)
EOF
```

Expected at `c2ac853`: 35 categories, 218 rows.

## Appendix B. File map

Existing files this plan edits:

| File | Phases |
| --- | --- |
| `mobile/.../view/watchface/WatchFaceFragment.kt` | 1, 4, 5 |
| `mobile/.../view/watchface/WatchFacePrefsFragment.kt` | 1, 2, 4 |
| `mobile/.../view/watchface/WatchPreviewView.kt` | 1, 5 |
| `mobile/.../view/watchface/theme/WatchThemeRepository.kt` | 1 |
| `mobile/.../view/watchface/theme/WatchThemesActivity.kt` | 2 |
| `mobile/.../view/settings/SettingsCatalog.kt`, `SettingsSearchIndex.kt` | 1 |
| `mobile/.../view/mainactivity/MainActivity.kt` | 1 |
| `mobile/src/main/res/layout/fragment_watch_face.xml` and its `layout-land` twin | 1, 4, 5 |
| `mobile/src/main/res/layout/item_watch_theme.xml` | 2 |
| `mobile/src/main/res/layout/pref_typography_editor.xml`, `pref_color_editor.xml` | 4 |
| `mobile/src/main/res/xml/watch_face_settings.xml` | 1, 3 |
| `mobile/src/main/res/values*/strings.xml` | 3 |
| Structural tests under `mobile/src/test/.../res/` and `.../view/settings/` | 1, 3 |

New files, all under `mobile/`:

| File | Phase |
| --- | --- |
| `view/watchface/home/WatchTabNavigation.kt`, `WatchLooks.kt`, `PreviewCaption.kt` | 1 |
| `view/watchface/home/WatchLookThumbnailer.kt`, `WatchLooksAdapter.kt` | 1 |
| `view/watchface/home/HomeSurfacePreferences.kt` | 1 |
| `view/watchface/WatchFacePrefsFragmentHome.kt` | 1, 3 |
| `res/layout/pref_home_themes.xml`, `pref_home_areas.xml`, `item_watch_look.xml` | 1 |
| `view/watchface/VisualChoiceCatalog.kt`, `res/layout/item_visual_choice.xml` | 2 |
| `view/watchface/home/WatchHomeModel.kt`, `res/layout/pref_home_quick.xml` | 3 |
| `view/watchface/PreviewHitMap.kt`, `WatchElementModel.kt` | 5 |
| Tests named in each phase | 1 to 5 |

`mobile/.../` stands for `mobile/src/main/java/com/svartifoss/snfell/`.
