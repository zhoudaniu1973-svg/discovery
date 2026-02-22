package com.discovery

import android.content.Intent
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.discovery.parser.model.ParseStatus
import com.discovery.util.WebViewFetcher
import com.discovery.viewmodel.BaseForumViewModel
import com.facebook.shimmer.ShimmerFrameLayout

/**
 * 论坛页面 Activity 基类
 *
 * 统一封装以下重复逻辑，避免 MainActivity 和 DetailActivity 各自实现一遍：
 * - WebView Cloudflare 回退抓取
 * - 骨架屏（Shimmer）显示/隐藏
 * - 鉴权重定向（跳转登录页）
 * - WebView 请求状态的 Observer 绑定
 *
 * 子类只需：
 * 1. 调用 observeBaseViewModel(vm) 绑定通用 Observer
 * 2. 使用 showShimmer()/hideShimmer() 控制加载动画
 */
abstract class BaseForumActivity : AppCompatActivity() {

    // 子类需提供这两个 View 的引用，基类才能控制它们
    protected abstract val tvStatus: TextView
    protected abstract val shimmerLayout: ShimmerFrameLayout
    protected abstract val mainContentView: View  // 列表 RecyclerView 等主内容区

    private var webViewFetcher: WebViewFetcher? = null

    /**
     * 绑定基类负责处理的公共 Observer：鉴权 + WebView 回退请求
     *
     * 子类在 observeViewModel() 中调用此方法，再附加各自的业务 Observer
     */
    protected fun observeBaseViewModel(viewModel: BaseForumViewModel) {
        // 鉴权：需要登录时跳转登录页
        viewModel.authRequired.observe(this) { status ->
            if (status == ParseStatus.NEED_LOGIN) {
                viewModel.clearAuthRequired()
                navigateToLogin(status.name)
            }
        }

        // CF 拦截回退：触发 WebView 抓取
        viewModel.needWebViewFetch.observe(this) { url ->
            if (url != null) {
                fetchViaWebView(viewModel, url)
            }
        }

        // 错误信息展示
        viewModel.error.observe(this) { error ->
            if (error != null) {
                tvStatus.text = error
                tvStatus.visibility = View.VISIBLE
                hideShimmer()
            } else {
                tvStatus.visibility = View.GONE
            }
        }

        // WebView 空闲预热：主线程空闲时提前创建 WebView 实例
        // 避免 CF 回退触发时等待 500-800ms 的冷启动
        Looper.myQueue().addIdleHandler {
            val container = findViewById<ViewGroup>(android.R.id.content)
            val fetcher = webViewFetcher ?: WebViewFetcher(this, container).also {
                webViewFetcher = it
            }
            fetcher.preWarm()
            false  // 返回 false 表示只执行一次
        }
    }

    /**
     * 使用隐藏 WebView 抓取页面（Cloudflare 回退方案）
     */
    private fun fetchViaWebView(viewModel: BaseForumViewModel, url: String) {
        val container = findViewById<ViewGroup>(android.R.id.content)
        val fetcher = webViewFetcher ?: WebViewFetcher(this, container).also {
            webViewFetcher = it
        }

        fetcher.fetch(url, object : WebViewFetcher.Callback {
            override fun onSuccess(html: String) {
                viewModel.handleWebViewResult(html, append = false)
            }

            override fun onStatusDetected(status: ParseStatus) {
                if (status == ParseStatus.NEED_LOGIN) {
                    viewModel.clearWebViewRequest()
                    navigateToLogin(status.name)
                } else {
                    tvStatus.text = "Status: $status"
                    tvStatus.visibility = View.VISIBLE
                    viewModel.clearWebViewRequest()
                }
            }

            override fun onError(message: String) {
                tvStatus.text = message
                tvStatus.visibility = View.VISIBLE
                viewModel.clearWebViewRequest()
            }
        })
    }

    /** 跳转到登录页 */
    private fun navigateToLogin(reason: String) {
        val intent = Intent(this, LoginActivity::class.java)
        intent.putExtra(LoginActivity.EXTRA_REASON, reason)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    /** 显示骨架屏，隐藏主内容区 */
    protected fun showShimmer() {
        shimmerLayout.visibility = View.VISIBLE
        shimmerLayout.startShimmer()
        mainContentView.visibility = View.GONE
    }

    /** 隐藏骨架屏，显示主内容区 */
    protected fun hideShimmer() {
        shimmerLayout.stopShimmer()
        shimmerLayout.visibility = View.GONE
        mainContentView.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        super.onDestroy()
        webViewFetcher?.destroy()
    }
}
