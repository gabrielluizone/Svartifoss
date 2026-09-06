package com.svartifoss.snfell.config

import android.content.Context
import android.content.SharedPreferences
import com.svartifoss.snfell.common.FaceScopedPreferences
import com.svartifoss.snfell.common.MiscPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds the snapshot that ships as `mobile/src/main/res/raw/default_config.json` - the state a
 * fresh install starts from.
 *
 * This app is a personal sideload, so "default" means the author's own saved setup rather than a
 * generic first-run guess: `GlobalActionConfig.seedBundledDefaultConfig` imports that file through
 * [ConfigBackup] before any config file exists. There has never been a way to *produce* it from a
 * running phone, though, so the shipped copy could only be updated by hand-editing an ordinary
 * backup. This is that missing half, behind the developer switch: export, hand the file over, drop
 * it into `res/raw`.
 *
 * It is deliberately **not** the same document the Settings backup writes. A backup is restored
 * onto the same person's next phone, so it should carry everything; this one is copied into an APK
 * and applied to strangers, and three groups of state must not make that trip. What separates them
 * is the [ConfigBackupSection] split that already exists, which is why this is a policy over that
 * enum rather than a second exporter.
 */
object DefaultConfigExport {

    /** The name the shipped resource has, offered as the save-dialog filename so the file arrives
     *  ready to drop into `res/raw` without being renamed. */
    const val FILE_NAME = "default_config.json"

    /** Marker recording that this document came from here and not from the Settings backup. The
     *  two are the same format and the difference between them is invisible by eye, while getting
     *  it wrong ships somebody's listening history inside the APK. */
    const val MARKER_KEY = "defaultConfigExport"

    private const val MARKER_VERSION = 1

    /**
     * State that must never become somebody else's default.
     *
     * [ConfigBackupSection.HISTORY] is the author's own searches and played tracks - personal, and
     * meaningless as a starting point. [ConfigBackupSection.PRIVACY] is consent: crash reporting
     * and announcements are choices each person makes, and shipping one pre-made would answer for
     * them. [ConfigBackupSection.LOCAL_APP_STATE] records what this phone has already done -
     * the notification-access prompt having been shown, the one-shot migrations having run, the
     * update checker's bookkeeping - so shipping it suppresses a first-run prompt that a new
     * install genuinely needs and marks migrations complete that never happened there.
     */
    val EXCLUDED_SECTIONS: Set<ConfigBackupSection> = setOf(
            ConfigBackupSection.HISTORY,
            ConfigBackupSection.PRIVACY,
            ConfigBackupSection.LOCAL_APP_STATE)

    /** Everything else: buttons, actions, settings, the whole watch appearance and theme library,
     *  saved playlist shortcuts, and the icon stores those refer to. */
    val INCLUDED_SECTIONS: Set<ConfigBackupSection> =
            ConfigBackupSection.ALL - EXCLUDED_SECTIONS

    /**
     * Individual keys dropped from an otherwise included section.
     *
     * Only [MiscPreferences.APP_LANGUAGE], and it is the one entry that would be actively broken
     * rather than merely nosy: the picker's whole point is that it *overrides* the device locale,
     * so shipping the author's value would open the app in Portuguese on a phone set to Japanese,
     * with nothing on screen explaining why. Left unset, [com.svartifoss.snfell.common.AppLocales]
     * resolves to the system language, which is the right default for everyone.
     */
    val EXCLUDED_PREFERENCE_KEYS: Set<String> = setOf(MiscPreferences.APP_LANGUAGE.key)

    /**
     * Named preference files dropped from [ConfigBackupSection.AUXILIARY_DATA].
     *
     * That section otherwise carries `custom_icon_storage`, which is the metadata half of the icon
     * files and has to travel with them. `community_theme_submission` is the author's gallery
     * identity and submission bookkeeping - an account's own records, not a default.
     */
    val EXCLUDED_NAMED_PREFERENCE_STORES: Set<String> = setOf("community_theme_submission")

    /**
     * Whether a preference key belongs in a defaults snapshot.
     *
     * Compares the *base* key so a face-scoped `key@face` entry is judged by the setting it holds.
     * No excluded key is scoped today, which is exactly why this is written down: a scoped one
     * would otherwise slip through under twenty different names.
     */
    fun shipsPreference(key: String): Boolean =
            key.substringBefore(FaceScopedPreferences.SCOPE_SEPARATOR) !in EXCLUDED_PREFERENCE_KEYS

    fun shipsNamedPreferenceStore(name: String): Boolean =
            name !in EXCLUDED_NAMED_PREFERENCE_STORES

    /** The document to hand over, ready to be committed as `res/raw/default_config.json`. */
    fun build(context: Context, preferences: SharedPreferences): JSONObject {
        val json = ConfigBackup.export(context, preferences, INCLUDED_SECTIONS)
        val omitted = redact(json)
        json.put(MARKER_KEY, JSONObject().apply {
            put("version", MARKER_VERSION)
            put("omittedSections", JSONArray().apply {
                ConfigBackupSection.values()
                        .filter { it in EXCLUDED_SECTIONS }
                        .forEach { put(it.id) }
            })
            put("omitted", JSONArray().apply { omitted.forEach(::put) })
        })
        return json
    }

    /**
     * Strips the individually excluded entries, returning what it removed.
     *
     * Separate from [build] so the removal is testable against a real document rather than only
     * through a running phone, and so a caller can report what was left out. [ConfigBackup.import]
     * reads by key and ignores anything it does not know, so both this and [MARKER_KEY] are inert
     * on the way back in.
     */
    internal fun redact(json: JSONObject): List<String> {
        val removed = mutableListOf<String>()
        json.optJSONObject("preferences")?.let { prefs ->
            prefs.keys().asSequence().toList()
                    .filterNot(::shipsPreference)
                    .forEach { key ->
                        prefs.remove(key)
                        removed += key
                    }
        }
        json.optJSONObject("namedPreferences")?.let { stores ->
            stores.keys().asSequence().toList()
                    .filterNot(::shipsNamedPreferenceStore)
                    .forEach { name ->
                        stores.remove(name)
                        removed += "namedPreferences/$name"
                    }
        }
        return removed
    }
}
