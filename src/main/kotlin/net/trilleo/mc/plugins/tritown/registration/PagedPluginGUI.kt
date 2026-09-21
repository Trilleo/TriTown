package net.trilleo.mc.plugins.tritown.registration

import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.trilleo.mc.plugins.tritown.enums.FillMode
import net.trilleo.mc.plugins.tritown.enums.PagedGUIMode
import net.trilleo.mc.plugins.tritown.enums.PagedLayout
import net.trilleo.mc.plugins.tritown.utils.itemStack
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.util.*

/**
 * A [PluginGUI] subclass that automatically splits content across
 * multiple pages with **Previous** and **Next** navigation buttons.
 *
 * Extend this class and place the subclass anywhere inside the
 * `net.trilleo.mc.plugins.tritown.guis` package (or any subpackage) to
 * have it automatically discovered and registered at startup.
 *
 * The bottom row of the inventory is reserved for navigation controls, and
 * [layout] decides what the rest of it does. [PagedLayout.FULL] fills every slot
 * above that row with content — a 6-row GUI gives 45 content slots per page.
 * [PagedLayout.FRAMED] insets the content by one slot on every side, giving 28
 * instead and a border around them. [PagedLayout.CENTERED] frames it the same
 * way and centres a page that is not full.
 *
 * The class must have either:
 * - A no-arg constructor, **or**
 * - A constructor that accepts a single `JavaPlugin` parameter (the plugin
 *   instance will be injected automatically).
 *
 * Two item-supply modes are available via [mode]:
 * - [PagedGUIMode.LIST] *(default)* – override [getItems] to provide a flat
 *   list of items that are distributed automatically across pages.
 * - [PagedGUIMode.SET] – override [getSetItems] to provide a map of
 *   `page → (slot → item)`, giving full control over each item's position.
 *
 * Example (LIST mode):
 * ```kotlin
 * package net.trilleo.mc.plugins.tritown.guis
 *
 * import org.bukkit.Material
 * import org.bukkit.inventory.ItemStack
 *
 * class RewardsGUI : PagedPluginGUI(
 *     id = "rewards",
 *     titleKey = "gui.rewards.title",
 *     rows = 6
 * ) {
 *     override fun getItems(player: Player): List<ItemStack> {
 *         return List(100) { index ->
 *             val item = ItemStack(Material.DIAMOND)
 *             val meta = item.itemMeta
 *             meta.displayName(Component.text("Reward #${index + 1}"))
 *             item.itemMeta = meta
 *             item
 *         }
 *     }
 * }
 * ```
 *
 * Example (SET mode):
 * ```kotlin
 * package net.trilleo.mc.plugins.tritown.guis
 *
 * import net.trilleo.mc.plugins.tritown.enums.PagedGUIMode
 * import org.bukkit.Material
 * import org.bukkit.inventory.ItemStack
 *
 * class CustomGUI : PagedPluginGUI(
 *     id = "custom",
 *     titleKey = "gui.custom.title",
 *     rows = 4,
 *     mode = PagedGUIMode.SET
 * ) {
 *     override fun getSetItems(player: Player): Map<Int, Map<Int, ItemStack>> {
 *         return mapOf(
 *             0 to mapOf(4 to ItemStack(Material.DIAMOND)),
 *             1 to mapOf(4 to ItemStack(Material.EMERALD))
 *         )
 *     }
 * }
 * ```
 */
abstract class PagedPluginGUI(
    id: String,
    titleKey: String,
    rows: Int = 6,
    fillMode: FillMode = FillMode.NONE,
    val mode: PagedGUIMode = PagedGUIMode.LIST,
    val layout: PagedLayout = PagedLayout.FULL,
) : PluginGUI(id, titleKey, rows, fillMode) {

    /** Tracks the current page for each player viewing this GUI. */
    private val playerPages = mutableMapOf<UUID, Int>()

    /**
     * Which position on the page each drawn slot shows, per viewer.
     *
     * Remembered as drawn rather than worked out again from the layout, because
     * a centred page puts its items in different slots depending on how many
     * of them there are.
     */
    private val shownPositions = mutableMapOf<UUID, Map<Int, Int>>()

    /**
     * Returns all items that should be distributed across pages for the
     * given player. The list may be of any size; items are automatically
     * split into pages of [pageSize] each.
     *
     * Used when [mode] is [PagedGUIMode.LIST]. Override this method to supply
     * the items to paginate.
     *
     * @param player the player the GUI is being opened for
     * @return the full list of items to paginate
     */
    open fun getItems(player: Player): List<ItemStack> = emptyList()

    /**
     * Returns a map of items to place at specific pages and slots.
     *
     * The outer map key is the **zero-based page index**; the inner map key is
     * the **zero-based position** within the content area of that page (0 to
     * [pageSize]`- 1`), not an inventory slot — a framed layout maps those
     * positions onto the slots inside its border. Pages that are missing from
     * the map are rendered empty.
     *
     * Used when [mode] is [PagedGUIMode.SET]. Override this method to supply
     * manually positioned items.
     *
     * @param player the player the GUI is being opened for
     * @return a map of `page → (position → item)` describing the full contents
     */
    open fun getSetItems(player: Player): Map<Int, Map<Int, ItemStack>> = emptyMap()

    /**
     * Called when a player clicks a **content slot** (not a navigation
     * button or a button in the navigation row). Override to add custom click
     * handling.
     *
     * Use [contentIndex] to turn the click into a position in the list
     * returned by [getItems]; the raw slot is an inventory slot and does not
     * match that list once a border is in the way.
     *
     * Clicks are cancelled by default to prevent item theft.
     *
     * @param event the inventory click event
     * @param page  the page the player is currently viewing (zero-based)
     */
    open fun onContentClick(event: InventoryClickEvent, page: Int) {}

    /**
     * Buttons to place in the navigation row, keyed by their offset in that row
     * (0 to 8).
     *
     * Offsets 0, 4 and 8 belong to Previous, the page indicator and Next, and
     * anything placed there is ignored. Because these sit in a row that never
     * moves, a button here stays where it is however the content grows — which
     * is the point of putting an action here rather than among the items.
     *
     * @param player the player the GUI is being drawn for
     */
    open fun navButtons(player: Player): Map<Int, ItemStack> = emptyMap()

    /**
     * Called when a player clicks one of this GUI's own [navButtons].
     *
     * @param event  the inventory click event
     * @param offset the offset in the navigation row that was clicked (0 to 8)
     */
    open fun onNavClick(event: InventoryClickEvent, offset: Int) {}

    /** The inventory slots that hold content, in reading order. */
    private val contentSlots: List<Int> by lazy {
        when (layout) {
            PagedLayout.FULL -> (0 until (rows - 1) * ROW_SIZE).toList()
            PagedLayout.FRAMED, PagedLayout.CENTERED -> GUIFrame.contentSlots(rows)
        }
    }

    private val contentSlotSet: Set<Int> by lazy { contentSlots.toSet() }

    /** How many items fit on one page. */
    protected val pageSize: Int
        get() = contentSlots.size.coerceAtLeast(1)

    /** The first slot index of the navigation row (the last row). */
    private val navRowStart: Int
        get() = (rows - 1) * ROW_SIZE

    /**
     * The position in [getItems]'s list that the slot [event] clicked shows on
     * [page], or `null` when that slot is not part of the content area.
     *
     * An empty slot inside the content area still has a position, just one past
     * the end of the list — except on a [PagedLayout.CENTERED] page, where only
     * the slots holding an item are content at all.
     *
     * In [PagedGUIMode.SET] this is `page * pageSize` plus the position the
     * item was given in [getSetItems].
     */
    protected fun contentIndex(event: InventoryClickEvent, page: Int): Int? =
        shownPositions[event.whoClicked.uniqueId]?.get(event.rawSlot)?.let { position -> page * pageSize + position }

    /**
     * Redraws the page [player] is looking at, into the inventory they have open.
     *
     * A menu whose contents change under a click — an entry removed, an item
     * moved — would otherwise have to reopen itself to show the change, which
     * drops the viewer back onto the first page of whatever they were part-way
     * through. The page is clamped, so the last item leaving a page steps back
     * rather than showing an empty one.
     */
    protected fun refresh(player: Player, inventory: Inventory) {
        val total = totalPages(player)
        val page = (playerPages[player.uniqueId] ?: 0).coerceIn(0, total - 1)
        playerPages[player.uniqueId] = page
        renderPage(player, inventory, page)
    }

    // ----- PluginGUI overrides ------------------------------------------------

    override fun setup(player: Player, inventory: Inventory) {
        playerPages[player.uniqueId] = 0
        renderPage(player, inventory, 0)
    }

    override fun onClick(event: InventoryClickEvent) {
        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        val page = playerPages[player.uniqueId] ?: return
        val slot = event.rawSlot

        // Ignore clicks outside the GUI inventory
        if (slot < 0 || slot >= rows * ROW_SIZE) return

        val navOffset = slot - navRowStart
        when {
            navOffset == PREVIOUS_OFFSET -> {
                if (page > 0) {
                    openPage(player, event.inventory, page - 1)
                    player.playSound(Sound.sound(Key.key("minecraft:ui.button.click"), Sound.Source.UI, 1f, 1f))
                }
            }

            navOffset == NEXT_OFFSET -> {
                val totalPages = totalPages(player)
                if (page < totalPages - 1) {
                    openPage(player, event.inventory, page + 1)
                    player.playSound(Sound.sound(Key.key("minecraft:ui.button.click"), Sound.Source.UI, 1f, 1f))
                }
            }

            navOffset == PAGE_INDICATOR_OFFSET -> return
            navOffset in 0 until ROW_SIZE -> onNavClick(event, navOffset)
            slot in contentSlotSet -> onContentClick(event, page)
        }
    }

    override fun onClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        playerPages.remove(player.uniqueId)
        shownPositions.remove(player.uniqueId)
    }

    // ----- Internal helpers ---------------------------------------------------

    /**
     * Calculates the total number of pages for the given [player] based on the
     * active [mode].
     */
    private fun totalPages(player: Player): Int = when (mode) {
        PagedGUIMode.LIST -> {
            val itemCount = getItems(player).size
            if (itemCount == 0) 1 else (itemCount + pageSize - 1) / pageSize
        }

        PagedGUIMode.SET -> {
            val maxPage = getSetItems(player).keys.maxOrNull() ?: -1
            maxOf(maxPage + 1, 1)
        }
    }

    /** Switches the player to the given [page] and re-renders the inventory. */
    private fun openPage(player: Player, inventory: Inventory, page: Int) {
        playerPages[player.uniqueId] = page
        renderPage(player, inventory, page)
    }

    /** Clears the inventory and populates it with the items for [page]. */
    private fun renderPage(player: Player, inventory: Inventory, page: Int) {
        inventory.clear()

        fillInventory(this, inventory)
        if (layout != PagedLayout.FULL) GUIFrame.draw(inventory, contentSlots)

        val totalPages = totalPages(player)
        var slots = contentSlots

        when (mode) {
            PagedGUIMode.LIST -> {
                val items = getItems(player)
                val start = page * pageSize
                val onPage = items.subList(minOf(start, items.size), minOf(start + pageSize, items.size))
                if (layout == PagedLayout.CENTERED) slots = GUIFrame.centeredSlots(rows, onPage.size)
                onPage.forEachIndexed { position, item -> inventory.setItem(slots[position], item) }
            }

            PagedGUIMode.SET -> {
                val pageItems = getSetItems(player)[page] ?: emptyMap()
                for ((position, item) in pageItems) {
                    contentSlots.getOrNull(position)?.let { inventory.setItem(it, item) }
                }
            }
        }
        shownPositions[player.uniqueId] = slots.withIndex().associate { (position, slot) -> slot to position }

        renderNavRow(player, inventory, page, totalPages)
    }

    /** Draws the navigation row: filler, then this GUI's own buttons, then the page controls. */
    private fun renderNavRow(player: Player, inventory: Inventory, page: Int, totalPages: Int) {
        // A framed menu carries its border all the way round, so the row below the
        // content matches the rest of it rather than changing colour.
        val filler = if (layout != PagedLayout.FULL) {
            GUIFrame.pane()
        } else {
            itemStack(Material.GRAY_STAINED_GLASS_PANE) {
                name(" ")
                hideTooltip(true)
            }
        }

        for (offset in 0 until ROW_SIZE) {
            inventory.setItem(navRowStart + offset, filler.clone())
        }

        for ((offset, button) in navButtons(player)) {
            if (offset in 0 until ROW_SIZE && offset !in RESERVED_OFFSETS) {
                inventory.setItem(navRowStart + offset, button)
            }
        }

        if (page > 0) {
            inventory.setItem(
                navRowStart + PREVIOUS_OFFSET, createNavItem(
                    Material.ARROW,
                    player.tr("gui.previous-page")
                )
            )
        }

        inventory.setItem(
            navRowStart + PAGE_INDICATOR_OFFSET, createNavItem(
                Material.PAPER,
                player.tr("gui.page", "page" to page + 1, "pages" to totalPages)
            )
        )

        if (page < totalPages - 1) {
            inventory.setItem(
                navRowStart + NEXT_OFFSET, createNavItem(
                    Material.ARROW,
                    player.tr("gui.next-page")
                )
            )
        }
    }

    /** Creates a navigation item with the given material and display name. */
    private fun createNavItem(material: Material, name: String): ItemStack {
        val item = itemStack(material) {
            this.name(name)
        }
        return item
    }

    companion object {
        private const val ROW_SIZE = 9
        private const val PREVIOUS_OFFSET = 0
        private const val PAGE_INDICATOR_OFFSET = 4
        private const val NEXT_OFFSET = 8

        /** Navigation-row offsets the page controls own, which [navButtons] may not use. */
        private val RESERVED_OFFSETS = setOf(PREVIOUS_OFFSET, PAGE_INDICATOR_OFFSET, NEXT_OFFSET)
    }

    /**
     * Pre-fills all slots in [inventory] with a filler glass pane determined
     * by the GUI's [FillMode].  Does nothing when the mode is [FillMode.NONE].
     */
    private fun fillInventory(gui: PluginGUI, inventory: Inventory) {
        val material = when (gui.fillMode) {
            FillMode.LIGHT -> Material.WHITE_STAINED_GLASS_PANE
            FillMode.DARK -> Material.BLACK_STAINED_GLASS_PANE
            FillMode.NONE -> return
        }
        val filler = itemStack(material) {
            name(" ")
            hideTooltip(true)
        }
        for (slot in 0 until inventory.size) {
            inventory.setItem(slot, filler.clone())
        }
    }
}
