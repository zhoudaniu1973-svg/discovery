package com.discovery

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.discovery.parser.model.ParseStatus
import com.discovery.parser.network.CookieStore
import com.discovery.ui.ThreadListAdapter
import com.discovery.util.setupActionBarAutoHide
import com.discovery.util.WebViewFetcher
import com.discovery.viewmodel.MainViewModel
import com.facebook.shimmer.ShimmerFrameLayout

/**
 * 列表页 Activity (ViewModel 重构版)
 */
class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    private lateinit var tvStatus: TextView
    private lateinit var adapter: ThreadListAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var shimmerLayout: ShimmerFrameLayout
    private lateinit var rvThreads: RecyclerView

    private var webViewFetcher: WebViewFetcher? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        CookieStore.load(this)

        // 初始化视图
        tvStatus = findViewById(R.id.tvStatus)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        shimmerLayout = findViewById(R.id.shimmerLayout)
        rvThreads = findViewById(R.id.rvThreads)

        // RecyclerView 配置
        val layoutManager = LinearLayoutManager(this)
        rvThreads.layoutManager = layoutManager
        adapter = ThreadListAdapter(mutableListOf()) { item ->
            val intent = Intent(this, DetailActivity::class.java)
            intent.putExtra(DetailActivity.EXTRA_TID, item.tid)
            intent.putExtra(DetailActivity.EXTRA_TITLE, item.title)
            intent.putExtra(DetailActivity.EXTRA_MAX_PAGE, item.threadMaxPage)
            startActivity(intent)
        }
        rvThreads.adapter = adapter
        setupActionBarAutoHide(rvThreads)

        // 下拉刷新
        swipeRefresh.setOnRefreshListener {
            viewModel.loadFirstPage()
        }

        // 上拉加载更多
        rvThreads.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                if (viewModel.isLoading.value == true) return
                if (!viewModel.hasMore()) return
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                if (lastVisible >= adapter.itemCount - 3) {
                    viewModel.loadNextPage()
                }
            }
        })

        // 观察数据
        observeViewModel()

        // 双击返回退出
        setupBackPressHandler()

        // 首次加载
        if (viewModel.threads.value.isNullOrEmpty()) {
            showShimmer()
            viewModel.loadFirstPage()
        }
    }

    private fun observeViewModel() {
        viewModel.threads.observe(this) { threads ->
            adapter.replaceAll(threads)
            hideShimmer()
        }

        viewModel.isRefreshing.observe(this) { isRefreshing ->
            swipeRefresh.isRefreshing = isRefreshing
        }

        viewModel.error.observe(this) { error ->
            if (error != null) {
                tvStatus.text = error
                tvStatus.visibility = View.VISIBLE
                hideShimmer()
            } else {
                tvStatus.visibility = View.GONE
            }
        }

        viewModel.authRequired.observe(this) { status ->
            if (status == ParseStatus.NEED_LOGIN) {
                viewModel.clearAuthRequired()
                val intent = Intent(this, LoginActivity::class.java)
                intent.putExtra(LoginActivity.EXTRA_REASON, status.name)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }
        }

        viewModel.needWebViewFetch.observe(this) { url ->
            if (url != null) {
                fetchViaWebView(url)
            }
        }
    }

    private fun fetchViaWebView(url: String) {
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
                    val intent = Intent(this@MainActivity, LoginActivity::class.java)
                    intent.putExtra(LoginActivity.EXTRA_REASON, status.name)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
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

    private fun showShimmer() {
        shimmerLayout.visibility = View.VISIBLE
        shimmerLayout.startShimmer()
        rvThreads.visibility = View.GONE
    }

    private fun hideShimmer() {
        shimmerLayout.stopShimmer()
        shimmerLayout.visibility = View.GONE
        rvThreads.visibility = View.VISIBLE
    }

    private fun setupBackPressHandler() {
        var lastBackAt = 0L
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val now = System.currentTimeMillis()
                if (now - lastBackAt < 1500) {
                    finish()
                } else {
                    lastBackAt = now
                    Toast.makeText(this@MainActivity, "再按一次退出", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        webViewFetcher?.destroy()
    }
}
