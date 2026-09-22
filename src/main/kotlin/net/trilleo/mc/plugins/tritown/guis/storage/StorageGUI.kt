package net.trilleo.mc.plugins.tritown.guis.storage

import net.kyori.adventure.text.Component
import net.trilleo.mc.plugins.tritown.Main
import net.trilleo.mc.plugins.tritown.commands.storage.StorageCommand
import net.trilleo.mc.plugins.tritown.enums.FillMode
import net.trilleo.mc.plugins.tritown.guis.ConfirmGUI
import net.trilleo.mc.plugins.tritown.guis.admin.AdminStorageGUI
import net.trilleo.mc.plugins.tritown.guis.admin.PanelRender
import net.trilleo.mc.plugins.tritown.guis.menu.MainMenuGUI
import net.trilleo.mc.plugins.tritown.guis.menu.MenuRender
import net.trilleo.mc.plugins.tritown.registration.GUIFrame
import net.trilleo.mc.plugins.tritown.registration.GUIManager
import net.trilleo.mc.plugins.tritown.registration.PluginGUI
import net.trilleo.mc.plugins.tritown.storage.*
import net.trilleo.mc.plugins.tritown.utils.ComponentUtil
import net.trilleo.mc.plugins.tritown.utils.itemStack
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * One page of a player's storage.
 *
 * The top five rows are real slots, and this is the one menu in TriTown that
 * lets the game move items itself: a player sorts their storage the way they
 * would sort a chest. Everything else is guarded. The bottom row is buttons and
 * nothing may be put into it or taken out of it, a shift-click into the page is
 * done by hand so it can never land on a button, and anything the storage will
 * not hold is turned away before it reaches a slot.
 *
 * What is on screen is copied back into the storage after every change, when
 * the page is switched and when the menu closes. Whoever changes a storage has
 * to be the one person holding it — see [StorageManager.claim] — so two windows
 * can never write different versions of the same page.
 */
class StorageGUI : PluginGUI(
    id = ID,
    titleKey = "gui.storage.title",
    rows = ROWS,
    fillMode = FillMode.NONE,
) {

    /** Whose storage [viewer] is looking at, which page, and whether they may change it. */
    private class View(val owner: UUID, var page: Int, val access: StorageManager.Access) {
        var inventory: Inventory? = null
        val canEdit: Boolean get() = access == StorageManager.Access.EDIT
    }

    private val views = ConcurrentHashMap<UUID, View>()

    override fun title(player: Player): Component {
        val view = views[player.uniqueId] ?: return super.title(player)
        val storage = StorageManager.cached(view.owner)
        val total = storage?.let(StorageManager::pageCount) ?: 1
        val name = StorageRender.pageName(player, storage?.pageOrNull(view.page), view.page)

        val title = if (view.owner == player.uniqueId) {
            player.tr("gui.storage.title", "name" to name, "page" to view.page + 1, "total" to total)
        } else {
            player.tr(
                "gui.storage.title-inspect",
                "owner" to StorageRender.ownerName(view.owner),
                "page" to view.page + 1,
                "total" to total,
            )
        }
        return ComponentUtil.parse(title)
    }

    override fun setup(player: Player, inventory: Inventory) {
        val view = views[player.uniqueId] ?: return
        val storage = StorageManager.cached(view.owner) ?: return
        view.inventory = inventory

        val page = storage.page(view.page)
        for (slot in 0 until StoragePage.SIZE) inventory.setItem(slot, page.slots[slot]?.clone())

        GUIFrame.draw(inventory, (0 until StoragePage.SIZE).toList())
        buttons(player, view, storage).forEach { (slot, item) -> inventory.setItem(slot, item) }
    }

    // ── Clicks ──────────────────────────────────────────────────────────

    override fun onClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val view = views[player.uniqueId] ?: run {
            event.isCancelled = true
            return
        }

        if (event.action == InventoryAction.COLLECT_TO_CURSOR) {
            // Gathers matching items from every slot on screen, buttons included.
            event.isCancelled = true
            return
        }

        val slot = event.rawSlot
        when {
            slot in BUTTON_SLOTS -> {
                event.isCancelled = true
                button(player, view, slot, event.click)
            }

            slot in 0 until StoragePage.SIZE -> content(player, view, event)

            event.action == InventoryAction.MOVE_TO_OTHER_INVENTORY -> {
                event.isCancelled = true
                if (view.canEdit) shiftIn(player, view, event)
            }
        }
    }

    override fun onDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        val view = views[player.uniqueId]
        val intoTop = event.rawSlots.any { it < ROWS * ROW_SIZE }
        if (!intoTop) return

        when {
            view == null || !view.canEdit || event.rawSlots.any { it in BUTTON_SLOTS } -> event.isCancelled = true
            !StorageItems.accepts(event.oldCursor) -> {
                event.isCancelled = true
                StorageRender.refuse(player, "storage.error.not-allowed")
            }

            else -> snapshotLater(player, view)
        }
    }

    override fun onClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        finish(player)
    }

    /**
     * A click on one of the page's own slots, which the game carries out unless
     * it would put something the storage refuses there.
     */
    private fun content(player: Player, view: View, event: InventoryClickEvent) {
        if (!view.canEdit || event.action !in CONTENT_ACTIONS) {
            event.isCancelled = true
            return
        }

        val incoming = when (event.action) {
            InventoryAction.PLACE_ALL, InventoryAction.PLACE_SOME, InventoryAction.PLACE_ONE,
            InventoryAction.SWAP_WITH_CURSOR -> event.cursor

            InventoryAction.HOTBAR_SWAP -> if (event.click == ClickType.SWAP_OFFHAND) {
                player.inventory.itemInOffHand
            } else {
                player.inventory.getItem(event.hotbarButton)
            }

            else -> null
        }

        if (!StorageItems.accepts(incoming)) {
            event.isCancelled = true
            StorageRender.refuse(player, "storage.error.not-allowed")
            return
        }

        snapshotLater(player, view)
    }

    /**
     * A shift-click in the player's own inventory, moved onto the page by hand.
     *
     * Left to the game, it would merge into any stack on screen that matched,
     * and a button is a stack on screen.
     */
    private fun shiftIn(player: Player, view: View, event: InventoryClickEvent) {
        val clicked = event.currentItem ?: return
        if (clicked.type.isAir) return
        if (!StorageItems.accepts(clicked)) {
            StorageRender.refuse(player, "storage.error.not-allowed")
            return
        }

        val storage = StorageManager.cached(view.owner) ?: return
        snapshot(view)

        val leftover = SlotFill.insert(listOf(storage.page(view.page).slots), clicked.clone(), StorageItems.rules)
        if (leftover != null && leftover.amount == clicked.amount) return

        event.clickedInventory?.setItem(event.slot, leftover)
        StorageManager.markDirty(storage)
        redraw(player, view)
    }

    // ── Buttons ─────────────────────────────────────────────────────────

    private fun button(player: Player, view: View, slot: Int, click: ClickType) {
        val storage = StorageManager.cached(view.owner) ?: return
        val pages = StorageManager.pageCount(storage)

        when (slot) {
            SLOT_PREVIOUS -> if (view.page > 0) switchPage(player, view, view.page - 1)
            SLOT_OVERVIEW -> leave(player) { StoragePagesGUI.show(player, view.owner, view.page, readOnly = !view.canEdit) }
            SLOT_DEPOSIT -> if (view.canEdit) quickDeposit(player, view, storage)
            SLOT_SORT -> if (view.canEdit) sort(player, view, storage, everyPage = click.isShiftClick)
            SLOT_INFO -> leave(player) { back(player, view) }
            SLOT_WITHDRAW -> if (view.canEdit) withdrawPage(player, view, storage)
            SLOT_UNPACK -> if (view.canEdit) unpackLater(player, view)
            SLOT_SETTINGS -> if (canRename(player, view)) {
                leave(player) { StoragePageSettingsGUI.show(player, view.owner, view.page) }
            }

            SLOT_NEXT -> when {
                view.page + 1 < pages -> switchPage(player, view, view.page + 1)
                view.owner == player.uniqueId && view.canEdit && StorageManager.nextPageCost(storage) != null ->
                    leave(player) { offerPage(player, view.page) }
            }
        }
    }

    /** Puts every stack from the main inventory whose material is already stored into the storage. */
    private fun quickDeposit(player: Player, view: View, storage: PlayerStorage) {
        snapshot(view)
        val materials = StorageManager.materials(storage)
        var moved = 0

        for (index in MAIN_INVENTORY) {
            val stack = player.inventory.getItem(index) ?: continue
            if (stack.type !in materials || !StorageItems.accepts(stack)) continue

            val leftover = StorageManager.deposit(storage, view.page, stack.clone())
            moved += stack.amount - (leftover?.amount ?: 0)
            player.inventory.setItem(index, leftover)
        }

        if (moved == 0) {
            StorageRender.refuse(player, "storage.error.nothing-to-deposit")
            return
        }
        MenuRender.click(player)
        player.sendPrefixed(player.tr("storage.deposited", "amount" to moved))
        redraw(player, view)
    }

    private fun sort(player: Player, view: View, storage: PlayerStorage, everyPage: Boolean) {
        snapshot(view)
        val targets = if (everyPage) (0 until StorageManager.pageCount(storage)) else listOf(view.page)
        targets.forEach { SlotFill.sort(storage.page(it).slots, StorageItems.rules, StorageItems.order) }

        StorageManager.markDirty(storage)
        MenuRender.click(player)
        redraw(player, view)
    }

    /** Moves as much of the page as fits into the player's inventory, leaving the rest where it was. */
    private fun withdrawPage(player: Player, view: View, storage: PlayerStorage) {
        snapshot(view)
        val page = storage.page(view.page)
        var moved = 0

        for (index in page.slots.indices) {
            val stack = page.slots[index] ?: continue
            val leftover = player.inventory.addItem(stack.clone()).values.firstOrNull()
            moved += stack.amount - (leftover?.amount ?: 0)
            page.slots[index] = leftover
        }

        if (moved == 0) {
            StorageRender.refuse(player, "storage.error.inventory-full")
            return
        }
        StorageManager.markDirty(storage)
        MenuRender.click(player)
        redraw(player, view)
    }

    /**
     * Empties the shulker box or bundle on the cursor into the storage.
     *
     * Run on the next tick, once the refused click has settled, so the cursor
     * read is the one the player really holds. Nothing moves unless all of it
     * fits: a box half unpacked would leave the player to work out what went where.
     */
    private fun unpackLater(player: Player, view: View) {
        Bukkit.getScheduler().runTask(Main.instance, Runnable {
            if (views[player.uniqueId] !== view || player.openInventory.topInventory !== view.inventory) return@Runnable
            val storage = StorageManager.cached(view.owner) ?: return@Runnable

            val cursor = player.itemOnCursor
            if (!StorageItems.isContainer(cursor)) {
                StorageRender.refuse(player, "storage.error.unpack-hint")
                return@Runnable
            }
            val contents = StorageItems.unpacked(cursor)
            if (contents.isEmpty()) {
                StorageRender.refuse(player, "storage.error.unpack-empty")
                return@Runnable
            }

            snapshot(view)
            if (!StorageManager.fits(storage, contents)) {
                StorageRender.refuse(player, "storage.error.no-room")
                return@Runnable
            }

            contents.forEach { StorageManager.deposit(storage, view.page, it.clone()) }
            player.setItemOnCursor(StorageItems.emptied(cursor))
            MenuRender.click(player)
            player.sendPrefixed(player.tr("storage.unpacked", "amount" to contents.sumOf { it.amount }))
            redraw(player, view)
        })
    }

    private fun back(player: Player, view: View) {
        if (view.owner == player.uniqueId) MainMenuGUI.show(player) else AdminStorageGUI.show(player)
    }

    private fun canRename(player: Player, view: View): Boolean =
        view.canEdit && (view.owner == player.uniqueId || player.hasPermission(StorageCommand.ADMIN_PERMISSION))

    // ── Drawing ─────────────────────────────────────────────────────────

    private fun buttons(player: Player, view: View, storage: PlayerStorage): Map<Int, ItemStack> {
        val pages = StorageManager.pageCount(storage)
        val buttons = mutableMapOf<Int, ItemStack>()

        if (view.page > 0) {
            buttons[SLOT_PREVIOUS] = card(player, Material.ARROW, "gui.storage.previous", "gui.storage.previous-lore")
        }
        buttons[SLOT_OVERVIEW] = card(player, Material.BOOKSHELF, "gui.storage.overview", "gui.storage.overview-lore")
        buttons[SLOT_INFO] = info(player, view, storage)

        if (view.canEdit) {
            buttons[SLOT_DEPOSIT] = card(player, Material.HOPPER, "gui.storage.deposit", "gui.storage.deposit-lore")
            buttons[SLOT_SORT] = card(player, Material.COMPARATOR, "gui.storage.sort", "gui.storage.sort-lore")
            buttons[SLOT_WITHDRAW] = card(player, Material.DROPPER, "gui.storage.withdraw", "gui.storage.withdraw-lore")
            buttons[SLOT_UNPACK] = card(player, Material.SHULKER_BOX, "gui.storage.unpack", "gui.storage.unpack-lore")
        }
        if (canRename(player, view)) {
            buttons[SLOT_SETTINGS] = card(player, Material.NAME_TAG, "gui.storage.settings", "gui.storage.settings-lore")
        }

        val buyable = view.owner == player.uniqueId && view.canEdit
        when {
            view.page + 1 < pages ->
                buttons[SLOT_NEXT] = card(player, Material.ARROW, "gui.storage.next", "gui.storage.next-lore")

            buyable -> StorageRender.buyCard(player, storage)?.let { buttons[SLOT_NEXT] = it }
        }
        return buttons
    }

    private fun info(player: Player, view: View, storage: PlayerStorage): ItemStack {
        val page = storage.pageOrNull(view.page)
        val lines = mutableListOf(
            player.tr("gui.storage.info-page", "page" to view.page + 1, "total" to StorageManager.pageCount(storage)),
            player.tr("gui.storage.overview-used", "amount" to (page?.used ?: 0), "total" to StoragePage.SIZE),
        )
        if (view.owner != player.uniqueId) {
            lines += player.tr("gui.storage.info-owner", "owner" to StorageRender.ownerName(view.owner))
        }
        if (!view.canEdit) lines += player.tr("gui.storage.info-read-only")
        lines += ""
        lines += player.tr(if (view.owner == player.uniqueId) "gui.storage.info-back" else "gui.storage.info-back-admin")

        return PanelRender.card(
            StorageRender.icon(page),
            player.tr("gui.storage.info", "name" to StorageRender.pageName(player, page, view.page)),
            lines,
        )
    }

    private fun card(player: Player, material: Material, nameKey: String, loreKey: String): ItemStack =
        PanelRender.card(material, player.tr(nameKey), listOf(player.tr(loreKey)))

    // ── Keeping the storage and the screen the same ─────────────────────

    /** Copies what is on screen into the storage. */
    private fun snapshot(view: View) {
        if (!view.canEdit) return
        val inventory = view.inventory ?: return
        val storage = StorageManager.cached(view.owner) ?: return
        val page = storage.page(view.page)

        var changed = false
        for (slot in 0 until StoragePage.SIZE) {
            val shown = inventory.getItem(slot)?.takeIf { !it.type.isAir }?.clone()
            if (shown != page.slots[slot]) {
                page.slots[slot] = shown
                changed = true
            }
        }

        if (changed) {
            StorageManager.markDirty(storage)
            watchers(view).forEach { GUIManager.refresh(it) }
        }
    }

    /**
     * Copies the page back on the next tick, once the game has carried the
     * click out; the screen only shows its result after this handler returns.
     */
    private fun snapshotLater(player: Player, view: View) {
        Bukkit.getScheduler().runTask(Main.instance, Runnable {
            if (views[player.uniqueId] === view && player.openInventory.topInventory === view.inventory) snapshot(view)
        })
    }

    /** Rewrites [player]'s screen from the storage, after something changed it behind the screen's back. */
    private fun redraw(player: Player, view: View) {
        GUIManager.refresh(player)
        watchers(view).forEach { GUIManager.refresh(it) }
    }

    /** Everyone else looking at the same page read-only, whose screens have to follow it. */
    private fun watchers(view: View): List<Player> = views.entries
        .filter { (_, other) -> other !== view && other.owner == view.owner && other.page == view.page && !other.canEdit }
        .mapNotNull { (viewer, _) -> Bukkit.getPlayer(viewer) }
        .filter { GUIManager.openGUI(it)?.id == ID }

    private fun switchPage(player: Player, view: View, target: Int) {
        snapshot(view)
        MenuRender.later(player) {
            if (views[player.uniqueId] !== view) return@later
            // Once more, for anything moved between the click and now.
            snapshot(view)
            view.page = target
            GUIManager.open(player, ID)
        }
    }

    /**
     * Leaves the storage for another menu.
     *
     * Opening another menu from here does not deliver a close to this one, so
     * the storage is let go of by hand, on the tick the next menu opens.
     */
    private fun leave(player: Player, then: () -> Unit) {
        MenuRender.later(player) {
            finish(player)
            then()
        }
    }

    /** Copies the page back, lets go of the storage and writes it. Safe to call more than once. */
    fun finish(player: Player) {
        val view = views.remove(player.uniqueId) ?: return
        snapshot(view)
        StorageManager.cached(view.owner)?.let { if (it.dirty) StorageManager.save(it) }
        StorageManager.release(view.owner, player.uniqueId)
    }

    /** Every viewer with a storage open. */
    fun viewerIds(): Set<UUID> = views.keys.toSet()

    companion object {

        const val ID = "storage"

        private const val ROWS = 6
        private const val ROW_SIZE = 9

        private const val SLOT_PREVIOUS = 45
        private const val SLOT_OVERVIEW = 46
        private const val SLOT_DEPOSIT = 47
        private const val SLOT_SORT = 48
        private const val SLOT_INFO = 49
        private const val SLOT_WITHDRAW = 50
        private const val SLOT_UNPACK = 51
        private const val SLOT_SETTINGS = 52
        private const val SLOT_NEXT = 53

        private val BUTTON_SLOTS = StoragePage.SIZE until ROWS * ROW_SIZE

        /** The player's main inventory without the hotbar, which quick deposit never empties. */
        private val MAIN_INVENTORY = 9..35

        /** What the game may do on a page slot; anything else is refused. */
        private val CONTENT_ACTIONS = setOf(
            InventoryAction.NOTHING,
            InventoryAction.PICKUP_ALL,
            InventoryAction.PICKUP_SOME,
            InventoryAction.PICKUP_HALF,
            InventoryAction.PICKUP_ONE,
            InventoryAction.PLACE_ALL,
            InventoryAction.PLACE_SOME,
            InventoryAction.PLACE_ONE,
            InventoryAction.SWAP_WITH_CURSOR,
            InventoryAction.HOTBAR_SWAP,
            InventoryAction.DROP_ALL_SLOT,
            InventoryAction.DROP_ONE_SLOT,
            InventoryAction.MOVE_TO_OTHER_INVENTORY,
            InventoryAction.CLONE_STACK,
            InventoryAction.PICKUP_ALL_INTO_BUNDLE,
            InventoryAction.PICKUP_SOME_INTO_BUNDLE,
        )

        private fun gui(): StorageGUI? = GUIManager.getGUI(ID) as? StorageGUI

        /**
         * Opens page [page] of [owner]'s storage for [viewer].
         *
         * @param readOnly open it only to look, even when [viewer] could change it
         * @return `false` when it could not be opened; [viewer] has been told why
         */
        fun open(viewer: Player, owner: UUID, page: Int = 0, readOnly: Boolean = false): Boolean {
            val gui = gui() ?: return false
            if (!StorageManager.isAvailable) {
                viewer.sendPrefixed(viewer.tr("common.error", "message" to viewer.tr("storage.error.unavailable")))
                return false
            }

            gui.finish(viewer)
            val storage = StorageManager.get(owner) ?: run {
                StorageRender.refuse(viewer, "storage.error.unreadable")
                return false
            }

            val inspect = viewer.uniqueId != owner && viewer.hasPermission(StorageCommand.ADMIN_PERMISSION)
            val access = StorageManager.claim(owner, viewer.uniqueId, inspect, readOnly) ?: run {
                StorageRender.refuse(viewer, "storage.error.busy")
                StorageManager.unloadIfIdle(owner)
                return false
            }

            val index = page.coerceIn(0, StorageManager.pageCount(storage) - 1)
            gui.views[viewer.uniqueId] = View(owner, index, access)
            if (!GUIManager.open(viewer, ID)) {
                gui.finish(viewer)
                return false
            }
            return true
        }

        /** Closes [player]'s storage, if they have one open, so it is copied back before anything else happens. */
        fun close(player: Player) {
            val gui = gui() ?: return
            gui.finish(player)
            if (GUIManager.openGUI(player)?.id == ID) player.closeInventory()
        }

        /** Closes every open storage, for a shutdown. */
        fun closeAll() {
            val gui = gui() ?: return
            gui.viewerIds().mapNotNull(Bukkit::getPlayer).forEach(::close)
        }

        /** Asks [player] whether to buy one more page, going back to page [returnTo] if they do not. */
        fun offerPage(player: Player, returnTo: Int) {
            val storage = StorageManager.get(player.uniqueId) ?: return
            val card = StorageRender.buyCard(player, storage) ?: return

            val confirm = itemStack(Material.LIME_CONCRETE) {
                name(player.tr("gui.storage.buy-confirm"))
            }
            ConfirmGUI.show(
                player,
                title = player.tr("gui.storage.buy-title"),
                subject = card,
                choices = listOf(ConfirmGUI.Choice(confirm) { buy(it, returnTo) }),
                onCancel = { open(it, it.uniqueId, returnTo) },
            )
        }

        /** Buys a page for [player] and opens it, or says why not and goes back to [returnTo]. */
        private fun buy(player: Player, returnTo: Int) {
            when (StorageManager.buyPage(player)) {
                StorageManager.Purchase.BOUGHT -> {
                    val storage = StorageManager.get(player.uniqueId) ?: return
                    val page = StorageManager.owned(storage)
                    player.sendPrefixed(player.tr("storage.bought", "page" to page))
                    open(player, player.uniqueId, page - 1)
                }

                StorageManager.Purchase.CANNOT_AFFORD -> {
                    StorageRender.refuse(player, "storage.error.cannot-afford")
                    open(player, player.uniqueId, returnTo)
                }

                StorageManager.Purchase.AT_MAX -> {
                    StorageRender.refuse(player, "storage.error.max-pages")
                    open(player, player.uniqueId, returnTo)
                }

                StorageManager.Purchase.UNAVAILABLE -> StorageRender.refuse(player, "storage.error.unavailable")
            }
        }
    }
}
