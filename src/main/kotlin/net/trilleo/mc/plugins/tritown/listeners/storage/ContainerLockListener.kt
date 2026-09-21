package net.trilleo.mc.plugins.tritown.listeners.storage

import net.trilleo.mc.plugins.tritown.commands.storage.StorageCommand
import net.trilleo.mc.plugins.tritown.config.StorageSettings
import net.trilleo.mc.plugins.tritown.storage.StorageManager
import net.trilleo.mc.plugins.tritown.utils.ComponentUtil
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Material
import org.bukkit.Tag
import org.bukkit.block.Barrel
import org.bukkit.block.Chest
import org.bukkit.block.DoubleChest
import org.bukkit.block.ShulkerBox
import org.bukkit.entity.ChestBoat
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.entity.minecart.StorageMinecart
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityPlaceEvent
import org.bukkit.event.inventory.*
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.Inventory
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Retires the vanilla containers the storage replaces.
 *
 * They can still be placed, as decoration, with a warning that they will not
 * hold anything. Every one of them is withdraw-only: a player can empty a chest
 * into their storage, but not fill one, and neither can a hopper. One with
 * nothing left in it does not open at all, so a decorative chest never shows
 * an empty inventory that looks as if it were waiting to be filled.
 *
 * A container is recognised by what holds its inventory, never by the
 * inventory's type. TriTown's own menus and other plugins' are chest-shaped too,
 * and they have no block or entity behind them.
 */
class ContainerLockListener : Listener {

    /** When each player was last told a container is withdraw-only, so a busy click is not a busy chat. */
    private val lastHint = ConcurrentHashMap<UUID, Long>()

    // ── Placing ─────────────────────────────────────────────────────────

    /** Placing is allowed, as decoration, but the player is told what they are putting down. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onPlace(event: BlockPlaceEvent) {
        if (!isLockedMaterial(event.blockPlaced.type) || exempt(event.player)) return
        warnPlacing(event.player)
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    fun onEntityPlace(event: EntityPlaceEvent) {
        val player = event.player ?: return
        if (!isLockedEntity(event.entity) || exempt(player)) return
        warnPlacing(player)
    }

    // ── Opening ─────────────────────────────────────────────────────────

    /** An empty container is decoration and does not open; one with items in it opens to be emptied. */
    @EventHandler(ignoreCancelled = true)
    fun onOpen(event: InventoryOpenEvent) {
        val player = event.player as? Player ?: return
        val inventory = event.inventory
        if (!isLocked(inventory) || exempt(player)) return
        if (!inventory.isEmpty) return

        event.isCancelled = true
        hint(player, "storage.lock.empty")
    }

    // ── Filling ─────────────────────────────────────────────────────────

    @EventHandler(ignoreCancelled = true)
    fun onClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val top = event.view.topInventory
        if (!isLocked(top) || exempt(player)) return

        val inTop = event.rawSlot in 0 until top.size
        val fills = when (event.action) {
            InventoryAction.MOVE_TO_OTHER_INVENTORY -> !inTop
            InventoryAction.HOTBAR_SWAP, InventoryAction.HOTBAR_MOVE_AND_READD -> inTop && swapsSomethingIn(player, event)
            in TAKING -> false
            else -> inTop
        }
        if (!fills) return

        event.isCancelled = true
        hint(player, "storage.lock.withdraw-only")
    }

    @EventHandler(ignoreCancelled = true)
    fun onDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        val top = event.view.topInventory
        if (!isLocked(top) || exempt(player)) return
        if (event.rawSlots.none { it < top.size }) return

        event.isCancelled = true
        hint(player, "storage.lock.withdraw-only")
    }

    /** Hoppers and droppers may still pull from a container; they may not push into one. */
    @EventHandler(ignoreCancelled = true)
    fun onHopper(event: InventoryMoveItemEvent) {
        if (isLocked(event.destination)) event.isCancelled = true
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        lastHint.remove(event.player.uniqueId)
    }

    // ── What is locked ──────────────────────────────────────────────────

    private fun lockActive(): Boolean = StorageManager.isAvailable

    private fun lock(): StorageSettings.ContainerLock? =
        if (lockActive()) StorageSettings.snapshot.lock else null

    private fun exempt(player: Player): Boolean = player.hasPermission(StorageCommand.BYPASS_PERMISSION)

    private fun isLockedMaterial(type: Material): Boolean {
        val lock = lock() ?: return false
        return when {
            Tag.SHULKER_BOXES.isTagged(type) -> lock.shulkerBoxes
            type == Material.ENDER_CHEST -> lock.enderChest
            type == Material.CHEST || type == Material.TRAPPED_CHEST || type == Material.BARREL -> lock.chests
            else -> false
        }
    }

    private fun isLockedEntity(entity: Entity): Boolean {
        val lock = lock() ?: return false
        return lock.chests && (entity is StorageMinecart || entity is ChestBoat)
    }

    /**
     * Whether [inventory] belongs to one of the retired containers.
     *
     * A hopper minecart is left alone: it is part of a machine, and locking it
     * would stop it pulling items out of the very chests players are meant to empty.
     */
    private fun isLocked(inventory: Inventory): Boolean {
        val lock = lock() ?: return false
        if (inventory.type == InventoryType.ENDER_CHEST) return lock.enderChest

        return when (inventory.getHolder(false)) {
            is ShulkerBox -> lock.shulkerBoxes
            is Chest, is DoubleChest, is Barrel, is StorageMinecart, is ChestBoat -> lock.chests
            else -> false
        }
    }

    /** A number key or the off-hand key swaps an item in only when the key's slot holds one. */
    private fun swapsSomethingIn(player: Player, event: InventoryClickEvent): Boolean {
        val incoming = if (event.click == ClickType.SWAP_OFFHAND) {
            player.inventory.itemInOffHand
        } else {
            player.inventory.getItem(event.hotbarButton)
        }
        return incoming != null && !incoming.type.isAir
    }

    // ── Telling the player ──────────────────────────────────────────────

    private fun warnPlacing(player: Player) {
        player.sendPrefixed(player.tr("storage.lock.decoration"))
    }

    private fun hint(player: Player, key: String) {
        val now = System.currentTimeMillis()
        val last = lastHint[player.uniqueId]
        if (last != null && now - last < HINT_COOLDOWN_MS) return
        lastHint[player.uniqueId] = now
        player.sendActionBar(ComponentUtil.parse(player.tr(key)))
    }

    private companion object {
        const val HINT_COOLDOWN_MS = 2_000L

        /** What only ever moves items out of the container. */
        val TAKING = setOf(
            InventoryAction.NOTHING,
            InventoryAction.PICKUP_ALL,
            InventoryAction.PICKUP_SOME,
            InventoryAction.PICKUP_HALF,
            InventoryAction.PICKUP_ONE,
            InventoryAction.DROP_ALL_SLOT,
            InventoryAction.DROP_ONE_SLOT,
            InventoryAction.DROP_ALL_CURSOR,
            InventoryAction.DROP_ONE_CURSOR,
            InventoryAction.COLLECT_TO_CURSOR,
            InventoryAction.CLONE_STACK,
            InventoryAction.PICKUP_FROM_BUNDLE,
            InventoryAction.PICKUP_ALL_INTO_BUNDLE,
            InventoryAction.PICKUP_SOME_INTO_BUNDLE,
        )
    }
}
