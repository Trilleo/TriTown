package net.trilleo.mc.plugins.tritown.enums

/**
 * Which way goods are moving in a shop trade.
 *
 * Buying and selling are limited independently, so the side is what a per-player
 * counter is filed under as well as what a trade is.
 */
enum class TradeSide {

    /** The player is buying from the shop. */
    BUY,

    /** The shop is buying back from the player. */
    SELL,
}
