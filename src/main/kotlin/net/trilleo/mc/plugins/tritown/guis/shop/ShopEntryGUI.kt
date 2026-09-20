package net.trilleo.mc.plugins.tritown.guis.shop

import net.trilleo.mc.plugins.tritown.config.ShopSettings
import net.trilleo.mc.plugins.tritown.enums.FillMode
import net.trilleo.mc.plugins.tritown.enums.LimitPeriod
import net.trilleo.mc.plugins.tritown.enums.MatchMode
import net.trilleo.mc.plugins.tritown.registration.GUIManager
import net.trilleo.mc.plugins.tritown.registration.PluginGUI
import net.trilleo.mc.plugins.tritown.shops.*
import net.trilleo.mc.plugins.tritown.utils.*
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Everything about one entry: what it costs, what it pays, who may have it and
 * how much of it.
 *
 * Anything that is a number or a name is typed in chat rather than clicked up
 * one at a time — a price of 12500 is not something to reach by clicking. A
 * left click asks for the value, a right click clears it, and settings with only
 * a few states cycle on a click instead.
 */
class ShopEntryGUI : PluginGUI(
    id = ID,
    titleKey = "gui.shop-entry.title",
    rows = 6,
    fillMode = FillMode.DARK,
) {

    private data class Target(val shopId: String, val entryId: String)

    private val editing = ConcurrentHashMap<UUID, Target>()

    /** Opens the editor for [entry] of [shop]. */
    fun open(player: Player, shop: ShopDefinition, entry: ShopEntry) {
        editing[player.uniqueId] = Target(shop.id, entry.id)
        GUIManager.open(player, ID)
    }

    override fun setup(player: Player, inventory: Inventory) {
        val (_, entry) = resolve(player) ?: return

        inventory.setItem(SLOT_GOODS, goods(player, entry))
        inventory.setItem(SLOT_BUNDLE, bundle(player, entry))

        inventory.setItem(SLOT_BUY_TOGGLE, toggle(player, entry.isBuyable, "gui.shop-entry.buyable"))
        inventory.setItem(SLOT_BUY_MONEY, money(player, entry.buy, "gui.shop-entry.buy-price"))
        inventory.setItem(SLOT_BUY_ITEMS, items(player, entry.buy, "gui.shop-entry.buy-items"))

        inventory.setItem(SLOT_SELL_TOGGLE, toggle(player, entry.isSellable, "gui.shop-entry.sellable"))
        inventory.setItem(SLOT_SELL_MONEY, money(player, entry.sell, "gui.shop-entry.sell-price"))
        inventory.setItem(SLOT_SELL_ITEMS, items(player, entry.sell, "gui.shop-entry.sell-items"))

        inventory.setItem(SLOT_LIMIT, limit(player, entry))
        inventory.setItem(SLOT_STOCK, stock(player, entry))
        inventory.setItem(SLOT_PERMISSION, permission(player, entry))
        inventory.setItem(SLOT_TOWNY, towny(player, entry))
        inventory.setItem(SLOT_HIDDEN, toggle(player, entry.gate.hideWhenLocked, "gui.shop-entry.hidden"))
        inventory.setItem(SLOT_DISCOUNT, toggle(player, entry.discountable, "gui.shop-entry.discountable"))
        inventory.setItem(SLOT_MATCH, match(player, entry))

        inventory.setItem(SLOT_BACK, button(player, Material.ARROW, "gui.shop-entry.back", "gui.shop-entry.back-lore"))
    }

    override fun onClick(event: InventoryClickEvent) {
        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        val (shop, entry) = resolve(player) ?: return
        val clear = event.click == ClickType.RIGHT || event.click == ClickType.SHIFT_RIGHT

        when (event.rawSlot) {
            SLOT_BUY_TOGGLE -> entry.buy = if (entry.isBuyable) null else ShopCost.FREE
            SLOT_SELL_TOGGLE -> entry.sell = if (entry.isSellable) null else suggestedSell(entry)
            SLOT_BUY_MONEY -> return askMoney(player, shop, entry, buying = true, clear = clear)
            SLOT_SELL_MONEY -> return askMoney(player, shop, entry, buying = false, clear = clear)
            SLOT_BUY_ITEMS -> return ShopRender.navigate { ShopCostGUI.show(player, shop, entry, buying = true) }
            SLOT_SELL_ITEMS -> return ShopRender.navigate { ShopCostGUI.show(player, shop, entry, buying = false) }
            SLOT_BUNDLE -> return editBundle(player, shop, entry)
            SLOT_LIMIT -> return editLimit(player, shop, entry, event.click)
            SLOT_STOCK -> return editStock(player, shop, entry, clear)
            SLOT_PERMISSION -> return askPermission(player, shop, entry, clear)
            SLOT_TOWNY -> entry.gate = entry.gate.copy(towny = cycle(entry.gate.towny, event.click))
            SLOT_HIDDEN -> entry.gate = entry.gate.copy(hideWhenLocked = !entry.gate.hideWhenLocked)
            SLOT_DISCOUNT -> entry.discountable = !entry.discountable
            SLOT_MATCH -> entry.matchMode =
                if (entry.matchMode == MatchMode.EXACT) MatchMode.MATERIAL else MatchMode.EXACT

            SLOT_BACK -> return ShopRender.navigate { ShopEditorGUI.show(player, shop) }
            else -> return
        }

        ShopManager.save()
        setup(player, event.inventory)
    }

    override fun onClose(event: InventoryCloseEvent) {
        editing.remove((event.player as? Player)?.uniqueId ?: return)
    }

    // ── Editing ─────────────────────────────────────────────────────────

    private fun askMoney(player: Player, shop: ShopDefinition, entry: ShopEntry, buying: Boolean, clear: Boolean) {
        if (clear) {
            applyMoney(entry, buying, 0.0)
            ShopManager.save()
            ShopRender.navigate { show(player, shop, entry) }
            return
        }

        prompt(player, shop, entry, player.tr("gui.shop-entry.prompt-price")) { input ->
            val amount = input.toDoubleOrNull()
            if (amount == null || amount < 0.0) {
                player.sendPrefixed(player.tr("common.error", "message" to player.tr("common.invalid-amount")))
            } else {
                applyMoney(entry, buying, amount)
                ShopManager.save()
            }
        }
    }

    private fun applyMoney(entry: ShopEntry, buying: Boolean, amount: Double) {
        if (buying) {
            entry.buy = (entry.buy ?: ShopCost.FREE).copy(money = amount)
        } else {
            entry.sell = (entry.sell ?: ShopCost.FREE).copy(money = amount)
        }
    }

    private fun askPermission(player: Player, shop: ShopDefinition, entry: ShopEntry, clear: Boolean) {
        if (clear) {
            entry.gate = entry.gate.copy(permission = null)
            ShopManager.save()
            ShopRender.navigate { show(player, shop, entry) }
            return
        }

        prompt(player, shop, entry, player.tr("gui.shop-entry.prompt-permission")) { input ->
            entry.gate = entry.gate.copy(permission = input.ifBlank { null })
            ShopManager.save()
        }
    }

    /**
     * Asks how many items one purchase should hand over.
     *
     * Not capped at a stack: a bundle of 128 bread is handed over as two stacks,
     * and refusing it would only make an administrator create two entries for
     * what is one thing being sold.
     */
    private fun editBundle(player: Player, shop: ShopDefinition, entry: ShopEntry) {
        prompt(player, shop, entry, player.tr("gui.shop-entry.prompt-bundle")) { input ->
            val amount = input.toIntOrNull()
            if (amount == null || amount < 1) {
                player.sendPrefixed(player.tr("common.error", "message" to player.tr("common.invalid-amount")))
            } else {
                entry.bundle = amount
                ShopManager.save()
            }
        }
    }

    /** Left sets the amount, right clears the limit, and a middle click steps the window. */
    private fun editLimit(player: Player, shop: ShopDefinition, entry: ShopEntry, click: ClickType) {
        when (click) {
            ClickType.RIGHT, ClickType.SHIFT_RIGHT -> {
                entry.limit = null
                ShopManager.save()
                ShopRender.navigate { show(player, shop, entry) }
            }

            ClickType.MIDDLE -> {
                val current = entry.limit ?: ShopLimit(1, LimitPeriod.NONE)
                entry.limit = current.copy(period = cycle(current.period, ClickType.LEFT))
                ShopManager.save()
                ShopRender.navigate { show(player, shop, entry) }
            }

            else -> prompt(player, shop, entry, player.tr("gui.shop-entry.prompt-limit")) { input ->
                val amount = input.toIntOrNull()
                if (amount == null || amount < 0) {
                    player.sendPrefixed(player.tr("common.error", "message" to player.tr("common.invalid-amount")))
                } else {
                    entry.limit = if (amount == 0) null else {
                        (entry.limit ?: ShopLimit(amount, LimitPeriod.DAILY)).copy(amount = amount)
                    }
                    ShopManager.save()
                }
            }
        }
    }

    /** Asks for `max` and `restock seconds` on one line, because they only make sense together. */
    private fun editStock(player: Player, shop: ShopDefinition, entry: ShopEntry, clear: Boolean) {
        if (clear) {
            entry.stock = null
            ShopManager.save()
            ShopRender.navigate { show(player, shop, entry) }
            return
        }

        prompt(player, shop, entry, player.tr("gui.shop-entry.prompt-stock")) { input ->
            val parts = input.split(' ', limit = 2)
            val max = parts.getOrNull(0)?.toIntOrNull()
            val seconds = parts.getOrNull(1)?.toLongOrNull() ?: 0L

            if (max == null || max < 0 || seconds < 0L) {
                player.sendPrefixed(player.tr("common.error", "message" to player.tr("common.invalid-amount")))
            } else {
                entry.stock = if (max == 0) null else ShopStock(max, seconds, max, System.currentTimeMillis())
                ShopManager.save()
            }
        }
    }

    /**
     * Closes the menu, asks [question], and reopens on the answer.
     *
     * Reopened either way, so a mistyped price does not leave the administrator
     * standing in the world wondering where the editor went.
     */
    private fun prompt(
        player: Player,
        shop: ShopDefinition,
        entry: ShopEntry,
        question: String,
        onInput: (String) -> Unit,
    ) {
        player.closeInventory()
        ChatPrompt.ask(player, question) { input ->
            onInput(input)
            show(player, shop, entry)
        }
    }

    private fun suggestedSell(entry: ShopEntry): ShopCost {
        val rate = if (ShopSettings.isLoaded) ShopSettings.snapshot.sellRate else 0.5
        val buy = entry.buy?.money ?: 0.0
        return ShopCost(ShopPricing.round(buy * rate, MONEY_SCALE))
    }

    private inline fun <reified T : Enum<T>> cycle(value: T, click: ClickType): T {
        val values = enumValues<T>()
        val step = if (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) -1 else 1
        return values[(value.ordinal + step + values.size) % values.size]
    }

    // ── Drawing ─────────────────────────────────────────────────────────

    private fun goods(player: Player, entry: ShopEntry): ItemStack = ShopRender.withLore(
        entry.displayStack(),
        listOf(player.tr("gui.shop-entry.bundle", "amount" to entry.bundleSize)),
    )

    private fun bundle(player: Player, entry: ShopEntry): ItemStack = itemStack(Material.PAPER) {
        name(player.tr("gui.shop-entry.bundle-size"))
        meta {
            lore(
                LoreUtil.wrapLore(
                    player.tr("gui.shop-entry.amount", "amount" to entry.bundleSize) +
                            "<newline>" + player.tr("gui.shop-entry.bundle-lore") +
                            "<newline>" + player.tr("gui.shop-entry.click-set")
                )
            )
        }
    }

    private fun toggle(player: Player, on: Boolean, nameKey: String): ItemStack =
        itemStack(if (on) Material.LIME_DYE else Material.GRAY_DYE) {
            name(player.tr(nameKey))
            meta {
                lore(
                    LoreUtil.wrapLore(
                        player.tr("gui.shop-entry.state", "state" to player.tr(if (on) "common.on" else "common.off")) +
                                "<newline>" + player.tr("gui.shop-entry.click-toggle")
                    )
                )
            }
        }

    private fun money(player: Player, cost: ShopCost?, nameKey: String): ItemStack = itemStack(Material.GOLD_INGOT) {
        name(player.tr(nameKey))
        meta {
            lore(
                LoreUtil.wrapLore(
                    player.tr("gui.shop-entry.amount", "amount" to ShopRender.money(cost?.money ?: 0.0)) +
                            "<newline>" + player.tr("gui.shop-entry.click-set") +
                            "<newline>" + player.tr("gui.shop-entry.right-click-clear")
                )
            )
        }
    }

    private fun items(player: Player, cost: ShopCost?, nameKey: String): ItemStack = itemStack(Material.CHEST) {
        name(player.tr(nameKey))
        meta {
            lore(
                LoreUtil.wrapLore(
                    player.tr("gui.shop-entry.item-count", "amount" to (cost?.items?.size ?: 0)) +
                            "<newline>" + player.tr("gui.shop-entry.click-open")
                )
            )
        }
    }

    private fun limit(player: Player, entry: ShopEntry): ItemStack = itemStack(Material.CLOCK) {
        name(player.tr("gui.shop-entry.limit"))
        meta {
            val current = entry.limit
            val value = if (current == null) {
                player.tr("common.none")
            } else {
                player.tr(
                    "gui.shop-entry.limit-value",
                    "amount" to current.amount,
                    "period" to ShopRender.periodName(player, current.period),
                )
            }

            lore(
                LoreUtil.wrapLore(
                    player.tr("gui.shop-entry.amount", "amount" to value) +
                            "<newline>" + player.tr("gui.shop-entry.count-lore") +
                            "<newline>" + player.tr("gui.shop-entry.click-set") +
                            "<newline>" + player.tr("gui.shop-entry.middle-click-period") +
                            "<newline>" + player.tr("gui.shop-entry.right-click-clear")
                )
            )
        }
    }

    private fun stock(player: Player, entry: ShopEntry): ItemStack = itemStack(Material.BARREL) {
        name(player.tr("gui.shop-entry.stock"))
        meta {
            val current = entry.stock
            val value = if (current == null) {
                player.tr("gui.shop-entry.stock-unlimited")
            } else {
                player.tr(
                    "gui.shop-entry.stock-value",
                    "amount" to current.remaining,
                    "max" to current.max,
                    "seconds" to current.restockSeconds,
                )
            }

            lore(
                LoreUtil.wrapLore(
                    player.tr("gui.shop-entry.amount", "amount" to value) +
                            "<newline>" + player.tr("gui.shop-entry.count-lore") +
                            "<newline>" + player.tr("gui.shop-entry.click-set") +
                            "<newline>" + player.tr("gui.shop-entry.right-click-clear")
                )
            )
        }
    }

    private fun permission(player: Player, entry: ShopEntry): ItemStack = itemStack(Material.NAME_TAG) {
        name(player.tr("gui.shop-entry.permission"))
        meta {
            lore(
                LoreUtil.wrapLore(
                    player.tr(
                        "gui.shop-entry.amount",
                        "amount" to (entry.gate.permission ?: player.tr("common.none")),
                    ) +
                            "<newline>" + player.tr("gui.shop-entry.click-set") +
                            "<newline>" + player.tr("gui.shop-entry.right-click-clear")
                )
            )
        }
    }

    private fun towny(player: Player, entry: ShopEntry): ItemStack = itemStack(Material.OAK_SIGN) {
        name(player.tr("gui.shop-entry.towny"))
        meta {
            lore(
                LoreUtil.wrapLore(
                    player.tr(
                        "gui.shop-entry.amount",
                        "amount" to ShopRender.requirementName(player, entry.gate.towny),
                    ) + "<newline>" + player.tr("gui.shop-entry.click-cycle")
                )
            )
        }
    }

    private fun match(player: Player, entry: ShopEntry): ItemStack = itemStack(Material.COMPARATOR) {
        name(player.tr("gui.shop-entry.match"))
        meta {
            lore(
                LoreUtil.wrapLore(
                    player.tr("gui.shop-entry.amount", "amount" to ShopRender.matchName(player, entry.matchMode)) +
                            "<newline>" + player.tr("gui.shop-entry.match-lore") +
                            "<newline>" + player.tr("gui.shop-entry.click-toggle")
                )
            )
        }
    }

    private fun button(player: Player, material: Material, nameKey: String, loreKey: String): ItemStack =
        itemStack(material) {
            name(player.tr(nameKey))
            meta { lore(LoreUtil.wrapLore(player.tr(loreKey))) }
        }

    private fun resolve(player: Player): Pair<ShopDefinition, ShopEntry>? {
        val target = editing[player.uniqueId] ?: return null
        val shop = ShopManager.get(target.shopId) ?: return null
        val entry = shop.entry(target.entryId) ?: return null
        return shop to entry
    }

    companion object {
        const val ID = "shop-entry"

        /** Money is only ever shown here, so the currency's own scale is not worth reaching for. */
        private const val MONEY_SCALE = 2

        private const val SLOT_GOODS = 4
        private const val SLOT_BUNDLE = 13
        private const val SLOT_BUY_TOGGLE = 19
        private const val SLOT_BUY_MONEY = 20
        private const val SLOT_BUY_ITEMS = 21
        private const val SLOT_SELL_TOGGLE = 23
        private const val SLOT_SELL_MONEY = 24
        private const val SLOT_SELL_ITEMS = 25
        private const val SLOT_LIMIT = 29
        private const val SLOT_STOCK = 31
        private const val SLOT_PERMISSION = 33
        private const val SLOT_TOWNY = 38
        private const val SLOT_HIDDEN = 40
        private const val SLOT_DISCOUNT = 42
        private const val SLOT_MATCH = 44
        private const val SLOT_BACK = 49

        /** Opens the entry editor through the registered instance. */
        fun show(player: Player, shop: ShopDefinition, entry: ShopEntry): Boolean {
            val gui = GUIManager.getGUI(ID) as? ShopEntryGUI ?: return false
            gui.open(player, shop, entry)
            return true
        }
    }
}
