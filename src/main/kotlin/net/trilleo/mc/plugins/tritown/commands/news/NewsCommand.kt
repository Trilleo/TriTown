package net.trilleo.mc.plugins.tritown.commands.news

import net.trilleo.mc.plugins.tritown.guis.news.NewsListGUI
import net.trilleo.mc.plugins.tritown.guis.news.NewsManageGUI
import net.trilleo.mc.plugins.tritown.guis.news.NewsPostGUI
import net.trilleo.mc.plugins.tritown.news.NewsManager
import net.trilleo.mc.plugins.tritown.news.NewsReadState
import net.trilleo.mc.plugins.tritown.registration.PluginCommand
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * The server's news: read it, catch up on it, and — for administrators — write it.
 *
 * It declares no permission on purpose, the way `/tritown menu` does: every
 * player is meant to read the news. Writing it is checked against
 * [MANAGE_PERMISSION], which the editor menus check as well.
 *
 * `open <id>` is what the links in the join summary and the announcement run.
 */
class NewsCommand : PluginCommand(
    name = "news",
    description = "Read the server's news",
    usage = "/tritown news [open <id>|readall|manage]",
) {

    override val extraPermissions = listOf(MANAGE_PERMISSION)

    override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
        val player = sender as? Player ?: run {
            sender.sendPrefixed(sender.tr("command.news.players-only"))
            return true
        }

        if (!NewsManager.isAvailable) {
            player.sendPrefixed(player.tr("command.news.unavailable"))
            return true
        }

        when (args.firstOrNull()?.lowercase()) {
            null -> NewsListGUI.show(player)
            "open" -> open(player, args.getOrNull(1))
            "readall" -> {
                NewsReadState.markAllRead(player)
                player.sendPrefixed(player.tr("command.news.all-read"))
            }

            "manage" -> {
                if (!player.hasPermission(MANAGE_PERMISSION)) {
                    player.sendPrefixed(player.tr("command.no-permission"))
                } else {
                    NewsManageGUI.show(player)
                }
            }

            else -> player.sendPrefixed(player.tr("command.news.usage"))
        }
        return true
    }

    override fun tabComplete(sender: CommandSender, args: Array<out String>): List<String> = when (args.size) {
        1 -> actions(sender).filter { it.startsWith(args[0], ignoreCase = true) }
        2 -> if (args[0].equals("open", ignoreCase = true)) {
            NewsManager.published().map { it.id }.filter { it.startsWith(args[1], ignoreCase = true) }
        } else {
            emptyList()
        }

        else -> emptyList()
    }

    /** A draft opens only for an editor, and then as a preview, so a link to one never shows it to a player. */
    private fun open(player: Player, id: String?) {
        val post = id?.let(NewsManager::get)
        when {
            post == null -> player.sendPrefixed(player.tr("command.news.unknown"))
            post.isPublished -> NewsPostGUI.show(player, post, preview = false)
            player.hasPermission(MANAGE_PERMISSION) -> NewsPostGUI.show(player, post, preview = true)
            else -> player.sendPrefixed(player.tr("command.news.unknown"))
        }
    }

    private fun actions(sender: CommandSender): List<String> =
        if (sender.hasPermission(MANAGE_PERMISSION)) listOf("open", "readall", "manage") else listOf("open", "readall")

    companion object {
        /** Needed to write, publish and delete news. */
        const val MANAGE_PERMISSION = "tritown.news.manage"
    }
}
