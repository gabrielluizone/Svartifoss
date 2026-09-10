package com.svartifoss.snfell.logging

/**
 * Whether the platform actually carries the API level it reports.
 *
 * `Build.VERSION.SDK_INT` is taken on trust by every `Api34Impl`-style guard in AndroidX, so a
 * device that reports 34 over a framework that has no API 34 executes branches written for a
 * platform it does not have. The result is a `NoSuchFieldError`, `NoSuchMethodError` or
 * `NoClassDefFoundError` raised from inside a library, on a code path our own build guards
 * correctly - which reads as an app bug and is not one. Five such crashes (4.0 build 77) cost a
 * full investigation before the shipped dex could be shown to hold the guard at the very line the
 * trace named, so the point of this object is to make the next one self-describing rather than to
 * change any behaviour: nothing here is repairable from app code.
 *
 * The reflective probing lives in [PlatformApiProbe]; this half is the decision, kept free of
 * `android.*` so it can be pinned by a plain JVM test.
 */
object PlatformApiIntegrity {

    /** Crashlytics custom key. Present on every session so an absent key means an older build. */
    const val CUSTOM_KEY = "platform_api_integrity"

    /** The first API level whose members this app probes for. */
    const val PROBED_FROM_SDK = 34

    /** Reported when the device is below [PROBED_FROM_SDK] and so was never asked anything. */
    const val NOT_PROBED = "not-probed"

    /** Reported when every probed member was present. */
    const val CONSISTENT = "ok"

    private const val MISSING_PREFIX = "missing-api-"

    /**
     * @param sdkInt what the device claims, i.e. `Build.VERSION.SDK_INT`.
     * @param absentMembers short labels for the probed members the framework turned out not to
     *     have. Only meaningful at or above [PROBED_FROM_SDK]; below it nothing is probed, so
     *     anything passed here is ignored rather than read as a device that lies.
     */
    fun resolve(sdkInt: Int, absentMembers: Collection<String>): Report {
        if (sdkInt < PROBED_FROM_SDK) {
            // A device below the probed level is not making a claim we have checked. Saying "ok"
            // here would make the key mean two different things, and "inconsistent" would accuse
            // every older device of lying purely because it was never asked.
            return Report(consistent = true, probed = false, summary = NOT_PROBED)
        }
        // Sorted and de-duplicated so one broken platform yields one Crashlytics value to group
        // by, whatever order the probes happened to run in.
        val missing = absentMembers.filter { it.isNotBlank() }.distinct().sorted()
        if (missing.isEmpty()) {
            return Report(consistent = true, probed = true, summary = CONSISTENT)
        }
        return Report(
                consistent = false,
                probed = true,
                summary = MISSING_PREFIX + PROBED_FROM_SDK + ":" + missing.joinToString(","))
    }

    /**
     * @param consistent false only when the platform was asked and came up short. A device that
     *     was never probed reports true, because nothing about it is known to be wrong.
     * @param probed whether any question was actually put to the framework.
     * @param summary the Crashlytics custom-key value.
     */
    data class Report(
        val consistent: Boolean,
        val probed: Boolean,
        val summary: String
    )
}
