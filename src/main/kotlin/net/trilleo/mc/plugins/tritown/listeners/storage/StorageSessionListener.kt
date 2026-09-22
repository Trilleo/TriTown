package net.trilleo.mc.plugins.tritown.listeners.storage

import net.trilleo.mc.plugins.tritown.guis.storage.StorageGUI
import net.trilleo.mc.plugins.tritown.storage.StorageManager
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent

/**
 * Writes a player's storage when they leave.
 *
 * An open storage is closed first, while the page on screen can still be
 * copied back, and only then is the storage written and let go of — unless an
 * administrator is still looking through it, in which case it goes when they do.
 */
class StorageSessionListener : Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    fun onQuit(event: PlayerQuitEvent) {
        val player = event.player
        StorageGUI.close(player)
        StorageManager.unloadIfIdle(player.uniqueId, leaving = true)
    }
}
