package net.trilleo.mc.plugins.tritown.guis.news

import net.kyori.adventure.text.Component
import net.trilleo.mc.plugins.tritown.enums.FillMode
import net.trilleo.mc.plugins.tritown.enums.PagedLayout
import net.trilleo.mc.plugins.tritown.guis.menu.MenuRender
import net.trilleo.mc.plugins.tritown.news.NewsCategory
import net.trilleo.mc.plugins.tritown.news.NewsManager
import net.trilleo.mc.plugins.tritown.news.NewsPost
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

/** One category of a post, an entry to an item, for a category too long to read off its card. */
class NewsCategoryGUI : PagedPluginGUI(
    id = ID,
    titleKey = "gui.news-category.title",
    rows = 6,
    fillMode = FillMode.NONE,
    layout = PagedLayout.FRAMED,
) {

    private class Viewing(val postId: String, val categoryId: String, val preview: Boolean)

    private val viewing = ConcurrentHashMap<UUID, Viewing>()

    fun open(player: Player, post: NewsPost, category: NewsCategory, preview: Boolean) {
        viewing[player.uniqueId] = Viewing(post.id, category.id, preview)
        GUIManager.open(player, ID)
    }

    override fun title(player: Player): Component {
        val category = categoryOf(player) ?: return super.title(player)
        return ComponentUtil.parse(
            player.tr(
                "gui.news-category.title",
                "name" to NewsRender.text(player, category.name)
            )
        )
    }

    override fun getItems(player: Player): List<ItemStack> {
        val category = categoryOf(player) ?: return emptyList()
        return category.entries.map { entry ->
            NewsRender.card(
                NewsRender.tagMaterial(entry.tag),
                NewsRender.tag(player, entry.tag),
                listOf(player.tr("gui.news-category.text", "text" to NewsRender.text(player, entry.text))),
            )
        }
    }

    override fun navButtons(player: Player): Map<Int, ItemStack> = mapOf(
        MenuRender.BACK_OFFSET to NewsRender.button(
            player,
            Material.ARROW,
            "gui.news-category.back",
            "gui.news-category.back-lore"
        ),
    )

    override fun onNavClick(event: InventoryClickEvent, offset: Int) {
        if (offset != MenuRender.BACK_OFFSET) return
        val player = event.whoClicked as? Player ?: return
        val state = viewing[player.uniqueId] ?: return
        val post = NewsManager.get(state.postId) ?: return
        MenuRender.later(player) { NewsPostGUI.show(player, post, state.preview) }
    }

    override fun onClose(event: InventoryCloseEvent) {
        super.onClose(event)
        viewing.remove(event.player.uniqueId)
    }

    private fun categoryOf(player: Player): NewsCategory? {
        val state = viewing[player.uniqueId] ?: return null
        return NewsManager.get(state.postId)?.category(state.categoryId)
    }

    companion object {
        const val ID = "news-category"

        fun show(player: Player, post: NewsPost, category: NewsCategory, preview: Boolean): Boolean {
            val gui = GUIManager.getGUI(ID) as? NewsCategoryGUI ?: return false
            gui.open(player, post, category, preview)
            return true
        }
    }
}
