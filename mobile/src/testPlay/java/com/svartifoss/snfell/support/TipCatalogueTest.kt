package com.svartifoss.snfell.support

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TipCatalogueTest {

    private fun offer(id: String, micros: Long, price: String = "R$ ${micros / 1_000_000}") =
            TipOffer(id, price, micros, "BRL")

    @Test
    fun theCheapestAmountComesFirstWhateverOrderPlayReturnedThem() {
        val listed = TipCatalogue.options(listOf(
                offer("support_tip_3", 20_000_000),
                offer("support_tip_1", 5_000_000),
                offer("support_tip_4", 50_000_000),
                offer("support_tip_2", 10_000_000)))

        assertEquals(
                listOf("support_tip_1", "support_tip_2", "support_tip_3", "support_tip_4"),
                listed.map { it.productId })
    }

    @Test
    fun aRepricedTierMovesToWhereItsPriceBelongs() {
        // Prices are set in Play Console, so tier 1 may well end up dearer than tier 2.
        val listed = TipCatalogue.options(listOf(
                offer("support_tip_1", 30_000_000),
                offer("support_tip_2", 10_000_000)))

        assertEquals(listOf("support_tip_2", "support_tip_1"), listed.map { it.productId })
    }

    @Test
    fun equalPricesKeepTheOrderOfTheProductIds() {
        val listed = TipCatalogue.options(listOf(
                offer("support_tip_3", 10_000_000),
                offer("support_tip_2", 10_000_000)))

        assertEquals(listOf("support_tip_2", "support_tip_3"), listed.map { it.productId })
    }

    @Test
    fun onlyOurOwnProductsAreEverListed() {
        val listed = TipCatalogue.options(listOf(
                offer("support_tip_1", 5_000_000),
                offer("premium_unlock", 1_000_000),
                offer("", 2_000_000)))

        assertEquals(listOf("support_tip_1"), listed.map { it.productId })
    }

    @Test
    fun anOfferWithoutAReadablePriceIsNotShownAsABlankButton() {
        val listed = TipCatalogue.options(listOf(
                offer("support_tip_1", 5_000_000, price = ""),
                offer("support_tip_2", 10_000_000, price = "   "),
                offer("support_tip_3", 20_000_000)))

        assertEquals(listOf("support_tip_3"), listed.map { it.productId })
    }

    @Test
    fun aFreeProductIsNotATip() {
        val listed = TipCatalogue.options(listOf(
                offer("support_tip_1", 0, price = "Free"),
                offer("support_tip_2", 10_000_000)))

        assertEquals(listOf("support_tip_2"), listed.map { it.productId })
    }

    @Test
    fun theSameProductReportedTwiceIsListedOnce() {
        val listed = TipCatalogue.options(listOf(
                offer("support_tip_1", 5_000_000),
                offer("support_tip_1", 5_000_000)))

        assertEquals(1, listed.size)
    }

    @Test
    fun noProductsMeansNoOption() {
        assertTrue(TipCatalogue.options(emptyList()).isEmpty())
    }

    @Test
    fun thePriceIsShownExactlyAsPlayFormattedIt() {
        val listed = TipCatalogue.options(listOf(offer("support_tip_1", 5_990_000, price = "R$ 5,99")))

        assertEquals("R$ 5,99", listed.single().formattedPrice)
    }

    @Test
    fun theProductIdsAreFourDistinctConsoleSafeNames() {
        // Play Console accepts lowercase letters, digits, underscores and periods, starting with a
        // letter or digit - and never lets a deleted id be reused.
        assertEquals(4, TipProducts.IDS.size)
        assertEquals(TipProducts.IDS.size, TipProducts.IDS.toSet().size)
        TipProducts.IDS.forEach {
            assertTrue("$it is not a valid Play Console product id", Regex("[a-z0-9][a-z0-9_.]*").matches(it))
        }
    }
}
