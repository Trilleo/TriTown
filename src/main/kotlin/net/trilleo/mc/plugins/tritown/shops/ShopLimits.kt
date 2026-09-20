package net.trilleo.mc.plugins.tritown.shops

import com.google.gson.JsonObject
import net.trilleo.mc.plugins.tritown.data.PlayerDataManager
import org.bukkit.entity.Player

/**
 * How many items of a limited entry each player has already bought.
 *
 * Counters live in the buyer's own player data rather than with the shop: they
 * are read and written only while that player is online, and keeping them there
 * means deleting a shop cannot leave a growing table of dead counters behind.
 *
 * A count is stored with the window it belongs to, so a window that has turned
 * over is simply ignored — nothing has to sweep old counters at midnight.
 */
object ShopLimits {

    private const val ROOT_KEY = "shop-limits"
    private const val COUNT = "n"
    private const val WINDOW = "w"

    /** How many more items of [entry] in [shop] the player may buy, or `null` when it is unlimited. */
    fun remaining(
        player: Player,
        shop: ShopDefinition,
        entry: ShopEntry,
        now: Long = System.currentTimeMillis(),
    ): Int? {
        val limit = entry.limit ?: return null
        val record = record(player, shop, entry) ?: return limit.amount
        return limit.remaining(record.first, record.second, now)
    }

    /** Records [items] bought, resetting the count first when the window has turned over. */
    fun record(
        player: Player,
        shop: ShopDefinition,
        entry: ShopEntry,
        items: Int,
        now: Long = System.currentTimeMillis(),
    ) {
        val limit = entry.limit ?: return
        val window = limit.windowAt(now)
        val previous = record(player, shop, entry)
        val count = if (previous != null && previous.second == window) previous.first + items else items

        val root = root(player)
        root.add(key(shop, entry), JsonObject().apply {
            addProperty(COUNT, count)
            addProperty(WINDOW, window)
        })
        PlayerDataManager.get(player).set(ROOT_KEY, root)
    }

    /** Forgets every counter [player] holds for [shop], for a shop that has been deleted. */
    fun forget(player: Player, shop: ShopDefinition) {
        val root = root(player)
        val prefix = "${shop.id}/"
        root.keySet().filter { it.startsWith(prefix) }.forEach(root::remove)
        PlayerDataManager.get(player).set(ROOT_KEY, root)
    }

    private fun record(player: Player, shop: ShopDefinition, entry: ShopEntry): Pair<Int, Long>? {
        val stored = root(player).getAsJsonObject(key(shop, entry)) ?: return null
        val count = stored.get(COUNT)?.asInt ?: return null
        val window = stored.get(WINDOW)?.asLong ?: return null
        return count to window
    }

    private fun root(player: Player): JsonObject = PlayerDataManager.get(player).getJsonObject(ROOT_KEY)

    private fun key(shop: ShopDefinition, entry: ShopEntry): String = "${shop.id}/${entry.id}"
}
