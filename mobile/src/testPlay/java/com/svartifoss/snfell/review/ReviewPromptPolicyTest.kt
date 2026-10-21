package com.svartifoss.snfell.review

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewPromptPolicyTest {

    private val day = ReviewPromptPolicy.DAY_MS
    private val minDays = ReviewPromptPolicy.MIN_DAYS_USED
    private val quietDays = ReviewPromptPolicy.MIN_DAYS_BETWEEN_ASKS

    private val firstDay = 100L

    /** Opens the app on [days] consecutive days, the way somebody who keeps coming back would. */
    private fun usedFor(days: Int): ReviewPromptState =
            (0 until days).fold(ReviewPromptState()) { state, i ->
                ReviewPromptPolicy.opened(state, firstDay + i)
            }

    /** The day the [minDays]-th different day of use falls on. */
    private val thirdDay = firstDay + minDays - 1

    private fun due(state: ReviewPromptState, today: Long) =
            ReviewPromptPolicy.isDue(state, today, today * day)

    // ---- counting days of use ----

    @Test
    fun aDayIsCountedOnceHoweverOftenTheAppIsOpened() {
        var state = ReviewPromptState()
        repeat(10) { state = ReviewPromptPolicy.opened(state, firstDay) }

        assertEquals(1, state.daysUsed)
    }

    @Test
    fun everyDifferentDayCounts() {
        assertEquals(5, usedFor(5).daysUsed)
    }

    // ---- the first ask ----

    @Test
    fun aNewInstallIsLeftAloneUntilItHasBeenUsedOnSeveralDays() {
        for (days in 1 until minDays) {
            assertFalse("asked after only $days day(s) of use", due(usedFor(days), firstDay + days - 1))
        }
    }

    @Test
    fun theDayOfUseThatReachesTheMinimumIsTheFirstThatAsks() {
        assertTrue(due(usedFor(minDays), thirdDay))
    }

    @Test
    fun manyOpeningsInOneDayAreNotManyDaysOfUse() {
        var state = ReviewPromptState()
        repeat(50) { state = ReviewPromptPolicy.opened(state, firstDay) }

        assertFalse(due(state, firstDay))
    }

    // ---- a request that got nothing ----

    @Test
    fun aRequestPlayCouldNotAnswerSpendsTodayButNotAnAsk() {
        val state = ReviewPromptPolicy.attempted(usedFor(minDays), thirdDay)

        assertFalse("not twice in one day", due(state, thirdDay))
        assertEquals("nothing was shown, so nothing was spent", 0, state.asks)
    }

    @Test
    fun theNextDayTriesAgain() {
        val failed = ReviewPromptPolicy.attempted(usedFor(minDays), thirdDay)

        val tomorrow = ReviewPromptPolicy.opened(failed, thirdDay + 1)

        assertTrue(due(tomorrow, thirdDay + 1))
    }

    // ---- an ask ----

    @Test
    fun anAskIsCountedAndRemembered() {
        val state = ReviewPromptPolicy.asked(usedFor(minDays), thirdDay, thirdDay * day + 5)

        assertEquals(1, state.asks)
        assertEquals(thirdDay * day + 5, state.lastAskAtMillis)
        assertEquals("it also spends the day", thirdDay, state.lastAttemptDay)
        assertFalse(due(state, thirdDay))
    }

    @Test
    fun theNextAskWaitsOutTheQuietPeriod() {
        val state = ReviewPromptPolicy.asked(usedFor(minDays), thirdDay, thirdDay * day)

        assertFalse("the last day inside the quiet period", due(state, thirdDay + quietDays - 1))
        assertTrue("the first day after it", due(state, thirdDay + quietDays))
    }

    @Test
    fun theQuietPeriodIsMeasuredFromTheAskNotFromTheDayOfTheCalendar() {
        // Asked late in the evening: a day boundary a few minutes later must not count as a day.
        val evening = thirdDay * day + day - 60_000
        val state = ReviewPromptPolicy.asked(usedFor(minDays), thirdDay, evening)

        assertFalse(ReviewPromptPolicy.isDue(state, thirdDay + quietDays, (thirdDay + quietDays) * day))
        assertTrue(ReviewPromptPolicy.isDue(state, thirdDay + quietDays, evening + quietDays * day))
    }

    @Test
    fun itStopsForGoodAfterTheLastAsk() {
        var state = usedFor(minDays)
        var today = thirdDay
        repeat(ReviewPromptPolicy.MAX_ASKS) {
            assertTrue("ask ${it + 1} should be allowed", due(state, today))
            state = ReviewPromptPolicy.asked(state, today, today * day)
            today += quietDays
            state = ReviewPromptPolicy.opened(state, today)
        }

        assertEquals(ReviewPromptPolicy.MAX_ASKS, state.asks)
        assertFalse(due(state, today))
        assertFalse("not even a year on", due(ReviewPromptPolicy.opened(state, today + 365), today + 365))
    }

    // ---- a clock that moved ----

    @Test
    fun aClockSetBackPastTheLastAskDoesNotSilenceTheAppForever() {
        val farFuture = 5_000L
        val state = ReviewPromptPolicy.asked(usedFor(minDays), farFuture, farFuture * day)

        // The phone's date is now 200, long before the "ask" it remembers. That ask never happened
        // on this calendar; the period must not be waiting for a date that is years away.
        assertTrue(due(state, 200))
    }

    // ---- the promise to the person ----

    @Test
    fun theBudgetStaysInsideWhatPlayDocumentsAndWhatIsNotNagging() {
        assertTrue(
                "Play says asking again within a month may show nothing, so asking sooner only burns the quota",
                quietDays >= 30)
        assertTrue(
                "a prompt that comes back more than a few times in an install's life is nagging",
                ReviewPromptPolicy.MAX_ASKS <= 5)
        assertTrue(
                "one day of use is somebody who has not seen the app yet",
                minDays >= 2)
    }
}
