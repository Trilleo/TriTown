package net.trilleo.mc.plugins.tritown.protection

import java.util.*

/**
 * Short-lived notes that say "whatever drops here, now, belongs to this player".
 *
 * Most drops do not exist yet when the game says who caused them. A mob's loot,
 * berries picked off a bush and a dropper's item all appear a moment after the
 * event that names the player. So the event opens a window at that spot, and the
 * item's spawn claims it. Nothing is written onto the `ItemStack`: a mark that
 * was never stripped would follow the item into an inventory and stop it
 * stacking.
 *
 * A window lasts the tick it was opened in and the next one, which is as long
 * as the game ever takes to spawn the drops. A window with no owner is a
 * *suppression*. It wins over any other window at the same spot, so a player's
 * death drops stay public even when their killer's window overlaps them.
 *
 * Kept free of Bukkit so the matching can be tested on plain coordinates.
 *
 * @param clock  the current server tick
 * @param radius how far from the window's centre a spawn still counts, in blocks
 */
class DropWindows(private val clock: () -> Int, radius: Double = 2.0) {

    /** What a spawn at a point matched. */
    sealed interface Match {
        data object None : Match
        data object Public : Match
        data class Owned(val owner: UUID) : Match
    }

    private data class Window(val world: UUID, val x: Double, val y: Double, val z: Double, val owner: UUID?, val tick: Int)

    private val radiusSquared = radius * radius
    private val windows = ArrayList<Window>()

    fun expect(world: UUID, x: Double, y: Double, z: Double, owner: UUID) {
        open(Window(world, x, y, z, owner, clock()))
    }

    fun suppress(world: UUID, x: Double, y: Double, z: Double) {
        open(Window(world, x, y, z, null, clock()))
    }

    /** The owner a drop spawning at this point should have: a suppression first, then the nearest window. */
    fun match(world: UUID, x: Double, y: Double, z: Double): Match {
        prune()
        var nearest: Window? = null
        var nearestDistance = Double.MAX_VALUE
        for (window in windows) {
            if (window.world != world) continue
            val distance = square(window.x - x) + square(window.y - y) + square(window.z - z)
            if (distance > radiusSquared) continue
            if (window.owner == null) return Match.Public
            if (distance < nearestDistance) {
                nearest = window
                nearestDistance = distance
            }
        }
        return nearest?.owner?.let { Match.Owned(it) } ?: Match.None
    }

    private fun open(window: Window) {
        prune()
        windows += window
    }

    private fun prune() {
        val oldest = clock() - LIFETIME_TICKS
        windows.removeIf { it.tick < oldest }
    }

    private fun square(value: Double) = value * value

    private companion object {
        /** Ticks after the one a window was opened in that it still matches. */
        const val LIFETIME_TICKS = 1
    }
}
