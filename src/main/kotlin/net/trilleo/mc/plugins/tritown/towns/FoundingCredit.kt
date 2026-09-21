package net.trilleo.mc.plugins.tritown.towns

import com.palmergames.bukkit.towny.TownyAPI
import com.palmergames.bukkit.towny.`object`.Town
import net.trilleo.mc.plugins.tritown.config.TownSettings
import net.trilleo.mc.plugins.tritown.data.PlayerDataManager
import net.trilleo.mc.plugins.tritown.utils.ComponentUtil
import net.trilleo.mc.plugins.tritown.utils.EconomyUtil
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Money set aside for a player's first town, which nothing else can spend.
 *
 * The credit is never paid out. It is taken off the price in Towny's
 * `PreNewTownEvent`, so Towny still runs its own checks, confirmation and
 * charge, and only charges what the credit does not cover. Because no money
 * moves, the economy statistics have nothing to record. Founding just drains
 * less.
 *
 * A player is offered it once. Whether they hold it is kept in their
 * [net.trilleo.mc.plugins.tritown.data.PlayerData], and the amount is read from
 * the config each time, so an owner who changes it changes it for everyone
 * still holding one.
 */
object FoundingCredit {

    /** What a check on a player changed. */
    enum class Change { GRANTED, USED, FORFEITED }

    private const val KEY = "founding-credit"
    private const val HELD = "held"
    private const val USED = "used"
    private const val FORFEITED = "forfeited"
    private const val INELIGIBLE = "ineligible"

    /** The town each player was last quoted a reduced price for. */
    private val founding = ConcurrentHashMap<UUID, String>()

    /** The credit's value, or 0 when it is switched off. */
    val amount: Double get() = TownSettings.snapshot.foundingCredit

    /** Whether [player] has a credit to spend. */
    fun holds(player: Player): Boolean = amount > 0.0 && state(player) == HELD

    /**
     * Grants the credit to a player who has never been offered it and has no
     * town, and takes it from one who joined a town while offline.
     */
    fun review(player: Player): Change? {
        if (amount <= 0.0) return null
        val hasTown = TownyAPI.getInstance().getResident(player)?.hasTown() == true

        return when (state(player)) {
            null -> if (hasTown) {
                setState(player, INELIGIBLE)
                null
            } else {
                setState(player, HELD)
                Change.GRANTED
            }

            HELD -> if (hasTown) {
                setState(player, FORFEITED)
                Change.FORFEITED
            } else {
                null
            }

            else -> null
        }
    }

    /**
     * How much of [price] the credit covers when [player] founds [townName].
     *
     * Nothing is spent here: Towny has not asked for confirmation yet, and the
     * player may never give it. The town is remembered so [settle] can tell
     * this founding apart from joining someone else's town.
     */
    fun cover(player: Player, townName: String, price: Double): Double {
        if (price <= 0.0 || !holds(player)) return 0.0
        founding[player.uniqueId] = townName
        return minOf(amount, price)
    }

    /**
     * Settles the credit once [player] has become a member of [town].
     *
     * Towny adds the founder as the first resident part-way through creating
     * the town, so this one event sees both outcomes. A town that is new and
     * has only them is the one the credit was quoted for. Any other town gives
     * the credit up.
     */
    fun settle(player: Player, town: Town): Change? {
        val quoted = founding.remove(player.uniqueId)
        if (!holds(player)) return null

        val founded = quoted.equals(town.name, ignoreCase = true) && town.numResidents == 1
        setState(player, if (founded) USED else FORFEITED)
        return if (founded) Change.USED else Change.FORFEITED
    }

    /** Drops the quote held for [player], who is leaving. */
    fun forget(player: Player) {
        founding.remove(player.uniqueId)
    }

    /** [amount] of money as text that is safe inside MiniMessage. */
    fun display(amount: Double = this.amount): String =
        if (EconomyUtil.isAvailable) ComponentUtil.escape(EconomyUtil.format(amount)) else amount.toString()

    private fun state(player: Player): String? =
        PlayerDataManager.get(player).let { if (it.has(KEY)) it.getString(KEY) else null }

    private fun setState(player: Player, state: String) {
        PlayerDataManager.get(player).set(KEY, state)
    }
}
