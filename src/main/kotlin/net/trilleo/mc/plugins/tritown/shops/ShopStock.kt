package net.trilleo.mc.plugins.tritown.shops

/**
 * A finite supply of one entry, shared by everybody, that refills on a timer.
 *
 * Counted in items rather than in purchases, the same way a per-player limit is,
 * so a stock of 64 is sixty-four items however large a bundle one purchase is.
 *
 * Restocking is lazy: nothing counts down in the background, and the supply is
 * brought up to date the moment somebody looks at it. A shop nobody visits for
 * a week therefore costs nothing and is still correct when they do.
 *
 * A restock fills back to [max] rather than adding one unit per period, so a
 * long absence cannot bank an unbounded supply.
 *
 * @param max            how many items a restock fills back to
 * @param restockSeconds how long a restock takes; 0 never restocks, making the supply one-off
 */
data class ShopStock(
    val max: Int,
    val restockSeconds: Long,
    var remaining: Int = max,
    var lastRestock: Long = 0L,
) {

    /** How many items can be bought right now, restocking first if one is due. */
    fun available(now: Long): Int {
        restock(now)
        return remaining
    }

    /** Brings the supply up to date, filling it when at least one restock period has passed. */
    fun restock(now: Long) {
        if (restockSeconds <= 0L) return
        if (lastRestock == 0L) {
            lastRestock = now
            return
        }

        val period = restockSeconds * 1000L
        val elapsed = now - lastRestock
        if (elapsed < period) return

        remaining = max
        // Advanced by whole periods so the refill time does not drift later on every restock.
        lastRestock += elapsed / period * period
    }

    /** Takes [count] items, or returns `false` and takes nothing when the supply is short. */
    fun take(count: Int, now: Long): Boolean {
        if (count <= 0 || available(now) < count) return false
        remaining -= count
        return true
    }

    /** Puts [count] items back, for a purchase that was rolled back. */
    fun restore(count: Int) {
        remaining = (remaining + count).coerceAtMost(max)
    }
}
