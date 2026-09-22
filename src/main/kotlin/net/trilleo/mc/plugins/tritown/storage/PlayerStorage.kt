package net.trilleo.mc.plugins.tritown.storage

import org.bukkit.inventory.ItemStack
import java.util.*

/**
 * One page of a storage: the five rows above the storage menu's buttons.
 *
 * @param name a name the owner gave it, as they typed it; escaped whenever it is shown
 * @param icon the name of the material it is shown as in the overview
 */
class StoragePage(var name: String? = null, var icon: String? = null) {

    val slots: Array<ItemStack?> = arrayOfNulls(SIZE)

    /** How many slots hold something. */
    val used: Int get() = slots.count { it != null }

    val isEmpty: Boolean get() = slots.all { it == null }

    companion object {
        /** Slots on a page: a large chest less the row of buttons. */
        const val SIZE = 45
    }
}

/**
 * Everything one player keeps in their storage.
 *
 * Only [StorageManager] creates or changes one. The pages list only ever holds
 * pages that have been opened or filled, so a storage that owns twenty pages
 * but has used two keeps two in memory and on disk.
 *
 * @param purchased  how many pages the owner has paid for, or been given, beyond the free ones
 * @param unreadable items a load could not decode, carried along untouched so a save never drops them
 */
class PlayerStorage(
    val owner: UUID,
    @Volatile var purchased: Int,
    pages: List<StoragePage>,
    val unreadable: List<String>,
) {

    private val pages: MutableList<StoragePage> = pages.toMutableList()

    /** Pages that exist, which may be fewer than the pages owned. */
    val existing: List<StoragePage> get() = pages

    /** Whether anything has changed since this storage was last written. */
    @Volatile
    var dirty: Boolean = false

    /** The page at [index], created blank when it has not been used yet. */
    fun page(index: Int): StoragePage {
        require(index >= 0) { "Pages are counted from zero" }
        while (pages.size <= index) pages += StoragePage()
        return pages[index]
    }

    /** The page at [index] when it exists, without creating it. */
    fun pageOrNull(index: Int): StoragePage? = pages.getOrNull(index)

    /** One past the last page that holds anything, so pages with items are never hidden. */
    val filledPages: Int get() = pages.indexOfLast { !it.isEmpty } + 1

    /** How many slots hold something, across every page. */
    val usedSlots: Int get() = pages.sumOf { it.used }
}
