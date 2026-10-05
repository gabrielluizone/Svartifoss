package com.svartifoss.snfell.support

import com.android.billingclient.api.BillingClient.BillingResponseCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TipLedgerTest {

    private val ok = BillingResponseCode.OK

    private fun tip(
            token: String,
            state: TipPurchaseState = TipPurchaseState.PURCHASED,
            products: List<String> = listOf("support_tip_1")
    ) = TipPurchase(token, products, state)

    // ---- a paid tip ----

    @Test
    fun aPaidTipIsThankedAndConsumed() {
        val step = TipLedger().onPurchasesUpdated(ok, listOf(tip("a")))

        assertEquals(listOf<TipEvent>(TipEvent.Thanks), step.events)
        assertEquals(listOf("a"), step.consume)
        assertTrue(!step.quietResync)
    }

    @Test
    fun theSamePurchaseSeenAgainWhileItIsBeingConsumedIsLeftAlone() {
        val ledger = TipLedger()
        ledger.onPurchasesUpdated(ok, listOf(tip("a")))

        // The startup query can run while the consume from the update is still in flight.
        val again = ledger.onOwned(listOf(tip("a")))

        assertEquals(TipStep(), again)
    }

    @Test
    fun aConsumedPurchaseThatPlaysCacheStillListsIsNotConsumedAgain() {
        val ledger = TipLedger()
        ledger.onPurchasesUpdated(ok, listOf(tip("a")))
        ledger.consumed("a")

        assertEquals(TipStep(), ledger.onOwned(listOf(tip("a"))))
    }

    @Test
    fun aFailedConsumeIsTriedAgainAtTheNextQueryWithoutThankingTwice() {
        val ledger = TipLedger()
        ledger.onPurchasesUpdated(ok, listOf(tip("a")))
        ledger.consumeFailed("a")

        val retry = ledger.onOwned(listOf(tip("a")))

        assertTrue("the person was already thanked", retry.events.isEmpty())
        assertEquals(listOf("a"), retry.consume)
    }

    @Test
    fun twoTipsInOneUpdateAreThankedOnceAndBothConsumed() {
        val step = TipLedger().onPurchasesUpdated(ok, listOf(tip("a"), tip("b", products = listOf("support_tip_2"))))

        assertEquals(listOf<TipEvent>(TipEvent.Thanks), step.events)
        assertEquals(listOf("a", "b"), step.consume)
    }

    @Test
    fun aTokenRepeatedInOneUpdateIsConsumedOnce() {
        val step = TipLedger().onPurchasesUpdated(ok, listOf(tip("a"), tip("a")))

        assertEquals(listOf("a"), step.consume)
    }

    // ---- a payment that is not final yet ----

    @Test
    fun aPendingTipIsAnnouncedAndNotConsumed() {
        val step = TipLedger().onPurchasesUpdated(ok, listOf(tip("b", TipPurchaseState.PENDING)))

        assertEquals(listOf<TipEvent>(TipEvent.Pending), step.events)
        assertTrue("consuming a pending purchase is an error", step.consume.isEmpty())
    }

    @Test
    fun aPendingTipIsThankedAndConsumedWhenItClears() {
        val ledger = TipLedger()
        ledger.onPurchasesUpdated(ok, listOf(tip("b", TipPurchaseState.PENDING)))

        val cleared = ledger.onPurchasesUpdated(ok, listOf(tip("b", TipPurchaseState.PURCHASED)))

        assertEquals(listOf<TipEvent>(TipEvent.Thanks), cleared.events)
        assertEquals(listOf("b"), cleared.consume)
    }

    @Test
    fun aPendingTipThatClearedWhileTheAppWasClosedIsThankedAtStartup() {
        val step = TipLedger().onOwned(listOf(tip("b")))

        assertEquals(listOf<TipEvent>(TipEvent.Thanks), step.events)
        assertEquals(listOf("b"), step.consume)
    }

    @Test
    fun aPendingTipFoundAtStartupIsNotAnnouncedAgain() {
        // It was announced when it started; announcing it on every launch would be a nag.
        assertEquals(TipStep(), TipLedger().onOwned(listOf(tip("b", TipPurchaseState.PENDING))))
    }

    // ---- the quiet repair after "already owned" ----

    @Test
    fun aQuietQueryConsumesWithoutThankingNowOrLater() {
        val ledger = TipLedger()

        val quiet = ledger.onOwned(listOf(tip("a")), quiet = true)
        assertTrue(quiet.events.isEmpty())
        assertEquals(listOf("a"), quiet.consume)

        ledger.consumeFailed("a")
        assertTrue("already settled quietly, never thanked", ledger.onOwned(listOf(tip("a"))).events.isEmpty())
    }

    @Test
    fun anAlreadyOwnedAnswerFailsTheAttemptAndAsksForAQuietRepair() {
        val step = TipLedger().onPurchasesUpdated(BillingResponseCode.ITEM_ALREADY_OWNED, emptyList())

        assertEquals(listOf<TipEvent>(TipEvent.Failed), step.events)
        assertTrue(step.consume.isEmpty())
        assertTrue(step.quietResync)
    }

    // ---- ending without a purchase ----

    @Test
    fun closingPlaysSheetSaysNothing() {
        assertEquals(TipStep(), TipLedger().onPurchasesUpdated(BillingResponseCode.USER_CANCELED, emptyList()))
    }

    @Test
    fun everyOtherAnswerIsAFailureThatConsumesNothing() {
        listOf(
                BillingResponseCode.SERVICE_UNAVAILABLE,
                BillingResponseCode.BILLING_UNAVAILABLE,
                BillingResponseCode.ITEM_UNAVAILABLE,
                BillingResponseCode.DEVELOPER_ERROR,
                BillingResponseCode.ERROR,
                BillingResponseCode.ITEM_NOT_OWNED,
                BillingResponseCode.NETWORK_ERROR,
                BillingResponseCode.SERVICE_DISCONNECTED,
                BillingResponseCode.FEATURE_NOT_SUPPORTED
        ).forEach { code ->
            val step = TipLedger().onPurchasesUpdated(code, listOf(tip("a")))
            assertEquals("code $code", listOf<TipEvent>(TipEvent.Failed), step.events)
            assertTrue("code $code must not consume", step.consume.isEmpty())
        }
    }

    @Test
    fun anOkWithNoPurchasesSaysNothing() {
        assertEquals(TipStep(), TipLedger().onPurchasesUpdated(ok, emptyList()))
    }

    // ---- only ever our own purchases ----

    @Test
    fun aPurchaseOfSomethingElseIsNeverConsumedOrThankedFor() {
        val other = tip("x", products = listOf("premium_forever"))

        assertEquals(TipStep(), TipLedger().onPurchasesUpdated(ok, listOf(other)))
        assertEquals(TipStep(), TipLedger().onOwned(listOf(other)))
    }

    @Test
    fun aPurchaseThatIncludesAnythingButTipsIsLeftAlone() {
        val mixed = tip("x", products = listOf("support_tip_1", "premium_forever"))

        assertEquals(TipStep(), TipLedger().onOwned(listOf(mixed)))
    }

    @Test
    fun aPurchaseWithoutATokenOrProductsCannotBeConsumed() {
        assertEquals(TipStep(), TipLedger().onOwned(listOf(tip(""), TipPurchase("t", emptyList(), TipPurchaseState.PURCHASED))))
    }

    @Test
    fun anUnspecifiedStateIsNeitherThankedNorConsumed() {
        assertEquals(TipStep(), TipLedger().onOwned(listOf(tip("a", TipPurchaseState.OTHER))))
    }

    // ---- classification ----

    @Test
    fun responseCodesAreClassifiedForTips() {
        assertEquals(TipResponseKind.OK, TipResponse.kind(BillingResponseCode.OK))
        assertEquals(TipResponseKind.CANCELED, TipResponse.kind(BillingResponseCode.USER_CANCELED))
        assertEquals(TipResponseKind.ALREADY_OWNED, TipResponse.kind(BillingResponseCode.ITEM_ALREADY_OWNED))
        assertEquals(TipResponseKind.FAILED, TipResponse.kind(BillingResponseCode.ERROR))
        assertEquals("a code Billing has not defined yet is a failure, not a success",
                TipResponseKind.FAILED, TipResponse.kind(9_999))
    }
}
