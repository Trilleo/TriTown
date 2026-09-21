package net.trilleo.mc.plugins.tritown.guis.shop

import net.trilleo.mc.plugins.tritown.enums.FillMode
import net.trilleo.mc.plugins.tritown.enums.PagedLayout
import net.trilleo.mc.plugins.tritown.registration.GUIManager
import net.trilleo.mc.plugins.tritown.registration.PagedPluginGUI
import net.trilleo.mc.plugins.tritown.shops.ShopDefinition
import net.trilleo.mc.plugins.tritown.shops.ShopManager
import net.trilleo.mc.plugins.tritown.utils.LoreUtil
import net.trilleo.mc.plugins.tritown.utils.itemStack
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack

/**
 * Every shop on the server, for an administrator to pick one to work on.
 *
 * Read straight out of [ShopManager] on each render, because the list is short
 * and an administrator who has just created a shop expects to see it.
 */
class ShopListGUI : PagedPluginGUI(
    id = ID,
    titleKey = "gui.shop-list.title",
    rows = 6,
    fillMode = FillMode.NONE,
    layout = PagedLayout.FRAMED,
) {

    override fun getItems(player: Player): List<ItemStack> {
        val shops = ShopManager.all()
        if (shops.isEmpty()) {
            return listOf(
                itemStack(Material.BARRIER) {
                    name(player.tr("gui.shop-list.empty"))
                    meta { lore(LoreUtil.wrapLore(player.tr("gui.shop-list.empty-lore"))) }
                }
            )
        }

        return shops.map { shop -> icon(player, shop) }
    }

    override fun onContentClick(event: InventoryClickEvent, page: Int) {
        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        val index = contentIndex(event, page) ?: return
        val shop = ShopManager.all().getOrNull(index) ?: return

        ShopRender.navigate {
            if (event.click == ClickType.SHIFT_LEFT) ShopGUI.show(player, shop) else ShopEditorGUI.show(player, shop)
        }
    }

    private fun icon(player: Player, shop: ShopDefinition): ItemStack {
        val isGlobal = ShopManager.isGlobal(shop.id)

        val lore = buildList {
            add(player.tr("gui.shop-list.id", "id" to shop.id))
            add(player.tr("gui.shop-list.entries", "amount" to shop.entries.size))
            add(player.tr("gui.shop-list.npcs", "amount" to shop.npcIds.size))
            if (isGlobal) add(player.tr("gui.shop-list.global"))
            add(player.tr("gui.shop-list.click-edit"))
            add(player.tr("gui.shop-list.click-preview"))
        }

        // The one shop players reach without walking to it, so it is worth telling apart at a glance.
        return itemStack(if (isGlobal) Material.ENDER_CHEST else Material.CHEST) {
            name(shop.displayName)
            meta { lore(LoreUtil.wrapLore(lore.joinToString("<newline>"))) }
        }
    }

    companion object {
        const val ID = "shop-list"

        /** Opens the list for [player] through the registered instance. */
        fun show(player: Player): Boolean = GUIManager.open(player, ID)
    }
}
