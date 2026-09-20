package net.trilleo.mc.plugins.tritown.shops

import net.trilleo.mc.plugins.tritown.config.ShopSettings
import net.trilleo.mc.plugins.tritown.enums.LimitPeriod
import net.trilleo.mc.plugins.tritown.enums.MatchMode
import net.trilleo.mc.plugins.tritown.enums.TownyRequirement
import net.trilleo.mc.plugins.tritown.shops.storage.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Logger

/**
 * Every shop the server has, and the only thing that reads or writes them.
 *
 * Definitions are edited rarely and must never be lost, so an edit is written
 * out the moment it is made. Stock and statistics change on every purchase, so
 * they only mark the shops dirty and are flushed by
 * [net.trilleo.mc.plugins.tritown.tasks.shop.ShopSaveTask] — a crash costs at
 * most one flush interval of counters, never a definition.
 *
 * Shops are read from the server thread when a menu opens and written from the
 * flush task, so the registry is concurrent and a write takes a snapshot.
 */
object ShopManager {

    private val shops = ConcurrentHashMap<String, ShopDefinition>()
    private val dirty = AtomicBoolean(false)

    private lateinit var storage: ShopStorage
    private lateinit var logger: Logger

    @Volatile
    var isReady: Boolean = false
        private set

    /**
     * Loads every stored shop.
     *
     * A store that cannot be read at all leaves the feature off rather than
     * starting empty, because an empty start would be written back over the
     * real shops at the next save.
     */
    fun start(store: ShopStorage, pluginLogger: Logger) {
        storage = store
        logger = pluginLogger

        val loaded = try {
            store.loadAll()
        } catch (e: ShopStorageException) {
            logger.severe("Shops are unavailable: ${e.message}")
            isReady = false
            return
        }

        shops.clear()
        for (stored in loaded) {
            val shop = toDefinition(stored)
            shops[shop.id] = shop
        }

        dirty.set(false)
        isReady = true
        logger.info("Loaded ${shops.size} shop(s)")
    }

    /** Writes anything outstanding and stops serving shops. */
    fun shutdown() {
        if (!isReady) return
        flush()
        isReady = false
        shops.clear()
    }

    // ── Reading ─────────────────────────────────────────────────────────

    /** The shop with [id], or `null` when there is none. */
    fun get(id: String): ShopDefinition? = shops[id.lowercase()]

    /**
     * The shop `/trades` opens, creating it empty when it is not there.
     *
     * Created rather than reported missing, so a fresh server has somewhere to
     * put its first entry and `/trades` never answers with an apology. It is an
     * ordinary shop in every other way: it is listed, edited, gated and sorted
     * like the rest, and an owner who wants a different one only has to point
     * `shops.global-id` somewhere else.
     */
    fun global(): ShopDefinition? {
        if (!isReady || !ShopSettings.isLoaded) return null

        val id = ShopSettings.snapshot.globalId
        return get(id) ?: create(id, id)
    }

    /** Whether [id] is the shop `/trades` opens, which is the one shop that may not be deleted. */
    fun isGlobal(id: String): Boolean =
        ShopSettings.isLoaded && id.equals(ShopSettings.snapshot.globalId, ignoreCase = true)

    /** Every shop, ordered by id so a listing does not shuffle between restarts. */
    fun all(): List<ShopDefinition> = shops.values.sortedBy { it.id }

    /** Every shop id, ordered. */
    fun ids(): List<String> = shops.keys.sorted()

    /** The shop bound to the FancyNpcs NPC with [npcId], or `null` when that NPC opens nothing. */
    fun byNpc(npcId: String): ShopDefinition? = shops.values.firstOrNull { npcId in it.npcIds }

    // ── Editing ─────────────────────────────────────────────────────────

    /**
     * Creates an empty shop called [id].
     *
     * @return the new shop, or `null` when [id] is malformed or already taken
     */
    fun create(id: String, displayName: String): ShopDefinition? {
        val key = id.lowercase()
        if (!ShopDefinition.isValidId(key) || shops.containsKey(key)) return null

        val shop = ShopDefinition(id = key, displayName = displayName)
        shops[key] = shop
        save()
        return shop
    }

    /**
     * Removes the shop with [id]. Returns `false` when there was none.
     *
     * The global shop is not one of them: it would be recreated empty on the
     * next start anyway, so deleting it only ever means losing its entries
     * without losing the shop. Emptying it in the editor is the honest way to
     * do that.
     */
    fun delete(id: String): Boolean {
        if (isGlobal(id)) return false

        val removed = shops.remove(id.lowercase()) != null
        if (removed) save()
        return removed
    }

    /** Writes every shop out now, for a change to a definition that must not be lost. */
    fun save() {
        if (!isReady) return
        storage.saveAll(snapshot())
        dirty.set(false)
    }

    /** Records that stock or statistics changed, to be written by the next flush. */
    fun markDirty() {
        dirty.set(true)
    }

    /** Writes the shops out when anything has changed since the last write. */
    fun flush() {
        if (!isReady) return
        if (!dirty.compareAndSet(true, false)) return
        storage.saveAll(snapshot())
    }

    private fun snapshot(): List<StoredShop> = shops.values.sortedBy { it.id }.map(::toStored)

    // ── Conversion ──────────────────────────────────────────────────────

    private fun toDefinition(stored: StoredShop): ShopDefinition {
        val entries = stored.entries.mapNotNull { entry -> toEntry(stored.id, entry) }

        return ShopDefinition(
            id = stored.id.lowercase(),
            displayName = stored.displayName.ifBlank { stored.id },
            gate = ShopGate(stored.permission, requirement(stored.towny), stored.hideWhenLocked),
            entries = entries.toMutableList(),
            npcIds = stored.npcIds.toCollection(linkedSetOf()),
        )
    }

    private fun toEntry(shopId: String, entry: StoredEntry): ShopEntry? {
        val item = ItemCodec.decode(entry.item) ?: run {
            logger.warning("Dropped an unreadable item from shop $shopId")
            return null
        }

        val buyLimit = toLimit(entry.limitAmount, entry.limitPeriod)
        val sellLimit = toLimit(entry.sellLimitAmount, entry.sellLimitPeriod)

        val stock = if (entry.stockMax > 0) {
            ShopStock(
                max = entry.stockMax,
                restockSeconds = entry.stockRestockSeconds,
                remaining = entry.stockRemaining.coerceIn(0, entry.stockMax),
                lastRestock = entry.stockLastRestock,
            )
        } else {
            null
        }

        return ShopEntry(
            id = entry.id.ifBlank { UUID.randomUUID().toString() },
            item = item,
            // A shop written before the bundle was its own field kept it as the item's stack size.
            bundle = if (entry.bundle > 0) entry.bundle else item.amount,
            buy = toCost(entry.buy),
            sell = toCost(entry.sell),
            gate = ShopGate(entry.permission, requirement(entry.towny), entry.hideWhenLocked),
            buyLimit = buyLimit,
            sellLimit = sellLimit,
            stock = stock,
            discountable = entry.discountable,
            matchMode = enumOrDefault(entry.matchMode, MatchMode.EXACT),
            stats = ShopStats(entry.bought, entry.sold, entry.moneyIn, entry.moneyOut),
        )
    }

    private fun toStored(shop: ShopDefinition): StoredShop = StoredShop(
        id = shop.id,
        displayName = shop.displayName,
        permission = shop.gate.permission,
        towny = shop.gate.towny.name,
        hideWhenLocked = shop.gate.hideWhenLocked,
        npcIds = shop.npcIds.toList(),
        entries = shop.entries.map { entry ->
            StoredEntry(
                id = entry.id,
                item = ItemCodec.encode(entry.item),
                bundle = entry.bundleSize,
                buy = toStoredCost(entry.buy),
                sell = toStoredCost(entry.sell),
                permission = entry.gate.permission,
                towny = entry.gate.towny.name,
                hideWhenLocked = entry.gate.hideWhenLocked,
                limitAmount = entry.buyLimit?.amount ?: 0,
                limitPeriod = (entry.buyLimit?.period ?: LimitPeriod.NONE).name,
                sellLimitAmount = entry.sellLimit?.amount ?: 0,
                sellLimitPeriod = (entry.sellLimit?.period ?: LimitPeriod.NONE).name,
                stockMax = entry.stock?.max ?: 0,
                stockRestockSeconds = entry.stock?.restockSeconds ?: 0L,
                stockRemaining = entry.stock?.remaining ?: 0,
                stockLastRestock = entry.stock?.lastRestock ?: 0L,
                discountable = entry.discountable,
                matchMode = entry.matchMode.name,
                bought = entry.stats.bought,
                sold = entry.stats.sold,
                moneyIn = entry.stats.moneyIn,
                moneyOut = entry.stats.moneyOut,
            )
        },
    )

    private fun toLimit(amount: Int, period: String): ShopLimit? =
        if (amount > 0) ShopLimit(amount, enumOrDefault(period, LimitPeriod.NONE)) else null

    private fun toCost(stored: StoredCost?): ShopCost? =
        stored?.let { ShopCost(it.money, ItemCodec.decodeAll(it.items)) }

    private fun toStoredCost(cost: ShopCost?): StoredCost? =
        cost?.let { StoredCost(it.money, ItemCodec.encodeAll(it.items)) }

    private fun requirement(name: String): TownyRequirement = enumOrDefault(name, TownyRequirement.NONE)

    /** A stored name that no longer exists falls back rather than failing the whole load. */
    private inline fun <reified T : Enum<T>> enumOrDefault(name: String, default: T): T =
        enumValues<T>().firstOrNull { it.name.equals(name, ignoreCase = true) } ?: default
}
