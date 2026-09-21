package net.trilleo.mc.plugins.tritown.commands.storage

import net.trilleo.mc.plugins.tritown.economy.EconomyService
import net.trilleo.mc.plugins.tritown.enums.AccountType
import net.trilleo.mc.plugins.tritown.guis.storage.StorageGUI
import net.trilleo.mc.plugins.tritown.registration.PluginCommand
import net.trilleo.mc.plugins.tritown.storage.StorageManager
import net.trilleo.mc.plugins.tritown.utils.ComponentUtil
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.*

/**
 * Opening your storage, and — for administrators — anyone else's.
 *
 * It declares no permission on purpose, the way `/tritown menu` does: every
 * player has a storage, and whether the feature runs at all is
 * `storage.enabled` in the configuration rather than a node. Looking into or
 * changing another player's storage needs [ADMIN_PERMISSION].
 */
class StorageCommand : PluginCommand(
    name = "storage",
    description = "Open your storage",
    usage = "/tritown storage [view <player>|pages <player> <add|set> <amount>]",
) {

    override val extraPermissions = listOf(ADMIN_PERMISSION, BYPASS_PERMISSION)

    override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
        if (!StorageManager.isAvailable) {
            sender.sendPrefixed(error(sender, "storage.error.unavailable"))
            return true
        }

        when (args.firstOrNull()?.lowercase()) {
            null -> {
                val player = sender as? Player ?: run {
                    sender.sendPrefixed(sender.tr("command.storage.players-only"))
                    return true
                }
                StorageGUI.open(player, player.uniqueId)
            }

            VIEW -> if (admin(sender)) view(sender, args.getOrNull(1))
            PAGES -> if (admin(sender)) pages(sender, args)
            else -> sender.sendPrefixed(sender.tr("command.storage.usage"))
        }
        return true
    }

    override fun tabComplete(sender: CommandSender, args: Array<out String>): List<String> {
        if (!sender.hasPermission(ADMIN_PERMISSION)) return emptyList()
        val options = when (args.size) {
            1 -> listOf(VIEW, PAGES)
            2 -> Bukkit.getOnlinePlayers().map { it.name }
            3 -> if (args[0].equals(PAGES, ignoreCase = true)) listOf(ADD, SET) else emptyList()
            else -> emptyList()
        }
        return options.filter { it.startsWith(args.last(), ignoreCase = true) }
    }

    /** Opens [name]'s storage, online or not, for the administrator who asked. */
    private fun view(sender: CommandSender, name: String?) {
        val player = sender as? Player ?: run {
            sender.sendPrefixed(sender.tr("command.storage.players-only"))
            return
        }
        val owner = name?.let(::resolve) ?: run {
            player.sendPrefixed(error(player, "command.storage.not-found", "name" to ComponentUtil.escape(name.orEmpty())))
            return
        }
        StorageGUI.open(player, owner)
    }

    /** Gives a player pages for free, or sets how many they own. */
    private fun pages(sender: CommandSender, args: Array<out String>) {
        val name = args.getOrNull(1)
        val mode = args.getOrNull(2)?.lowercase()
        val amount = args.getOrNull(3)?.toIntOrNull()
        if (name == null || mode !in setOf(ADD, SET) || amount == null) {
            sender.sendPrefixed(sender.tr("command.storage.usage"))
            return
        }

        val owner = resolve(name) ?: run {
            sender.sendPrefixed(error(sender, "command.storage.not-found", "name" to ComponentUtil.escape(name)))
            return
        }
        val storage = StorageManager.get(owner) ?: run {
            sender.sendPrefixed(error(sender, "storage.error.unreadable"))
            return
        }

        val target = if (mode == ADD) StorageManager.owned(storage) + amount else amount
        StorageManager.setOwned(storage, target)
        StorageManager.unloadIfIdle(owner)

        sender.sendPrefixed(
            sender.tr(
                "command.storage.pages-set",
                "name" to ComponentUtil.escape(name),
                "amount" to StorageManager.owned(storage),
            )
        )
    }

    /**
     * The player called [name], without ever asking Mojang: someone online, someone
     * the server has cached, or someone TriTown's economy has an account for.
     */
    private fun resolve(name: String): UUID? =
        Bukkit.getPlayerExact(name)?.uniqueId
            ?: Bukkit.getOfflinePlayerIfCached(name)?.uniqueId
            ?: EconomyService.resolveByName(name)?.takeIf { it.type == AccountType.PLAYER }?.uuid

    private fun admin(sender: CommandSender): Boolean {
        if (sender.hasPermission(ADMIN_PERMISSION)) return true
        sender.sendPrefixed(sender.tr("command.no-permission"))
        return false
    }

    private fun error(sender: CommandSender, key: String, vararg args: Pair<String, Any?>): String =
        sender.tr("common.error", "message" to sender.tr(key, *args))

    companion object {
        /** Opening, changing and handing out pages of any player's storage. */
        const val ADMIN_PERMISSION = "tritown.storage.admin"

        /** Placing and filling the vanilla containers the storage replaces. */
        const val BYPASS_PERMISSION = "tritown.storage.bypass"

        private const val VIEW = "view"
        private const val PAGES = "pages"
        private const val ADD = "add"
        private const val SET = "set"
    }
}
