package net.trilleo.mc.plugins.tritown.guis.news

import net.trilleo.mc.plugins.tritown.commands.news.NewsCommand
import net.trilleo.mc.plugins.tritown.enums.FillMode
import net.trilleo.mc.plugins.tritown.enums.PagedLayout
import net.trilleo.mc.plugins.tritown.guis.menu.MainMenuGUI
import net.trilleo.mc.plugins.tritown.guis.menu.MenuRender
import net.trilleo.mc.plugins.tritown.news.NewsManager
import net.trilleo.mc.plugins.tritown.news.NewsPost
import net.trilleo.mc.plugins.tritown.news.NewsReadState
import net.trilleo.mc.plugins.tritown.registration.GUIManager
import net.trilleo.mc.plugins.tritown.registration.PagedPluginGUI
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack

/**
 * Every published post, pinned ones first and then the newest, each glowing
 * until the viewer has read it.
 *
 * Opened from the main menu, with `/tritown news`, or from the link in the
 * join summary. Administrators also find their way into the editor here.
 */
class NewsListGUI : PagedPluginGUI(
    id = ID,
    titleKey = "gui.news-list.title",
    rows = 6,
    fillMode = FillMode.NONE,
    layout = PagedLayout.FRAMED,
) {

    override fun getItems(player: Player): List<ItemStack> {
        val posts = NewsManager.published()
        if (posts.isEmpty()) {
            return listOf(
                NewsRender.card(
                    Material.BARRIER,
                    player.tr("gui.news-list.empty"),
                    listOf(player.tr("gui.news-list.empty-lore")),
                )
            )
        }

        val unread = NewsReadState.unread(player).mapTo(mutableSetOf()) { it.id }
        return posts.map { card(player, it, it.id in unread) }
    }

    override fun navButtons(player: Player): Map<Int, ItemStack> = buildMap {
        put(MenuRender.BACK_OFFSET, MenuRender.back(player))
        if (NewsReadState.unreadCount(player) > 0) {
            put(
                MenuRender.EXTRA_OFFSET,
                NewsRender.button(player, Material.MILK_BUCKET, "gui.news-list.read-all", "gui.news-list.read-all-lore"),
            )
        }
        if (player.hasPermission(NewsCommand.MANAGE_PERMISSION)) {
            put(
                MANAGE_OFFSET,
                NewsRender.button(player, Material.WRITABLE_BOOK, "gui.news-list.manage", "gui.news-list.manage-lore"),
            )
        }
    }

    override fun onNavClick(event: InventoryClickEvent, offset: Int) {
        val player = event.whoClicked as? Player ?: return
        when (offset) {
            MenuRender.BACK_OFFSET -> MenuRender.later(player) { MainMenuGUI.show(player) }
            MenuRender.EXTRA_OFFSET -> {
                NewsReadState.markAllRead(player)
                MenuRender.click(player)
                refresh(player, event.inventory)
            }

            MANAGE_OFFSET -> if (player.hasPermission(NewsCommand.MANAGE_PERMISSION)) {
                MenuRender.later(player) { NewsManageGUI.show(player) }
            }
        }
    }

    override fun onContentClick(event: InventoryClickEvent, page: Int) {
        val player = event.whoClicked as? Player ?: return
        val index = contentIndex(event, page) ?: return
        val post = NewsManager.published().getOrNull(index) ?: return
        MenuRender.later(player) { NewsPostGUI.show(player, post, preview = false) }
    }

    private fun card(player: Player, post: NewsPost, unread: Boolean): ItemStack {
        val lines = buildList {
            if (unread) add(player.tr("gui.news-list.unread"))
            addAll(NewsRender.postLines(player, post))
            add("")
            add(player.tr("gui.news-list.click-read"))
        }
        return NewsRender.card(NewsRender.postIcon(post), NewsRender.title(player, post), lines, glow = unread)
    }

    companion object {
        const val ID = "news-list"

        private const val MANAGE_OFFSET = 7

        fun show(player: Player): Boolean = GUIManager.open(player, ID)
    }
}
