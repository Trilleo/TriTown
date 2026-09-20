package net.trilleo.mc.plugins.tritown.trades

import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.trilleo.mc.plugins.tritown.Main
import net.trilleo.mc.plugins.tritown.config.TradeSettings
import net.trilleo.mc.plugins.tritown.utils.ComponentUtil
import net.trilleo.mc.plugins.tritown.utils.InventoryUtil
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Who has asked whom to trade, who is trading, and the one way a trade ends.
 *
 * A trade holds items that belong to a player, so the single rule everything
 * here is built around is that escrow is handed back exactly once: [cancel] is
 * the only way a trade ends without the swap happening, and [TradeSession.end]
 * makes it idempotent, because a player closing the menu at the moment the
 * other one disconnects would otherwise hand the same stacks back twice.
 *
 * Everything runs on the server thread — trades touch inventories — so the maps
 * are concurrent only to keep a stray read from another thread from tearing,
 * never to make two trades safe to settle at once.
 */
object TradeManager {

    /**
     * An outstanding request, and what the player who sent it called the player
     * they sent it to — kept so that a request lapsing while the other player
     * is offline can still name them.
     */
    private data class Pending(val sentAt: Long, val targetName: String)

    /** Who has asked whom, as target → requester → the request. */
    private val requests = ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, Pending>>()

    /** Both players of a trade point at the same session. */
    private val sessions = ConcurrentHashMap<UUID, TradeSession>()

    /** How long the same player's repeated click on another player is read as the same request. */
    private const val REPEAT_MILLIS = 3_000L

    /** Whether players may trade with one another at all. */
    val isEnabled: Boolean
        get() = TradeSettings.isLoaded && TradeSettings.snapshot.enabled

    /** The trade [uuid] is in, or `null` when they are not trading. */
    fun sessionOf(uuid: UUID): TradeSession? = sessions[uuid]

    /** Whether [uuid] is already in a trade. */
    fun isTrading(uuid: UUID): Boolean = sessions.containsKey(uuid)

    /** Every live trade, each one exactly once. */
    fun sessions(): List<TradeSession> = sessions.values.distinct()

    /** Whether [first] and [second] are close enough to trade. */
    fun inRange(first: Player, second: Player): Boolean {
        if (first.world != second.world) return false
        val limit = if (TradeSettings.isLoaded) TradeSettings.snapshot.distanceSquared else 100.0
        return first.location.distanceSquared(second.location) <= limit
    }

    // ── Requests ────────────────────────────────────────────────────────

    /**
     * Why [from] cannot ask [to] to trade right now, as a `command.trade.*`
     * key, or `null` when they can.
     *
     * The same answer whether the asking was a command or a click, so the two
     * ways in cannot drift apart.
     */
    fun refusal(from: Player, to: Player): String? = when {
        !isEnabled -> "command.trade.disabled"
        from.uniqueId == to.uniqueId -> "command.trade.self"
        !from.canSee(to) -> "command.trade.not-found"
        isTrading(from.uniqueId) -> "command.trade.you-busy"
        isTrading(to.uniqueId) -> "command.trade.busy"
        !inRange(from, to) -> "command.trade.too-far"
        else -> null
    }

    /**
     * Asks [to] to trade with [from], telling both players where things stand.
     *
     * A request repeated within a few seconds is ignored rather than announced
     * again: the client sends two packets for one right-click, and a player
     * clicking twice did not mean to send two.
     */
    fun request(from: Player, to: Player) {
        val now = System.currentTimeMillis()
        val pending = requests.computeIfAbsent(to.uniqueId) { ConcurrentHashMap() }
        val previous = pending[from.uniqueId]
        if (previous != null && now - previous.sentAt < REPEAT_MILLIS) return

        pending[from.uniqueId] = Pending(now, to.name)

        val seconds = if (TradeSettings.isLoaded) TradeSettings.snapshot.requestExpirySeconds else 60L
        from.sendPrefixed(from.tr("command.trade.sent", "name" to name(to), "seconds" to seconds))
        from.playSound(sound("minecraft:ui.button.click"))

        to.sendPrefixed(to.tr("command.trade.received", "name" to name(from)))
        to.playSound(sound("minecraft:entity.experience_orb.pickup"))
    }

    /** Whether [requester] has an outstanding request to [target]. */
    fun hasRequest(target: UUID, requester: UUID): Boolean = requests[target]?.containsKey(requester) == true

    /** Everyone with an outstanding request to [target]. */
    fun requestersFor(target: UUID): List<UUID> = requests[target]?.keys?.toList().orEmpty()

    /** Drops [requester]'s request to [target], and reports whether there was one. */
    fun dropRequest(target: UUID, requester: UUID): Boolean {
        val pending = requests[target] ?: return false
        val removed = pending.remove(requester) != null
        if (pending.isEmpty()) requests.remove(target)
        return removed
    }

    /** Drops every request [uuid] has sent or been sent. */
    fun dropRequests(uuid: UUID) {
        requests.remove(uuid)
        requests.values.forEach { it.remove(uuid) }
        requests.entries.removeIf { it.value.isEmpty() }
    }

    /** Lapses every request older than `player-trades.request-expiry`, telling the player who sent it. */
    fun expireRequests(now: Long) {
        val limit = if (TradeSettings.isLoaded) TradeSettings.snapshot.requestExpiryMillis else 60_000L

        for ((targetId, pending) in requests) {
            for ((requesterId, request) in pending) {
                if (now - request.sentAt < limit) continue
                pending.remove(requesterId)

                val requester = Bukkit.getPlayer(requesterId) ?: continue
                requester.sendPrefixed(
                    requester.tr("command.trade.expired", "name" to ComponentUtil.escape(request.targetName))
                )
            }
            if (pending.isEmpty()) requests.remove(targetId)
        }
    }

    // ── The trade itself ────────────────────────────────────────────────

    /**
     * Opens a trade between [first] and [second].
     *
     * Both are registered before either menu is drawn, so the first thing
     * either of them can click already sees a trade they are both in.
     */
    fun begin(first: Player, second: Player): TradeSession {
        dropRequests(first.uniqueId)
        dropRequests(second.uniqueId)

        val session = TradeSession(
            initiator = TradeParty(first.uniqueId, first.name),
            target = TradeParty(second.uniqueId, second.name),
            startedAt = System.currentTimeMillis(),
        )

        sessions[first.uniqueId] = session
        sessions[second.uniqueId] = session
        return session
    }

    /**
     * Calls [session] off, hands every escrowed item back and tells both
     * players why, with [reasonKey].
     *
     * [leaving] is the player object of someone who is disconnecting. Their
     * inventory is written before the server saves it, which is why they are
     * passed in rather than looked up: by the time the lookup would run they
     * may already be gone.
     */
    fun cancel(session: TradeSession, reasonKey: String, leaving: Player? = null) {
        if (!session.end()) return

        for (party in session.parties) {
            sessions.remove(party.uuid, session)

            val items = party.offer.drain()
            val player = leaving?.takeIf { it.uniqueId == party.uuid } ?: party.player

            if (player == null) {
                if (items.isNotEmpty()) {
                    Main.instance.logger.warning(
                        "Could not return ${items.size} escrowed stack(s) to ${party.name}: they are offline"
                    )
                }
                continue
            }

            InventoryUtil.give(player, items)
            // Not wrapped in the error colours: a trade called off because a
            // player closed the menu is not a mistake, it is the menu working.
            player.sendPrefixed(player.tr(reasonKey))
            player.playSound(sound("minecraft:entity.villager.no"))
            close(player)
        }
    }

    /**
     * Ends [session] after the swap has already happened.
     *
     * Nothing is handed back here: [TradeExchange] emptied both offers into the
     * other player as it went, so there is no escrow left to return.
     */
    fun finish(session: TradeSession) {
        if (!session.end()) return

        for (party in session.parties) {
            sessions.remove(party.uuid, session)

            val player = party.player ?: continue
            player.sendPrefixed(player.tr("trade.done", "name" to name(session.other(party))))
            player.playSound(sound("minecraft:entity.player.levelup"))
            close(player)
        }
    }

    /** Ends whatever [player] was part of as they disconnect. */
    fun handleQuit(player: Player) {
        dropRequests(player.uniqueId)
        sessions[player.uniqueId]?.let { cancel(it, "trade.cancelled.quit", leaving = player) }
    }

    /** Calls every live trade off, so no escrow is left holding items when the plugin stops. */
    fun shutdown() {
        sessions().forEach { cancel(it, "trade.cancelled.shutdown") }
        sessions.clear()
        requests.clear()
    }

    /** A player's name, escaped, because it is about to be embedded in MiniMessage. */
    fun name(player: Player): String = ComponentUtil.escape(player.name)

    /** A party's name, escaped, for a message about someone who may have left. */
    fun name(party: TradeParty): String = ComponentUtil.escape(party.player?.name ?: party.name)

    /**
     * Closes [player]'s menu on the following tick.
     *
     * A trade is usually called off from inside a click or a close of the very
     * menu being shut, and closing an inventory while one of those is still
     * being delivered leaves the server and the client disagreeing about what
     * is on screen.
     */
    private fun close(player: Player) {
        // The scheduler refuses work from a plugin that is already stopping, and
        // a shutdown is not inside a click anyway, so it closes straight away.
        if (!Main.instance.isEnabled) {
            player.closeInventory()
            return
        }
        Bukkit.getScheduler().runTask(Main.instance, Runnable { player.closeInventory() })
    }

    private fun sound(key: String): Sound = Sound.sound(Key.key(key), Sound.Source.UI, 1f, 1f)
}
