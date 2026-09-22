package net.trilleo.mc.plugins.tritown.storage

import net.trilleo.mc.plugins.tritown.economy.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pages get dearer the more a player has bought, and the price has to be the
 * same wherever it is read: on the button, in the confirmation and at the till.
 */
class StoragePricingTest {

    @Test
    fun `the first page bought costs the base price`() {
        assertEquals(Money(50_000), StoragePricing.cost(1, 500.0, 1.5, 2))
    }

    @Test
    fun `each page costs the multiplier more than the one before`() {
        assertEquals(Money(75_000), StoragePricing.cost(2, 500.0, 1.5, 2))
        assertEquals(Money(112_500), StoragePricing.cost(3, 500.0, 1.5, 2))
        assertEquals(Money(168_750), StoragePricing.cost(4, 500.0, 1.5, 2))
    }

    @Test
    fun `prices are rounded to the currency's smallest unit`() {
        assertEquals(Money(33), StoragePricing.cost(2, 0.1, 3.333, 2))
        assertEquals(Money(2), StoragePricing.cost(2, 1.0, 1.5, 0))
    }

    @Test
    fun `a multiplier of one keeps every page at the same price`() {
        assertEquals(Money(10_000), StoragePricing.cost(20, 100.0, 1.0, 2))
    }

    @Test
    fun `the next page is priced by how many were bought, not how many are owned`() {
        assertEquals(Money(50_000), StoragePricing.next(purchased = 0, owned = 2, maxPages = 27, 500.0, 1.5, 2))
        assertEquals(Money(75_000), StoragePricing.next(purchased = 1, owned = 5, maxPages = 27, 500.0, 1.5, 2))
    }

    @Test
    fun `a storage at the maximum has no next page`() {
        assertNull(StoragePricing.next(purchased = 25, owned = 27, maxPages = 27, 500.0, 1.5, 2))
    }

    @Test
    fun `a price too large to hold is refused rather than wrapped`() {
        assertNull(StoragePricing.cost(256, 1_000_000.0, 10.0, 2))
    }
}
