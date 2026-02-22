package com.discovery.viewmodel

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.discovery.Constants
import com.discovery.parser.model.ParseStatus
import com.discovery.parser.model.PostItem
import com.discovery.parser.model.ViewThreadResult
import com.discovery.parser.network.DiscuzClient
import com.discovery.parser.service.ForumParser
import com.discovery.util.PageCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 帖子详情页 ViewModel
 *
 * 继承 BaseForumViewModel，仅保留与"帖子列表"相关的业务逻辑：
 * - 分页加载帖子内容
 * - 只看楼主过滤功能
 * - 缓存策略
 */
class DetailViewModel(application: Application) : BaseForumViewModel(application) {

    private val parser = ForumParser()

    private val _posts = MutableLiveData<List<PostItem>>(emptyList())
    val posts: LiveData<List<PostItem>> = _posts

    // 分页状态
    var currentPage = 1
        private set
    var maxPage = 1
        private set
    var tid: String = "0"
        private set

    // 只看楼主
    private var onlyAuthorEnabled = false
    private var threadAuthor: String? = null
    private val allPosts = mutableListOf<PostItem>()

    fun init(threadId: String, initialMaxPage: Int) {
        tid = threadId
        maxPage = initialMaxPage
    }

    fun loadFirstPage(forceRefresh: Boolean = false) {
        if (_isLoading.value == true) return
        allPosts.clear()
        currentPage = 1
        threadAuthor = null
        _isRefreshing.value = true
        loadPage(1, append = false, forceRefresh = forceRefresh)
    }

    fun loadNextPage() {
        if (_isLoading.value == true) return
        if (currentPage >= maxPage) return
        loadPage(currentPage + 1, append = true, forceRefresh = true)
    }

    fun hasMore(): Boolean = currentPage < maxPage

    private fun loadPage(page: Int, append: Boolean, forceRefresh: Boolean = false) {
        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            val url = Constants.buildViewThreadUrl(tid, page)
            var servedCached = false
            var cachedData: ViewThreadResult? = null

            // 非追加模式：先从缓存命中展示，再决定是否后台刷新
            if (!append) {
                val cachedEntry = PageCache.getViewThreadEntry(url)
                if (cachedEntry != null) {
                    cachedData = cachedEntry.data
                    applyViewThread(cachedEntry.data, append = false)
                    servedCached = true
                    if (!forceRefresh && !PageCache.isViewThreadStale(cachedEntry)) {
                        // 缓存新鲜，直接返回
                        _isLoading.value = false
                        _isRefreshing.value = false
                        return@launch
                    }
                }
            }

            // 尝试从原始 HTML 缓存获取（跳过网络请求，直接走解析）
            var html: String? = null
            val rawHtmlEntry = PageCache.getRawHtmlEntry(url)
            if (rawHtmlEntry != null && !forceRefresh && !PageCache.isRawHtmlStale(rawHtmlEntry)) {
                html = rawHtmlEntry.data
            }

            // HTML 缓存未命中，从网络获取
            if (html == null) {
                val loadResult = withContext(Dispatchers.IO) {
                    DiscuzClient.fetch(url)
                }

                when {
                    loadResult.status == ParseStatus.SUCCESS && loadResult.data != null -> {
                        html = loadResult.data
                        // 缓存原始 HTML，供下次快速加载
                        PageCache.putRawHtml(url, html)
                    }
                    loadResult.status == ParseStatus.NEED_LOGIN -> {
                        if (!servedCached) _authRequired.value = ParseStatus.NEED_LOGIN
                        _isLoading.value = false
                        _isRefreshing.value = false
                        return@launch
                    }
                    loadResult.status == ParseStatus.CF_CHALLENGE -> {
                        if (!servedCached) _needWebViewFetch.value = url
                        _isLoading.value = false
                        _isRefreshing.value = false
                        return@launch
                    }
                    else -> {
                        if (!servedCached) _error.value = "Status: ${loadResult.status}"
                        _isLoading.value = false
                        _isRefreshing.value = false
                        return@launch
                    }
                }
            }

            // 两阶段加载（无论 HTML 来自缓存还是网络）
            // 阶段 1: 快速解析前 3 条帖子（纯文本，无图片），立即推送首屏
            if (!servedCached) {
                val quickResult = withContext(Dispatchers.Default) {
                    parser.parseViewThreadQuick(html, limit = 3)
                }
                if (quickResult.status == ParseStatus.SUCCESS && quickResult.data != null) {
                    applyViewThread(quickResult.data, append)
                }
            }

            // 阶段 2: 完整解析所有帖子（含 HTML 富文本和图片）
            val fullResult = withContext(Dispatchers.Default) {
                parser.parseViewThread(html)
            }
            if (fullResult.status == ParseStatus.SUCCESS && fullResult.data != null) {
                PageCache.putViewThread(url, fullResult.data)
                if (!servedCached || fullResult.data != cachedData) {
                    applyViewThread(fullResult.data, append)
                }
            } else if (!servedCached) {
                _error.value = "Parse Error: ${fullResult.errorMessage}"
            }

            _isLoading.value = false
            _isRefreshing.value = false
        }
    }

    /** WebView 回退抓取成功后，由 Activity 回调此方法解析并展示 */
    override fun handleWebViewResult(html: String, append: Boolean) {
        val parseResult = parser.parseViewThread(html)
        if (parseResult.status == ParseStatus.SUCCESS) {
            val data = parseResult.data
            if (data != null) {
                applyViewThread(data, append)
            } else {
                _error.value = "Parse Error: Empty parse data"
            }
        } else {
            _error.value = "Parse Error: ${parseResult.errorMessage}"
        }
        _isLoading.value = false
        _isRefreshing.value = false
        _needWebViewFetch.value = null
    }

    fun toggleOnlyAuthor(): Boolean {
        onlyAuthorEnabled = !onlyAuthorEnabled
        _posts.value = filterPosts(allPosts)
        return onlyAuthorEnabled
    }

    fun isOnlyAuthorEnabled(): Boolean = onlyAuthorEnabled

    private fun filterPosts(source: List<PostItem>): List<PostItem> {
        val author = threadAuthor
        if (!onlyAuthorEnabled || author.isNullOrBlank()) return source
        return source.filter { it.authorName == author }
    }


    private fun applyViewThread(data: ViewThreadResult, append: Boolean) {
        currentPage = data.currentPage
        maxPage = maxOf(maxPage, data.maxPage, currentPage)

        if (threadAuthor.isNullOrBlank()) {
            threadAuthor = data.posts.firstOrNull { it.authorName.isNotBlank() }?.authorName
        }

        if (append) {
            allPosts.addAll(data.posts)
        } else {
            allPosts.clear()
            allPosts.addAll(data.posts)
        }
        _posts.value = filterPosts(allPosts)
        _error.value = null
    }
}
