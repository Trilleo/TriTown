package net.trilleo.mc.plugins.tritown.config

/**
 * An immutable snapshot of the `towns` block of `config.yml`.
 *
 * @param foundingCredit how much of a first town's price TriTown covers; 0 turns the credit off
 */
data class TownSettings(
    val foundingCredit: Double,
) {

    companion object {

        @Volatile
        private var current: TownSettings? = null

        /** The settings in force. */
        val snapshot: TownSettings
            get() = current ?: error("Town settings have not been loaded yet")

        /** Reads the `towns` block from [config] and makes it the current snapshot. */
        fun load(config: PluginConfig): TownSettings = read(config).also { current = it }

        private fun read(config: PluginConfig): TownSettings = TownSettings(
            foundingCredit = config.getDouble("towns.founding-credit", 100.0).coerceAtLeast(0.0),
        )
    }
}
