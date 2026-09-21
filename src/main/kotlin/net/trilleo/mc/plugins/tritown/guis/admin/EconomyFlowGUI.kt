package net.trilleo.mc.plugins.tritown.guis.admin

import net.trilleo.mc.plugins.tritown.economy.EconomyPulse
import net.trilleo.mc.plugins.tritown.enums.AccountType
import net.trilleo.mc.plugins.tritown.enums.FillMode
import net.trilleo.mc.plugins.tritown.enums.FlowCategory
import net.trilleo.mc.plugins.tritown.enums.PagedLayout
import net.trilleo.mc.plugins.tritown.registration.GUIManager
import net.trilleo.mc.plugins.tritown.registration.PagedPluginGUI
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Every source of money and every holder of it, in full.
 *
 * The overview shows the three largest of each because that is what fits on a
 * card; this is the same window opened out, so a category that only ever moves a
 * little is still visible, and each one carries its own net — which is the line
 * that says whether it feeds the economy or drains it.
 *
 * The figures are built once when the menu opens, because a paged menu asks for
 * its items again on every page turn and re-totalling the window each time would
 * do the same work several times per click.
 */
class EconomyFlowGUI : PagedPluginGUI(
    id = ID,
    titleKey = "gui.admin-flow.title",
    rows = 6,
    fillMode = FillMode.NONE,
    layout = PagedLayout.FRAMED,
) {

    private val snapshots = ConcurrentHashMap<UUID, List<ItemStack>>()

    override fun setup(player: Player, inventory: Inventory) {
        snapshots[player.uniqueId] = render(player)
        super.setup(player, inventory)
    }

    override fun getItems(player: Player): List<ItemStack> = snapshots[player.uniqueId] ?: emptyList()

    override fun navButtons(player: Player): Map<Int, ItemStack> = mapOf(
        BACK_OFFSET to PanelRender.card(
            Material.ARROW,
            player.tr("gui.admin-flow.back"),
            listOf(player.tr("gui.admin-flow.back-lore")),
        ),
        WINDOW_OFFSET to PanelRender.card(
            Material.CLOCK,
            player.tr("gui.admin-economy.window", "window" to player.tr(PanelState.window(player).key)),
            listOf(player.tr("gui.admin-economy.window-lore")),
        ),
    )

    override fun onNavClick(event: InventoryClickEvent, offset: Int) {
        val player = event.whoClicked as? Player ?: return

        when (offset) {
            BACK_OFFSET -> GUIManager.openLater(player, EconomyPanelGUI.ID)
            WINDOW_OFFSET -> {
                PanelState.cycle(player, event.click != ClickType.RIGHT)
                setup(player, event.inventory)
            }
        }
    }

    override fun onClose(event: InventoryCloseEvent) {
        super.onClose(event)
        snapshots.remove((event.player as? Player)?.uniqueId ?: return)
    }

    // ── Rendering ───────────────────────────────────────────────────────

    private fun render(player: Player): List<ItemStack> {
        if (!EconomyPulse.isEnabled) {
            return listOf(
                PanelRender.card(
                    Material.BARRIER,
                    player.tr("gui.admin-economy.disabled"),
                    listOf(player.tr("gui.admin-economy.disabled-lore")),
                )
            )
        }

        val window = PanelState.window(player)
        val flow = EconomyPulse.window(window.hours)

        val items = mutableListOf(header(player, flow, window.key))
        flow.categories().forEach { items += category(player, flow, it) }

        items += PanelRender.card(
            Material.PAPER,
            player.tr("gui.admin-flow.holders"),
            listOf(player.tr("gui.admin-flow.holders-lore")),
        )
        holders(flow).forEach { items += holder(player, flow, it) }

        return items
    }

    private fun header(player: Player, flow: EconomyPulse.Flow, windowKey: String): ItemStack = PanelRender.card(
        Material.BOOK,
        player.tr("gui.admin-flow.header", "window" to player.tr(windowKey)),
        listOf(
            player.tr(
                "gui.admin-economy.window-range",
                "from" to PanelRender.time(flow.from),
                "to" to PanelRender.time(flow.to),
            ),
            "",
            player.tr("gui.admin-economy.generation-total", "amount" to PanelRender.money(flow.created)),
            player.tr("gui.admin-economy.sinks-total", "amount" to PanelRender.money(flow.destroyed)),
            player.tr("gui.admin-economy.net-total", "amount" to PanelRender.delta(player, flow.net)),
            "",
            player.tr("gui.admin-flow.movements", "amount" to flow.movements),
            player.tr("gui.admin-economy.circulation-volume", "amount" to PanelRender.money(flow.circulated)),
        ),
    )

    private fun category(player: Player, flow: EconomyPulse.Flow, category: FlowCategory): ItemStack {
        val created = flow.createdBy[category] ?: 0L
        val destroyed = flow.destroyedBy[category] ?: 0L

        return PanelRender.card(
            material(category),
            player.tr("gui.admin-flow.category", "name" to PanelRender.categoryName(player, category)),
            listOf(
                player.tr(
                    "gui.admin-flow.created",
                    "amount" to PanelRender.money(created),
                    "percent" to PanelRender.percent(PanelRender.share(created, flow.created)),
                ),
                player.tr(
                    "gui.admin-flow.destroyed",
                    "amount" to PanelRender.money(destroyed),
                    "percent" to PanelRender.percent(PanelRender.share(destroyed, flow.destroyed)),
                ),
                player.tr("gui.admin-flow.net", "amount" to PanelRender.delta(player, created - destroyed)),
                "",
                player.tr(descriptionOf(category)),
            ),
        )
    }

    private fun holder(player: Player, flow: EconomyPulse.Flow, type: AccountType): ItemStack {
        val received = flow.createdTo[type] ?: 0L
        val paid = flow.destroyedFrom[type] ?: 0L

        return PanelRender.card(
            material(type),
            player.tr("gui.admin-flow.holder", "name" to PanelRender.holderName(player, type)),
            listOf(
                player.tr("gui.admin-flow.received", "amount" to PanelRender.money(received)),
                player.tr("gui.admin-flow.paid", "amount" to PanelRender.money(paid)),
                player.tr("gui.admin-flow.net", "amount" to PanelRender.delta(player, received - paid)),
            ),
        )
    }

    /** Every kind of account that saw any money, most movement first. */
    private fun holders(flow: EconomyPulse.Flow): List<AccountType> =
        (flow.createdTo.keys + flow.destroyedFrom.keys)
            .sortedByDescending { (flow.createdTo[it] ?: 0L) + (flow.destroyedFrom[it] ?: 0L) }

    private fun material(category: FlowCategory): Material = when (category) {
        FlowCategory.STARTING_BALANCE -> Material.EGG
        FlowCategory.SHOP -> Material.EMERALD
        FlowCategory.STORAGE -> Material.ENDER_CHEST
        FlowCategory.TOWNY -> Material.BELL
        FlowCategory.ADMIN -> Material.COMMAND_BLOCK
        FlowCategory.PAYMENT -> Material.ENDER_PEARL
        FlowCategory.EXTERNAL -> Material.REDSTONE
        FlowCategory.OTHER -> Material.PAPER
    }

    private fun material(type: AccountType): Material = when (type) {
        AccountType.PLAYER -> Material.PLAYER_HEAD
        AccountType.TOWN -> Material.BRICKS
        AccountType.NATION -> Material.GOLDEN_HELMET
        AccountType.NPC -> Material.VILLAGER_SPAWN_EGG
        AccountType.SERVER -> Material.COMMAND_BLOCK
        AccountType.UNKNOWN -> Material.BARRIER
    }

    /** Spelled out rather than built from the enum, so every key is a literal the language test can see. */
    private fun descriptionOf(category: FlowCategory): String = when (category) {
        FlowCategory.STARTING_BALANCE -> "gui.admin-flow.about-starting-balance"
        FlowCategory.SHOP -> "gui.admin-flow.about-shop"
        FlowCategory.STORAGE -> "gui.admin-flow.about-storage"
        FlowCategory.TOWNY -> "gui.admin-flow.about-towny"
        FlowCategory.ADMIN -> "gui.admin-flow.about-admin"
        FlowCategory.PAYMENT -> "gui.admin-flow.about-payment"
        FlowCategory.EXTERNAL -> "gui.admin-flow.about-external"
        FlowCategory.OTHER -> "gui.admin-flow.about-other"
    }

    companion object {
        const val ID = "admin-flow"

        private const val BACK_OFFSET = 2
        private const val WINDOW_OFFSET = 6
    }
}
