package com.svartifoss.snfell.support

import com.android.billingclient.api.BillingClient.BillingResponseCode

/** A purchase as the tip logic sees it: no Billing types, so a plain JVM test can drive it. */
internal data class TipPurchase(
        val token: String,
        val productIds: List<String>,
        val state: TipPurchaseState
) {
    /**
     * Whether this is one of ours. **Every** product on it must be a tip, and the token must be
     * usable: consuming a purchase that carries anything else would hand back something the person
     * paid for and keeps - a non-consumable entitlement a later version of the listing might sell
     * becomes buyable again the moment it is consumed, and that cannot be undone from here.
     */
    val isTip: Boolean
        get() = token.isNotBlank() && productIds.isNotEmpty() &&
                productIds.all { it in TipProducts.IDS }
}

internal enum class TipPurchaseState { PURCHASED, PENDING, OTHER }

/** What the person is told. Shown by whichever activity is on screen, in its own language. */
internal sealed interface TipEvent {
    /** The payment went through. */
    data object Thanks : TipEvent

    /** The payment was started and Play is waiting for it to be confirmed (cash, bank slip). */
    data object Pending : TipEvent

    /** It did not happen. Cancelling is not a failure and never produces this. */
    data object Failed : TipEvent
}

/** What to do after a purchase update or a query of what is owned. */
internal data class TipStep(
        val events: List<TipEvent> = emptyList(),
        /** Purchase tokens to consume now. Consuming also acknowledges, which Play requires. */
        val consume: List<String> = emptyList(),
        /**
         * Query what is owned and settle it **without** thanking anyone. Asked for when Play says an
         * earlier tip is still unconsumed: the person is trying to tip *now*, so a "thank you"
         * would read as if this attempt had worked, when the reason it did not is a leftover.
         */
        val quietResync: Boolean = false
)

/** How a Billing response code matters to a tip. */
internal enum class TipResponseKind {
    OK,

    /** The person closed Play's sheet. Not an error and not worth a message. */
    CANCELED,

    /** An earlier tip of the same amount was paid for and never consumed. */
    ALREADY_OWNED,

    /** Everything else: Play unavailable, offline, a product that does not exist, a developer error. */
    FAILED
}

internal object TipResponse {
    fun kind(responseCode: Int): TipResponseKind = when (responseCode) {
        BillingResponseCode.OK -> TipResponseKind.OK
        BillingResponseCode.USER_CANCELED -> TipResponseKind.CANCELED
        BillingResponseCode.ITEM_ALREADY_OWNED -> TipResponseKind.ALREADY_OWNED
        else -> TipResponseKind.FAILED
    }
}

/**
 * Decides, for each thing Play tells us about a purchase, whether to thank the person, whether to
 * consume it, and whether to stay quiet. Pure bookkeeping around three sets, so the rules that
 * decide whether somebody is thanked twice or never are tested rather than hoped for.
 *
 * ## The two things this has to get right
 *
 * **A paid tip must be consumed.** Play refunds a purchase that is not acknowledged within three
 * days, and `consumeAsync` is what acknowledges a consumable. The same purchase reaches us twice on
 * a normal path (the update that ends the flow, then the owned-purchases query at the next
 * startup), and a consume that failed has to be tried again without telling the person a second
 * time. [thanked] and [settled] are what keep those two apart: a token is thanked once, ever, and a
 * token that is consumed is never offered for consuming again, but one whose consume failed stays
 * eligible.
 *
 * **A pending payment is not a tip yet.** It is announced once and consumed only after Play reports
 * it as purchased - consuming a pending purchase is an error, and thanking one would be wrong if
 * the payment later falls through.
 *
 * Not thread-safe by design: [TipJar] calls it from the main thread only, and a lock here would only
 * hide a caller that wandered off.
 */
internal class TipLedger {

    private val thanked = HashSet<String>()
    private val settled = HashSet<String>()
    private val inFlight = HashSet<String>()

    /** [purchases] is what `PurchasesUpdatedListener` delivered for [responseCode]; may be empty. */
    fun onPurchasesUpdated(responseCode: Int, purchases: List<TipPurchase>): TipStep =
            when (TipResponse.kind(responseCode)) {
                TipResponseKind.OK -> {
                    val claim = claim(purchases)
                    val ours = purchases.filter { it.isTip }
                    TipStep(
                            events = when {
                                claim.newlyThanked -> listOf(TipEvent.Thanks)
                                claim.tokens.isEmpty() && ours.any { it.state == TipPurchaseState.PENDING } ->
                                    listOf(TipEvent.Pending)
                                else -> emptyList()
                            },
                            consume = claim.tokens
                    )
                }
                TipResponseKind.CANCELED -> TipStep()
                TipResponseKind.ALREADY_OWNED -> TipStep(
                        events = listOf(TipEvent.Failed),
                        quietResync = true
                )
                TipResponseKind.FAILED -> TipStep(events = listOf(TipEvent.Failed))
            }

    /**
     * What Play says is owned right now: asked at startup and after an "already owned" answer.
     * Finds the tips that were paid for and never consumed - the app was killed between the payment
     * and the consume, or a pending payment cleared while it was closed. [quiet] consumes them
     * without a thank-you; see [TipStep.quietResync].
     */
    fun onOwned(purchases: List<TipPurchase>, quiet: Boolean = false): TipStep {
        val claim = claim(purchases)
        return TipStep(
                events = if (claim.newlyThanked && !quiet) listOf(TipEvent.Thanks) else emptyList(),
                consume = claim.tokens
        )
    }

    /** The consume for [token] went through. */
    fun consumed(token: String) {
        inFlight.remove(token)
        settled.add(token)
    }

    /** The consume for [token] did not go through; the next [onOwned] offers it again. */
    fun consumeFailed(token: String) {
        inFlight.remove(token)
    }

    private class Claim(val tokens: List<String>, val newlyThanked: Boolean)

    /** Takes the purchased tips that nobody is consuming yet; marks them in flight and thanked. */
    private fun claim(purchases: List<TipPurchase>): Claim {
        val due = purchases
                .filter { it.isTip && it.state == TipPurchaseState.PURCHASED }
                .map { it.token }
                .distinct()
                .filter { it !in settled && it !in inFlight }
        inFlight.addAll(due)
        var newlyThanked = false
        due.forEach { if (thanked.add(it)) newlyThanked = true }
        return Claim(due, newlyThanked)
    }
}
