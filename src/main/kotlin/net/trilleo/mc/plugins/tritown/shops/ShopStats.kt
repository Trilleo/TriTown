package net.trilleo.mc.plugins.tritown.shops

/**
 * What one entry has traded since the counters were last reset.
 *
 * Counts items, the same unit as a stock and a per-player limit, and keeps the
 * two currency directions apart so an owner can see at a glance whether a shop
 * is draining the economy or feeding it.
 */
data class ShopStats(
    var bought: Long = 0L,
    var sold: Long = 0L,
    var moneyIn: Double = 0.0,
    var moneyOut: Double = 0.0,
) {

    /** Records a player buying [items] for [money]. */
    fun recordBuy(items: Int, money: Double) {
        bought += items
        moneyIn += money
    }

    /** Records a player selling [items] for [money]. */
    fun recordSell(items: Int, money: Double) {
        sold += items
        moneyOut += money
    }

    /** Whether anything has been traded at all. */
    val isEmpty: Boolean get() = bought == 0L && sold == 0L

    /** Clears every counter. */
    fun reset() {
        bought = 0L
        sold = 0L
        moneyIn = 0.0
        moneyOut = 0.0
    }
}
