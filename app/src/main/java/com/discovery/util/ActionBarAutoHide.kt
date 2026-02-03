package com.discovery.util

import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView

fun AppCompatActivity.setupActionBarAutoHide(
    recyclerView: RecyclerView,
    hideThresholdDp: Int = 24
) {
    val actionBar = supportActionBar ?: return
    actionBar.setShowHideAnimationEnabled(true)

    var isBarVisible = true
    var scrollDistance = 0
    val hideThresholdPx = (hideThresholdDp * resources.displayMetrics.density).toInt()

    recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (dy == 0) return

            if (!recyclerView.canScrollVertically(-1) && !isBarVisible) {
                actionBar.show()
                isBarVisible = true
                scrollDistance = 0
                return
            }

            if (dy > 0) {
                if (scrollDistance < 0) scrollDistance = 0
                scrollDistance += dy
                if (isBarVisible && scrollDistance > hideThresholdPx) {
                    actionBar.hide()
                    isBarVisible = false
                    scrollDistance = 0
                }
            } else {
                if (scrollDistance > 0) scrollDistance = 0
                scrollDistance += dy
                if (!isBarVisible && scrollDistance < -hideThresholdPx) {
                    actionBar.show()
                    isBarVisible = true
                    scrollDistance = 0
                }
            }
        }
    })
}
