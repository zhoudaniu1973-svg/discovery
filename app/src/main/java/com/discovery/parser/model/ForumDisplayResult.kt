package com.discovery.parser.model

data class ForumDisplayResult(
    val threads: List<ThreadListItem>,
    val currentPage: Int,
    val nextPageUrl: String?,
    val forumMaxPage: Int
)
