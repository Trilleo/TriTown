package net.trilleo.mc.plugins.tritown.commands.shop

import net.trilleo.mc.plugins.tritown.config.ShopSettings
import net.trilleo.mc.plugins.tritown.guis.shop.ShopEditorGUI
import net.trilleo.mc.plugins.tritown.guis.shop.ShopGUI
import net.trilleo.mc.plugins.tritown.guis.shop.ShopListGUI
import net.trilleo.mc.plugins.tritown.guis.shop.ShopStatsGUI
import net.trilleo.mc.plugins.tritown.registration.PluginCommand
import net.trilleo.mc.plugins.tritown.shops.ShopDefinition
import net.trilleo.mc.plugins.tritown.shops.ShopLimits
import net.trilleo.mc.plugins.tritown.shops.ShopManager
import net.trilleo.mc.plugins.tritown.shops.npc.ShopNpcBridge
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Sets shops up and puts NPCs behind their counters.
 *
 * Everything that describes a shop is edited in the menus rather than typed
 * here; this covers what a menu cannot do — creating and deleting a shop,
 * binding an NPC by name, and opening a shop for somebody from the console.
 */
class ShopCommand : PluginCommand(
    name = "shop",
    description = "Administer the server's shops",
    usage = "/tritown shop <list|create|delete|edit|open|bind|unbind|stats>",
    permission = PERMISSION,
) {

    override val extraPermissions = ACTIONS.map { permissionFor(it) }

    override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
        if (!ShopSettings.isLoaded || !ShopSettings.snapshot.enabled) {
            sender.sendPrefixed(sender.tr("command.shop.disabled"))
            return true
        }

        if (!ShopManager.isReady) {
            sender.sendPrefixed(sender.tr("command.shop.unavailable"))
            return true
        }

        val action = args.firstOrNull()?.lowercase()
        if (action == null || action !in ACTIONS) {
            sendUsage(sender)
            return true
        }

        if (!sender.hasPermission(permissionFor(action))) {
            sender.sendPrefixed(sender.tr("command.shop.no-permission-action", "action" to action))
            return true
        }

        when (action) {
            "list" -> list(sender)
            "create" -> create(sender, args)
            "delete" -> delete(sender, args)
            "edit" -> edit(sender, args)
            "open" -> open(sender, args)
            "bind" -> bind(sender, args)
            "unbind" -> unbind(sender, args)
            "stats" -> stats(sender, args)
        }
        return true
    }

    override fun tabComplete(sender: CommandSender, args: Array<out String>): List<String> = when (args.size) {
        1 -> ACTIONS.filter { sender.hasPermission(permissionFor(it)) && it.startsWith(args[0], ignoreCase = true) }

        2 -> when (args[0].lowercase()) {
            "unbind" -> ShopNpcBridge.names().filter { it.startsWith(args[1], ignoreCase = true) }
            "create" -> emptyList()
            else -> ShopManager.ids().filter { it.startsWith(args[1], ignoreCase = true) }
        }

        3 -> when (args[0].lowercase()) {
            "bind" -> ShopNpcBridge.names().filter { it.startsWith(args[2], ignoreCase = true) }
            "delete" -> listOf(CONFIRM).filter { it.startsWith(args[2], ignoreCase = true) }
            "open" -> Bukkit.getOnlinePlayers().map { it.name }.filter { it.startsWith(args[2], ignoreCase = true) }
            else -> emptyList()
        }

        else -> emptyList()
    }

    // ── Actions ─────────────────────────────────────────────────────────

    private fun list(sender: CommandSender) {
        val player = sender as? Player ?: return sender.sendPrefixed(sender.tr("command.shop.players-only"))
        ShopListGUI.show(player)
    }

    private fun create(sender: CommandSender, args: Array<out String>) {
        val id = args.getOrNull(1)
        if (id == null) {
            sender.sendPrefixed(sender.tr("command.shop.usage.create"))
            return
        }

        val name = args.drop(2).joinToString(" ").ifBlank { id }
        val shop = ShopManager.create(id, name)
        if (shop == null) {
            sender.sendPrefixed(sender.tr("command.shop.create-failed", "id" to id))
            return
        }

        sender.sendPrefixed(sender.tr("command.shop.created", "id" to shop.id))
        (sender as? Player)?.let { ShopEditorGUI.show(it, shop) }
    }

    /**
     * Deletes a shop, and only when the word is typed out.
     *
     * A shop can hold an afternoon of setting up and there is no undo, so the
     * confirmation is deliberately something that cannot be reached by pressing
     * tab twice.
     */
    private fun delete(sender: CommandSender, args: Array<out String>) {
        val shop = resolve(sender, args.getOrNull(1)) ?: return

        if (!args.getOrNull(2).equals(CONFIRM, ignoreCase = true)) {
            sender.sendPrefixed(sender.tr("command.shop.delete-confirm", "id" to shop.id))
            return
        }

        if (ShopManager.isGlobal(shop.id)) {
            sender.sendPrefixed(sender.tr("command.shop.delete-global", "id" to shop.id))
            return
        }

        Bukkit.getOnlinePlayers().forEach { ShopLimits.forget(it, shop) }
        ShopManager.delete(shop.id)
        sender.sendPrefixed(sender.tr("command.shop.deleted", "id" to shop.id))
    }

    private fun edit(sender: CommandSender, args: Array<out String>) {
        val player = sender as? Player ?: return sender.sendPrefixed(sender.tr("command.shop.players-only"))
        val shop = resolve(sender, args.getOrNull(1)) ?: return
        ShopEditorGUI.show(player, shop)
    }

    /** Opens a shop for somebody, so a menu can be checked without an NPC and the console can open one. */
    private fun open(sender: CommandSender, args: Array<out String>) {
        val shop = resolve(sender, args.getOrNull(1)) ?: return

        val target = args.getOrNull(2)?.let { Bukkit.getPlayerExact(it) } ?: sender as? Player
        if (target == null) {
            sender.sendPrefixed(sender.tr("command.shop.usage.open"))
            return
        }

        ShopGUI.show(target, shop)
        if (target !== sender) {
            sender.sendPrefixed(sender.tr("command.shop.opened", "id" to shop.id, "player" to target.name))
        }
    }

    private fun bind(sender: CommandSender, args: Array<out String>) {
        val shop = resolve(sender, args.getOrNull(1)) ?: return

        val npcName = args.getOrNull(2)
        if (npcName == null) {
            sender.sendPrefixed(sender.tr("command.shop.usage.bind"))
            return
        }

        if (!ShopNpcBridge.isAvailable) {
            sender.sendPrefixed(sender.tr("command.shop.npcs-unavailable", "plugin" to ShopNpcBridge.PLUGIN_NAME))
            return
        }

        val npcId = ShopNpcBridge.idOf(npcName)
        if (npcId == null) {
            sender.sendPrefixed(sender.tr("command.shop.npc-unknown", "npc" to npcName))
            return
        }

        // An NPC opens one shop, so binding it to another moves it rather than leaving it ambiguous.
        ShopManager.all().forEach { it.npcIds.remove(npcId) }
        shop.npcIds += npcId
        ShopManager.save()

        sender.sendPrefixed(sender.tr("command.shop.bound", "npc" to npcName, "id" to shop.id))
    }

    private fun unbind(sender: CommandSender, args: Array<out String>) {
        val npcName = args.getOrNull(1)
        if (npcName == null) {
            sender.sendPrefixed(sender.tr("command.shop.usage.unbind"))
            return
        }

        // Resolved through FancyNpcs when it can be, so an NPC that has since been deleted can still be unbound by id.
        val npcId = ShopNpcBridge.idOf(npcName) ?: npcName
        val shop = ShopManager.all().firstOrNull { npcId in it.npcIds }
        if (shop == null) {
            sender.sendPrefixed(sender.tr("command.shop.npc-not-bound", "npc" to npcName))
            return
        }

        shop.npcIds.remove(npcId)
        ShopManager.save()
        sender.sendPrefixed(sender.tr("command.shop.unbound", "npc" to npcName, "id" to shop.id))
    }

    private fun stats(sender: CommandSender, args: Array<out String>) {
        val player = sender as? Player ?: return sender.sendPrefixed(sender.tr("command.shop.players-only"))
        val shop = resolve(sender, args.getOrNull(1)) ?: return
        ShopStatsGUI.show(player, shop)
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private fun resolve(sender: CommandSender, id: String?): ShopDefinition? {
        if (id == null) {
            sendUsage(sender)
            return null
        }

        val shop = ShopManager.get(id)
        if (shop == null) sender.sendPrefixed(sender.tr("command.shop.unknown", "id" to id))
        return shop
    }

    private fun sendUsage(sender: CommandSender) {
        sender.sendPrefixed(sender.tr("command.shop.usage.header"))
        for (action in ACTIONS) {
            if (sender.hasPermission(permissionFor(action))) sender.sendPrefixed(usageFor(sender, action))
        }
    }

    /** Spelled out rather than built from the action, so every line is a key the translation test can see. */
    private fun usageFor(sender: CommandSender, action: String): String = when (action) {
        "list" -> sender.tr("command.shop.usage.list")
        "create" -> sender.tr("command.shop.usage.create")
        "delete" -> sender.tr("command.shop.usage.delete")
        "edit" -> sender.tr("command.shop.usage.edit")
        "open" -> sender.tr("command.shop.usage.open")
        "bind" -> sender.tr("command.shop.usage.bind")
        "unbind" -> sender.tr("command.shop.usage.unbind")
        else -> sender.tr("command.shop.usage.stats")
    }

    private companion object {
        const val PERMISSION = "tritown.shop.admin"
        const val CONFIRM = "confirm"

        val ACTIONS = listOf("list", "create", "delete", "edit", "open", "bind", "unbind", "stats")

        fun permissionFor(action: String): String = "$PERMISSION.$action"
    }
}
