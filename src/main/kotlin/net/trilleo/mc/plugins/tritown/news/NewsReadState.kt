package net.trilleo.mc.plugins.tritown.news

import com.google.gson.JsonArray
import com.google.gson.JsonPrimitive
import net.trilleo.mc.plugins.tritown.data.PlayerDataManager
import org.bukkit.entity.Player

/**
 * Which posts each player has read, kept in their [net.trilleo.mc.plugins.tritown.data.PlayerData].
 *
 * A player also carries the moment they first met the news. Posts published
 * before it do not count as unread, so someone joining a server with a year of
 * update notes is not greeted with all of them — only the newest, which is
 * always counted until it is read, so a new player still hears about the
 * latest changes.
 *
 * The menu item reads [unreadCount] every second for every player, so nothing
 * here may do more than walk the published posts once.
 */
object NewsReadState {

    private const val READ_KEY = "news-read"
    private const val SINCE_KEY = "news-since"

    /** The unread posts for [player], in the order the news lists them. */
    fun unread(player: Player): List<NewsPost> {
        if (!NewsManager.isAvailable) return emptyList()
        val data = PlayerDataManager.get(player)
        val since = data.getString(SINCE_KEY).toLongOrNull() ?: 0L
        return unreadOf(NewsManager.published(), readIds(player), since)
    }

    fun unreadCount(player: Player): Int = unread(player).size

    fun isUnread(player: Player, post: NewsPost): Boolean = unread(player).any { it.id == post.id }

    fun markRead(player: Player, post: NewsPost) {
        val ids = readIds(player)
        if (post.id in ids) return
        store(player, ids + post.id)
    }

    fun markAllRead(player: Player) {
        store(player, readIds(player) + NewsManager.published().map { it.id })
    }

    /**
     * Sets the moment [player] first met the news, and forgets posts that no
     * longer exist so the list cannot grow for ever. Called as they join.
     */
    fun review(player: Player) {
        val data = PlayerDataManager.get(player)
        if (!data.has(SINCE_KEY)) data.set(SINCE_KEY, System.currentTimeMillis().toString())

        val ids = readIds(player)
        val kept = ids.filterTo(mutableSetOf()) { NewsManager.get(it) != null }
        if (kept.size != ids.size) store(player, kept)
    }

    /**
     * The posts in [published] that are still unread, given the [read] ids and
     * the moment [since] the player first met the news. The newest post counts
     * whatever [since] says. Kept apart from [PlayerDataManager] so it can be tested.
     */
    internal fun unreadOf(published: List<NewsPost>, read: Set<String>, since: Long): List<NewsPost> {
        val newest = published.maxByOrNull { it.publishedAt ?: 0L }
        return published.filter { post ->
            post.id !in read && ((post.publishedAt ?: 0L) > since || post === newest)
        }
    }

    private fun readIds(player: Player): Set<String> =
        PlayerDataManager.get(player).getJsonArray(READ_KEY).mapNotNullTo(mutableSetOf()) {
            runCatching { it.asString }.getOrNull()
        }

    private fun store(player: Player, ids: Set<String>) {
        val array = JsonArray()
        ids.forEach { array.add(JsonPrimitive(it)) }
        PlayerDataManager.get(player).set(READ_KEY, array)
    }
}
