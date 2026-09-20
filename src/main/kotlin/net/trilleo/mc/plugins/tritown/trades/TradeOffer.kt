package net.trilleo.mc.plugins.tritown.trades

import net.trilleo.mc.plugins.tritown.economy.Money
import org.bukkit.inventory.ItemStack

/**
 * What one player has put into a trade.
 *
 * The items here are held by the trade, not by the player: they left the
 * player's inventory the moment they were offered, and they go back there the
 * moment the trade ends any way other than going through. That is what lets the
 * other side trust what it is looking at — an offer cannot be dropped, stored
 * or handed to somebody else while it is on the table.
 *
 * Money is the exception, and deliberately so: it is named here but only moves
 * when both sides have confirmed, because a balance is not something that can
 * be held aside without it being missing from everything else that reads it.
 */
class TradeOffer {

    private val stacks = mutableListOf<ItemStack>()

    /** The money this side has named, always zero or more. */
    var money: Money = Money.ZERO
        private set

    /** Copies of the stacks on the table, in the order they were put down. */
    val items: List<ItemStack>
        get() = stacks.map { it.clone() }

    /** How many stacks are on the table. */
    val size: Int
        get() = stacks.size

    /** Whether this side is offering nothing at all. */
    val isEmpty: Boolean
        get() = stacks.isEmpty() && money.isZero

    /** A copy of the stack at [index], or `null` when there is nothing there. */
    fun itemAt(index: Int): ItemStack? = stacks.getOrNull(index)?.clone()

    /**
     * Puts as much of [stack] on the table as will fit, and reports how many
     * items that was.
     *
     * Stacks that can merge are merged first, so offering a sword and then
     * thirty-two arrows twice leaves three entries rather than three-quarters
     * of the table full. What does not fit stays with the caller, which is what
     * keeps the player's own inventory and the offer adding up.
     */
    fun add(stack: ItemStack): Int {
        if (stack.type.isAir || stack.amount <= 0) return 0

        var outstanding = stack.amount

        for (existing in stacks) {
            if (outstanding == 0) break
            if (!existing.isSimilar(stack)) continue

            val room = existing.maxStackSize - existing.amount
            if (room <= 0) continue

            val moved = minOf(room, outstanding)
            existing.amount += moved
            outstanding -= moved
        }

        while (outstanding > 0 && stacks.size < MAX_STACKS) {
            val size = minOf(outstanding, stack.maxStackSize.coerceAtLeast(1))
            stacks += stack.clone().apply { amount = size }
            outstanding -= size
        }

        return stack.amount - outstanding
    }

    /** Takes the stack at [index] off the table and hands it back, or `null` when there is nothing there. */
    fun takeAt(index: Int): ItemStack? = if (index in stacks.indices) stacks.removeAt(index) else null

    /** Names [amount] as this side's money. A negative amount is read as nothing. */
    fun setMoney(amount: Money) {
        money = if (amount.isNegative) Money.ZERO else amount
    }

    /**
     * Empties the offer and hands back everything that was on it.
     *
     * The one way items leave, whether the trade went through or was called
     * off, so nothing can be handed over twice.
     */
    fun drain(): List<ItemStack> {
        val taken = stacks.toList()
        stacks.clear()
        money = Money.ZERO
        return taken
    }

    companion object {

        /** How many stacks one side may put on the table, which is the size of its half of the menu. */
        const val MAX_STACKS = 16
    }
}
