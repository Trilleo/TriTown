package net.trilleo.mc.plugins.tritown.listeners.protection

import io.papermc.paper.event.player.PlayerItemFrameChangeEvent
import net.trilleo.mc.plugins.tritown.Main
import net.trilleo.mc.plugins.tritown.protection.Claims
import net.trilleo.mc.plugins.tritown.protection.ItemOwnership
import net.trilleo.mc.plugins.tritown.protection.Protection
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.entity.ItemFrame
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockShearEntityEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.hanging.HangingBreakByEntityEvent
import org.bukkit.event.player.PlayerArmorStandManipulateEvent
import org.bukkit.event.player.PlayerInteractEntityEvent

/**
 * Entities that hold a player's items: item frames, armor stands, allays, and
 * mobs a player has saddled, armoured or given a chest.
 *
 * Each belongs to whoever put the item there, for as long as it holds one.
 * Nobody else can take the item, shear it off, knock it loose or break what
 * holds it. What the entity drops when it is destroyed anyway goes to its
 * owner (see [ActionDropListener]).
 */
class EntityProtectionListener : Listener {

    // ── Item frames ─────────────────────────────────────────────────────

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    fun onFrameChange(event: PlayerItemFrameChangeEvent) {
        if (event.action == PlayerItemFrameChangeEvent.ItemFrameChangeAction.PLACE) return
        refuse(event.player, event.itemFrame) { event.isCancelled = true }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onFramePlaced(event: PlayerItemFrameChangeEvent) {
        if (event.action != PlayerItemFrameChangeEvent.ItemFrameChangeAction.PLACE) return
        claimFor(event.player, event.itemFrame)
    }

    /** A frame's item knocked loose by a hit, or a stand struck down. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    fun onDamage(event: EntityDamageByEntityEvent) {
        val entity = event.entity
        if (entity !is ItemFrame && entity !is ArmorStand) return
        val player = Protection.responsible(event.damager) ?: return
        refuse(player, entity) { event.isCancelled = true }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    fun onHangingBreak(event: HangingBreakByEntityEvent) {
        val player = Protection.responsible(event.remover) ?: return
        refuse(player, event.entity) { event.isCancelled = true }
    }

    /** Whatever the frame drops as its breaker takes it down is theirs, when nobody claimed it. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onHangingBroken(event: HangingBreakByEntityEvent) {
        val player = Protection.responsible(event.remover) ?: return
        if (Protection.settings(player.world)?.actions != true) return
        ItemOwnership.expect(event.entity.location, player.uniqueId)
    }

    // ── Armor stands ────────────────────────────────────────────────────

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    fun onManipulate(event: PlayerArmorStandManipulateEvent) {
        refuse(event.player, event.rightClicked) { event.isCancelled = true }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onManipulated(event: PlayerArmorStandManipulateEvent) {
        claimNextTick(event.player, event.rightClicked)
    }

    // ── Allays and mounts ───────────────────────────────────────────────

    /** Riding, feeding, opening or shearing a mob someone else has equipped. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    fun onInteract(event: PlayerInteractEntityEvent) {
        val entity = event.rightClicked
        if (entity is ItemFrame || entity is ArmorStand || entity is Player) return
        refuse(event.player, entity) { event.isCancelled = true }
    }

    /** The item goes on after the event, so whether anything did is read a tick later. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onInteracted(event: PlayerInteractEntityEvent) {
        val entity = event.rightClicked
        if (entity is ItemFrame || entity is ArmorStand || entity is Player) return
        claimNextTick(event.player, entity)
    }

    /** A dispenser's shears only take a saddle off a mob whose owner also holds the dispenser. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    fun onDispenserShear(event: BlockShearEntityEvent) {
        if (Protection.settings(event.entity.world)?.entities != true) return
        val holder = Claims.of(event.entity) ?: return
        val owner = Claims.ownerOf(holder) ?: return
        val dispenser = Claims.of(event.block)?.let(Claims::ownerOf)
        if (dispenser != owner) event.isCancelled = true
    }

    // ── Shared ──────────────────────────────────────────────────────────

    private inline fun refuse(player: Player, entity: Entity, cancel: () -> Unit) {
        if (Protection.settings(entity.world)?.entities != true) return
        val holder = Claims.of(entity) ?: return
        val owner = Claims.ownerOf(holder)
        if (!Protection.refuses(player, owner)) return
        cancel()
        Protection.hintOwned(player, owner!!)
    }

    private fun claimFor(player: Player, entity: Entity) {
        if (Protection.settings(entity.world)?.entities != true || Protection.bypasses(player)) return
        val holder = Claims.of(entity) ?: return
        if (Claims.ownerOf(holder) == null) Claims.claim(holder, player.uniqueId)
    }

    private fun claimNextTick(player: Player, entity: Entity) {
        if (Protection.settings(entity.world)?.entities != true || Protection.bypasses(player)) return
        if (Claims.of(entity) == null) return
        val owner = player.uniqueId
        entity.scheduler.run(Main.instance, {
            val holder = Claims.of(entity) ?: return@run
            if (Claims.ownerOf(holder) == null && !holder.isVacant()) Claims.claim(holder, owner)
        }, null)
    }
}
