package net.trilleo.mc.plugins.tritown.shops

import net.trilleo.mc.plugins.tritown.config.ShopSettings
import net.trilleo.mc.plugins.tritown.economy.CurrencyRegistry
import net.trilleo.mc.plugins.tritown.economy.EconomyContext
import net.trilleo.mc.plugins.tritown.economy.TransactionReason
import net.trilleo.mc.plugins.tritown.enums.MatchMode
import net.trilleo.mc.plugins.tritown.enums.TownyRequirement
import net.trilleo.mc.plugins.tritown.enums.TradeSide
import net.trilleo.mc.plugins.tritown.utils.EconomyUtil
import net.trilleo.mc.plugins.tritown.utils.InventoryUtil
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * Buying and selling, and the only place either happens.
 *
 * Every trade runs on the server thread, because it touches an inventory. The
 * ordering is what makes it safe: everything that can refuse is asked before
 * anything is taken, and anything that is taken is remembered so it can be put
 * back if a later step still fails. A player can end a failed trade poorer in
 * neither money nor items.
 *
 * The goods a shop sells are created and the money paid for them leaves the
 * economy, which is what an admin shop is for — there is no shop account behind
 * it holding either.
 */
object ShopTrade {

    /** The most items one trade may move, so nothing can try to buy a warehouse in one click. */
    const val MAX_ITEMS = 4096

    /** The outcome of a trade. */
    sealed interface Result {

        /** The trade went through. */
        data class Success(val amount: Int, val money: Double) : Result

        /** The trade was refused, with a `shop.error.*` key and any arguments the message needs. */
        data class Failure(val key: String, val args: List<Pair<String, Any?>> = emptyList()) : Result
    }

    // ── Quoting ─────────────────────────────────────────────────────────

    /**
     * What [amount] items of [entry] would cost [player], with any discount applied.
     *
     * Money is priced per item, so any amount can be quoted: a bundle of
     * sixteen at 10 puts one at 0.63, which is what keeping the entry's own
     * ratio means. A price that asks for items cannot be divided that finely —
     * a quarter of an iron ingot is not a thing to hand over — so an amount
     * that is not a whole number of bundles has no price at all, and is refused
     * before it is ever asked for.
     */
    fun quoteBuy(
        player: Player,
        entry: ShopEntry,
        amount: Int,
        standing: Set<TownyRequirement> = ShopAccess.standing(player),
    ): ShopQuote? {
        val cost = entry.buy ?: return null
        val bundles = wholeBundles(entry, cost, amount) ?: return null
        val discount = if (entry.discountable) discountFor(standing) else 0.0
        val full = ShopPricing.round(cost.money / entry.bundleSize * amount, scale())

        return ShopQuote(
            amount = amount,
            money = ShopPricing.apply(full, discount, scale()),
            fullMoney = full,
            items = multiply(cost.items, bundles),
            discount = discount,
        )
    }

    /** What the shop would pay [player] for [amount] items of [entry]. Discounts never apply to a payout. */
    fun quoteSell(entry: ShopEntry, amount: Int): ShopQuote? {
        val payout = entry.sell ?: return null
        val bundles = wholeBundles(entry, payout, amount) ?: return null
        val total = ShopPricing.round(payout.money / entry.bundleSize * amount, scale())
        return ShopQuote(amount, total, total, multiply(payout.items, bundles), 0.0)
    }

    /**
     * How many items of [entry] [player] could sell right now, rounded down to
     * whole purchases.
     *
     * Bounded by what they are carrying and their own selling limit, for a
     * shift-click that sells the lot.
     */
    fun maxSellable(player: Player, shop: ShopDefinition, entry: ShopEntry): Int {
        if (!entry.isSellable) return 0
        var max = minOf(MAX_ITEMS, ShopInventory.count(player, entry.item, entry.matchMode))

        ShopLimits.remaining(player, shop, entry, TradeSide.SELL)?.let { max = minOf(max, it) }

        val bundle = entry.bundleSize
        return (max / bundle * bundle).coerceAtLeast(0)
    }

    // ── Buying ──────────────────────────────────────────────────────────

    /**
     * Why [player] could not buy [amount] items of [entry] right now, or `null`
     * when they could.
     *
     * Everything a purchase can refuse over except the money itself, which only
     * the withdrawal may decide — asking the balance and then charging it are
     * two steps a trade must never take. The amount menu greys an option out
     * with what this returns, so what it shows and what a click does cannot
     * disagree.
     */
    fun buyRefusal(
        player: Player,
        shop: ShopDefinition,
        entry: ShopEntry,
        amount: Int,
        standing: Set<TownyRequirement> = ShopAccess.standing(player),
    ): Result.Failure? {
        if (!ShopManager.isReady) return Result.Failure("shop.error.unavailable")
        if (amount !in 1..MAX_ITEMS) return Result.Failure("shop.error.bad-amount")

        val cost = entry.buy ?: return Result.Failure("shop.error.not-for-sale")

        ShopAccess.refusalKey(player, shop.gate, standing)?.let { return Result.Failure(it) }
        ShopAccess.refusalKey(player, entry.gate, standing)?.let { return Result.Failure(it) }

        if (wholeBundles(entry, cost, amount) == null) {
            return Result.Failure("shop.error.part-bundle", listOf("amount" to entry.bundleSize))
        }

        ShopLimits.remaining(player, shop, entry, TradeSide.BUY)?.let { left ->
            if (left < amount) return Result.Failure("shop.error.limit-reached", listOf("amount" to left))
        }

        entry.stock?.let { stock ->
            if (stock.available(System.currentTimeMillis()) < amount) {
                return Result.Failure("shop.error.out-of-stock", listOf("amount" to stock.remaining))
            }
        }

        val quote = quoteBuy(player, entry, amount, standing) ?: return Result.Failure("shop.error.not-for-sale")
        if (!InventoryUtil.hasSpaceFor(player, entry.goodsStacks(amount))) {
            return Result.Failure("shop.error.no-space")
        }
        if (quote.hasMoney && !EconomyUtil.isAvailable) return Result.Failure("shop.error.economy-unavailable")

        for (item in quote.items) {
            if (ShopInventory.count(player, item, entry.matchMode) < item.amount) {
                return Result.Failure("shop.error.missing-items")
            }
        }

        return null
    }

    /** Sells [amount] items of [entry] to [player]. */
    fun buy(player: Player, shop: ShopDefinition, entry: ShopEntry, amount: Int): Result {
        val standing = ShopAccess.standing(player)
        buyRefusal(player, shop, entry, amount, standing)?.let { return it }

        val quote = quoteBuy(player, entry, amount, standing) ?: return Result.Failure("shop.error.not-for-sale")
        val goods = entry.goodsStacks(amount)
        val stock = entry.stock
        val now = System.currentTimeMillis()

        // Taken before any money moves, and handed back below if the money then refuses.
        val taken = takeItems(player, quote.items, entry.matchMode)
            ?: return Result.Failure("shop.error.missing-items")

        if (stock != null && !stock.take(amount, now)) {
            InventoryUtil.give(player, taken)
            return Result.Failure("shop.error.out-of-stock", listOf("amount" to stock.remaining))
        }

        if (quote.hasMoney) {
            val reason = TransactionReason.of(TransactionReason.SHOP_BUY, "shop" to shop.displayName)
            if (!EconomyUtil.withdraw(player, quote.money, EconomyContext.SOURCE_SHOP, reason)) {
                stock?.restore(amount)
                InventoryUtil.give(player, taken)
                return Result.Failure("shop.error.cannot-afford", listOf("price" to format(quote.money)))
            }
        }

        InventoryUtil.give(player, goods)
        ShopLimits.record(player, shop, entry, TradeSide.BUY, amount, now)
        entry.stats.recordBuy(amount, quote.money)
        ShopManager.markDirty()

        return Result.Success(amount, quote.money)
    }

    // ── Selling ─────────────────────────────────────────────────────────

    /** Buys [amount] items of [entry] back from [player]. */
    fun sell(player: Player, shop: ShopDefinition, entry: ShopEntry, amount: Int): Result {
        if (!ShopManager.isReady) return Result.Failure("shop.error.unavailable")
        if (amount !in 1..MAX_ITEMS) return Result.Failure("shop.error.bad-amount")

        val payout = entry.sell ?: return Result.Failure("shop.error.not-bought")

        val standing = ShopAccess.standing(player)
        ShopAccess.refusalKey(player, shop.gate, standing)?.let { return Result.Failure(it) }
        ShopAccess.refusalKey(player, entry.gate, standing)?.let { return Result.Failure(it) }

        if (wholeBundles(entry, payout, amount) == null) {
            return Result.Failure("shop.error.part-bundle", listOf("amount" to entry.bundleSize))
        }

        val now = System.currentTimeMillis()

        ShopLimits.remaining(player, shop, entry, TradeSide.SELL, now)?.let { left ->
            if (left < amount) return Result.Failure("shop.error.sell-limit-reached", listOf("amount" to left))
        }

        val quote = quoteSell(entry, amount) ?: return Result.Failure("shop.error.not-bought")
        if (!InventoryUtil.hasSpaceFor(player, quote.items)) return Result.Failure("shop.error.no-space")
        if (quote.hasMoney && !EconomyUtil.isAvailable) return Result.Failure("shop.error.economy-unavailable")

        val handedOver = ShopInventory.remove(player, entry.item, entry.matchMode, amount)
            ?: return Result.Failure("shop.error.missing-goods")

        if (quote.hasMoney) {
            val reason = TransactionReason.of(TransactionReason.SHOP_SELL, "shop" to shop.displayName)
            if (!EconomyUtil.deposit(player, quote.money, EconomyContext.SOURCE_SHOP, reason)) {
                InventoryUtil.give(player, handedOver)
                return Result.Failure("shop.error.payout-refused")
            }
        }

        InventoryUtil.give(player, quote.items)
        ShopLimits.record(player, shop, entry, TradeSide.SELL, amount, now)
        entry.stats.recordSell(amount, quote.money)
        ShopManager.markDirty()

        return Result.Success(amount, quote.money)
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    /**
     * How many bundles [amount] is, or `null` when [cost] cannot be divided
     * that finely.
     *
     * A price or payout made only of money scales to any amount, so the bundle
     * count is only ever needed — and only ever has to be whole — when items
     * are part of it.
     */
    private fun wholeBundles(entry: ShopEntry, cost: ShopCost, amount: Int): Int? {
        val bundle = entry.bundleSize
        if (cost.items.isNotEmpty() && amount % bundle != 0) return null
        return amount / bundle
    }

    /**
     * Takes every stack in [required], putting back anything already taken if
     * one of them turns out to be short.
     */
    private fun takeItems(player: Player, required: List<ItemStack>, mode: MatchMode): List<ItemStack>? {
        val taken = mutableListOf<ItemStack>()
        for (item in required) {
            val removed = ShopInventory.remove(player, item, mode, item.amount)
            if (removed == null) {
                InventoryUtil.give(player, taken)
                return null
            }
            taken += removed
        }
        return taken
    }

    private fun multiply(items: List<ItemStack>, bundles: Int): List<ItemStack> =
        items.map { it.clone().apply { amount = it.amount * bundles } }

    private fun discountFor(standing: Set<TownyRequirement>): Double =
        if (ShopSettings.isLoaded) ShopPricing.discount(ShopSettings.snapshot.discounts, standing) else 0.0

    private fun scale(): Int = if (CurrencyRegistry.isLoaded) CurrencyRegistry.primary.fractionalDigits else 2

    private fun format(money: Double): String =
        if (EconomyUtil.isAvailable) EconomyUtil.format(money) else money.toString()
}
