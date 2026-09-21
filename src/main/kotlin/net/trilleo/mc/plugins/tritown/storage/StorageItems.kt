package net.trilleo.mc.plugins.tritown.storage

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.trilleo.mc.plugins.tritown.menu.MenuItem
import org.bukkit.Tag
import org.bukkit.block.ShulkerBox
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BlockStateMeta
import org.bukkit.inventory.meta.BundleMeta

/**
 * What a storage will hold, and how its items are merged, ordered and unpacked.
 */
object StorageItems {

    /** [SlotFill]'s rules for real items. */
    val rules = object : SlotFill.Rules<ItemStack> {
        override fun similar(a: ItemStack, b: ItemStack): Boolean = a.isSimilar(b)
        override fun maxStack(stack: ItemStack): Int = stack.maxStackSize
        override fun amount(stack: ItemStack): Int = stack.amount
        override fun withAmount(stack: ItemStack, amount: Int): ItemStack = stack.clone().apply { this.amount = amount }
    }

    /** By material, then by name, so like sits beside like and a renamed stack keeps its place. */
    val order: Comparator<ItemStack> = compareBy<ItemStack> { it.type.name }
        .thenBy { stack -> stack.itemMeta?.displayName()?.let(PlainTextComponentSerializer.plainText()::serialize) ?: "" }
        .thenByDescending { it.amount }

    /**
     * Whether [item] may be put in a storage.
     *
     * A shulker box or bundle with anything inside is refused, because a page
     * of full boxes would hold many pages' worth and nobody would buy one; the
     * Unpack button empties them in instead. The menu item never leaves its slot.
     */
    fun accepts(item: ItemStack?): Boolean {
        if (item == null || item.type.isAir) return true
        if (MenuItem.isMenuItem(item)) return false
        return contents(item).isEmpty()
    }

    /** Whether [item] is a container Unpack can empty, whatever it holds. */
    fun isContainer(item: ItemStack?): Boolean =
        item != null && (Tag.SHULKER_BOXES.isTagged(item.type) || item.itemMeta is BundleMeta)

    /** What [item] carries inside it, when it is a shulker box or a bundle. */
    fun contents(item: ItemStack): List<ItemStack> {
        val meta = item.itemMeta ?: return emptyList()
        if (meta is BundleMeta) return meta.items.filter { !it.type.isAir }
        if (meta is BlockStateMeta && Tag.SHULKER_BOXES.isTagged(item.type) && meta.hasBlockState()) {
            val box = meta.blockState as? ShulkerBox ?: return emptyList()
            return box.inventory.contents.filterNotNull().filter { !it.type.isAir }
        }
        return emptyList()
    }

    /**
     * Everything [item] carries, with any container inside it emptied out too,
     * so a bundle packed in a shulker box cannot ride into the storage full.
     */
    fun unpacked(item: ItemStack): List<ItemStack> = contents(item).flatMap { inner ->
        if (contents(inner).isEmpty()) listOf(inner) else listOf(emptied(inner)) + unpacked(inner)
    }

    /** [item] with nothing inside it. */
    fun emptied(item: ItemStack): ItemStack {
        val copy = item.clone()
        copy.editMeta { meta ->
            if (meta is BundleMeta) meta.setItems(null)
            if (meta is BlockStateMeta && meta.hasBlockState()) {
                val box = meta.blockState as? ShulkerBox ?: return@editMeta
                box.inventory.clear()
                meta.blockState = box
            }
        }
        return copy
    }
}
