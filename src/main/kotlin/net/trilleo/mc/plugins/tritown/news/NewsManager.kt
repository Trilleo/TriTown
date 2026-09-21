package net.trilleo.mc.plugins.tritown.news

import net.trilleo.mc.plugins.tritown.config.NewsSettings
import net.trilleo.mc.plugins.tritown.news.storage.NewsStorage
import net.trilleo.mc.plugins.tritown.news.storage.NewsStorageException
import org.bukkit.entity.Player
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

/**
 * Every news post, drafts included, and the only thing that reads or writes one.
 *
 * Every change is a change to what a post says, which an administrator typed
 * and would have to type again, so each one is written straight away rather
 * than flushed later. Posts change a few times a week; the write is cheap.
 */
object NewsManager {

    private val posts = ConcurrentHashMap<String, NewsPost>()

    @Volatile
    private var store: NewsStorage? = null
    private var logger: Logger? = null

    /** Whether the posts loaded. When they did not, the feature stays off rather than risk writing over them. */
    @Volatile
    var isReady: Boolean = false
        private set

    /** Whether players can read the news right now. */
    val isAvailable: Boolean
        get() = isReady && NewsSettings.isLoaded && NewsSettings.snapshot.enabled

    fun start(store: NewsStorage, logger: Logger) {
        this.store = store
        this.logger = logger
        posts.clear()
        try {
            store.loadAll().forEach { posts[it.id] = it }
            isReady = true
        } catch (e: NewsStorageException) {
            logger.severe("The news could not be loaded, so it is switched off until the file is fixed: ${e.message}")
            isReady = false
        }
    }

    fun get(id: String): NewsPost? = posts[id]

    /** Published posts as players see them: pinned ones first, then the newest. */
    fun published(): List<NewsPost> = posts.values
        .filter { it.isPublished }
        .sortedWith(compareByDescending<NewsPost> { it.pinned }.thenByDescending { it.publishedAt ?: 0L })

    /** Drafts, newest first, then everything published — the order an editor wants them in. */
    fun forEditors(): List<NewsPost> =
        posts.values.filter { !it.isPublished }.sortedByDescending { it.createdAt } + published()

    fun create(author: Player, title: String): NewsPost {
        val post = NewsPost(
            id = NewsIds.next(posts::containsKey),
            title = LocalizedText(title),
            createdAt = System.currentTimeMillis(),
            authorUuid = author.uniqueId.toString(),
            authorName = author.name,
        )
        posts[post.id] = post
        save()
        return post
    }

    fun delete(post: NewsPost) {
        posts.remove(post.id)
        save()
    }

    /**
     * Makes [post] visible to everyone, as new: it is dated now, so it is
     * unread for every player again even if it had been published before.
     */
    fun publish(post: NewsPost, announce: Boolean, publisher: Player) {
        post.status = PostStatus.PUBLISHED
        post.publishedAt = System.currentTimeMillis()
        post.editedAt = null
        save()

        // The publisher has just written it, so it is not news to them.
        NewsReadState.markRead(publisher, post)
        if (announce) NewsNotifier.announce(post)
    }

    fun unpublish(post: NewsPost) {
        post.status = PostStatus.DRAFT
        save()
    }

    /**
     * Records a change to [post] and writes it.
     *
     * A published post is dated as edited, so readers can tell it changed
     * after they read it, but it is not made unread again: correcting a typo
     * should not call everyone back.
     */
    fun changed(post: NewsPost) {
        if (post.isPublished) post.editedAt = System.currentTimeMillis()
        save()
    }

    fun save() {
        if (!isReady) return
        store?.saveAll(posts.values.sortedBy { it.createdAt })
    }
}
