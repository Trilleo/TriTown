package net.trilleo.mc.plugins.tritown.guis.shop

import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.trilleo.mc.plugins.tritown.Main
import net.trilleo.mc.plugins.tritown.enums.LimitPeriod
import net.trilleo.mc.plugins.tritown.enums.MatchMode
import net.trilleo.mc.plugins.tritown.enums.TownyRequirement
import net.trilleo.mc.plugins.tritown.shops.ShopCost
import net.trilleo.mc.plugins.tritown.shops.ShopEntry
import net.trilleo.mc.plugins.tritown.shops.ShopTrade
import net.trilleo.mc.plugins.tritown.utils.ComponentUtil
import net.trilleo.mc.plugins.tritown.utils.EconomyUtil
import net.trilleo.mc.plugins.tritown.utils.LoreUtil
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * The pieces every shop menu draws with.
 *
 * Kept in one place because the player's menu, the editor and the statistics
 * view all describe the same things — a price, a stock level, a requirement —
 * and they should read identically wherever they appear.
 */
object ShopRender {

    private val plain = PlainTextComponentSerializer.plainText()

    /**
     * Opens the next menu on the following tick.
     *
     * A click is still being delivered while its handler runs, and opening an
     * inventory from inside that delivery leaves the server and the client
     * disagreeing about what is on screen.
     */
    fun navigate(open: () -> Unit) {
        Bukkit.getScheduler().runTask(Main.instance, Runnable { open() })
    }

    /**
     * What to call [item] in a line of text.
     *
     * A renamed item is called by the name it was given, escaped because an
     * administrator wrote it. Anything else uses the client's own translation,
     * so a Chinese player reads Chinese item names without TriTown shipping a
     * copy of Minecraft's dictionary.
     */
    fun itemName(item: ItemStack): String {
        val meta = item.itemMeta
        val custom: Component? = if (meta != null && meta.hasDisplayName()) meta.displayName() else null
        return custom?.let { ComponentUtil.escape(plain.serialize(it)) } ?: "<lang:${item.translationKey()}>"
    }

    /**
     * `3x Diamond`, for one line of a price or payout.
     *
     * [key] chooses the wording, so the same line can be coloured as something
     * the player hands over or as something they are given.
     */
    fun itemLine(player: Player, item: ItemStack, multiplier: Int = 1, key: String = "gui.shop.cost-item"): String =
        player.tr(key, "amount" to item.amount * multiplier, "item" to itemName(item))

    /**
     * What [amount] of [entry] costs, money first, coloured as something the
     * player hands over.
     *
     * Shared by the shelf and the amount menu so a price reads the same in
     * both, and taken from a quote rather than multiplied here, because what a
     * discount does to it is the quote's business.
     */
    fun buyLines(
        player: Player,
        entry: ShopEntry,
        amount: Int,
        standing: Set<TownyRequirement>,
    ): List<String> {
        val cost = entry.buy ?: return emptyList()
        val quote = ShopTrade.quoteBuy(player, entry, amount, standing) ?: return emptyList()
        val lines = mutableListOf<String>()

        if (quote.isDiscounted) {
            lines += player.tr(
                "gui.shop.buy-discounted",
                "price" to money(quote.money),
                "full" to money(quote.fullMoney),
            )
        } else if (cost.hasMoney) {
            lines += player.tr("gui.shop.buy", "price" to money(quote.money))
        }

        cost.items.forEach { lines += itemLine(player, it, amount / entry.bundleSize, "gui.shop.buy-item") }
        return lines
    }

    /** What [entry] pays out for [amount], coloured as something the player is given. */
    fun sellLines(player: Player, entry: ShopEntry, amount: Int): List<String> {
        val payout = entry.sell ?: return emptyList()
        val quote = ShopTrade.quoteSell(entry, amount) ?: return emptyList()
        val lines = mutableListOf<String>()

        if (payout.hasMoney) lines += player.tr("gui.shop.sell", "price" to money(quote.money))

        payout.items.forEach { lines += itemLine(player, it, amount / entry.bundleSize, "gui.shop.sell-item") }
        return lines
    }

    /**
     * [blocks] run together into one lore, a blank line between each pair.
     *
     * Empty blocks are dropped rather than spaced, so an entry that is only for
     * sale does not carry a gap where its payout would have been.
     */
    fun sections(blocks: List<List<String>>): List<String> =
        blocks.filter { it.isNotEmpty() }.reduceOrNull { left, right -> left + "" + right } ?: emptyList()

    /** Every line a [cost] needs, money first, or an empty list when it asks for nothing. */
    fun costLines(player: Player, cost: ShopCost?, multiplier: Int = 1): List<String> {
        if (cost == null || cost.isFree) return emptyList()

        val lines = mutableListOf<String>()
        if (cost.hasMoney) lines += money(cost.money * multiplier)
        cost.items.forEach { lines += itemLine(player, it, multiplier) }
        return lines
    }

    /** [amount] as the server's currency, or a bare number when no economy is available. */
    fun money(amount: Double): String =
        if (EconomyUtil.isAvailable) ComponentUtil.escape(EconomyUtil.format(amount)) else amount.toString()

    /** A Towny requirement's name in [player]'s language. */
    fun requirementName(player: Player, requirement: TownyRequirement): String = when (requirement) {
        TownyRequirement.NONE -> player.tr("gui.shop.requirement-none")
        TownyRequirement.HAS_TOWN -> player.tr("gui.shop.requirement-has-town")
        TownyRequirement.NO_TOWN -> player.tr("gui.shop.requirement-no-town")
        TownyRequirement.HAS_NATION -> player.tr("gui.shop.requirement-has-nation")
        TownyRequirement.IS_MAYOR -> player.tr("gui.shop.requirement-is-mayor")
        TownyRequirement.IS_KING -> player.tr("gui.shop.requirement-is-king")
    }

    /** A limit period's name in [player]'s language. */
    fun periodName(player: Player, period: LimitPeriod): String = when (period) {
        LimitPeriod.NONE -> player.tr("gui.shop.period-none")
        LimitPeriod.DAILY -> player.tr("gui.shop.period-daily")
        LimitPeriod.WEEKLY -> player.tr("gui.shop.period-weekly")
    }

    /** A match mode's name in [player]'s language. */
    fun matchName(player: Player, mode: MatchMode): String = when (mode) {
        MatchMode.EXACT -> player.tr("gui.shop.match-exact")
        MatchMode.MATERIAL -> player.tr("gui.shop.match-material")
    }

    /**
     * Tells [player] a trade went through, in the words the amount deserves.
     *
     * Every menu that can start a trade says the same thing afterwards, so this
     * lives here rather than being written out again in each of them.
     */
    fun announce(player: Player, entry: ShopEntry, result: ShopTrade.Result.Success) {
        val key = if (result.money > 0.0 || entry.buy?.hasMoney == true) "shop.traded" else "shop.traded-items"

        player.sendPrefixed(
            player.tr(
                key,
                "amount" to result.amount,
                "item" to itemName(entry.item),
                "price" to money(result.money),
            )
        )
        player.playSound(Sound.sound(Key.key("minecraft:entity.villager.yes"), Sound.Source.UI, 1f, 1f))
    }

    /** Tells [player] why a trade was refused, colouring the reason as this plugin's errors are. */
    fun refuse(player: Player, failure: ShopTrade.Result.Failure) {
        val reason = player.tr(failure.key, *failure.args.toTypedArray())

        player.sendPrefixed(player.tr("common.error", "message" to reason))
        player.playSound(Sound.sound(Key.key("minecraft:entity.villager.no"), Sound.Source.UI, 1f, 1f))
    }

    /**
     * A copy of [item] that glints, for the one thing a menu is asking about.
     *
     * The glint is overridden rather than an enchantment added, because the
     * goods are drawn exactly as they are sold and an enchantment on the icon
     * would misdescribe what is on the shelf.
     */
    fun glowing(item: ItemStack): ItemStack = item.clone().apply {
        val meta = itemMeta ?: return@apply
        meta.setEnchantmentGlintOverride(true)
        itemMeta = meta
    }

    /**
     * A copy of [item] called [name] and described by [lines].
     *
     * The name is reset and un-italicised the way the item DSL does it, because
     * a display name written straight onto an item comes out in Minecraft's own
     * italics and reads as a rename rather than a label.
     */
    fun named(item: ItemStack, name: String, lines: List<String>): ItemStack {
        val copy = withLore(item, lines)
        val meta = copy.itemMeta ?: return copy

        meta.displayName(ComponentUtil.parse("<reset><i:false>$name"))
        copy.itemMeta = meta
        return copy
    }

    /**
     * A copy of [item] with [lines] added under whatever lore it already has.
     *
     * The goods keep their own description, because an item that says what it
     * does should still say it on the shelf.
     */
    fun withLore(item: ItemStack, lines: List<String>): ItemStack {
        if (lines.isEmpty()) return item.clone()

        val copy = item.clone()
        val meta = copy.itemMeta ?: return copy
        val existing = meta.lore().orEmpty()
        val added = LoreUtil.wrapLore(lines.joinToString("<newline>"))

        meta.lore(if (existing.isEmpty()) added else existing + Component.empty() + added)
        copy.itemMeta = meta
        return copy
    }
}
