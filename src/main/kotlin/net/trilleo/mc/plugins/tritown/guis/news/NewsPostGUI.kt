package net.trilleo.mc.plugins.tritown.guis.news

import net.kyori.adventure.text.Component
import net.trilleo.mc.plugins.tritown.commands.news.NewsCommand
import net.trilleo.mc.plugins.tritown.enums.FillMode
import net.trilleo.mc.plugins.tritown.enums.PagedLayout
import net.trilleo.mc.plugins.tritown.guis.menu.MenuRender
import net.trilleo.mc.plugins.tritown.news.NewsCategory
import net.trilleo.mc.plugins.tritown.news.NewsManager
import net.trilleo.mc.plugins.tritown.news.NewsPost
import net.trilleo.mc.plugins.tritown.news.NewsReadState
import net.trilleo.mc.plugins.tritown.registration.GUIManager
import net.trilleo.mc.plugins.tritown.registration.PagedPluginGUI
import net.trilleo.mc.plugins.tritown.utils.ComponentUtil
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.ItemStack
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * One post: its card in the middle of the top row, then a card per category,
 * in the post's order, listing its entries — which is how most posts are read
 * in full without a click.
 *
 * A category too long for its card is cut short with a count of the rest, and
 * clicking it lists the entries one by one. The book button shows the whole
 * post as pages, which reads better once a post runs long.
 *
 * Opened as a preview from the editor, the post shows as a player will see it,
 * but is not marked read and leads back to the editor.
 */
class NewsPostGUI : PagedPluginGUI(
    id = ID,
    titleKey = "gui.news-post.title",
    rows = 6,
    fillMode = FillMode.NONE,
    layout = PagedLayout.FRAMED,
) {

    private class Viewing(val postId: String, val preview: Boolean)

    private val viewing = ConcurrentHashMap<UUID, Viewing>()

    fun open(player: Player, post: NewsPost, preview: Boolean) {
        viewing[player.uniqueId] = Viewing(post.id, preview)
        if (!preview && post.isPublished) NewsReadState.markRead(player, post)
        GUIManager.open(player, ID)
    }

    override fun title(player: Player): Component {
        val post = postOf(player) ?: return super.title(player)
        return ComponentUtil.parse(player.tr("gui.news-post.title", "title" to NewsRender.text(player, post.title)))
    }

    override fun getItems(player: Player): List<ItemStack> {
        val post = postOf(player) ?: return emptyList()
        return categories(post).map { category(player, it) }
    }

    override fun topButtons(player: Player): Map<Int, ItemStack> {
        val post = postOf(player) ?: return emptyMap()
        return mapOf(HEADER_OFFSET to header(player, post))
    }

    override fun navButtons(player: Player): Map<Int, ItemStack> {
        val state = viewing[player.uniqueId] ?: return emptyMap()
        return buildMap {
            put(
                MenuRender.BACK_OFFSET,
                if (state.preview) {
                    NewsRender.button(player, Material.ARROW, "gui.news-post.back-editor", "gui.news-post.back-editor-lore")
                } else {
                    NewsRender.button(player, Material.ARROW, "gui.news-post.back", "gui.news-post.back-lore")
                },
            )
            put(MenuRender.EXTRA_OFFSET, NewsRender.button(player, Material.WRITTEN_BOOK, "gui.news-post.book", "gui.news-post.book-lore"))
            if (!state.preview && player.hasPermission(NewsCommand.MANAGE_PERMISSION)) {
                put(EDIT_OFFSET, NewsRender.button(player, Material.WRITABLE_BOOK, "gui.news-post.edit", "gui.news-post.edit-lore"))
            }
        }
    }

    override fun onNavClick(event: InventoryClickEvent, offset: Int) {
        val player = event.whoClicked as? Player ?: return
        val state = viewing[player.uniqueId] ?: return
        val post = postOf(player) ?: return

        when (offset) {
            MenuRender.BACK_OFFSET -> MenuRender.later(player) {
                if (state.preview) NewsEditorGUI.show(player, post) else NewsListGUI.show(player)
            }

            MenuRender.EXTRA_OFFSET -> MenuRender.later(player) {
                player.closeInventory()
                player.openBook(NewsRender.book(player, post))
            }

            EDIT_OFFSET -> if (!state.preview && player.hasPermission(NewsCommand.MANAGE_PERMISSION)) {
                MenuRender.later(player) { NewsEditorGUI.show(player, post) }
            }
        }
    }

    override fun onContentClick(event: InventoryClickEvent, page: Int) {
        val player = event.whoClicked as? Player ?: return
        val state = viewing[player.uniqueId] ?: return
        val post = postOf(player) ?: return

        val index = contentIndex(event, page) ?: return
        val category = categories(post).getOrNull(index) ?: return
        MenuRender.later(player) { NewsCategoryGUI.show(player, post, category, state.preview) }
    }

    override fun onClose(event: InventoryCloseEvent) {
        super.onClose(event)
        viewing.remove(event.player.uniqueId)
    }

    private fun header(player: Player, post: NewsPost): ItemStack {
        val lines = buildList {
            if (viewing[player.uniqueId]?.preview == true) {
                add(player.tr(if (post.isPublished) "gui.news-post.preview-published" else "gui.news-post.preview-draft"))
            }
            addAll(NewsRender.postLines(player, post))
        }
        return NewsRender.card(NewsRender.postIcon(post), NewsRender.title(player, post), lines)
    }

    private fun category(player: Player, category: NewsCategory): ItemStack {
        val lines = buildList {
            category.entries.take(NewsRender.LORE_ENTRIES).forEach { add(NewsRender.entryLine(player, it)) }
            val rest = category.entries.size - NewsRender.LORE_ENTRIES
            if (rest > 0) add(player.tr("gui.news-post.more", "amount" to rest))
            add("")
            add(player.tr("gui.news-post.click-category"))
        }
        return NewsRender.card(NewsRender.categoryIcon(category), NewsRender.text(player, category.name), lines)
    }

    /** A category with nothing in it yet has nothing to show a reader. */
    private fun categories(post: NewsPost): List<NewsCategory> = post.categories.filter { it.entries.isNotEmpty() }

    private fun postOf(player: Player): NewsPost? = viewing[player.uniqueId]?.postId?.let(NewsManager::get)

    companion object {
        const val ID = "news-post"

        private const val EDIT_OFFSET = 7
        private const val HEADER_OFFSET = 4

        fun show(player: Player, post: NewsPost, preview: Boolean): Boolean {
            val gui = GUIManager.getGUI(ID) as? NewsPostGUI ?: return false
            gui.open(player, post, preview)
            return true
        }
    }
}
