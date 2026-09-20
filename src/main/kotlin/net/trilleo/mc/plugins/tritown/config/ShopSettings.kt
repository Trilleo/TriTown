package net.trilleo.mc.plugins.tritown.config

import net.trilleo.mc.plugins.tritown.enums.TownyRequirement
import net.trilleo.mc.plugins.tritown.shops.ShopDefinition

/**
 * An immutable snapshot of the `shops` block of `config.yml`.
 *
 * Built whole and swapped in on a reload, the same way [EconomySettings] is, so
 * a shop being used while the configuration is re-read cannot see half of it.
 *
 * @param saveIntervalSeconds how often stock and statistics are written out; definitions save immediately
 * @param confirmAbove        the currency total that makes a purchase ask for confirmation first; 0 never asks
 * @param sellRate            what an entry pays back, as a fraction of its buy price, when no sell price was set
 * @param globalId            the id of the shop `/trades` opens, which is created empty when it does not exist
 * @param discounts           how much each standing in Towny takes off a price, as a fraction between 0 and 1
 */
data class ShopSettings(
    val enabled: Boolean,
    val saveIntervalSeconds: Long,
    val confirmAbove: Double,
    val sellRate: Double,
    val globalId: String,
    val discounts: Map<TownyRequirement, Double>,
) {

    companion object {

        /** What the global shop is called when `config.yml` does not say. */
        const val DEFAULT_GLOBAL_ID = "trades"

        /** The standings a discount can be attached to, by their key in `config.yml`. */
        private val DISCOUNT_KEYS = mapOf(
            "has-town" to TownyRequirement.HAS_TOWN,
            "has-nation" to TownyRequirement.HAS_NATION,
            "is-mayor" to TownyRequirement.IS_MAYOR,
            "is-king" to TownyRequirement.IS_KING,
        )

        @Volatile
        private var current: ShopSettings? = null

        /** The settings in force. */
        val snapshot: ShopSettings
            get() = current ?: error("Shop settings have not been loaded yet")

        /** Whether [load] has run. */
        val isLoaded: Boolean
            get() = current != null

        /** Reads the `shops` block from [config] and makes it the current snapshot. */
        fun load(config: PluginConfig): ShopSettings = read(config).also { current = it }

        /**
         * The id `/trades` opens.
         *
         * An id the shop registry would refuse is no use — the shop behind it
         * could never be created — so a malformed one falls back to the default
         * rather than leaving the command pointing at nothing.
         */
        private fun globalId(config: PluginConfig): String {
            val id = config.getString("shops.global-id", DEFAULT_GLOBAL_ID).lowercase()
            return if (ShopDefinition.isValidId(id)) id else DEFAULT_GLOBAL_ID
        }

        private fun read(config: PluginConfig): ShopSettings {
            val discounts = DISCOUNT_KEYS.mapNotNull { (key, requirement) ->
                val rate = config.getDouble("shops.discounts.$key", 0.0).coerceIn(0.0, 1.0)
                if (rate <= 0.0) null else requirement to rate
            }.toMap()

            return ShopSettings(
                enabled = config.getBoolean("shops.enabled", true),
                saveIntervalSeconds = config.getLong("shops.save-interval", 60L).coerceIn(5L, 3600L),
                confirmAbove = config.getDouble("shops.confirm-above", 0.0).coerceAtLeast(0.0),
                sellRate = config.getDouble("shops.sell-rate", 0.5).coerceIn(0.0, 1.0),
                globalId = globalId(config),
                discounts = discounts,
            )
        }
    }
}
