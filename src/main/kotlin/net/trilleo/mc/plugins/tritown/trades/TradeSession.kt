package net.trilleo.mc.plugins.tritown.trades

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID

/** One player's side of a live trade: what they have put up, and whether they have agreed to it. */
class TradeParty(val uuid: UUID, val name: String) {

    /** What this player has put on the table. */
    val offer = TradeOffer()

    /** Whether this player has agreed to the trade as it currently stands. */
    var confirmed = false

    /**
     * When this player was sent to chat to type an amount, or `0` when they are
     * at the menu.
     *
     * A chest menu has nowhere to type, so naming an exact amount means closing
     * it — and a closed trade menu is otherwise a cancelled trade. This is what
     * tells the two apart, and what the other side is shown instead of a
     * confirmation while it is set.
     */
    var promptingSince: Long = 0L

    /**
     * When this player stopped having a question waiting without an answer
     * reaching the menu, or `0`.
     *
     * Backing out of the question and answering it look the same for the one
     * tick between the answer being taken and the callback running, so a player
     * is only treated as having walked away once they have looked idle twice.
     */
    var promptIdleSince: Long = 0L

    /** Whether this player is away in chat naming an amount. */
    val isPrompting: Boolean
        get() = promptingSince != 0L

    /** Sends this player off to chat at [now]. */
    fun beginPrompt(now: Long) {
        promptingSince = now
        promptIdleSince = 0L
    }

    /** Brings this player back to the menu. */
    fun endPrompt() {
        promptingSince = 0L
        promptIdleSince = 0L
    }

    /** This player, or `null` when they are no longer online. */
    val player: Player?
        get() = Bukkit.getPlayer(uuid)
}

/**
 * A trade between two players, from the moment both agreed to open it until it
 * goes through or is called off.
 *
 * Nothing here moves items or money; it is the state both menus are drawn from
 * and the rules about when a confirmation counts. [TradeManager] owns the
 * lifecycle and [TradeExchange] does the swap.
 *
 * Every change to either offer goes through [touch], which is what makes the
 * confirmations mean something: they only ever describe the table as it was at
 * the moment they were given.
 */
class TradeSession(val initiator: TradeParty, val target: TradeParty, val startedAt: Long) {

    private var over = false

    /** Until when a confirmation is refused, because the table just changed. */
    var lockedUntil: Long = 0L
        private set

    /** Both sides, initiator first. */
    val parties: List<TradeParty>
        get() = listOf(initiator, target)

    /** Whether both players have agreed to the trade as it stands. */
    val bothConfirmed: Boolean
        get() = initiator.confirmed && target.confirmed

    /** The side [uuid] is on, or `null` when they are not in this trade. */
    fun partyOf(uuid: UUID): TradeParty? = when (uuid) {
        initiator.uuid -> initiator
        target.uuid -> target
        else -> null
    }

    /** The side opposite [party]. */
    fun other(party: TradeParty): TradeParty = if (party === initiator) target else initiator

    /**
     * Whether a confirmation would be refused at [now].
     *
     * A click takes a moment to arrive, so without this a player could change
     * what is on the table in the instant between the other side deciding and
     * the server hearing about it.
     */
    fun isLocked(now: Long): Boolean = now < lockedUntil

    /**
     * Records that something on the table changed at [now].
     *
     * Both confirmations are dropped, because neither of them describes this
     * table any more, and the lock is started so that the change cannot be
     * beaten by a click already on its way.
     */
    fun touch(now: Long) {
        initiator.confirmed = false
        target.confirmed = false
        lockedUntil = now + LOCK_MILLIS
    }

    /**
     * Marks the trade finished.
     *
     * @return `false` when it already was, so that a trade cancelled from two
     *   places at once — a player closing the menu as the other one quits —
     *   only hands its escrow back once
     */
    fun end(): Boolean {
        if (over) return false
        over = true
        return true
    }

    companion object {

        /** How long a confirmation is refused after the table changes. */
        const val LOCK_MILLIS = 1_000L
    }
}
