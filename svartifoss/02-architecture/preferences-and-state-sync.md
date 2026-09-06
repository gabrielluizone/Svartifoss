---
title: Preferences and State Sync
aliases:
  - Preference Architecture
tags:
  - svartifoss/architecture
  - preferences
  - synchronization
summary: Phone-owned settings, per-face scopes, durable/immediate delivery, filtering, and payload selection.
---

# Preferences and State Sync

## Ownership

Watch-facing settings are declared as typed definitions in `common/.../MiscPreferences.kt`, edited primarily on the phone, and consumed on both sides. `MiscPreferences.EXPORTABLE` is a critical registry: it determines which values participate in watch synchronization and configuration backup. Appearance capture further intersects it with `FaceScopedPreferences.SCOPED_KEYS`.

The watch keeps a local `SharedPreferences` projection so it can render while disconnected, but it is not the long-term authority.

## Process-wide coordinator

`WatchPreferenceSyncCoordinator`, started by phone `WearMusicCenter.onCreate`, observes default preferences for the entire phone process. It debounces/conflates edits, retries with exponential backoff, and republishes once at process start to repair stale watches.

This replaced screen-lifecycle listeners. A user can leave a fragment immediately after changing a setting; synchronization cannot depend on that fragment remaining alive.

## One filtered snapshot, two transports

The coordinator selects one snapshot and feeds it to both:

1. a `WatchPreferenceMessage` over `/PreferencesSync/Apply` for immediate additive application;
2. the durable `/Settings` DataItem, which survives reboot and owns removal through its key inventory.

Both paths use the same `shouldSyncWatchPreference` filtering. Phone-only JSON such as search history or shortcut storage must not consume the watch payload budget simply because it lives in default preferences.

## Face scopes

Appearance keys are stored as `<baseKey>@<scope>`. A built-in face reads:

1. explicit value in its scope;
2. that face's authored default;
3. compatible legacy global value;
4. definition default.

`FaceScopedPreferences.getString` and `getBoolean` must preserve the same fallback order.

A saved custom theme is different. Applying it materializes a complete snapshot into the fixed `custom_active` scope and writes metadata identifying the active theme, schema, completeness, and revision. A custom context reads only that snapshot and known defaults; it never leaks through to arbitrary values from the recipient's built-in scopes.

## Payload budget

Materializing every scoped key for all recognized faces produced a payload far above the Data Layer limit. The selector now divides values into:

- **mandatory:** global behavior plus the complete active appearance scope;
- **cache:** inactive face scopes packed deterministically while the configured budget remains.

The active scope is never sacrificed to make room for inactive cache. If an inactive scope is omitted and the user selects that face on the watch, the watch applies the base layout immediately, reports the choice to the phone, and receives that now-active scope in the next mandatory snapshot.

Theme-install size checks must project the prospective `custom_active` snapshot through this same selector. Measuring the entire phone preference file would reject a valid theme because of unrelated saved faces.

## Watch-originated face changes

The wrist picker writes a built-in base face locally for instant feedback and posts `PreferencesBus` so the open player refreshes. It also sends `MESSAGE_SET_SCREEN_FACE` to the phone. Custom theme selections are encoded as `custom:<id>`; only the phone has the full profile and can materialize it correctly.

Selecting a built-in face clears active-custom metadata. Otherwise the appearance resolver would continue returning the custom context and make the visible choice appear ineffective.

## Adding a setting

A new watch-facing behavior usually needs:

- a typed definition in `MiscPreferences`;
- inclusion in `EXPORTABLE`;
- phone settings UI and localization;
- watch read/application;
- a default/fallback test.

A per-face appearance setting additionally needs `SCOPED_KEYS`, preview rendering, watch rendering, theme constraints/default fixtures, cross-language schema mirrors, and parity tests. See [Change playbooks](../04-development/change-playbooks.md).

## Source anchors

- `common/.../MiscPreferences.kt`
- `common/.../FaceScopedPreferences.kt`
- `common/.../ThemeAppearance.kt`
- `common/.../WatchPreferenceMessage.kt`
- `mobile/.../WatchPreferenceSyncCoordinator.kt`
- `wear/.../communication/PreferencesReceiver.kt`
- `wear/.../communication/PreferenceMessageReceiver.kt`

## Related notes

- [Watch UI and appearance](watch-ui-and-appearance.md)
- [Preference domains](../05-reference/preference-domains.md)
- [Architecture invariants](../04-development/architecture-invariants.md)

