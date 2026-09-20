package net.trilleo.mc.plugins.tritown.guis.shop

import net.trilleo.mc.plugins.tritown.enums.FillMode
import net.trilleo.mc.plugins.tritown.enums.TownyRequirement
import net.trilleo.mc.plugins.tritown.registration.GUIManager
import net.trilleo.mc.plugins.tritown.registration.PluginGUI
import net.trilleo.mc.plugins.tritown.shops.*
import net.trilleo.mc.plugins.tritown.utils.EconomyUtil
import net.trilleo.mc.plugins.tritown.utils.LoreUtil
import net.trilleo.mc.plugins.tritown.utils.itemStack
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
 * How many of one entry to buy.
 *
 * The amounts on offer are the ones a player already thinks in — a single, and
 * the stack fractions above it — rather than a number typed in chat, and they
 * are priced at the entry's own rate, so eight of something sold sixteen at a
 * time costs half of what the shelf quotes.
 *
 * An amount the player cannot take is drawn as grey glass saying why and does
 * nothing when clicked: a menu that offers something and then refuses it is
 * worse than one that says so up front. What greys an option out is the same
 * [ShopTrade.buyRefusal] the purchase itself runs, so the two cannot drift
 * apart. Only the money is asked about separately, because a balance may be
 * read to draw a menu but never to decide a charge.
 */
class ShopAmountGUI : PluginGUI(
    id = ID,
    titleKey = "gui.shop-amount.title",
    rows = 3,
    fillMode = FillMode.DARK,
) {

    private data class Target(val shopId: String, val entryId: String)

    private val choosing = ConcurrentHashMap<UUID, Target>()

    /** Asks [player] how many of [entry] they want. */
    fun open(player: Player, shop: ShopDefinition, entry: ShopEntry) {
        choosing[player.uniqueId] = Target(shop.id, entry.id)
        GUIManager.open(player, ID)
    }

    override fun setup(player: Player, inventory: Inventory) {
        val (shop, entry) = resolve(player) ?: return
        val standing = ShopAccess.standing(player)

        inventory.setItem(SLOT_GOODS, goods(player, entry, standing))
        AMOUNTS.forEachIndexed { index, amount ->
            inventory.setItem(SLOT_FIRST + index, option(player, shop, entry, amount, standing))
        }
        inventory.setItem(
            SLOT_BACK,
            button(player, Material.ARROW, "gui.shop-amount.back", "gui.shop-amount.back-lore"),
        )
    }

    override fun onClick(event: InventoryClickEvent) {
        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        val (shop, entry) = resolve(player) ?: return

        if (event.rawSlot == SLOT_BACK) {
            ShopRender.navigate { ShopGUI.show(player, shop) }
            return
        }

        val index = event.rawSlot - SLOT_FIRST
        val amount = if (index >= 0) AMOUNTS.getOrNull(index) else null
        if (amount != null) buy(player, shop, entry, amount, event.inventory)
    }

    override fun onClose(event: InventoryCloseEvent) {
        choosing.remove((event.player as? Player)?.uniqueId ?: return)
    }

    // ── Buying ──────────────────────────────────────────────────────────

    /**
     * Buys [amount], then redraws rather than closing.
     *
     * A player who wanted sixteen often wants sixteen more, and what they can
     * still take has changed under them — their balance, the stock and their
     * own limit all moved.
     */
    private fun buy(player: Player, shop: ShopDefinition, entry: ShopEntry, amount: Int, inventory: Inventory) {
        if (refusal(player, shop, entry, amount, ShopAccess.standing(player)) != null) return
        if (ShopConfirmGUI.askIfDear(player, shop, entry, amount)) return

        when (val result = ShopTrade.buy(player, shop, entry, amount)) {
            is ShopTrade.Result.Success -> {
                ShopRender.announce(player, entry, result)
                setup(player, inventory)
            }

            is ShopTrade.Result.Failure -> ShopRender.refuse(player, result)
        }
    }

    /**
     * Why [amount] is not on offer, or `null` when it is.
     *
     * The trade's own refusal, plus the one thing it deliberately leaves out:
     * whether the player has the money. A balance is read here only to draw the
     * menu — the charge itself is still decided by the withdrawal.
     */
    private fun refusal(
        player: Player,
        shop: ShopDefinition,
        entry: ShopEntry,
        amount: Int,
        standing: Set<TownyRequirement>,
    ): ShopTrade.Result.Failure? {
        ShopTrade.buyRefusal(player, shop, entry, amount, standing)?.let { return it }

        val quote = ShopTrade.quoteBuy(player, entry, amount, standing) ?: return null
        if (quote.hasMoney && EconomyUtil.isAvailable && !EconomyUtil.has(player, quote.money)) {
            return ShopTrade.Result.Failure(
                "shop.error.cannot-afford",
                listOf("price" to ShopRender.money(quote.money)),
            )
        }

        return null
    }

    // ── Drawing ─────────────────────────────────────────────────────────

    /** The goods themselves, priced as the shelf prices them, so the rate the amounts follow is on screen. */
    private fun goods(player: Player, entry: ShopEntry, standing: Set<TownyRequirement>): ItemStack {
        val bundle = entry.bundleSize
        val perPurchase =
            if (bundle > 1) listOf(player.tr("gui.shop-amount.bundle", "amount" to bundle)) else emptyList()

        return LoreUtil.withLore(
            entry.displayStack(),
            ShopRender.sections(listOf(ShopRender.buyLines(player, entry, bundle, standing), perPurchase)),
        )
    }

    private fun option(
        player: Player,
        shop: ShopDefinition,
        entry: ShopEntry,
        amount: Int,
        standing: Set<TownyRequirement>,
    ): ItemStack {
        val refusal = refusal(player, shop, entry, amount, standing)
        val name = player.tr(
            if (refusal == null) "gui.shop-amount.option" else "gui.shop-amount.option-locked",
            "amount" to amount,
            "item" to ShopRender.itemName(entry.item),
        )

        val closing = refusal
            ?.let { listOf(player.tr(it.key, *it.args.toTypedArray())) }
            ?: listOf(player.tr("gui.shop-amount.click-buy"))

        val lore = ShopRender.sections(listOf(ShopRender.buyLines(player, entry, amount, standing), closing))

        if (refusal != null) {
            return itemStack(Material.GRAY_STAINED_GLASS_PANE) {
                name(name)
                meta { lore(LoreUtil.wrapLore(lore.joinToString("<newline>"))) }
            }
        }

        // The goods themselves, stacked as far as a slot can show, so the amount is read before the label is.
        val shown = entry.item.clone().apply { this.amount = amount.coerceIn(1, entry.item.maxStackSize) }
        return ShopRender.named(shown, name, lore)
    }

    private fun button(player: Player, material: Material, nameKey: String, loreKey: String): ItemStack =
        itemStack(material) {
            name(player.tr(nameKey))
            meta { lore(LoreUtil.wrapLore(player.tr(loreKey))) }
        }

    private fun resolve(player: Player): Pair<ShopDefinition, ShopEntry>? {
        val target = choosing[player.uniqueId] ?: return null
        val shop = ShopManager.get(target.shopId) ?: return null
        val entry = shop.entry(target.entryId) ?: return null
        return shop to entry
    }

    companion object {
        const val ID = "shop-amount"

        /** What is on offer: a single, and the stack fractions above it. */
        val AMOUNTS = listOf(1, 8, 16, 32, 64)

        /** The middle row, centred: five options either side of slot 13 rather than packed against the left. */
        private const val SLOT_FIRST = 11
        private const val SLOT_GOODS = 4
        private const val SLOT_BACK = 22

        /** Opens the amount menu for [entry] through the registered instance. */
        fun show(player: Player, shop: ShopDefinition, entry: ShopEntry): Boolean {
            val gui = GUIManager.getGUI(ID) as? ShopAmountGUI ?: return false
            gui.open(player, shop, entry)
            return true
        }
    }
}
