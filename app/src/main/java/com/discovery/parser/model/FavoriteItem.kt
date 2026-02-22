package com.discovery.parser.model

/**
 * 收藏帖子数据模型
 *
 * 存储用户收藏的帖子信息，用于本地持久化和收藏列表展示。
 * 设计上只保留展示所需的最小字段集，保持存储体积小。
 */
data class FavoriteItem(
    val tid: String,          // 帖子 ID
    val title: String,        // 帖子标题
    val authorName: String,   // 作者名
    val savedAt: Long = System.currentTimeMillis()  // 收藏时间戳
)
