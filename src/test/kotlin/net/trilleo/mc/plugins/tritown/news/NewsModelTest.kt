package net.trilleo.mc.plugins.tritown.news

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The pieces of the news that decide what a player reads: which language, which tag, and what is unread. */
class NewsModelTest {

    // ── LocalizedText ───────────────────────────────────────────────────

    @Test
    fun `a missing translation falls back to the main text`() {
        val text = LocalizedText("Hello", mutableMapOf("zh_CN" to "你好"))

        assertEquals("你好", text.get("zh_CN"))
        assertEquals("你好", text.get("ZH_cn"))
        assertEquals("Hello", text.get("en_US"))
        assertEquals("Hello", text.get(null))
    }

    @Test
    fun `translating again replaces the old translation whatever its case`() {
        val text = LocalizedText("Hello", mutableMapOf("zh_cn" to "旧"))
        text.translate("zh_CN", "新")

        assertEquals(mapOf("zh_CN" to "新"), text.translations)

        text.clear("ZH_CN")
        assertNull(text.translation("zh_CN"))
    }

    // ── NewsShorthand ───────────────────────────────────────────────────

    @Test
    fun `a prefix picks the tag and is dropped from the text`() {
        assertEquals(
            EntryTag.NEW to "Added harvest festivals",
            NewsShorthand.parse("+ Added harvest festivals", EntryTag.NOTE)
        )
        assertEquals(
            EntryTag.CHANGED to "Shops restock hourly",
            NewsShorthand.parse("*Shops restock hourly", EntryTag.NOTE)
        )
        assertEquals(
            EntryTag.FIXED to "Upkeep charged once",
            NewsShorthand.parse("! Upkeep charged once", EntryTag.NOTE)
        )
        assertEquals(EntryTag.REMOVED to "Old spawn", NewsShorthand.parse("- Old spawn", EntryTag.NOTE))
        assertEquals(EntryTag.NOTE to "Restart at 6", NewsShorthand.parse("? Restart at 6", EntryTag.NEW))
    }

    @Test
    fun `a line without a prefix keeps the previous tag`() {
        assertEquals(EntryTag.FIXED to "Another fix", NewsShorthand.parse("  Another fix ", EntryTag.FIXED))
    }

    // ── Unread ──────────────────────────────────────────────────────────

    private fun published(id: String, at: Long) =
        NewsPost(id = id, status = PostStatus.PUBLISHED, publishedAt = at)

    @Test
    fun `posts from before a player first met the news are not unread, except the newest`() {
        val posts = listOf(published("new", 300L), published("mid", 200L), published("old", 100L))

        val unread = NewsReadState.unreadOf(posts, read = emptySet(), since = 1_000L)

        assertEquals(listOf("new"), unread.map { it.id })
    }

    @Test
    fun `posts published since then are unread until read`() {
        val posts = listOf(published("c", 300L), published("b", 200L), published("a", 100L))

        val unread = NewsReadState.unreadOf(posts, read = setOf("c"), since = 150L)

        assertEquals(listOf("b"), unread.map { it.id })
    }

    @Test
    fun `the newest post is found by date even when a pinned one is listed first`() {
        val pinned = published("pinned", 100L).apply { pinned = true }
        val posts = listOf(pinned, published("latest", 500L))

        val unread = NewsReadState.unreadOf(posts, read = emptySet(), since = 1_000L)

        assertEquals(listOf("latest"), unread.map { it.id })
    }
}
