package net.trilleo.mc.plugins.tritown.shops.storage

/**
 * A shop as it sits on disk.
 *
 * Deliberately separate from the live model: items are Base64 here rather than
 * `ItemStack`s, which keeps the whole storage layer free of Bukkit and therefore
 * testable without a server. [net.trilleo.mc.plugins.tritown.shops.ShopManager]
 * converts between the two.
 *
 * Gson builds these by reflection, so every field needs a default — an older
 * file that predates a field must still load.
 */
data class StoredShop(
    val id: String = "",
    val displayName: String = "",
    val permission: String? = null,
    val towny: String = "NONE",
    val hideWhenLocked: Boolean = false,
    val npcIds: List<String> = emptyList(),
    val entries: List<StoredEntry> = emptyList(),
)

/** One entry of a [StoredShop]. */
data class StoredEntry(
    val id: String = "",
    val item: String = "",
    val bundle: Int = 0,
    val buy: StoredCost? = null,
    val sell: StoredCost? = null,
    val permission: String? = null,
    val towny: String = "NONE",
    val hideWhenLocked: Boolean = false,
    val limitAmount: Int = 0,
    val limitPeriod: String = "NONE",
    val sellLimitAmount: Int = 0,
    val sellLimitPeriod: String = "NONE",
    val stockMax: Int = 0,
    val stockRestockSeconds: Long = 0L,
    val stockRemaining: Int = 0,
    val stockLastRestock: Long = 0L,
    val discountable: Boolean = true,
    val matchMode: String = "EXACT",
    val bought: Long = 0L,
    val sold: Long = 0L,
    val moneyIn: Double = 0.0,
    val moneyOut: Double = 0.0,
)

/** A price or a payout of a [StoredEntry]. */
data class StoredCost(
    val money: Double = 0.0,
    val items: List<String> = emptyList(),
)
