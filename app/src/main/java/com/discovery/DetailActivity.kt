package com.discovery

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.discovery.parser.model.FavoriteItem
import com.discovery.parser.network.CookieStore
import com.discovery.ui.PostListAdapter
import com.discovery.util.FavoriteStore
import com.discovery.util.FontSizeStore
import com.discovery.util.setupActionBarAutoHide
import com.discovery.util.setupPaging
import com.discovery.viewmodel.DetailViewModel
import com.facebook.shimmer.ShimmerFrameLayout

/**
 * 帖子详情页
 *
 * 继承 BaseForumActivity，通用的 WebView 回退、骨架屏、
 * 鉴权跳转已在基类中处理，本类只关注帖子列表展示和分页逻辑。
 */
class DetailActivity : BaseForumActivity() {

    private val viewModel: DetailViewModel by viewModels()

    // 实现基类的抽象 View 属性
    override lateinit var tvStatus: TextView
    override lateinit var shimmerLayout: ShimmerFrameLayout
    override lateinit var mainContentView: View  // 指向 RecyclerView

    private lateinit var adapter: PostListAdapter
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var rvPosts: RecyclerView

    private var hasAutoScrolled = false

    // 收藏相关
    private var tid = "0"
    private var threadTitle = ""
    private var authorName = ""
    private var favoriteMenuItem: MenuItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        CookieStore.load(this)
        FavoriteStore.init(this)
        FontSizeStore.init(this)

        // 初始化视图
        tvStatus = findViewById(R.id.tvStatus)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        shimmerLayout = findViewById(R.id.shimmerLayout)
        rvPosts = findViewById(R.id.rvPosts)
        mainContentView = rvPosts  // 基类通过 mainContentView 控制显示/隐藏

        tid = intent.getStringExtra(EXTRA_TID) ?: "0"
        val maxPage = intent.getIntExtra(EXTRA_MAX_PAGE, 1)
        threadTitle = intent.getStringExtra(EXTRA_TITLE) ?: "Thread"
        supportActionBar?.title = threadTitle

        viewModel.init(tid, maxPage)

        val layoutManager = LinearLayoutManager(this)
        rvPosts.layoutManager = layoutManager
        adapter = PostListAdapter(lifecycleScope)
        adapter.updateFontSize(FontSizeStore.getSize())  // 使用用户偏好的字体大小
        rvPosts.adapter = adapter
        setupActionBarAutoHide(rvPosts)

        swipeRefresh.setOnRefreshListener {
            hasAutoScrolled = false
            viewModel.loadFirstPage(forceRefresh = true)
        }

        rvPosts.setupPaging(
            layoutManager = layoutManager,
            preloadThreshold = 3,
            canLoadMore = { viewModel.hasMore() },
            isLoading = { viewModel.isLoading.value == true },
            onLoadMore = { viewModel.loadNextPage() }
        )

        observeViewModel()

        if (viewModel.posts.value.isNullOrEmpty()) {
            showShimmer()
            viewModel.loadFirstPage()
        }
    }

    // ---- 收藏菜单 ----

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_detail, menu)
        favoriteMenuItem = menu.findItem(R.id.action_favorite)
        updateFavoriteIcon()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_favorite -> {
                toggleFavorite()
                true
            }
            R.id.action_font_size -> {
                showFontSizeDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /** 切换收藏状态 */
    private fun toggleFavorite() {
        if (FavoriteStore.isFavorite(tid)) {
            FavoriteStore.remove(tid)
            Toast.makeText(this, "已取消收藏", Toast.LENGTH_SHORT).show()
        } else {
            // 尝试从已加载的帖子中获取作者名
            val author = viewModel.posts.value?.firstOrNull()?.authorName ?: ""
            FavoriteStore.add(FavoriteItem(tid, threadTitle, author))
            Toast.makeText(this, "已收藏", Toast.LENGTH_SHORT).show()
        }
        updateFavoriteIcon()
    }

    /** 根据收藏状态切换图标（空心/实心星） */
    private fun updateFavoriteIcon() {
        val isFav = FavoriteStore.isFavorite(tid)
        favoriteMenuItem?.setIcon(
            if (isFav) R.drawable.ic_favorite else R.drawable.ic_favorite_border
        )
    }

    // ---- 字体大小调整 ----

    /** 显示字体大小调整对话框（SeekBar + 实时预览） */
    private fun showFontSizeDialog() {
        val currentSize = FontSizeStore.getSize()
        val min = FontSizeStore.MIN_SIZE
        val max = FontSizeStore.MAX_SIZE

        // 构建对话框布局
        val dialogView = layoutInflater.inflate(
            android.R.layout.simple_list_item_1, null, false
        )
        // 用纯代码构建，避免引入额外布局文件
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(60, 40, 60, 20)
        }
        val label = TextView(this).apply {
            text = "字体大小：${currentSize}sp"
            textSize = 16f
        }
        val seekBar = SeekBar(this).apply {
            this.max = max - min
            progress = currentSize - min
        }
        // 预览文字
        val preview = TextView(this).apply {
            text = "预览效果：这是一段示例文字"
            textSize = currentSize.toFloat()
            setPadding(0, 20, 0, 0)
        }
        container.addView(label)
        container.addView(seekBar)
        container.addView(preview)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val size = progress + min
                label.text = "字体大小：${size}sp"
                preview.textSize = size.toFloat()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        AlertDialog.Builder(this)
            .setTitle("调整字体大小")
            .setView(container)
            .setPositiveButton("确定") { _, _ ->
                val newSize = seekBar.progress + min
                FontSizeStore.setSize(newSize)
                adapter.updateFontSize(newSize)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun observeViewModel() {
        // 绑定基类通用 Observer（鉴权、WebView 回退、错误）
        observeBaseViewModel(viewModel)

        // 帖子数据更新
        viewModel.posts.observe(this) { posts ->
            adapter.replaceAll(posts)
            hideShimmer()
            // 首次加载完成时强制滚到顶，防止自动滚到底部
            if (posts.isNotEmpty() && !hasAutoScrolled && viewModel.currentPage == 1) {
                hasAutoScrolled = true
                rvPosts.post {
                    rvPosts.stopScroll()
                    (rvPosts.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(0, 0)
                }
            }
        }

        // 下拉刷新状态同步
        viewModel.isRefreshing.observe(this) { isRefreshing ->
            swipeRefresh.isRefreshing = isRefreshing
        }
    }

    companion object {
        const val EXTRA_TID = "extra_tid"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_MAX_PAGE = "extra_max_page"
    }
}
