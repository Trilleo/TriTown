package net.trilleo.mc.plugins.tritown.guis.news

import net.trilleo.mc.plugins.tritown.commands.news.NewsCommand
import net.trilleo.mc.plugins.tritown.enums.FillMode
import net.trilleo.mc.plugins.tritown.guis.menu.MenuRender
import net.trilleo.mc.plugins.tritown.news.LocalizedText
import net.trilleo.mc.plugins.tritown.news.NewsManager
import net.trilleo.mc.plugins.tritown.news.NewsPost
import net.trilleo.mc.plugins.tritown.registration.GUIFrame
import net.trilleo.mc.plugins.tritown.registration.GUIManager
import net.trilleo.mc.plugins.tritown.registration.PluginGUI
import net.trilleo.mc.plugins.tritown.utils.Lang
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * One piece of a post's text in every language the server has.
 *
 * The default text is what anyone without a translation reads; each language
 * file installed gets a button of its own below it. The same menu serves a
 * title, a summary, a category name and an entry, which is why it is handed
 * the field to edit and the way back rather than knowing either.
 */
class NewsTextGUI : PluginGUI(
    id = ID,
    titleKey = "gui.news-text.title",
    rows = 6,
    fillMode = FillMode.NONE,
) {

    /**
     * What is being translated.
     *
     * @param field     finds the text in the post as it is now, or `null` once it is gone
     * @param maxLength the most visible characters each version may have, if there is a limit
     * @param back      reopens the menu this was reached from
     */
    class Target(
        val postId: String,
        val field: (NewsPost) -> LocalizedText?,
        val maxLength: Int?,
        val back: (Player) -> Unit,
    )

    private val targets = ConcurrentHashMap<UUID, Target>()

    fun open(player: Player, target: Target) {
        targets[player.uniqueId] = target
        GUIManager.open(player, ID)
    }

    override fun setup(player: Player, inventory: Inventory) {
        val text = textOf(player) ?: return
        val languages = languageSlots()

        GUIFrame.draw(inventory, languages.keys + MAIN_SLOT)
        inventory.setItem(MAIN_SLOT, main(player, text))
        languages.forEach { (slot, id) -> inventory.setItem(slot, language(player, text, id)) }
        inventory.setItem(
            BACK_SLOT,
            NewsRender.button(player, Material.ARROW, "gui.news-text.back", "gui.news-text.back-lore")
        )
    }

    override fun onClick(event: InventoryClickEvent) {
        event.isCancelled = true
        if (event.clickedInventory !== event.view.topInventory) return

        val player = event.whoClicked as? Player ?: return
        if (!player.hasPermission(NewsCommand.MANAGE_PERMISSION)) return
        val target = targets[player.uniqueId] ?: return
        val post = NewsManager.get(target.postId) ?: return
        val text = target.field(post) ?: return
        val reopen: () -> Unit = { show(player, target) }

        if (event.rawSlot == BACK_SLOT) {
            MenuRender.later(player) { target.back(player) }
            return
        }

        if (event.rawSlot == MAIN_SLOT) {
            NewsRender.prompt(player, player.tr("gui.news-text.prompt-main"), target.maxLength, reopen) {
                text.main = it
                NewsManager.changed(post)
            }
            return
        }

        val language = languageSlots()[event.rawSlot] ?: return
        if (event.click == ClickType.RIGHT || event.click == ClickType.SHIFT_RIGHT) {
            text.clear(language)
            NewsManager.changed(post)
            MenuRender.click(player)
            setup(player, event.inventory)
            return
        }

        NewsRender.prompt(
            player,
            player.tr("gui.news-text.prompt-language", "language" to language),
            target.maxLength,
            reopen
        ) {
            text.translate(language, it)
            NewsManager.changed(post)
        }
    }

    override fun onClose(event: InventoryCloseEvent) {
        targets.remove(event.player.uniqueId)
    }

    private fun main(player: Player, text: LocalizedText): ItemStack = NewsRender.card(
        Material.WRITABLE_BOOK,
        player.tr("gui.news-text.main"),
        listOf(
            player.tr("gui.news-text.current", "text" to text.main),
            "",
            player.tr("gui.news-text.main-lore"),
            player.tr("gui.news-text.click-edit"),
        ),
    )

    private fun language(player: Player, text: LocalizedText, id: String): ItemStack {
        val translation = text.translation(id)
        val lines = buildList {
            add(
                if (translation != null) player.tr("gui.news-text.current", "text" to translation)
                else player.tr("gui.news-text.untranslated")
            )
            add("")
            add(player.tr("gui.news-text.click-edit"))
            if (translation != null) add(player.tr("gui.news-text.right-click-clear"))
        }
        return NewsRender.card(
            if (translation != null) Material.FILLED_MAP else Material.MAP,
            player.tr("gui.news-text.language", "language" to id),
            lines,
        )
    }

    /** One slot per installed language, packed along the row under the default text. Seven fit. */
    private fun languageSlots(): Map<Int, String> {
        val ids = Lang.ids.take(MAX_LANGUAGES)
        return GUIFrame.packedColumns(ids.size).zip(ids)
            .associate { (column, id) -> LANGUAGE_ROW * ROW_SIZE + column to id }
    }

    private fun textOf(player: Player): LocalizedText? {
        val target = targets[player.uniqueId] ?: return null
        return NewsManager.get(target.postId)?.let(target.field)
    }

    companion object {
        const val ID = "news-text"

        private const val ROW_SIZE = 9
        private const val MAIN_SLOT = 13
        private const val LANGUAGE_ROW = 3
        private const val MAX_LANGUAGES = 7
        private const val BACK_SLOT = 49

        fun show(player: Player, target: Target): Boolean {
            val gui = GUIManager.getGUI(ID) as? NewsTextGUI ?: return false
            gui.open(player, target)
            return true
        }
    }
}
