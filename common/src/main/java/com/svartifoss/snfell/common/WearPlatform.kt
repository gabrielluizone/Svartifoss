package com.svartifoss.snfell.common

/**
 * What the watch's Android API level says about the platform the app is running on.
 *
 * Exists because of one support question that could not be answered: an always-on screen that shows
 * the app for an instant and is then replaced by the watch face. Wear OS has *two* inactivity
 * timeouts - the first takes the device into ambient, where the app draws its own always-on screen,
 * and the second hides the app and returns to the watch face. Only the second is the complaint, and
 * whether an app can prevent it is decided by the platform version rather than by anything the app
 * does: publishing an ongoing activity holds the app past it on **Wear OS 5 and higher**, and does
 * not on the versions below. Svartifoss has published one for as long as the watch service has
 * existed, so on Wear OS 5+ this is already handled and below it there is nothing further to do.
 *
 * That is exactly why the report differed between two watches with the same app - and why neither
 * user could say anything useful about the difference. The watch is the only side that can answer
 * what it is running, so it now says, and [ambientHoldSupport] turns the number into the sentence.
 *
 * Deliberately API level and nothing else: the model, manufacturer and build fingerprint would all
 * identify the device, and the privacy policy describes no such collection.
 */
object WearPlatform {

    /** API levels the Wear OS generations shipped on. Wear OS 4 skipped Android 12L/13's 32. */
    private const val WEAR_3 = 30
    private const val WEAR_4 = 33
    private const val WEAR_5 = 34
    private const val WEAR_6 = 36

    /**
     * The first Wear OS generation on which an ongoing activity keeps the app on screen instead of
     * the system returning to the watch face after its second timeout.
     */
    const val AMBIENT_HOLD_MIN_API = WEAR_5

    /** Whether an ongoing activity is expected to hold the player past the watch-face timeout. */
    fun holdsAmbientWithOngoingActivity(apiLevel: Int): Boolean = apiLevel >= AMBIENT_HOLD_MIN_API

    /**
     * The Wear OS generation an API level belongs to, or null when it is not one this knows.
     *
     * Null rather than a guess: the mapping is a published fact per release, not a formula, and a
     * diagnostic that invents "Wear OS 7" from an unrecognised number is worse than one that admits
     * it does not know while still printing the level it was given.
     */
    fun wearOsGeneration(apiLevel: Int): Int? = when {
        apiLevel >= WEAR_6 -> 6
        apiLevel >= WEAR_5 -> 5
        apiLevel >= WEAR_4 -> 4
        apiLevel >= WEAR_3 -> 3
        else -> null
    }

    /** One line for the diagnostics screen, naming the level and what follows from it. */
    fun describe(apiLevel: Int): String {
        val generation = wearOsGeneration(apiLevel)
                ?: return "API $apiLevel (not a Wear OS release this build knows)"
        val hold = if (holdsAmbientWithOngoingActivity(apiLevel)) {
            "the always-on player is held past the watch-face timeout"
        } else {
            "the system returns to the watch face on its own timeout - an ongoing activity only " +
                    "holds the player from Wear OS $AMBIENT_HOLD_GENERATION"
        }
        return "Wear OS $generation (API $apiLevel) - $hold"
    }

    private const val AMBIENT_HOLD_GENERATION = 5
}
