package net.trilleo.mc.plugins.tritown.guis.news

import net.trilleo.mc.plugins.tritown.commands.news.NewsCommand
import net.trilleo.mc.plugins.tritown.config.NewsSettings
import net.trilleo.mc.plugins.tritown.enums.FillMode
import net.trilleo.mc.plugins.tritown.guis.menu.MenuRender
import net.trilleo.mc.plugins.tritown.news.EntryTag
import net.trilleo.mc.plugins.tritown.news.NewsCategory
import net.trilleo.mc.plugins.tritown.news.NewsEntry
import net.trilleo.mc.plugins.tritown.news.NewsManager
import net.trilleo.mc.plugins.tritown.news.NewsPost
import net.trilleo.mc.plugins.tritown.registration.GUIFrame
import net.trilleo.mc.plugins.tritown.registration.GUIManager
import net.trilleo.mc.plugins.tritown.registration.PluginGUI
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/** One entry: what it says, in each language, and which tag it carries. */
class NewsEntryGUI : PluginGUI(
    id = ID,
    titleKey = "gui.news-entry.title",
    rows = 6,
    fillMode = FillMode.NONE,
) {

    private class Editing(val postId: String, val categoryId: String, val entryId: String)

    private val editing = ConcurrentHashMap<UUID, Editing>()

    fun open(player: Player, post: NewsPost, category: NewsCategory, entry: NewsEntry) {
        editing[player.uniqueId] = Editing(post.id, category.id, entry.id)
        GUIManager.open(player, ID)
    }

    override fun setup(player: Player, inventory: Inventory) {
        val (_, _, entry) = target(player) ?: return

        GUIFrame.draw(inventory, tagSlots().keys + TEXT_SLOT)
        inventory.setItem(
            TEXT_SLOT,
            NewsRender.card(
                Material.WRITABLE_BOOK,
                NewsRender.entryLine(player, entry),
                listOf(
                    player.tr("gui.news-settings.value", "value" to entry.text.main),
                    player.tr("gui.news-settings.translated", "amount" to entry.text.translations.size),
                    "",
                    player.tr("gui.news-settings.click-edit"),
                    player.tr("gui.news-settings.right-click-translate"),
                ),
            ),
        )
        tagSlots().forEach { (slot, tag) ->
            val lines = listOf(
                player.tr(if (tag == entry.tag) "gui.news-entry.tag-current" else "gui.news-entry.tag-click")
            )
            inventory.setItem(
                slot,
                NewsRender.card(NewsRender.tagMaterial(tag), NewsRender.tag(player, tag), lines, glow = tag == entry.tag),
            )
        }
        inventory.setItem(BACK_SLOT, NewsRender.button(player, Material.ARROW, "gui.news-entry.back", "gui.news-entry.back-lore"))
        inventory.setItem(DELETE_SLOT, NewsRender.button(player, Material.LAVA_BUCKET, "gui.news-entry.delete", "gui.news-entry.delete-lore"))
    }

    override fun onClick(event: InventoryClickEvent) {
        event.isCancelled = true
        if (event.clickedInventory !== event.view.topInventory) return

        val player = event.whoClicked as? Player ?: return
        if (!player.hasPermission(NewsCommand.MANAGE_PERMISSION)) return
        val (post, category, entry) = target(player) ?: return
        val right = event.click == ClickType.RIGHT || event.click == ClickType.SHIFT_RIGHT
        val maxLength = NewsSettings.snapshot.maxEntryLength

        when (event.rawSlot) {
            TEXT_SLOT -> if (right) {
                MenuRender.later(player) {
                    NewsTextGUI.show(
                        player,
                        NewsTextGUI.Target(
                            post.id,
                            { it.category(category.id)?.entry(entry.id)?.text },
                            maxLength,
                        ) { show(it, post, category, entry) },
                    )
                }
            } else {
                NewsRender.prompt(player, player.tr("gui.news-entry.prompt-text"), maxLength, { show(player, post, category, entry) }) {
                    entry.text.main = it
                    NewsManager.changed(post)
                }
            }

            BACK_SLOT -> MenuRender.later(player) { NewsCategoryEditorGUI.show(player, post, category) }

            DELETE_SLOT -> {
                category.entries.remove(entry)
                NewsManager.changed(post)
                player.sendPrefixed(player.tr("news.editor.entry-removed"))
                MenuRender.later(player) { NewsCategoryEditorGUI.show(player, post, category) }
            }

            else -> {
                val tag = tagSlots()[event.rawSlot] ?: return
                entry.tag = tag
                NewsManager.changed(post)
                MenuRender.click(player)
                setup(player, event.inventory)
            }
        }
    }

    override fun onClose(event: InventoryCloseEvent) {
        editing.remove(event.player.uniqueId)
    }

    private fun tagSlots(): Map<Int, EntryTag> =
        GUIFrame.packedColumns(EntryTag.entries.size).zip(EntryTag.entries).associate { (column, tag) ->
            TAG_ROW * ROW_SIZE + column to tag
        }

    private fun target(player: Player): Triple<NewsPost, NewsCategory, NewsEntry>? {
        val state = editing[player.uniqueId] ?: return null
        val post = NewsManager.get(state.postId) ?: return null
        val category = post.category(state.categoryId) ?: return null
        val entry = category.entry(state.entryId) ?: return null
        return Triple(post, category, entry)
    }

    companion object {
        const val ID = "news-entry"

        private const val ROW_SIZE = 9
        private const val TEXT_SLOT = 13
        private const val TAG_ROW = 3
        private const val BACK_SLOT = 48
        private const val DELETE_SLOT = 50

        fun show(player: Player, post: NewsPost, category: NewsCategory, entry: NewsEntry): Boolean {
            val gui = GUIManager.getGUI(ID) as? NewsEntryGUI ?: return false
            gui.open(player, post, category, entry)
            return true
        }
    }
}
