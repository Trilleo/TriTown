package net.trilleo.mc.plugins.tritown.storage.storage

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.trilleo.mc.plugins.tritown.utils.AtomicFile
import java.io.File
import java.util.*
import java.util.logging.Logger

/**
 * Keeps each player's storage in a file of its own under `<dataFolder>/storage/`.
 *
 * One file per player, so saving one storage never rewrites anyone else's.
 * Writes go through [AtomicFile], with the previous copy kept as `.bak`, and a
 * file that will not parse falls back to that backup. When neither can be read
 * the load fails outright: an empty storage handed back instead would be saved
 * over the player's items the moment they closed it.
 */
class JsonStorageStore(directory: File, private val logger: Logger) : StorageStore {

    private val root = File(directory, DIRECTORY)
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val ioLock = Any()

    override fun load(owner: UUID): StoredStorage? = synchronized(ioLock) {
        val file = fileOf(owner)
        val backup = backupOf(owner)

        val document = read(file) ?: run {
            if (file.exists()) logger.warning("${file.name} could not be read; falling back to the backup")
            read(backup) ?: run {
                if (file.exists() || backup.exists()) {
                    throw StorageStoreException("Neither ${file.name} nor its backup could be read")
                }
                return null
            }
        }

        val version = document.get(KEY_VERSION)?.asInt ?: SCHEMA_VERSION
        if (version > SCHEMA_VERSION) {
            throw StorageStoreException("${file.name} was written by a newer TriTown (schema $version)")
        }

        val body = document.getAsJsonObject(KEY_STORAGE) ?: return null
        return runCatching { gson.fromJson(body, StoredStorage::class.java) }
            .getOrElse { throw StorageStoreException("${file.name} does not hold a storage", it) }
    }

    override fun save(storage: StoredStorage) = synchronized(ioLock) {
        val owner = storage.owner?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: run {
            logger.severe("Refused to save a storage without a valid owner")
            return
        }
        root.mkdirs()

        val document = JsonObject().apply {
            addProperty(KEY_VERSION, SCHEMA_VERSION)
            add(KEY_STORAGE, gson.toJsonTree(storage))
        }

        try {
            AtomicFile.write(fileOf(owner).toPath(), backupOf(owner).toPath(), gson.toJson(document))
        } catch (e: Exception) {
            logger.severe("Failed to write the storage of $owner: [${e.javaClass.simpleName}] ${e.message}")
        }
    }

    override fun summaries(): List<StorageSummary> = synchronized(ioLock) {
        val files = root.listFiles { file -> file.isFile && file.name.endsWith(EXTENSION) } ?: return emptyList()

        files.mapNotNull { file ->
            val owner = runCatching { UUID.fromString(file.name.removeSuffix(EXTENSION)) }.getOrNull()
                ?: return@mapNotNull null
            val body = read(file)?.getAsJsonObject(KEY_STORAGE) ?: return@mapNotNull null

            val pages = body.getAsJsonArray("pages")
            val used = pages?.sumOf { page ->
                page.takeIf { it.isJsonObject }?.asJsonObject?.getAsJsonObject("slots")?.size() ?: 0
            } ?: 0

            StorageSummary(
                owner = owner,
                purchased = body.get("purchased")?.asInt ?: 0,
                pages = pages?.size() ?: 0,
                usedSlots = used,
            )
        }
    }

    private fun fileOf(owner: UUID) = File(root, "$owner$EXTENSION")

    private fun backupOf(owner: UUID) = File(root, "$owner$EXTENSION.bak")

    private fun read(file: File): JsonObject? {
        if (!file.exists()) return null
        return runCatching { JsonParser.parseString(file.readText()).asJsonObject }.getOrNull()
    }

    companion object {
        /** The layout this store writes. A newer one is refused rather than guessed at. */
        const val SCHEMA_VERSION = 1

        private const val DIRECTORY = "storage"
        private const val EXTENSION = ".json"
        private const val KEY_VERSION = "schemaVersion"
        private const val KEY_STORAGE = "storage"
    }
}
