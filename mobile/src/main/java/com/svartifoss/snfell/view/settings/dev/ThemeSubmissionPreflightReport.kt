package com.svartifoss.snfell.view.settings.dev

import android.content.Context
import com.svartifoss.snfell.view.watchface.theme.CommunityThemeSubmissionDraftResult
import com.svartifoss.snfell.view.watchface.theme.WatchThemeRepository
import com.svartifoss.snfell.view.watchface.theme.WatchThemeValue

private fun WatchThemeValue.describe(): String = when (this) {
    is WatchThemeValue.Text -> "\"$value\""
    is WatchThemeValue.Flag -> value.toString()
    is WatchThemeValue.Number -> value.toString()
}

/**
 * Runs every locally saved theme through the exact same check the submission screen uses
 * ([WatchThemeRepository.prepareCommunityThemeSubmission]) and reports the outcome per theme.
 *
 * The submission screen itself only ever shows the generic `community_theme_submit_invalid` /
 * `_unsupported_setting` toasts, which name neither the theme nor - for a plain "invalid" result -
 * the setting responsible. This surfaces [CommunityThemeSubmissionDraftResult.UnsupportedSetting]'s
 * key and value directly, since that is the one outcome with something concrete to report, and
 * lets every saved theme be checked at once instead of one at a time through the submit flow.
 */
internal fun buildThemeSubmissionPreflightReport(context: Context): String {
    val repository = WatchThemeRepository(context)
    val profiles = repository.load()
    if (profiles.isEmpty()) {
        return "No saved themes in the local library (Watch tab -> Watch themes)."
    }

    return buildString {
        appendLine("${profiles.size} saved theme(s):")
        for (profile in profiles) {
            appendLine()
            appendLine("\"${profile.name}\" (base face: ${profile.baseFace})")
            if (profile.publishedTheme != null) {
                appendLine("  Installed from the gallery - cannot be re-submitted as a new theme.")
                continue
            }
            when (val result = repository.prepareCommunityThemeSubmission(profile.id, profile.name)) {
                is CommunityThemeSubmissionDraftResult.Ready ->
                    appendLine("  OK - would submit cleanly (${result.draft.settings.size} settings).")
                is CommunityThemeSubmissionDraftResult.UnsupportedSetting ->
                    appendLine("  BLOCKED - \"${result.key}\" holds a value the public vocabulary " +
                            "does not accept: ${result.value.describe()}")
                is CommunityThemeSubmissionDraftResult.DeviceLocalSetting ->
                    appendLine("  PRIVATE - \"${result.key}\" is \"${result.value}\", which names " +
                            "a file on this phone. Not a fault: this theme is deliberately " +
                            "unpublishable and works normally here.")
                CommunityThemeSubmissionDraftResult.ProfileNotFound ->
                    appendLine("  BLOCKED - profile could not be re-read from storage.")
                CommunityThemeSubmissionDraftResult.PublishedThemeCannotBeSubmitted ->
                    appendLine("  BLOCKED - installed from the gallery, cannot be re-submitted.")
                CommunityThemeSubmissionDraftResult.InvalidPublicName ->
                    appendLine("  BLOCKED - its saved name is not a valid public submission name " +
                            "(this check uses the theme's current name as a stand-in for whatever " +
                            "name would actually be typed at submission).")
                CommunityThemeSubmissionDraftResult.InvalidProfile ->
                    appendLine("  BLOCKED - fails the public parser (unknown/archived face, an " +
                            "unrecognised setting, or a schema mismatch). Names neither the field " +
                            "nor the value; narrow it down by process of elimination.")
                CommunityThemeSubmissionDraftResult.ProfileTooLarge ->
                    appendLine("  BLOCKED - would push the phone -> watch preference payload past " +
                            "its transport budget.")
            }
        }
    }
}
