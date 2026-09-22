package net.trilleo.mc.plugins.tritown.storage

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * Depositing and sorting move a player's items around without them watching
 * each slot, so what matters is that nothing is ever lost or made up.
 */
class SlotFillTest {

    private data class Stack(val type: String, val amount: Int, val max: Int = 64)

    private val rules = object : SlotFill.Rules<Stack> {
        override fun similar(a: Stack, b: Stack) = a.type == b.type
        override fun maxStack(stack: Stack) = stack.max
        override fun amount(stack: Stack) = stack.amount
        override fun withAmount(stack: Stack, amount: Int) = stack.copy(amount = amount)
    }

    private val byType = compareBy<Stack> { it.type }

    @Test
    fun `a deposit tops up matching stacks on every page before starting a new one on the first`() {
        val first = arrayOf<Stack?>(null, Stack("dirt", 10))
        val second = arrayOf<Stack?>(Stack("dirt", 60), null)

        assertNull(SlotFill.insert(listOf(first, second), Stack("dirt", 60), rules))

        assertContentEquals(arrayOf<Stack?>(Stack("dirt", 2), Stack("dirt", 64)), first)
        assertContentEquals(arrayOf<Stack?>(Stack("dirt", 64), null), second)
    }

    @Test
    fun `what does not fit comes back`() {
        val page = arrayOf<Stack?>(Stack("stone", 60), null)

        val leftover = SlotFill.insert(listOf(page), Stack("stone", 100), rules)

        assertEquals(Stack("stone", 32), leftover)
        assertContentEquals(arrayOf<Stack?>(Stack("stone", 64), Stack("stone", 64)), page)
    }

    @Test
    fun `an item that does not stack takes a slot each`() {
        val page = arrayOfNulls<Stack>(3)

        val leftover = SlotFill.insert(listOf(page), Stack("sword", 2, max = 1), rules)

        assertNull(leftover)
        assertContentEquals(arrayOf<Stack?>(Stack("sword", 1, 1), Stack("sword", 1, 1), null), page)
    }

    @Test
    fun `sorting merges stacks, orders them, and keeps every item`() {
        val page = arrayOf<Stack?>(Stack("stone", 40), null, Stack("dirt", 5), Stack("stone", 40), null)

        SlotFill.sort(page, rules, byType)

        assertContentEquals(
            arrayOf<Stack?>(Stack("dirt", 5), Stack("stone", 64), Stack("stone", 16), null, null),
            page,
        )
    }

    @Test
    fun `a page that cannot be sorted without losing items is left alone`() {
        val page = arrayOf<Stack?>(Stack("stone", 200), null)
        val before = page.copyOf()

        assertFalse(SlotFill.sort(page, rules, byType))
        assertContentEquals(before, page)
    }
}
