package net.trilleo.mc.plugins.tritown.guis.storage

import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.trilleo.mc.plugins.tritown.economy.Money
import net.trilleo.mc.plugins.tritown.guis.admin.PanelRender
import net.trilleo.mc.plugins.tritown.storage.PlayerStorage
import net.trilleo.mc.plugins.tritown.storage.StorageManager
import net.trilleo.mc.plugins.tritown.storage.StoragePage
import net.trilleo.mc.plugins.tritown.utils.ComponentUtil
import net.trilleo.mc.plugins.tritown.utils.EconomyUtil
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.*

/** The pieces the storage menus are drawn with. */
object StorageRender {

    private val refuse = Sound.sound(Key.key("minecraft:block.note_block.bass"), Sound.Source.UI, 1f, 0.8f)

    /** What a page is shown as when its owner has not picked anything. */
    val DEFAULT_ICON: Material = Material.CHEST

    /** [money] written out with the server's currency, escaped for embedding in MiniMessage. */
    fun money(money: Money): String {
        val amount = StorageManager.toDouble(money)
        return if (EconomyUtil.isAvailable) ComponentUtil.escape(EconomyUtil.format(amount)) else amount.toString()
    }

    /** [owner]'s name, escaped, or a short form of their id when the server has never seen them. */
    fun ownerName(owner: UUID): String =
        ComponentUtil.escape(Bukkit.getOfflinePlayer(owner).name ?: owner.toString().substringBefore('-'))

    /** The name [page] is shown by: the one its owner gave it, or its number. */
    fun pageName(viewer: Player, page: StoragePage?, index: Int): String =
        page?.name?.let(ComponentUtil::escape) ?: viewer.tr("gui.storage.page-default", "page" to index + 1)

    /** The material [page] is shown as. */
    fun icon(page: StoragePage?): Material =
        page?.icon?.let(Material::matchMaterial)?.takeIf { it.isItem && !it.isAir } ?: DEFAULT_ICON

    /** An item's name in the viewer's own language, whatever language the server runs in. */
    fun itemName(material: Material): String = "<lang:${material.translationKey()}>"

    /**
     * [page] as a card for the overview: its name, how full it is, and what
     * there is most of.
     */
    fun pageCard(viewer: Player, page: StoragePage?, index: Int, current: Boolean): ItemStack {
        val used = page?.used ?: 0
        val lines = mutableListOf(
            viewer.tr("gui.storage.overview-used", "amount" to used, "total" to StoragePage.SIZE),
        )

        val top = page?.slots?.filterNotNull()
            ?.groupBy { it.type }
            ?.mapValues { (_, stacks) -> stacks.sumOf { it.amount } }
            ?.entries?.sortedByDescending { it.value }
            ?.take(PREVIEW_SIZE)
            .orEmpty()
        if (top.isNotEmpty()) {
            lines += ""
            top.forEach { (material, amount) ->
                lines += viewer.tr("gui.storage.overview-item", "amount" to amount, "name" to itemName(material))
            }
        }

        lines += ""
        lines += viewer.tr(if (current) "gui.storage.overview-current" else "gui.storage.overview-open")

        return PanelRender.card(icon(page), viewer.tr("gui.storage.overview-name", "name" to pageName(viewer, page, index)), lines)
            .also { item -> if (current) item.editMeta { it.setEnchantmentGlintOverride(true) } }
    }

    /** The offer of one more page, or `null` when [storage] cannot grow. */
    fun buyCard(viewer: Player, storage: PlayerStorage): ItemStack? {
        val cost = StorageManager.nextPageCost(storage) ?: return null
        val page = StorageManager.owned(storage) + 1
        return PanelRender.card(
            Material.WRITABLE_BOOK,
            viewer.tr("gui.storage.buy", "page" to page),
            listOf(
                viewer.tr("gui.storage.buy-cost", "amount" to money(cost)),
                viewer.tr("gui.storage.buy-balance", "amount" to balance(viewer)),
                "",
                viewer.tr("gui.storage.buy-click"),
            ),
        )
    }

    /** [player]'s balance, written out, or a dash when there is no economy to ask. */
    fun balance(player: Player): String =
        if (EconomyUtil.isAvailable) ComponentUtil.escape(EconomyUtil.format(EconomyUtil.balance(player)))
        else player.tr("common.none")

    /** Tells [player] why something did not happen, with a sound so a refused click is never silent. */
    fun refuse(player: Player, key: String, vararg args: Pair<String, Any?>) {
        player.playSound(refuse)
        player.sendPrefixed(player.tr("common.error", "message" to player.tr(key, *args)))
    }

    private const val PREVIEW_SIZE = 3
}
