package com.svartifoss.snfell.review

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import com.svartifoss.snfell.NotificationService
import com.svartifoss.snfell.R
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Play Store build: asks for a rating with Google Play's own in-app review card, which slides up
 * over the app, takes the stars and the optional comment, and posts the review - so nobody has to
 * leave the app, search the Store for it and find the right page. The github build has no Store
 * to post to and gets the inert twin in `src/github`; `src/main` only calls [attach] (MainActivity)
 * and [showNow] (the Developer row). FlavorReviewIsolationTest pins that nothing of the library
 * reaches `src/main` or the github build.
 *
 * ## Everything here follows from what the API does and does not say
 *
 * The card is drawn and owned by the Play Store app. The app gets no answer about it: not whether
 * the card appeared (Play keeps a quota it will not disclose and may simply decline), not whether
 * anybody rated, not what they wrote. Three things follow. An ask is counted when it is handed to
 * Play, because nothing later could correct that count. A card that did not appear is not an error
 * and nobody is told about it. And there is no "Rate us" button that raises it: Play says not to,
 * since a button that does nothing because the quota is spent reads as a broken one.
 *
 * Nothing of ours comes before the card either - no "are you enjoying the app?" - which Play also
 * forbids, and which would be a filter on who gets to leave a rating besides.
 *
 * What the app keeps is a few counters in a private preferences file of its own (see
 * [ReviewPromptState]): not the default file, which is mirrored to the watch, and nothing leaves
 * the phone.
 */
object ReviewPrompt {

    /** Whether this build can ask for a rating at all. Only a build that came from the Store can. */
    const val SUPPORTED = true

    private const val PREFS = "review_prompt"
    private const val KEY_DAYS_USED = "days_used"
    private const val KEY_LAST_DAY_USED = "last_day_used"
    private const val KEY_LAST_ATTEMPT_DAY = "last_attempt_day"
    private const val KEY_ASKS = "asks"
    private const val KEY_LAST_ASK_AT = "last_ask_at"

    /**
     * How long the screen has to stay put and in front before the card is raised. The app is
     * opened for a reason, and a sheet that lands on the first frame, mid-tap, takes the touch the
     * person was making.
     */
    private const val SETTLE_MS = 2_500L

    /** Past this, Play is not coming back for this attempt. */
    private const val REQUEST_TIMEOUT_MS = 10_000L

    /**
     * Wires the prompt to [activity] for as long as it lives. Call once, from `onCreate`. Every
     * return to the screen counts as using the app that day, and decides again whether to ask.
     */
    fun attach(activity: AppCompatActivity) {
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                considerAsking(activity)
            }
        }
    }

    /**
     * Asks Play for the card right now, whatever [ReviewPromptPolicy] says: the Developer row's way
     * of seeing it. It is not counted against the budget, and it says when Play had nothing to
     * show, because here a silent failure would only look like a broken row.
     *
     * Play's quota applies to a build installed from the production track and does not to one
     * installed from internal testing, which is the one place this is for. A build put on the phone
     * with adb gets no card at all, and says so.
     */
    fun showNow(activity: AppCompatActivity) {
        activity.lifecycleScope.launch {
            val manager = ReviewManagerFactory.create(activity.applicationContext)
            val info = requestCard(manager)
            if (info == null) {
                Toast.makeText(activity, R.string.dev_show_rating_card_unavailable, Toast.LENGTH_LONG).show()
                return@launch
            }
            manager.launchReviewFlow(activity, info)
        }
    }

    private suspend fun considerAsking(activity: AppCompatActivity) {
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = LocalDate.now().toEpochDay()

        val before = read(prefs)
        val counted = ReviewPromptPolicy.opened(before, today)
        if (counted != before) write(prefs, counted)

        if (!ReviewPromptPolicy.isDue(counted, today, System.currentTimeMillis())) return

        // Before notification access is granted nothing in the app works, and what would be rated
        // is a setup screen. Asking has to wait for somebody who got past it.
        if (!NotificationService.isEnabled(activity)) return

        delay(SETTLE_MS)
        if (!isShowable(activity)) return

        val manager = ReviewManagerFactory.create(activity.applicationContext)
        val info = requestCard(manager)
        if (info == null) {
            // Spends today's attempt without spending an ask: nothing was shown, and a Play that
            // cannot answer here is tried again tomorrow, not with every screen of the day.
            write(prefs, ReviewPromptPolicy.attempted(read(prefs), today))
            return
        }

        // The request took a moment, and the person may have moved on, or something may have come
        // up over the app: a dialog, the notification shade. Asking again at the next opening is
        // not a cost; a card over the wrong screen is.
        if (!isShowable(activity)) return

        write(prefs, ReviewPromptPolicy.asked(read(prefs), today, System.currentTimeMillis()))
        Timber.i("Handing the review card to Play (ask %d of %d)", read(prefs).asks, ReviewPromptPolicy.MAX_ASKS)
        // Not awaited: the card is its own screen over this one, so this coroutine is cancelled
        // the moment it appears, and nothing is left to do after it closes.
        manager.launchReviewFlow(activity, info)
    }

    /**
     * In front, on screen, and not under a dialog or the shade. A dialog or the shade takes the
     * window's focus, which makes this one check cover the notification-access prompt, the face-reset
     * recommendation and anything added later without any of them having to know about this.
     */
    private fun isShowable(activity: AppCompatActivity): Boolean =
            activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && activity.hasWindowFocus()

    /** Play's handle for the card, or null when Play cannot give one here. Cancellation passes through. */
    private suspend fun requestCard(manager: ReviewManager): ReviewInfo? {
        val info = try {
            withTimeoutOrNull(REQUEST_TIMEOUT_MS) { manager.requestReviewFlow().await() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // No throwable on purpose: the usual cause is a build that did not come from the Store,
            // and that is not a crash to report.
            Timber.i("Play has no review card for this build: %s", e.message)
            return null
        }
        if (info == null) Timber.i("Play did not answer the review request within %d ms", REQUEST_TIMEOUT_MS)
        return info
    }

    private fun read(prefs: SharedPreferences) = ReviewPromptState(
            daysUsed = prefs.getInt(KEY_DAYS_USED, 0),
            lastDayUsed = prefs.getLong(KEY_LAST_DAY_USED, ReviewPromptState.NEVER),
            lastAttemptDay = prefs.getLong(KEY_LAST_ATTEMPT_DAY, ReviewPromptState.NEVER),
            asks = prefs.getInt(KEY_ASKS, 0),
            lastAskAtMillis = prefs.getLong(KEY_LAST_ASK_AT, ReviewPromptState.NEVER))

    private fun write(prefs: SharedPreferences, state: ReviewPromptState) {
        prefs.edit()
                .putInt(KEY_DAYS_USED, state.daysUsed)
                .putLong(KEY_LAST_DAY_USED, state.lastDayUsed)
                .putLong(KEY_LAST_ATTEMPT_DAY, state.lastAttemptDay)
                .putInt(KEY_ASKS, state.asks)
                .putLong(KEY_LAST_ASK_AT, state.lastAskAtMillis)
                .apply()
    }
}
