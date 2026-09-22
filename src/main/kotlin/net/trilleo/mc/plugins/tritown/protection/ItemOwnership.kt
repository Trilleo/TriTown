package net.trilleo.mc.plugins.tritown.protection

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.entity.Item
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.*

/**
 * Who a dropped item belongs to.
 *
 * The owner is the game's own field, [Item.getOwner]. The game saves it with
 * the entity, refuses a pickup by anyone else, and never merges two drops with
 * different owners. `/give` already uses it for whatever does not fit. Nothing
 * else records ownership, and an item in an inventory is never marked at all:
 * it is its holder's by being there.
 *
 * Drops that do not exist yet are given an owner through [expect], which the
 * item's spawn consumes (see [DropWindows]).
 */
object ItemOwnership {

    private val windows = DropWindows(Bukkit::getCurrentTick)

    fun ownerOf(item: Item): UUID? = item.owner

    fun bind(item: Item, owner: UUID) {
        item.owner = owner
    }

    /**
     * Drops [stack] at [player]'s feet as theirs, for items the plugin hands a
     * player that do not fit.
     *
     * Never drop an item for a player with `dropItemNaturally` alone: anyone
     * standing nearby could take it.
     */
    fun dropFor(player: Player, stack: ItemStack): Item =
        player.world.dropItemNaturally(player.location, stack) { item ->
            if (Protection.settings(player.world) != null) bind(item, player.uniqueId)
        }

    /** Drops spawning near [location] this tick or the next belong to [owner]. */
    fun expect(location: Location, owner: UUID) {
        windows.expect(location.world.uid, location.x, location.y, location.z, owner)
    }

    fun expect(block: Block, owner: UUID) = expect(block.location.toCenterLocation(), owner)

    /** Drops spawning near [location] this tick or the next stay public, whatever else expects them. */
    fun suppress(location: Location) {
        windows.suppress(location.world.uid, location.x, location.y, location.z)
    }

    fun match(location: Location): DropWindows.Match =
        windows.match(location.world.uid, location.x, location.y, location.z)
}
