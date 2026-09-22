package net.trilleo.mc.plugins.tritown.guis.admin

import net.trilleo.mc.plugins.tritown.commands.storage.StorageCommand
import net.trilleo.mc.plugins.tritown.config.StorageSettings
import net.trilleo.mc.plugins.tritown.economy.EconomyPulse
import net.trilleo.mc.plugins.tritown.enums.FillMode
import net.trilleo.mc.plugins.tritown.enums.FlowCategory
import net.trilleo.mc.plugins.tritown.enums.PagedLayout
import net.trilleo.mc.plugins.tritown.guis.menu.MenuRender
import net.trilleo.mc.plugins.tritown.guis.storage.StorageGUI
import net.trilleo.mc.plugins.tritown.guis.storage.StorageRender
import net.trilleo.mc.plugins.tritown.registration.GUIManager
import net.trilleo.mc.plugins.tritown.registration.PagedPluginGUI
import net.trilleo.mc.plugins.tritown.storage.StorageManager
import net.trilleo.mc.plugins.tritown.storage.StoragePage
import net.trilleo.mc.plugins.tritown.storage.storage.StorageSummary
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.ItemStack
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Every storage on the server, fullest first.
 *
 * Read from disk each time it is opened, without decoding an item, so it shows
 * offline players too. Clicking a storage opens it read-only; shift-clicking
 * opens it to change, for an administrator who may.
 */
class AdminStorageGUI : PagedPluginGUI(
    id = ID,
    titleKey = "gui.admin-storage.title",
    rows = 6,
    fillMode = FillMode.NONE,
    layout = PagedLayout.FRAMED,
) {

    private val snapshots = ConcurrentHashMap<UUID, List<StorageSummary>>()

    override fun getItems(player: Player): List<ItemStack> {
        if (!StorageManager.isAvailable) {
            return listOf(
                PanelRender.card(Material.BARRIER, player.tr("gui.admin-storage.disabled"), listOf(player.tr("gui.admin-storage.disabled-lore")))
            )
        }
        val summaries = snapshots[player.uniqueId].orEmpty()
        return listOf(header(player, summaries)) + summaries.map { row(player, it) }
    }

    override fun onContentClick(event: InventoryClickEvent, page: Int) {
        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return

        // The header takes the first position, so the storages start one behind it.
        val index = (contentIndex(event, page) ?: return) - 1
        val summary = snapshots[player.uniqueId]?.getOrNull(index) ?: return

        val edit = event.click == ClickType.SHIFT_LEFT && player.hasPermission(StorageCommand.ADMIN_PERMISSION)
        MenuRender.later(player) { StorageGUI.open(player, summary.owner, readOnly = !edit) }
    }

    override fun navButtons(player: Player): Map<Int, ItemStack> = mapOf(
        BACK_OFFSET to PanelRender.card(
            Material.ARROW,
            player.tr("gui.admin-storage.back"),
            listOf(player.tr("gui.admin-storage.back-lore")),
        ),
    )

    override fun onNavClick(event: InventoryClickEvent, offset: Int) {
        val player = event.whoClicked as? Player ?: return
        if (offset == BACK_OFFSET) GUIManager.openLater(player, AdminPanelGUI.ID)
    }

    override fun onClose(event: InventoryCloseEvent) {
        super.onClose(event)
        snapshots.remove(event.player.uniqueId)
    }

    // ── Rendering ───────────────────────────────────────────────────────

    private fun header(player: Player, summaries: List<StorageSummary>): ItemStack {
        val window = PanelState.window(player)
        val lines = mutableListOf(
            player.tr("gui.admin-storage.count", "amount" to summaries.size),
            player.tr("gui.admin-storage.bought", "amount" to summaries.sumOf { it.purchased }),
            player.tr("gui.admin-storage.slots", "amount" to summaries.sumOf { it.usedSlots }),
        )
        if (EconomyPulse.isEnabled) {
            val revenue = EconomyPulse.window(window.hours).destroyedBy[FlowCategory.STORAGE] ?: 0L
            lines += player.tr(
                "gui.admin-storage.revenue",
                "amount" to PanelRender.money(revenue),
                "window" to player.tr(window.key),
            )
        }
        lines += ""
        lines += player.tr("gui.admin-storage.header-lore")

        return PanelRender.card(Material.WRITABLE_BOOK, player.tr("gui.admin-storage.header"), lines)
    }

    private fun row(player: Player, summary: StorageSummary): ItemStack {
        val settings = StorageSettings.snapshot
        val owned = (settings.freePages + summary.purchased).coerceAtMost(settings.maxPages)

        val lines = mutableListOf(
            player.tr("gui.admin-storage.pages", "amount" to owned, "bought" to summary.purchased),
            player.tr(
                "gui.admin-storage.used",
                "amount" to summary.usedSlots,
                "total" to owned * StoragePage.SIZE,
            ),
            "",
            player.tr("gui.admin-storage.click-view"),
        )
        if (player.hasPermission(StorageCommand.ADMIN_PERMISSION)) lines += player.tr("gui.admin-storage.click-edit")

        return MenuRender.head(Bukkit.getOfflinePlayer(summary.owner), player.tr("gui.admin-storage.owner", "name" to StorageRender.ownerName(summary.owner)), lines)
    }

    companion object {
        const val ID = "admin-storage"

        private const val BACK_OFFSET = 2

        /** Reads every storage off disk and opens the list for [viewer] once it has. */
        fun show(viewer: Player) {
            val gui = GUIManager.getGUI(ID) as? AdminStorageGUI ?: return
            if (!StorageManager.isAvailable) {
                GUIManager.open(viewer, ID)
                return
            }
            StorageManager.summaries { summaries ->
                if (!viewer.isOnline) return@summaries
                gui.snapshots[viewer.uniqueId] = summaries.sortedByDescending { it.usedSlots }
                GUIManager.open(viewer, ID)
            }
        }
    }
}
