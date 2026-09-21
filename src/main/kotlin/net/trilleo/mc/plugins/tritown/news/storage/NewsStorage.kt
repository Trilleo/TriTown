package net.trilleo.mc.plugins.tritown.news.storage

import net.trilleo.mc.plugins.tritown.news.NewsPost

/** Where news posts are kept between restarts. */
interface NewsStorage {

    /**
     * Every stored post, drafts included.
     *
     * @throws NewsStorageException when the data cannot be read at all, which
     *   stops the feature rather than letting an empty list overwrite it
     */
    fun loadAll(): List<NewsPost>

    /** Replaces everything on disk with [posts]. */
    fun saveAll(posts: List<NewsPost>)
}

/** Raised when stored news cannot be read, and must not be silently replaced. */
class NewsStorageException(message: String) : Exception(message)
