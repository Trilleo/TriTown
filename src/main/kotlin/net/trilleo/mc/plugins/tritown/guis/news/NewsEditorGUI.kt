package net.trilleo.mc.plugins.tritown.guis.news

import net.kyori.adventure.text.Component
import net.trilleo.mc.plugins.tritown.commands.news.NewsCommand
import net.trilleo.mc.plugins.tritown.enums.FillMode
import net.trilleo.mc.plugins.tritown.enums.PagedLayout
import net.trilleo.mc.plugins.tritown.guis.ConfirmGUI
import net.trilleo.mc.plugins.tritown.guis.menu.MenuRender
import net.trilleo.mc.plugins.tritown.news.LocalizedText
import net.trilleo.mc.plugins.tritown.news.NewsCategory
import net.trilleo.mc.plugins.tritown.news.NewsIds
import net.trilleo.mc.plugins.tritown.news.NewsManager
import net.trilleo.mc.plugins.tritown.news.NewsPost
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
 * One post's categories, in the order readers see them, and everything that
 * is done to the post as a whole: its settings, a preview, publishing and
 * deleting it.
 *
 * Categories are moved the way shop entries are: a right click marks the one
 * being moved, and the next click says where it goes. While one is held, the
 * first buttons of the navigation row turn into the move's own controls.
 */
class NewsEditorGUI : PagedPluginGUI(
    id = ID,
    titleKey = "gui.news-editor.title",
    rows = 6,
    fillMode = FillMode.NONE,
    layout = PagedLayout.FRAMED,
) {

    private val editing = ConcurrentHashMap<UUID, String>()

    /** The category each administrator is part-way through moving, by its id. */
    private val moving = ConcurrentHashMap<UUID, String>()

    fun open(player: Player, post: NewsPost) {
        editing[player.uniqueId] = post.id
        moving.remove(player.uniqueId)
        GUIManager.open(player, ID)
    }

    override fun title(player: Player): Component {
        val post = postOf(player) ?: return super.title(player)
        return ComponentUtil.parse(player.tr("gui.news-editor.title", "title" to NewsRender.text(player, post.title)))
    }

    override fun getItems(player: Player): List<ItemStack> {
        val post = postOf(player) ?: return emptyList()
        val held = moving[player.uniqueId]
        return post.categories.map { icon(player, it, held) }
    }

    override fun navButtons(player: Player): Map<Int, ItemStack> {
        val post = postOf(player) ?: return emptyMap()
        val back = NewsRender.button(player, Material.ARROW, "gui.news-editor.back", "gui.news-editor.back-lore")

        val held = moving[player.uniqueId]?.let(post::category)
        if (held != null) {
            return mapOf(
                SLOT_HELD to NewsRender.glowing(
                    NewsRender.card(
                        NewsRender.categoryIcon(held),
                        NewsRender.text(player, held.name),
                        listOf(player.tr("gui.news-editor.holding"), player.tr("gui.news-editor.click-put-down")),
                    )
                ),
                SLOT_MOVE_FIRST to NewsRender.button(player, Material.SPECTRAL_ARROW, "gui.news-editor.move-first", "gui.news-editor.move-first-lore"),
                SLOT_MOVE_LAST to NewsRender.button(player, Material.TIPPED_ARROW, "gui.news-editor.move-last", "gui.news-editor.move-last-lore"),
                SLOT_BACK to back,
            )
        }

        return mapOf(
            SLOT_ADD to NewsRender.button(player, Material.BOOKSHELF, "gui.news-editor.add", "gui.news-editor.add-lore"),
            SLOT_SETTINGS to NewsRender.button(player, Material.COMPARATOR, "gui.news-editor.settings", "gui.news-editor.settings-lore"),
            SLOT_PREVIEW to NewsRender.button(player, Material.SPYGLASS, "gui.news-editor.preview", "gui.news-editor.preview-lore"),
            SLOT_BACK to back,
            SLOT_PUBLISH to if (post.isPublished) {
                NewsRender.button(player, Material.GRAY_DYE, "gui.news-editor.unpublish", "gui.news-editor.unpublish-lore")
            } else {
                NewsRender.button(player, Material.LIME_DYE, "gui.news-editor.publish", "gui.news-editor.publish-lore")
            },
            SLOT_DELETE to NewsRender.button(player, Material.LAVA_BUCKET, "gui.news-editor.delete", "gui.news-editor.delete-lore"),
        )
    }

    override fun onNavClick(event: InventoryClickEvent, offset: Int) {
        val player = event.whoClicked as? Player ?: return
        if (!player.hasPermission(NewsCommand.MANAGE_PERMISSION)) return
        val post = postOf(player) ?: return
        val held = moving[player.uniqueId]

        if (held != null) {
            when (offset) {
                SLOT_HELD -> release(player, event.inventory)
                SLOT_MOVE_FIRST -> place(player, post, held, 0, event.inventory)
                SLOT_MOVE_LAST -> place(player, post, held, post.categories.size, event.inventory)
                SLOT_BACK -> MenuRender.later(player) { NewsManageGUI.show(player) }
            }
            return
        }

        when (offset) {
            SLOT_ADD -> addCategory(player, post)
            SLOT_SETTINGS -> MenuRender.later(player) { NewsSettingsGUI.show(player, post) }
            SLOT_PREVIEW -> MenuRender.later(player) { NewsPostGUI.show(player, post, preview = true) }
            SLOT_BACK -> MenuRender.later(player) { NewsManageGUI.show(player) }
            SLOT_PUBLISH -> if (post.isPublished) {
                NewsManager.unpublish(post)
                player.sendPrefixed(player.tr("news.editor.unpublished"))
                MenuRender.click(player)
                refresh(player, event.inventory)
            } else if (post.entryCount == 0) {
                player.sendPrefixed(player.tr("news.editor.publish-empty"))
            } else {
                MenuRender.later(player) { confirmPublish(player, post) }
            }

            SLOT_DELETE -> MenuRender.later(player) { NewsManageGUI.confirmDelete(player, post) { show(it, post) } }
        }
    }

    override fun onContentClick(event: InventoryClickEvent, page: Int) {
        val player = event.whoClicked as? Player ?: return
        if (!player.hasPermission(NewsCommand.MANAGE_PERMISSION)) return
        val post = postOf(player) ?: return
        val index = contentIndex(event, page) ?: return

        val held = moving[player.uniqueId]
        if (held != null) {
            place(player, post, held, index, event.inventory)
            return
        }

        val category = post.categories.getOrNull(index) ?: return
        when (event.click) {
            ClickType.SHIFT_LEFT -> MenuRender.later(player) { confirmRemove(player, post, category) }
            ClickType.RIGHT, ClickType.SHIFT_RIGHT -> {
                moving[player.uniqueId] = category.id
                MenuRender.click(player)
                refresh(player, event.inventory)
            }

            else -> MenuRender.later(player) { NewsCategoryEditorGUI.show(player, post, category) }
        }
    }

    override fun onClose(event: InventoryCloseEvent) {
        super.onClose(event)
        editing.remove(event.player.uniqueId)
        moving.remove(event.player.uniqueId)
    }

    private fun addCategory(player: Player, post: NewsPost) {
        player.closeInventory()
        ChatPrompt.ask(player, player.tr("gui.news-editor.prompt-category")) { input ->
            if (input.isNotBlank()) {
                val category = NewsCategory(
                    id = NewsIds.next { id -> post.categories.any { it.id == id } },
                    name = LocalizedText(input),
                )
                post.categories += category
                NewsManager.changed(post)
                NewsCategoryEditorGUI.show(player, post, category)
            } else {
                show(player, post)
            }
        }
    }

    /**
     * Publishing is the one step players see, so it is asked about first —
     * and whether to announce it, since a small post does not need to take
     * over everyone's screen.
     */
    private fun confirmPublish(player: Player, post: NewsPost) {
        val subject = NewsRender.card(NewsRender.postIcon(post), NewsRender.title(player, post), NewsRender.postLines(player, post))
        ConfirmGUI.show(
            player,
            title = player.tr("gui.news-editor.publish-title"),
            subject = subject,
            choices = listOf(
                ConfirmGUI.Choice(NewsRender.button(player, Material.BELL, "gui.news-editor.publish-announce", "gui.news-editor.publish-announce-lore")) {
                    publish(it, post, announce = true)
                },
                ConfirmGUI.Choice(NewsRender.button(player, Material.LIME_DYE, "gui.news-editor.publish-quiet", "gui.news-editor.publish-quiet-lore")) {
                    publish(it, post, announce = false)
                },
            ),
            onCancel = { show(it, post) },
        )
    }

    private fun publish(player: Player, post: NewsPost, announce: Boolean) {
        NewsManager.publish(post, announce, player)
        player.sendPrefixed(player.tr("news.editor.published"))
        show(player, post)
    }

    private fun confirmRemove(player: Player, post: NewsPost, category: NewsCategory) {
        ConfirmGUI.show(
            player,
            title = player.tr("gui.news-editor.remove-title"),
            subject = icon(player, category, null, hints = false),
            choices = listOf(
                ConfirmGUI.Choice(NewsRender.button(player, Material.LAVA_BUCKET, "gui.news-editor.remove", "gui.news-editor.remove-lore")) {
                    post.categories.remove(category)
                    NewsManager.changed(post)
                    it.sendPrefixed(it.tr("news.editor.category-removed"))
                    show(it, post)
                },
            ),
            onCancel = { show(it, post) },
        )
    }

    /**
     * Moves the held category to [index], landing in front of whatever was
     * clicked — the same rule the shop editor follows, which is why the target
     * shifts back a place when moving forwards.
     */
    private fun place(player: Player, post: NewsPost, categoryId: String, index: Int, inventory: Inventory) {
        moving.remove(player.uniqueId)

        val from = post.categories.indexOfFirst { it.id == categoryId }
        if (from >= 0 && from != index) {
            val category = post.categories.removeAt(from)
            val target = (if (from < index) index - 1 else index).coerceIn(0, post.categories.size)
            post.categories.add(target, category)
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

    private fun icon(player: Player, category: NewsCategory, heldId: String?, hints: Boolean = true): ItemStack {
        val lines = buildList {
            add(player.tr("gui.news-editor.entries", "amount" to category.entries.size))
            category.entries.take(NewsRender.LORE_ENTRIES).forEach { add(NewsRender.entryLine(player, it)) }
            val rest = category.entries.size - NewsRender.LORE_ENTRIES
            if (rest > 0) add(player.tr("gui.news-post.more", "amount" to rest))
            if (!hints) return@buildList

            add("")
            when {
                heldId == category.id -> {
                    add(player.tr("gui.news-editor.being-moved"))
                    add(player.tr("gui.news-editor.click-put-down"))
                }

                heldId != null -> add(player.tr("gui.news-editor.click-place-before"))

                else -> {
                    add(player.tr("gui.news-editor.click-category"))
                    add(player.tr("gui.news-editor.right-click-category"))
                    add(player.tr("gui.news-editor.shift-click-category"))
                }
            }
        }
        return NewsRender.card(
            NewsRender.categoryIcon(category),
            NewsRender.text(player, category.name),
            lines,
            glow = heldId == category.id,
        )
    }

    private fun postOf(player: Player): NewsPost? = editing[player.uniqueId]?.let(NewsManager::get)

    companion object {
        const val ID = "news-editor"

        // Offsets in the navigation row; 0, 4 and 8 belong to the page controls.
        private const val SLOT_ADD = 1
        private const val SLOT_SETTINGS = 2
        private const val SLOT_PREVIEW = 3
        private const val SLOT_BACK = 5
        private const val SLOT_PUBLISH = 6
        private const val SLOT_DELETE = 7

        private const val SLOT_HELD = SLOT_ADD
        private const val SLOT_MOVE_FIRST = SLOT_SETTINGS
        private const val SLOT_MOVE_LAST = SLOT_PREVIEW

        fun show(player: Player, post: NewsPost): Boolean {
            val gui = GUIManager.getGUI(ID) as? NewsEditorGUI ?: return false
            gui.open(player, post)
            return true
        }
    }
}
