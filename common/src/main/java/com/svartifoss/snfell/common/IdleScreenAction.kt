package com.svartifoss.snfell.common

/**
 * What the watch's "Nothing playing" screen offers, both as its main button and as an optional
 * screen to jump straight to when the app opens with no session.
 *
 * Every case maps onto something the watch could already do - resuming the last app, the action
 * menu, the saved shortcuts, search, the queue - none of which the idle screen used to expose. It
 * is a preference rather than a fixed choice because "what do I want when nothing is playing"
 * genuinely differs: one user resumes the same podcast, another always starts from a playlist.
 */
enum class IdleScreenAction(val preferenceValue: String) {
    /** Sends a bare PLAY, which the phone routes to whichever app played last. */
    RESUME("resume"),

    /** Opens the saved streaming shortcuts list. */
    SHORTCUTS("shortcuts"),

    /** Opens the configurable action menu. */
    MENU("menu"),

    /** Opens voice/keyboard search. */
    SEARCH("search"),

    /** Opens the queue, which falls back to recently played when there is no live queue. */
    QUEUE("queue"),

    /** No button at all - the passive screen the app shipped before. */
    NONE("none");

    companion object {
        /**
         * Unknown values resolve to [RESUME] for the main button, since an unrecognised value
         * should still leave the user with a way to start playback.
         */
        fun forButton(value: String?): IdleScreenAction =
                entries.firstOrNull { it.preferenceValue == value } ?: RESUME

        /**
         * Unknown values resolve to [NONE] when deciding what to auto-open: guessing wrong here
         * would hijack the screen on every launch, so silence is the safe failure.
         */
        fun forAutoOpen(value: String?): IdleScreenAction =
                entries.firstOrNull { it.preferenceValue == value } ?: NONE

        /**
         * Auto-open deliberately cannot be [RESUME]: starting playback merely because the app was
         * opened would be a surprise, not a shortcut.
         */
        val AUTO_OPEN_CHOICES: List<IdleScreenAction> = listOf(NONE, SHORTCUTS, MENU, SEARCH, QUEUE)
    }
}
