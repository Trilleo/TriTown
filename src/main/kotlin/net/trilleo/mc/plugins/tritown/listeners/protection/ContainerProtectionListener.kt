package net.trilleo.mc.plugins.tritown.listeners.protection

import io.papermc.paper.event.entity.ItemTransportingEntityValidateTargetEvent
import io.papermc.paper.event.player.PlayerInsertLecternBookEvent
import net.trilleo.mc.plugins.tritown.protection.ClaimHolder
import net.trilleo.mc.plugins.tritown.protection.Claims
import net.trilleo.mc.plugins.tritown.protection.ItemOwnership
import net.trilleo.mc.plugins.tritown.protection.Protection
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.World
import org.bukkit.block.*
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.*
import org.bukkit.event.entity.EntityChangeBlockEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryMoveItemEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.inventory.InventoryPickupItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerTakeLecternBookEvent
import org.bukkit.event.vehicle.VehicleDestroyEvent

/**
 * Containers belong to whoever fills them, for as long as they hold anything.
 *
 * Only the owner can open, feed, empty or break a claimed container. One
 * player at a time can have a free one open, and opening it claims it, so a
 * hopper cannot slip somebody else's items in while a viewer is taking things
 * out. Hoppers carry the owner along with the items: X's items can fill a free
 * container, which becomes X's, but never one that Y holds.
 *
 * Interaction blocks — decorated pots, chiseled bookshelves, shelves,
 * jukeboxes and campfires — follow the same rule through right-clicks. A lectern
 * can still be read by anyone. Only taking its book is the owner's.
 */
class ContainerProtectionListener : Listener {

    // ── Opening ─────────────────────────────────────────────────────────

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    fun onOpen(event: InventoryOpenEvent) {
        val player = event.player as? Player ?: return
        if (Protection.settings(player.world)?.containers != true) return
        if (event.inventory.getHolder(false) is Lectern || Protection.bypasses(player)) return
        val holder = Claims.of(event.inventory) ?: return

        // Asked before the owner, because reading the owner of an empty holder nobody else is viewing releases it.
        val inUse = event.inventory.viewers.any { it != player && it is Player && !Protection.bypasses(it) }
        val owner = if (inUse) Claims.recorded(holder) else Claims.ownerOf(holder)
        if (Protection.refuses(player, owner)) {
            event.isCancelled = true
            Protection.hintOwned(player, owner!!)
        } else if (inUse) {
            event.isCancelled = true
            Protection.hint(player, player.tr("protection.hint.in-use"))
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onOpened(event: InventoryOpenEvent) {
        val player = event.player as? Player ?: return
        if (Protection.settings(player.world)?.containers != true || Protection.bypasses(player)) return
        if (event.inventory.getHolder(false) is Lectern) return
        val holder = Claims.of(event.inventory) ?: return
        if (Claims.ownerOf(holder) == null) Claims.claim(holder, player.uniqueId)
    }

    @EventHandler
    fun onClose(event: InventoryCloseEvent) {
        if (!event.inventory.isEmpty) return
        Claims.of(event.inventory)?.let(Claims::release)
    }

    // ── Hoppers, droppers and hopper minecarts ──────────────────────────

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    fun onMove(event: InventoryMoveItemEvent) {
        // Cheapest first: most moves are between holders nobody has claimed.
        val source = Claims.of(event.source) ?: return
        val from = Claims.ownerOf(source) ?: return
        val world = source.location.world ?: return
        if (Protection.settings(world)?.containers != true) return

        val destination = Claims.of(event.destination) ?: return
        if (!accept(destination, from)) event.isCancelled = true
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    fun onHopperPickup(event: InventoryPickupItemEvent) {
        val from = event.item.owner ?: return
        if (Protection.settings(event.item.world)?.containers != true) return

        val destination = Claims.of(event.inventory)
        if (destination == null || !accept(destination, from)) event.isCancelled = true
    }

    /** Whether [owner]'s items may go into [destination], claiming it for them if nobody holds it. */
    private fun accept(destination: ClaimHolder, owner: java.util.UUID): Boolean {
        val holder = Claims.ownerOf(destination)
        if (holder == null) Claims.claim(destination, owner)
        return holder == null || holder == owner
    }

    /** A copper golem never carries items out of, or into, something a player holds. */
    @EventHandler
    fun onGolemTarget(event: ItemTransportingEntityValidateTargetEvent) {
        if (!event.isAllowed || Protection.settings(event.block.world)?.containers != true) return
        val holder = Claims.of(event.block) ?: return
        if (Claims.ownerOf(holder) != null) event.isAllowed = false
    }

    // ── Breaking, placing and blowing up ────────────────────────────────

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    fun onBreak(event: BlockBreakEvent) {
        val player = event.player
        if (Protection.settings(player.world)?.containers != true) return
        val holder = Claims.of(event.block) ?: return
        val owner = Claims.ownerOf(holder)
        if (!Protection.refuses(player, owner)) return
        event.isCancelled = true
        Protection.hintOwned(player, owner!!)
    }

    /** A block placed with items already inside it, such as a filled shulker box, is its placer's. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onPlace(event: BlockPlaceEvent) {
        if (Protection.settings(event.player.world)?.containers != true) return
        val holder = Claims.of(event.blockPlaced) ?: return
        if (holder.isVacant()) Claims.release(holder) else Claims.claim(holder, event.player.uniqueId)
    }

    @EventHandler(ignoreCancelled = true)
    fun onEntityExplode(event: EntityExplodeEvent) {
        if (Protection.settings(event.entity.world)?.explosionGuard != true) return
        event.blockList().removeIf(::isHeld)
    }

    @EventHandler(ignoreCancelled = true)
    fun onBlockExplode(event: BlockExplodeEvent) {
        if (Protection.settings(event.block.world)?.explosionGuard != true) return
        event.blockList().removeIf(::isHeld)
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    fun onPistonExtend(event: BlockPistonExtendEvent) {
        if (pistonBreaksHolder(event.block.world, event.blocks, event.block.getRelative(event.direction))) {
            event.isCancelled = true
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    fun onPistonRetract(event: BlockPistonRetractEvent) {
        if (pistonBreaksHolder(event.block.world, event.blocks, null)) event.isCancelled = true
    }

    /**
     * Whether a piston would move or break a claimed holder.
     *
     * The game never moves a block entity, but it breaks one whose move reaction
     * is `BREAK`, such as a decorated pot, and spills its contents as public drops.
     * Those blocks are not always in [moved], so the blocks around everything
     * that moves, and the one in front of the head, are checked too. Only the
     * breakable ones among them count, so a piston door beside a claimed furnace
     * still works.
     */
    private fun pistonBreaksHolder(world: World, moved: List<Block>, head: Block?): Boolean {
        if (Protection.settings(world)?.containers != true) return false
        if (moved.any(::isHeld)) return true

        val around = moved.flatMap { block -> FACES.map(block::getRelative) } + listOfNotNull(head)
        return around.distinct().any { it.pistonMoveReaction == PistonMoveReaction.BREAK && isHeld(it) }
    }

    /** An arrow through a decorated pot, a wither chewing through a furnace. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    fun onEntityChangeBlock(event: EntityChangeBlockEvent) {
        if (event.entity is Player) return
        val settings = Protection.settings(event.block.world) ?: return
        if (!settings.containers) return
        val holder = Claims.of(event.block) ?: return
        val owner = Claims.ownerOf(holder) ?: return

        val player = Protection.responsible(event.entity)
        if (if (player != null) Protection.refuses(player, owner) else settings.explosionGuard) event.isCancelled = true
    }

    private fun isHeld(block: Block): Boolean = Claims.of(block)?.let(Claims::ownerOf) != null

    // ── Minecarts and boats ─────────────────────────────────────────────

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    fun onVehicleDestroy(event: VehicleDestroyEvent) {
        val vehicle = event.vehicle
        if (Protection.settings(vehicle.world)?.containers != true) return
        val holder = Claims.of(vehicle) ?: return
        val owner = Claims.ownerOf(holder) ?: return

        val player = Protection.responsible(event.attacker)
        if (player != null && Protection.refuses(player, owner)) {
            event.isCancelled = true
            Protection.hintOwned(player, owner)
            return
        }
        ItemOwnership.expect(vehicle.location, owner)
    }

    // ── What a claimed block produces ───────────────────────────────────

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onDispense(event: BlockDispenseEvent) = expectFromClaimant(event.block)

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onCraft(event: CrafterCraftEvent) = expectFromClaimant(event.block)

    /** A campfire drops what it has finished cooking. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onCook(event: BlockCookEvent) {
        if (event.block.getState(false) is Campfire) expectFromClaimant(event.block)
    }

    private fun expectFromClaimant(block: Block) {
        if (Protection.settings(block.world)?.containers != true) return
        val holder = Claims.of(block) ?: return
        Claims.ownerOf(holder)?.let { ItemOwnership.expect(block, it) }
    }

    // ── Interaction blocks ──────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOW)
    fun onInteract(event: PlayerInteractEvent) {
        val block = interactionBlock(event) ?: return
        val holder = Claims.of(block) ?: return
        val owner = Claims.ownerOf(holder)
        if (!Protection.refuses(event.player, owner)) return

        event.setUseInteractedBlock(Event.Result.DENY)
        event.setUseItemInHand(Event.Result.DENY)
        Protection.hintOwned(event.player, owner!!)
    }

    /** Claimed on the click, before the item goes in; if nothing does, the next reader lets the claim go. */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onInteracted(event: PlayerInteractEvent) {
        if (event.useInteractedBlock() == Event.Result.DENY || Protection.bypasses(event.player)) return
        val block = interactionBlock(event) ?: return
        val holder = Claims.of(block) ?: return
        if (Claims.ownerOf(holder) == null) Claims.claim(holder, event.player.uniqueId)
    }

    private fun interactionBlock(event: PlayerInteractEvent): Block? {
        if (event.action != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return null
        val block = event.clickedBlock ?: return null
        if (Protection.settings(block.world)?.containers != true) return null
        return block.takeIf { block.getState(false).let { it is DecoratedPot || it is ChiseledBookshelf || it is Shelf || it is Jukebox || it is Campfire } }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onLecternInsert(event: PlayerInsertLecternBookEvent) {
        if (Protection.settings(event.block.world)?.containers != true || Protection.bypasses(event.player)) return
        Claims.of(event.block)?.let { Claims.claim(it, event.player.uniqueId) }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    fun onLecternTake(event: PlayerTakeLecternBookEvent) {
        val player = event.player
        if (Protection.settings(player.world)?.containers != true) return
        val holder = Claims.of(event.lectern.block) ?: return
        val owner = Claims.ownerOf(holder)
        if (!Protection.refuses(player, owner)) return
        event.isCancelled = true
        Protection.hintOwned(player, owner!!)
    }

    private companion object {
        val FACES = listOf(BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)
    }
}
