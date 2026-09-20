package net.trilleo.mc.plugins.tritown.shops

import net.trilleo.mc.plugins.tritown.enums.MatchMode
import net.trilleo.mc.plugins.tritown.enums.TradeSide
import org.bukkit.inventory.ItemStack
import java.util.*

/**
 * One line of goods in a shop.
 *
 * The [item] is stored verbatim, custom data and all, and [bundle] is how many
 * of it one purchase moves: an entry of bread with a bundle of 16 sells sixteen
 * loaves per click. The two are kept apart so a bundle can exceed what a stack
 * holds — 128 bread is a valid bundle, handed over as two stacks. Buying and
 * selling are independent, so an entry can do either, both, or — with both left
 * null — act as a display piece.
 *
 * @param id        stable across reordering and renaming, because trade counters are keyed by it
 * @param bundle    how many items one purchase moves; at least 1, with no upper bound
 * @param buyLimit  how many items one player may buy per window, counted in items rather than purchases
 * @param sellLimit the same cap on the other side, kept apart so neither spends the other's allowance
 */
data class ShopEntry(
    val id: String = UUID.randomUUID().toString(),
    var item: ItemStack,
    var bundle: Int = 1,
    var buy: ShopCost? = null,
    var sell: ShopCost? = null,
    var gate: ShopGate = ShopGate.OPEN,
    var buyLimit: ShopLimit? = null,
    var sellLimit: ShopLimit? = null,
    var stock: ShopStock? = null,
    var discountable: Boolean = true,
    var matchMode: MatchMode = MatchMode.EXACT,
    var stats: ShopStats = ShopStats(),
) {

    /** How many items one bundle is, never less than one. */
    val bundleSize: Int get() = bundle.coerceAtLeast(1)

    /** Whether a player can buy this. */
    val isBuyable: Boolean get() = buy != null

    /** Whether the shop buys this back. */
    val isSellable: Boolean get() = sell != null

    /** The per-player cap that applies to [side], or `null` when that side is uncapped. */
    fun limitOn(side: TradeSide): ShopLimit? = when (side) {
        TradeSide.BUY -> buyLimit
        TradeSide.SELL -> sellLimit
    }

    /** Sets the per-player cap that applies to [side]. */
    fun setLimitOn(side: TradeSide, limit: ShopLimit?) {
        when (side) {
            TradeSide.BUY -> buyLimit = limit
            TradeSide.SELL -> sellLimit = limit
        }
    }

    /**
     * One bundle as a single stack, for a menu slot rather than for handing over.
     *
     * Capped at what a slot can show, because a bundle larger than a stack still
     * has to be drawn in one square; the real count is written into the lore.
     */
    fun displayStack(): ItemStack = item.clone().apply { amount = bundleSize.coerceAtMost(item.maxStackSize) }

    /**
     * [bundles] bundles of the goods, split into stacks the game allows.
     *
     * Split here rather than left as one oversized stack, so that what is
     * checked for room is exactly what is handed over.
     */
    fun goodsStacks(bundles: Int = 1): List<ItemStack> {
        val total = bundleSize * bundles
        val perStack = item.maxStackSize.coerceAtLeast(1)

        return buildList {
            var outstanding = total
            while (outstanding > 0) {
                val size = minOf(outstanding, perStack)
                add(item.clone().apply { amount = size })
                outstanding -= size
            }
        }
    }

    /** Whether [stack] is close enough to the goods to count, under this entry's [matchMode]. */
    fun matches(stack: ItemStack): Boolean = when (matchMode) {
        MatchMode.EXACT -> item.isSimilar(stack)
        MatchMode.MATERIAL -> item.type == stack.type
    }
}
