package net.trilleo.mc.plugins.tritown.news

import net.kyori.adventure.sound.Sound
import net.kyori.adventure.title.Title
import net.trilleo.mc.plugins.tritown.config.NewsSettings
import net.trilleo.mc.plugins.tritown.utils.ComponentUtil
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Bukkit
import org.bukkit.entity.Player

/**
 * Tells players there is something to read: a summary as they join, and an
 * announcement to everyone online the moment a post is published.
 *
 * Every line links to the post it names, so reading it is one click away.
 */
object NewsNotifier {

    /** Lists what [player] has not read yet, if anything. */
    fun joinSummary(player: Player) {
        if (!player.isOnline || !NewsManager.isAvailable) return
        val unread = NewsReadState.unread(player)
        if (unread.isEmpty()) return

        val preview = NewsSettings.snapshot.joinPreview
        player.sendPrefixed(player.tr("news.join.header", "amount" to unread.size))
        unread.take(preview).forEach { post ->
            player.sendMessage(
                ComponentUtil.parse(
                    player.tr(
                        "news.join.entry",
                        "id" to post.id,
                        "title" to title(player, post)
                    )
                )
            )
        }
        if (unread.size > preview) {
            player.sendMessage(ComponentUtil.parse(player.tr("news.join.more", "amount" to unread.size - preview)))
        }
        player.sendMessage(ComponentUtil.parse(player.tr("news.join.open")))
    }

    /** Puts [post] in front of everyone online, in each player's own language. */
    fun announce(post: NewsPost) {
        val settings = NewsSettings.snapshot
        val sound = settings.announceSound?.let { Sound.sound(it, Sound.Source.MASTER, 1f, 1f) }

        Bukkit.getOnlinePlayers().forEach { player ->
            val title = title(player, post)
            if (settings.announceTitle) {
                player.showTitle(
                    Title.title(
                        ComponentUtil.parse(player.tr("news.broadcast.title")),
                        ComponentUtil.parse(title),
                    )
                )
            }
            if (settings.announceChat) {
                player.sendPrefixed(player.tr("news.broadcast.chat", "id" to post.id, "title" to title))
            }
            sound?.let(player::playSound)
        }
    }

    private fun title(player: Player, post: NewsPost): String = post.title.forViewer(player)
}
