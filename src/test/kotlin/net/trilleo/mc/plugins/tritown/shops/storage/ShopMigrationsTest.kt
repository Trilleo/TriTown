package net.trilleo.mc.plugins.tritown.shops.storage

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * An upgrade changes the unit an administrator's numbers are written in, never
 * what those numbers mean: a shop must trade exactly as it did before the file
 * was brought forward.
 */
class ShopMigrationsTest {

    private fun shop(entry: StoredEntry) = StoredShop(id = "market", entries = listOf(entry))

    private fun upgraded(entry: StoredEntry, from: Int = 1): StoredEntry =
        ShopMigrations.upgrade(listOf(shop(entry)), from).single().entries.single()

    @Test
    fun `schema 1 counted purchases, so its numbers are multiplied by the bundle`() {
        val entry = upgraded(
            StoredEntry(
                id = "bread",
                bundle = 16,
                limitAmount = 3,
                stockMax = 8,
                stockRemaining = 5,
                bought = 4L,
                sold = 2L,
            )
        )

        assertEquals(48, entry.limitAmount)
        assertEquals(128, entry.stockMax)
        assertEquals(80, entry.stockRemaining)
        assertEquals(64L, entry.bought)
        assertEquals(32L, entry.sold)
    }

    @Test
    fun `an entry with no limit and no stock keeps having neither`() {
        val entry = upgraded(StoredEntry(id = "bread", bundle = 16))

        assertEquals(0, entry.limitAmount)
        assertEquals(0, entry.stockMax)
    }

    @Test
    fun `a bundle that was never written counts as one`() {
        val entry = upgraded(StoredEntry(id = "bread", limitAmount = 4, stockMax = 9, stockRemaining = 2))

        assertEquals(4, entry.limitAmount)
        assertEquals(9, entry.stockMax)
        assertEquals(2, entry.stockRemaining)
    }

    @Test
    fun `a file already at the current version is left alone`() {
        val entry = StoredEntry(id = "bread", bundle = 16, limitAmount = 3, stockMax = 8)

        assertEquals(entry, upgraded(entry, from = ShopSchema.CURRENT))
    }

    @Test
    fun `the selling limit is left to its default on an upgraded file`() {
        val entry = upgraded(StoredEntry(id = "bread", bundle = 16, limitAmount = 3))

        assertEquals(0, entry.sellLimitAmount)
        assertEquals("NONE", entry.sellLimitPeriod)
    }
}
