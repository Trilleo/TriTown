package net.trilleo.mc.plugins.tritown.guis.shop

import net.kyori.adventure.text.Component
import net.trilleo.mc.plugins.tritown.enums.FillMode
import net.trilleo.mc.plugins.tritown.enums.PagedLayout
import net.trilleo.mc.plugins.tritown.enums.TownyRequirement
import net.trilleo.mc.plugins.tritown.enums.TradeSide
import net.trilleo.mc.plugins.tritown.registration.GUIManager
import net.trilleo.mc.plugins.tritown.registration.PagedPluginGUI
import net.trilleo.mc.plugins.tritown.shops.*
import net.trilleo.mc.plugins.tritown.utils.ComponentUtil
import net.trilleo.mc.plugins.tritown.utils.LoreUtil
import net.trilleo.mc.plugins.tritown.utils.itemStack
import net.trilleo.mc.plugins.tritown.utils.tr
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
            ClickType.LEFT -> tradeBuy(player, shop, entry, entry.bundleSize)
            ClickType.SHIFT_LEFT -> chooseAmount(player, shop, entry)
            ClickType.RIGHT -> ShopTrade.sell(player, shop, entry, entry.bundleSize)
            ClickType.SHIFT_RIGHT -> sellMax(player, shop, entry)
            else -> return
        } ?: return

        when (result) {
            is ShopTrade.Result.Success -> {
                ShopRender.announce(player, entry, result)
                redraw(event, view, index, player, shop, entry)
            }

            is ShopTrade.Result.Failure -> ShopRender.refuse(player, result)
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
    private fun tradeBuy(player: Player, shop: ShopDefinition, entry: ShopEntry, amount: Int): ShopTrade.Result? {
        if (ShopConfirmGUI.askIfDear(player, shop, entry, amount)) return null
        return ShopTrade.buy(player, shop, entry, amount)
    }

    /**
     * Opens the amount menu, and reports nothing because the trade happens
     * there.
     *
     * Only offered for goods that stack: an amount menu for a single item is
     * five ways of saying one.
     */
    private fun chooseAmount(player: Player, shop: ShopDefinition, entry: ShopEntry): ShopTrade.Result? {
        if (!entry.isBuyable || !entry.isStackable) return null
        ShopRender.navigate { ShopAmountGUI.show(player, shop, entry) }
        return null
    }

    private fun sellMax(player: Player, shop: ShopDefinition, entry: ShopEntry): ShopTrade.Result {
        val amount = ShopTrade.maxSellable(player, shop, entry)
        if (amount > 0) return ShopTrade.sell(player, shop, entry, amount)

        ShopLimits.remaining(player, shop, entry, TradeSide.SELL)?.let { left ->
            if (left < entry.bundleSize) {
                return ShopTrade.Result.Failure("shop.error.sell-limit-reached", listOf("amount" to left))
            }
        }
        return ShopTrade.Result.Failure("shop.error.missing-goods")
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

    /**
     * One entry as it sits on the shelf.
     *
     * The lore is built in blocks — what it costs, what it pays, how much of it
     * is left, and what a click does — which [ShopRender.sections] spaces apart.
     * Reading a price should not mean picking it out of a list of instructions.
     */
    private fun draw(
        viewer: Player,
        shop: ShopDefinition,
        entry: ShopEntry,
        standing: Set<TownyRequirement>,
    ): ItemStack {
        val refusal = ShopAccess.refusalKey(viewer, entry.gate, standing)

        val lore = ShopRender.sections(
            listOf(
                ShopRender.buyLines(viewer, entry, entry.bundleSize, standing),
                ShopRender.sellLines(viewer, entry, entry.bundleSize),
                availabilityLines(viewer, shop, entry),
                if (refusal != null) listOf(viewer.tr(refusal)) else clickLines(viewer, entry),
            )
        )

        return LoreUtil.withLore(entry.displayStack(), lore)
    }

    private fun availabilityLines(viewer: Player, shop: ShopDefinition, entry: ShopEntry): List<String> {
        val lines = mutableListOf<String>()

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

        return lines
    }

    private fun clickLines(viewer: Player, entry: ShopEntry): List<String> {
        val lines = mutableListOf<String>()

        if (entry.isBuyable) {
            lines += viewer.tr("gui.shop.click-buy")
            if (entry.isStackable) lines += viewer.tr("gui.shop.click-amount")
        }
        if (entry.isSellable) {
            lines += viewer.tr("gui.shop.click-sell")
            lines += viewer.tr("gui.shop.click-sell-max")
        }

        return lines
    }

    private fun shopOf(player: Player): ShopDefinition? = views[player.uniqueId]?.let { ShopManager.get(it.shopId) }

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
