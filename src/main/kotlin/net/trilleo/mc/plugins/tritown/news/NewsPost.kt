package net.trilleo.mc.plugins.tritown.news

import net.trilleo.mc.plugins.tritown.utils.Lang
import org.bukkit.command.CommandSender
import java.security.SecureRandom

/**
 * One update note: a titled post made of categories, each holding short entries.
 *
 * The classes here are what the news file stores, field for field, so none of
 * them names a Bukkit type — an icon is a material's name, read back through
 * [net.trilleo.mc.plugins.tritown.guis.news.NewsRender.material]. Every field
 * has a default, which is what lets Gson fill in whatever an older file lacks.
 *
 * Everything written here is an administrator's MiniMessage, not a translation
 * key; [LocalizedText] carries the other languages.
 */
class NewsPost(
    var id: String = "",
    var title: LocalizedText = LocalizedText(),
    var summary: LocalizedText? = null,
    /** A version or season the post belongs to, such as `v1.3`. One language only: it is a name, not a sentence. */
    var label: String? = null,
    var icon: String = DEFAULT_ICON,
    var categories: MutableList<NewsCategory> = mutableListOf(),
    var status: PostStatus = PostStatus.DRAFT,
    var pinned: Boolean = false,
    var createdAt: Long = 0L,
    var publishedAt: Long? = null,
    var editedAt: Long? = null,
    var authorUuid: String? = null,
    var authorName: String? = null,
) {

    val isPublished: Boolean
        get() = status == PostStatus.PUBLISHED

    /** How many entries the post holds across every category. */
    val entryCount: Int
        get() = categories.sumOf { it.entries.size }

    fun category(id: String): NewsCategory? = categories.firstOrNull { it.id == id }

    /**
     * Puts back what a hand-edited or damaged file left null.
     *
     * Gson writes a JSON `null`, or an enum name this build does not know, into
     * a non-null Kotlin field without complaint, so the file is checked here
     * once rather than at every place that reads it.
     */
    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    fun repair() {
        title = title ?: LocalizedText()
        title.repair()
        summary?.repair()
        icon = icon ?: DEFAULT_ICON
        categories = (categories ?: mutableListOf()).filterNotNull().toMutableList()
        status = status ?: PostStatus.DRAFT
        categories.forEach { it.repair() }
    }

    companion object {
        const val DEFAULT_ICON = "WRITABLE_BOOK"
    }
}

class NewsCategory(
    var id: String = "",
    var name: LocalizedText = LocalizedText(),
    var icon: String = DEFAULT_ICON,
    var entries: MutableList<NewsEntry> = mutableListOf(),
) {

    fun entry(id: String): NewsEntry? = entries.firstOrNull { it.id == id }

    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    internal fun repair() {
        name = name ?: LocalizedText()
        name.repair()
        icon = icon ?: DEFAULT_ICON
        entries = (entries ?: mutableListOf()).filterNotNull().toMutableList()
        entries.forEach { it.repair() }
    }

    companion object {
        const val DEFAULT_ICON = "BOOK"
    }
}

/** One change, in a sentence or two. */
class NewsEntry(
    var id: String = "",
    var tag: EntryTag = EntryTag.NEW,
    var text: LocalizedText = LocalizedText(),
) {

    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    internal fun repair() {
        tag = tag ?: EntryTag.NOTE
        text = text ?: LocalizedText()
        text.repair()
    }
}

/** What kind of change an entry describes. The label and colour of each live in the language files. */
enum class EntryTag { NEW, CHANGED, FIXED, REMOVED, NOTE }

enum class PostStatus { DRAFT, PUBLISHED }

/**
 * Text an administrator wrote, with an optional version for each language.
 *
 * [main] is what everyone sees unless their language has a translation of its
 * own, so a post written in one language still reads for every player.
 * Translations are keyed by language id, such as `zh_CN`, and matched without
 * regard to case, the way [net.trilleo.mc.plugins.tritown.utils.Lang] matches
 * a language file.
 */
class LocalizedText(
    var main: String = "",
    var translations: MutableMap<String, String> = mutableMapOf(),
) {

    /** The text for [languageId], falling back to [main]. */
    fun get(languageId: String?): String = languageId?.let(::translation) ?: main

    /** The text in the language [viewer] reads. */
    fun forViewer(viewer: CommandSender?): String = get(Lang.idFor(viewer))

    /** The translation for [languageId] alone, or `null` when there is none. */
    fun translation(languageId: String): String? =
        translations.entries.firstOrNull { it.key.equals(languageId, ignoreCase = true) }?.value

    fun translate(languageId: String, text: String) {
        clear(languageId)
        translations[languageId] = text
    }

    fun clear(languageId: String) {
        translations.keys.removeIf { it.equals(languageId, ignoreCase = true) }
    }

    @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
    internal fun repair() {
        main = main ?: ""
        translations = (translations ?: mutableMapOf()).filterValues { it != null }.toMutableMap()
    }
}

/** Short ids for posts, categories and entries: easy to type into `/tritown news open`, and never reused by chance. */
object NewsIds {

    private const val LENGTH = 6
    private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"
    private val random = SecureRandom()

    /** A new id that [taken] does not already hold. */
    fun next(taken: (String) -> Boolean = { false }): String {
        while (true) {
            val id = String(CharArray(LENGTH) { ALPHABET[random.nextInt(ALPHABET.length)] })
            if (!taken(id)) return id
        }
    }
}
