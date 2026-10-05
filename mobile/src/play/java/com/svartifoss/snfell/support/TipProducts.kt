package com.svartifoss.snfell.support

/**
 * The tip products the Play build sells, and how what Google Play reports about them becomes the
 * list the dialog shows.
 *
 * ## What a tip is
 *
 * The Play listing is a paid app, so whoever installed it already has every feature, and a tip is
 * not a way to get anything more. These are **consumable one-time products** whose only effect is
 * that money moves: nothing in `src/main` can learn that a tip happened (`FlavorSupportIsolationTest`
 * pins that the billing code is reachable from nowhere but this flavor), so none of the app's
 * behaviour can ever depend on one. Consumable rather than one-time-forever for the reason that
 * matters to a person: a non-consumable can be bought once, and somebody who wants to say thanks
 * twice should be able to.
 *
 * Google Play Billing is used because Play's Payments policy requires it for anything sold inside
 * a Play listing - a button that leads to any other way of paying is not allowed, which is why the
 * github build's Buy Me a Coffee and Ko-fi links do not exist in this one.
 *
 * ## Where the amounts come from
 *
 * Not from this code. Each product's price is set in Play Console, per country, and the app shows
 * the string Play formatted for the signed-in account (`R$ 10,00`, `$1.99`), so a tier can be
 * repriced, or converted into a local currency, without a release. See `docs/play-tips.md` for the
 * products to create.
 */
internal object TipProducts {

    /**
     * The Play Console product ids, cheapest tier first. A product that is not created, or is
     * inactive, is simply absent from what Play returns, so the dialog shows however many exist and
     * the option hides itself when none do. The ids are permanent in Play Console - a deleted one
     * can never be reused - hence neutral names that survive a repricing.
     */
    val IDS: List<String> = listOf("support_tip_1", "support_tip_2", "support_tip_3", "support_tip_4")
}

/** One amount Google Play offered for a tip, already formatted for this account. */
internal data class TipOffer(
        val productId: String,
        /** The price as Play formats it for this country and currency; shown verbatim. */
        val formattedPrice: String,
        /** For ordering only: millionths of the currency unit. */
        val priceMicros: Long,
        val currencyCode: String
)

internal object TipCatalogue {

    /**
     * What the dialog lists: only our own products, once each, cheapest first.
     *
     * An offer with no readable price is dropped rather than shown as a blank button, and a price of
     * zero is dropped because Play Console allows a free product and a "tip" of nothing would be an
     * invitation to a dialog that charges no one. Ties on price keep the order of [TipProducts.IDS]
     * so the list does not reshuffle between two refreshes.
     */
    fun options(offers: List<TipOffer>): List<TipOffer> =
            offers.asSequence()
                    .filter { it.productId in TipProducts.IDS }
                    .filter { it.formattedPrice.isNotBlank() && it.priceMicros > 0L }
                    .distinctBy { it.productId }
                    .sortedWith(compareBy<TipOffer>({ it.priceMicros }, { TipProducts.IDS.indexOf(it.productId) }))
                    .toList()
}
