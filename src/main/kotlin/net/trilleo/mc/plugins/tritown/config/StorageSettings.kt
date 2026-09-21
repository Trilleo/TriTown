package net.trilleo.mc.plugins.tritown.config

/**
 * An immutable snapshot of the `storage` block of `config.yml`.
 *
 * Built whole and swapped in on a reload, the same way [ShopSettings] is, so a
 * page being bought while the configuration is re-read cannot be priced from
 * half of it.
 *
 * @param enabled             whether players have a storage at all
 * @param freePages           how many pages every player has without paying
 * @param maxPages            the most pages a storage can reach, free ones included
 * @param priceBase           what the first bought page costs
 * @param priceMultiplier     how much dearer each bought page is than the one before
 * @param saveIntervalSeconds how often changed storages are written out
 * @param lock                which vanilla containers are withdraw-only
 */
data class StorageSettings(
    val enabled: Boolean,
    val freePages: Int,
    val maxPages: Int,
    val priceBase: Double,
    val priceMultiplier: Double,
    val saveIntervalSeconds: Long,
    val lock: ContainerLock,
) {

    /**
     * Which groups of vanilla containers can no longer be placed or filled.
     *
     * @param chests       chests, trapped chests, barrels, chest minecarts and chest boats
     * @param shulkerBoxes every colour of shulker box
     * @param enderChest   the ender chest
     */
    data class ContainerLock(
        val chests: Boolean,
        val shulkerBoxes: Boolean,
        val enderChest: Boolean,
    )

    companion object {

        /** The most pages a storage may be configured to reach. */
        const val PAGE_LIMIT = 256

        @Volatile
        private var current: StorageSettings? = null

        /** The settings in force. */
        val snapshot: StorageSettings
            get() = current ?: error("Storage settings have not been loaded yet")

        /** Whether [load] has run. */
        val isLoaded: Boolean
            get() = current != null

        /** Reads the `storage` block from [config] and makes it the current snapshot. */
        fun load(config: PluginConfig): StorageSettings = read(config).also { current = it }

        private fun read(config: PluginConfig): StorageSettings {
            val maxPages = config.getInt("storage.max-pages", 27).coerceIn(1, PAGE_LIMIT)
            val locking = config.getBoolean("storage.lock-containers.enabled", true)

            return StorageSettings(
                enabled = config.getBoolean("storage.enabled", true),
                freePages = config.getInt("storage.free-pages", 2).coerceIn(1, maxPages),
                maxPages = maxPages,
                priceBase = config.getDouble("storage.price.base", 500.0).coerceAtLeast(0.0),
                priceMultiplier = config.getDouble("storage.price.multiplier", 1.5).coerceIn(1.0, 10.0),
                saveIntervalSeconds = config.getLong("storage.save-interval", 30L).coerceIn(5L, 3600L),
                lock = ContainerLock(
                    chests = locking && config.getBoolean("storage.lock-containers.chests", true),
                    shulkerBoxes = locking && config.getBoolean("storage.lock-containers.shulker-boxes", true),
                    enderChest = locking && config.getBoolean("storage.lock-containers.ender-chest", true),
                ),
            )
        }
    }
}
