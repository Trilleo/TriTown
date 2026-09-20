package net.trilleo.mc.plugins.tritown.config

/**
 * An immutable snapshot of the `player-trades` block of `config.yml`.
 *
 * Built whole and swapped in on a reload, the same way [ShopSettings] is, so a
 * trade being carried out while the configuration is re-read cannot see half of
 * it.
 *
 * @param enabled              whether players may trade with one another at all
 * @param distance             how many blocks apart the two players may be, when asking and for as long as the menu is open
 * @param requestExpirySeconds how long an unanswered request stands before it lapses
 */
data class TradeSettings(
    val enabled: Boolean,
    val distance: Double,
    val requestExpirySeconds: Long,
) {

    /** Compared against `Location.distanceSquared`, so a square root is never taken to check a range. */
    val distanceSquared: Double = distance * distance

    /** [requestExpirySeconds] in the milliseconds every timestamp in the trade code is kept in. */
    val requestExpiryMillis: Long = requestExpirySeconds * 1_000L

    companion object {

        @Volatile
        private var current: TradeSettings? = null

        /** The settings in force. */
        val snapshot: TradeSettings
            get() = current ?: error("Trade settings have not been loaded yet")

        /** Whether [load] has run. */
        val isLoaded: Boolean
            get() = current != null

        /** Reads the `player-trades` block from [config] and makes it the current snapshot. */
        fun load(config: PluginConfig): TradeSettings = read(config).also { current = it }

        private fun read(config: PluginConfig): TradeSettings = TradeSettings(
            enabled = config.getBoolean("player-trades.enabled", true),
            distance = config.getDouble("player-trades.distance", 10.0).coerceIn(1.0, 128.0),
            requestExpirySeconds = config.getLong("player-trades.request-expiry", 60L).coerceIn(5L, 600L),
        )
    }
}
