package net.trilleo.mc.plugins.tritown.news.storage

import net.trilleo.mc.plugins.tritown.news.EntryTag
import net.trilleo.mc.plugins.tritown.news.LocalizedText
import net.trilleo.mc.plugins.tritown.news.NewsCategory
import net.trilleo.mc.plugins.tritown.news.NewsEntry
import net.trilleo.mc.plugins.tritown.news.NewsPost
import net.trilleo.mc.plugins.tritown.news.PostStatus
import java.io.File
import java.nio.file.Files
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.test.*

/** The news file holds what administrators wrote by hand, so it must survive a crash, a bad edit and an old build. */
class JsonNewsStorageTest {

    private val directory: File = Files.createTempDirectory("tritown-news").toFile()
    private val logger = Logger.getAnonymousLogger().apply { level = Level.OFF }
    private val storage = JsonNewsStorage(directory, logger)

    private val newsFile = File(File(directory, "news"), "posts.json")
    private val backupFile = File(File(directory, "news"), "posts.json.bak")

    @AfterTest
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun `a server with no news starts empty`() {
        assertEquals(emptyList(), storage.loadAll())
    }

    @Test
    fun `a post survives a round trip`() {
        val post = NewsPost(
            id = "abc123",
            title = LocalizedText("<gold>Autumn update", mutableMapOf("zh_CN" to "秋季更新")),
            summary = LocalizedText("Harvest season is here."),
            label = "v1.3",
            icon = "PUMPKIN",
            categories = mutableListOf(
                NewsCategory(
                    id = "cat001",
                    name = LocalizedText("Towns"),
                    icon = "BELL",
                    entries = mutableListOf(
                        NewsEntry("ent001", EntryTag.NEW, LocalizedText("Towns can now hold a harvest festival.")),
                        NewsEntry("ent002", EntryTag.FIXED, LocalizedText("Upkeep is no longer charged twice.")),
                    ),
                )
            ),
            status = PostStatus.PUBLISHED,
            pinned = true,
            createdAt = 1_000L,
            publishedAt = 2_000L,
            authorName = "Trilleo",
        )

        storage.saveAll(listOf(post))
        val loaded = storage.loadAll().single()

        assertEquals("abc123", loaded.id)
        assertEquals("秋季更新", loaded.title.get("zh_CN"))
        assertEquals("Harvest season is here.", loaded.summary?.main)
        assertEquals("v1.3", loaded.label)
        assertEquals(PostStatus.PUBLISHED, loaded.status)
        assertTrue(loaded.pinned)
        assertEquals(2_000L, loaded.publishedAt)
        assertEquals(listOf(EntryTag.NEW, EntryTag.FIXED), loaded.categories.single().entries.map { it.tag })
        assertEquals("Upkeep is no longer charged twice.", loaded.categories.single().entry("ent002")?.text?.main)
    }

    @Test
    fun `fields an older file lacks take their defaults`() {
        newsFile.parentFile.mkdirs()
        newsFile.writeText("""{"schemaVersion":1,"posts":[{"id":"old001","title":{"main":"Hello"}}]}""")

        val loaded = storage.loadAll().single()

        assertEquals(PostStatus.DRAFT, loaded.status)
        assertEquals(NewsPost.DEFAULT_ICON, loaded.icon)
        assertTrue(loaded.categories.isEmpty())
        assertTrue(loaded.title.translations.isEmpty())
    }

    @Test
    fun `an unknown tag or explicit null is repaired rather than left null`() {
        newsFile.parentFile.mkdirs()
        newsFile.writeText(
            """{"schemaVersion":1,"posts":[{"id":"bad001","title":null,"status":"ARCHIVED","categories":[
               {"id":"c","name":{"main":"X"},"entries":[{"id":"e","tag":"SHINY","text":{"main":"t"}}]}]}]}"""
        )

        val loaded = storage.loadAll().single()

        assertEquals("", loaded.title.main)
        assertEquals(PostStatus.DRAFT, loaded.status)
        assertEquals(EntryTag.NOTE, loaded.categories.single().entries.single().tag)
    }

    @Test
    fun `an unreadable file falls back to the backup`() {
        storage.saveAll(listOf(NewsPost(id = "first1")))
        storage.saveAll(listOf(NewsPost(id = "second")))
        newsFile.writeText("{ not json")

        assertEquals(listOf("first1"), storage.loadAll().map { it.id })
    }

    @Test
    fun `a file and backup that are both unreadable refuse to load`() {
        newsFile.parentFile.mkdirs()
        newsFile.writeText("{ not json")
        backupFile.writeText("{ also not json")

        assertFailsWith<NewsStorageException> { storage.loadAll() }
    }

    @Test
    fun `a file from a newer build is refused`() {
        newsFile.parentFile.mkdirs()
        newsFile.writeText("""{"schemaVersion":99,"posts":[]}""")

        assertFailsWith<NewsStorageException> { storage.loadAll() }
    }
}
