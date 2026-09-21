package net.trilleo.mc.plugins.tritown.registration

import net.trilleo.mc.plugins.tritown.utils.itemStack
import org.bukkit.Material
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

/**
 * The border drawn around a menu's content, and the slots it leaves free.
 *
 * Shared rather than living in [PagedPluginGUI], because a plain [PluginGUI]
 * that lays out a grid of its own wants exactly the same border and the same
 * idea of which slots are inside it.
 */
object GUIFrame {

    private const val ROW_SIZE = 9
    private const val INNER_COLUMNS = 7
    private const val MIDDLE_COLUMN = 4
    private const val MAX_SPACED = 4

    /** A single border slot: black glass with no name and no tooltip. */
    fun pane(): ItemStack = itemStack(Material.BLACK_STAINED_GLASS_PANE) {
        name(" ")
        hideTooltip(true)
    }

    /**
     * The slots inside the border, in reading order.
     *
     * The top and bottom rows and the first and last column are the border, so
     * content is everything in between. A menu that wants the bottom row for
     * navigation or buttons of its own simply draws over it: that row is the
     * bottom edge either way, and is never content.
     */
    fun contentSlots(rows: Int): List<Int> {
        if (rows < 3) return emptyList()

        return buildList {
            for (row in 1..rows - 2) {
                for (column in 1 until ROW_SIZE - 1) add(row * ROW_SIZE + column)
            }
        }
    }

    /**
     * The columns (0 to 8) of [count] buttons spread along a row with a gap
     * between each, centred on the middle column.
     *
     * Four is the most that fit inside the border: one button sits in the
     * middle, two either side of it, three at 2, 4 and 6, four at 1, 3, 5 and 7.
     */
    fun spacedColumns(count: Int): List<Int> {
        require(count in 0..MAX_SPACED) { "At most $MAX_SPACED buttons fit spaced along a row, not $count" }
        val first = MIDDLE_COLUMN - (count - 1)
        return List(count) { first + it * 2 }
    }

    /**
     * The columns (1 to 7) of [count] items packed side by side inside the
     * border, centred on the middle column.
     *
     * An even count cannot straddle the middle column evenly, so it leaves that
     * column empty instead: two items sit at 3 and 5 rather than off to one side
     * at 3 and 4, which keeps both halves of the row the same.
     */
    fun packedColumns(count: Int): List<Int> {
        require(count in 0..INNER_COLUMNS) { "At most $INNER_COLUMNS items fit inside a row, not $count" }
        val first = MIDDLE_COLUMN - count / 2
        return if (count % 2 == 1) {
            List(count) { first + it }
        } else {
            (first..first + count).filter { it != MIDDLE_COLUMN }
        }
    }

    /**
     * The slots of a [rows]-row framed menu that show [count] items, centred
     * both ways.
     *
     * Rows are filled seven at a time and the last one is [packedColumns], and
     * the block of rows they take sits in the middle of the space inside the
     * border — so two players in a list read as a pair in the middle of the
     * menu rather than as two items lost in its top-left corner. Filling every
     * slot gives exactly [contentSlots].
     */
    fun centeredSlots(rows: Int, count: Int): List<Int> {
        val innerRows = (rows - 2).coerceAtLeast(0)
        require(count in 0..innerRows * INNER_COLUMNS) { "$count items do not fit inside a $rows-row menu" }
        if (count == 0) return emptyList()

        val used = (count + INNER_COLUMNS - 1) / INNER_COLUMNS
        val firstRow = 1 + (innerRows - used) / 2

        return buildList {
            for (index in 0 until used) {
                val inRow = if (index == used - 1) count - INNER_COLUMNS * index else INNER_COLUMNS
                packedColumns(inRow).forEach { add((firstRow + index) * ROW_SIZE + it) }
            }
        }
    }

    /**
     * Fills every slot of [inventory] that is not in [contentSlots] with the border.
     *
     * Callers draw their own buttons over it afterwards, so a reserved row can be
     * bordered first and then written into.
     */
    fun draw(inventory: Inventory, contentSlots: Collection<Int>) {
        val inside = contentSlots.toSet()
        val pane = pane()
        for (slot in 0 until inventory.size) {
            if (slot !in inside) inventory.setItem(slot, pane.clone())
        }
    }
}
