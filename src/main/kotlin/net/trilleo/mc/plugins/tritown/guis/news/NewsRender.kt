package net.trilleo.mc.plugins.tritown.guis.news

import net.kyori.adventure.inventory.Book
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.trilleo.mc.plugins.tritown.guis.admin.PanelRender
import net.trilleo.mc.plugins.tritown.news.EntryTag
import net.trilleo.mc.plugins.tritown.news.LocalizedText
import net.trilleo.mc.plugins.tritown.news.NewsCategory
import net.trilleo.mc.plugins.tritown.news.NewsEntry
import net.trilleo.mc.plugins.tritown.news.NewsPost
import net.trilleo.mc.plugins.tritown.utils.ChatPrompt
import net.trilleo.mc.plugins.tritown.utils.ComponentUtil
import net.trilleo.mc.plugins.tritown.utils.LoreUtil
import net.trilleo.mc.plugins.tritown.utils.itemStack
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * The pieces the news menus draw with, so a post, a tag or a date reads the
 * same in the reader, the editor and the book.
 *
 * Everything a post says was written by an administrator in MiniMessage and is
 * embedded as written, the way a shop's name is.
 */
object NewsRender {

    /** How many entries a category card lists before pointing at the rest. */
    const val LORE_ENTRIES = 8

    fun text(viewer: Player, text: LocalizedText): String = text.forViewer(viewer)

    /** A stored material name as an item, or [fallback] when it names nothing that can sit in a menu. */
    fun material(name: String, fallback: Material): Material =
        Material.matchMaterial(name)?.takeIf { it.isItem && !it.isAir } ?: fallback

    fun postIcon(post: NewsPost): Material = material(post.icon, Material.WRITABLE_BOOK)

    fun categoryIcon(category: NewsCategory): Material = material(category.icon, Material.BOOK)

    fun tag(viewer: Player, tag: EntryTag): String = when (tag) {
        EntryTag.NEW -> viewer.tr("news.tag.new")
        EntryTag.CHANGED -> viewer.tr("news.tag.changed")
        EntryTag.FIXED -> viewer.tr("news.tag.fixed")
        EntryTag.REMOVED -> viewer.tr("news.tag.removed")
        EntryTag.NOTE -> viewer.tr("news.tag.note")
    }

    fun tagMaterial(tag: EntryTag): Material = when (tag) {
        EntryTag.NEW -> Material.LIME_DYE
        EntryTag.CHANGED -> Material.YELLOW_DYE
        EntryTag.FIXED -> Material.LIGHT_BLUE_DYE
        EntryTag.REMOVED -> Material.RED_DYE
        EntryTag.NOTE -> Material.PAPER
    }

    /** One entry as a single line: its tag, then what it says. */
    fun entryLine(viewer: Player, entry: NewsEntry): String =
        viewer.tr("news.entry", "tag" to tag(viewer, entry.tag), "text" to text(viewer, entry.text))

    fun title(viewer: Player, post: NewsPost): String {
        val title = text(viewer, post.title)
        return if (post.pinned) viewer.tr("gui.news.pinned-title", "title" to title) else viewer.tr("gui.news.title", "title" to title)
    }

    /**
     * What a post is about, for the top of its card: label, dates, author,
     * summary and what each category holds.
     */
    fun postLines(viewer: Player, post: NewsPost): List<String> = buildList {
        post.label?.let { add(viewer.tr("gui.news.label", "label" to it)) }
        post.publishedAt?.let { add(viewer.tr("gui.news.published", "date" to PanelRender.time(it))) }
        post.editedAt?.let { add(viewer.tr("gui.news.edited", "date" to PanelRender.time(it))) }
        post.authorName?.let { add(viewer.tr("gui.news.author", "name" to ComponentUtil.escape(it))) }
        post.summary?.let {
            add("")
            add(viewer.tr("gui.news.summary", "text" to text(viewer, it)))
        }
        val categories = post.categories.filter { it.entries.isNotEmpty() }
        if (categories.isNotEmpty()) {
            add("")
            categories.forEach { category ->
                add(viewer.tr("gui.news.category-count", "name" to text(viewer, category.name), "amount" to category.entries.size))
            }
        }
    }

    fun card(material: Material, name: String, lines: List<String>, glow: Boolean = false): ItemStack =
        PanelRender.card(material, name, lines).also { item ->
            if (glow) item.editMeta { it.setEnchantmentGlintOverride(true) }
        }

    fun button(viewer: Player, material: Material, nameKey: String, loreKey: String): ItemStack =
        itemStack(material) {
            name(viewer.tr(nameKey))
            meta { lore(LoreUtil.wrapLore(viewer.tr(loreKey))) }
        }

    fun glowing(item: ItemStack): ItemStack = item.clone().also { copy ->
        copy.editMeta { it.setEnchantmentGlintOverride(true) }
    }

    /**
     * Closes the menu and asks [player] for a line of text in chat, then
     * [reopen]s whatever they were in.
     *
     * A blank answer changes nothing, and one longer than [maxLength] visible
     * characters is refused with a message rather than cut short, so what was
     * typed is never silently changed.
     */
    fun prompt(player: Player, question: String, maxLength: Int?, reopen: () -> Unit, onInput: (String) -> Unit) {
        player.closeInventory()
        ChatPrompt.ask(player, question) { input ->
            when {
                input.isBlank() -> Unit
                maxLength != null && visibleLength(input) > maxLength ->
                    player.sendPrefixed(player.tr("news.editor.too-long", "amount" to maxLength))

                else -> onInput(input)
            }
            reopen()
        }
    }

    /** How many characters [text] shows once its MiniMessage tags are gone. */
    fun visibleLength(text: String): Int =
        PlainTextComponentSerializer.plainText().serialize(ComponentUtil.parse(text)).length

    /**
     * [post] as a written book: a title page, then each category and its
     * entries, running on to a new page whenever one fills up.
     *
     * A book shows long notes far better than item lore does, and it is opened
     * straight from the post without any item being handed out.
     */
    fun book(viewer: Player, post: NewsPost): Book {
        val pages = mutableListOf<Component>()

        val cover = buildList {
            add(viewer.tr("news.book.title", "title" to text(viewer, post.title)))
            post.label?.let { add(viewer.tr("news.book.label", "label" to it)) }
            post.publishedAt?.let { add(viewer.tr("news.book.date", "date" to PanelRender.time(it))) }
            post.summary?.let { add(""); add(viewer.tr("news.book.summary", "text" to text(viewer, it))) }
        }
        pages += ComponentUtil.parse(cover.joinToString("<newline>"))

        var page = mutableListOf<String>()
        var used = 0
        fun flush() {
            if (page.isNotEmpty()) pages += ComponentUtil.parse(page.joinToString("<newline>"))
            page = mutableListOf()
            used = 0
        }
        fun write(line: String) {
            val height = lineHeight(line)
            if (used + height > BOOK_LINES) flush()
            page += line
            used += height
        }

        post.categories.filter { it.entries.isNotEmpty() }.forEach { category ->
            if (used > 0 && used + 3 > BOOK_LINES) flush() else if (used > 0) write("")
            write(viewer.tr("news.book.category", "name" to text(viewer, category.name)))
            category.entries.forEach { write(viewer.tr("news.book.entry", "tag" to tag(viewer, it.tag), "text" to text(viewer, it.text))) }
        }
        flush()

        return Book.book(
            ComponentUtil.parse(text(viewer, post.title)),
            ComponentUtil.parse(ComponentUtil.escape(post.authorName ?: "")),
            pages,
        )
    }

    /** Roughly how many of a book page's lines [line] takes once it wraps. A CJK character is about two wide. */
    private fun lineHeight(line: String): Int {
        val plain = PlainTextComponentSerializer.plainText().serialize(ComponentUtil.parse(line))
        val width = plain.sumOf { if (it.code >= WIDE_FROM) 2 else 1 }
        return width / BOOK_LINE_CHARS + 1
    }

    private const val BOOK_LINES = 13
    private const val BOOK_LINE_CHARS = 19
    private const val WIDE_FROM = 0x2E80
}
