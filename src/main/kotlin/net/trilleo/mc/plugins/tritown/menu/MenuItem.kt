package net.trilleo.mc.plugins.tritown.menu

import net.trilleo.mc.plugins.tritown.Main
import net.trilleo.mc.plugins.tritown.config.MainMenuSettings
import net.trilleo.mc.plugins.tritown.registration.PluginItem
import net.trilleo.mc.plugins.tritown.utils.LoreUtil
import net.trilleo.mc.plugins.tritown.utils.itemStack
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.PlayerInventory
import org.bukkit.persistence.PersistentDataType

/**
 * The item in the last hotbar slot that opens the main menu.
 *
 * Everything about it rests on one invariant: **while a player is online and
 * the item is enabled, exactly one copy exists, in slot [SLOT] of their own
 * inventory — and none exists anywhere else, ever.** [reconcile] is the only
 * code that creates a copy, and it restores the invariant from whatever state
 * it finds: strays anywhere in the inventory, on the cursor or in an open
 * container are deleted, and the slot is refilled.
 *
 * [net.trilleo.mc.plugins.tritown.listeners.menu.MenuItemListener] refuses
 * every action that would move a copy, but it does not have to be perfect.
 * Whatever gets past it — a creative client writing its own slots, a plugin
 * calling `setItem`, a drop the server hands back — is undone by the next
 * [reconcile], which the listener asks for after anything suspicious and
 * [net.trilleo.mc.plugins.tritown.tasks.menu.MenuItemTask] runs every second
 * regardless. A duplicate can therefore never outlive the second it was made
 * in, and cannot leave the player during it: it is not tradeable, droppable,
 * storable or craftable.
 *
 * The item is taken off a player as they quit and off everyone when TriTown
 * stops, so it is never written into a save and never outlives the plugin.
 */
object MenuItem {

    /** The last slot of the hotbar. */
    const val SLOT = 8

    /** The [PluginItem.ITEM_ID_KEY] value marking a stack as the menu item. */
    private const val ID = "main-menu"

    /** Whether players should be carrying the item. */
    val isEnabled: Boolean
        get() = MainMenuSettings.isLoaded && MainMenuSettings.snapshot.itemEnabled

    /** Whether [stack] is a copy of the menu item. Read without cloning its meta, since this runs on every click. */
    fun isMenuItem(stack: ItemStack?): Boolean =
        stack != null && !stack.isEmpty &&
                stack.persistentDataContainer.get(PluginItem.ITEM_ID_KEY, PersistentDataType.STRING) == ID

    /**
     * The item as [player] should be carrying it, named in their language.
     *
     * It never stacks, so two copies can never become one stack of two that
     * looks like a single legitimate item.
     */
    fun create(player: Player): ItemStack = itemStack(MainMenuSettings.snapshot.itemMaterial) {
        name(player.tr("menu-item.name"))
        flag(ItemFlag.HIDE_ATTRIBUTES)
        pdc(PluginItem.ITEM_ID_KEY, PersistentDataType.STRING, ID)
        meta {
            lore(LoreUtil.wrapLore(player.tr("menu-item.lore")))
            setMaxStackSize(1)
            setEnchantmentGlintOverride(true)
        }
    }

    /**
     * Restores the invariant for [player].
     *
     * Cheap when nothing is wrong: one pass over the inventory and a comparison
     * with a freshly built copy, which also catches a stale one — a language
     * the player has since switched, or a material changed by a reload.
     */
    fun reconcile(player: Player) {
        if (!player.isOnline || player.isDead) return

        val wanted = if (isEnabled) create(player) else null
        removeCopies(player, keepSlot = wanted != null)
        if (wanted == null) return

        val inventory = player.inventory
        val held = inventory.getItem(SLOT)
        if (held != null && held.amount == 1 && held.isSimilar(wanted)) return

        // Copied before the slot is overwritten: the stack read out of it may be a live view of that slot.
        val displaced = held?.takeIf { !it.isEmpty && !isMenuItem(it) }?.clone()
        inventory.setItem(SLOT, wanted)
        displaced?.let { relocate(player, it) }
    }

    /** [reconcile] on the next tick, once whatever the server is in the middle of has finished moving items. */
    fun reconcileLater(player: Player) {
        Bukkit.getScheduler().runTask(Main.instance, Runnable { reconcile(player) })
    }

    /** [reconcile] for everyone online, after a reload may have changed what the item should be. */
    fun reconcileAll() {
        Bukkit.getOnlinePlayers().forEach(::reconcile)
    }

    /** Takes every copy off [player], so none is saved with their inventory. */
    fun strip(player: Player) = removeCopies(player, keepSlot = false)

    /** [strip] for everyone online, as TriTown stops. */
    fun stripAll() {
        Bukkit.getOnlinePlayers().forEach(::strip)
    }

    /**
     * Deletes every copy inside [inventory].
     *
     * Another player's inventory is left alone, because the copy in its last
     * slot is the one that player is meant to have — an administrator looking
     * into it must not take it away.
     */
    fun purge(inventory: Inventory) {
        if (inventory is PlayerInventory) return
        for (slot in 0 until inventory.size) {
            if (isMenuItem(inventory.getItem(slot))) inventory.setItem(slot, null)
        }
    }

    /** Deletes every copy [player] can reach, other than the one in [SLOT] when [keepSlot] is set. */
    private fun removeCopies(player: Player, keepSlot: Boolean) {
        val inventory = player.inventory
        for (slot in 0 until inventory.size) {
            if (slot == SLOT && keepSlot) continue
            if (isMenuItem(inventory.getItem(slot))) inventory.setItem(slot, null)
        }
        if (isMenuItem(player.itemOnCursor)) player.setItemOnCursor(null)
        purge(player.openInventory.topInventory)
    }

    /**
     * Finds room for what was in the slot before the menu item took it.
     *
     * The menu item goes in first, so the slot it now fills cannot be handed
     * straight back; whatever does not fit is dropped at the player's feet
     * rather than deleted.
     */
    private fun relocate(player: Player, displaced: ItemStack) {
        val leftover = player.inventory.addItem(displaced).values
        leftover.forEach { player.world.dropItemNaturally(player.location, it) }
        player.sendPrefixed(player.tr(if (leftover.isEmpty()) "menu-item.relocated" else "menu-item.dropped"))
    }
}
