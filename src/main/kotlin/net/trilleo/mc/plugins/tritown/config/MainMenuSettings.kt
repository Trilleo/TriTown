package net.trilleo.mc.plugins.tritown.config

import org.bukkit.Material
import java.util.logging.Logger

/**
 * An immutable snapshot of the `main-menu` block of `config.yml`.
 *
 * @param itemEnabled  whether every player carries the menu item in the last slot of their hotbar
 * @param itemMaterial what the menu item is made of
 */
data class MainMenuSettings(
    val itemEnabled: Boolean,
    val itemMaterial: Material,
) {

    companion object {

        private val DEFAULT_MATERIAL = Material.NETHER_STAR

        @Volatile
        private var current: MainMenuSettings? = null

        /** The settings in force. */
        val snapshot: MainMenuSettings
            get() = current ?: error("Main menu settings have not been loaded yet")

        /** Whether [load] has run. */
        val isLoaded: Boolean
            get() = current != null

        /** Reads the `main-menu` block from [config] and makes it the current snapshot. */
        fun load(config: PluginConfig, logger: Logger): MainMenuSettings = read(config, logger).also { current = it }

        private fun read(config: PluginConfig, logger: Logger): MainMenuSettings = MainMenuSettings(
            itemEnabled = config.getBoolean("main-menu.item.enabled", true),
            itemMaterial = material(config.getString("main-menu.item.material", DEFAULT_MATERIAL.name), logger),
        )

        /** Air and blocks with no item form cannot sit in a hotbar, so they fall back to the default. */
        private fun material(name: String, logger: Logger): Material {
            val material = Material.matchMaterial(name)
            if (material != null && material.isItem && !material.isAir) return material

            logger.warning("main-menu.item.material '$name' is not an item; using ${DEFAULT_MATERIAL.name}")
            return DEFAULT_MATERIAL
        }
    }
}
