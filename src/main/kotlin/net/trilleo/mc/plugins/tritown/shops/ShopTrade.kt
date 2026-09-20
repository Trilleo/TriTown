package net.trilleo.mc.plugins.tritown.shops

import net.trilleo.mc.plugins.tritown.config.ShopSettings
import net.trilleo.mc.plugins.tritown.economy.CurrencyRegistry
import net.trilleo.mc.plugins.tritown.economy.EconomyContext
import net.trilleo.mc.plugins.tritown.economy.TransactionReason
import net.trilleo.mc.plugins.tritown.enums.MatchMode
import net.trilleo.mc.plugins.tritown.enums.TownyRequirement
import net.trilleo.mc.plugins.tritown.enums.TradeSide
import net.trilleo.mc.plugins.tritown.utils.EconomyUtil
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import kotlin.math.floor

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

    /** The most bundles one click may move, so a shift-click cannot try to buy a chest-load at once. */
    const val MAX_BUNDLES = 64

    /** The outcome of a trade. */
    sealed interface Result {

        /** The trade went through. */
        data class Success(val bundles: Int, val money: Double) : Result

        /** The trade was refused, with a `shop.error.*` key and any arguments the message needs. */
        data class Failure(val key: String, val args: List<Pair<String, Any?>> = emptyList()) : Result
    }

    // ── Quoting ─────────────────────────────────────────────────────────

    /** What [bundles] of [entry] would cost [player], with any discount applied. */
    fun quoteBuy(
        player: Player,
        entry: ShopEntry,
        bundles: Int,
        standing: Set<TownyRequirement> = ShopAccess.standing(player),
    ): ShopQuote? {
        val cost = entry.buy ?: return null
        val discount = if (entry.discountable) discountFor(standing) else 0.0
        val full = ShopPricing.round(cost.money * bundles, scale())

        return ShopQuote(
            bundles = bundles,
            money = ShopPricing.apply(full, discount, scale()),
            fullMoney = full,
            items = multiply(cost.items, bundles),
            discount = discount,
        )
    }

    /** What the shop would pay [player] for [bundles] of [entry]. Discounts never apply to a payout. */
    fun quoteSell(entry: ShopEntry, bundles: Int): ShopQuote? {
        val payout = entry.sell ?: return null
        val total = ShopPricing.round(payout.money * bundles, scale())
        return ShopQuote(bundles, total, total, multiply(payout.items, bundles), 0.0)
    }

    /**
     * The most bundles of [entry] [player] could buy right now.
     *
     * Bounded by their money, the items the price asks for, the room they have,
     * the supply left, their own limit and [MAX_BUNDLES] — whichever runs out
     * first. Used by a shift-click, which buys as many as it can rather than
     * refusing outright.
     *
     * Stock and limits are counted in items, so a bundle that does not fit
     * whole into what is left is not offered: half a bundle is not a purchase.
     */
    fun maxBuyable(
        player: Player,
        shop: ShopDefinition,
        entry: ShopEntry,
        standing: Set<TownyRequirement> = ShopAccess.standing(player),
    ): Int {
        val cost = entry.buy ?: return 0
        val bundle = entry.bundleSize
        var max = MAX_BUNDLES

        entry.stock?.let { max = minOf(max, it.available(System.currentTimeMillis()) / bundle) }
        ShopLimits.remaining(player, shop, entry, TradeSide.BUY)?.let { max = minOf(max, it / bundle) }

        for (item in cost.items) {
            val held = ShopInventory.count(player, item, entry.matchMode)
            max = minOf(max, held / item.amount.coerceAtLeast(1))
        }

        if (cost.money > 0.0) {
            if (!EconomyUtil.isAvailable) return 0
            val discount = if (entry.discountable) discountFor(standing) else 0.0
            val unit = ShopPricing.apply(ShopPricing.round(cost.money, scale()), discount, scale())
            max = if (unit <= 0.0) max else minOf(max, floor(EconomyUtil.balance(player) / unit).toInt())
        }

        // Room is the slowest check, so it narrows an already-bounded number rather than searching from the top.
        while (max > 0 && !ShopInventory.hasSpaceFor(player, entry.goodsStacks(max))) max--

        return max.coerceAtLeast(0)
    }

    /**
     * How many bundles of [entry] [player] could sell right now.
     *
     * Bounded by what they are carrying, their own selling limit and
     * [MAX_BUNDLES], for a shift-click that sells the lot.
     */
    fun maxSellable(player: Player, shop: ShopDefinition, entry: ShopEntry): Int {
        if (!entry.isSellable) return 0
        val bundle = entry.bundleSize
        val held = ShopInventory.count(player, entry.item, entry.matchMode)
        var max = minOf(MAX_BUNDLES, held / bundle)

        ShopLimits.remaining(player, shop, entry, TradeSide.SELL)?.let { max = minOf(max, it / bundle) }

        return max.coerceAtLeast(0)
    }

    // ── Buying ──────────────────────────────────────────────────────────

    /** Sells [bundles] of [entry] to [player]. */
    fun buy(player: Player, shop: ShopDefinition, entry: ShopEntry, bundles: Int): Result {
        if (!ShopManager.isReady) return Result.Failure("shop.error.unavailable")
        if (bundles !in 1..MAX_BUNDLES) return Result.Failure("shop.error.bad-amount")

        val cost = entry.buy ?: return Result.Failure("shop.error.not-for-sale")

        val standing = ShopAccess.standing(player)
        ShopAccess.refusalKey(player, shop.gate, standing)?.let { return Result.Failure(it) }
        ShopAccess.refusalKey(player, entry.gate, standing)?.let { return Result.Failure(it) }

        val items = entry.bundleSize * bundles

        ShopLimits.remaining(player, shop, entry, TradeSide.BUY)?.let { left ->
            if (left < items) return Result.Failure("shop.error.limit-reached", listOf("amount" to left))
        }

        val stock = entry.stock
        val now = System.currentTimeMillis()
        if (stock != null && stock.available(now) < items) {
            return Result.Failure("shop.error.out-of-stock", listOf("amount" to stock.remaining))
        }

        val quote = quoteBuy(player, entry, bundles, standing) ?: return Result.Failure("shop.error.not-for-sale")
        val goods = entry.goodsStacks(bundles)
        if (!ShopInventory.hasSpaceFor(player, goods)) return Result.Failure("shop.error.no-space")

        if (quote.hasMoney && !EconomyUtil.isAvailable) return Result.Failure("shop.error.economy-unavailable")

        // Taken before any money moves, and handed back below if the money then refuses.
        val taken = takeItems(player, quote.items, entry.matchMode)
            ?: return Result.Failure("shop.error.missing-items")

        if (stock != null && !stock.take(items, now)) {
            ShopInventory.give(player, taken)
            return Result.Failure("shop.error.out-of-stock", listOf("amount" to stock.remaining))
        }

        if (quote.hasMoney) {
            val reason = TransactionReason.of(TransactionReason.SHOP_BUY, "shop" to shop.displayName)
            if (!EconomyUtil.withdraw(player, quote.money, EconomyContext.SOURCE_SHOP, reason)) {
                stock?.restore(items)
                ShopInventory.give(player, taken)
                return Result.Failure("shop.error.cannot-afford", listOf("price" to format(quote.money)))
            }
        }

        ShopInventory.give(player, goods)
        ShopLimits.record(player, shop, entry, TradeSide.BUY, items, now)
        entry.stats.recordBuy(bundles, quote.money)
        ShopManager.markDirty()

        return Result.Success(bundles, quote.money)
    }

    // ── Selling ─────────────────────────────────────────────────────────

    /** Buys [bundles] of [entry] back from [player]. */
    fun sell(player: Player, shop: ShopDefinition, entry: ShopEntry, bundles: Int): Result {
        if (!ShopManager.isReady) return Result.Failure("shop.error.unavailable")
        if (bundles !in 1..MAX_BUNDLES) return Result.Failure("shop.error.bad-amount")
        if (!entry.isSellable) return Result.Failure("shop.error.not-bought")

        val standing = ShopAccess.standing(player)
        ShopAccess.refusalKey(player, shop.gate, standing)?.let { return Result.Failure(it) }
        ShopAccess.refusalKey(player, entry.gate, standing)?.let { return Result.Failure(it) }

        val items = entry.bundleSize * bundles
        val now = System.currentTimeMillis()

        ShopLimits.remaining(player, shop, entry, TradeSide.SELL, now)?.let { left ->
            if (left < items) return Result.Failure("shop.error.sell-limit-reached", listOf("amount" to left))
        }

        val quote = quoteSell(entry, bundles) ?: return Result.Failure("shop.error.not-bought")
        if (!ShopInventory.hasSpaceFor(player, quote.items)) return Result.Failure("shop.error.no-space")
        if (quote.hasMoney && !EconomyUtil.isAvailable) return Result.Failure("shop.error.economy-unavailable")

        val handedOver = ShopInventory.remove(player, entry.item, entry.matchMode, items)
            ?: return Result.Failure("shop.error.missing-goods")

        if (quote.hasMoney) {
            val reason = TransactionReason.of(TransactionReason.SHOP_SELL, "shop" to shop.displayName)
            if (!EconomyUtil.deposit(player, quote.money, EconomyContext.SOURCE_SHOP, reason)) {
                ShopInventory.give(player, handedOver)
                return Result.Failure("shop.error.payout-refused")
            }
        }

        ShopInventory.give(player, quote.items)
        ShopLimits.record(player, shop, entry, TradeSide.SELL, items, now)
        entry.stats.recordSell(bundles, quote.money)
        ShopManager.markDirty()

        return Result.Success(bundles, quote.money)
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    /**
     * Takes every stack in [required], putting back anything already taken if
     * one of them turns out to be short.
     */
    private fun takeItems(player: Player, required: List<ItemStack>, mode: MatchMode): List<ItemStack>? {
        val taken = mutableListOf<ItemStack>()
        for (item in required) {
            val removed = ShopInventory.remove(player, item, mode, item.amount)
            if (removed == null) {
                ShopInventory.give(player, taken)
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
