package net.trilleo.mc.plugins.tritown.commands.shop

import net.trilleo.mc.plugins.tritown.config.ShopSettings
import net.trilleo.mc.plugins.tritown.guis.shop.ShopGUI
import net.trilleo.mc.plugins.tritown.registration.PluginCommand
import net.trilleo.mc.plugins.tritown.shops.ShopManager
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * The server's own shop, open from anywhere.
 *
 * Every other shop stands behind an NPC somebody has to walk to, which is the
 * point of them — a shop is a place. This one is the exception: the goods the
 * server always trades, reachable wherever a player is standing.
 *
 * It declares no permission on purpose, the way `/tritown scoreboard` does.
 * What a player may see and buy in it is the shop's own gate's business, which
 * an administrator sets in the shop's settings like any other.
 */
class TradesCommand : PluginCommand(
    name = "trades",
    description = "Open the server's global shop",
    usage = "/trades",
    isMainCommand = true,
) {

    override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
        val player = sender as? Player ?: run {
            sender.sendPrefixed(sender.tr("command.trades.players-only"))
            return true
        }

        if (!ShopSettings.isLoaded || !ShopSettings.snapshot.enabled) {
            player.sendPrefixed(player.tr("command.trades.disabled"))
            return true
        }

        val shop = ShopManager.global()
        if (shop == null) {
            player.sendPrefixed(player.tr("command.trades.unavailable"))
            return true
        }

        ShopGUI.show(player, shop)
        return true
    }

    override fun tabComplete(sender: CommandSender, args: Array<out String>): List<String> = emptyList()
}
