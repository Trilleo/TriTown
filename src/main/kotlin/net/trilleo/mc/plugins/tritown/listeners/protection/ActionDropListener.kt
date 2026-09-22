package net.trilleo.mc.plugins.tritown.listeners.protection

import net.trilleo.mc.plugins.tritown.protection.Claims
import net.trilleo.mc.plugins.tritown.protection.ItemOwnership
import net.trilleo.mc.plugins.tritown.protection.Protection
import org.bukkit.block.TileState
import org.bukkit.entity.Item
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Event
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockDispenseLootEvent
import org.bukkit.event.block.BlockDropItemEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityDropItemEvent
import org.bukkit.event.player.PlayerFishEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerShearEntityEvent

/**
 * Credits a drop to the player whose action produced it: mining, farming,
 * shearing, fishing, killing a mob, opening a vault.
 *
 * Where the drops already exist as entities, they are bound on the spot.
 * Everywhere else a window is opened at the spot and the spawn claims it (see
 * [ItemOwnership.expect]). Anything a claimed holder leaves behind goes to its
 * claimant rather than to whoever broke or killed it.
 */
class ActionDropListener : Listener {

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    fun onBlockDrop(event: BlockDropItemEvent) {
        val settings = Protection.settings(event.block.world) ?: return
        val claimant = if (settings.containers) {
            (event.blockState as? TileState)?.let { Claims.recorded(it.persistentDataContainer) }
        } else null
        val owner = claimant ?: event.player.uniqueId.takeIf { settings.actions } ?: return

        event.items.forEach { ItemOwnership.bind(it, owner) }
        // A container's contents are spilled by the block itself, and may not come through this event.
        ItemOwnership.expect(event.block, owner)
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    fun onDeath(event: EntityDeathEvent) {
        val entity = event.entity
        if (entity is Player) return
        val settings = Protection.settings(entity.world) ?: return

        val claimant = if (settings.entities) Claims.recorded(entity.persistentDataContainer) else null
        val owner = claimant ?: entity.killer?.uniqueId?.takeIf { settings.mobLoot } ?: return
        ItemOwnership.expect(entity.location, owner)
    }

    /** What an entity drops by itself — a frame's item, an allay's — is its claimant's. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    fun onEntityDrop(event: EntityDropItemEvent) {
        val settings = Protection.settings(event.entity.world) ?: return
        if (!settings.entities || event.itemDrop.owner != null) return
        Claims.recorded(event.entity.persistentDataContainer)?.let { ItemOwnership.bind(event.itemDrop, it) }
    }

    /**
     * Everything a right-click can knock loose: berries, glow berries, honeycomb,
     * pumpkin seeds, bone meal from a composter, brushing.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onInteractBlock(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK || event.useInteractedBlock() == Event.Result.DENY) return
        val block = event.clickedBlock ?: return
        if (Protection.settings(block.world)?.actions != true) return
        ItemOwnership.expect(block, event.player.uniqueId)
    }

    /** A scute brushed off an armadillo, a bowl of stew milked from a mooshroom. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onInteractEntity(event: PlayerInteractEntityEvent) {
        val entity = event.rightClicked
        if (Protection.settings(entity.world)?.actions != true) return
        ItemOwnership.expect(entity.location, event.player.uniqueId)
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onShear(event: PlayerShearEntityEvent) {
        val entity = event.entity
        if (Protection.settings(entity.world)?.actions != true) return
        ItemOwnership.expect(entity.location, event.player.uniqueId)
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    fun onFish(event: PlayerFishEvent) {
        if (event.state != PlayerFishEvent.State.CAUGHT_FISH) return
        if (Protection.settings(event.player.world)?.actions != true) return
        (event.caught as? Item)?.let { ItemOwnership.bind(it, event.player.uniqueId) }
    }

    /** A vault's or trial spawner's reward. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onLoot(event: BlockDispenseLootEvent) {
        val player = event.player ?: return
        if (Protection.settings(event.block.world)?.actions != true) return
        ItemOwnership.expect(event.block, player.uniqueId)
    }
}
