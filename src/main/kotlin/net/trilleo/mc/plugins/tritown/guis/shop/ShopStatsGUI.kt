package net.trilleo.mc.plugins.tritown.guis.shop

import net.trilleo.mc.plugins.tritown.enums.FillMode
import net.trilleo.mc.plugins.tritown.enums.PagedLayout
import net.trilleo.mc.plugins.tritown.registration.GUIManager
import net.trilleo.mc.plugins.tritown.registration.PagedPluginGUI
import net.trilleo.mc.plugins.tritown.shops.ShopDefinition
import net.trilleo.mc.plugins.tritown.shops.ShopEntry
import net.trilleo.mc.plugins.tritown.shops.ShopManager
import net.trilleo.mc.plugins.tritown.utils.LoreUtil
import net.trilleo.mc.plugins.tritown.utils.itemStack
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
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
 * What a shop has actually traded.
 *
 * The header totals the whole shop, so an owner can see at a glance whether it
 * is draining currency out of the economy or feeding it in, and each entry below
 * shows which line is responsible.
 */
class ShopStatsGUI : PagedPluginGUI(
    id = ID,
    titleKey = "gui.shop-stats.title",
    rows = 6,
    fillMode = FillMode.NONE,
    layout = PagedLayout.FRAMED,
) {

    private val viewing = ConcurrentHashMap<UUID, String>()

    /** Opens the figures for [shop]. */
    fun open(player: Player, shop: ShopDefinition) {
        viewing[player.uniqueId] = shop.id
        GUIManager.open(player, ID)
    }

    override fun getItems(player: Player): List<ItemStack> {
        val shop = shopOf(player) ?: return emptyList()
        return listOf(header(player, shop)) + shop.entries.map { entry -> row(player, entry) }
    }

    override fun onContentClick(event: InventoryClickEvent, page: Int) {
        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        val shop = shopOf(player) ?: return

        val index = contentIndex(event, page) ?: return
        if (index != 0 || event.click != ClickType.SHIFT_LEFT) return

        shop.entries.forEach { it.stats.reset() }
        ShopManager.save()
        player.sendPrefixed(player.tr("shop.stats-reset", "shop" to shop.displayName))
        ShopRender.navigate { show(player, shop) }
    }

    override fun onClose(event: InventoryCloseEvent) {
        super.onClose(event)
        viewing.remove((event.player as? Player)?.uniqueId ?: return)
    }

    private fun header(player: Player, shop: ShopDefinition): ItemStack {
        val bought = shop.entries.sumOf { it.stats.bought }
        val sold = shop.entries.sumOf { it.stats.sold }
        val moneyIn = shop.entries.sumOf { it.stats.moneyIn }
        val moneyOut = shop.entries.sumOf { it.stats.moneyOut }

        val lore = listOf(
            player.tr("gui.shop-stats.bought", "amount" to bought),
            player.tr("gui.shop-stats.sold", "amount" to sold),
            player.tr("gui.shop-stats.money-in", "amount" to ShopRender.money(moneyIn)),
            player.tr("gui.shop-stats.money-out", "amount" to ShopRender.money(moneyOut)),
            player.tr("gui.shop-stats.net", "amount" to ShopRender.money(moneyIn - moneyOut)),
            player.tr("gui.shop-stats.reset"),
        )

        return itemStack(Material.WRITABLE_BOOK) {
            name(shop.displayName)
            meta { lore(LoreUtil.wrapLore(lore.joinToString("<newline>"))) }
        }
    }

    private fun row(player: Player, entry: ShopEntry): ItemStack {
        val lore = listOf(
            player.tr("gui.shop-stats.bought", "amount" to entry.stats.bought),
            player.tr("gui.shop-stats.sold", "amount" to entry.stats.sold),
            player.tr("gui.shop-stats.money-in", "amount" to ShopRender.money(entry.stats.moneyIn)),
            player.tr("gui.shop-stats.money-out", "amount" to ShopRender.money(entry.stats.moneyOut)),
        )

        return LoreUtil.withLore(entry.displayStack(), lore)
    }

    private fun shopOf(player: Player): ShopDefinition? = viewing[player.uniqueId]?.let(ShopManager::get)

    companion object {
        const val ID = "shop-stats"

        /** Opens a shop's figures through the registered instance. */
        fun show(player: Player, shop: ShopDefinition): Boolean {
            val gui = GUIManager.getGUI(ID) as? ShopStatsGUI ?: return false
            gui.open(player, shop)
            return true
        }
    }
}
