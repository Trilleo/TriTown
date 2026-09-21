package net.trilleo.mc.plugins.tritown.shops.storage

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.trilleo.mc.plugins.tritown.utils.AtomicFile
import java.io.File
import java.util.logging.Logger

/**
 * Keeps every shop in one JSON file under `<dataFolder>/shops/`.
 *
 * Writes go to a temporary file which is then moved into place, with the
 * previous copy kept as `.bak`, so a crash mid-write leaves either the old file
 * or the new one and never a truncated one. A file that will not parse falls
 * back to the backup rather than starting empty — an empty start would be
 * written back over the real data on the next save.
 *
 * A single entry that will not parse is skipped with a warning. Losing one line
 * of one shop is recoverable; refusing to open the server is not.
 */
class JsonShopStorage(directory: File, private val logger: Logger) : ShopStorage {

    private val root = File(directory, DIRECTORY)
    private val shopsFile = File(root, SHOPS_FILE)
    private val backupFile = File(root, "$SHOPS_FILE.bak")
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val ioLock = Any()

    override val schemaVersion: Int = ShopSchema.CURRENT

    override fun loadAll(): List<StoredShop> = synchronized(ioLock) {
        root.mkdirs()

        val document = read(shopsFile) ?: run {
            if (shopsFile.exists()) {
                logger.warning("${shopsFile.name} could not be read; falling back to the backup")
            }
            read(backupFile) ?: run {
                if (backupFile.exists()) {
                    throw ShopStorageException("Neither ${shopsFile.name} nor its backup could be read")
                }
                return emptyList()
            }
        }

        val version = document.get(KEY_VERSION)?.asInt ?: ShopSchema.CURRENT
        ShopSchema.checkReadable(version, shopsFile.name)

        val array = document.getAsJsonArray(KEY_SHOPS) ?: return emptyList()
        val shops = array.mapNotNull { element ->
            runCatching { gson.fromJson(element, StoredShop::class.java) }
                .getOrNull()
                ?.takeIf { it.id.isNotBlank() }
                ?: run {
                    logger.warning("Skipped an unreadable shop in ${shopsFile.name}")
                    null
                }
        }

        if (version < ShopSchema.CURRENT) {
            logger.info("Upgrading ${shopsFile.name} from schema $version to ${ShopSchema.CURRENT}")
        }
        ShopMigrations.upgrade(shops, version)
    }

    override fun saveAll(shops: List<StoredShop>) = synchronized(ioLock) {
        root.mkdirs()

        val document = JsonObject().apply {
            addProperty(KEY_VERSION, ShopSchema.CURRENT)
            add(KEY_SHOPS, gson.toJsonTree(shops))
        }

        try {
            AtomicFile.write(shopsFile.toPath(), backupFile.toPath(), gson.toJson(document))
        } catch (e: Exception) {
            logger.severe("Failed to write ${shopsFile.name}: [${e.javaClass.simpleName}] ${e.message}")
        }
    }

    private fun read(file: File): JsonObject? {
        if (!file.exists()) return null
        return runCatching { JsonParser.parseString(file.readText()).asJsonObject }.getOrNull()
    }

    private companion object {
        const val DIRECTORY = "shops"
        const val SHOPS_FILE = "shops.json"
        const val KEY_VERSION = "schemaVersion"
        const val KEY_SHOPS = "shops"
    }
}
