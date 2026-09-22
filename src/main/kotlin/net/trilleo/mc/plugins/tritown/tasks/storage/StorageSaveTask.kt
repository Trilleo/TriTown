package net.trilleo.mc.plugins.tritown.tasks.storage

import net.trilleo.mc.plugins.tritown.config.StorageSettings
import net.trilleo.mc.plugins.tritown.registration.PluginTask
import net.trilleo.mc.plugins.tritown.storage.StorageManager

/**
 * Writes changed storages to disk on an interval.
 *
 * Runs on the server thread, because items can only be encoded there; the
 * encoded text is written on the storage's own writer thread. Closing a storage
 * or switching its page writes it too, so this only bounds what a crash can
 * cost while someone is still moving items around in one.
 */
class StorageSaveTask : PluginTask(
    delay = saveIntervalTicks(),
    period = saveIntervalTicks(),
) {
    override fun run() {
        StorageManager.flushDirty()
    }
}

/**
 * The configured save interval in ticks.
 *
 * Read at construction, which is safe because the storage settings are loaded
 * in `onEnable` before the task registrar runs.
 */
private fun saveIntervalTicks(): Long {
    val seconds = if (StorageSettings.isLoaded) StorageSettings.snapshot.saveIntervalSeconds else 30L
    return seconds * 20L
}
