package com.svartifoss.snfell.whatsnew

/**
 * When the "What's new" window is shown, as a pure decision so every fallback can be pinned by a
 * JVM test: [WhatsNewPrompt] only reads the three facts and acts on the answer.
 *
 * The window exists for builds that have no updater of their own. The sideloaded build greets an
 * update with its Updates screen; the Play build has none, and Play's own "What's new" text lives
 * in the Store, which an update never sends anybody back to. Without this, an update installs in
 * the background and the first thing its user sees is an app that has quietly changed.
 *
 * Three things have to be told apart, and the version code alone cannot:
 *  - a **fresh install** has nothing to compare against and is shown nothing, only recorded;
 *  - an **update from a build that predates this code** has no recorded version either, and is the
 *    very person the window is for, so a missing record is read as "older than everything" when
 *    the install is known to be an update;
 *  - an **update the window has no notes for** (a patch release after the ones it describes) is
 *    recorded and stays quiet.
 */
object WhatsNewPolicy {

    /** What the stored version reads as when this build has never recorded one. */
    const val NEVER_RECORDED = -1L

    enum class Action {
        /** Already recorded for this build; nothing to read or write. */
        NOTHING,

        /** Record this build's version and show nothing. */
        REMEMBER,

        /** Show the window, once, and record this build's version. */
        SHOW,
    }

    /**
     * @param lastSeenVersionCode the version code recorded the last time the screen opened, or
     *        [NEVER_RECORDED]
     * @param currentVersionCode this build's version code
     * @param firstReleaseWithNotes the first version code that carries the notes the window shows
     * @param isUpdateInstall whether the OS says this install has been updated at least once
     *        (`lastUpdateTime` later than `firstInstallTime`); false for a fresh install
     */
    fun decide(
            lastSeenVersionCode: Long,
            currentVersionCode: Long,
            firstReleaseWithNotes: Long,
            isUpdateInstall: Boolean,
    ): Action {
        if (lastSeenVersionCode == currentVersionCode) return Action.NOTHING

        val lastSeen = when {
            lastSeenVersionCode != NEVER_RECORDED -> lastSeenVersionCode
            // Nothing recorded and never updated: a first run, with nothing that "changed".
            !isUpdateInstall -> return Action.REMEMBER
            // Nothing recorded on an install that has been updated: it came from a build older
            // than this feature, so it has seen none of the notes.
            else -> 0L
        }

        val notesAreNewToThem = lastSeen < firstReleaseWithNotes
        val buildCarriesNotes = firstReleaseWithNotes <= currentVersionCode
        return if (notesAreNewToThem && buildCarriesNotes) Action.SHOW else Action.REMEMBER
    }
}
