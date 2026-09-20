package net.trilleo.mc.plugins.tritown.shops

import org.bukkit.inventory.ItemStack

/**
 * What a given number of items would actually cost, or pay out, for one
 * player at this moment.
 *
 * Prices are worked out once and then both shown and charged from the same
 * quote, so a discount can never appear in the menu without being applied at
 * the till.
 *
 * @param money     the total actually charged or paid, after any discount
 * @param fullMoney the total before the discount, for showing what was saved
 * @param amount    how many items the quote is for
 * @param items     the stacks required or paid out in total, for all of [amount]
 */
data class ShopQuote(
    val amount: Int,
    val money: Double,
    val fullMoney: Double,
    val items: List<ItemStack>,
    val discount: Double,
) {

    /** Whether a discount was applied. */
    val isDiscounted: Boolean get() = discount > 0.0 && money < fullMoney

    /** Whether any currency changes hands. */
    val hasMoney: Boolean get() = money > 0.0

    /** Whether this quote asks for nothing at all. */
    val isFree: Boolean get() = money <= 0.0 && items.isEmpty()
}
