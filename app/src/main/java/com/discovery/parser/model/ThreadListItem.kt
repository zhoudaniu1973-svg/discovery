package com.discovery.parser.model

data class ThreadListItem(
    val tid: String,
    val title: String,
    val authorName: String,
    val authorUid: String,
    val postDate: String,
    val replies: Int,
    val views: Int,
    val lastPoster: String,
    val lastTime: String,
    val threadMaxPage: Int = 1
)
