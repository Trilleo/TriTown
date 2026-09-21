package net.trilleo.mc.plugins.tritown.economy

/**
 * Says where the transaction currently being made came from, so the ledger can
 * record it without every call having to pass a reason down through the stack.
 *
 * Most money moves because Towny or another plugin asked Vault to move it, and
 * those calls arrive with nothing to identify them — hence the default. TriTown's
 * own commands wrap their work in [with] so the record says what actually
 * happened.
 */
object EconomyContext {

    /**
     * @param source where the call came from, e.g. `command`, `vault`, `towny`
     * @param reason why the money moved, as [TransactionReason] encodes it
     */
    data class Entry(val source: String, val reason: String)

    /** What a transaction is attributed to when nothing has claimed it. */
    val DEFAULT = Entry(source = "vault", reason = TransactionReason.EXTERNAL)

    private val current = ThreadLocal<Entry>()

    /** Runs [block] with every transaction it makes attributed to [source] and [reason]. */
    fun <T> with(source: String, reason: String, block: () -> T): T {
        val previous = current.get()
        current.set(Entry(source, reason))
        try {
            return block()
        } finally {
            // Restored rather than cleared, because these can nest.
            if (previous == null) current.remove() else current.set(previous)
        }
    }

    /** Runs [block] attributed to a TriTown command. */
    fun <T> command(reason: String, block: () -> T): T = with(SOURCE_COMMAND, reason, block)

    /** The attribution in force on this thread. */
    fun current(): Entry = current.get() ?: DEFAULT

    const val SOURCE_COMMAND = "command"
    const val SOURCE_TOWNY = "towny"
    const val SOURCE_SHOP = "shop"
    const val SOURCE_TRADE = "trade"
    const val SOURCE_STORAGE = "storage"
}
