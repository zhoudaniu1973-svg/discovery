package com.discovery

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.discovery.parser.network.CookieStore
import com.discovery.ui.ThreadListAdapter
import com.discovery.util.setupActionBarAutoHide
import com.discovery.util.setupPaging
import com.discovery.viewmodel.MainViewModel
import com.facebook.shimmer.ShimmerFrameLayout

/**
 * 论坛帖子列表页
 *
 * 继承 BaseForumActivity，通用的 WebView 回退、骨架屏、
 * 鉴权跳转已在基类中处理，本类只关注列表展示和分页逻辑。
 */
class MainActivity : BaseForumActivity() {

    private val viewModel: MainViewModel by viewModels()

    // 实现基类的抽象 View 属性
    override lateinit var tvStatus: TextView
    override lateinit var shimmerLayout: ShimmerFrameLayout
    override lateinit var mainContentView: View  // 指向 RecyclerView

    private lateinit var adapter: ThreadListAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var rvThreads: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        CookieStore.load(this)

        // 初始化视图
        tvStatus = findViewById(R.id.tvStatus)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        shimmerLayout = findViewById(R.id.shimmerLayout)
        rvThreads = findViewById(R.id.rvThreads)
        mainContentView = rvThreads  // 基类通过 mainContentView 控制显示/隐藏

        // RecyclerView 配置
        val layoutManager = LinearLayoutManager(this)
        rvThreads.layoutManager = layoutManager
        adapter = ThreadListAdapter { item ->
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
            viewModel.loadFirstPage(forceRefresh = true)
        }

        // 上拉加载更多
        rvThreads.setupPaging(
            layoutManager = layoutManager,
            preloadThreshold = 3,
            canLoadMore = { viewModel.hasMore() },
            isLoading = { viewModel.isLoading.value == true },
            onLoadMore = { viewModel.loadNextPage() }
        )

        observeViewModel()
        setupBackPressHandler()

        // 首次加载
        if (viewModel.threads.value.isNullOrEmpty()) {
            showShimmer()
            viewModel.loadFirstPage()
        }
    }

    // ---- ActionBar 菜单：收藏入口 ----

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_favorites -> {
                startActivity(Intent(this, FavoritesActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun observeViewModel() {
        // 绑定基类通用 Observer（鉴权、WebView 回退、错误）
        observeBaseViewModel(viewModel)

        // 列表数据更新
        viewModel.threads.observe(this) { threads ->
            adapter.replaceAll(threads)
            hideShimmer()
        }

        // 下拉刷新状态同步
        viewModel.isRefreshing.observe(this) { isRefreshing ->
            swipeRefresh.isRefreshing = isRefreshing
        }
    }

    /** 双击返回键退出应用 */
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
}
