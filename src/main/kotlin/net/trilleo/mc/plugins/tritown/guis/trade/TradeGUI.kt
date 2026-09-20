package net.trilleo.mc.plugins.tritown.guis.trade

import net.kyori.adventure.text.Component
import net.trilleo.mc.plugins.tritown.economy.Money
import net.trilleo.mc.plugins.tritown.enums.FillMode
import net.trilleo.mc.plugins.tritown.registration.GUIFrame
import net.trilleo.mc.plugins.tritown.registration.GUIManager
import net.trilleo.mc.plugins.tritown.registration.PluginGUI
import net.trilleo.mc.plugins.tritown.trades.TradeExchange
import net.trilleo.mc.plugins.tritown.trades.TradeManager
import net.trilleo.mc.plugins.tritown.trades.TradeOffer
import net.trilleo.mc.plugins.tritown.trades.TradeParty
import net.trilleo.mc.plugins.tritown.trades.TradeSession
import net.trilleo.mc.plugins.tritown.utils.ChatPrompt
import net.trilleo.mc.plugins.tritown.utils.ComponentUtil
import net.trilleo.mc.plugins.tritown.utils.InventoryUtil
import net.trilleo.mc.plugins.tritown.utils.LoreUtil
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory

/**
 * The table two players trade across.
 *
 * Both of them are looking at the same trade through a window of their own, so
 * every side of the menu is drawn for its viewer: the left half is always the
 * items you put up and the right half always the other player's, whichever end
 * of the trade you are at. Anything either of them changes redraws both
 * windows, which is what keeps the two from ever showing different tables.
 *
 * Items are held by the trade rather than by the player from the moment they
 * are put up, so what the other side is looking at cannot be spent or dropped
 * behind their back. Closing the menu calls the trade off and hands everything
 * back; the one exception is stepping out to chat to name an exact amount,
 * which the other side is shown instead of a confirmation.
 */
class TradeGUI : PluginGUI(
    id = ID,
    titleKey = "gui.trade.title",
    rows = ROWS,
    fillMode = FillMode.NONE,
) {

    override fun title(player: Player): Component {
        val session = TradeManager.sessionOf(player.uniqueId)
        val you = session?.partyOf(player.uniqueId)
        val name = if (session != null && you != null) TradeManager.name(session.other(you)) else ""
        return ComponentUtil.parse(player.tr("gui.trade.title", "name" to name))
    }

    override fun setup(player: Player, inventory: Inventory) {
        val session = TradeManager.sessionOf(player.uniqueId) ?: return
        val you = session.partyOf(player.uniqueId) ?: return
        val them = session.other(you)

        inventory.clear()
        GUIFrame.draw(inventory, ITEM_SLOTS)

        val takeBack = listOf(player.tr("gui.trade.take-back"))
        you.offer.items.forEachIndexed { index, item ->
            YOURS.getOrNull(index)?.let { inventory.setItem(it, LoreUtil.withLore(item, takeBack)) }
        }

        val offered = listOf(player.tr("gui.trade.their-item", "name" to TradeManager.name(them)))
        them.offer.items.forEachIndexed { index, item ->
            THEIRS.getOrNull(index)?.let { inventory.setItem(it, LoreUtil.withLore(item, offered)) }
        }

        inventory.setItem(SLOT_PARTNER, TradeRender.partner(player, them))
        inventory.setItem(SLOT_MONEY_YOURS, TradeRender.yourMoney(player, you))
        inventory.setItem(SLOT_MONEY_THEIRS, TradeRender.theirMoney(player, them))
        inventory.setItem(SLOT_CONFIRM_YOURS, TradeRender.yourConfirm(player, you))
        inventory.setItem(SLOT_CONFIRM_THEIRS, TradeRender.theirConfirm(player, them))
        inventory.setItem(SLOT_STATUS, TradeRender.status(player, session, System.currentTimeMillis()))
    }

    /**
     * Nothing is ever moved by the client here.
     *
     * Every click is cancelled and then carried out by hand, because the only
     * way the offer and the player's own inventory can be kept adding up is for
     * one piece of code to move both.
     */
    override fun onClick(event: InventoryClickEvent) {
        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        val session = TradeManager.sessionOf(player.uniqueId) ?: return
        val you = session.partyOf(player.uniqueId) ?: return

        if (event.clickedInventory === player.inventory) {
            offer(player, session, you, event)
            return
        }
        if (event.clickedInventory !== event.view.topInventory) return

        when (event.rawSlot) {
            SLOT_MONEY_YOURS -> money(player, session, you, event.click)
            SLOT_CONFIRM_YOURS -> confirm(player, session, you)
            else -> YOURS.indexOf(event.rawSlot).takeIf { it >= 0 }?.let { takeBack(player, session, you, it) }
        }
    }

    override fun onClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        val session = TradeManager.sessionOf(player.uniqueId) ?: return
        val you = session.partyOf(player.uniqueId) ?: return

        // Stepping out to chat to name an amount closes the menu too, and that
        // is the one close that does not mean the trade is off.
        if (you.isPrompting) return

        TradeManager.cancel(session, "trade.cancelled.closed")
    }

    // ── Items ───────────────────────────────────────────────────────────

    /** Moves what was clicked in the player's own inventory onto the table. */
    private fun offer(player: Player, session: TradeSession, you: TradeParty, event: InventoryClickEvent) {
        val clicked = event.currentItem ?: return
        if (clicked.type.isAir) return

        val wanted = if (event.click == ClickType.RIGHT) 1 else clicked.amount
        val accepted = you.offer.add(clicked.clone().apply { amount = wanted })
        if (accepted <= 0) {
            TradeRender.refuse(player, "trade.error.offer-full", "amount" to TradeOffer.MAX_STACKS)
            return
        }

        val left = clicked.amount - accepted
        event.clickedInventory?.setItem(event.slot, if (left > 0) clicked.clone().apply { amount = left } else null)

        TradeRender.click(player)
        changed(session)
    }

    /** Takes the stack at [index] of the player's own half back off the table. */
    private fun takeBack(player: Player, session: TradeSession, you: TradeParty, index: Int) {
        val stack = you.offer.itemAt(index) ?: return
        if (!InventoryUtil.hasSpaceFor(player, listOf(stack))) {
            TradeRender.refuse(player, "trade.error.no-room")
            return
        }

        you.offer.takeAt(index)?.let { InventoryUtil.give(player, listOf(it)) }
        TradeRender.click(player)
        changed(session)
    }

    // ── Money ───────────────────────────────────────────────────────────

    /** Puts money on or takes it off, or steps out to chat for an exact amount. */
    private fun money(player: Player, session: TradeSession, you: TradeParty, click: ClickType) {
        if (!TradeRender.isMoneyAvailable) {
            TradeRender.refuse(player, "trade.error.economy-unavailable")
            return
        }

        if (click == ClickType.DROP || click == ClickType.CONTROL_DROP) {
            promptMoney(player, session, you)
            return
        }

        val bulk = click.isShiftClick
        val step = TradeRender.step(bulk)
        val wanted = when (click) {
            ClickType.LEFT, ClickType.SHIFT_LEFT -> you.offer.money.plusExact(step)
            ClickType.RIGHT, ClickType.SHIFT_RIGHT -> you.offer.money.minusExact(step)
            else -> return
        }

        val capped = cap(player, wanted)
        if (capped == you.offer.money) {
            // Asking for more than there is has to be said; asking for less than
            // nothing is just the button running out of room.
            if (wanted > capped) TradeRender.refuse(player, "trade.error.cannot-afford")
            return
        }

        you.offer.setMoney(capped)
        TradeRender.click(player)
        changed(session)
    }

    /** Sends the player to chat for an exact amount, leaving the trade standing while they type. */
    private fun promptMoney(player: Player, session: TradeSession, you: TradeParty) {
        you.beginPrompt(System.currentTimeMillis())
        session.touch(System.currentTimeMillis())
        redraw(session)

        player.closeInventory()
        ChatPrompt.ask(
            player,
            player.tr("trade.money-prompt", "amount" to TradeRender.money(TradeRender.balanceOf(player))),
        ) { input -> answerMoney(player, input) }
    }

    /** Reads what the player typed and puts them back at the menu, whatever it was. */
    private fun answerMoney(player: Player, input: String) {
        val session = TradeManager.sessionOf(player.uniqueId) ?: return
        val you = session.partyOf(player.uniqueId) ?: return
        you.endPrompt()

        val typed = input.toDoubleOrNull()
        val wanted = typed?.takeIf { it.isFinite() && it >= 0.0 }?.let { runCatching { TradeRender.of(it) }.getOrNull() }

        if (wanted == null) {
            player.sendPrefixed(player.tr("common.invalid-amount"))
        } else {
            val capped = cap(player, wanted)
            you.offer.setMoney(capped)
            if (capped < wanted) {
                player.sendPrefixed(player.tr("trade.money-capped", "amount" to TradeRender.money(capped)))
            }
        }

        session.touch(System.currentTimeMillis())
        show(player)
        redraw(session)
    }

    /** [wanted] brought inside what the player actually has, and never below nothing. */
    private fun cap(player: Player, wanted: Money): Money = when {
        wanted.isNegative -> Money.ZERO
        else -> minOf(wanted, TradeRender.balanceOf(player))
    }

    // ── Confirming ──────────────────────────────────────────────────────

    /** Agrees to the trade as it stands, or takes that agreement back. */
    private fun confirm(player: Player, session: TradeSession, you: TradeParty) {
        if (you.confirmed) {
            you.confirmed = false
            TradeRender.click(player)
            redraw(session)
            return
        }

        val now = System.currentTimeMillis()
        if (session.isLocked(now)) {
            TradeRender.refuse(player, "trade.error.locked")
            return
        }
        if (!you.offer.money.isZero && TradeRender.balanceOf(player) < you.offer.money) {
            TradeRender.refuse(player, "trade.error.cannot-afford")
            return
        }

        you.confirmed = true
        TradeRender.click(player)
        redraw(session)

        if (session.bothConfirmed) settle(session)
    }

    /**
     * Carries the trade out, now that both sides have agreed to it.
     *
     * A refusal drops both confirmations rather than ending the trade: what
     * went wrong is usually something the players can put right — making room,
     * or asking for less — without starting over.
     */
    private fun settle(session: TradeSession) {
        when (val result = TradeExchange.execute(session)) {
            is TradeExchange.Result.Success -> TradeManager.finish(session)

            is TradeExchange.Result.Failure -> {
                session.touch(System.currentTimeMillis())
                session.parties.forEach { party ->
                    party.player?.let { TradeRender.refuse(it, result.key, "name" to result.name) }
                }
                redraw(session)
            }
        }
    }

    // ── Keeping both windows the same ───────────────────────────────────

    /** Records a change to the table and redraws both windows. */
    private fun changed(session: TradeSession) {
        session.touch(System.currentTimeMillis())
        redraw(session)
    }

    companion object {

        const val ID = "trade"

        private const val ROWS = 6

        /** The viewer's own half: the left four columns of the top four rows. */
        private val YOURS = (0..3).flatMap { row -> (0..3).map { row * 9 + it } }

        /** The other player's half, mirrored across the divider. */
        private val THEIRS = (0..3).flatMap { row -> (5..8).map { row * 9 + it } }

        private val ITEM_SLOTS = YOURS + THEIRS

        private const val SLOT_MONEY_YOURS = 38
        private const val SLOT_PARTNER = 40
        private const val SLOT_MONEY_THEIRS = 42
        private const val SLOT_CONFIRM_YOURS = 48
        private const val SLOT_STATUS = 49
        private const val SLOT_CONFIRM_THEIRS = 50

        init {
            // Each half of the menu is exactly as big as an offer, so nothing a
            // player puts up can be held by the trade without being drawn.
            require(YOURS.size == TradeOffer.MAX_STACKS) { "A trade's half of the menu must fit a whole offer" }
        }

        /** Opens the trade [player] is in. */
        fun show(player: Player) {
            GUIManager.open(player, ID)
        }

        /**
         * Redraws whichever of [session]'s two windows is actually open.
         *
         * Both players are looking at the same trade, so anything that changes
         * it — a click in either menu, or the watchdog bringing someone back
         * from chat — redraws both rather than only the window it happened in.
         */
        fun redraw(session: TradeSession) {
            for (party in session.parties) {
                val player = party.player ?: continue
                if (GUIManager.openGUI(player)?.id == ID) GUIManager.refresh(player)
            }
        }
    }
}
