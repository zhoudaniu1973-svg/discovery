package com.discovery.util

import android.util.LruCache
import com.discovery.parser.model.ForumDisplayResult
import com.discovery.parser.model.ViewThreadResult

object PageCache {
    private const val FORUM_CACHE_SIZE = 20
    private const val THREAD_CACHE_SIZE = 20
    private const val RAW_HTML_CACHE_SIZE = 10  // 原始 HTML 缓存（体积较大，数量限制更严格）
    private const val FORUM_STALE_AFTER_MS = 45_000L
    private const val THREAD_STALE_AFTER_MS = 60_000L
    private const val RAW_HTML_STALE_AFTER_MS = 60_000L

    data class CacheEntry<T>(
        val data: T,
        val cachedAtMs: Long
    )

    private val forumCache = LruCache<String, CacheEntry<ForumDisplayResult>>(FORUM_CACHE_SIZE)
    private val threadCache = LruCache<String, CacheEntry<ViewThreadResult>>(THREAD_CACHE_SIZE)
    // 原始 HTML 缓存：用于两阶段加载时跳过网络请求
    private val rawHtmlCache = LruCache<String, CacheEntry<String>>(RAW_HTML_CACHE_SIZE)

    fun getForumDisplay(url: String): ForumDisplayResult? = forumCache.get(url)?.data

    fun getForumDisplayEntry(url: String): CacheEntry<ForumDisplayResult>? = forumCache.get(url)

    fun putForumDisplay(url: String, result: ForumDisplayResult) {
        forumCache.put(url, CacheEntry(result, System.currentTimeMillis()))
    }

    fun isForumDisplayStale(entry: CacheEntry<ForumDisplayResult>): Boolean {
        return isStale(entry.cachedAtMs, FORUM_STALE_AFTER_MS)
    }

    fun getViewThread(url: String): ViewThreadResult? = threadCache.get(url)?.data

    fun getViewThreadEntry(url: String): CacheEntry<ViewThreadResult>? = threadCache.get(url)

    fun putViewThread(url: String, result: ViewThreadResult) {
        threadCache.put(url, CacheEntry(result, System.currentTimeMillis()))
    }

    fun isViewThreadStale(entry: CacheEntry<ViewThreadResult>): Boolean {
        return isStale(entry.cachedAtMs, THREAD_STALE_AFTER_MS)
    }

    private fun isStale(cachedAtMs: Long, staleAfterMs: Long): Boolean {
        val age = System.currentTimeMillis() - cachedAtMs
        return age >= staleAfterMs
    }

    // ---- 原始 HTML 缓存 ----

    fun getRawHtml(url: String): String? = rawHtmlCache.get(url)?.data

    fun getRawHtmlEntry(url: String): CacheEntry<String>? = rawHtmlCache.get(url)

    fun putRawHtml(url: String, html: String) {
        rawHtmlCache.put(url, CacheEntry(html, System.currentTimeMillis()))
    }

    fun isRawHtmlStale(entry: CacheEntry<String>): Boolean {
        return isStale(entry.cachedAtMs, RAW_HTML_STALE_AFTER_MS)
    }
}
