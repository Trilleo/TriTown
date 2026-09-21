package net.trilleo.mc.plugins.tritown.news.storage

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.trilleo.mc.plugins.tritown.news.NewsPost
import net.trilleo.mc.plugins.tritown.utils.AtomicFile
import java.io.File
import java.util.logging.Logger

/**
 * Keeps every news post in one JSON file, `<dataFolder>/news/posts.json`.
 *
 * Written through [AtomicFile] with the previous copy kept as `.bak`, and read
 * back from that backup when the main file will not parse — an empty start
 * would be written over the real posts on the next save. A single post that
 * will not parse is skipped with a warning.
 *
 * The file records the layout version it was written in. A newer one is
 * refused, because an older build would drop what it did not understand and
 * write that loss back.
 */
class JsonNewsStorage(directory: File, private val logger: Logger) : NewsStorage {

    private val root = File(directory, DIRECTORY)
    private val newsFile = File(root, NEWS_FILE)
    private val backupFile = File(root, "$NEWS_FILE.bak")
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val ioLock = Any()

    override fun loadAll(): List<NewsPost> = synchronized(ioLock) {
        root.mkdirs()

        val document = read(newsFile) ?: run {
            if (newsFile.exists()) {
                logger.warning("${newsFile.name} could not be read; falling back to the backup")
            }
            read(backupFile) ?: run {
                if (backupFile.exists()) {
                    throw NewsStorageException("Neither ${newsFile.name} nor its backup could be read")
                }
                return emptyList()
            }
        }

        val version = document.get(KEY_VERSION)?.asInt ?: SCHEMA
        if (version > SCHEMA) {
            throw NewsStorageException(
                "${newsFile.name} was written by a newer version of TriTown (schema $version, this build reads " +
                        "up to $SCHEMA). Update TriTown rather than letting an older build overwrite it."
            )
        }

        val array = document.getAsJsonArray(KEY_POSTS) ?: return emptyList()
        array.mapNotNull { element ->
            runCatching { gson.fromJson(element, NewsPost::class.java).also { it.repair() } }
                .getOrNull()
                ?.takeIf { it.id.isNotBlank() }
                ?: run {
                    logger.warning("Skipped an unreadable post in ${newsFile.name}")
                    null
                }
        }
    }

    override fun saveAll(posts: List<NewsPost>) = synchronized(ioLock) {
        root.mkdirs()

        val document = JsonObject().apply {
            addProperty(KEY_VERSION, SCHEMA)
            add(KEY_POSTS, gson.toJsonTree(posts))
        }

        try {
            AtomicFile.write(newsFile.toPath(), backupFile.toPath(), gson.toJson(document))
        } catch (e: Exception) {
            logger.severe("Failed to write ${newsFile.name}: [${e.javaClass.simpleName}] ${e.message}")
        }
    }

    private fun read(file: File): JsonObject? {
        if (!file.exists()) return null
        return runCatching { JsonParser.parseString(file.readText()).asJsonObject }.getOrNull()
    }

    private companion object {
        const val DIRECTORY = "news"
        const val NEWS_FILE = "posts.json"
        const val KEY_VERSION = "schemaVersion"
        const val KEY_POSTS = "posts"

        /** The layout this build writes. */
        const val SCHEMA = 1
    }
}
