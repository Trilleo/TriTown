package net.trilleo.mc.plugins.tritown.storage

import net.trilleo.mc.plugins.tritown.economy.Money
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * What storage pages cost.
 *
 * Each page bought costs [base] times [multiplier] for every page bought before
 * it, so the first is [base] and the price climbs from there. Counting bought
 * pages rather than owned ones keeps the price where a player left it when an
 * owner hands out more free pages.
 *
 * Kept free of Bukkit and the economy, so the arithmetic can be tested alone.
 */
object StoragePricing {

    /**
     * The price of one more page for a storage that has bought [purchased] and
     * owns [owned] pages, at [scale] fractional digits.
     *
     * @return `null` when [owned] has reached [maxPages], or the price no longer
     *   fits in an amount of money
     */
    fun next(purchased: Int, owned: Int, maxPages: Int, base: Double, multiplier: Double, scale: Int): Money? {
        if (owned >= maxPages) return null
        return cost(purchased + 1, base, multiplier, scale)
    }

    /** The [nth] page bought, counting from one, or `null` when it is beyond any amount of money. */
    fun cost(nth: Int, base: Double, multiplier: Double, scale: Int): Money? {
        require(nth >= 1) { "Pages bought are counted from one" }
        if (!base.isFinite() || !multiplier.isFinite() || base < 0.0 || multiplier < 1.0) return null

        val price = BigDecimal.valueOf(base)
            .multiply(BigDecimal.valueOf(multiplier).pow(nth - 1))
            .setScale(scale, RoundingMode.HALF_UP)
            .movePointRight(scale)

        return runCatching { Money(price.longValueExact()) }.getOrNull()
    }
}
