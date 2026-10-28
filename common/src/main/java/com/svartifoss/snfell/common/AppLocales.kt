package com.svartifoss.snfell.common

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Resolution of [MiscPreferences.APP_LANGUAGE] into an actual [Locale], shared by the phone and the
 * watch so both interpret the same stored tag identically.
 *
 * The two apps apply it by different means, because they cannot use the same one:
 *  - the phone routes it through `AppCompatDelegate.setApplicationLocales`, which on Android 13+
 *    hands off to the framework's per-app language setting and shows up in system Settings;
 *  - the watch wraps each activity's base context ([wrap]), because two of its activities are plain
 *    `ComponentActivity` and AppCompat's pre-33 locale backport only recreates `AppCompatActivity`.
 *
 * [SUPPORTED] is the honest list of what the app is actually translated into. Adding a language
 * means adding a `values-<code>` resource folder *and* an entry here - listing a tag with no
 * translation would just render English under a different label.
 */
object AppLocales {

    /** Stored value meaning "follow the device", as opposed to any concrete tag. */
    const val SYSTEM = "system"

    /** BCP-47 tags the app ships translations for. Order is the order shown in the picker. */
    val SUPPORTED: List<String> = listOf(
            "en", "pt-BR", "pt-PT", "de", "es", "it", "nl", "ru", "el", "ro", "id", "fa", "zh-Hans",
            "is", "fr", "tr", "vi", "cs", "sv", "nb", "hu", "ar", "hi", "fil", "kk", "ja", "ko", "pl", "zh-Hant", "uk", "th", "he", "da", "fi", "sk", "bg", "sr", "hr", "ms", "bn", "my", "ta", "te", "mr", "ckb")

    /**
     * The [Locale] for a stored preference value, or null to follow the system.
     *
     * Unknown tags resolve to null rather than to themselves: a value can arrive from an imported
     * backup or from a newer build on the other device, and falling back to the system language is
     * far better than forcing a locale with no resources behind it.
     */
    fun localeFor(value: String?): Locale? {
        if (value.isNullOrBlank() || value == SYSTEM) return null
        if (value !in SUPPORTED) return null
        return Locale.forLanguageTag(APPLIED_TAGS[value] ?: value)
    }

    /**
     * Stored tags applied as a more specific one. English is stored and offered as plain "en", but
     * a bare "en" is not a safe request to make of Android: its own resources keep English in the
     * untagged default, have no plain "en" variant, and do carry the test pseudo-locales en-XA and
     * en-XC. On Android 12 and earlier a request for "en" resolved the system's strings to en-XC,
     * which wraps each word in about a hundred invisible direction marks - so every
     * `@android:string/cancel` and `ok` the app shows measured as wide as that, and a dialog's
     * buttons crowded out their neighbours (the action editor's Delete button disappeared; seen on a
     * Pixel 3, Android 10). en-US resolves to the system's default English, and the app's own
     * strings are the same either way: it has one English, in `values/`.
     */
    private val APPLIED_TAGS = mapOf("en" to "en-US")

    /** The stored value, normalised - anything unrecognised collapses to [SYSTEM]. */
    fun normalize(value: String?): String =
            if (value != null && value in SUPPORTED) value else SYSTEM

    /**
     * [context] re-based on the chosen language, for use from `Activity.attachBaseContext`. Returns
     * the context untouched when following the system, so the default path adds no wrapping at all.
     */
    fun wrap(context: Context, value: String?): Context {
        val locale = localeFor(value) ?: return context
        val configuration = Configuration(context.resources.configuration)
        Locale.setDefault(locale)
        configuration.setLocale(locale)
        return context.createConfigurationContext(configuration)
    }
}
