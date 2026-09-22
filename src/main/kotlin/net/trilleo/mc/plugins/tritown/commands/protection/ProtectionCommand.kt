package net.trilleo.mc.plugins.tritown.commands.protection

import net.trilleo.mc.plugins.tritown.protection.ClaimHolder
import net.trilleo.mc.plugins.tritown.protection.Claims
import net.trilleo.mc.plugins.tritown.protection.Protection
import net.trilleo.mc.plugins.tritown.registration.PluginCommand
import net.trilleo.mc.plugins.tritown.utils.ComponentUtil
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Who owns the container or entity an administrator is looking at, and
 * clearing that claim, say when a player left a furnace full and quit for good.
 */
class ProtectionCommand : PluginCommand(
    name = "protection",
    description = "See or clear who owns a container or entity",
    usage = "/tritown protection <inspect|release>",
    permission = Protection.ADMIN_PERMISSION,
) {

    override val extraPermissions = listOf(Protection.BYPASS_PERMISSION)

    override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
        val player = sender as? Player ?: run {
            sender.sendPrefixed(sender.tr("command.protection.players-only"))
            return true
        }
        val action = args.firstOrNull()?.lowercase()
        if (action != INSPECT && action != RELEASE) {
            player.sendPrefixed(player.tr("command.protection.usage"))
            return true
        }

        val holder = target(player) ?: run {
            player.sendPrefixed(player.tr("common.error", "message" to player.tr("command.protection.no-target")))
            return true
        }
        val owner = Claims.ownerOf(holder)

        when {
            owner == null -> player.sendPrefixed(player.tr("command.protection.public"))
            action == INSPECT -> player.sendPrefixed(
                player.tr("command.protection.owner", "name" to ComponentUtil.escape(Protection.nameOf(owner)))
            )

            else -> {
                Claims.release(holder)
                player.sendPrefixed(
                    player.tr("command.protection.released", "name" to ComponentUtil.escape(Protection.nameOf(owner)))
                )
            }
        }
        return true
    }

    override fun tabComplete(sender: CommandSender, args: Array<out String>): List<String> =
        if (args.size == 1) listOf(INSPECT, RELEASE).filter { it.startsWith(args[0], ignoreCase = true) } else emptyList()

    /** The entity in the crosshair if there is one, the block otherwise. */
    private fun target(player: Player): ClaimHolder? =
        player.getTargetEntity(REACH)?.let(Claims::of)
            ?: player.getTargetBlockExact(REACH)?.let(Claims::of)

    private companion object {
        const val INSPECT = "inspect"
        const val RELEASE = "release"
        const val REACH = 6
    }
}
