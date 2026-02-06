package com.discovery

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.discovery.parser.model.ParseStatus
import com.discovery.parser.network.CookieStore
import com.discovery.ui.PostListAdapter
import com.discovery.util.setupActionBarAutoHide
import com.discovery.util.setupPaging
import com.discovery.util.WebViewFetcher
import com.discovery.viewmodel.DetailViewModel
import com.facebook.shimmer.ShimmerFrameLayout

/**
 * 详情页 Activity (ViewModel 重构版)
 */
class DetailActivity : AppCompatActivity() {

    private val viewModel: DetailViewModel by viewModels()

    private lateinit var tvStatus: TextView
    private lateinit var adapter: PostListAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var shimmerLayout: ShimmerFrameLayout
    private lateinit var rvPosts: RecyclerView

    private var webViewFetcher: WebViewFetcher? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        CookieStore.load(this)

        // 初始化视图
        tvStatus = findViewById(R.id.tvStatus)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        shimmerLayout = findViewById(R.id.shimmerLayout)
        rvPosts = findViewById(R.id.rvPosts)

        // 参数
        val tid = intent.getStringExtra(EXTRA_TID) ?: "0"
        val maxPage = intent.getIntExtra(EXTRA_MAX_PAGE, 1)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Thread"
        supportActionBar?.title = title

        viewModel.init(tid, maxPage)

        // RecyclerView 配置
        val layoutManager = LinearLayoutManager(this)
        rvPosts.layoutManager = layoutManager
        adapter = PostListAdapter(
            onReplyClick = {
                Toast.makeText(this, "暂不支持回复", Toast.LENGTH_SHORT).show()
            },
            onOnlyAuthorClick = {
                val enabled = viewModel.toggleOnlyAuthor()
                adapter.setOnlyAuthorEnabled(enabled)
                Toast.makeText(this, if (enabled) "只看楼主" else "显示全部", Toast.LENGTH_SHORT).show()
            }
        )
        rvPosts.adapter = adapter
        setupActionBarAutoHide(rvPosts)

        // 下拉刷新
        swipeRefresh.setOnRefreshListener {
            viewModel.loadFirstPage()
        }

        // 上拉加载更多
        rvPosts.setupPaging(
            layoutManager = layoutManager,
            preloadThreshold = 3,
            canLoadMore = { viewModel.hasMore() },
            isLoading = { viewModel.isLoading.value == true },
            onLoadMore = { viewModel.loadNextPage() }
        )

        // 观察数据
        observeViewModel()

        // 首次加载
        if (viewModel.posts.value.isNullOrEmpty()) {
            showShimmer()
            viewModel.loadFirstPage()
        }
    }

    private fun observeViewModel() {
        viewModel.posts.observe(this) { posts ->
            adapter.replaceAll(posts)
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
                    val intent = Intent(this@DetailActivity, LoginActivity::class.java)
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
        rvPosts.visibility = View.GONE
    }

    private fun hideShimmer() {
        shimmerLayout.stopShimmer()
        shimmerLayout.visibility = View.GONE
        rvPosts.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        super.onDestroy()
        webViewFetcher?.destroy()
    }

    companion object {
        const val EXTRA_TID = "extra_tid"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_MAX_PAGE = "extra_max_page"
    }
}
