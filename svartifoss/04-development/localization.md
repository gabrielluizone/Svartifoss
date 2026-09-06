---
title: Localization
tags:
  - svartifoss/development
  - localization
summary: The synchronized phone/watch language model, supported locale registry, resource folder traps, and picker-array contract.
---

# Localization

## Application language model

Language is a user preference, not merely a device setting. `MiscPreferences.APP_LANGUAGE` is exportable and reaches the watch. `common/.../AppLocales.kt` normalizes the same stored BCP-47 tag on both sides.

The phone applies it through `AppCompatDelegate.setApplicationLocales`, integrating with Android 13+ per-app language settings. The watch wraps each activity's base context because several watch activities are plain `ComponentActivity`, outside AppCompat's pre-33 recreation mechanism.

Unknown or newer tags fall back to the system language rather than forcing a locale for which the installed APK has no resources.

## Supported set

The current source registry has 40 locales including English:

`en`, `pt-BR`, `pt-PT`, `de`, `es`, `it`, `nl`, `ru`, `el`, `ro`, `id`, `fa`, `zh-Hans`, `is`, `fr`, `tr`, `vi`, `cs`, `sv`, `nb`, `hu`, `ar`, `hi`, `fil`, `kk`, `ja`, `ko`, `pl`, `zh-Hant`, `uk`, `th`, `he`, `da`, `fi`, `sk`, `bg`, `sr`, `hr`, `ms`, and `bn`.

This is a source-tree snapshot; `AppLocales.SUPPORTED` is the authority. Some older public copy still states a smaller historical count.

## Adding a locale

Keep these places aligned:

1. a resource folder in **each** of `mobile`, `wear`, and `common`;
2. `AppLocales.SUPPORTED`, in picker order;
3. `mobile/src/main/res/xml/locales_config.xml`;
4. `app_language_entries` and `app_language_values`.

Verify the actual Android resource qualifier. Five current locales use BCP-47 folder syntax:

- `values-b+id`
- `values-b+fil`
- `values-b+he`
- `values-b+zh+Hans`
- `values-b+zh+Hant`

Modern tags and legacy Android folder codes are not interchangeable. A plausible-looking folder can remain dead without a compile error.

## Picker arrays

`ListPreference` pairs `entries` and `entryValues` strictly by index. Values usually live only in default resources because they are stable tokens; labels are translated. If a translated label array omits an item in the middle, every subsequent label maps to the wrong behavior. If it omits a final item, the option is unreachable in that locale.

When adding any picker value:

- insert its label at the same index in every translated array;
- keep stable values unlocalized;
- run `TranslatedArrayAlignmentTest`;
- run vocabulary tests if the picker feeds community themes;
- update `common` and watch strings when shared/watch UI names the option independently.

## Codes versus labels

Wire and persisted values cross devices as stable codes. The rendering device owns localization. Track metadata output/download status, hand-gesture state, actions, and preference tokens must not be serialized as a phone-localized string; phone and watch can intentionally use different languages or differ in version.

## Resource contract tests

Useful guards include:

- `TranslatedArrayAlignmentTest`
- `CommunityThemeTranslationTest`
- `SettingsCatalogTest`
- `SettingsSearchRoutingTest`
- community vocabulary/resource parity tests

## Source anchors

- `common/src/main/java/com/svartifoss/snfell/common/AppLocales.kt`
- `mobile/src/main/java/com/svartifoss/snfell/view/settings/AppLanguage.kt`
- `wear/src/main/java/com/svartifoss/snfell/watch/util/WatchLanguage.kt`
- `mobile/src/main/res/xml/locales_config.xml`
- each module's `src/main/res/values*`

## Related notes

- [Change playbooks](change-playbooks.md)
- [Testing strategy](testing-strategy.md)
- [Preference domains](../05-reference/preference-domains.md)

