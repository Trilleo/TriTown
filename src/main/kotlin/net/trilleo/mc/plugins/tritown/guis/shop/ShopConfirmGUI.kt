package net.trilleo.mc.plugins.tritown.guis.shop

import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.trilleo.mc.plugins.tritown.enums.FillMode
import net.trilleo.mc.plugins.tritown.registration.GUIManager
import net.trilleo.mc.plugins.tritown.registration.PluginGUI
import net.trilleo.mc.plugins.tritown.shops.ShopDefinition
import net.trilleo.mc.plugins.tritown.shops.ShopEntry
import net.trilleo.mc.plugins.tritown.shops.ShopManager
import net.trilleo.mc.plugins.tritown.shops.ShopTrade
import net.trilleo.mc.plugins.tritown.utils.LoreUtil
import net.trilleo.mc.plugins.tritown.utils.itemStack
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * A last look at an expensive purchase before it goes through.
 *
 * Only shown above `shops.confirm-above`, so an ordinary purchase is still a
 * single click. The price is re-quoted when the player accepts rather than
 * being carried over from here, so a discount that lapsed while the menu sat
 * open cannot be spent.
 */
class ShopConfirmGUI : PluginGUI(
    id = ID,
    titleKey = "gui.shop-confirm.title",
    rows = 3,
    fillMode = FillMode.DARK,
) {

    private data class Pending(val shopId: String, val entryId: String, val bundles: Int)

    private val pending = ConcurrentHashMap<UUID, Pending>()

    /** Asks [player] to confirm buying [bundles] of [entry]. */
    fun open(player: Player, shop: ShopDefinition, entry: ShopEntry, bundles: Int) {
        pending[player.uniqueId] = Pending(shop.id, entry.id, bundles)
        GUIManager.open(player, ID)
    }

    override fun setup(player: Player, inventory: Inventory) {
        val (shop, entry, bundles) = resolve(player) ?: return
        val quote = ShopTrade.quoteBuy(player, entry, bundles)

        val lines = buildList {
            add(player.tr("gui.shop-confirm.amount", "amount" to entry.bundleSize * bundles))
            if (quote != null && quote.hasMoney) {
                add(player.tr("gui.shop-confirm.price", "price" to ShopRender.money(quote.money)))
            }
            entry.buy?.items?.forEach { add(ShopRender.itemLine(player, it, bundles, "gui.shop.buy-item")) }
        }

        inventory.setItem(SLOT_GOODS, ShopRender.withLore(entry.displayStack(), lines))
        inventory.setItem(
            SLOT_ACCEPT,
            button(player, Material.LIME_CONCRETE, "gui.shop-confirm.accept", "gui.shop-confirm.accept-lore"),
        )
        inventory.setItem(
            SLOT_CANCEL,
            button(player, Material.RED_CONCRETE, "gui.shop-confirm.cancel", "gui.shop-confirm.cancel-lore"),
        )
    }

    override fun onClick(event: InventoryClickEvent) {
        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        when (event.rawSlot) {
            SLOT_ACCEPT -> accept(player)
            SLOT_CANCEL -> back(player)
            else -> return
        }
    }

    override fun onClose(event: InventoryCloseEvent) {
        pending.remove((event.player as? Player)?.uniqueId ?: return)
    }

    private fun accept(player: Player) {
        val (shop, entry, bundles) = resolve(player) ?: return
        when (val result = ShopTrade.buy(player, shop, entry, bundles)) {
            is ShopTrade.Result.Success -> {
                player.sendPrefixed(
                    player.tr(
                        "shop.traded",
                        "amount" to entry.bundleSize * result.bundles,
                        "item" to ShopRender.itemName(entry.item),
                        "price" to ShopRender.money(result.money),
                    )
                )
                player.playSound(Sound.sound(Key.key("minecraft:entity.villager.yes"), Sound.Source.UI, 1f, 1f))
            }

            is ShopTrade.Result.Failure -> player.sendPrefixed(
                player.tr("common.error", "message" to player.tr(result.key, *result.args.toTypedArray()))
            )
        }

        back(player)
    }

    /** Returns to the shop the purchase came from, so a cancel does not dump the player back into the world. */
    private fun back(player: Player) {
        val shop = pending[player.uniqueId]?.let { ShopManager.get(it.shopId) }
        if (shop == null) {
            player.closeInventory()
            return
        }
        ShopRender.navigate { ShopGUI.show(player, shop) }
    }

    private fun resolve(player: Player): Triple<ShopDefinition, ShopEntry, Int>? {
        val held = pending[player.uniqueId] ?: return null
        val shop = ShopManager.get(held.shopId) ?: return null
        val entry = shop.entry(held.entryId) ?: return null
        return Triple(shop, entry, held.bundles)
    }

    private fun button(player: Player, material: Material, nameKey: String, loreKey: String): ItemStack =
        itemStack(material) {
            name(player.tr(nameKey))
            meta { lore(LoreUtil.wrapLore(player.tr(loreKey))) }
        }

    companion object {
        const val ID = "shop-confirm"

        private const val SLOT_GOODS = 13
        private const val SLOT_ACCEPT = 11
        private const val SLOT_CANCEL = 15
    }
}
