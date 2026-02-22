package com.discovery

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.discovery.parser.model.ThreadListItem
import com.discovery.util.FavoriteStore

/**
 * 收藏列表页
 *
 * 展示用户收藏的帖子列表，复用 ThreadListAdapter 的布局样式。
 * 支持左滑删除收藏。点击跳转到帖子详情页。
 */
class FavoritesActivity : AppCompatActivity() {

    private lateinit var rvFavorites: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var adapter: com.discovery.ui.ThreadListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favorites)

        supportActionBar?.title = "我的收藏"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        FavoriteStore.init(this)

        rvFavorites = findViewById(R.id.rvFavorites)
        tvEmpty = findViewById(R.id.tvEmpty)

        // 复用帖子列表 Adapter，点击跳到详情页
        adapter = com.discovery.ui.ThreadListAdapter { item ->
            val intent = Intent(this, DetailActivity::class.java)
            intent.putExtra(DetailActivity.EXTRA_TID, item.tid)
            intent.putExtra(DetailActivity.EXTRA_TITLE, item.title)
            startActivity(intent)
        }
        rvFavorites.layoutManager = LinearLayoutManager(this)
        rvFavorites.adapter = adapter

        // 左滑删除收藏
        setupSwipeToDelete()
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    /** 刷新收藏列表 */
    private fun refreshList() {
        val favorites = FavoriteStore.getAll()
        // 将 FavoriteItem 转为 ThreadListItem 以复用 Adapter
        val items = favorites.map { fav ->
            ThreadListItem(
                tid = fav.tid,
                title = fav.title,
                authorName = fav.authorName,
                authorUid = "",
                postDate = "",
                replies = 0,
                views = 0,
                lastPoster = "",
                lastTime = ""
            )
        }
        adapter.replaceAll(items)

        // 空状态切换
        if (items.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            rvFavorites.visibility = View.GONE
        } else {
            tvEmpty.visibility = View.GONE
            rvFavorites.visibility = View.VISIBLE
        }
    }

    /** 左滑删除收藏 */
    private fun setupSwipeToDelete() {
        val callback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val item = adapter.currentList[position]
                FavoriteStore.remove(item.tid)
                refreshList()
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(rvFavorites)
    }
}
