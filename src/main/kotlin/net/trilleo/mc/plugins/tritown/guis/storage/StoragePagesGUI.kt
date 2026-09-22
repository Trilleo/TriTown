package net.trilleo.mc.plugins.tritown.guis.storage

import net.kyori.adventure.text.Component
import net.trilleo.mc.plugins.tritown.enums.FillMode
import net.trilleo.mc.plugins.tritown.enums.PagedLayout
import net.trilleo.mc.plugins.tritown.guis.admin.PanelRender
import net.trilleo.mc.plugins.tritown.guis.menu.MenuRender
import net.trilleo.mc.plugins.tritown.registration.GUIManager
import net.trilleo.mc.plugins.tritown.registration.PagedPluginGUI
import net.trilleo.mc.plugins.tritown.storage.StorageManager
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
 * Every page of a storage at once: what each is called, how full it is and what
 * it mostly holds, so a player can go straight to the page they want. The offer
 * of another page sits at the end of the list, where a new page would appear.
 */
class StoragePagesGUI : PagedPluginGUI(
    id = ID,
    titleKey = "gui.storage-pages.title",
    rows = 6,
    fillMode = FillMode.NONE,
    layout = PagedLayout.FRAMED,
) {

    /** Whose storage [viewer] is looking over, which page they came from, and whether they may change it. */
    private data class Context(val owner: UUID, val current: Int, val readOnly: Boolean)

    private val contexts = ConcurrentHashMap<UUID, Context>()

    override fun title(player: Player): Component {
        val context = contexts[player.uniqueId] ?: return super.title(player)
        if (context.owner == player.uniqueId) return super.title(player)
        return ComponentUtil.parse(
            player.tr("gui.storage-pages.title-inspect", "owner" to StorageRender.ownerName(context.owner))
        )
    }

    override fun getItems(player: Player): List<ItemStack> {
        val context = contexts[player.uniqueId] ?: return emptyList()
        val storage = StorageManager.get(context.owner) ?: return emptyList()

        val pages = (0 until StorageManager.pageCount(storage)).map { index ->
            StorageRender.pageCard(player, storage.pageOrNull(index), index, current = index == context.current)
        }
        val buy = if (canBuy(player, context)) StorageRender.buyCard(player, storage) else null
        return pages + listOfNotNull(buy)
    }

    override fun onContentClick(event: InventoryClickEvent, page: Int) {
        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        val context = contexts[player.uniqueId] ?: return
        val storage = StorageManager.get(context.owner) ?: return

        val index = contentIndex(event, page) ?: return
        val pages = StorageManager.pageCount(storage)
        when {
            index < pages -> MenuRender.later(player) {
                StorageGUI.open(player, context.owner, index, context.readOnly)
            }

            index == pages && canBuy(player, context) && StorageManager.nextPageCost(storage) != null ->
                MenuRender.later(player) { StorageGUI.offerPage(player, context.current) }
        }
    }

    override fun navButtons(player: Player): Map<Int, ItemStack> = mapOf(
        MenuRender.BACK_OFFSET to PanelRender.card(
            Material.ARROW,
            player.tr("gui.storage-pages.back"),
            listOf(player.tr("gui.storage-pages.back-lore")),
        ),
    )

    override fun onNavClick(event: InventoryClickEvent, offset: Int) {
        val player = event.whoClicked as? Player ?: return
        val context = contexts[player.uniqueId] ?: return
        if (offset == MenuRender.BACK_OFFSET) {
            MenuRender.later(player) { StorageGUI.open(player, context.owner, context.current, context.readOnly) }
        }
    }

    override fun onClose(event: InventoryCloseEvent) {
        super.onClose(event)
        // An offline player's storage was loaded only to be shown here.
        contexts.remove(event.player.uniqueId)?.let { StorageManager.unloadIfIdle(it.owner) }
    }

    private fun canBuy(player: Player, context: Context): Boolean = context.owner == player.uniqueId && !context.readOnly

    companion object {
        const val ID = "storage-pages"

        /** Opens the overview of [owner]'s storage for [viewer], who came from page [current]. */
        fun show(viewer: Player, owner: UUID, current: Int, readOnly: Boolean): Boolean {
            val gui = GUIManager.getGUI(ID) as? StoragePagesGUI ?: return false
            gui.contexts[viewer.uniqueId] = Context(owner, current, readOnly)
            return GUIManager.open(viewer, ID)
        }
    }
}
