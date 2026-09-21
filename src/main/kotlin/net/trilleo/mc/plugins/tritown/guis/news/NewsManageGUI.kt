package net.trilleo.mc.plugins.tritown.guis.news

import net.trilleo.mc.plugins.tritown.commands.news.NewsCommand
import net.trilleo.mc.plugins.tritown.enums.FillMode
import net.trilleo.mc.plugins.tritown.enums.PagedLayout
import net.trilleo.mc.plugins.tritown.guis.ConfirmGUI
import net.trilleo.mc.plugins.tritown.guis.menu.MenuRender
import net.trilleo.mc.plugins.tritown.news.NewsManager
import net.trilleo.mc.plugins.tritown.news.NewsPost
import net.trilleo.mc.plugins.tritown.registration.GUIManager
import net.trilleo.mc.plugins.tritown.registration.PagedPluginGUI
import net.trilleo.mc.plugins.tritown.utils.ChatPrompt
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack

/**
 * Every post an administrator can work on: drafts first, since those are the
 * ones in progress, then everything already published.
 */
class NewsManageGUI : PagedPluginGUI(
    id = ID,
    titleKey = "gui.news-manage.title",
    rows = 6,
    fillMode = FillMode.NONE,
    layout = PagedLayout.FRAMED,
) {

    override fun getItems(player: Player): List<ItemStack> {
        val posts = NewsManager.forEditors()
        if (posts.isEmpty()) {
            return listOf(
                NewsRender.card(
                    Material.BARRIER,
                    player.tr("gui.news-manage.empty"),
                    listOf(player.tr("gui.news-manage.empty-lore")),
                )
            )
        }
        return posts.map { card(player, it) }
    }

    override fun navButtons(player: Player): Map<Int, ItemStack> = mapOf(
        MenuRender.BACK_OFFSET to NewsRender.button(player, Material.ARROW, "gui.news-manage.back", "gui.news-manage.back-lore"),
        MenuRender.EXTRA_OFFSET to NewsRender.button(player, Material.WRITABLE_BOOK, "gui.news-manage.new", "gui.news-manage.new-lore"),
    )

    override fun onNavClick(event: InventoryClickEvent, offset: Int) {
        val player = event.whoClicked as? Player ?: return
        when (offset) {
            MenuRender.BACK_OFFSET -> MenuRender.later(player) { NewsListGUI.show(player) }
            MenuRender.EXTRA_OFFSET -> create(player)
        }
    }

    override fun onContentClick(event: InventoryClickEvent, page: Int) {
        val player = event.whoClicked as? Player ?: return
        if (!player.hasPermission(NewsCommand.MANAGE_PERMISSION)) return
        val index = contentIndex(event, page) ?: return
        val post = NewsManager.forEditors().getOrNull(index) ?: return

        if (event.click == ClickType.SHIFT_LEFT || event.click == ClickType.SHIFT_RIGHT) {
            MenuRender.later(player) { confirmDelete(player, post) { NewsManageGUI.show(it) } }
        } else {
            MenuRender.later(player) { NewsEditorGUI.show(player, post) }
        }
    }

    private fun create(player: Player) {
        if (!player.hasPermission(NewsCommand.MANAGE_PERMISSION)) return
        player.closeInventory()
        ChatPrompt.ask(player, player.tr("gui.news-manage.prompt-title")) { input ->
            if (input.isBlank()) {
                show(player)
                return@ask
            }
            val post = NewsManager.create(player, input)
            player.sendPrefixed(player.tr("news.editor.created"))
            NewsEditorGUI.show(player, post)
        }
    }

    private fun card(player: Player, post: NewsPost): ItemStack {
        val lines = buildList {
            add(
                if (post.isPublished) player.tr("gui.news-manage.published")
                else player.tr("gui.news-manage.draft")
            )
            add(player.tr("gui.news-manage.id", "id" to post.id))
            add(player.tr("gui.news-manage.entries", "categories" to post.categories.size, "amount" to post.entryCount))
            addAll(NewsRender.postLines(player, post))
            add("")
            add(player.tr("gui.news-manage.click-edit"))
            add(player.tr("gui.news-manage.shift-click-delete"))
        }
        return NewsRender.card(NewsRender.postIcon(post), NewsRender.title(player, post), lines, glow = !post.isPublished)
    }

    companion object {
        const val ID = "news-manage"

        fun show(player: Player): Boolean = GUIManager.open(player, ID)

        /** Asks before deleting [post] for good. Deleting it leads here; backing out leads to [onCancel]. */
        fun confirmDelete(player: Player, post: NewsPost, onCancel: (Player) -> Unit) {
            val subject = NewsRender.card(NewsRender.postIcon(post), NewsRender.title(player, post), NewsRender.postLines(player, post))
            ConfirmGUI.show(
                player,
                title = player.tr("gui.news-manage.delete-title"),
                subject = subject,
                choices = listOf(
                    ConfirmGUI.Choice(
                        NewsRender.button(player, Material.LAVA_BUCKET, "gui.news-manage.delete", "gui.news-manage.delete-lore")
                    ) {
                        NewsManager.delete(post)
                        it.sendPrefixed(it.tr("news.editor.deleted"))
                        show(it)
                    },
                ),
                onCancel = onCancel,
            )
        }
    }
}
