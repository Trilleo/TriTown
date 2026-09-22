package net.trilleo.mc.plugins.tritown.protection

import net.trilleo.mc.plugins.tritown.config.ProtectionSettings
import net.trilleo.mc.plugins.tritown.utils.ComponentUtil
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Where item protection applies, who is exempt from it, and how a refusal is
 * told to the player.
 *
 * Every listener asks [settings] for its own switch. It gets `null` when
 * protection is off entirely, or off in the world the event happened in.
 */
object Protection {

    /** Opening, taking from and breaking anything another player has claimed. */
    const val BYPASS_PERMISSION = "tritown.protection.bypass"

    /** `/tritown protection`: seeing and clearing who owns a container or entity. */
    const val ADMIN_PERMISSION = "tritown.protection.admin"

    private const val HINT_COOLDOWN_MS = 2_000L

    /** When each player was last told something is not theirs, so a busy click is not a busy chat. */
    private val lastHint = ConcurrentHashMap<UUID, Long>()

    /** The settings in force in [world], or `null` when nothing there is protected. */
    fun settings(world: World): ProtectionSettings? {
        if (!ProtectionSettings.isLoaded) return null
        val settings = ProtectionSettings.snapshot
        if (!settings.enabled || world.name.lowercase() in settings.disabledWorlds) return null
        return settings
    }

    fun bypasses(player: Player): Boolean = player.hasPermission(BYPASS_PERMISSION)

    /** Whether [player] may not touch something [owner] holds. */
    fun refuses(player: Player, owner: UUID?): Boolean =
        owner != null && owner != player.uniqueId && !bypasses(player)

    /** The player behind [entity]: the entity itself, or whoever fired it. */
    fun responsible(entity: Entity?): Player? = when (entity) {
        is Player -> entity
        is Projectile -> entity.shooter as? Player
        else -> null
    }

    /** A player's name, without ever asking Mojang: only the name the server already knows. */
    fun nameOf(owner: UUID): String = Bukkit.getOfflinePlayer(owner).name ?: owner.toString().take(8)

    /** Tells [player], in the action bar, that [owner] holds what they reached for. */
    fun hintOwned(player: Player, owner: UUID) {
        hint(player, player.tr("protection.hint.owned", "name" to ComponentUtil.escape(nameOf(owner))))
    }

    fun hint(player: Player, message: String) {
        val now = System.currentTimeMillis()
        val last = lastHint[player.uniqueId]
        if (last != null && now - last < HINT_COOLDOWN_MS) return
        lastHint[player.uniqueId] = now
        player.sendActionBar(ComponentUtil.parse(message))
    }

    fun forget(player: Player) {
        lastHint.remove(player.uniqueId)
    }
}
