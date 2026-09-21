package net.trilleo.mc.plugins.tritown.guis.menu

import com.palmergames.bukkit.towny.TownyAPI
import net.trilleo.mc.plugins.tritown.config.ShopSettings
import net.trilleo.mc.plugins.tritown.economy.BaltopCache
import net.trilleo.mc.plugins.tritown.economy.CurrencyRegistry
import net.trilleo.mc.plugins.tritown.economy.EconomyService
import net.trilleo.mc.plugins.tritown.enums.FillMode
import net.trilleo.mc.plugins.tritown.guis.admin.AdminPanelGUI
import net.trilleo.mc.plugins.tritown.guis.admin.PanelRender
import net.trilleo.mc.plugins.tritown.registration.CommandRegistrar
import net.trilleo.mc.plugins.tritown.registration.GUIFrame
import net.trilleo.mc.plugins.tritown.registration.GUIManager
import net.trilleo.mc.plugins.tritown.registration.PluginGUI
import net.trilleo.mc.plugins.tritown.scoreboard.ScoreboardService
import net.trilleo.mc.plugins.tritown.shops.ShopAccess
import net.trilleo.mc.plugins.tritown.shops.ShopManager
import net.trilleo.mc.plugins.tritown.towns.FoundingCredit
import net.trilleo.mc.plugins.tritown.trades.TradeManager
import net.trilleo.mc.plugins.tritown.utils.ComponentUtil
import net.trilleo.mc.plugins.tritown.utils.EconomyUtil
import net.trilleo.mc.plugins.tritown.utils.TownyUtil
import net.trilleo.mc.plugins.tritown.utils.itemStack
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * The way in to everything TriTown offers a player.
 *
 * The profile sits on its own at the top, and the buttons below it are laid
 * out in rows of up to three. A button for something the server has switched
 * off, or the viewer may not use, is left out rather than shown greyed, and
 * each row is centred on what is left — so the menu never has a hole in it,
 * whichever features a server runs. Where a button leads, the menu it opens
 * does the work; this only points the way.
 *
 * Opened by right-clicking the [net.trilleo.mc.plugins.tritown.menu.MenuItem]
 * or with `/tritown menu`.
 */
class MainMenuGUI : PluginGUI(
    id = ID,
    titleKey = "gui.menu.title",
    rows = 6,
    fillMode = FillMode.NONE,
) {

    private enum class Button { PROFILE, TOWN, SHOP, TRADE, PAY, LEADERBOARD, SERVER, SIDEBAR, ADMIN, CLOSE }

    /** Which button each slot holds, per viewer, since the buttons shown depend on who is looking. */
    private val layouts = ConcurrentHashMap<UUID, Map<Int, Button>>()

    override fun setup(player: Player, inventory: Inventory) {
        val layout = layout(player)
        layouts[player.uniqueId] = layout

        GUIFrame.draw(inventory, layout.keys)
        layout.forEach { (slot, button) -> inventory.setItem(slot, render(player, button)) }
    }

    override fun onClick(event: InventoryClickEvent) {
        event.isCancelled = true
        if (event.clickedInventory !== event.view.topInventory) return

        val player = event.whoClicked as? Player ?: return
        when (layouts[player.uniqueId]?.get(event.rawSlot)) {
            Button.TOWN -> MenuRender.later(player) { player.performCommand(TOWNY_MENU_COMMAND) }
            Button.SHOP -> MenuRender.later(player) { CommandRegistrar.run(player, "trades") }
            Button.TRADE -> MenuRender.later(player) { PlayerPickerGUI.show(player, PlayerPickerGUI.Mode.TRADE) }
            Button.PAY -> MenuRender.later(player) { PlayerPickerGUI.show(player, PlayerPickerGUI.Mode.PAY) }
            Button.LEADERBOARD -> MenuRender.later(player) { LeaderboardGUI.show(player) }
            Button.ADMIN -> MenuRender.later(player) { GUIManager.open(player, AdminPanelGUI.ID) }
            Button.CLOSE -> MenuRender.later(player) { player.closeInventory() }
            Button.SIDEBAR -> {
                ScoreboardService.toggle(player)
                MenuRender.click(player)
                GUIManager.refresh(player)
            }

            Button.PROFILE, Button.SERVER, null -> Unit
        }
    }

    override fun onClose(event: InventoryCloseEvent) {
        layouts.remove(event.player.uniqueId)
    }

    // ── Layout ──────────────────────────────────────────────────────────

    /**
     * The profile in the first row inside the border, then the rows of buttons
     * the viewer gets, each centred and with empty rows dropped so the rest
     * close up beneath it.
     */
    private fun layout(player: Player): Map<Int, Button> {
        val rows = listOf(
            listOf(Button.PROFILE),
            listOfNotNull(
                Button.TOWN.takeIf { hasTownyMenu() },
                Button.SHOP.takeIf { globalShopFor(player) },
                Button.TRADE.takeIf { TradeManager.isEnabled },
            ),
            listOfNotNull(
                Button.PAY.takeIf { hasEconomy() && CommandRegistrar.canRun(player, "pay") },
                Button.LEADERBOARD.takeIf { hasEconomy() && CommandRegistrar.canRun(player, "baltop") },
                Button.SERVER,
            ),
            listOfNotNull(
                Button.SIDEBAR.takeIf { ScoreboardService.isRunning },
                Button.ADMIN.takeIf { player.hasPermission(AdminPanelGUI.PERMISSION) },
            ),
        ).filter { it.isNotEmpty() }

        val layout = mutableMapOf<Int, Button>()
        rows.forEachIndexed { index, buttons ->
            GUIFrame.spacedColumns(buttons.size).zip(buttons).forEach { (column, button) ->
                layout[(FIRST_ROW + index) * ROW_SIZE + column] = button
            }
        }
        layout[CLOSE_SLOT] = Button.CLOSE
        return layout
    }

    private fun hasTownyMenu(): Boolean = Bukkit.getPluginManager().isPluginEnabled(TOWNY_MENU)

    private fun hasEconomy(): Boolean = EconomyService.isReady && CurrencyRegistry.isLoaded

    private fun globalShopFor(player: Player): Boolean {
        if (!ShopSettings.isLoaded || !ShopSettings.snapshot.enabled || !ShopManager.isReady) return false
        val shop = ShopManager.global() ?: return false
        return ShopAccess.canOpen(player, shop)
    }

    // ── Buttons ─────────────────────────────────────────────────────────

    private fun render(player: Player, button: Button): ItemStack = when (button) {
        Button.PROFILE -> profile(player)
        Button.TOWN -> town(player)
        Button.SHOP -> card(player, Material.EMERALD, "gui.menu.shop", "gui.menu.shop-lore")
        Button.TRADE -> trade(player)
        Button.PAY -> card(player, Material.GOLD_INGOT, "gui.menu.pay", "gui.menu.pay-lore")
        Button.LEADERBOARD -> leaderboard(player)
        Button.SERVER -> server(player)
        Button.SIDEBAR -> sidebar(player)
        Button.ADMIN -> card(player, Material.COMMAND_BLOCK, "gui.menu.admin", "gui.menu.admin-lore")
        Button.CLOSE -> itemStack(Material.BARRIER) { name(player.tr("gui.menu.close")) }
    }

    /** A button that only says what it opens. */
    private fun card(player: Player, material: Material, nameKey: String, loreKey: String): ItemStack =
        PanelRender.card(material, player.tr(nameKey), listOf(player.tr(loreKey), "", player.tr("gui.admin.click-open")))

    private fun profile(player: Player): ItemStack {
        val resident = TownyAPI.getInstance().getResident(player)
        val none = player.tr("common.none")
        val lines = mutableListOf<String>()

        if (EconomyUtil.isAvailable) {
            val balance = ComponentUtil.escape(EconomyUtil.format(EconomyUtil.balance(player)))
            lines += player.tr("gui.menu.profile-balance", "balance" to balance)
        }
        if (FoundingCredit.holds(player)) {
            lines += player.tr("gui.menu.profile-credit", "amount" to FoundingCredit.display())
        }
        rank(player)?.let { lines += player.tr("gui.menu.profile-rank", "rank" to it) }

        lines += ""
        lines += player.tr(
            "gui.menu.profile-town",
            "town" to (resident?.townOrNull?.let { TownyUtil.name(it.name) } ?: none),
        )
        lines += player.tr(
            "gui.menu.profile-nation",
            "nation" to (resident?.nationOrNull?.let { TownyUtil.name(it.name) } ?: none),
        )

        return MenuRender.head(player, player.tr("gui.menu.profile", "name" to ComponentUtil.escape(player.name)), lines)
    }

    /** The viewer's town at a glance, or how to get one, on the button that opens TownyMenu. */
    private fun town(player: Player): ItemStack {
        val town = TownyAPI.getInstance().getResident(player)?.townOrNull
        val lines = mutableListOf<String>()

        if (town == null) {
            lines += player.tr("gui.menu.town-none")
            if (FoundingCredit.holds(player)) {
                lines += player.tr("gui.menu.town-credit", "amount" to FoundingCredit.display())
            }
        } else {
            lines += player.tr("gui.menu.town-name", "town" to TownyUtil.name(town.name))
            lines += player.tr("gui.menu.town-residents", "amount" to town.numResidents)
            lines += player.tr("gui.menu.town-bank", "amount" to TownyUtil.balance(town))
            lines += player.tr("gui.menu.town-upkeep", "amount" to TownyUtil.money(TownyUtil.townUpkeep(town)))
            lines += player.tr(
                "gui.admin.server-newday",
                "time" to TownyUtil.duration(player, TownyUtil.secondsUntilNewDay()),
            )
        }

        lines += ""
        lines += player.tr("gui.menu.town-open")
        return PanelRender.card(Material.BELL, player.tr("gui.menu.town"), lines)
    }

    /** Glows while somebody is waiting for an answer, which is the one thing here worth interrupting for. */
    private fun trade(player: Player): ItemStack {
        val waiting = TradeManager.requestersFor(player.uniqueId).count { Bukkit.getPlayer(it) != null }
        val lines = mutableListOf(player.tr("gui.menu.trade-lore"))
        if (waiting > 0) lines += player.tr("gui.menu.trade-waiting", "amount" to waiting)
        lines += ""
        lines += player.tr("gui.admin.click-open")

        return PanelRender.card(Material.CHEST, player.tr("gui.menu.trade"), lines).also { item ->
            if (waiting > 0) item.editMeta { it.setEnchantmentGlintOverride(true) }
        }
    }

    /** The top three, so the leaderboard is worth a glance without being opened. */
    private fun leaderboard(player: Player): ItemStack {
        val lines = mutableListOf(player.tr("gui.menu.leaderboard-lore"))
        val top = BaltopCache.current.entries.take(PREVIEW_SIZE)
        if (top.isNotEmpty()) {
            lines += ""
            top.forEachIndexed { index, entry ->
                lines += player.tr(
                    "gui.menu.leaderboard-entry",
                    "rank" to index + 1,
                    "name" to LeaderboardGUI.name(entry),
                    "balance" to LeaderboardGUI.balance(entry),
                )
            }
        }
        lines += ""
        lines += player.tr("gui.admin.click-open")
        return PanelRender.card(Material.GOLD_BLOCK, player.tr("gui.menu.leaderboard"), lines)
    }

    private fun server(player: Player): ItemStack {
        val towny = TownyAPI.getInstance()
        val lines = listOf(
            player.tr("gui.admin.server-online", "amount" to Bukkit.getOnlinePlayers().size),
            player.tr("gui.admin.server-towns", "amount" to towny.towns.size),
            player.tr("gui.admin.server-nations", "amount" to towny.nations.size),
            "",
            player.tr("gui.admin.server-newday", "time" to TownyUtil.duration(player, TownyUtil.secondsUntilNewDay())),
        )
        return PanelRender.card(Material.CLOCK, player.tr("gui.menu.server"), lines)
    }

    private fun sidebar(player: Player): ItemStack {
        val shown = ScoreboardService.isEnabledFor(player)
        val lines = listOf(
            player.tr(if (shown) "gui.menu.sidebar-on" else "gui.menu.sidebar-off"),
            "",
            player.tr(if (shown) "gui.menu.sidebar-hide" else "gui.menu.sidebar-show"),
        )
        val material = if (shown) Material.LIME_DYE else Material.GRAY_DYE
        return PanelRender.card(material, player.tr("gui.menu.sidebar"), lines)
    }

    /** The viewer's place on the leaderboard, when the economy is TriTown's and they are on it. */
    private fun rank(player: Player): Int? {
        if (!hasEconomy()) return null
        val index = BaltopCache.current.entries.indexOfFirst { it.uuid == player.uniqueId }
        return if (index < 0) null else index + 1
    }

    companion object {
        const val ID = "main-menu"

        private const val ROW_SIZE = 9
        private const val FIRST_ROW = 1
        private const val CLOSE_SLOT = 49
        private const val PREVIEW_SIZE = 3

        private const val TOWNY_MENU = "TownyMenu"
        private const val TOWNY_MENU_COMMAND = "townymenu"

        /** Opens the main menu for [player]. */
        fun show(player: Player): Boolean = GUIManager.open(player, ID)
    }
}
