package net.trilleo.mc.plugins.tritown.listeners.trade

import net.trilleo.mc.plugins.tritown.trades.TradeManager
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.EquipmentSlot

/**
 * Asking to trade by shift-right-clicking the other player, and cleaning up
 * after someone who disconnects mid-trade.
 *
 * The quit half matters more than it looks: a trade is holding items that
 * belong to the player leaving, and a `PlayerQuitEvent` handler still runs
 * before the server writes their inventory to disk, so handing the escrow back
 * here is what keeps a disconnect from costing them anything.
 */
class TradeListener : Listener {

    /**
     * Opens a request when one player shift-right-clicks another.
     *
     * Right-clicking an entity sends two packets, and the second of them is
     * delivered as [PlayerInteractAtEntityEvent] — which is a
     * [PlayerInteractEntityEvent] as well, so without dropping it here one
     * click would ask twice. The off hand is dropped for the same reason.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onInteract(event: PlayerInteractEntityEvent) {
        if (event is PlayerInteractAtEntityEvent) return
        if (event.hand != EquipmentSlot.HAND) return
        if (!event.player.isSneaking) return

        val target = event.rightClicked as? Player ?: return
        if (isNpc(target)) return
        if (!TradeManager.isEnabled) return

        val player = event.player
        TradeManager.refusal(player, target)?.let { key ->
            player.sendPrefixed(player.tr("common.error", "message" to player.tr(key, "name" to TradeManager.name(target))))
            return
        }

        TradeManager.request(player, target)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        TradeManager.handleQuit(event.player)
    }

    /**
     * Whether [target] is an NPC wearing a player's shape rather than somebody
     * who could trade.
     *
     * A shop keeper is a player entity as far as this event is concerned, so
     * the usual `NPC` marker is checked and, for the plugins that do not set
     * one, whether the server's own player list actually has them: a fake
     * player is never in it.
     */
    private fun isNpc(target: Player): Boolean =
        target.hasMetadata("NPC") || Bukkit.getPlayer(target.uniqueId) !== target
}
