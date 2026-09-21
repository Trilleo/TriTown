package net.trilleo.mc.plugins.tritown.guis.storage

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.trilleo.mc.plugins.tritown.commands.storage.StorageCommand
import net.trilleo.mc.plugins.tritown.enums.FillMode
import net.trilleo.mc.plugins.tritown.guis.admin.PanelRender
import net.trilleo.mc.plugins.tritown.guis.menu.MenuRender
import net.trilleo.mc.plugins.tritown.registration.GUIFrame
import net.trilleo.mc.plugins.tritown.registration.GUIManager
import net.trilleo.mc.plugins.tritown.registration.PluginGUI
import net.trilleo.mc.plugins.tritown.storage.StorageManager
import net.trilleo.mc.plugins.tritown.utils.ChatPrompt
import net.trilleo.mc.plugins.tritown.utils.ComponentUtil
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Naming a storage page and choosing what it is shown as.
 *
 * Only the page's name and icon change here, never what it holds, so this does
 * not take the storage's lock: its owner can rename a page while an
 * administrator is looking through it.
 */
class StoragePageSettingsGUI : PluginGUI(
    id = ID,
    titleKey = "gui.storage-settings.title",
    rows = 6,
    fillMode = FillMode.NONE,
) {

    private data class Target(val owner: UUID, val page: Int)

    private val targets = ConcurrentHashMap<UUID, Target>()

    override fun setup(player: Player, inventory: Inventory) {
        val target = targets[player.uniqueId] ?: return
        val storage = StorageManager.get(target.owner) ?: return
        val page = storage.pageOrNull(target.page)

        GUIFrame.draw(inventory, BUTTONS + PREVIEW_SLOT + BACK_SLOT)
        inventory.setItem(PREVIEW_SLOT, StorageRender.pageCard(player, page, target.page, current = false).also { item ->
            item.editMeta { it.lore(null) }
        })
        inventory.setItem(
            SLOT_RENAME,
            PanelRender.card(Material.NAME_TAG, player.tr("gui.storage-settings.rename"), listOf(player.tr("gui.storage-settings.rename-lore")))
        )
        inventory.setItem(
            SLOT_ICON,
            PanelRender.card(Material.ITEM_FRAME, player.tr("gui.storage-settings.icon"), listOf(player.tr("gui.storage-settings.icon-lore")))
        )
        inventory.setItem(
            SLOT_RESET,
            PanelRender.card(Material.BARRIER, player.tr("gui.storage-settings.reset"), listOf(player.tr("gui.storage-settings.reset-lore")))
        )
        inventory.setItem(BACK_SLOT, PanelRender.card(Material.ARROW, player.tr("gui.storage-settings.back"), listOf(player.tr("gui.storage-settings.back-lore"))))
    }

    override fun onClick(event: InventoryClickEvent) {
        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        val target = targets[player.uniqueId] ?: return
        if (!mayEdit(player, target)) return
        val storage = StorageManager.get(target.owner) ?: return

        if (event.clickedInventory === player.inventory) {
            val clicked = event.currentItem ?: return
            if (clicked.type.isAir) return
            storage.page(target.page).icon = clicked.type.name
            StorageManager.save(storage)
            MenuRender.click(player)
            GUIManager.refresh(player)
            return
        }
        if (event.clickedInventory !== event.view.topInventory) return

        when (event.rawSlot) {
            SLOT_RENAME -> MenuRender.later(player) { rename(player, target) }
            SLOT_ICON -> player.sendPrefixed(player.tr("gui.storage-settings.icon-hint"))
            SLOT_RESET -> {
                storage.pageOrNull(target.page)?.let { page ->
                    page.name = null
                    page.icon = null
                    StorageManager.save(storage)
                }
                MenuRender.click(player)
                GUIManager.refresh(player)
            }

            BACK_SLOT -> MenuRender.later(player) { backToPage(player, target) }
        }
    }

    override fun onClose(event: InventoryCloseEvent) {
        val target = targets.remove(event.player.uniqueId) ?: return
        StorageManager.unloadIfIdle(target.owner)
    }

    /** Asks for the page's new name in chat, then comes back here. */
    private fun rename(player: Player, target: Target) {
        player.closeInventory()

        ChatPrompt.ask(player, player.tr("gui.storage-settings.rename-prompt", "amount" to MAX_NAME)) { input ->
            val plain = PlainTextComponentSerializer.plainText().serialize(ComponentUtil.parse(input)).trim()
            val storage = StorageManager.get(target.owner)
            when {
                storage == null || !mayEdit(player, target) -> Unit
                plain.isEmpty() || plain.length > MAX_NAME ->
                    StorageRender.refuse(player, "storage.error.bad-name", "amount" to MAX_NAME)

                else -> {
                    storage.page(target.page).name = plain
                    StorageManager.save(storage)
                    player.sendPrefixed(player.tr("storage.renamed", "name" to ComponentUtil.escape(plain)))
                }
            }
            show(player, target.owner, target.page)
        }
    }

    private fun backToPage(player: Player, target: Target) {
        targets.remove(player.uniqueId)
        StorageGUI.open(player, target.owner, target.page)
    }

    private fun mayEdit(player: Player, target: Target): Boolean =
        target.owner == player.uniqueId || player.hasPermission(StorageCommand.ADMIN_PERMISSION)

    companion object {
        const val ID = "storage-settings"

        /** The longest name a page may have, in characters. */
        private const val MAX_NAME = 32

        private const val PREVIEW_SLOT = 13
        private const val SLOT_RENAME = 29
        private const val SLOT_ICON = 31
        private const val SLOT_RESET = 33
        private const val BACK_SLOT = 49

        private val BUTTONS = listOf(SLOT_RENAME, SLOT_ICON, SLOT_RESET)

        /** Opens the settings of page [page] of [owner]'s storage for [viewer]. */
        fun show(viewer: Player, owner: UUID, page: Int): Boolean {
            val gui = GUIManager.getGUI(ID) as? StoragePageSettingsGUI ?: return false
            gui.targets[viewer.uniqueId] = Target(owner, page)
            return GUIManager.open(viewer, ID)
        }
    }
}
