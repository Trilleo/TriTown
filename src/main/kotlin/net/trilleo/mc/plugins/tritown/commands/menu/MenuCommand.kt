package net.trilleo.mc.plugins.tritown.commands.menu

import net.trilleo.mc.plugins.tritown.guis.menu.MainMenuGUI
import net.trilleo.mc.plugins.tritown.registration.PluginCommand
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Opens the main menu, for a player who would rather type than reach for the
 * item — or on a server that has switched the item off.
 *
 * It declares no permission on purpose, the way `/tritown scoreboard` does:
 * every player is meant to reach the menu, and the menu itself leaves out what
 * a viewer may not use.
 */
class MenuCommand : PluginCommand(
    name = "menu",
    description = "Open the main menu",
    usage = "/tritown menu",
) {

    override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
        val player = sender as? Player ?: run {
            sender.sendPrefixed(sender.tr("command.menu.players-only"))
            return true
        }

        MainMenuGUI.show(player)
        return true
    }

    override fun tabComplete(sender: CommandSender, args: Array<out String>): List<String> = emptyList()
}
