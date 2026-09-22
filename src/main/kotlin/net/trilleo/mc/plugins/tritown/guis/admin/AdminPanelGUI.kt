package net.trilleo.mc.plugins.tritown.guis.admin

import com.palmergames.bukkit.towny.TownyAPI
import net.trilleo.mc.plugins.tritown.Main
import net.trilleo.mc.plugins.tritown.config.ShopSettings
import net.trilleo.mc.plugins.tritown.config.StorageSettings
import net.trilleo.mc.plugins.tritown.economy.EconomyPulse
import net.trilleo.mc.plugins.tritown.enums.FillMode
import net.trilleo.mc.plugins.tritown.enums.FlowCategory
import net.trilleo.mc.plugins.tritown.enums.StatsWindow
import net.trilleo.mc.plugins.tritown.guis.menu.MainMenuGUI
import net.trilleo.mc.plugins.tritown.guis.menu.MenuRender
import net.trilleo.mc.plugins.tritown.guis.shop.ShopRender
import net.trilleo.mc.plugins.tritown.registration.GUIFrame
import net.trilleo.mc.plugins.tritown.registration.GUIManager
import net.trilleo.mc.plugins.tritown.registration.PluginGUI
import net.trilleo.mc.plugins.tritown.shops.ShopManager
import net.trilleo.mc.plugins.tritown.storage.StorageManager
import net.trilleo.mc.plugins.tritown.utils.EconomyUtil
import net.trilleo.mc.plugins.tritown.utils.TownyUtil
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

/**
 * The way in to everything an administrator runs the server from.
 *
 * Deliberately thin: it names the sections and shows just enough of each to say
 * whether it is worth opening. It is reached from the main menu as well as by
 * command, so it leads back there. Everything a section knows lives in that
 * section's own menu, so adding one here is adding a card, not rewriting this.
 */
class AdminPanelGUI : PluginGUI(
    id = ID,
    titleKey = "gui.admin.title",
    rows = 6,
    fillMode = FillMode.NONE,
) {

    private enum class Card { ECONOMY, SHOPS, STORAGE, SERVER }

    override fun setup(player: Player, inventory: Inventory) {
        val cards = cardsFor(player)
        GUIFrame.draw(inventory, cards.keys + BACK_SLOT)

        cards.forEach { (slot, card) ->
            val item = when (card) {
                Card.ECONOMY -> economy(player)
                Card.SHOPS -> shops(player)
                Card.STORAGE -> storage(player)
                Card.SERVER -> server(player)
            }
            inventory.setItem(slot, item)
        }
        inventory.setItem(BACK_SLOT, MenuRender.back(player))
    }

    override fun onClick(event: InventoryClickEvent) {
        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        if (event.clickedInventory !== event.view.topInventory) return

        if (event.rawSlot == BACK_SLOT) {
            MenuRender.later(player) { MainMenuGUI.show(player) }
            return
        }

        when (cardsFor(player)[event.rawSlot]) {
            Card.ECONOMY -> GUIManager.openLater(player, EconomyPanelGUI.ID)
            Card.SHOPS -> GUIManager.openLater(player, AdminShopsGUI.ID)
            Card.STORAGE -> MenuRender.later(player) { AdminStorageGUI.show(player) }
            Card.SERVER, null -> Unit
        }
    }

    /** The cards [player] may open, centred along the row, so one they lack leaves no gap. */
    private fun cardsFor(player: Player): Map<Int, Card> {
        val cards = listOfNotNull(
            Card.ECONOMY.takeIf { player.hasPermission(ECONOMY_PERMISSION) },
            Card.SHOPS.takeIf { player.hasPermission(SHOPS_PERMISSION) },
            Card.STORAGE.takeIf { player.hasPermission(STORAGE_PERMISSION) },
            Card.SERVER,
        )
        return GUIFrame.spacedColumns(cards.size).zip(cards)
            .associate { (column, card) -> CARD_ROW * 9 + column to card }
    }

    // ── Cards ───────────────────────────────────────────────────────────

    private fun economy(player: Player): ItemStack {
        val lines = mutableListOf(player.tr("gui.admin.economy-lore"))

        if (!EconomyPulse.isEnabled) {
            lines += player.tr("gui.admin.economy-stats-off")
        } else {
            val supply = EconomyPulse.latest()
            val flow = EconomyPulse.window(StatsWindow.DAY.hours)
            lines += ""
            lines += player.tr(
                "gui.admin.economy-supply",
                "amount" to PanelRender.money(supply?.total ?: 0L),
            )
            lines += player.tr(
                "gui.admin.economy-net",
                "amount" to PanelRender.delta(player, flow.net),
                "window" to player.tr(StatsWindow.DAY.key),
            )
            lines += player.tr("gui.admin.economy-accounts", "amount" to (supply?.accountCount ?: 0))
        }

        lines += ""
        lines += player.tr("gui.admin.click-open")
        return PanelRender.card(Material.GOLD_INGOT, player.tr("gui.admin.economy"), lines)
    }

    private fun shops(player: Player): ItemStack {
        val lines = mutableListOf(player.tr("gui.admin.shops-lore"))

        if (!ShopSettings.isLoaded || !ShopSettings.snapshot.enabled || !ShopManager.isReady) {
            lines += player.tr("gui.admin.shops-off")
        } else {
            val shops = ShopManager.all()
            val moneyIn = shops.sumOf { shop -> shop.entries.sumOf { it.stats.moneyIn } }
            val moneyOut = shops.sumOf { shop -> shop.entries.sumOf { it.stats.moneyOut } }
            lines += ""
            lines += player.tr("gui.admin.shops-count", "amount" to shops.size)
            lines += player.tr("gui.admin.shops-taken", "amount" to ShopRender.money(moneyIn))
            lines += player.tr("gui.admin.shops-paid", "amount" to ShopRender.money(moneyOut))
        }

        lines += ""
        lines += player.tr("gui.admin.click-open")
        return PanelRender.card(Material.EMERALD, player.tr("gui.admin.shops"), lines)
    }

    private fun storage(player: Player): ItemStack {
        val lines = mutableListOf(player.tr("gui.admin.storage-lore"))

        if (!StorageManager.isAvailable) {
            lines += player.tr("gui.admin.storage-off")
        } else {
            val settings = StorageSettings.snapshot
            lines += ""
            lines += player.tr("gui.admin.storage-free", "amount" to settings.freePages, "max" to settings.maxPages)
            lines += player.tr("gui.admin.storage-open", "amount" to StorageManager.cachedAll().size)
            if (EconomyPulse.isEnabled) {
                val revenue = EconomyPulse.window(StatsWindow.DAY.hours).destroyedBy[FlowCategory.STORAGE] ?: 0L
                lines += player.tr(
                    "gui.admin.storage-revenue",
                    "amount" to PanelRender.money(revenue),
                    "window" to player.tr(StatsWindow.DAY.key),
                )
            }
        }

        lines += ""
        lines += player.tr("gui.admin.click-open")
        return PanelRender.card(Material.ENDER_CHEST, player.tr("gui.admin.storage"), lines)
    }

    /**
     * What the server is running, in the two or three numbers that say whether
     * anything is wrong before the sections are opened.
     */
    private fun server(player: Player): ItemStack {
        val towny = TownyAPI.getInstance()
        val provider = runCatching { EconomyUtil.economy.name }.getOrNull()

        val lines = listOf(
            player.tr("gui.admin.server-version", "version" to Main.instance.pluginMeta.version),
            player.tr(
                "gui.admin.server-provider",
                "provider" to (provider ?: player.tr("gui.admin.server-no-provider")),
            ),
            "",
            player.tr("gui.admin.server-towns", "amount" to towny.towns.size),
            player.tr("gui.admin.server-nations", "amount" to towny.nations.size),
            player.tr("gui.admin.server-online", "amount" to Bukkit.getOnlinePlayers().size),
            player.tr(
                "gui.admin.server-newday",
                "time" to TownyUtil.duration(player, TownyUtil.secondsUntilNewDay()),
            ),
        )

        return PanelRender.card(Material.BEACON, player.tr("gui.admin.server"), lines)
    }

    companion object {
        const val ID = "admin-panel"

        /** Opening the panel at all; each section asks for its own node on top of it. */
        const val PERMISSION = "tritown.admin"

        const val ECONOMY_PERMISSION = "tritown.admin.economy"
        const val SHOPS_PERMISSION = "tritown.admin.shops"
        const val STORAGE_PERMISSION = "tritown.admin.storage"

        private const val CARD_ROW = 2
        private const val BACK_SLOT = 49
    }
}
