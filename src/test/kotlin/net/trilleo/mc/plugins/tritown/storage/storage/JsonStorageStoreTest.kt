package net.trilleo.mc.plugins.tritown.storage.storage

import java.io.File
import java.nio.file.Files
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.test.*

/**
 * A storage file holds a player's belongings, so what it must never do is lose
 * them: not to a crash mid-write, and not to a file it cannot read being
 * replaced with an empty one.
 */
class JsonStorageStoreTest {

    private val directory: File = Files.createTempDirectory("tritown-storage").toFile()
    private val logger = Logger.getAnonymousLogger().apply { level = Level.OFF }
    private val store = JsonStorageStore(directory, logger)

    private val owner = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val file = File(File(directory, "storage"), "$owner.json")
    private val backup = File(File(directory, "storage"), "$owner.json.bak")

    @AfterTest
    fun cleanUp() {
        directory.deleteRecursively()
    }

    private fun sample(purchased: Int = 3) = StoredStorage(
        owner = owner.toString(),
        purchased = purchased,
        pages = listOf(
            StoredPage(name = "Ores", icon = "IRON_ORE", slots = mapOf("0" to "encoded-iron", "44" to "encoded-gold")),
            StoredPage(slots = mapOf("3" to "encoded-dirt")),
        ),
        unreadable = listOf("mystery"),
    )

    @Test
    fun `a player who never stored anything has no storage`() {
        assertNull(store.load(owner))
    }

    @Test
    fun `a storage survives a round trip`() {
        store.save(sample())
        assertEquals(sample(), store.load(owner))
    }

    @Test
    fun `an unreadable file falls back to the backup`() {
        store.save(sample(purchased = 1))
        store.save(sample(purchased = 2))
        file.writeText("{ not json")

        assertEquals(1, store.load(owner)?.purchased)
    }

    @Test
    fun `a storage that cannot be read at all is refused rather than started empty`() {
        store.save(sample())
        file.writeText("{ not json")
        backup.writeText("{ nor this")

        assertFailsWith<StorageStoreException> { store.load(owner) }
    }

    @Test
    fun `a file from a newer version is refused`() {
        store.save(sample())
        file.writeText(file.readText().replace("\"schemaVersion\": 1", "\"schemaVersion\": 99"))

        assertFailsWith<StorageStoreException> { store.load(owner) }
    }

    @Test
    fun `summaries count what is stored without decoding it`() {
        store.save(sample())
        val other = UUID.fromString("00000000-0000-0000-0000-000000000002")
        store.save(StoredStorage(owner = other.toString(), purchased = 0, pages = emptyList()))

        val summaries = store.summaries().associateBy { it.owner }

        assertEquals(StorageSummary(owner, purchased = 3, pages = 2, usedSlots = 3), summaries[owner])
        assertEquals(StorageSummary(other, purchased = 0, pages = 0, usedSlots = 0), summaries[other])
    }
}
