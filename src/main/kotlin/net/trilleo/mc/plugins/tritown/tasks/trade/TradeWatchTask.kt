package net.trilleo.mc.plugins.tritown.tasks.trade

import net.trilleo.mc.plugins.tritown.guis.trade.TradeGUI
import net.trilleo.mc.plugins.tritown.registration.GUIManager
import net.trilleo.mc.plugins.tritown.registration.PluginTask
import net.trilleo.mc.plugins.tritown.trades.TradeManager
import net.trilleo.mc.plugins.tritown.trades.TradeParty
import net.trilleo.mc.plugins.tritown.trades.TradeSession
import net.trilleo.mc.plugins.tritown.utils.ChatPrompt
import org.bukkit.entity.Player

/**
 * Watches every live trade once a second: lapses requests nobody answered, and
 * calls off a trade whose two players are no longer in a position to finish it.
 *
 * The menu itself only hears about what is clicked in it, so everything that
 * happens *around* a trade — one player walking away, the other one being
 * shown something else, someone leaving a question hanging in chat — is noticed
 * here. Every one of those ends with the escrow going back where it came from.
 */
class TradeWatchTask : PluginTask(delay = INTERVAL_TICKS, period = INTERVAL_TICKS) {

    override fun run() {
        if (!TradeManager.isEnabled) return

        val now = System.currentTimeMillis()
        TradeManager.expireRequests(now)
        TradeManager.sessions().forEach { watch(it, now) }
    }

    private fun watch(session: TradeSession, now: Long) {
        val first = session.initiator.player
        val second = session.target.player
        if (first == null || second == null) {
            TradeManager.cancel(session, "trade.cancelled.gone")
            return
        }

        if (!TradeManager.inRange(first, second)) {
            TradeManager.cancel(session, "trade.cancelled.too-far")
            return
        }

        // A trade that has only just opened may not have drawn both menus yet.
        if (now - session.startedAt < OPENING_GRACE_MILLIS) return

        for (party in session.parties) {
            val player = party.player ?: continue

            if (party.isPrompting) {
                if (!resume(session, party, player, now)) return
                continue
            }

            if (GUIManager.openGUI(player)?.id != TradeGUI.ID) {
                TradeManager.cancel(session, "trade.cancelled.closed")
                return
            }
        }
    }

    /**
     * Deals with a player who is off in chat naming an amount.
     *
     * Backing out of the question and answering it look identical for the one
     * tick between the answer being taken and the callback running, so a player
     * is only brought back to the menu once they have looked idle twice.
     *
     * @return `false` when the trade was called off and the rest of it should be left alone
     */
    private fun resume(session: TradeSession, party: TradeParty, player: Player, now: Long): Boolean {
        if (ChatPrompt.isWaiting(player)) {
            party.promptIdleSince = 0L
            if (now - party.promptingSince > PROMPT_TIMEOUT_MILLIS) {
                TradeManager.cancel(session, "trade.cancelled.away")
                return false
            }
            return true
        }

        if (party.promptIdleSince == 0L) {
            party.promptIdleSince = now
            return true
        }

        party.endPrompt()
        TradeGUI.show(player)
        TradeGUI.redraw(session)
        return true
    }

    companion object {

        /** Once a second: often enough that walking away ends a trade promptly, cheap enough to ignore. */
        private const val INTERVAL_TICKS = 20L

        /** How long after a trade opens before its menus are expected to be on screen. */
        private const val OPENING_GRACE_MILLIS = 2_000L

        /** How long a player may leave a question hanging in chat before the trade is called off. */
        private const val PROMPT_TIMEOUT_MILLIS = 60_000L
    }
}
