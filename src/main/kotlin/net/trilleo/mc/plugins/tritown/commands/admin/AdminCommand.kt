package net.trilleo.mc.plugins.tritown.commands.admin

import net.trilleo.mc.plugins.tritown.guis.admin.AdminPanelGUI
import net.trilleo.mc.plugins.tritown.guis.admin.AdminShopsGUI
import net.trilleo.mc.plugins.tritown.guis.admin.EconomyPanelGUI
import net.trilleo.mc.plugins.tritown.registration.GUIManager
import net.trilleo.mc.plugins.tritown.registration.PluginCommand
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Opens the administration panel.
 *
 * The panel is where the server is read rather than changed, so the command
 * takes nothing but the section to land on — everything it shows is a menu, and
 * a section named here only saves a click.
 */
class AdminCommand : PluginCommand(
    name = "admin",
    description = "Open the admin panel",
    usage = "/tritown admin [economy|shops]",
    permission = AdminPanelGUI.PERMISSION,
) {

    override val extraPermissions = listOf(AdminPanelGUI.ECONOMY_PERMISSION, AdminPanelGUI.SHOPS_PERMISSION)

    override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
        val player = sender as? Player ?: run {
            sender.sendPrefixed(sender.tr("command.admin.players-only"))
            return true
        }

        val section = args.firstOrNull()?.lowercase()
        if (section != null && section !in SECTIONS) {
            player.sendPrefixed(player.tr("command.admin.unknown-section", "section" to section))
            return true
        }

        val permission = permissionFor(section)
        if (permission != null && !player.hasPermission(permission)) {
            player.sendPrefixed(player.tr("command.admin.no-permission-section", "section" to section.orEmpty()))
            return true
        }

        if (!GUIManager.open(player, guiFor(section))) {
            player.sendPrefixed(player.tr("command.admin.unavailable"))
        }
        return true
    }

    override fun tabComplete(sender: CommandSender, args: Array<out String>): List<String> =
        if (args.size == 1) {
            SECTIONS.filter {
                permissionFor(it)?.let(sender::hasPermission) != false && it.startsWith(args[0], ignoreCase = true)
            }
        } else {
            emptyList()
        }

    private fun guiFor(section: String?): String = when (section) {
        "economy" -> EconomyPanelGUI.ID
        "shops" -> AdminShopsGUI.ID
        else -> AdminPanelGUI.ID
    }

    private fun permissionFor(section: String?): String? = when (section) {
        "economy" -> AdminPanelGUI.ECONOMY_PERMISSION
        "shops" -> AdminPanelGUI.SHOPS_PERMISSION
        else -> null
    }

    private companion object {
        val SECTIONS = listOf("economy", "shops")
    }
}
