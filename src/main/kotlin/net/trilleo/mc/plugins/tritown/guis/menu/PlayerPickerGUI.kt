package net.trilleo.mc.plugins.tritown.guis.menu

import net.kyori.adventure.text.Component
import net.trilleo.mc.plugins.tritown.config.TradeSettings
import net.trilleo.mc.plugins.tritown.enums.FillMode
import net.trilleo.mc.plugins.tritown.enums.PagedLayout
import net.trilleo.mc.plugins.tritown.registration.CommandRegistrar
import net.trilleo.mc.plugins.tritown.registration.GUIManager
import net.trilleo.mc.plugins.tritown.registration.PagedPluginGUI
import net.trilleo.mc.plugins.tritown.trades.TradeManager
import net.trilleo.mc.plugins.tritown.utils.*
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.ItemStack
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * Picks another player to trade with or to pay.
 *
 * Choosing someone runs the same command typing it would — `/trade <name>` or
 * `/pay <name> <amount>` — so every rule and message stays with the command,
 * and this menu only saves the typing.
 *
 * For a trade it lists only the players close enough to trade with, with
 * anyone waiting for an answer first, since those are the only clicks that can
 * do anything. The list is a snapshot, and the refresh button takes a new one
 * as people move about.
 */
class PlayerPickerGUI : PagedPluginGUI(
    id = ID,
    titleKey = "gui.menu-players.title-trade",
    rows = 6,
    fillMode = FillMode.NONE,
    layout = PagedLayout.CENTERED,
) {

    /** What the picker is picking someone for. */
    enum class Mode { TRADE, PAY }

    /** One viewer's list: who each item stands for, `null` for an item that is only a notice. */
    private class View(val mode: Mode, val targets: List<UUID?>, val items: List<ItemStack>)

    private val views = ConcurrentHashMap<UUID, View>()

    override fun title(player: Player): Component {
        val key = if (views[player.uniqueId]?.mode == Mode.PAY) "gui.menu-players.title-pay" else titleKey
        return ComponentUtil.parse(player.tr(key))
    }

    override fun getItems(player: Player): List<ItemStack> = views[player.uniqueId]?.items ?: emptyList()

    override fun navButtons(player: Player): Map<Int, ItemStack> = mapOf(
        MenuRender.BACK_OFFSET to MenuRender.back(player),
        MenuRender.EXTRA_OFFSET to itemStack(Material.SPYGLASS) {
            name(player.tr("gui.menu-players.refresh"))
            meta { lore(LoreUtil.wrapLore(player.tr("gui.menu-players.refresh-lore"))) }
        },
    )

    override fun onNavClick(event: InventoryClickEvent, offset: Int) {
        val player = event.whoClicked as? Player ?: return
        when (offset) {
            MenuRender.BACK_OFFSET -> MenuRender.later(player) { MainMenuGUI.show(player) }
            MenuRender.EXTRA_OFFSET -> {
                val mode = views[player.uniqueId]?.mode ?: return
                views[player.uniqueId] = build(player, mode)
                MenuRender.click(player)
                refresh(player, event.inventory)
            }
        }
    }

    override fun onContentClick(event: InventoryClickEvent, page: Int) {
        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        val view = views[player.uniqueId] ?: return
        val uuid = contentIndex(event, page)?.let(view.targets::getOrNull) ?: return

        val target = Bukkit.getPlayer(uuid)
        if (target == null) {
            player.sendPrefixed(player.tr("common.error", "message" to player.tr("gui.menu-players.gone")))
            return
        }

        val name = target.name
        when (view.mode) {
            Mode.TRADE -> MenuRender.later(player) {
                player.closeInventory()
                CommandRegistrar.run(player, "trade", name)
            }

            Mode.PAY -> MenuRender.later(player) {
                player.closeInventory()
                ChatPrompt.ask(player, payPrompt(player, name)) { amount ->
                    CommandRegistrar.run(
                        player,
                        "pay",
                        name,
                        amount
                    )
                }
            }
        }
    }

    override fun onClose(event: InventoryCloseEvent) {
        super.onClose(event)
        views.remove(event.player.uniqueId)
    }

    private fun open(viewer: Player, mode: Mode) {
        views[viewer.uniqueId] = build(viewer, mode)
        GUIManager.open(viewer, ID)
    }

    private fun build(viewer: Player, mode: Mode): View {
        val others = Bukkit.getOnlinePlayers().filter { it.uniqueId != viewer.uniqueId && viewer.canSee(it) }

        val entries: List<Pair<Player, ItemStack>> = when (mode) {
            Mode.TRADE -> {
                val waiting = TradeManager.requestersFor(viewer.uniqueId).toSet()
                val (asking, rest) = others.partition { it.uniqueId in waiting }
                val nearby = rest.filter { TradeManager.inRange(viewer, it) }
                    .sortedBy { it.location.distanceSquared(viewer.location) }
                asking.map { it to request(viewer, it) } + nearby.map { it to nearby(viewer, it) }
            }

            Mode.PAY -> others.sortedBy { it.name.lowercase() }.map { it to payee(viewer, it) }
        }

        if (entries.isEmpty()) return View(mode, listOf(null), listOf(nobody(viewer, mode)))
        return View(mode, entries.map { it.first.uniqueId }, entries.map { it.second })
    }

    // ── Items ───────────────────────────────────────────────────────────

    private fun request(viewer: Player, other: Player): ItemStack =
        MenuRender.head(other, nameOf(viewer, other), listOf(viewer.tr("gui.menu-players.request")), glow = true)

    private fun nearby(viewer: Player, other: Player): ItemStack {
        val blocks = other.location.distance(viewer.location).roundToInt()
        return MenuRender.head(
            other,
            nameOf(viewer, other),
            listOf(viewer.tr("gui.menu-players.distance", "amount" to blocks), "", viewer.tr("gui.menu-players.ask")),
        )
    }

    private fun payee(viewer: Player, other: Player): ItemStack =
        MenuRender.head(other, nameOf(viewer, other), listOf(viewer.tr("gui.menu-players.pay")))

    private fun nobody(viewer: Player, mode: Mode): ItemStack = itemStack(Material.BARRIER) {
        when (mode) {
            Mode.TRADE -> {
                name(viewer.tr("gui.menu-players.nobody-near"))
                val distance = TradeSettings.snapshot.distance.roundToInt()
                meta { lore(LoreUtil.wrapLore(viewer.tr("gui.menu-players.nobody-near-lore", "amount" to distance))) }
            }

            Mode.PAY -> name(viewer.tr("gui.menu-players.nobody-online"))
        }
    }

    private fun nameOf(viewer: Player, other: Player): String =
        viewer.tr("gui.menu-players.name", "name" to ComponentUtil.escape(other.name))

    private fun payPrompt(player: Player, name: String): String {
        val balance = if (EconomyUtil.isAvailable) EconomyUtil.format(EconomyUtil.balance(player)) else "-"
        return player.tr(
            "gui.menu-players.pay-prompt",
            "name" to ComponentUtil.escape(name),
            "balance" to ComponentUtil.escape(balance),
        )
    }

    companion object {
        const val ID = "menu-players"

        /** Opens the picker for [player] through the registered instance. */
        fun show(player: Player, mode: Mode): Boolean {
            val gui = GUIManager.getGUI(ID) as? PlayerPickerGUI ?: return false
            gui.open(player, mode)
            return true
        }
    }
}
