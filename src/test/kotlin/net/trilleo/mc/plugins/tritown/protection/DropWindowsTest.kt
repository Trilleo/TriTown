package net.trilleo.mc.plugins.tritown.protection

import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A window decides who owns a drop nobody else has claimed, so a miss hands the
 * item to everyone and a false match hands it to the wrong player.
 */
class DropWindowsTest {

    private var tick = 100
    private val windows = DropWindows({ tick })

    private val world = UUID.randomUUID()
    private val alice = UUID.randomUUID()
    private val bob = UUID.randomUUID()

    @Test
    fun `a spawn near a window belongs to its owner`() {
        windows.expect(world, 0.5, 64.5, 0.5, alice)
        assertEquals(DropWindows.Match.Owned(alice), windows.match(world, 0.8, 64.2, 0.4))
    }

    @Test
    fun `a spawn out of reach, or in another world, matches nothing`() {
        windows.expect(world, 0.5, 64.5, 0.5, alice)
        assertEquals(DropWindows.Match.None, windows.match(world, 5.0, 64.5, 0.5))
        assertEquals(DropWindows.Match.None, windows.match(UUID.randomUUID(), 0.5, 64.5, 0.5))
    }

    @Test
    fun `the nearest window wins`() {
        windows.expect(world, 0.0, 64.0, 0.0, alice)
        windows.expect(world, 1.5, 64.0, 0.0, bob)
        assertEquals(DropWindows.Match.Owned(bob), windows.match(world, 1.2, 64.0, 0.0))
    }

    @Test
    fun `a suppression beats any window around it`() {
        windows.expect(world, 0.0, 64.0, 0.0, alice)
        windows.suppress(world, 1.0, 64.0, 0.0)
        assertEquals(DropWindows.Match.Public, windows.match(world, 0.1, 64.0, 0.0))
    }

    @Test
    fun `a window lasts its own tick and the next, and no longer`() {
        windows.expect(world, 0.0, 64.0, 0.0, alice)
        tick += 1
        assertEquals(DropWindows.Match.Owned(alice), windows.match(world, 0.0, 64.0, 0.0))
        tick += 1
        assertEquals(DropWindows.Match.None, windows.match(world, 0.0, 64.0, 0.0))
    }
}
