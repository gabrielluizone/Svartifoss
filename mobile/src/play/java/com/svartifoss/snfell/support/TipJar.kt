package com.svartifoss.snfell.support

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * The tip option's connection to Google Play Billing: finds out which tip amounts can be bought
 * on this device, starts the purchase and settles it. `play` flavor only - see [TipProducts] for
 * what a tip is and, as importantly, what it is not.
 *
 * Process-scoped on purpose. A purchase is a conversation with Play's own screen on top of ours,
 * which the system may end by recreating or even killing the activity that started it, and the
 * answer arrives at whichever process is alive. Tying the client to an activity would lose exactly
 * the answers that matter; tying it to the process loses none, and the owned-purchases query at the
 * next connection catches the one case a process death still drops.
 *
 * Everything that touches state runs on the main thread (the scope below, and Billing's own
 * callbacks), which is why [TipLedger] needs no locking.
 *
 * ## Nothing is verified, deliberately
 *
 * Apps that sell something verify purchases on a server so a forged one cannot unlock it. A forged
 * tip unlocks nothing, so a server would defend an empty vault, and the project deliberately has no
 * backend of its own. If this ever gates anything, that changes - and so does the answer to whether
 * it may be a tip at all.
 */
internal object TipJar {

    /** The most often the products are re-read; the drawer is rebuilt with every Activity. */
    private const val REFRESH_TTL_MS = 10 * 60_000L

    /** Past this, Play is not coming back for this attempt; the next refresh builds a fresh client. */
    private const val CONNECT_TIMEOUT_MS = 15_000L

    private const val CONSUME_ATTEMPTS = 3
    private const val CONSUME_RETRY_DELAY_MS = 5_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Serialises connect + query, so two refreshes can never ask one client to connect twice. */
    private val connectLock = Mutex()

    private val ledger = TipLedger()

    private val offerState = MutableStateFlow<List<TipOffer>>(emptyList())

    /**
     * The amounts on sale right now, cheapest first. Empty while Play has not answered, when Play
     * is not available here, and when no tip product exists yet - all of which look the same to the
     * person: no option.
     */
    val offers: StateFlow<List<TipOffer>> = offerState.asStateFlow()

    private val eventChannel = Channel<TipEvent>(Channel.BUFFERED)

    /**
     * What to tell the person, queued until an activity is there to say it. A channel and not a
     * shared flow because the answer to a purchase routinely arrives while our activity is stopped
     * behind Play's sheet, and an event with nobody collecting must wait rather than vanish.
     */
    val events: Flow<TipEvent> = eventChannel.receiveAsFlow()

    private var client: BillingClient? = null
    private var details: Map<String, ProductDetails> = emptyMap()
    private var lastRefreshAt = 0L
    private var refreshing = false

    /**
     * Connects if needed, reads the tip products and settles any tip that was paid for and never
     * consumed. Cheap to call: it does nothing inside [REFRESH_TTL_MS] of the last attempt, however
     * that attempt ended, so an unreachable Play is not asked again with every screen rotation.
     */
    fun refresh(context: Context) {
        if (refreshing) return
        val now = SystemClock.elapsedRealtime()
        if (lastRefreshAt != 0L && now - lastRefreshAt < REFRESH_TTL_MS) return
        refreshing = true
        val appContext = context.applicationContext
        scope.launch {
            try {
                connectLock.withLock {
                    val billing = clientFor(appContext)
                    if (!connect(billing)) return@withLock
                    loadOffers(billing)
                    settleOwned(billing, quiet = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Reading the tip products failed")
            } finally {
                lastRefreshAt = SystemClock.elapsedRealtime()
                refreshing = false
            }
        }
    }

    /**
     * Opens Play's purchase sheet for [productId]. Must be called from the main thread with the
     * activity that is on screen. The outcome arrives through [events] - not from here - because
     * the person may take a minute, or leave and come back.
     */
    fun start(activity: Activity, productId: String) {
        val billing = client?.takeIf { it.isReady }
        val product = details[productId]
        if (billing == null || product == null) {
            Timber.w("Tip %s cannot start: client ready=%s, product known=%s",
                    productId, billing != null, product != null)
            eventChannel.trySend(TipEvent.Failed)
            return
        }
        val flow = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(product)
                                .apply { offerTokenFor(product)?.let { setOfferToken(it) } }
                                .build()))
                .build()
        val result = billing.launchBillingFlow(activity, flow)
        if (result.responseCode != BillingResponseCode.OK) {
            Timber.w("Play refused to open the tip sheet: %d %s", result.responseCode, result.debugMessage)
            // The same classification a finished purchase gets, so an "already owned" answer repairs
            // itself here as well.
            scope.launch { execute(ledger.onPurchasesUpdated(result.responseCode, emptyList()), billing) }
        }
    }

    private fun clientFor(context: Context): BillingClient =
            client ?: BillingClient.newBuilder(context)
                    .setListener { result, purchases -> onPurchasesUpdated(result, purchases) }
                    // Required for one-time products since Billing 7. A payment that Play cannot
                    // confirm at once (cash at a shop, a bank slip) completes later, and the ledger
                    // handles it arriving as a second update or at the next startup.
                    .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                    .enableAutoServiceReconnection()
                    .build()
                    .also { client = it }

    /** True when connected. False means Play is not available here, which is an ordinary state. */
    private suspend fun connect(billing: BillingClient): Boolean {
        if (billing.isReady) return true
        val connected = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
            suspendCancellableCoroutine<Boolean> { continuation ->
                billing.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(result: BillingResult) {
                        if (result.responseCode != BillingResponseCode.OK) {
                            Timber.i("Play Billing is not available here: %d %s",
                                    result.responseCode, result.debugMessage)
                        }
                        // With auto reconnection this can be called again on a later reconnect.
                        if (continuation.isActive) continuation.resume(result.responseCode == BillingResponseCode.OK)
                    }

                    override fun onBillingServiceDisconnected() = Unit
                })
            }
        }
        if (connected == null) {
            // Timed out while connecting: give up on this client rather than leave one that is
            // neither connected nor failed, which a second startConnection() would refuse.
            Timber.w("Play Billing did not answer within %d ms", CONNECT_TIMEOUT_MS)
            billing.endConnection()
            client = null
        }
        return connected == true
    }

    private suspend fun loadOffers(billing: BillingClient) {
        val params = QueryProductDetailsParams.newBuilder()
                .setProductList(TipProducts.IDS.map { id ->
                    QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(id)
                            .setProductType(BillingClient.ProductType.INAPP)
                            .build()
                })
                .build()
        val result = billing.queryProductDetails(params)
        if (result.billingResult.responseCode != BillingResponseCode.OK) {
            Timber.w("The tip products could not be read: %d %s",
                    result.billingResult.responseCode, result.billingResult.debugMessage)
            return
        }
        val found = result.productDetailsList.orEmpty()
        details = found.associateBy { it.productId }
        offerState.value = TipCatalogue.options(found.mapNotNull(::offerOf))
        Timber.i("Tip products on sale: %d of %d", offerState.value.size, TipProducts.IDS.size)
    }

    private fun offerOf(product: ProductDetails): TipOffer? {
        val price = purchaseOptionOf(product) ?: return null
        return TipOffer(product.productId, price.formattedPrice, price.priceAmountMicros, price.priceCurrencyCode)
    }

    /**
     * The price a tip is sold at. A product made the ordinary way has one purchase option and that
     * is it. Play Console can also give a one-time product several options or offers, in which case
     * Billing wants the buyer's choice named by an offer token; there is no buyer choice here, so
     * the base option (the one with no offer id) is used, for the price shown and for the purchase
     * alike - the two must come from the same option or the sheet would charge a different amount
     * than the button promised.
     */
    private fun purchaseOptionOf(product: ProductDetails): ProductDetails.OneTimePurchaseOfferDetails? {
        val options = product.oneTimePurchaseOfferDetailsList.orEmpty()
        if (options.size < 2) return product.oneTimePurchaseOfferDetails ?: options.firstOrNull()
        return options.firstOrNull { it.offerId == null } ?: options.first()
    }

    /** Only a product with several options needs naming one; passing a token to a single option is not how Billing documents it. */
    private fun offerTokenFor(product: ProductDetails): String? =
            if (product.oneTimePurchaseOfferDetailsList.orEmpty().size < 2) null
            else purchaseOptionOf(product)?.offerToken

    private suspend fun settleOwned(billing: BillingClient, quiet: Boolean) {
        val owned = billing.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build())
        if (owned.billingResult.responseCode != BillingResponseCode.OK) {
            Timber.w("What is owned could not be read: %d", owned.billingResult.responseCode)
            return
        }
        execute(ledger.onOwned(owned.purchasesList.orEmpty().map(::tipPurchaseOf), quiet), billing)
    }

    private fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        val billing = client ?: return
        if (result.responseCode != BillingResponseCode.OK && result.responseCode != BillingResponseCode.USER_CANCELED) {
            Timber.w("A tip ended with %d: %s", result.responseCode, result.debugMessage)
        }
        val step = ledger.onPurchasesUpdated(result.responseCode, purchases.orEmpty().map(::tipPurchaseOf))
        scope.launch { execute(step, billing) }
    }

    /** Says what the step says to say, then consumes; a quiet resync comes last. */
    private suspend fun execute(step: TipStep, billing: BillingClient) {
        step.events.forEach { eventChannel.trySend(it) }
        step.consume.forEach { consume(billing, it) }
        if (step.quietResync) settleOwned(billing, quiet = true)
    }

    /**
     * Consuming is what acknowledges a consumable, and Play refunds a purchase nobody acknowledged
     * within three days - so a failure is retried a few times here and then left for the next
     * startup's query to pick up, rather than given up on.
     */
    private suspend fun consume(billing: BillingClient, token: String) {
        for (attempt in 1..CONSUME_ATTEMPTS) {
            val code = billing.consumePurchase(ConsumeParams.newBuilder().setPurchaseToken(token).build())
                    .billingResult.responseCode
            // Not owned: already consumed, by a retry that did get through or by another device.
            if (code == BillingResponseCode.OK || code == BillingResponseCode.ITEM_NOT_OWNED) {
                ledger.consumed(token)
                return
            }
            Timber.w("Consuming a tip failed with %d (attempt %d of %d)", code, attempt, CONSUME_ATTEMPTS)
            if (attempt < CONSUME_ATTEMPTS) delay(CONSUME_RETRY_DELAY_MS)
        }
        ledger.consumeFailed(token)
    }

    private fun tipPurchaseOf(purchase: Purchase) = TipPurchase(
            token = purchase.purchaseToken,
            productIds = purchase.products,
            state = when (purchase.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> TipPurchaseState.PURCHASED
                Purchase.PurchaseState.PENDING -> TipPurchaseState.PENDING
                else -> TipPurchaseState.OTHER
            })
}
