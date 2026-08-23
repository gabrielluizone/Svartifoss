# UX Information Architecture

This document is the product map and compatibility contract for the structural settings redesign. It describes where users look for a capability; preference definitions and runtime behavior remain authoritative in code.

## Current Product Map

The phone app has four primary destinations:

- **Watch** configures the now-playing appearance through a live preview, saved themes, and the Player, Background, Colors, Text, Always-on, Panels, and Mini buttons areas.
- **Controls** assigns touch zones, the center tap, physical buttons, swipes, and mini buttons separately for **Music playing** and **No playback**.
- **Actions** configures the four-slot quick panel and the ordered action menu shown on the watch.
- **Settings** contains General, Behavior, Automation, Apps, and Data & support. Help is a toolbar action.

Settings Search indexes the preference XML for both Watch and Settings. Controls and Actions use their own editors and are not preference-search destinations.

On the watch, the now-playing screen is the hub. A center double-tap opens Quick actions; a configurable center long-press opens the face picker, queue, or nothing. Swipe up opens the action menu by default, while configurable actions can open Queue, Lyrics, Search, Library, and Shortcuts. Swipe right follows Wear OS back/dismiss behavior. The Tile, complication, launcher, and recent-app entry all lead into this runtime flow.

## Target Information Architecture

Keep the four primary phone destinations. Within Settings and Watch appearance, use clearly named, horizontally swipeable tabs so each page remains focused without sacrificing the familiar side-to-side gesture. The live watch preview and saved-theme affordance remain visible context for Watch appearance.

Settings sections own these concepts:

- **General:** updates, phone theme, language, accent, and mini-player appearance.
- **Behavior:** watch input, rotary behavior, haptics, action-menu behavior, and notification popups.
- **Automation:** auto-start mode and its per-app exceptions, closing rules, and no-playback behavior.
- **Apps:** playback data and lyrics, streaming services and shortcuts, queue artwork, and Android integration/access.
- **Data & support:** backup/restore, privacy and diagnostics, version, help, and project information.

Watch appearance sections own these concepts:

- **Player:** base layout, element visibility, Metadata-face content, progress/seeking behavior, time, and screen-awake behavior.
- **Background:** album art, blur, dimming, fading, shading, and readability treatments.
- **Colors:** album-derived palette plus title, artist, clock, progress, volume, and panel color policies.
- **Text:** typefaces and title, artist, clock, lyrics, and source-icon typography only.
- **Always-on:** ambient layout, content, art treatment, color, intensity, and burn-in-conscious detail.
- **Panels:** shared backdrop, volume/seek overlays, Quick actions, queue, and list presentation.
- **Mini buttons:** shortcut-row presentation and related screen-gesture mode.

Use conditional visibility for genuine dependencies (for example, custom color, Flex axes, or face-specific controls). A hidden advanced row must remain discoverable through its controlling choice and must never become a dead-end search result.

## Persistence and Compatibility Invariants

A visual move is not a data migration. Preserve all existing preference keys, value encodings, defaults, and legacy fallback reads. In particular:

- `MiscPreferences.EXPORTABLE` remains the contract for Phone → Watch sync and configuration backup.
- Watch-appearance values remain face-scoped through `FaceScopedPreferences`; changing the active face must still select that face's values.
- Saved custom themes continue to snapshot the scoped appearance definitions, independent of the currently selected built-in face.
- Backup and restore continue to include preferences, button/action configuration, shortcuts, and custom themes without translating keys.
- XML `android:dependency` relationships and the conditional rules in `MiscSettingsFragment` and `WatchFacePrefsFragment` survive category moves.
- Informational/action rows that are deliberately unpersisted remain unpersisted. Global rows mirrored in Watch appearance, such as queue artwork access, must not become face-scoped.
- Do not add a migration unless a stored format truly changes. Document and test any unavoidable migration explicitly.

## Navigation and Search Contract

A searchable route is the tuple `(tab, section, preferenceKey)`. The tab selects Settings or Watch, the section selects the owning pager page, and the preference key scrolls to and highlights the row.

`SettingsCatalog` is the single source of truth for category-to-section ownership. Every XML category shown by a fragment must be registered and reachable; dynamically managed categories must also have an explicit searchable owner. When a preference moves, update the XML and catalog together rather than teaching search a second hierarchy.

Search text comes from localized XML titles and summaries. Matching should include the section and category vocabulary so users can search concepts such as “automation” or “clock,” not only exact setting names. Results show the breadcrumb **tab › section › category**. If a result can be conditionally hidden, route to the prerequisite preference or make the destination reveal it safely.

Rotation restores the selected section and highlight state. Normal entry from bottom navigation remembers the most recently selected page; a search route opens the matching page directly.

## Validation Checklist

- Compare preference keys, defaults, dependencies, and entry values with checkpoint `d8a6264`: `git diff d8a6264 -- mobile/src/main/res/xml common/src/main/java/com/svartifoss/snfell/common/MiscPreferences.kt`.
- Run catalog, search-ranking, appearance-scoping, theme, backup, and preference-sync tests, then `./gradlew test` with JDK 21.
- Build both apps: `./gradlew :mobile:assembleDebug :wear:assembleDebug`.
- Search by preference title, category, and section; test a conditional result and Back/rotation behavior.
- Change the same scoped value on two faces and confirm each face retains its value after Phone → Watch sync.
- Save/apply a custom theme, export configuration, change values, restore, and verify themes, controls, shortcuts, and defaults.
- Exercise Controls in both playback states, Quick actions, the action menu, AOD, queue/list panels, and the full watch navigation once more.
