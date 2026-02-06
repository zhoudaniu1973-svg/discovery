package com.discovery.util

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

fun RecyclerView.setupPaging(
    layoutManager: LinearLayoutManager,
    preloadThreshold: Int = 3,
    canLoadMore: () -> Boolean,
    isLoading: () -> Boolean,
    onLoadMore: () -> Unit
) {
    addOnScrollListener(object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (dy <= 0) return
            if (isLoading()) return
            if (!canLoadMore()) return
            val lastVisible = layoutManager.findLastVisibleItemPosition()
            if (lastVisible >= adapter?.itemCount?.minus(preloadThreshold) ?: Int.MAX_VALUE) {
                onLoadMore()
            }
        }
    })
}