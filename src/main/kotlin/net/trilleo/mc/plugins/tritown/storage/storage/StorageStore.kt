package net.trilleo.mc.plugins.tritown.storage.storage

import java.util.*

/**
 * Where players' storages are kept between restarts.
 *
 * Deals only in [StoredStorage], whose items are already encoded text, so a
 * store never touches a Bukkit type and can write from any thread.
 */
interface StorageStore {

    /**
     * [owner]'s storage, or `null` when they have never stored anything.
     *
     * @throws StorageStoreException when a file exists but cannot be read, which
     *   must stop that storage rather than let an empty one overwrite it
     */
    fun load(owner: UUID): StoredStorage?

    /** Replaces [storage]'s file with what it holds now. */
    fun save(storage: StoredStorage)

    /** A light count of every storage on file, read without decoding a single item. */
    fun summaries(): List<StorageSummary>
}

/**
 * One storage as it is written to disk.
 *
 * Fields are nullable because Gson fills a missing one with `null` whatever the
 * declaration says; [StoredPage.slots] maps a slot's index to its encoded item,
 * so an empty slot costs nothing.
 *
 * @param unreadable items that could not be decoded, kept as they were found so
 *   they are never written away
 */
data class StoredStorage(
    val owner: String? = null,
    val purchased: Int = 0,
    val pages: List<StoredPage>? = null,
    val unreadable: List<String>? = null,
)

data class StoredPage(
    val name: String? = null,
    val icon: String? = null,
    val slots: Map<String, String>? = null,
)

/** How much one storage holds, for the admin panel. */
data class StorageSummary(val owner: UUID, val purchased: Int, val pages: Int, val usedSlots: Int)

/** Raised when a storage file exists but cannot be read, and must not be silently replaced. */
class StorageStoreException(message: String, cause: Throwable? = null) : Exception(message, cause)
