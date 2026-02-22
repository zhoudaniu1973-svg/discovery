package com.discovery.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.discovery.parser.model.ParseStatus

/**
 * 论坛页面 ViewModel 基类
 *
 * 统一管理以下通用状态，避免 MainViewModel 和 DetailViewModel 重复声明：
 * - 加载/刷新状态
 * - 错误信息
 * - 登录鉴权
 * - WebView 下载回退
 */
abstract class BaseForumViewModel(application: Application) : AndroidViewModel(application) {

    // ---- 通用 UI 状态 ----

    protected val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    protected val _isRefreshing = MutableLiveData(false)
    val isRefreshing: LiveData<Boolean> = _isRefreshing

    protected val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    protected val _authRequired = MutableLiveData<ParseStatus?>(null)
    val authRequired: LiveData<ParseStatus?> = _authRequired

    protected val _needWebViewFetch = MutableLiveData<String?>(null)
    val needWebViewFetch: LiveData<String?> = _needWebViewFetch

    // ---- 通用操作方法 ----

    /**
     * 清除 WebView 抓取请求，避免重复触发
     */
    fun clearWebViewRequest() {
        _needWebViewFetch.value = null
    }

    /**
     * 清除鉴权跳转信号，防止重复弹出登录页
     */
    fun clearAuthRequired() {
        _authRequired.value = null
    }

    /**
     * WebView 成功抓取到 HTML 后，由 Activity 回调此方法进行解析和展示
     * 子类必须实现：对应自己的数据类型（线程列表 or 帖子列表）
     *
     * @param html    WebView 抓取到的完整 HTML
     * @param append  是否追加（翻页）而非替换
     */
    abstract fun handleWebViewResult(html: String, append: Boolean)
}
