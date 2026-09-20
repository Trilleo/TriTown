package net.trilleo.mc.plugins.tritown.guis.trade

import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.trilleo.mc.plugins.tritown.economy.CurrencyRegistry
import net.trilleo.mc.plugins.tritown.economy.Money
import net.trilleo.mc.plugins.tritown.guis.trade.TradeRender.STEP
import net.trilleo.mc.plugins.tritown.trades.TradeManager
import net.trilleo.mc.plugins.tritown.trades.TradeParty
import net.trilleo.mc.plugins.tritown.trades.TradeSession
import net.trilleo.mc.plugins.tritown.utils.*
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta

/**
 * The pieces the trade menu is drawn with, and the money arithmetic behind its
 * buttons.
 *
 * Both players are looking at the same trade from opposite sides, so every item
 * here is built for a viewer rather than for a side: what is "yours" in one
 * window is "theirs" in the other, and only the viewer decides which wording
 * and which language it is written in.
 */
object TradeRender {

    /** What one click puts on or takes off the table, in whole units of the currency. */
    private const val STEP = 100.0

    /** How much a shift-click multiplies [STEP] by. */
    private const val BULK = 10

    /** Whether money can be part of a trade at all right now. */
    val isMoneyAvailable: Boolean
        get() = EconomyUtil.isAvailable

    /** What one click adds or removes. */
    fun step(bulk: Boolean): Money = of(if (bulk) STEP * BULK else STEP)

    /** [amount] in the currency's minor units, at the scale the economy is keeping balances in. */
    fun of(amount: Double): Money =
        if (CurrencyRegistry.isLoaded) CurrencyRegistry.primary.of(amount) else Money.ofDouble(amount, 2)

    /** [money] back as the `Double` the Vault economy speaks in. */
    fun toDouble(money: Money): Double =
        if (CurrencyRegistry.isLoaded) CurrencyRegistry.primary.toDouble(money) else money.toDouble(2)

    /** What [player] can actually put up, which is every button's ceiling. */
    fun balanceOf(player: Player): Money =
        if (isMoneyAvailable) of(EconomyUtil.balance(player)) else Money.ZERO

    /** [money] written out with the server's currency, escaped for embedding in MiniMessage. */
    fun money(money: Money): String =
        if (isMoneyAvailable) ComponentUtil.escape(EconomyUtil.format(toDouble(money))) else toDouble(money).toString()

    // ── The menu's own items ────────────────────────────────────────────

    /** The other player, so it is never in doubt whose window this is. */
    fun partner(viewer: Player, them: TradeParty): ItemStack {
        val name = TradeManager.name(them)
        return itemStack(Material.PLAYER_HEAD) {
            name(viewer.tr("gui.trade.partner", "name" to name))
            meta {
                lore(LoreUtil.wrapLore(viewer.tr("gui.trade.partner-lore")))
                (this as? SkullMeta)?.owningPlayer = them.player
            }
        }
    }

    /** What the viewer has put up, and how to change it. */
    fun yourMoney(viewer: Player, you: TradeParty): ItemStack {
        if (!isMoneyAvailable) return noMoney(viewer)

        val lines = listOf(
            viewer.tr("gui.trade.money-amount", "amount" to money(you.offer.money)),
            viewer.tr("gui.trade.money-balance", "amount" to money(balanceOf(viewer))),
            "",
            viewer.tr("gui.trade.money-add", "amount" to money(step(bulk = false))),
            viewer.tr("gui.trade.money-remove", "amount" to money(step(bulk = false))),
            viewer.tr("gui.trade.money-bulk", "amount" to money(step(bulk = true))),
            viewer.tr("gui.trade.money-exact"),
        )

        return itemStack(Material.GOLD_INGOT) {
            name(viewer.tr("gui.trade.money-yours"))
            meta { lore(LoreUtil.wrapLore(lines.joinToString("<newline>"))) }
        }
    }

    /** What the other player has put up. */
    fun theirMoney(viewer: Player, them: TradeParty): ItemStack {
        if (!isMoneyAvailable) return noMoney(viewer)

        return itemStack(Material.GOLD_NUGGET) {
            name(viewer.tr("gui.trade.money-theirs", "name" to TradeManager.name(them)))
            meta {
                lore(
                    LoreUtil.wrapLore(
                        viewer.tr("gui.trade.money-amount", "amount" to money(them.offer.money))
                    )
                )
            }
        }
    }

    /** The viewer's own confirmation, which their click turns on and off. */
    fun yourConfirm(viewer: Player, you: TradeParty): ItemStack {
        val material = if (you.confirmed) Material.LIME_CONCRETE else Material.GRAY_CONCRETE
        val nameKey = if (you.confirmed) "gui.trade.confirmed" else "gui.trade.confirm"
        val loreKey = if (you.confirmed) "gui.trade.confirmed-lore" else "gui.trade.confirm-lore"

        return itemStack(material) {
            name(viewer.tr(nameKey))
            meta { lore(LoreUtil.wrapLore(viewer.tr(loreKey))) }
        }
    }

    /** Where the other player has got to, including having stepped out to chat to name an amount. */
    fun theirConfirm(viewer: Player, them: TradeParty): ItemStack {
        val name = TradeManager.name(them)
        val material = when {
            them.isPrompting -> Material.ORANGE_CONCRETE
            them.confirmed -> Material.LIME_CONCRETE
            else -> Material.GRAY_CONCRETE
        }
        val key = when {
            them.isPrompting -> "gui.trade.their-away"
            them.confirmed -> "gui.trade.their-confirmed"
            else -> "gui.trade.their-confirm"
        }

        return itemStack(material) {
            name(viewer.tr(key, "name" to name))
        }
    }

    /** What the menu expects of the viewer, and what has just changed under them. */
    fun status(viewer: Player, session: TradeSession, now: Long): ItemStack {
        val lines = buildList {
            add(viewer.tr("gui.trade.status-add"))
            add(viewer.tr("gui.trade.status-take"))
            add(viewer.tr("gui.trade.status-both"))
            add("")
            add(viewer.tr("gui.trade.status-close"))
            if (session.isLocked(now)) {
                add("")
                add(viewer.tr("gui.trade.status-locked"))
            }
        }

        return itemStack(Material.PAPER) {
            name(viewer.tr("gui.trade.status"))
            meta { lore(LoreUtil.wrapLore(lines.joinToString("<newline>"))) }
        }
    }

    // ── Feedback ────────────────────────────────────────────────────────

    /** Tells [player] why a click did nothing, in the plugin's own error colours. */
    fun refuse(player: Player, key: String, vararg args: Pair<String, Any?>) {
        player.sendPrefixed(player.tr("common.error", "message" to player.tr(key, *args)))
        player.playSound(sound("minecraft:entity.villager.no"))
    }

    /** The click that means something changed on the table. */
    fun click(player: Player) {
        player.playSound(sound("minecraft:ui.button.click"))
    }

    private fun noMoney(viewer: Player): ItemStack = itemStack(Material.BARRIER) {
        name(viewer.tr("gui.trade.money-none"))
    }

    private fun sound(key: String): Sound = Sound.sound(Key.key(key), Sound.Source.UI, 1f, 1f)
}
