package com.discovery.viewmodel

import android.app.Application
import android.view.ViewGroup
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.discovery.Constants
import com.discovery.parser.model.ForumDisplayResult
import com.discovery.parser.model.ParseStatus
import com.discovery.parser.model.ThreadListItem
import com.discovery.parser.network.DiscuzClient
import com.discovery.parser.service.ForumParser
import com.discovery.util.WebViewFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 列表页 ViewModel
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val parser = ForumParser()

    // UI 状态
    private val _threads = MutableLiveData<List<ThreadListItem>>(emptyList())
    val threads: LiveData<List<ThreadListItem>> = _threads

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _isRefreshing = MutableLiveData(false)
    val isRefreshing: LiveData<Boolean> = _isRefreshing

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    private val _authRequired = MutableLiveData<ParseStatus?>(null)
    val authRequired: LiveData<ParseStatus?> = _authRequired

    private val _needWebViewFetch = MutableLiveData<WebViewFetchRequest?>(null)
    val needWebViewFetch: LiveData<WebViewFetchRequest?> = _needWebViewFetch

    // 分页状态
    var currentPage = 1
        private set
    var forumMaxPage = 1
        private set
    var nextPageUrl: String? = null
        private set

    private val allThreads = mutableListOf<ThreadListItem>()

    fun loadFirstPage() {
        if (_isLoading.value == true) return
        allThreads.clear()
        currentPage = 1
        forumMaxPage = 1
        nextPageUrl = null
        _isRefreshing.value = true
        loadUrl(Constants.buildForumDisplayUrl(page = 1), append = false)
    }

    fun loadNextPage() {
        if (_isLoading.value == true) return
        if (!hasMore()) return
        val url = nextPageUrl?.let { resolveUrl(it) } ?: return
        loadUrl(url, append = true)
    }

    fun hasMore(): Boolean = !nextPageUrl.isNullOrBlank() && currentPage < forumMaxPage

    private fun loadUrl(url: String, append: Boolean) {
        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                DiscuzClient.fetch(url)
            }

            var waitingWebView = false

            when (result.status) {
                ParseStatus.SUCCESS -> {
                    val parseResult = withContext(Dispatchers.Default) {
                        parser.parseForumDisplay(result.data!!)
                    }
                    if (parseResult.status == ParseStatus.SUCCESS) {
                        val data = parseResult.data!!
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
                    } else {
                        _error.value = "Parse Error: ${parseResult.errorMessage}"
                    }
                }
                ParseStatus.NEED_LOGIN -> {
                    _authRequired.value = ParseStatus.NEED_LOGIN
                }
                ParseStatus.CF_CHALLENGE -> {
                    _needWebViewFetch.value = WebViewFetchRequest(url, append)
                    waitingWebView = true
                }
                else -> {
                    _error.value = "Status: ${result.status}"
                }
            }

            if (!waitingWebView) {
                _isLoading.value = false
                _isRefreshing.value = false
            }
        }
    }

    fun handleWebViewResult(html: String, append: Boolean) {
        viewModelScope.launch {
            val parseResult = withContext(Dispatchers.Default) {
                parser.parseForumDisplay(html)
            }
            if (parseResult.status == ParseStatus.SUCCESS) {
                val data = parseResult.data!!
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
            } else {
                _error.value = "Parse Error: ${parseResult.errorMessage}"
            }
            _isLoading.value = false
            _isRefreshing.value = false
            _needWebViewFetch.value = null
        }
    }

    fun clearWebViewRequest() {
        _needWebViewFetch.value = null
        _isLoading.value = false
        _isRefreshing.value = false
    }

    fun clearAuthRequired() {
        _authRequired.value = null
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
