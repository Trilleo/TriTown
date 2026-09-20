package net.trilleo.mc.plugins.tritown.enums

import net.trilleo.mc.plugins.tritown.economy.EconomyContext
import net.trilleo.mc.plugins.tritown.economy.TransactionReason

/**
 * What a movement of money was for, in the few buckets an economy is actually
 * read in.
 *
 * A transaction is recorded with a reason key and a source, which together are
 * precise enough to read one line of a history but far too fine to total: the
 * reason carries arguments (`money.reason.admin-set?admin=Bob`), so summing by
 * reason would produce a row per administrator. A category is the stable,
 * translatable grouping the statistics are kept in, and it never grows with the
 * number of players.
 */
enum class FlowCategory {

    /** The balance a player is given the first time they join. Always a faucet. */
    STARTING_BALANCE,

    /** Bought from, or sold to, one of the server's own shops. */
    SHOP,

    /** Anything Towny moved: bank deposits and withdrawals, plot sales, upkeep, taxes. */
    TOWNY,

    /** An administrator giving, taking, setting or resetting a balance. */
    ADMIN,

    /** A payment from one account to another, whether sent with `/pay` or settled by a trade. */
    PAYMENT,

    /** Another plugin, through Vault, with nothing to identify it further. */
    EXTERNAL,

    /** Recorded with a reason TriTown has no category for. */
    OTHER;

    /** The translation key naming this category, spelled out so the language test can see it. */
    val key: String
        get() = when (this) {
            STARTING_BALANCE -> "money.flow.starting-balance"
            SHOP -> "money.flow.shop"
            TOWNY -> "money.flow.towny"
            ADMIN -> "money.flow.admin"
            PAYMENT -> "money.flow.payment"
            EXTERNAL -> "money.flow.external"
            OTHER -> "money.flow.other"
        }

    companion object {

        /**
         * The category a transaction recorded with [source] and [reason] belongs
         * to.
         *
         * The reason is matched on its key alone, since anything after `?` is an
         * argument. The source is only consulted when the reason says nothing
         * useful, which is what happens when another plugin moves money through
         * Vault inside an operation TriTown did attribute.
         */
        fun of(source: String, reason: String): FlowCategory {
            val key = reason.substringBefore('?')
            return when {
                key == TransactionReason.STARTING_BALANCE -> STARTING_BALANCE
                key == TransactionReason.SHOP_BUY || key == TransactionReason.SHOP_SELL -> SHOP
                key == TransactionReason.TOWNY || key == TransactionReason.TOWN_DELETED -> TOWNY
                key == TransactionReason.ADMIN_SET || key == TransactionReason.ADMIN_RESET -> ADMIN
                key == TransactionReason.PAYMENT || key == TransactionReason.TRADE -> PAYMENT
                source == EconomyContext.SOURCE_SHOP -> SHOP
                source == EconomyContext.SOURCE_TOWNY -> TOWNY
                key == TransactionReason.EXTERNAL -> EXTERNAL
                else -> OTHER
            }
        }
    }
}
