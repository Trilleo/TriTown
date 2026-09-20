package net.trilleo.mc.plugins.tritown.guis.shop

import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.trilleo.mc.plugins.tritown.config.ShopSettings
import net.trilleo.mc.plugins.tritown.enums.FillMode
import net.trilleo.mc.plugins.tritown.enums.PagedLayout
import net.trilleo.mc.plugins.tritown.enums.TownyRequirement
import net.trilleo.mc.plugins.tritown.enums.TradeSide
import net.trilleo.mc.plugins.tritown.registration.GUIManager
import net.trilleo.mc.plugins.tritown.registration.PagedPluginGUI
import net.trilleo.mc.plugins.tritown.shops.*
import net.trilleo.mc.plugins.tritown.utils.*
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.ItemStack
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * What a player sees when they click a shopkeeper.
 *
 * GUIs are singletons, so which shop is open and what was drawn for it are held
 * per viewer. The drawing is done once when the menu opens rather than in
 * [getItems], which the paging code calls on every render and again for every
 * page count.
 *
 * A trade redraws only the entry that was traded. Rebuilding the whole menu
 * would throw the viewer back to the first page, and nothing else on the page
 * changed.
 */
class ShopGUI : PagedPluginGUI(
    id = ID,
    titleKey = "gui.shop.title",
    rows = 6,
    fillMode = FillMode.NONE,
    layout = PagedLayout.FRAMED,
) {

    /**
     * @param entryIds the entry drawn in each content slot, in order, with `null` where nothing is
     */
    private data class View(
        val shopId: String,
        val entryIds: MutableList<String?>,
        val items: MutableList<ItemStack>,
    )

    private val views = ConcurrentHashMap<UUID, View>()

    /** Opens [shop] for [viewer], drawing only what they are allowed to see. */
    fun open(viewer: Player, shop: ShopDefinition) {
        views[viewer.uniqueId] = render(viewer, shop)
        GUIManager.open(viewer, ID)
    }

    override fun title(player: Player): Component {
        val shop = shopOf(player)
        val name = shop?.displayName ?: player.tr("gui.shop.unknown")
        return ComponentUtil.parse(player.tr("gui.shop.title", "name" to name))
    }

    override fun getItems(player: Player): List<ItemStack> = views[player.uniqueId]?.items ?: emptyList()

    override fun onContentClick(event: InventoryClickEvent, page: Int) {
        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        val view = views[player.uniqueId] ?: return
        val shop = ShopManager.get(view.shopId) ?: return

        val index = contentIndex(page, event.rawSlot) ?: return
        val entry = view.entryIds.getOrNull(index)?.let(shop::entry) ?: return

        val result = when (event.click) {
            ClickType.LEFT -> tradeBuy(player, shop, entry, 1)
            ClickType.SHIFT_LEFT -> tradeBuy(player, shop, entry, ShopTrade.maxBuyable(player, shop, entry))
            ClickType.RIGHT -> ShopTrade.sell(player, shop, entry, 1)
            ClickType.SHIFT_RIGHT -> sellMax(player, shop, entry)
            else -> return
        } ?: return

        when (result) {
            is ShopTrade.Result.Success -> {
                announce(player, entry, result)
                redraw(event, view, index, player, shop, entry)
            }

            is ShopTrade.Result.Failure -> {
                val reason = player.tr(result.key, *result.args.toTypedArray())
                player.sendPrefixed(player.tr("common.error", "message" to reason))
                player.playSound(Sound.sound(Key.key("minecraft:entity.villager.no"), Sound.Source.UI, 1f, 1f))
            }
        }
    }

    override fun onClose(event: InventoryCloseEvent) {
        super.onClose(event)
        views.remove((event.player as? Player)?.uniqueId ?: return)
    }

    // ── Trading ─────────────────────────────────────────────────────────

    /**
     * Buys, unless the bill is large enough to be worth a second look — then the
     * confirmation menu takes over and this reports nothing.
     */
    private fun tradeBuy(player: Player, shop: ShopDefinition, entry: ShopEntry, bundles: Int): ShopTrade.Result? {
        if (bundles <= 0) return nothingToBuy(player, shop, entry)

        val threshold = if (ShopSettings.isLoaded) ShopSettings.snapshot.confirmAbove else 0.0
        val quote = ShopTrade.quoteBuy(player, entry, bundles)
        val confirm = confirmGUI()

        if (threshold > 0.0 && confirm != null && quote != null && quote.money > threshold) {
            ShopRender.navigate { confirm.open(player, shop, entry, bundles) }
            return null
        }

        return ShopTrade.buy(player, shop, entry, bundles)
    }

    private fun sellMax(player: Player, shop: ShopDefinition, entry: ShopEntry): ShopTrade.Result {
        val bundles = ShopTrade.maxSellable(player, shop, entry)
        if (bundles > 0) return ShopTrade.sell(player, shop, entry, bundles)

        ShopLimits.remaining(player, shop, entry, TradeSide.SELL)?.let { left ->
            if (left < entry.bundleSize) {
                return ShopTrade.Result.Failure("shop.error.sell-limit-reached", listOf("amount" to left))
            }
        }
        return ShopTrade.Result.Failure("shop.error.missing-goods")
    }

    /**
     * Why a shift-click could not buy anything at all.
     *
     * A limit and a stock are counted in items, so either can leave a remainder
     * too small for one more bundle while still reading as more than nothing —
     * telling that player they cannot afford it would simply be untrue.
     */
    private fun nothingToBuy(player: Player, shop: ShopDefinition, entry: ShopEntry): ShopTrade.Result.Failure {
        ShopLimits.remaining(player, shop, entry, TradeSide.BUY)?.let { left ->
            if (left < entry.bundleSize) {
                return ShopTrade.Result.Failure("shop.error.limit-reached", listOf("amount" to left))
            }
        }

        entry.stock?.let { stock ->
            if (stock.available(System.currentTimeMillis()) < entry.bundleSize) {
                return ShopTrade.Result.Failure("shop.error.out-of-stock", listOf("amount" to stock.remaining))
            }
        }

        return ShopTrade.Result.Failure("shop.error.cannot-afford", listOf("price" to unitPrice(player, entry)))
    }

    private fun announce(player: Player, entry: ShopEntry, result: ShopTrade.Result.Success) {
        val key = if (result.money > 0.0 || entry.buy?.hasMoney == true) "shop.traded" else "shop.traded-items"
        player.sendPrefixed(
            player.tr(
                key,
                "amount" to entry.bundleSize * result.bundles,
                "item" to ShopRender.itemName(entry.item),
                "price" to ShopRender.money(result.money),
            )
        )
        player.playSound(Sound.sound(Key.key("minecraft:entity.villager.yes"), Sound.Source.UI, 1f, 1f))
    }

    private fun redraw(
        event: InventoryClickEvent,
        view: View,
        index: Int,
        player: Player,
        shop: ShopDefinition,
        entry: ShopEntry,
    ) {
        val refreshed = draw(player, shop, entry, ShopAccess.standing(player))
        view.items[index] = refreshed
        event.inventory.setItem(event.rawSlot, refreshed)
    }

    // ── Drawing ─────────────────────────────────────────────────────────

    private fun render(viewer: Player, shop: ShopDefinition): View {
        val standing = ShopAccess.standing(viewer)
        val entryIds = mutableListOf<String?>()
        val items = mutableListOf<ItemStack>()

        for (entry in shop.entries) {
            val refusal = ShopAccess.refusalKey(viewer, entry.gate, standing)
            if (refusal != null && entry.gate.hideWhenLocked) continue

            entryIds += entry.id
            items += draw(viewer, shop, entry, standing)
        }

        if (items.isEmpty()) {
            entryIds += null
            items += itemStack(Material.BARRIER) {
                name(viewer.tr("gui.shop.empty"))
                meta { lore(LoreUtil.wrapLore(viewer.tr("gui.shop.empty-lore"))) }
            }
        }

        return View(shop.id, entryIds, items)
    }

    private fun draw(
        viewer: Player,
        shop: ShopDefinition,
        entry: ShopEntry,
        standing: Set<TownyRequirement>,
    ): ItemStack {
        val lines = mutableListOf<String>()
        val refusal = ShopAccess.refusalKey(viewer, entry.gate, standing)

        if (entry.isBuyable) {
            val quote = ShopTrade.quoteBuy(viewer, entry, 1, standing)
            if (quote != null && quote.isDiscounted) {
                lines += viewer.tr(
                    "gui.shop.buy-discounted",
                    "price" to ShopRender.money(quote.money),
                    "full" to ShopRender.money(quote.fullMoney),
                )
            } else if (entry.buy?.hasMoney == true) {
                lines += viewer.tr("gui.shop.buy", "price" to ShopRender.money(quote?.money ?: 0.0))
            }
            entry.buy?.items?.forEach { lines += ShopRender.itemLine(viewer, it) }
        }

        if (entry.isSellable) {
            entry.sell?.let { payout ->
                if (payout.hasMoney) lines += viewer.tr("gui.shop.sell", "price" to ShopRender.money(payout.money))
                payout.items.forEach { lines += ShopRender.itemLine(viewer, it) }
            }
        }

        entry.stock?.let { stock ->
            lines += viewer.tr(
                "gui.shop.stock",
                "amount" to stock.available(System.currentTimeMillis()),
                "max" to stock.max,
            )
        }

        entry.buyLimit?.let { limit ->
            lines += viewer.tr(
                "gui.shop.limit",
                "amount" to (ShopLimits.remaining(viewer, shop, entry, TradeSide.BUY) ?: limit.amount),
                "period" to ShopRender.periodName(viewer, limit.period),
            )
        }

        entry.sellLimit?.let { limit ->
            lines += viewer.tr(
                "gui.shop.sell-limit",
                "amount" to (ShopLimits.remaining(viewer, shop, entry, TradeSide.SELL) ?: limit.amount),
                "period" to ShopRender.periodName(viewer, limit.period),
            )
        }

        if (refusal != null) {
            lines += viewer.tr(refusal)
        } else {
            if (entry.isBuyable) {
                lines += viewer.tr("gui.shop.click-buy")
                lines += viewer.tr("gui.shop.click-buy-max")
            }
            if (entry.isSellable) {
                lines += viewer.tr("gui.shop.click-sell")
                lines += viewer.tr("gui.shop.click-sell-max")
            }
        }

        return ShopRender.withLore(entry.displayStack(), lines)
    }

    private fun shopOf(player: Player): ShopDefinition? = views[player.uniqueId]?.let { ShopManager.get(it.shopId) }

    private fun unitPrice(player: Player, entry: ShopEntry): String =
        ShopRender.money(ShopTrade.quoteBuy(player, entry, 1)?.money ?: 0.0)

    private fun confirmGUI(): ShopConfirmGUI? = GUIManager.getGUI(ShopConfirmGUI.ID) as? ShopConfirmGUI

    companion object {
        const val ID = "shop"

        /** Opens [shop] for [viewer] through the registered instance. */
        fun show(viewer: Player, shop: ShopDefinition): Boolean {
            val gui = GUIManager.getGUI(ID) as? ShopGUI ?: return false
            gui.open(viewer, shop)
            return true
        }
    }
}
