package net.trilleo.mc.plugins.tritown.guis.admin

import net.trilleo.mc.plugins.tritown.config.ShopSettings
import net.trilleo.mc.plugins.tritown.enums.FillMode
import net.trilleo.mc.plugins.tritown.enums.PagedLayout
import net.trilleo.mc.plugins.tritown.guis.shop.ShopEditorGUI
import net.trilleo.mc.plugins.tritown.guis.shop.ShopRender
import net.trilleo.mc.plugins.tritown.guis.shop.ShopStatsGUI
import net.trilleo.mc.plugins.tritown.registration.GUIManager
import net.trilleo.mc.plugins.tritown.registration.PagedPluginGUI
import net.trilleo.mc.plugins.tritown.shops.ShopDefinition
import net.trilleo.mc.plugins.tritown.shops.ShopManager
import net.trilleo.mc.plugins.tritown.utils.ComponentUtil
import net.trilleo.mc.plugins.tritown.utils.LoreUtil
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack

/**
 * What every shop on the server has traded, side by side.
 *
 * A single shop's figures already have a menu of their own, reached from its
 * editor; this is the row above that, where a shop that is quietly draining the
 * economy stands out next to the ones that are not. Clicking a shop opens its
 * own figures, so the two are one view at two depths rather than two views of
 * the same thing.
 */
class AdminShopsGUI : PagedPluginGUI(
    id = ID,
    titleKey = "gui.admin-shops.title",
    rows = 6,
    fillMode = FillMode.NONE,
    layout = PagedLayout.FRAMED,
) {

    override fun getItems(player: Player): List<ItemStack> {
        if (!ShopSettings.isLoaded || !ShopSettings.snapshot.enabled || !ShopManager.isReady) {
            return listOf(
                PanelRender.card(
                    Material.BARRIER,
                    player.tr("gui.admin-shops.disabled"),
                    listOf(player.tr("gui.admin-shops.disabled-lore")),
                )
            )
        }

        val shops = ShopManager.all()
        if (shops.isEmpty()) {
            return listOf(
                PanelRender.card(
                    Material.BARRIER,
                    player.tr("gui.admin-shops.empty"),
                    listOf(player.tr("gui.admin-shops.empty-lore")),
                )
            )
        }

        return listOf(header(player, shops)) + shops.map { row(player, it) }
    }

    override fun navButtons(player: Player): Map<Int, ItemStack> = mapOf(
        BACK_OFFSET to PanelRender.card(
            Material.ARROW,
            player.tr("gui.admin-shops.back"),
            listOf(player.tr("gui.admin-shops.back-lore")),
        ),
    )

    override fun onNavClick(event: InventoryClickEvent, offset: Int) {
        val player = event.whoClicked as? Player ?: return
        if (offset == BACK_OFFSET) GUIManager.openLater(player, AdminPanelGUI.ID)
    }

    override fun onContentClick(event: InventoryClickEvent, page: Int) {
        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        if (!ShopManager.isReady) return

        // The header takes the first position, so the shops start one behind it.
        val index = (contentIndex(event, page) ?: return) - 1
        val shop = ShopManager.all().getOrNull(index) ?: return

        if (event.click == ClickType.SHIFT_LEFT) {
            if (player.hasPermission(EDIT_PERMISSION)) ShopRender.navigate { ShopEditorGUI.show(player, shop) }
            return
        }
        ShopRender.navigate { ShopStatsGUI.show(player, shop) }
    }

    // ── Rendering ───────────────────────────────────────────────────────

    private fun header(player: Player, shops: List<ShopDefinition>): ItemStack {
        val moneyIn = shops.sumOf { shop -> shop.entries.sumOf { it.stats.moneyIn } }
        val moneyOut = shops.sumOf { shop -> shop.entries.sumOf { it.stats.moneyOut } }
        val bought = shops.sumOf { shop -> shop.entries.sumOf { it.stats.bought } }
        val sold = shops.sumOf { shop -> shop.entries.sumOf { it.stats.sold } }

        return PanelRender.card(
            Material.WRITABLE_BOOK,
            player.tr("gui.admin-shops.header"),
            listOf(
                player.tr("gui.admin-shops.count", "amount" to shops.size),
                player.tr("gui.admin-shops.entries", "amount" to shops.sumOf { it.entries.size }),
                "",
                player.tr("gui.shop-stats.bought", "amount" to bought),
                player.tr("gui.shop-stats.sold", "amount" to sold),
                player.tr("gui.shop-stats.money-in", "amount" to ShopRender.money(moneyIn)),
                player.tr("gui.shop-stats.money-out", "amount" to ShopRender.money(moneyOut)),
                player.tr("gui.shop-stats.net", "amount" to ShopRender.money(moneyIn - moneyOut)),
                "",
                player.tr("gui.admin-shops.header-lore"),
            ),
        )
    }

    private fun row(player: Player, shop: ShopDefinition): ItemStack {
        val moneyIn = shop.entries.sumOf { it.stats.moneyIn }
        val moneyOut = shop.entries.sumOf { it.stats.moneyOut }

        val lines = listOf(
            player.tr("gui.admin-shops.id", "id" to shop.id),
            player.tr("gui.admin-shops.entries", "amount" to shop.entries.size),
            "",
            player.tr("gui.shop-stats.money-in", "amount" to ShopRender.money(moneyIn)),
            player.tr("gui.shop-stats.money-out", "amount" to ShopRender.money(moneyOut)),
            player.tr("gui.shop-stats.net", "amount" to ShopRender.money(moneyIn - moneyOut)),
            "",
            player.tr("gui.admin-shops.click-stats"),
            player.tr("gui.admin-shops.click-edit"),
        )

        // A shop wears its own goods, so a list of them is recognisable before a
        // single line of it has been read. The shop's name is written by an
        // administrator as MiniMessage, which is why it is not escaped.
        val icon = (shop.entries.firstOrNull()?.displayStack() ?: ItemStack(Material.CHEST)).clone()
        icon.editMeta { meta ->
            meta.displayName(ComponentUtil.parse(shop.displayName))
            meta.lore(null)
        }

        return LoreUtil.withLore(icon, lines)
    }

    companion object {
        const val ID = "admin-shops"

        private const val EDIT_PERMISSION = "tritown.shop.admin.edit"
        private const val BACK_OFFSET = 2
    }
}
