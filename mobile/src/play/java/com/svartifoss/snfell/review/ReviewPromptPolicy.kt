package com.svartifoss.snfell.review

/**
 * What the rating prompt remembers between launches. Plain numbers, so a JVM test can drive the
 * rules in [ReviewPromptPolicy] without Android.
 */
internal data class ReviewPromptState(
        /** Days the app was opened on, each counted once: the measure of "has used it enough". */
        val daysUsed: Int = 0,
        /** The day [daysUsed] last grew on, as days since the epoch in the phone's own time zone. */
        val lastDayUsed: Long = NEVER,
        /** The day Play was last asked for the card, **a request that failed included**. */
        val lastAttemptDay: Long = NEVER,
        /** How many times the card was handed to Play to show. */
        val asks: Int = 0,
        /** When it was last handed over, in epoch milliseconds. */
        val lastAskAtMillis: Long = NEVER
) {
    companion object {
        const val NEVER = -1L
    }
}

/**
 * When the app may ask for a rating. Pure, so the rules that keep it from nagging are tested
 * rather than hoped for.
 *
 * Google Play enforces a quota of its own on the card and says plainly that the value is an
 * implementation detail it can change without notice, so "Play will refuse if I ask too much" is
 * not a plan: the guidance is to apply your own logic and ask at the best moment. The same page
 * says that calling the flow more than once inside a month "might not always display a dialog",
 * and that is the reason a refused ask cannot be told apart from a shown one - the API never says
 * whether the card appeared or whether anyone rated. So an ask is counted when it is handed to
 * Play, and these numbers are the whole budget.
 *
 * ## What is deliberately not here
 *
 * Nothing of ours comes before the card ("do you like the app?" is forbidden by Play and would be
 * a filter on who may rate), and no button raises it: Play advises against one because it does
 * nothing whenever the quota is spent, which reads as broken. So the decision is made once, when
 * the app is opened, and nowhere else a user can reach; the Developer row that skips it is for
 * testing (see [ReviewPrompt.showNow]).
 */
internal object ReviewPromptPolicy {

    /**
     * Days the app has to have been opened on before it first asks. A watch controller is set up
     * once and then lives on the wrist, so the phone app is opened rarely; three different days is
     * somebody who has come back to it, which is the earliest anyone can say anything useful.
     */
    const val MIN_DAYS_USED = 3

    /** Quiet period after an ask: twice Play's "less than a month" so the next one can really show. */
    const val MIN_DAYS_BETWEEN_ASKS = 60

    /** Over the life of an install. Somebody who has been asked this often has had their chance. */
    const val MAX_ASKS = 3

    const val DAY_MS = 24L * 60 * 60 * 1000

    /** Counts [today] as a day of use, once however often the app is opened. */
    fun opened(state: ReviewPromptState, today: Long): ReviewPromptState =
            if (state.lastDayUsed == today) state
            else state.copy(daysUsed = state.daysUsed + 1, lastDayUsed = today)

    /**
     * Whether to ask now. At most one attempt a day, so a Play that cannot answer here (a build not
     * installed from the Store, no Play services) is tried once and left alone until tomorrow
     * instead of with every screen of the day.
     */
    fun isDue(state: ReviewPromptState, today: Long, nowMillis: Long): Boolean =
            state.daysUsed >= MIN_DAYS_USED &&
                    state.asks < MAX_ASKS &&
                    state.lastAttemptDay != today &&
                    quietPeriodOver(state, nowMillis)

    /** Play was asked for the card and could not give one. Spends today's attempt, not an ask. */
    fun attempted(state: ReviewPromptState, today: Long): ReviewPromptState =
            state.copy(lastAttemptDay = today)

    /** The card was handed to Play to show. Whether it appeared is not something anyone can learn. */
    fun asked(state: ReviewPromptState, today: Long, nowMillis: Long): ReviewPromptState =
            state.copy(lastAttemptDay = today, asks = state.asks + 1, lastAskAtMillis = nowMillis)

    /**
     * A clock set back past the last ask counts as the period being over: refusing until the
     * calendar caught up with a date that never happened would be a silent, permanent "never".
     * Asks stay bounded by [MAX_ASKS] and the one attempt a day either way.
     */
    private fun quietPeriodOver(state: ReviewPromptState, nowMillis: Long): Boolean {
        if (state.lastAskAtMillis == ReviewPromptState.NEVER) return true
        val elapsed = nowMillis - state.lastAskAtMillis
        return elapsed < 0 || elapsed >= MIN_DAYS_BETWEEN_ASKS * DAY_MS
    }
}
