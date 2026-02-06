package com.discovery.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
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
 * 详情页 ViewModel
 */
class DetailViewModel(application: Application) : AndroidViewModel(application) {

    private val parser = ForumParser()

    // UI 状态
    private val _posts = MutableLiveData<List<PostItem>>(emptyList())
    val posts: LiveData<List<PostItem>> = _posts

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _isRefreshing = MutableLiveData(false)
    val isRefreshing: LiveData<Boolean> = _isRefreshing

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    private val _authRequired = MutableLiveData<ParseStatus?>(null)
    val authRequired: LiveData<ParseStatus?> = _authRequired

    private val _needWebViewFetch = MutableLiveData<String?>(null)
    val needWebViewFetch: LiveData<String?> = _needWebViewFetch

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

    fun loadFirstPage() {
        if (_isLoading.value == true) return
        allPosts.clear()
        currentPage = 1
        threadAuthor = null
        _isRefreshing.value = true
        loadPage(1, append = false)
    }

    fun loadNextPage() {
        if (_isLoading.value == true) return
        if (currentPage >= maxPage) return
        loadPage(currentPage + 1, append = true)
    }

    fun hasMore(): Boolean = currentPage < maxPage

    private fun loadPage(page: Int, append: Boolean) {
        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            val url = Constants.buildViewThreadUrl(tid, page)
            when (val loadResult = withContext(Dispatchers.IO) { loadViewThread(url) }) {
                is LoadResult.Success -> applyViewThread(loadResult.data, append)
                is LoadResult.NeedLogin -> _authRequired.value = ParseStatus.NEED_LOGIN
                is LoadResult.NeedWebViewFetch -> _needWebViewFetch.value = loadResult.url
                is LoadResult.Error -> _error.value = loadResult.message
            }

            _isLoading.value = false
            _isRefreshing.value = false
        }
    }

    fun handleWebViewResult(html: String, append: Boolean) {
        val parseResult = parser.parseViewThread(html)
        if (parseResult.status == ParseStatus.SUCCESS) {
            applyViewThread(parseResult.data!!, append)
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

    fun clearWebViewRequest() {
        _needWebViewFetch.value = null
    }

    fun clearAuthRequired() {
        _authRequired.value = null
    }

    private fun loadViewThread(url: String): LoadResult<ViewThreadResult> {
        PageCache.getViewThread(url)?.let { cached ->
            return LoadResult.Success(cached)
        }
        val result = DiscuzClient.fetch(url)
        return when (result.status) {
            ParseStatus.SUCCESS -> {
                val parseResult = parser.parseViewThread(result.data!!)
                if (parseResult.status == ParseStatus.SUCCESS) {
                    PageCache.putViewThread(url, parseResult.data!!)
                    LoadResult.Success(parseResult.data!!)
                } else {
                    LoadResult.Error("Parse Error: ${parseResult.errorMessage}", parseResult.status)
                }
            }
            ParseStatus.NEED_LOGIN -> LoadResult.NeedLogin
            ParseStatus.CF_CHALLENGE -> LoadResult.NeedWebViewFetch(url)
            else -> LoadResult.Error("Status: ${result.status}", result.status)
        }
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
