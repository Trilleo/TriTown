package net.trilleo.mc.plugins.tritown.guis

import net.kyori.adventure.text.Component
import net.trilleo.mc.plugins.tritown.enums.FillMode
import net.trilleo.mc.plugins.tritown.guis.menu.MenuRender
import net.trilleo.mc.plugins.tritown.registration.GUIFrame
import net.trilleo.mc.plugins.tritown.registration.GUIManager
import net.trilleo.mc.plugins.tritown.registration.PluginGUI
import net.trilleo.mc.plugins.tritown.utils.ComponentUtil
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
 * Asks before something that cannot be undone.
 *
 * What is at stake sits at the top, and the answers along the row beneath it:
 * the caller's own choices, then Cancel. Every answer runs on the next tick,
 * so it may open another menu. Closing the menu answers nothing.
 *
 * ```kotlin
 * ConfirmGUI.show(
 *     player,
 *     title = player.tr("gui.news-manage.delete-title"),
 *     subject = card,
 *     choices = listOf(ConfirmGUI.Choice(deleteButton) { NewsManager.delete(post) }),
 *     onCancel = { NewsManageGUI.show(it) },
 * )
 * ```
 */
class ConfirmGUI : PluginGUI(
    id = ID,
    titleKey = "gui.confirm.title",
    rows = 6,
    fillMode = FillMode.NONE,
) {

    /** An answer: the button that gives it, and what happens when it is clicked. */
    class Choice(val item: ItemStack, val action: (Player) -> Unit)

    private class Question(
        val title: String?,
        val subject: ItemStack,
        val choices: List<Choice>,
        val onCancel: (Player) -> Unit,
    )

    private val questions = ConcurrentHashMap<UUID, Question>()

    override fun title(player: Player): Component =
        questions[player.uniqueId]?.title?.let(ComponentUtil::parse) ?: super.title(player)

    override fun setup(player: Player, inventory: Inventory) {
        val question = questions[player.uniqueId] ?: return
        val slots = answerSlots(question)

        GUIFrame.draw(inventory, slots.keys + SUBJECT_SLOT)
        inventory.setItem(SUBJECT_SLOT, question.subject)
        slots.forEach { (slot, choice) -> inventory.setItem(slot, choice?.item ?: cancel(player)) }
    }

    override fun onClick(event: InventoryClickEvent) {
        event.isCancelled = true
        if (event.clickedInventory !== event.view.topInventory) return

        val player = event.whoClicked as? Player ?: return
        val question = questions[player.uniqueId] ?: return
        val slots = answerSlots(question)
        if (event.rawSlot !in slots) return

        // Taken before acting, so a double click cannot answer twice.
        questions.remove(player.uniqueId)
        val choice = slots[event.rawSlot]
        MenuRender.later(player) { if (choice != null) choice.action(player) else question.onCancel(player) }
    }

    override fun onClose(event: InventoryCloseEvent) {
        questions.remove(event.player.uniqueId)
    }

    /** Where each answer goes; `null` is Cancel, which always comes last. */
    private fun answerSlots(question: Question): Map<Int, Choice?> {
        val answers: List<Choice?> = question.choices + null
        return GUIFrame.spacedColumns(answers.size).zip(answers).associate { (column, choice) ->
            ANSWER_ROW * ROW_SIZE + column to choice
        }
    }

    private fun cancel(player: Player): ItemStack = itemStack(Material.RED_CONCRETE) {
        name(player.tr("gui.confirm.cancel"))
        meta { lore(LoreUtil.wrapLore(player.tr("gui.confirm.cancel-lore"))) }
    }

    companion object {
        const val ID = "confirm"

        private const val ROW_SIZE = 9
        private const val SUBJECT_SLOT = 13
        private const val ANSWER_ROW = 3

        /**
         * Asks [player] to pick one of [choices] — at most three, since Cancel
         * takes the fourth place in the row — about [subject].
         *
         * @param title the menu's title, already translated, or `null` for the generic one
         */
        fun show(
            player: Player,
            title: String?,
            subject: ItemStack,
            choices: List<Choice>,
            onCancel: (Player) -> Unit,
        ): Boolean {
            val gui = GUIManager.getGUI(ID) as? ConfirmGUI ?: return false
            require(choices.size in 1..3) { "A confirmation offers one to three choices besides Cancel" }
            gui.questions[player.uniqueId] = Question(title, subject, choices, onCancel)
            return GUIManager.open(player, ID)
        }
    }
}
