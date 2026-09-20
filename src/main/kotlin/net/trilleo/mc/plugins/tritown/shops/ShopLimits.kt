package net.trilleo.mc.plugins.tritown.shops

import com.google.gson.JsonObject
import net.trilleo.mc.plugins.tritown.data.PlayerDataManager
import net.trilleo.mc.plugins.tritown.enums.TradeSide
import org.bukkit.entity.Player

/**
 * How many items of a limited entry each player has already traded.
 *
 * Counters live in the trader's own player data rather than with the shop: they
 * are read and written only while that player is online, and keeping them there
 * means deleting a shop cannot leave a growing table of dead counters behind.
 *
 * Buying and selling are counted apart, so an entry can be "buy 64 a day, sell
 * 256 a day" without one side eating the other's allowance.
 *
 * A count is stored with the window it belongs to, so a window that has turned
 * over is simply ignored — nothing has to sweep old counters at midnight.
 */
object ShopLimits {

    private const val ROOT_KEY = "shop-limits"
    private const val COUNT = "n"
    private const val WINDOW = "w"

    /** How many more items of [entry] in [shop] the player may trade on [side], or `null` when it is unlimited. */
    fun remaining(
        player: Player,
        shop: ShopDefinition,
        entry: ShopEntry,
        side: TradeSide,
        now: Long = System.currentTimeMillis(),
    ): Int? {
        val limit = entry.limitOn(side) ?: return null
        val record = record(player, shop, entry, side) ?: return limit.amount
        return limit.remaining(record.first, record.second, now)
    }

    /** Records [items] traded on [side], resetting the count first when the window has turned over. */
    fun record(
        player: Player,
        shop: ShopDefinition,
        entry: ShopEntry,
        side: TradeSide,
        items: Int,
        now: Long = System.currentTimeMillis(),
    ) {
        val limit = entry.limitOn(side) ?: return
        val window = limit.windowAt(now)
        val previous = record(player, shop, entry, side)
        val count = if (previous != null && previous.second == window) previous.first + items else items

        val root = root(player)
        root.add(key(shop, entry, side), JsonObject().apply {
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

    private fun record(player: Player, shop: ShopDefinition, entry: ShopEntry, side: TradeSide): Pair<Int, Long>? {
        val stored = root(player).getAsJsonObject(key(shop, entry, side)) ?: return null
        val count = stored.get(COUNT)?.asInt ?: return null
        val window = stored.get(WINDOW)?.asLong ?: return null
        return count to window
    }

    private fun root(player: Player): JsonObject = PlayerDataManager.get(player).getJsonObject(ROOT_KEY)

    /**
     * Buying keeps the key it has always had, so counters written before the
     * sell limit existed still count against the day they were written.
     */
    private fun key(shop: ShopDefinition, entry: ShopEntry, side: TradeSide): String = when (side) {
        TradeSide.BUY -> "${shop.id}/${entry.id}"
        TradeSide.SELL -> "${shop.id}/${entry.id}/sell"
    }
}
