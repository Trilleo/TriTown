package net.trilleo.mc.plugins.tritown.listeners.protection

import net.trilleo.mc.plugins.tritown.protection.DropWindows
import net.trilleo.mc.plugins.tritown.protection.ItemOwnership
import net.trilleo.mc.plugins.tritown.protection.Protection
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.Piglin
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.*
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerPickupArrowEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.persistence.PersistentDataType
import java.util.*

/**
 * Items on the ground: who they belong to, and who may pick them up.
 *
 * What a player drops is theirs, and so is whatever an action of theirs
 * produced (see [ActionDropListener]). Death drops, and anything the world drops
 * by itself, are public. Only the owner can pick up an owned item. No mob,
 * allay or fox can either, except that a piglin still takes gold to barter with
 * and pays the thrower back.
 */
class DropProtectionListener : Listener {

    // ── Giving a drop its owner ─────────────────────────────────────────

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    fun onDrop(event: PlayerDropItemEvent) {
        val settings = Protection.settings(event.player.world) ?: return
        if (settings.drops) ItemOwnership.bind(event.itemDrop, event.player.uniqueId)
    }

    /** A death's drops stay public, even when the killer's window reaches them. */
    @EventHandler(priority = EventPriority.LOWEST)
    fun onDeath(event: PlayerDeathEvent) {
        if (Protection.settings(event.player.world) == null) return
        ItemOwnership.suppress(event.player.location)
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    fun onSpawn(event: ItemSpawnEvent) {
        val item = event.entity
        if (item.owner != null) return
        val settings = Protection.settings(item.world) ?: return

        when (val match = ItemOwnership.match(item.location)) {
            DropWindows.Match.Public -> return
            is DropWindows.Match.Owned -> return ItemOwnership.bind(item, match.owner)
            DropWindows.Match.None -> Unit
        }

        // Whatever a player threw that no event above saw, such as what a closed crafting grid spills.
        val thrower = item.thrower ?: return
        if (settings.drops && Bukkit.getPlayer(thrower) != null) ItemOwnership.bind(item, thrower)
    }

    // ── Picking up ──────────────────────────────────────────────────────

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    fun onPickup(event: EntityPickupItemEvent) {
        val owner = event.item.owner ?: return
        val entity = event.entity

        // The game already refuses anyone but the owner; this covers a plugin that lets it through.
        if (entity is Player) {
            if (entity.uniqueId != owner) event.isCancelled = true
            return
        }

        if (entity is Piglin && event.item.itemStack.type in entity.barterList) {
            entity.persistentDataContainer.set(BARTER_OWNER, PersistentDataType.STRING, owner.toString())
            return
        }
        event.isCancelled = true
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    fun onBarter(event: PiglinBarterEvent) {
        val piglin = event.entity
        val owner = barterOwner(piglin) ?: return
        piglin.persistentDataContainer.remove(BARTER_OWNER)
        ItemOwnership.expect(piglin.location, owner)
    }

    /** Gold a piglin was still holding when it died goes back to whoever threw it, not to the killer. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    fun onPiglinDeath(event: EntityDeathEvent) {
        val piglin = event.entity as? Piglin ?: return
        val owner = barterOwner(piglin) ?: return
        if (piglin.killer?.uniqueId == owner) return

        val held = piglin.equipment.itemInOffHand
        if (held.isEmpty) return
        val index = event.drops.indexOfFirst { it.isSimilar(held) }
        if (index < 0) return
        val stack = event.drops.removeAt(index)
        piglin.world.dropItemNaturally(piglin.location, stack) { ItemOwnership.bind(it, owner) }
    }

    @EventHandler(ignoreCancelled = true)
    fun onMerge(event: ItemMergeEvent) {
        if (event.entity.owner != event.target.owner) event.isCancelled = true
    }

    /** An arrow or trident only goes back to whoever fired it. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    fun onArrowPickup(event: PlayerPickupArrowEvent) {
        val settings = Protection.settings(event.player.world) ?: return
        if (!settings.projectiles) return

        val shooter = event.arrow.ownerUniqueId ?: return
        if (shooter == event.player.uniqueId) return
        if (Bukkit.getOfflinePlayer(shooter).hasPlayedBefore()) event.isCancelled = true
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        Protection.forget(event.player)
    }

    private fun barterOwner(piglin: Piglin): UUID? =
        piglin.persistentDataContainer.get(BARTER_OWNER, PersistentDataType.STRING)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    private companion object {
        val BARTER_OWNER = NamespacedKey("tritown", "barter_owner")
    }
}
