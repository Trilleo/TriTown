package net.trilleo.mc.plugins.tritown.shops

import net.trilleo.mc.plugins.tritown.enums.MatchMode
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * Finding and taking the goods a shop trade asks for.
 *
 * Every method here runs on the server thread, because inventories may not be
 * touched from anywhere else. Removals hand back exactly what they took so a
 * trade that fails later can put it right back — a purchase must never be able
 * to leave a player short of both the goods and the price.
 *
 * Only the [MatchMode] side of an inventory lives here; giving items back and
 * asking whether they would fit is the same for everything that moves items, so
 * that lives in [net.trilleo.mc.plugins.tritown.utils.InventoryUtil].
 */
object ShopInventory {

    /** How many of [template] the player holds, counting by [mode]. */
    fun count(player: Player, template: ItemStack, mode: MatchMode): Int =
        player.inventory.storageContents.sumOf { stack ->
            if (stack != null && matches(template, stack, mode)) stack.amount else 0
        }

    /**
     * Takes [amount] items matching [template] out of the player's storage.
     *
     * @return the stacks that were removed, or `null` when the player did not
     *   have enough — in which case nothing is taken at all
     */
    fun remove(player: Player, template: ItemStack, mode: MatchMode, amount: Int): List<ItemStack>? {
        if (amount <= 0) return emptyList()
        if (count(player, template, mode) < amount) return null

        val removed = mutableListOf<ItemStack>()
        var outstanding = amount
        val contents = player.inventory.storageContents

        for (index in contents.indices) {
            if (outstanding == 0) break
            val stack = contents[index] ?: continue
            if (!matches(template, stack, mode)) continue

            val taken = minOf(outstanding, stack.amount)
            removed += stack.clone().apply { this.amount = taken }
            outstanding -= taken

            if (taken == stack.amount) {
                contents[index] = null
            } else {
                stack.amount -= taken
            }
        }

        player.inventory.storageContents = contents
        return removed
    }

    private fun matches(template: ItemStack, stack: ItemStack, mode: MatchMode): Boolean = when (mode) {
        MatchMode.EXACT -> template.isSimilar(stack)
        MatchMode.MATERIAL -> template.type == stack.type
    }
}
