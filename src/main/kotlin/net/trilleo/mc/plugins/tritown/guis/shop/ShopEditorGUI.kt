package net.trilleo.mc.plugins.tritown.guis.shop

import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.trilleo.mc.plugins.tritown.enums.FillMode
import net.trilleo.mc.plugins.tritown.enums.PagedLayout
import net.trilleo.mc.plugins.tritown.registration.GUIManager
import net.trilleo.mc.plugins.tritown.registration.PagedPluginGUI
import net.trilleo.mc.plugins.tritown.shops.ShopCost
import net.trilleo.mc.plugins.tritown.shops.ShopDefinition
import net.trilleo.mc.plugins.tritown.shops.ShopEntry
import net.trilleo.mc.plugins.tritown.shops.ShopManager
import net.trilleo.mc.plugins.tritown.utils.LoreUtil
import net.trilleo.mc.plugins.tritown.utils.itemStack
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * What one shop sells, in the order players see it, and where entries are
 * added, rearranged and removed.
 *
 * An entry is added by clicking a stack in your own inventory, or dragging one
 * over the menu. Nothing actually moves: the stack is copied, custom data and
 * all, and stays where it was. An editor that took the item would lose it to a
 * crash or a mistimed close, and an administrator setting up a shop is usually
 * holding the only copy of whatever they are adding.
 *
 * Reordering works the same way — nothing is picked up in the inventory sense.
 * A right click marks an entry as the one being moved, and the next click on a
 * slot says where it goes. Real item movement would need live slots in a menu
 * that must never hold the only copy of an item, and a mark survives turning
 * the page, which a stack on the cursor does not.
 *
 * The actions live in the navigation row rather than after the last entry, so
 * they stay under the same finger however many entries the shop grows.
 */
class ShopEditorGUI : PagedPluginGUI(
    id = ID,
    titleKey = "gui.shop-editor.title",
    rows = 6,
    fillMode = FillMode.NONE,
    layout = PagedLayout.FRAMED,
) {

    private val editing = ConcurrentHashMap<UUID, String>()

    /** The entry each administrator is part-way through moving, by its id. */
    private val moving = ConcurrentHashMap<UUID, String>()

    /** Opens the editor for [shop]. */
    fun open(player: Player, shop: ShopDefinition) {
        editing[player.uniqueId] = shop.id
        moving.remove(player.uniqueId)
        GUIManager.open(player, ID)
    }

    override fun getItems(player: Player): List<ItemStack> {
        val shop = shopOf(player) ?: return emptyList()
        val held = moving[player.uniqueId]
        return shop.entries.map { entry -> icon(player, entry, held) }
    }

    override fun navButtons(player: Player): Map<Int, ItemStack> {
        val shop = shopOf(player) ?: return emptyMap()
        val held = moving[player.uniqueId]?.let(shop::entry)
        val back = button(player, Material.ARROW, "gui.shop-editor.back", "gui.shop-editor.back-lore")

        if (held != null) {
            return mapOf(
                SLOT_HELD to heldButton(player, held),
                SLOT_MOVE_FIRST to button(
                    player, Material.SPECTRAL_ARROW,
                    "gui.shop-editor.move-first", "gui.shop-editor.move-first-lore",
                ),
                SLOT_MOVE_LAST to button(
                    player, Material.TIPPED_ARROW,
                    "gui.shop-editor.move-last", "gui.shop-editor.move-last-lore",
                ),
                SLOT_LIST to back,
            )
        }

        return buildMap {
            put(SLOT_ADD, button(player, Material.PAPER, "gui.shop-editor.add", "gui.shop-editor.add-lore"))
            put(SLOT_SETTINGS, settingsButton(player, shop))
            put(
                SLOT_STATS,
                button(player, Material.WRITABLE_BOOK, "gui.shop-editor.stats", "gui.shop-editor.stats-lore"),
            )
            put(SLOT_LIST, back)
            if (isSortable(shop)) {
                put(SLOT_SORT, button(player, Material.HOPPER, "gui.shop-editor.sort", "gui.shop-editor.sort-lore"))
            }
        }
    }

    override fun onNavClick(event: InventoryClickEvent, offset: Int) {
        val player = event.whoClicked as? Player ?: return
        val shop = shopOf(player) ?: return
        val held = moving[player.uniqueId]

        if (held != null) {
            when (offset) {
                SLOT_HELD -> release(player, event.inventory)
                SLOT_MOVE_FIRST -> place(player, shop, held, 0, event.inventory)
                SLOT_MOVE_LAST -> place(player, shop, held, shop.entries.size, event.inventory)
                SLOT_LIST -> ShopRender.navigate { ShopListGUI.show(player) }
            }
            return
        }

        when (offset) {
            SLOT_SETTINGS -> ShopRender.navigate { ShopSettingsGUI.show(player, shop) }
            SLOT_STATS -> ShopRender.navigate { ShopStatsGUI.show(player, shop) }
            SLOT_SORT -> if (isSortable(shop)) ShopRender.navigate { ShopSortGUI.show(player, shop) }
            SLOT_LIST -> ShopRender.navigate { ShopListGUI.show(player) }
        }
    }

    /**
     * The paged base class drops clicks outside its own inventory, but one in
     * the administrator's own inventory is how an entry is added, so it is taken
     * before the rest is handed on.
     */
    override fun onClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player
        if (player != null && event.clickedInventory === player.inventory) {
            event.isCancelled = true
            if (moving.containsKey(player.uniqueId)) return
            val shop = shopOf(player) ?: return
            event.currentItem?.let { add(player, shop, it) }
            return
        }

        super.onClick(event)
    }

    override fun onContentClick(event: InventoryClickEvent, page: Int) {
        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        val shop = shopOf(player) ?: return

        val index = contentIndex(page, event.rawSlot) ?: return
        val held = moving[player.uniqueId]
        if (held != null) {
            place(player, shop, held, index, event.inventory)
            return
        }

        val entry = shop.entries.getOrNull(index) ?: return
        click(event.click, player, shop, entry, event.inventory)
    }

    override fun onDrag(event: InventoryDragEvent) {
        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        if (moving.containsKey(player.uniqueId)) return
        val shop = shopOf(player) ?: return
        add(player, shop, event.oldCursor)
    }

    override fun onClose(event: InventoryCloseEvent) {
        super.onClose(event)
        val player = event.player as? Player ?: return
        editing.remove(player.uniqueId)
        moving.remove(player.uniqueId)
    }

    private fun click(
        click: ClickType,
        player: Player,
        shop: ShopDefinition,
        entry: ShopEntry,
        inventory: Inventory,
    ) {
        when (click) {
            ClickType.SHIFT_LEFT -> {
                shop.entries.remove(entry)
                ShopManager.save()
                player.sendPrefixed(player.tr("shop.editor.entry-removed"))
                refresh(player, inventory)
            }

            ClickType.RIGHT, ClickType.SHIFT_RIGHT -> {
                moving[player.uniqueId] = entry.id
                refresh(player, inventory)
                playClick(player)
            }

            else -> ShopRender.navigate { ShopEntryGUI.show(player, shop, entry) }
        }
    }

    /**
     * Moves the entry being held to [index], the position of the slot clicked.
     *
     * The entry always lands immediately before whatever was clicked, whether
     * it came from earlier or later in the shop — which is why the target is a
     * slot back when it is moving forwards: by then the list has closed up
     * behind it. A click past the last entry appends, and a click on the entry
     * itself puts it down where it already is.
     */
    private fun place(player: Player, shop: ShopDefinition, entryId: String, index: Int, inventory: Inventory) {
        moving.remove(player.uniqueId)

        val from = shop.entries.indexOfFirst { it.id == entryId }
        if (from < 0 || from == index) {
            refresh(player, inventory)
            playClick(player)
            return
        }

        val entry = shop.entries.removeAt(from)
        val target = (if (from < index) index - 1 else index).coerceIn(0, shop.entries.size)
        shop.entries.add(target, entry)
        ShopManager.save()

        refresh(player, inventory)
        playClick(player)
    }

    /** Puts the held entry back down without moving it. */
    private fun release(player: Player, inventory: Inventory) {
        moving.remove(player.uniqueId)
        refresh(player, inventory)
        playClick(player)
    }

    /**
     * Adds [stack] as a new entry, priced at nothing until the administrator sets a price.
     *
     * The stack size clicked becomes the bundle, so putting a stack of 16 bread
     * on the shelf sells sixteen loaves at a time without any further setting up.
     */
    private fun add(player: Player, shop: ShopDefinition, stack: ItemStack) {
        if (stack.type.isAir) return

        val entry = ShopEntry(
            item = stack.clone().apply { amount = 1 },
            bundle = stack.amount.coerceAtLeast(1),
            buy = ShopCost.FREE,
        )
        shop.entries += entry
        ShopManager.save()

        player.sendPrefixed(player.tr("shop.editor.entry-added", "item" to ShopRender.itemName(entry.item)))
        ShopRender.navigate { ShopEntryGUI.show(player, shop, entry) }
    }

    private fun icon(player: Player, entry: ShopEntry, heldId: String?): ItemStack {
        val lore = buildList {
            add(player.tr("gui.shop-editor.bundle", "amount" to entry.bundleSize))
            if (entry.isBuyable) addAll(buyLines(player, entry)) else add(player.tr("gui.shop-editor.not-buyable"))
            if (entry.isSellable) addAll(sellLines(player, entry)) else add(player.tr("gui.shop-editor.not-sellable"))

            when {
                heldId == entry.id -> {
                    add(player.tr("gui.shop-editor.being-moved"))
                    add(player.tr("gui.shop-editor.click-put-down"))
                }

                heldId != null -> add(player.tr("gui.shop-editor.click-place-before"))

                else -> {
                    add(player.tr("gui.shop-editor.click-entry"))
                    add(player.tr("gui.shop-editor.right-click-entry"))
                    add(player.tr("gui.shop-editor.shift-click-entry"))
                }
            }
        }

        val icon = LoreUtil.withLore(entry.displayStack(), lore)
        return if (heldId == entry.id) ShopRender.glowing(icon) else icon
    }

    private fun buyLines(player: Player, entry: ShopEntry): List<String> =
        listOf(player.tr("gui.shop-editor.buy")) + ShopRender.costLines(player, entry.buy)

    private fun sellLines(player: Player, entry: ShopEntry): List<String> =
        listOf(player.tr("gui.shop-editor.sell")) + ShopRender.costLines(player, entry.sell)

    /** The entry being moved, in the navigation row so it is on screen whatever page is open. */
    private fun heldButton(player: Player, entry: ShopEntry): ItemStack = ShopRender.glowing(
        LoreUtil.withLore(
            entry.displayStack(),
            listOf(player.tr("gui.shop-editor.holding"), player.tr("gui.shop-editor.click-put-down")),
        )
    )

    private fun settingsButton(player: Player, shop: ShopDefinition): ItemStack = itemStack(Material.COMPARATOR) {
        name(player.tr("gui.shop-editor.settings"))
        meta { lore(LoreUtil.wrapLore(player.tr("gui.shop-editor.settings-lore", "id" to shop.id))) }
    }

    private fun button(player: Player, material: Material, nameKey: String, loreKey: String): ItemStack =
        itemStack(material) {
            name(player.tr(nameKey))
            meta { lore(LoreUtil.wrapLore(player.tr(loreKey))) }
        }

    private fun playClick(player: Player) =
        player.playSound(Sound.sound(Key.key("minecraft:ui.button.click"), Sound.Source.UI, 1f, 1f))

    /** Sorting a shop with one entry in it would only be a way to lose the arrangement of a shop with many. */
    private fun isSortable(shop: ShopDefinition): Boolean = shop.entries.size > 1

    private fun shopOf(player: Player): ShopDefinition? = editing[player.uniqueId]?.let(ShopManager::get)

    companion object {
        const val ID = "shop-editor"

        // Offsets in the navigation row; 0, 4 and 8 belong to the page controls.
        private const val SLOT_ADD = 1
        private const val SLOT_SETTINGS = 2
        private const val SLOT_STATS = 3
        private const val SLOT_LIST = 5
        private const val SLOT_SORT = 6

        // While an entry is being moved the first three carry the move's own
        // controls instead, so the row an administrator is already looking at
        // answers the question in front of them.
        private const val SLOT_HELD = SLOT_ADD
        private const val SLOT_MOVE_FIRST = SLOT_SETTINGS
        private const val SLOT_MOVE_LAST = SLOT_STATS

        /** Opens the editor for [shop] through the registered instance. */
        fun show(player: Player, shop: ShopDefinition): Boolean {
            val gui = GUIManager.getGUI(ID) as? ShopEditorGUI ?: return false
            gui.open(player, shop)
            return true
        }
    }
}
