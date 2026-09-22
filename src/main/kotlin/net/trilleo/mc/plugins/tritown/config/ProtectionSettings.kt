package net.trilleo.mc.plugins.tritown.config

/**
 * An immutable snapshot of the `item-protection` block of `config.yml`.
 *
 * Built whole and swapped in on a reload, the same way [TradeSettings] is, so a
 * hopper moving items while the configuration is re-read cannot see half of it.
 *
 * @param enabled        whether items are protected at all
 * @param disabledWorlds worlds, by name, where nothing is protected
 * @param drops          whether what a player drops belongs to them
 * @param actions        whether what a player mines, harvests, shears or fishes up belongs to them
 * @param mobLoot        whether a mob's loot belongs to the player who killed it
 * @param projectiles    whether only the shooter may pick an arrow or trident back up
 * @param containers     whether a container belongs to whoever fills it, for as long as it holds anything
 * @param entities       whether item frames, armor stands, allays and equipped mobs belong to whoever filled them
 * @param explosionGuard whether explosions leave claimed containers standing
 */
data class ProtectionSettings(
    val enabled: Boolean,
    val disabledWorlds: Set<String>,
    val drops: Boolean,
    val actions: Boolean,
    val mobLoot: Boolean,
    val projectiles: Boolean,
    val containers: Boolean,
    val entities: Boolean,
    val explosionGuard: Boolean,
) {

    companion object {

        @Volatile
        private var current: ProtectionSettings? = null

        /** The settings in force. */
        val snapshot: ProtectionSettings
            get() = current ?: error("Item protection settings have not been loaded yet")

        /** Whether [load] has run. */
        val isLoaded: Boolean
            get() = current != null

        /** Reads the `item-protection` block from [config] and makes it the current snapshot. */
        fun load(config: PluginConfig): ProtectionSettings = read(config).also { current = it }

        private fun read(config: PluginConfig): ProtectionSettings = ProtectionSettings(
            enabled = config.getBoolean("item-protection.enabled", true),
            disabledWorlds = config.getStringList("item-protection.disabled-worlds").map { it.lowercase() }.toSet(),
            drops = config.getBoolean("item-protection.drops", true),
            actions = config.getBoolean("item-protection.actions", true),
            mobLoot = config.getBoolean("item-protection.mob-loot", true),
            projectiles = config.getBoolean("item-protection.projectiles", true),
            containers = config.getBoolean("item-protection.containers", true),
            entities = config.getBoolean("item-protection.entities", true),
            explosionGuard = config.getBoolean("item-protection.explosion-guard", true),
        )
    }
}
