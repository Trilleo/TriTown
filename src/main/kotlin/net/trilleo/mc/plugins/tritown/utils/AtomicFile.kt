package net.trilleo.mc.plugins.tritown.utils

import java.nio.file.*

/**
 * Replaces a file without ever leaving a truncated copy behind.
 *
 * The content goes to a temporary file first, the previous file is kept as
 * [backup], and only then is the new one moved into place — so a crash
 * mid-write leaves either the old file or the new one, and the backup is there
 * for a reader that finds the main file unreadable.
 */
object AtomicFile {

    /** Writes [content] to [target], keeping what was there before as [backup]. */
    fun write(target: Path, backup: Path, content: String) {
        val temporary = target.resolveSibling("${target.fileName}.tmp")
        Files.writeString(
            temporary,
            content,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        )

        if (Files.exists(target)) {
            Files.move(target, backup, StandardCopyOption.REPLACE_EXISTING)
        }

        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
