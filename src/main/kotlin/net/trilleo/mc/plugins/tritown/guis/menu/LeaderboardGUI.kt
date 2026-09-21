package net.trilleo.mc.plugins.tritown.guis.menu

import net.trilleo.mc.plugins.tritown.commands.economy.accountTypeName
import net.trilleo.mc.plugins.tritown.commands.economy.displayName
import net.trilleo.mc.plugins.tritown.economy.BaltopCache
import net.trilleo.mc.plugins.tritown.economy.CurrencyRegistry
import net.trilleo.mc.plugins.tritown.economy.EconomyFormat
import net.trilleo.mc.plugins.tritown.enums.FillMode
import net.trilleo.mc.plugins.tritown.enums.PagedLayout
import net.trilleo.mc.plugins.tritown.registration.GUIManager
import net.trilleo.mc.plugins.tritown.registration.PagedPluginGUI
import net.trilleo.mc.plugins.tritown.utils.ComponentUtil
import net.trilleo.mc.plugins.tritown.utils.LoreUtil
import net.trilleo.mc.plugins.tritown.utils.itemStack
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.ItemStack
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * The richest accounts, as heads, in the order `/baltop` lists them.
 *
 * It reads [BaltopCache] rather than the ledger, so opening it never sorts
 * anything, and it is built once when opened: paging through it shows the
 * leaderboard as it was then, rather than one that reshuffles under the cursor.
 */
class LeaderboardGUI : PagedPluginGUI(
    id = ID,
    titleKey = "gui.menu-leaderboard.title",
    rows = 6,
    fillMode = FillMode.NONE,
    layout = PagedLayout.CENTERED,
) {

    private val snapshots = ConcurrentHashMap<UUID, List<ItemStack>>()

    override fun getItems(player: Player): List<ItemStack> = snapshots[player.uniqueId] ?: emptyList()

    override fun onContentClick(event: InventoryClickEvent, page: Int) {
        event.isCancelled = true
    }

    override fun navButtons(player: Player): Map<Int, ItemStack> =
        mapOf(MenuRender.BACK_OFFSET to MenuRender.back(player), MenuRender.EXTRA_OFFSET to yours(player))

    override fun onNavClick(event: InventoryClickEvent, offset: Int) {
        val player = event.whoClicked as? Player ?: return
        if (offset == MenuRender.BACK_OFFSET) MenuRender.later(player) { MainMenuGUI.show(player) }
    }

    override fun onClose(event: InventoryCloseEvent) {
        super.onClose(event)
        snapshots.remove(event.player.uniqueId)
    }

    private fun open(viewer: Player) {
        snapshots[viewer.uniqueId] = render(viewer)
        GUIManager.open(viewer, ID)
    }

    private fun render(viewer: Player): List<ItemStack> {
        val entries = BaltopCache.current.entries.take(SIZE)
        if (entries.isEmpty()) {
            return listOf(itemStack(Material.BARRIER) { name(viewer.tr("gui.menu-leaderboard.empty")) })
        }
        return entries.mapIndexed { index, entry -> entry(viewer, index + 1, entry) }
    }

    private fun entry(viewer: Player, rank: Int, entry: BaltopCache.Entry): ItemStack {
        val title = viewer.tr(rankKey(rank), "rank" to rank, "name" to name(entry))
        val lines = buildList {
            add(viewer.tr("gui.menu-leaderboard.balance", "balance" to balance(entry)))
            if (entry.type.isGovernment) {
                add(viewer.tr("gui.menu-leaderboard.account", "type" to accountTypeName(viewer, entry.type)))
            }
            if (entry.uuid == viewer.uniqueId) {
                add("")
                add(viewer.tr("gui.menu-leaderboard.you"))
            }
        }

        if (!entry.type.isGovernment) {
            return MenuRender.head(Bukkit.getOfflinePlayer(entry.uuid), title, lines, glow = entry.uuid == viewer.uniqueId)
        }
        return itemStack(Material.BELL) {
            name(title)
            meta { lore(LoreUtil.wrapLore(lines.joinToString("<newline>"))) }
        }
    }

    /** Where the viewer stands, kept in the navigation row so it is on every page. */
    private fun yours(viewer: Player): ItemStack {
        val entries = BaltopCache.current.entries
        val index = entries.indexOfFirst { it.uuid == viewer.uniqueId }
        val line = if (index < 0) {
            viewer.tr("gui.menu-leaderboard.unranked")
        } else {
            viewer.tr("gui.menu-leaderboard.ranked", "rank" to index + 1, "balance" to balance(entries[index]))
        }
        return MenuRender.head(viewer, viewer.tr("gui.menu-leaderboard.your-rank"), listOf(line))
    }

    /** Spelled out rather than built from the rank, so every key is one the translation test can see. */
    private fun rankKey(rank: Int): String = when (rank) {
        1 -> "gui.menu-leaderboard.first"
        2 -> "gui.menu-leaderboard.second"
        3 -> "gui.menu-leaderboard.third"
        else -> "gui.menu-leaderboard.entry"
    }

    companion object {
        const val ID = "menu-leaderboard"

        /** Four full pages; `/baltop` pages further for anyone who wants the long tail. */
        private const val SIZE = 112

        /** Opens the leaderboard for [player] through the registered instance. */
        fun show(player: Player): Boolean {
            val gui = GUIManager.getGUI(ID) as? LeaderboardGUI ?: return false
            gui.open(player)
            return true
        }

        /** [entry]'s name, safe inside MiniMessage. */
        fun name(entry: BaltopCache.Entry): String = displayName(entry.name, entry.type)

        /** [entry]'s balance in the primary currency, safe inside MiniMessage. */
        fun balance(entry: BaltopCache.Entry): String =
            ComponentUtil.escape(EconomyFormat.plain(CurrencyRegistry.primary, entry.balance))
    }
}
