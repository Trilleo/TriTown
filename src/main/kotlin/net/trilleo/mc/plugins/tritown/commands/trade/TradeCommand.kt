package net.trilleo.mc.plugins.tritown.commands.trade

import net.trilleo.mc.plugins.tritown.guis.trade.TradeGUI
import net.trilleo.mc.plugins.tritown.registration.PluginCommand
import net.trilleo.mc.plugins.tritown.trades.TradeManager
import net.trilleo.mc.plugins.tritown.utils.ComponentUtil
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Asking another player to trade, and answering someone who has asked.
 *
 * The same thing shift-right-clicking them does, for a player who would rather
 * type it — so the rules are the same too, down to having to be standing next
 * to each other. Nothing opens until the other player agrees.
 *
 * It declares no permission on purpose, the way `/trades` does: trading is
 * something every player does, and whether it happens at all is
 * `player-trades.enabled` in the configuration rather than a node.
 */
class TradeCommand : PluginCommand(
    name = "trade",
    description = "Ask another player to trade",
    usage = "/trade <player>",
    isMainCommand = true,
) {

    override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
        val player = sender as? Player ?: run {
            sender.sendPrefixed(sender.tr("command.trade.players-only"))
            return true
        }

        if (!TradeManager.isEnabled) {
            player.sendPrefixed(error(player, "command.trade.disabled"))
            return true
        }

        val first = args.firstOrNull() ?: run {
            player.sendPrefixed(player.tr("command.trade.usage"))
            return true
        }

        when (first.lowercase()) {
            ACCEPT -> answer(player, args.getOrNull(1), accept = true)
            DENY -> answer(player, args.getOrNull(1), accept = false)
            else -> ask(player, first)
        }
        return true
    }

    override fun tabComplete(sender: CommandSender, args: Array<out String>): List<String> {
        val player = sender as? Player ?: return emptyList()

        return when (args.size) {
            1 -> (listOf(ACCEPT, DENY) + nearby(player)).filter { it.startsWith(args[0], ignoreCase = true) }
            2 -> if (args[0].lowercase() in setOf(ACCEPT, DENY)) {
                requesters(player).filter { it.startsWith(args[1], ignoreCase = true) }
            } else {
                emptyList()
            }

            else -> emptyList()
        }
    }

    // ── Asking ──────────────────────────────────────────────────────────

    private fun ask(player: Player, name: String) {
        val target = Bukkit.getPlayerExact(name)?.takeIf { player.canSee(it) } ?: run {
            player.sendPrefixed(error(player, "command.trade.not-found", name))
            return
        }

        // Answering someone who has already asked, rather than asking them back
        // and leaving the two requests waiting for each other.
        if (TradeManager.hasRequest(player.uniqueId, target.uniqueId)) {
            open(player, target)
            return
        }

        TradeManager.refusal(player, target)?.let { key ->
            player.sendPrefixed(error(player, key, target.name))
            return
        }

        TradeManager.request(player, target)
    }

    // ── Answering ───────────────────────────────────────────────────────

    /**
     * Answers [name]'s request, or the only outstanding one when no name was
     * given.
     */
    private fun answer(player: Player, name: String?, accept: Boolean) {
        val waiting = requesters(player)
        val from = when {
            name != null -> name
            waiting.size == 1 -> waiting.first()
            waiting.isEmpty() -> {
                player.sendPrefixed(error(player, "command.trade.no-requests"))
                return
            }

            else -> {
                player.sendPrefixed(
                    player.tr(
                        "command.trade.which-request",
                        "names" to waiting.joinToString(", ") { ComponentUtil.escape(it) },
                    )
                )
                return
            }
        }

        val requester = Bukkit.getPlayerExact(from) ?: run {
            player.sendPrefixed(error(player, "command.trade.not-found", from))
            return
        }

        if (!TradeManager.hasRequest(player.uniqueId, requester.uniqueId)) {
            player.sendPrefixed(error(player, "command.trade.no-request", requester.name))
            return
        }

        if (!accept) {
            TradeManager.dropRequest(player.uniqueId, requester.uniqueId)
            player.sendPrefixed(player.tr("command.trade.denied", "name" to TradeManager.name(requester)))
            requester.sendPrefixed(
                requester.tr("command.trade.denied-by", "name" to TradeManager.name(player))
            )
            return
        }

        open(player, requester)
    }

    /** Opens the trade, checking one last time that both players are still able to. */
    private fun open(player: Player, other: Player) {
        TradeManager.dropRequest(player.uniqueId, other.uniqueId)

        TradeManager.refusal(player, other)?.let { key ->
            player.sendPrefixed(error(player, key, other.name))
            return
        }

        TradeManager.begin(other, player)
        TradeGUI.show(other)
        TradeGUI.show(player)
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    /** Everyone whose request [player] has not answered yet and who is still online. */
    private fun requesters(player: Player): List<String> =
        TradeManager.requestersFor(player.uniqueId).mapNotNull { Bukkit.getPlayer(it)?.name }

    /** Everyone [player] could ask right now, which is everyone close enough to trade. */
    private fun nearby(player: Player): List<String> = Bukkit.getOnlinePlayers()
        .filter { it.uniqueId != player.uniqueId && player.canSee(it) && TradeManager.inRange(player, it) }
        .map { it.name }

    /** A refusal in the plugin's error colours, with [name] escaped because a player typed it. */
    private fun error(player: Player, key: String, name: String = ""): String =
        player.tr("common.error", "message" to player.tr(key, "name" to ComponentUtil.escape(name)))

    private companion object {
        const val ACCEPT = "accept"
        const val DENY = "deny"
    }
}
