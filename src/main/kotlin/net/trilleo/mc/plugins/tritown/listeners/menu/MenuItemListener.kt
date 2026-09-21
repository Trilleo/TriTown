package net.trilleo.mc.plugins.tritown.listeners.menu

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent
import net.trilleo.mc.plugins.tritown.Main
import net.trilleo.mc.plugins.tritown.guis.menu.MainMenuGUI
import net.trilleo.mc.plugins.tritown.menu.MenuItem
import net.trilleo.mc.plugins.tritown.registration.GUIManager
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.entity.ItemSpawnEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.*
import org.bukkit.event.player.*
import org.bukkit.inventory.EquipmentSlot

/**
 * Keeps the [MenuItem] in its slot and opens the menu from it.
 *
 * Every handler here refuses one way a copy could leave the last hotbar slot.
 * They run first, at the lowest priority, so no other plugin ever sees an
 * action on the item that is about to be undone — and `GUIManager` skips
 * clicks that are already cancelled, so a menu that takes items out of the
 * player's inventory, like the trade table, never receives it either.
 *
 * Refusing is only half of it. After anything that could have left the
 * inventory out of step with what the client shows — a cancelled drop, a
 * creative-mode click, a respawn — [MenuItem.reconcileLater] puts it right,
 * deleting any copy that is not in the slot.
 */
class MenuItemListener : Listener {

    // ── Giving it out and taking it back ────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoin(event: PlayerJoinEvent) = MenuItem.reconcile(event.player)

    /** Taken back before the inventory is saved, so no copy is ever written to disk. */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) = MenuItem.strip(event.player)

    @EventHandler(priority = EventPriority.MONITOR)
    fun onRespawn(event: PlayerPostRespawnEvent) = MenuItem.reconcile(event.player)

    /** A per-world or per-mode inventory plugin swaps the whole inventory, item and all. */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onWorldChange(event: PlayerChangedWorldEvent) = MenuItem.reconcileLater(event.player)

    @EventHandler(priority = EventPriority.MONITOR)
    fun onGameModeChange(event: PlayerGameModeChangeEvent) = MenuItem.reconcileLater(event.player)

    /** The client reports its language after joining, and the item is named in it. */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onLocaleChange(event: PlayerLocaleChangeEvent) = MenuItem.reconcileLater(event.player)

    /** Never dropped on death, and never kept either: [onRespawn] hands out a fresh one. */
    @EventHandler(priority = EventPriority.LOWEST)
    fun onDeath(event: PlayerDeathEvent) {
        event.drops.removeIf(MenuItem::isMenuItem)
        event.itemsToKeep.removeIf(MenuItem::isMenuItem)
    }

    // ── Using it ────────────────────────────────────────────────────────

    /**
     * Right-clicking with the item opens the menu, and nothing else the click
     * would do happens: it is never placed, eaten, thrown, equipped or put into
     * a block such as a lectern, a pot or a jukebox.
     *
     * Both hands are refused, so the off hand cannot place something while the
     * menu is opening.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    fun onInteract(event: PlayerInteractEvent) {
        val player = event.player
        if (!MenuItem.isMenuItem(player.inventory.itemInMainHand)) return
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return

        event.setUseItemInHand(Event.Result.DENY)
        event.setUseInteractedBlock(Event.Result.DENY)
        if (event.hand == EquipmentSlot.HAND) open(player)
    }

    /**
     * Keeps the item out of item frames, armour stands, allays and anything
     * else that takes what it is clicked with.
     *
     * Players are let through, so shift-right-clicking someone to trade still
     * works with the item in hand.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    fun onInteractEntity(event: PlayerInteractEntityEvent) {
        if (event.rightClicked is Player) return
        if (MenuItem.isMenuItem(event.player.inventory.getItem(event.hand))) event.isCancelled = true
    }

    /** Armour stands arrive as this event rather than [PlayerInteractEntityEvent]. */
    @EventHandler(priority = EventPriority.LOWEST)
    fun onInteractAtEntity(event: PlayerInteractAtEntityEvent) {
        if (event.rightClicked is Player) return
        if (MenuItem.isMenuItem(event.player.inventory.getItem(event.hand))) event.isCancelled = true
    }

    /** Only reachable with a block chosen as the material, and refused there too. */
    @EventHandler(priority = EventPriority.LOWEST)
    fun onPlace(event: BlockPlaceEvent) {
        if (MenuItem.isMenuItem(event.itemInHand)) event.isCancelled = true
    }

    // ── Keeping it in its slot ──────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOWEST)
    fun onDrop(event: PlayerDropItemEvent) {
        if (!MenuItem.isMenuItem(event.itemDrop.itemStack)) return
        event.isCancelled = true
        MenuItem.reconcileLater(event.player)
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onSwapHands(event: PlayerSwapHandItemsEvent) {
        if (MenuItem.isMenuItem(event.mainHandItem) || MenuItem.isMenuItem(event.offHandItem)) {
            event.isCancelled = true
        }
    }

    /**
     * Refuses any click that would pick the item up, put something on top of
     * it, or swap it anywhere.
     *
     * The slot is checked as well as the item while the item is enabled, because
     * a number key or the swap-hands key moves whatever is in a slot without
     * either stack being the one clicked. A plain click on the item in the player's own inventory
     * opens the menu instead.
     *
     * A creative client writes its own slots and is only told no afterwards,
     * so every creative click on the item is followed by a reconcile.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    fun onClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val inventory = player.inventory

        val guarded = MenuItem.isEnabled
        val onSlot = guarded && event.clickedInventory === inventory && event.slot == MenuItem.SLOT
        val clickedItem = MenuItem.isMenuItem(event.currentItem)
        val touches = onSlot || clickedItem ||
                MenuItem.isMenuItem(event.cursor) ||
                (guarded && event.click == ClickType.NUMBER_KEY && event.hotbarButton == MenuItem.SLOT) ||
                (event.click == ClickType.SWAP_OFFHAND && MenuItem.isMenuItem(inventory.itemInOffHand))
        if (!touches) return

        event.isCancelled = true
        if (player.gameMode == GameMode.CREATIVE) MenuItem.reconcileLater(player)

        val plainClick = event.click == ClickType.LEFT || event.click == ClickType.RIGHT
        val ownInventory = event.view.topInventory.type == InventoryType.CRAFTING
        if ((onSlot || clickedItem) && plainClick && ownInventory && event.cursor.isEmpty) {
            // A menu opened while the click is still being delivered leaves the client out of step.
            Bukkit.getScheduler().runTask(Main.instance, Runnable { open(player) })
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        val intoSlot = MenuItem.isEnabled && event.rawSlots.any {
            event.view.getInventory(it) === player.inventory && event.view.convertSlot(it) == MenuItem.SLOT
        }
        if (!intoSlot && !MenuItem.isMenuItem(event.oldCursor)) return

        event.isCancelled = true
        if (player.gameMode == GameMode.CREATIVE) MenuItem.reconcileLater(player)
    }

    // ── Wherever it should never be ─────────────────────────────────────

    /** A beacon costs a nether star, and the item is one unless the owner picked otherwise. */
    @EventHandler(priority = EventPriority.LOWEST)
    fun onPrepareCraft(event: PrepareItemCraftEvent) {
        if (event.inventory.matrix.any(MenuItem::isMenuItem)) event.inventory.result = null
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onCraft(event: CraftItemEvent) {
        if (event.inventory.matrix.any(MenuItem::isMenuItem)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onItemSpawn(event: ItemSpawnEvent) {
        if (MenuItem.isMenuItem(event.entity.itemStack)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onPickup(event: EntityPickupItemEvent) {
        if (!MenuItem.isMenuItem(event.item.itemStack)) return
        event.isCancelled = true
        event.item.remove()
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onHopperPickup(event: InventoryPickupItemEvent) {
        if (!MenuItem.isMenuItem(event.item.itemStack)) return
        event.isCancelled = true
        event.item.remove()
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onHopperMove(event: InventoryMoveItemEvent) {
        if (MenuItem.isMenuItem(event.item)) event.isCancelled = true
    }

    /** Clears out a container that somehow came to hold a copy, the moment anyone looks inside. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onOpen(event: InventoryOpenEvent) = MenuItem.purge(event.inventory)

    /** A right-click on a block can arrive twice in one tick, and the second must not reopen the menu. */
    private fun open(player: Player) {
        if (GUIManager.openGUI(player) is MainMenuGUI) return
        MainMenuGUI.show(player)
    }
}
