package net.trilleo.mc.plugins.tritown.protection

import io.papermc.paper.block.TileStateInventoryHolder
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.block.Block
import org.bukkit.block.Campfire
import org.bukkit.block.Chest
import org.bukkit.block.DoubleChest
import org.bukkit.block.TileState
import org.bukkit.entity.*
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import java.util.*

/**
 * Something that can hold a player's items out in the world: a container, an
 * interaction block such as a decorated pot, or an entity such as an item frame.
 *
 * Built by [Claims.of] each time it is needed and never kept, because the block
 * or entity behind it can be gone by the next tick.
 */
class ClaimHolder internal constructor(
    internal val data: PersistentDataContainer,
    val location: Location,
    private val vacancy: () -> Boolean,
) {
    /**
     * Whether nothing is inside and nobody has it open.
     *
     * A viewer keeps the claim alive even on an empty holder. Otherwise a
     * hopper could claim it out from under them and fill it with items the
     * viewer would then take.
     */
    fun isVacant(): Boolean = vacancy()
}

/**
 * Who owns a container or an entity holder.
 *
 * A holder belongs to whoever fills it, for as long as it holds anything. The
 * claim is the owner's UUID in the holder's own persistent data, so the world
 * saves it with the block or entity. It is read lazily: [ownerOf] clears a
 * claim on a holder that has been emptied and closed. No task ever has to release one,
 * and a shared furnace is free again the moment its owner takes the last item out.
 *
 * Blocks are read through `getState(false)`, the live block entity rather than
 * a snapshot, so a claim is written without an `update()`. A claim is only
 * worth anything while items are inside, and moving those items is what marks
 * the chunk to be saved.
 *
 * Never claimed: menus with no holder (TriTown's own, and other plugins'),
 * the ender chest, which is already per player, and players themselves.
 * Villagers, piglins and other mobs that carry items of their own are not
 * holders either. Only mobs a player can saddle or armour are.
 */
object Claims {

    private val KEY = NamespacedKey("tritown", "claim")

    /** The slots a player fills on a mount or a pet. */
    private val MOUNT_SLOTS = listOf(EquipmentSlot.SADDLE, EquipmentSlot.BODY)

    // ── Finding a holder ────────────────────────────────────────────────

    fun of(inventory: Inventory): ClaimHolder? = when (val holder = inventory.getHolder(false)) {
        is DoubleChest -> (holder.leftSide as? Chest)?.let { of(it.block) }
        is TileState -> of(holder.block)
        is Entity -> of(holder)
        else -> null
    }

    fun of(block: Block): ClaimHolder? {
        val state = block.getState(false) as? TileState ?: return null
        val location = block.location.toCenterLocation()

        if (state is Chest) {
            // Both halves of a double chest answer to the claim on its left half.
            val inventory = state.inventory
            val left = (inventory.getHolder(false) as? DoubleChest)?.leftSide as? Chest
            val owner = left?.block?.getState(false) as? TileState ?: state
            return ClaimHolder(owner.persistentDataContainer, location) { vacant(inventory) }
        }

        val vacancy: () -> Boolean = when (state) {
            is TileStateInventoryHolder -> { { vacant(state.inventory) } }
            is Campfire -> { { (0 until state.size).all { state.getItem(it)?.isEmpty ?: true } } }
            else -> return null
        }
        return ClaimHolder(state.persistentDataContainer, location, vacancy)
    }

    fun of(entity: Entity): ClaimHolder? {
        val vacancy: () -> Boolean = when {
            entity is ItemFrame -> { { entity.item.isEmpty } }
            entity is ArmorStand -> { { EquipmentSlot.entries.all { slot -> isBare(entity, slot) } } }
            entity is Allay -> { { entity.equipment.itemInMainHand.isEmpty && vacant(entity.inventory) } }
            entity is Mob && entity !is Enemy && MOUNT_SLOTS.any(entity::canUseEquipmentSlot) -> {
                {
                    MOUNT_SLOTS.all { slot -> isBare(entity, slot) } &&
                            ((entity as? InventoryHolder)?.inventory?.let(::vacant) ?: true)
                }
            }

            entity is Vehicle && entity is InventoryHolder -> { { vacant(entity.inventory) } }
            else -> return null
        }
        return ClaimHolder(entity.persistentDataContainer, entity.location, vacancy)
    }

    private fun vacant(inventory: Inventory): Boolean = inventory.isEmpty && inventory.viewers.isEmpty()

    private fun isBare(entity: LivingEntity, slot: EquipmentSlot): Boolean =
        !entity.canUseEquipmentSlot(slot) || entity.equipment?.getItem(slot)?.isEmpty ?: true

    // ── Reading and writing a claim ─────────────────────────────────────

    /** Who holds [holder], or `null` when nobody does. A vacant holder loses its claim here. */
    fun ownerOf(holder: ClaimHolder): UUID? {
        val owner = recorded(holder.data) ?: return null
        if (!holder.isVacant()) return owner
        release(holder)
        return null
    }

    /**
     * The claim written on [data], whether or not anything is still inside.
     *
     * For what a block or entity leaves behind as it is destroyed, when its
     * contents can no longer be counted: the claim still says whose they were.
     */
    fun recorded(holder: ClaimHolder): UUID? = recorded(holder.data)

    fun recorded(data: PersistentDataContainer): UUID? =
        data.get(KEY, PersistentDataType.STRING)?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    fun claim(holder: ClaimHolder, owner: UUID) {
        holder.data.set(KEY, PersistentDataType.STRING, owner.toString())
    }

    fun release(holder: ClaimHolder) {
        holder.data.remove(KEY)
    }
}
