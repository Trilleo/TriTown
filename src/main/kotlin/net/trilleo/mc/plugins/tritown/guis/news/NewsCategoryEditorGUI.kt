package net.trilleo.mc.plugins.tritown.guis.news

import net.kyori.adventure.text.Component
import net.trilleo.mc.plugins.tritown.commands.news.NewsCommand
import net.trilleo.mc.plugins.tritown.config.NewsSettings
import net.trilleo.mc.plugins.tritown.enums.FillMode
import net.trilleo.mc.plugins.tritown.enums.PagedLayout
import net.trilleo.mc.plugins.tritown.guis.menu.MenuRender
import net.trilleo.mc.plugins.tritown.news.EntryTag
import net.trilleo.mc.plugins.tritown.news.LocalizedText
import net.trilleo.mc.plugins.tritown.news.NewsCategory
import net.trilleo.mc.plugins.tritown.news.NewsEntry
import net.trilleo.mc.plugins.tritown.news.NewsIds
import net.trilleo.mc.plugins.tritown.news.NewsManager
import net.trilleo.mc.plugins.tritown.news.NewsPost
import net.trilleo.mc.plugins.tritown.news.NewsShorthand
import net.trilleo.mc.plugins.tritown.registration.GUIManager
import net.trilleo.mc.plugins.tritown.registration.PagedPluginGUI
import net.trilleo.mc.plugins.tritown.utils.ChatPrompt
import net.trilleo.mc.plugins.tritown.utils.ComponentUtil
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
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
 * The entries of one category, where they are written, tagged, reordered and
 * removed.
 *
 * Writing is done in chat, one line after another: each line becomes an entry
 * and the next question follows at once, until the administrator types `done`.
 * A prefix on the line picks its tag ([NewsShorthand]), so a whole changelog
 * goes in without a click between entries.
 *
 * Entries move the way categories do in [NewsEditorGUI]: shift-right-click
 * marks one, and the next click says where it goes.
 */
class NewsCategoryEditorGUI : PagedPluginGUI(
    id = ID,
    titleKey = "gui.news-category-editor.title",
    rows = 6,
    fillMode = FillMode.NONE,
    layout = PagedLayout.FRAMED,
) {

    private class Editing(val postId: String, val categoryId: String)

    private val editing = ConcurrentHashMap<UUID, Editing>()

    /** The entry each administrator is part-way through moving, by its id. */
    private val moving = ConcurrentHashMap<UUID, String>()

    fun open(player: Player, post: NewsPost, category: NewsCategory) {
        editing[player.uniqueId] = Editing(post.id, category.id)
        moving.remove(player.uniqueId)
        GUIManager.open(player, ID)
    }

    override fun title(player: Player): Component {
        val (_, category) = target(player) ?: return super.title(player)
        return ComponentUtil.parse(
            player.tr("gui.news-category-editor.title", "name" to NewsRender.text(player, category.name))
        )
    }

    override fun getItems(player: Player): List<ItemStack> {
        val (_, category) = target(player) ?: return emptyList()
        val held = moving[player.uniqueId]
        return category.entries.map { icon(player, it, held) }
    }

    override fun navButtons(player: Player): Map<Int, ItemStack> {
        val (_, category) = target(player) ?: return emptyMap()
        val back = NewsRender.button(player, Material.ARROW, "gui.news-category-editor.back", "gui.news-category-editor.back-lore")

        val held = moving[player.uniqueId]?.let(category::entry)
        if (held != null) {
            return mapOf(
                SLOT_HELD to NewsRender.glowing(
                    NewsRender.card(
                        NewsRender.tagMaterial(held.tag),
                        NewsRender.entryLine(player, held),
                        listOf(player.tr("gui.news-category-editor.holding"), player.tr("gui.news-category-editor.click-put-down")),
                    )
                ),
                SLOT_MOVE_FIRST to NewsRender.button(player, Material.SPECTRAL_ARROW, "gui.news-category-editor.move-first", "gui.news-category-editor.move-first-lore"),
                SLOT_MOVE_LAST to NewsRender.button(player, Material.TIPPED_ARROW, "gui.news-category-editor.move-last", "gui.news-category-editor.move-last-lore"),
                SLOT_BACK to back,
            )
        }

        return mapOf(
            SLOT_ADD to NewsRender.button(player, Material.FEATHER, "gui.news-category-editor.add", "gui.news-category-editor.add-lore"),
            SLOT_NAME to NewsRender.card(
                Material.NAME_TAG,
                player.tr("gui.news-category-editor.name"),
                listOf(
                    player.tr("gui.news-settings.value", "value" to category.name.main),
                    player.tr("gui.news-settings.translated", "amount" to category.name.translations.size),
                    "",
                    player.tr("gui.news-settings.click-edit"),
                    player.tr("gui.news-settings.right-click-translate"),
                ),
            ),
            SLOT_ICON to NewsRender.card(
                NewsRender.categoryIcon(category),
                player.tr("gui.news-category-editor.icon"),
                listOf(
                    player.tr("gui.news-category-editor.icon-lore"),
                    "",
                    player.tr("gui.news-settings.right-click-reset"),
                ),
            ),
            SLOT_BACK to back,
        )
    }

    override fun onNavClick(event: InventoryClickEvent, offset: Int) {
        val player = event.whoClicked as? Player ?: return
        if (!player.hasPermission(NewsCommand.MANAGE_PERMISSION)) return
        val (post, category) = target(player) ?: return
        val held = moving[player.uniqueId]
        val right = event.click == ClickType.RIGHT || event.click == ClickType.SHIFT_RIGHT

        if (held != null) {
            when (offset) {
                SLOT_HELD -> release(player, event.inventory)
                SLOT_MOVE_FIRST -> place(player, post, category, held, 0, event.inventory)
                SLOT_MOVE_LAST -> place(player, post, category, held, category.entries.size, event.inventory)
                SLOT_BACK -> MenuRender.later(player) { NewsEditorGUI.show(player, post) }
            }
            return
        }

        when (offset) {
            SLOT_ADD -> {
                player.closeInventory()
                addEntries(player, post, category, EntryTag.NEW, first = true)
            }

            SLOT_NAME -> if (right) {
                MenuRender.later(player) {
                    NewsTextGUI.show(
                        player,
                        NewsTextGUI.Target(post.id, { it.category(category.id)?.name }, null) { show(it, post, category) },
                    )
                }
            } else {
                NewsRender.prompt(player, player.tr("gui.news-category-editor.prompt-name"), null, { show(player, post, category) }) {
                    category.name.main = it
                    NewsManager.changed(post)
                }
            }

            SLOT_ICON -> if (right) {
                category.icon = NewsCategory.DEFAULT_ICON
                NewsManager.changed(post)
                MenuRender.click(player)
                refresh(player, event.inventory)
            }

            SLOT_BACK -> MenuRender.later(player) { NewsEditorGUI.show(player, post) }
        }
    }

    /** A click in the administrator's own inventory picks the category's icon; the item stays where it is. */
    override fun onClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player
        if (player != null && event.clickedInventory === player.inventory) {
            event.isCancelled = true
            if (!player.hasPermission(NewsCommand.MANAGE_PERMISSION) || moving.containsKey(player.uniqueId)) return
            val (post, category) = target(player) ?: return
            val item = event.currentItem ?: return
            if (item.type.isAir) return

            category.icon = item.type.name
            NewsManager.changed(post)
            MenuRender.click(player)
            refresh(player, event.inventory)
            return
        }

        super.onClick(event)
    }

    override fun onContentClick(event: InventoryClickEvent, page: Int) {
        val player = event.whoClicked as? Player ?: return
        if (!player.hasPermission(NewsCommand.MANAGE_PERMISSION)) return
        val (post, category) = target(player) ?: return
        val index = contentIndex(event, page) ?: return

        val held = moving[player.uniqueId]
        if (held != null) {
            place(player, post, category, held, index, event.inventory)
            return
        }

        val entry = category.entries.getOrNull(index) ?: return
        when (event.click) {
            ClickType.SHIFT_LEFT -> {
                category.entries.remove(entry)
                NewsManager.changed(post)
                player.sendPrefixed(player.tr("news.editor.entry-removed"))
                refresh(player, event.inventory)
            }

            ClickType.SHIFT_RIGHT -> {
                moving[player.uniqueId] = entry.id
                MenuRender.click(player)
                refresh(player, event.inventory)
            }

            ClickType.RIGHT -> {
                entry.tag = EntryTag.entries[(entry.tag.ordinal + 1) % EntryTag.entries.size]
                NewsManager.changed(post)
                MenuRender.click(player)
                refresh(player, event.inventory)
            }

            else -> MenuRender.later(player) { NewsEntryGUI.show(player, post, category, entry) }
        }
    }

    override fun onClose(event: InventoryCloseEvent) {
        super.onClose(event)
        editing.remove(event.player.uniqueId)
        moving.remove(event.player.uniqueId)
    }

    /**
     * Asks for one entry after another until [DONE_WORD].
     *
     * Each line is saved the moment it is typed, so leaving part-way — typing
     * `cancel`, or logging out — keeps everything written so far. A line with
     * no prefix takes [tag], the tag of the line before it.
     */
    private fun addEntries(player: Player, post: NewsPost, category: NewsCategory, tag: EntryTag, first: Boolean) {
        val question = if (first) {
            player.tr("gui.news-category-editor.prompt-entries", "done" to DONE_WORD)
        } else {
            player.tr("gui.news-category-editor.prompt-next", "done" to DONE_WORD)
        }

        ChatPrompt.ask(player, question) { input ->
            // The post may have been deleted by someone else while this was being typed.
            if (NewsManager.get(post.id)?.category(category.id) == null) return@ask
            if (input.equals(DONE_WORD, ignoreCase = true)) {
                show(player, post, category)
                return@ask
            }

            val (parsedTag, text) = NewsShorthand.parse(input, tag)
            val maxLength = NewsSettings.snapshot.maxEntryLength
            when {
                text.isBlank() -> Unit
                NewsRender.visibleLength(text) > maxLength ->
                    player.sendPrefixed(player.tr("news.editor.too-long", "amount" to maxLength))

                else -> {
                    val entry = NewsEntry(
                        id = NewsIds.next { id -> category.entries.any { it.id == id } },
                        tag = parsedTag,
                        text = LocalizedText(text),
                    )
                    category.entries += entry
                    NewsManager.changed(post)
                    player.sendPrefixed(player.tr("news.editor.entry-added", "entry" to NewsRender.entryLine(player, entry)))
                }
            }
            addEntries(player, post, category, parsedTag, first = false)
        }
    }

    private fun place(player: Player, post: NewsPost, category: NewsCategory, entryId: String, index: Int, inventory: Inventory) {
        moving.remove(player.uniqueId)

        val from = category.entries.indexOfFirst { it.id == entryId }
        if (from >= 0 && from != index) {
            val entry = category.entries.removeAt(from)
            val target = (if (from < index) index - 1 else index).coerceIn(0, category.entries.size)
            category.entries.add(target, entry)
            NewsManager.changed(post)
        }

        MenuRender.click(player)
        refresh(player, inventory)
    }

    private fun release(player: Player, inventory: Inventory) {
        moving.remove(player.uniqueId)
        MenuRender.click(player)
        refresh(player, inventory)
    }

    private fun icon(player: Player, entry: NewsEntry, heldId: String?): ItemStack {
        val lines = buildList {
            add(player.tr("gui.news-category.text", "text" to entry.text.main))
            add(player.tr("gui.news-settings.translated", "amount" to entry.text.translations.size))
            add("")
            when {
                heldId == entry.id -> {
                    add(player.tr("gui.news-category-editor.being-moved"))
                    add(player.tr("gui.news-category-editor.click-put-down"))
                }

                heldId != null -> add(player.tr("gui.news-category-editor.click-place-before"))

                else -> {
                    add(player.tr("gui.news-category-editor.click-entry"))
                    add(player.tr("gui.news-category-editor.right-click-entry"))
                    add(player.tr("gui.news-category-editor.shift-right-click-entry"))
                    add(player.tr("gui.news-category-editor.shift-click-entry"))
                }
            }
        }
        return NewsRender.card(
            NewsRender.tagMaterial(entry.tag),
            NewsRender.tag(player, entry.tag),
            lines,
            glow = heldId == entry.id,
        )
    }

    private fun target(player: Player): Pair<NewsPost, NewsCategory>? {
        val state = editing[player.uniqueId] ?: return null
        val post = NewsManager.get(state.postId) ?: return null
        val category = post.category(state.categoryId) ?: return null
        return post to category
    }

    companion object {
        const val ID = "news-category-editor"

        /** Typed on its own to stop adding entries and go back to the menu. */
        const val DONE_WORD = "done"

        // Offsets in the navigation row; 0, 4 and 8 belong to the page controls.
        private const val SLOT_ADD = 1
        private const val SLOT_NAME = 2
        private const val SLOT_ICON = 3
        private const val SLOT_BACK = 5

        private const val SLOT_HELD = SLOT_ADD
        private const val SLOT_MOVE_FIRST = SLOT_NAME
        private const val SLOT_MOVE_LAST = SLOT_ICON

        fun show(player: Player, post: NewsPost, category: NewsCategory): Boolean {
            val gui = GUIManager.getGUI(ID) as? NewsCategoryEditorGUI ?: return false
            gui.open(player, post, category)
            return true
        }
    }
}
