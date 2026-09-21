package net.trilleo.mc.plugins.tritown.storage

import net.trilleo.mc.plugins.tritown.Main
import net.trilleo.mc.plugins.tritown.config.StorageSettings
import net.trilleo.mc.plugins.tritown.economy.CurrencyRegistry
import net.trilleo.mc.plugins.tritown.economy.EconomyContext
import net.trilleo.mc.plugins.tritown.economy.Money
import net.trilleo.mc.plugins.tritown.economy.TransactionReason
import net.trilleo.mc.plugins.tritown.shops.ItemCodec
import net.trilleo.mc.plugins.tritown.storage.storage.*
import net.trilleo.mc.plugins.tritown.utils.EconomyUtil
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

/**
 * Every player's storage, and the only thing that reads or writes one.
 *
 * A storage is loaded the first time it is opened and kept while its owner is
 * online or anyone is looking at it. Everything that changes one runs on the
 * server thread — items can only be encoded there — and the finished text is
 * handed to a single writer thread, so two saves of the same storage can never
 * land out of order.
 *
 * One person at a time may change a storage. Its owner normally; an
 * administrator inspecting it otherwise. Anyone else who opens it while it is
 * being changed gets a read-only view, or is turned away if they cannot inspect.
 */
object StorageManager {

    /** What a viewer may do with the storage they opened. */
    enum class Access { EDIT, READ }

    /** How buying a page went. */
    enum class Purchase { BOUGHT, AT_MAX, CANNOT_AFFORD, UNAVAILABLE }

    private val loaded = ConcurrentHashMap<UUID, PlayerStorage>()
    private val editors = ConcurrentHashMap<UUID, UUID>()
    private val viewers = ConcurrentHashMap<UUID, MutableSet<UUID>>()

    private lateinit var store: StorageStore
    private lateinit var logger: Logger
    private var writer: ExecutorService? = null

    @Volatile
    var isReady: Boolean = false
        private set

    /** Whether players can use their storage right now. */
    val isAvailable: Boolean
        get() = isReady && StorageSettings.isLoaded && StorageSettings.snapshot.enabled

    fun start(storageStore: StorageStore, pluginLogger: Logger) {
        store = storageStore
        logger = pluginLogger
        writer = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "TriTown-Storage").apply { isDaemon = true } }
        isReady = true
    }

    /**
     * Writes every loaded storage and waits for the writer to finish.
     *
     * Called after every open storage menu has been closed, so what is written
     * is what the players last saw.
     */
    fun shutdown() {
        if (!isReady) return
        isReady = false

        val pending = loaded.values.map(::toStored)
        writer?.let { executor ->
            executor.shutdown()
            executor.awaitTermination(10, TimeUnit.SECONDS)
        }
        writer = null
        pending.forEach(store::save)

        loaded.clear()
        editors.clear()
        viewers.clear()
    }

    // ── Loading ─────────────────────────────────────────────────────────

    /**
     * [owner]'s storage, loading it when it is not in memory.
     *
     * @return `null` when the feature is off or the storage's file cannot be read
     */
    fun get(owner: UUID): PlayerStorage? {
        if (!isReady) return null
        loaded[owner]?.let { return it }

        val stored = try {
            store.load(owner)
        } catch (e: StorageStoreException) {
            logger.severe("The storage of $owner is unavailable: ${e.message}")
            return null
        }

        val storage = stored?.let { fromStored(owner, it) } ?: PlayerStorage(owner, 0, emptyList(), emptyList())
        return loaded.putIfAbsent(owner, storage) ?: storage
    }

    /** [owner]'s storage when it is already in memory, without loading it. */
    fun cached(owner: UUID): PlayerStorage? = loaded[owner]

    /** Every storage in memory right now. */
    fun cachedAll(): Collection<PlayerStorage> = loaded.values

    /**
     * Writes [owner]'s storage and forgets it, once nobody needs it in memory.
     *
     * @param leaving whether [owner] is on their way out, and so counts as offline
     */
    fun unloadIfIdle(owner: UUID, leaving: Boolean = false) {
        if (!viewers[owner].isNullOrEmpty()) return
        if (!leaving && Bukkit.getPlayer(owner) != null) return

        val storage = loaded.remove(owner) ?: return
        if (storage.dirty) save(storage)
    }

    // ── Who is looking ──────────────────────────────────────────────────

    /**
     * Lets [viewer] into [owner]'s storage.
     *
     * @param inspect whether [viewer] may inspect other players' storage, which
     *   earns a read-only view of one that is being changed instead of a refusal
     * @param readOnly whether [viewer] only wants to look
     * @return what [viewer] may do, or `null` when someone else is changing it
     *   and [viewer] may not look over their shoulder
     */
    fun claim(owner: UUID, viewer: UUID, inspect: Boolean, readOnly: Boolean = false): Access? {
        val access = when {
            readOnly -> Access.READ
            editors.putIfAbsent(owner, viewer).let { it == null || it == viewer } -> Access.EDIT
            inspect -> Access.READ
            else -> return null
        }
        viewers.computeIfAbsent(owner) { ConcurrentHashMap.newKeySet() }.add(viewer)
        return access
    }

    /** Lets go of [owner]'s storage for [viewer], and unloads it when that was the last reason to keep it. */
    fun release(owner: UUID, viewer: UUID) {
        editors.remove(owner, viewer)
        viewers[owner]?.let { set ->
            set.remove(viewer)
            if (set.isEmpty()) viewers.remove(owner, set)
        }
        unloadIfIdle(owner)
    }

    /** Who is changing [owner]'s storage, if anyone. */
    fun editorOf(owner: UUID): UUID? = editors[owner]

    // ── Pages ───────────────────────────────────────────────────────────

    /** The pages [storage] has paid for or was given, capped at the configured maximum. */
    fun owned(storage: PlayerStorage): Int {
        val settings = StorageSettings.snapshot
        return (settings.freePages + storage.purchased).coerceAtMost(settings.maxPages)
    }

    /**
     * The pages [storage] can open: those it owns, and any beyond them that
     * still hold items after an owner lowered the allowance, so nothing stored
     * is ever hidden.
     */
    fun pageCount(storage: PlayerStorage): Int = maxOf(owned(storage), storage.filledPages)

    /** What one more page costs [storage], or `null` when it is at the maximum. */
    fun nextPageCost(storage: PlayerStorage): Money? {
        val settings = StorageSettings.snapshot
        return StoragePricing.next(
            purchased = storage.purchased,
            owned = owned(storage),
            maxPages = settings.maxPages,
            base = settings.priceBase,
            multiplier = settings.priceMultiplier,
            scale = scale(),
        )
    }

    /**
     * Sells [player] one more page.
     *
     * Charged first, and the page only granted once the money has gone, so a
     * refusal anywhere leaves the storage as it was.
     */
    fun buyPage(player: Player): Purchase {
        val storage = get(player.uniqueId) ?: return Purchase.UNAVAILABLE
        val cost = nextPageCost(storage) ?: return Purchase.AT_MAX
        if (!EconomyUtil.isAvailable) return Purchase.UNAVAILABLE

        val page = owned(storage) + 1
        val charged = EconomyUtil.withdraw(
            player,
            toDouble(cost),
            EconomyContext.SOURCE_STORAGE,
            TransactionReason.of(TransactionReason.STORAGE_PAGE, "page" to page),
        )
        if (!charged) return Purchase.CANNOT_AFFORD

        storage.purchased++
        save(storage)
        return Purchase.BOUGHT
    }

    /** Sets how many pages [storage] owns in total, for free. Never below the free pages. */
    fun setOwned(storage: PlayerStorage, pages: Int) {
        val settings = StorageSettings.snapshot
        storage.purchased = (pages.coerceIn(1, settings.maxPages) - settings.freePages).coerceAtLeast(0)
        save(storage)
    }

    /** [money] as the `Double` Vault speaks in. */
    fun toDouble(money: Money): Double =
        if (CurrencyRegistry.isLoaded) CurrencyRegistry.primary.toDouble(money) else money.toDouble(DEFAULT_SCALE)

    private fun scale(): Int = if (CurrencyRegistry.isLoaded) CurrencyRegistry.primary.fractionalDigits else DEFAULT_SCALE

    // ── Items ───────────────────────────────────────────────────────────

    /**
     * Puts [stack] into [storage], trying [first] before the other pages it can open.
     *
     * @return what did not fit, or `null` when all of it did
     */
    fun deposit(storage: PlayerStorage, first: Int, stack: ItemStack): ItemStack? {
        val order = listOf(first) + (0 until pageCount(storage)).filter { it != first }
        val leftover = SlotFill.insert(order.map { storage.page(it).slots }, stack, StorageItems.rules)
        if (leftover == null || leftover.amount != stack.amount) markDirty(storage)
        return leftover
    }

    /**
     * Whether [items] would all fit in [storage] together, tried on a copy so
     * nothing is moved when they would not.
     */
    fun fits(storage: PlayerStorage, items: List<ItemStack>): Boolean {
        val copies = (0 until pageCount(storage)).map { index ->
            storage.page(index).slots.map { it?.clone() }.toTypedArray()
        }
        return items.all { SlotFill.insert(copies, it, StorageItems.rules) == null }
    }

    /** The materials anywhere in [storage], for depositing like with like. */
    fun materials(storage: PlayerStorage): Set<Material> =
        storage.existing.flatMap { page -> page.slots.mapNotNull { it?.type } }.toSet()

    // ── Saving ──────────────────────────────────────────────────────────

    /** Records that [storage] changed, to be written by the next flush. */
    fun markDirty(storage: PlayerStorage) {
        storage.dirty = true
    }

    /** Writes [storage] now, off the server thread. */
    fun save(storage: PlayerStorage) {
        if (!isReady) return
        storage.dirty = false
        val stored = toStored(storage)
        val executor = writer
        if (executor == null || executor.isShutdown) store.save(stored) else executor.execute { store.save(stored) }
    }

    /** Writes every storage that changed since it was last written. */
    fun flushDirty() {
        if (!isReady) return
        loaded.values.filter { it.dirty }.forEach(::save)
    }

    /**
     * How much every storage on file holds, handed to [callback] on the server thread.
     *
     * Read on the writer thread, behind any save still waiting, and then
     * corrected with what is in memory, which is always the newer of the two.
     */
    fun summaries(callback: (List<StorageSummary>) -> Unit) {
        val executor = writer ?: return
        val live = loaded.values.associate { it.owner to summaryOf(it) }

        executor.execute {
            val onFile = runCatching { store.summaries() }.getOrElse {
                logger.warning("Could not read the storage summaries: ${it.message}")
                emptyList()
            }
            val merged = (onFile.associateBy { it.owner } + live).values.toList()
            Bukkit.getScheduler().runTask(Main.instance, Runnable { callback(merged) })
        }
    }

    private fun summaryOf(storage: PlayerStorage) = StorageSummary(
        owner = storage.owner,
        purchased = storage.purchased,
        pages = storage.existing.size,
        usedSlots = storage.usedSlots,
    )

    // ── Conversion ──────────────────────────────────────────────────────

    private fun toStored(storage: PlayerStorage): StoredStorage {
        val pages = storage.existing.map { page ->
            StoredPage(
                name = page.name,
                icon = page.icon,
                slots = page.slots.withIndex()
                    .filter { it.value != null }
                    .associate { (index, item) -> index.toString() to ItemCodec.encode(item!!) },
            )
        }
        val trimmed = pages.dropLastWhile { it.name == null && it.icon == null && it.slots.isNullOrEmpty() }

        return StoredStorage(
            owner = storage.owner.toString(),
            purchased = storage.purchased,
            pages = trimmed,
            unreadable = storage.unreadable,
        )
    }

    private fun fromStored(owner: UUID, stored: StoredStorage): PlayerStorage {
        val unreadable = stored.unreadable.orEmpty().toMutableList()

        val pages = stored.pages.orEmpty().map { storedPage ->
            StoragePage(storedPage.name, storedPage.icon).also { page ->
                storedPage.slots.orEmpty().forEach { (key, encoded) ->
                    val index = key.toIntOrNull()?.takeIf { it in 0 until StoragePage.SIZE }
                    val item = ItemCodec.decode(encoded)
                    if (index == null || item == null || page.slots[index] != null) {
                        unreadable += encoded
                    } else {
                        page.slots[index] = item
                    }
                }
            }
        }

        if (unreadable.size > stored.unreadable.orEmpty().size) {
            logger.warning(
                "Set aside ${unreadable.size - stored.unreadable.orEmpty().size} unreadable item(s) in the storage of $owner"
            )
        }

        return PlayerStorage(owner, stored.purchased.coerceAtLeast(0), pages, unreadable)
    }

    private const val DEFAULT_SCALE = 2
}
