package net.trilleo.mc.plugins.tritown.config

import net.kyori.adventure.key.Key
import java.util.logging.Logger

/**
 * An immutable snapshot of the `news` block of `config.yml`.
 *
 * @param enabled          whether players can read the news and administrators write it
 * @param joinMessage      whether a player with unread posts is told so as they join
 * @param joinDelayTicks   how long after joining that message waits, so it is not lost among the welcome messages
 * @param joinPreview      how many unread titles the join message lists before pointing at the rest
 * @param announceTitle    whether publishing a post shows a title on every online player's screen
 * @param announceChat     whether publishing a post sends every online player a line they can click
 * @param announceSound    the sound publishing a post plays, or `null` for none
 * @param maxEntryLength   the most characters one entry may be, so it stays a line rather than an essay
 */
data class NewsSettings(
    val enabled: Boolean,
    val joinMessage: Boolean,
    val joinDelayTicks: Long,
    val joinPreview: Int,
    val announceTitle: Boolean,
    val announceChat: Boolean,
    val announceSound: Key?,
    val maxEntryLength: Int,
) {

    companion object {

        @Volatile
        private var current: NewsSettings? = null

        /** The settings in force. */
        val snapshot: NewsSettings
            get() = current ?: error("News settings have not been loaded yet")

        /** Whether [load] has run. */
        val isLoaded: Boolean
            get() = current != null

        /** Reads the `news` block from [config] and makes it the current snapshot. */
        fun load(config: PluginConfig, logger: Logger): NewsSettings = read(config, logger).also { current = it }

        private fun read(config: PluginConfig, logger: Logger): NewsSettings = NewsSettings(
            enabled = config.getBoolean("news.enabled", true),
            joinMessage = config.getBoolean("news.join-message.enabled", true),
            joinDelayTicks = config.getLong("news.join-message.delay-seconds", 3L).coerceIn(0L, 60L) * 20L,
            joinPreview = config.getInt("news.join-message.preview", 3).coerceIn(0, 10),
            announceTitle = config.getBoolean("news.announce.title", true),
            announceChat = config.getBoolean("news.announce.chat", true),
            announceSound = sound(config.getString("news.announce.sound", DEFAULT_SOUND), logger),
            maxEntryLength = config.getInt("news.max-entry-length", 200).coerceIn(20, 1000),
        )

        /** A blank value switches the sound off; anything that is not a key is reported rather than played as silence. */
        private fun sound(name: String, logger: Logger): Key? {
            if (name.isBlank()) return null
            return runCatching { Key.key(name) }.getOrElse {
                logger.warning("news.announce.sound '$name' is not a sound key; playing none")
                null
            }
        }

        private const val DEFAULT_SOUND = "minecraft:block.note_block.chime"
    }
}
