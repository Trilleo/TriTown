package net.trilleo.mc.plugins.tritown.shops.storage

/**
 * Brings a shop file forward from an older [ShopSchema] version as it is read.
 *
 * Migrating on read rather than rewriting the file on start means a build that
 * loads a shop file and then fails to enable leaves the original untouched; the
 * upgraded shape reaches disk on the first ordinary save.
 *
 * Each step takes the shape one version forward, so a file several versions old
 * walks through them in turn rather than needing a path of its own.
 */
object ShopMigrations {

    /** [shops], read at [version], as this build understands them. */
    fun upgrade(shops: List<StoredShop>, version: Int): List<StoredShop> {
        var current = shops
        for (from in version until ShopSchema.CURRENT) {
            current = when (from) {
                1 -> toItemCounts(current)
                else -> current
            }
        }
        return current
    }

    /**
     * Schema 1 counted stock and limits in purchases; schema 2 counts them in
     * items.
     *
     * Multiplying by the bundle keeps every shop trading exactly as it did — an
     * entry selling sixteen at a time with a stock of four still offers
     * sixty-four items — so an administrator's numbers only change unit, never
     * meaning.
     */
    private fun toItemCounts(shops: List<StoredShop>): List<StoredShop> = shops.map { shop ->
        shop.copy(
            entries = shop.entries.map { entry ->
                val bundle = entry.bundle.coerceAtLeast(1)
                entry.copy(
                    limitAmount = entry.limitAmount * bundle,
                    stockMax = entry.stockMax * bundle,
                    stockRemaining = entry.stockRemaining * bundle,
                )
            }
        )
    }
}
