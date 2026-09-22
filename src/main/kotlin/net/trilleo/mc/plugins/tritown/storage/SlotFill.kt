package net.trilleo.mc.plugins.tritown.storage

/**
 * Putting stacks into rows of slots the way a chest would, and tidying them.
 *
 * Written against [Rules] rather than `ItemStack`, so the arithmetic — which
 * stacks merge, where the rest goes, what comes back — can be tested without a
 * server. [StorageItems] supplies the rules for real items.
 */
object SlotFill {

    /** How the stacks being moved behave. */
    interface Rules<T> {
        /** Whether [a] and [b] are the same item and may share a slot. */
        fun similar(a: T, b: T): Boolean

        /** The most of [stack] one slot holds. */
        fun maxStack(stack: T): Int

        fun amount(stack: T): Int

        /** A copy of [stack] holding [amount]. */
        fun withAmount(stack: T, amount: Int): T
    }

    /**
     * Puts [stack] into [pages], in order: first on top of the matching stacks
     * that have room, then into empty slots.
     *
     * Merging goes through every page before an empty slot is used, so a deposit
     * tops up what is already stored rather than starting a new stack beside it.
     *
     * @return what did not fit, or `null` when all of it did
     */
    fun <T : Any> insert(pages: List<Array<T?>>, stack: T, rules: Rules<T>): T? {
        var left = rules.amount(stack)
        val max = rules.maxStack(stack).coerceAtLeast(1)

        for (slots in pages) {
            for (index in slots.indices) {
                if (left <= 0) return null
                val held = slots[index] ?: continue
                if (!rules.similar(held, stack)) continue

                val room = max - rules.amount(held)
                if (room <= 0) continue
                val moved = minOf(room, left)
                slots[index] = rules.withAmount(held, rules.amount(held) + moved)
                left -= moved
            }
        }

        for (slots in pages) {
            for (index in slots.indices) {
                if (left <= 0) return null
                if (slots[index] != null) continue

                val moved = minOf(max, left)
                slots[index] = rules.withAmount(stack, moved)
                left -= moved
            }
        }

        return if (left > 0) rules.withAmount(stack, left) else null
    }

    /**
     * Merges the stacks in [slots] and orders them by [order], packed to the front.
     *
     * Nothing is lost: every item that went in comes back out, only in fewer
     * stacks where they could be joined.
     *
     * @return whether the page was sorted; it is left untouched when it could not be
     */
    fun <T : Any> sort(slots: Array<T?>, rules: Rules<T>, order: Comparator<T>): Boolean {
        val merged = mutableListOf<T>()
        for (stack in slots.filterNotNull().sortedWith(order)) {
            var left = rules.amount(stack)
            val max = rules.maxStack(stack).coerceAtLeast(1)

            for (index in merged.indices) {
                if (left <= 0) break
                val held = merged[index]
                if (!rules.similar(held, stack)) continue
                val moved = minOf(max - rules.amount(held), left)
                if (moved <= 0) continue
                merged[index] = rules.withAmount(held, rules.amount(held) + moved)
                left -= moved
            }

            while (left > 0) {
                val size = minOf(max, left)
                merged += rules.withAmount(stack, size)
                left -= size
            }
        }

        val ordered = merged.sortedWith(order)
        // Only an oversized stack could split into more than there are slots, and
        // then the page is left as it was rather than losing the overflow.
        if (ordered.size > slots.size) return false
        for (index in slots.indices) slots[index] = ordered.getOrNull(index)
        return true
    }
}
