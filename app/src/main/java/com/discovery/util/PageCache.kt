package com.discovery.util

import android.util.LruCache
import com.discovery.parser.model.ForumDisplayResult
import com.discovery.parser.model.ViewThreadResult

object PageCache {
    private const val FORUM_CACHE_SIZE = 20
    private const val THREAD_CACHE_SIZE = 20

    private val forumCache = LruCache<String, ForumDisplayResult>(FORUM_CACHE_SIZE)
    private val threadCache = LruCache<String, ViewThreadResult>(THREAD_CACHE_SIZE)

    fun getForumDisplay(url: String): ForumDisplayResult? = forumCache.get(url)

    fun putForumDisplay(url: String, result: ForumDisplayResult) {
        forumCache.put(url, result)
    }

    fun getViewThread(url: String): ViewThreadResult? = threadCache.get(url)

    fun putViewThread(url: String, result: ViewThreadResult) {
        threadCache.put(url, result)
    }
}