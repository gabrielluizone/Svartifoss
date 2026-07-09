package com.svartifoss.snfell.billing

import android.app.Activity
import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.preference.PreferenceManager
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.common.MiscPreferences
import com.svartifoss.snfell.util.launchWithPlayServicesErrorHandling
import com.matejdro.wearutils.preferences.definition.Preferences
import com.matejdro.wearutils.preferencesync.PreferencePusher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** One-time (lifetime) Premium unlock via the Play Billing Library. Owns the sole [BillingClient]
 *  connection for the app - app-scoped/singleton, same lifetime as [com.svartifoss.snfell.config.WatchInfoProvider].
 *
 *  Entitlement is cached in the default SharedPreferences as [MiscPreferences.PREMIUM_UNLOCKED],
 *  which is enough to make it reach the watch for free: writing it there triggers the exact same
 *  [PreferencePusher] path any other watch-facing preference already uses when edited in Settings -
 *  see [MiscSettingsFragment.pushPreferencesToWatch]/[WatchFacePrefsFragment]'s equivalent. This
 *  class additionally pushes immediately itself, since entitlement can change while neither of
 *  those fragments is open (e.g. right after [queryExistingPurchases] on app start). */
@Singleton
class PurchaseManager @Inject constructor(private val context: Context) : PurchasesUpdatedListener {

    private val coroutineScope = CoroutineScope(Job() + Dispatchers.Main)

    private val billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()

    private var premiumProductDetails: ProductDetails? = null

    private val _isPremium = MutableLiveData(
            Preferences.getBoolean(PreferenceManager.getDefaultSharedPreferences(context), MiscPreferences.PREMIUM_UNLOCKED)
    )

    /** Whether the Premium unlock is currently active. Seeded from the last known cached value so
     *  UI can gate immediately, then refreshed from Play Billing once connected. */
    val isPremium: LiveData<Boolean> get() = _isPremium

    init {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                    Timber.w("Billing setup failed: %s", billingResult.debugMessage)
                    return
                }

                coroutineScope.launchWithPlayServicesErrorHandling(context) {
                    queryProductDetails()
                    queryExistingPurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                // The library retries the connection on the next billingClient call - nothing to
                // do here, this app has no feature that requires billing to always be connected.
            }
        })
    }

    private suspend fun queryProductDetails() {
        val params = QueryProductDetailsParams.newBuilder()
                .setProductList(listOf(
                        QueryProductDetailsParams.Product.newBuilder()
                                .setProductId(PREMIUM_PRODUCT_ID)
                                .setProductType(BillingClient.ProductType.INAPP)
                                .build()
                ))
                .build()

        val result = billingClient.queryProductDetails(params)
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            Timber.w("Premium product query failed: %s", result.billingResult.debugMessage)
            return
        }

        premiumProductDetails = result.productDetailsList?.firstOrNull()
    }

    private suspend fun queryExistingPurchases() {
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        val result = billingClient.queryPurchasesAsync(params)
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            Timber.w("Premium purchase query failed: %s", result.billingResult.debugMessage)
            return
        }

        handlePurchases(result.purchasesList)
    }

    /** Opens the Play Billing purchase sheet for the lifetime Premium unlock. No-op (with a log)
     *  if product details haven't loaded yet - callers can retry after a moment or simply let the
     *  user tap again. */
    fun launchPurchaseFlow(activity: Activity) {
        val productDetails = premiumProductDetails
        if (productDetails == null) {
            Timber.w("Premium purchase flow requested before product details were ready")
            return
        }

        val params = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(productDetails)
                                .build()
                ))
                .build()

        billingClient.launchBillingFlow(activity, params)
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            if (billingResult.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
                Timber.w("Premium purchase flow failed: %s", billingResult.debugMessage)
            }
            return
        }

        coroutineScope.launchWithPlayServicesErrorHandling(context) {
            handlePurchases(purchases.orEmpty())
        }
    }

    private suspend fun handlePurchases(purchases: List<Purchase>) {
        val activePurchase = purchases.firstOrNull {
            it.products.contains(PREMIUM_PRODUCT_ID) && it.purchaseState == Purchase.PurchaseState.PURCHASED
        }

        if (activePurchase != null && !activePurchase.isAcknowledged) {
            val ackParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(activePurchase.purchaseToken)
                    .build()
            val ackResult = billingClient.acknowledgePurchase(ackParams)
            if (ackResult.responseCode != BillingClient.BillingResponseCode.OK) {
                Timber.w("Failed to acknowledge premium purchase: %s", ackResult.debugMessage)
            }
        }

        setPremium(activePurchase != null)
    }

    private suspend fun setPremium(unlocked: Boolean) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        if (Preferences.getBoolean(preferences, MiscPreferences.PREMIUM_UNLOCKED) == unlocked) {
            _isPremium.postValue(unlocked)
            return
        }

        val editor = preferences.edit()
        Preferences.putBoolean(editor, MiscPreferences.PREMIUM_UNLOCKED, unlocked)
        editor.apply()
        _isPremium.postValue(unlocked)

        PreferencePusher.pushPreferences(context, preferences, CommPaths.PREFERENCES_PREFIX, true)
    }

    companion object {
        const val PREMIUM_PRODUCT_ID = "premium_unlock"
    }
}
