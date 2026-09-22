package net.trilleo.mc.plugins.tritown.economy

import net.kyori.adventure.text.minimessage.MiniMessage
import net.trilleo.mc.plugins.tritown.utils.Lang
import org.bukkit.command.CommandSender

/**
 * Why a transaction happened, in a form that survives being written to the log.
 *
 * A reason is stored, so it cannot be a translated sentence: the language a
 * server runs in may change, and the log has to stay readable either way. What
 * is stored instead is the translation key, with any arguments appended as
 * `key?name=value&name=value` — still one line, still legible in the raw log,
 * and translatable when the history view shows it.
 *
 * Reasons that TriTown did not write, such as the one attached to a payment
 * another plugin made through Vault, are shown as they were recorded.
 */
object TransactionReason {

    const val EXTERNAL = "money.reason.external"
    const val STARTING_BALANCE = "money.reason.starting-balance"
    const val PAYMENT = "money.reason.payment"
    const val TOWNY = "money.reason.towny"
    const val TOWN_DELETED = "money.reason.town-deleted"
    const val ADMIN_SET = "money.reason.admin-set"
    const val ADMIN_RESET = "money.reason.admin-reset"
    const val SHOP_BUY = "money.reason.shop-buy"
    const val SHOP_SELL = "money.reason.shop-sell"
    const val TRADE = "money.reason.trade"
    const val STORAGE_PAGE = "money.reason.storage-page"

    /** Encodes [key] and its [args] into the single string that is stored with the transaction. */
    fun of(key: String, vararg args: Pair<String, Any?>): String =
        if (args.isEmpty()) key else args.joinToString("&", prefix = "$key?") { "${it.first}=${it.second}" }

    /**
     * The stored [reason] in [viewer]'s language, ready to embed in MiniMessage.
     *
     * Arguments carry town and player names, so they are escaped; so is a reason
     * that no language defines, which is shown as it was recorded.
     */
    fun translate(viewer: CommandSender?, reason: String): String {
        val key = reason.substringBefore('?')
        if (Lang.find(viewer, key) == null) return escape(reason)

        val args = reason.substringAfter('?', "")
            .split('&')
            .filter { it.isNotEmpty() }
            .map { it.substringBefore('=') to escape(it.substringAfter('=', "")) }

        return Lang.tr(viewer, key, *args.toTypedArray())
    }

    private fun escape(text: String): String = MiniMessage.miniMessage().escapeTags(text)
}
