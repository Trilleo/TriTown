package net.trilleo.mc.plugins.tritown.trades

import net.trilleo.mc.plugins.tritown.economy.CurrencyRegistry
import net.trilleo.mc.plugins.tritown.economy.EconomyContext
import net.trilleo.mc.plugins.tritown.economy.Money
import net.trilleo.mc.plugins.tritown.economy.TransactionReason
import net.trilleo.mc.plugins.tritown.utils.EconomyUtil
import net.trilleo.mc.plugins.tritown.utils.InventoryUtil
import org.bukkit.entity.Player

/**
 * The swap itself, and the only place a trade's items or money move.
 *
 * The ordering is what makes it safe, and it is the same discipline shop trades
 * follow: everything that can refuse is asked before anything is handed over,
 * and the one step that can still fail after that — the money — is taken before
 * either side's goods leave escrow. A refused trade leaves both players exactly
 * as they were, still at the menu, free to change what is on the table and try
 * again.
 *
 * Money is settled as a single net payment rather than as two. If one side puts
 * up 100 and the other 40, sixty moves once: two payments could leave a player
 * unable to make the second one with money the first had already taken, and
 * there is no state in which a trade should be half paid for.
 */
object TradeExchange {

    /** The outcome of a settlement. */
    sealed interface Result {

        /** The swap went through. */
        data object Success : Result

        /** The swap was refused, with a `trade.error.*` key and the player it is about. */
        data class Failure(val key: String, val name: String = "") : Result
    }

    /**
     * Carries out [session], which both players have confirmed.
     *
     * The session is left alone on failure: the caller drops both
     * confirmations and shows the reason, because what went wrong is usually
     * something the players can put right without starting over.
     */
    fun execute(session: TradeSession): Result {
        val initiator = session.initiator
        val target = session.target

        val first = initiator.player ?: return Result.Failure("trade.error.gone", TradeManager.name(initiator))
        val second = target.player ?: return Result.Failure("trade.error.gone", TradeManager.name(target))
        if (!TradeManager.inRange(first, second)) return Result.Failure("trade.error.too-far")

        val toFirst = target.offer.items
        val toSecond = initiator.offer.items
        if (!InventoryUtil.hasSpaceFor(first, toFirst)) {
            return Result.Failure("trade.error.no-space", TradeManager.name(first))
        }
        if (!InventoryUtil.hasSpaceFor(second, toSecond)) {
            return Result.Failure("trade.error.no-space", TradeManager.name(second))
        }

        settleMoney(first, initiator.offer.money, second, target.offer.money)?.let { return it }

        InventoryUtil.give(first, target.offer.drain())
        InventoryUtil.give(second, initiator.offer.drain())
        return Result.Success
    }

    /**
     * Moves what the two sides owe each other, or returns why it could not.
     *
     * Attributed rather than moved bare, so a trade shows up in the ledger and
     * the panel's figures as a payment between two players instead of as an
     * anonymous Vault call.
     */
    private fun settleMoney(first: Player, fromFirst: Money, second: Player, fromSecond: Money): Result.Failure? {
        val net = fromFirst.minusExact(fromSecond)
        if (net.isZero) return null

        if (!EconomyUtil.isAvailable) return Result.Failure("trade.error.economy-unavailable")

        val payer = if (net.isPositive) first else second
        val payee = if (net.isPositive) second else first
        val amount = toDouble(net.abs())

        val paid = EconomyContext.with(EconomyContext.SOURCE_TRADE, TransactionReason.TRADE) {
            EconomyUtil.transfer(payer, payee, amount)
        }

        return if (paid) null else Result.Failure("trade.error.money", TradeManager.name(payer))
    }

    /** [money] at the primary currency's scale, for the one call that crosses into Vault. */
    private fun toDouble(money: Money): Double =
        if (CurrencyRegistry.isLoaded) CurrencyRegistry.primary.toDouble(money) else money.toDouble(2)
}
