package net.trilleo.mc.plugins.tritown.tasks.menu

import net.trilleo.mc.plugins.tritown.menu.MenuItem
import net.trilleo.mc.plugins.tritown.registration.PluginTask

/**
 * Puts every player's [MenuItem] right once a second.
 *
 * The listener refuses what it can see, and this catches what it cannot: a
 * `/clear`, another plugin rewriting a slot, a creative client, or a pick-block
 * that swapped the item into the inventory. Nothing a copy made in between can
 * do takes longer than this to undo.
 */
class MenuItemTask : PluginTask(delay = PERIOD, period = PERIOD) {

    override fun run() = MenuItem.reconcileAll()

    private companion object {
        const val PERIOD = 20L
    }
}
