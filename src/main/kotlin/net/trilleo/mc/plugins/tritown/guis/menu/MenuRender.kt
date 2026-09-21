package net.trilleo.mc.plugins.tritown.guis.menu

import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.trilleo.mc.plugins.tritown.Main
import net.trilleo.mc.plugins.tritown.utils.LoreUtil
import net.trilleo.mc.plugins.tritown.utils.itemStack
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta

/** The pieces the main menu and the menus it opens are drawn with. */
object MenuRender {

    /**
     * The navigation-row offsets of a paged menu's own two buttons: back to the
     * main menu on the left of the page number, and one more on its right, so
     * the row stays symmetrical.
     */
    const val BACK_OFFSET = 3
    const val EXTRA_OFFSET = 5

    private val click = Sound.sound(Key.key("minecraft:ui.button.click"), Sound.Source.UI, 1f, 1f)

    /** The button that returns to the main menu. */
    fun back(viewer: Player): ItemStack = itemStack(Material.ARROW) {
        name(viewer.tr("gui.menu.back"))
        meta { lore(LoreUtil.wrapLore(viewer.tr("gui.menu.back-lore"))) }
    }

    /**
     * [owner]'s head, named [name], with [lines] of wrapped lore.
     *
     * Blank lines are kept, so the lore can be grouped. A [glow]ing head is one
     * the viewer should notice first, such as someone waiting for an answer.
     */
    fun head(owner: OfflinePlayer, name: String, lines: List<String>, glow: Boolean = false): ItemStack =
        itemStack(Material.PLAYER_HEAD) {
            name(name)
            meta {
                (this as SkullMeta).owningPlayer = owner
                lore(LoreUtil.wrapLore(lines.joinToString("<newline>")))
                if (glow) setEnchantmentGlintOverride(true)
            }
        }

    /**
     * Runs [action] on the following tick, with a click to go with it.
     *
     * A click is still being delivered while its handler runs, and opening or
     * closing an inventory from inside that delivery leaves the server and the
     * client disagreeing about what is on screen.
     */
    fun later(player: Player, action: () -> Unit) {
        player.playSound(click)
        Bukkit.getScheduler().runTask(Main.instance, Runnable { if (player.isOnline) action() })
    }

    /** The click sound on its own, for a button that changes the menu in place. */
    fun click(player: Player) = player.playSound(click)
}
