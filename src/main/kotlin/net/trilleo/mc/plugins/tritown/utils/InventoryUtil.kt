package net.trilleo.mc.plugins.tritown.utils

import net.trilleo.mc.plugins.tritown.protection.ItemOwnership
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * Putting stacks into a player's own inventory, and asking first whether they
 * will fit.
 *
 * Everything here runs on the server thread, because an inventory may not be
 * touched from anywhere else.
 *
 * ### Usage
 *
 * ```kotlin
 * if (!InventoryUtil.hasSpaceFor(player, goods)) return
 * InventoryUtil.give(player, goods)
 * ```
 */
object InventoryUtil {

    /** A player's storage, without armour or the off-hand, which none of this touches. */
    private const val STORAGE_SLOTS = 36

    /**
     * Whether [items] would all fit in the player's storage.
     *
     * Tested against a copy rather than by counting empty slots, so partial
     * stacks, stack limits and items that do not stack are all accounted for by
     * the same code that will do the real insertion.
     */
    fun hasSpaceFor(player: Player, items: List<ItemStack>): Boolean {
        if (items.isEmpty()) return true

        val scratch = Bukkit.createInventory(null, STORAGE_SLOTS)
        scratch.storageContents = player.inventory.storageContents.map { it?.clone() }.toTypedArray()
        return scratch.addItem(*split(items).toTypedArray()).isEmpty()
    }

    /**
     * Puts [items] into the player's storage, dropping at their feet whatever
     * will not fit. Nobody but the player can pick those drops up.
     *
     * Space is checked before anything is handed over, so a drop only happens
     * when something else filled the inventory in between. Dropping is still
     * the right answer there: the player has already paid.
     */
    fun give(player: Player, items: List<ItemStack>) {
        if (items.isEmpty()) return

        val leftover = player.inventory.addItem(*split(items).toTypedArray())
        for (stack in leftover.values) {
            ItemOwnership.dropFor(player, stack)
        }
    }

    /**
     * [items] broken down into stacks the game allows.
     *
     * A price multiplied by a shift-click can ask for more than one stack holds,
     * and an oversized stack is accepted in memory but cannot be stored, so it
     * is split before anything is measured against an inventory.
     */
    fun split(items: List<ItemStack>): List<ItemStack> = items.flatMap { item ->
        val perStack = item.maxStackSize.coerceAtLeast(1)
        if (item.amount <= perStack) return@flatMap listOf(item.clone())

        buildList {
            var outstanding = item.amount
            while (outstanding > 0) {
                val size = minOf(outstanding, perStack)
                add(item.clone().apply { amount = size })
                outstanding -= size
            }
        }
    }
}
