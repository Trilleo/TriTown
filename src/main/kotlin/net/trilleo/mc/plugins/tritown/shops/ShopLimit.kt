package net.trilleo.mc.plugins.tritown.shops

import net.trilleo.mc.plugins.tritown.enums.LimitPeriod

/**
 * A cap on how many items of one entry a single player may trade.
 *
 * Counted in items rather than in purchases, so an entry that hands over a
 * bundle of sixteen spends sixteen of the allowance per click: an administrator
 * who writes "64 a day" means sixty-four items however the bundle is sized.
 *
 * Windows are counted from the epoch rather than from each player's first
 * purchase, so everyone's daily limit rolls over at the same moment and a
 * player cannot stagger their buying to get more than the cap allows.
 *
 * @param amount how many items a player may trade per window
 * @param period how often the window starts over
 */
data class ShopLimit(val amount: Int, val period: LimitPeriod) {

    /** The window [epochMillis] falls in. A lifetime limit has only one window. */
    fun windowAt(epochMillis: Long): Long = when (period) {
        LimitPeriod.NONE -> 0L
        LimitPeriod.DAILY -> Math.floorDiv(epochMillis, DAY_MILLIS)
        LimitPeriod.WEEKLY -> Math.floorDiv(epochMillis, 7L * DAY_MILLIS)
    }

    /**
     * How many items are still available to a player who has traded [used] of
     * them in window [usedWindow].
     *
     * A count from an earlier window is spent, so it is ignored rather than
     * having to be cleared when the window turns over.
     */
    fun remaining(used: Int, usedWindow: Long, epochMillis: Long): Int =
        if (usedWindow != windowAt(epochMillis)) amount else (amount - used).coerceAtLeast(0)

    private companion object {
        const val DAY_MILLIS = 24L * 60L * 60L * 1000L
    }
}
