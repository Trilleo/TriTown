package net.trilleo.mc.plugins.tritown.news

/**
 * Reads the tag off the front of an entry typed in chat, so a whole changelog
 * can be written line after line without stopping to pick a tag for each.
 *
 * `+` is new, `*` changed, `!` fixed, `-` removed and `?` a note. A line
 * without one keeps the tag the previous line had.
 */
object NewsShorthand {

    private val prefixes = mapOf(
        '+' to EntryTag.NEW,
        '*' to EntryTag.CHANGED,
        '!' to EntryTag.FIXED,
        '-' to EntryTag.REMOVED,
        '?' to EntryTag.NOTE,
    )

    /** The tag and text of [line], using [fallback] when it starts with no prefix. */
    fun parse(line: String, fallback: EntryTag): Pair<EntryTag, String> {
        val trimmed = line.trim()
        val tag = trimmed.firstOrNull()?.let(prefixes::get) ?: return fallback to trimmed
        return tag to trimmed.drop(1).trim()
    }
}
