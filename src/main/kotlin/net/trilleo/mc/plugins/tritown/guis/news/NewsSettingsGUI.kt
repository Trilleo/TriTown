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
 * A post as a whole: its title, summary, label, icon, and whether it is pinned
 * to the top of the news.
 *
 * The icon is chosen by clicking any item in your own inventory, the way a
 * shop entry is added: only its type is read, and the item stays where it is.
 */
class NewsSettingsGUI : PluginGUI(
    id = ID,
    titleKey = "gui.news-settings.title",
    rows = 6,
    fillMode = FillMode.NONE,
) {

    private val editing = ConcurrentHashMap<UUID, String>()

    fun open(player: Player, post: NewsPost) {
        editing[player.uniqueId] = post.id
        GUIManager.open(player, ID)
    }

    override fun setup(player: Player, inventory: Inventory) {
        val post = postOf(player) ?: return

        GUIFrame.draw(inventory, SLOTS)
        inventory.setItem(SLOT_TITLE, title(player, post))
        inventory.setItem(SLOT_SUMMARY, summary(player, post))
        inventory.setItem(SLOT_LABEL, label(player, post))
        inventory.setItem(SLOT_ICON, icon(player, post))
        inventory.setItem(SLOT_PIN, pin(player, post))
        inventory.setItem(
            SLOT_BACK,
            NewsRender.button(player, Material.ARROW, "gui.news-settings.back", "gui.news-settings.back-lore")
        )
    }

    override fun onClick(event: InventoryClickEvent) {
        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        if (!player.hasPermission(NewsCommand.MANAGE_PERMISSION)) return
        val post = postOf(player) ?: return

        if (event.clickedInventory === player.inventory) {
            val item = event.currentItem ?: return
            if (item.type.isAir) return
            post.icon = item.type.name
            NewsManager.changed(post)
            MenuRender.click(player)
            setup(player, event.inventory)
            return
        }
        if (event.clickedInventory !== event.view.topInventory) return

        val right = event.click == ClickType.RIGHT || event.click == ClickType.SHIFT_RIGHT
        val shift = event.click == ClickType.SHIFT_LEFT || event.click == ClickType.SHIFT_RIGHT
        val reopen: () -> Unit = { show(player, post) }

        when (event.rawSlot) {
            SLOT_TITLE -> if (right) {
                translations(player, post) { it.title }
            } else {
                NewsRender.prompt(player, player.tr("gui.news-settings.prompt-title"), null, reopen) {
                    post.title.main = it
                    NewsManager.changed(post)
                }
            }

            SLOT_SUMMARY -> when {
                shift -> {
                    post.summary = null
                    NewsManager.changed(post)
                    MenuRender.click(player)
                    setup(player, event.inventory)
                }

                right && post.summary != null -> translations(player, post) { it.summary }
                else -> NewsRender.prompt(player, player.tr("gui.news-settings.prompt-summary"), null, reopen) {
                    val summary = post.summary ?: LocalizedText().also { created -> post.summary = created }
                    summary.main = it
                    NewsManager.changed(post)
                }
            }

            SLOT_LABEL -> if (right) {
                post.label = null
                NewsManager.changed(post)
                MenuRender.click(player)
                setup(player, event.inventory)
            } else {
                NewsRender.prompt(player, player.tr("gui.news-settings.prompt-label"), MAX_LABEL, reopen) {
                    post.label = it
                    NewsManager.changed(post)
                }
            }

            SLOT_ICON -> if (right) {
                post.icon = NewsPost.DEFAULT_ICON
                NewsManager.changed(post)
                MenuRender.click(player)
                setup(player, event.inventory)
            }

            SLOT_PIN -> {
                post.pinned = !post.pinned
                NewsManager.changed(post)
                MenuRender.click(player)
                setup(player, event.inventory)
            }

            SLOT_BACK -> MenuRender.later(player) { NewsEditorGUI.show(player, post) }
        }
    }

    override fun onClose(event: InventoryCloseEvent) {
        editing.remove(event.player.uniqueId)
    }

    private fun translations(player: Player, post: NewsPost, field: (NewsPost) -> LocalizedText?) {
        MenuRender.later(player) {
            NewsTextGUI.show(player, NewsTextGUI.Target(post.id, field, null) { show(it, post) })
        }
    }

    private fun title(player: Player, post: NewsPost): ItemStack = NewsRender.card(
        Material.NAME_TAG,
        player.tr("gui.news-settings.title-name"),
        listOf(
            player.tr("gui.news-settings.value", "value" to post.title.main),
            player.tr("gui.news-settings.translated", "amount" to post.title.translations.size),
            "",
            player.tr("gui.news-settings.click-edit"),
            player.tr("gui.news-settings.right-click-translate"),
        ),
    )

    private fun summary(player: Player, post: NewsPost): ItemStack {
        val summary = post.summary
        val lines = buildList {
            add(player.tr("gui.news-settings.summary-lore"))
            add(
                if (summary == null) player.tr("gui.news-settings.value", "value" to player.tr("common.none"))
                else player.tr("gui.news-settings.value", "value" to summary.main)
            )
            summary?.let { add(player.tr("gui.news-settings.translated", "amount" to it.translations.size)) }
            add("")
            add(player.tr("gui.news-settings.click-edit"))
            if (summary != null) {
                add(player.tr("gui.news-settings.right-click-translate"))
                add(player.tr("gui.news-settings.shift-click-clear"))
            }
        }
        return NewsRender.card(Material.BOOK, player.tr("gui.news-settings.summary"), lines)
    }

    private fun label(player: Player, post: NewsPost): ItemStack = NewsRender.card(
        Material.OAK_SIGN,
        player.tr("gui.news-settings.label"),
        listOf(
            player.tr("gui.news-settings.label-lore"),
            player.tr("gui.news-settings.value", "value" to (post.label ?: player.tr("common.none"))),
            "",
            player.tr("gui.news-settings.click-edit"),
            player.tr("gui.news-settings.right-click-clear"),
        ),
    )

    private fun icon(player: Player, post: NewsPost): ItemStack = NewsRender.card(
        NewsRender.postIcon(post),
        player.tr("gui.news-settings.icon"),
        listOf(
            player.tr("gui.news-settings.icon-lore"),
            "",
            player.tr("gui.news-settings.right-click-reset"),
        ),
    )

    private fun pin(player: Player, post: NewsPost): ItemStack = NewsRender.card(
        if (post.pinned) Material.LIME_DYE else Material.GRAY_DYE,
        player.tr("gui.news-settings.pin"),
        listOf(
            player.tr(if (post.pinned) "gui.news-settings.pinned" else "gui.news-settings.not-pinned"),
            "",
            player.tr("gui.news-settings.click-toggle"),
        ),
        glow = post.pinned,
    )

    private fun postOf(player: Player): NewsPost? = editing[player.uniqueId]?.let(NewsManager::get)

    companion object {
        const val ID = "news-settings"

        private const val SLOT_TITLE = 20
        private const val SLOT_SUMMARY = 22
        private const val SLOT_LABEL = 24
        private const val SLOT_ICON = 30
        private const val SLOT_PIN = 32
        private const val SLOT_BACK = 49

        private val SLOTS = setOf(SLOT_TITLE, SLOT_SUMMARY, SLOT_LABEL, SLOT_ICON, SLOT_PIN)

        /** A label names a version or a season, so it is kept to a few words. */
        private const val MAX_LABEL = 32

        fun show(player: Player, post: NewsPost): Boolean {
            val gui = GUIManager.getGUI(ID) as? NewsSettingsGUI ?: return false
            gui.open(player, post)
            return true
        }
    }
}
