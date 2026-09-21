package net.trilleo.mc.plugins.tritown.listeners.news

import net.trilleo.mc.plugins.tritown.config.NewsSettings
import net.trilleo.mc.plugins.tritown.news.NewsManager
import net.trilleo.mc.plugins.tritown.news.NewsNotifier
import net.trilleo.mc.plugins.tritown.news.NewsReadState
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin

/** Tells a player what they have missed as they join. */
class NewsJoinListener(private val plugin: JavaPlugin) : Listener {

    /** Above NORMAL, so the player's data has been loaded before it is read. */
    @EventHandler(priority = EventPriority.HIGH)
    fun onJoin(event: PlayerJoinEvent) {
        if (!NewsManager.isAvailable) return
        val player = event.player
        NewsReadState.review(player)

        val settings = NewsSettings.snapshot
        if (!settings.joinMessage) return
        // Later than the join, so the client has sent its language and the
        // summary is not lost among the server's welcome messages.
        plugin.server.scheduler.runTaskLater(
            plugin,
            Runnable { NewsNotifier.joinSummary(player) },
            settings.joinDelayTicks.coerceAtLeast(MIN_DELAY_TICKS),
        )
    }

    private companion object {
        const val MIN_DELAY_TICKS = 20L
    }
}
