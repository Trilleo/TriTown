package net.trilleo.mc.plugins.tritown.listeners.town

import com.palmergames.bukkit.towny.event.PreNewTownEvent
import com.palmergames.bukkit.towny.event.TownAddResidentEvent
import net.trilleo.mc.plugins.tritown.towns.FoundingCredit
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin

/** Grants, spends and forfeits the [FoundingCredit] as players join the server and towns. */
class FoundingCreditListener(private val plugin: JavaPlugin) : Listener {

    /** Above NORMAL, so the player's data has been loaded before it is read. */
    @EventHandler(priority = EventPriority.HIGH)
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        val key = when (FoundingCredit.review(player)) {
            FoundingCredit.Change.GRANTED -> "founding-credit.granted"
            FoundingCredit.Change.FORFEITED -> "founding-credit.forfeited"
            else -> return
        }
        // Later than the join itself, which is otherwise lost among the server's welcome messages.
        plugin.server.scheduler.runTaskLater(plugin, Runnable { tell(player, key) }, MESSAGE_DELAY_TICKS)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) = FoundingCredit.forget(event.player)

    /** Last, so the credit covers whatever is left after other plugins have changed the price. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPreNewTown(event: PreNewTownEvent) {
        val player = event.player
        val covered = FoundingCredit.cover(player, event.townName, event.price)
        if (covered <= 0.0) return

        event.price -= covered
        player.sendPrefixed(player.tr("founding-credit.applied", "amount" to FoundingCredit.display(covered)))
    }

    /** A resident added while offline is caught by [onJoin] instead. */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoinTown(event: TownAddResidentEvent) {
        val player = event.resident.player ?: return
        if (FoundingCredit.settle(player, event.town) == FoundingCredit.Change.FORFEITED) {
            tell(player, "founding-credit.forfeited")
        }
    }

    private fun tell(player: Player, key: String) {
        if (!player.isOnline) return
        player.sendPrefixed(player.tr(key, "amount" to FoundingCredit.display()))
    }

    private companion object {
        const val MESSAGE_DELAY_TICKS = 40L
    }
}
