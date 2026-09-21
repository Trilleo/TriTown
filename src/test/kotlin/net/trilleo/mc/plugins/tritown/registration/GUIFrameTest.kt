package net.trilleo.mc.plugins.tritown.registration

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The slot arithmetic behind every framed menu.
 *
 * Worth pinning down here rather than in game: an off-by-one puts an item under
 * the border where nothing can click it, and that looks like a menu that
 * ignores you rather than like a bug.
 */
class GUIFrameTest {

    @Test
    fun `a six-row menu keeps the four inner rows`() {
        val slots = GUIFrame.contentSlots(6)

        assertEquals(28, slots.size)
        assertEquals(10, slots.first())
        assertEquals(43, slots.last())
    }

    @Test
    fun `content is in reading order, so a list fills left to right`() {
        assertEquals(listOf(10, 11, 12, 13, 14, 15, 16, 19), GUIFrame.contentSlots(6).take(8))
    }

    @Test
    fun `the border rows and columns are never content`() {
        val slots = GUIFrame.contentSlots(6).toSet()

        val topRow = 0..8
        val bottomRow = 45..53
        val leftColumn = (0 until 6).map { it * 9 }
        val rightColumn = (0 until 6).map { it * 9 + 8 }

        assertTrue((topRow + bottomRow + leftColumn + rightColumn).none { it in slots })
    }

    @Test
    fun `a three-row menu keeps its single inner row`() {
        assertEquals((10..16).toList(), GUIFrame.contentSlots(3))
    }

    @Test
    fun `a menu too short to have an inside holds nothing`() {
        assertEquals(emptyList(), GUIFrame.contentSlots(2))
        assertEquals(emptyList(), GUIFrame.contentSlots(1))
    }

    @Test
    fun `spaced buttons sit either side of the middle column`() {
        assertEquals(emptyList(), GUIFrame.spacedColumns(0))
        assertEquals(listOf(4), GUIFrame.spacedColumns(1))
        assertEquals(listOf(3, 5), GUIFrame.spacedColumns(2))
        assertEquals(listOf(2, 4, 6), GUIFrame.spacedColumns(3))
        assertEquals(listOf(1, 3, 5, 7), GUIFrame.spacedColumns(4))
    }

    @Test
    fun `packed items stay inside the border and mirror around the middle`() {
        for (count in 0..7) {
            val columns = GUIFrame.packedColumns(count)
            assertEquals(count, columns.size)
            assertTrue(columns.all { it in 1..7 })
            assertEquals(columns.map { 8 - it }.sorted(), columns, "$count items are not symmetrical")
        }
    }

    @Test
    fun `an even count leaves the middle column empty`() {
        assertEquals(listOf(3, 5), GUIFrame.packedColumns(2))
        assertEquals(listOf(2, 3, 5, 6), GUIFrame.packedColumns(4))
        assertEquals(listOf(1, 2, 3, 5, 6, 7), GUIFrame.packedColumns(6))
    }

    @Test
    fun `a few items sit in the middle of the menu`() {
        assertEquals(listOf(22), GUIFrame.centeredSlots(6, 1))
        assertEquals(listOf(21, 23), GUIFrame.centeredSlots(6, 2))
    }

    @Test
    fun `a partial page centres its last row under full ones`() {
        assertEquals((19..25).toList() + listOf(30, 32), GUIFrame.centeredSlots(6, 9))
    }

    @Test
    fun `a full page is exactly the framed content`() {
        assertEquals(GUIFrame.contentSlots(6), GUIFrame.centeredSlots(6, 28))
    }

    @Test
    fun `nothing to show takes no slots`() {
        assertEquals(emptyList(), GUIFrame.centeredSlots(6, 0))
    }
}
