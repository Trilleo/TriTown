package net.trilleo.mc.plugins.tritown.shops.storage

/**
 * Versioning for the shop file's layout.
 *
 * The file records the version it was written with, so a later change to the
 * shape is detected rather than silently misread. A newer file is refused: an
 * older build would drop what it did not understand, and the next save would
 * write that loss back over the real data. An older one is brought forward by
 * [ShopMigrations] as it is read.
 *
 * ### History
 *
 * 1. The first shape shops were written in. Stock and per-player limits counted
 *    purchases.
 * 2. Stock and per-player limits count items rather than purchases.
 */
object ShopSchema {

    /** The version this build writes. */
    const val CURRENT: Int = 2

    /** The oldest version this build can still read. */
    const val OLDEST_SUPPORTED: Int = 1

    /** @throws ShopStorageException when data written at [version] cannot be read by this build */
    fun checkReadable(version: Int, source: String) {
        if (version > CURRENT) {
            throw ShopStorageException(
                "$source was written by a newer version of TriTown (schema $version, this build reads up to " +
                        "$CURRENT). Update TriTown rather than letting an older build overwrite it."
            )
        }
        if (version < OLDEST_SUPPORTED) {
            throw ShopStorageException(
                "$source uses schema $version, which this build can no longer read (oldest supported is " +
                        "$OLDEST_SUPPORTED)."
            )
        }
    }
}
