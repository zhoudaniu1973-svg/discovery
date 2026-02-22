package com.discovery.viewmodel

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.discovery.Constants
import com.discovery.parser.model.ForumDisplayResult
import com.discovery.parser.model.ParseStatus
import com.discovery.parser.model.ThreadListItem
import com.discovery.parser.network.DiscuzClient
import com.discovery.parser.service.ForumParser
import com.discovery.util.PageCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 论坛列表页 ViewModel
 *
 * 继承 BaseForumViewModel，仅保留与"线程列表"相关的业务逻辑：
 * - 分页加载帖子列表
 * - 缓存策略（命中缓存先展示，后台静默刷新）
 * - 翻页状态维护
 */
class MainViewModel(application: Application) : BaseForumViewModel(application) {

    private val parser = ForumParser()

    private val _threads = MutableLiveData<List<ThreadListItem>>(emptyList())
    val threads: LiveData<List<ThreadListItem>> = _threads

    // 分页状态
    var currentPage = 1
        private set
    var forumMaxPage = 1
        private set
    var nextPageUrl: String? = null
        private set

    private val allThreads = mutableListOf<ThreadListItem>()

    fun loadFirstPage(forceRefresh: Boolean = false) {
        if (_isLoading.value == true) return
        allThreads.clear()
        currentPage = 1
        forumMaxPage = 1
        nextPageUrl = null
        _isRefreshing.value = true
        loadUrl(Constants.buildForumDisplayUrl(page = 1), append = false, forceRefresh = forceRefresh)
    }

    fun loadNextPage() {
        if (_isLoading.value == true) return
        if (!hasMore()) return
        val url = nextPageUrl?.let { resolveUrl(it) } ?: return
        loadUrl(url, append = true, forceRefresh = true)
    }

    fun hasMore(): Boolean = !nextPageUrl.isNullOrBlank() && currentPage < forumMaxPage

    private fun loadUrl(url: String, append: Boolean, forceRefresh: Boolean = false) {
        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            var servedCached = false
            var cachedData: ForumDisplayResult? = null

            // 非追加模式：先从缓存命中展示，再决定是否后台刷新
            if (!append) {
                val cachedEntry = PageCache.getForumDisplayEntry(url)
                if (cachedEntry != null) {
                    cachedData = cachedEntry.data
                    applyForumDisplay(cachedEntry.data, append = false)
                    servedCached = true
                    if (!forceRefresh && !PageCache.isForumDisplayStale(cachedEntry)) {
                        // 缓存新鲜，直接返回
                        _isLoading.value = false
                        _isRefreshing.value = false
                        return@launch
                    }
                }
            }

            // 后台拉取最新数据
            when (val loadResult = withContext(Dispatchers.IO) { loadForumDisplayFresh(url) }) {
                is LoadResult.Success -> {
                    if (!servedCached || loadResult.data != cachedData) {
                        applyForumDisplay(loadResult.data, append)
                    }
                }
                is LoadResult.NeedLogin -> if (!servedCached) {
                    _authRequired.value = ParseStatus.NEED_LOGIN
                }
                is LoadResult.NeedWebViewFetch -> if (!servedCached) {
                    _needWebViewFetch.value = loadResult.url
                }
                is LoadResult.Error -> if (!servedCached) {
                    _error.value = loadResult.message
                }
            }
            _isLoading.value = false
            _isRefreshing.value = false
        }
    }

    /** WebView 回退抓取成功后，由 Activity 回调此方法解析并展示 */
    override fun handleWebViewResult(html: String, append: Boolean) {
        val parseResult = parser.parseForumDisplay(html)
        if (parseResult.status == ParseStatus.SUCCESS) {
            val data = parseResult.data
            if (data != null) {
                applyForumDisplay(data, append)
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

    private fun loadForumDisplayFresh(url: String): LoadResult<ForumDisplayResult> {
        val result = DiscuzClient.fetch(url)
        return when (result.status) {
            ParseStatus.SUCCESS -> {
                val parseResult = parser.parseForumDisplay(result.data!!)
                if (parseResult.status == ParseStatus.SUCCESS) {
                    val data = parseResult.data
                    if (data != null) {
                        PageCache.putForumDisplay(url, data)
                        LoadResult.Success(data)
                    } else {
                        LoadResult.Error("Parse Error: Empty parse data", parseResult.status)
                    }
                } else {
                    LoadResult.Error("Parse Error: ${parseResult.errorMessage}", parseResult.status)
                }
            }
            ParseStatus.NEED_LOGIN -> LoadResult.NeedLogin
            ParseStatus.CF_CHALLENGE -> LoadResult.NeedWebViewFetch(url)
            else -> LoadResult.Error("Status: ${result.status}", result.status)
        }
    }

    private fun applyForumDisplay(data: ForumDisplayResult, append: Boolean) {
        if (append) {
            allThreads.addAll(data.threads)
        } else {
            allThreads.clear()
            allThreads.addAll(data.threads)
        }
        _threads.value = allThreads.toList()
        currentPage = data.currentPage
        forumMaxPage = data.forumMaxPage
        nextPageUrl = data.nextPageUrl
        _error.value = null
    }

    private fun resolveUrl(href: String): String {
        val trimmed = href.trim()
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            Constants.BASE_FORUM_URL + trimmed.trimStart('/')
        }
    }
}
