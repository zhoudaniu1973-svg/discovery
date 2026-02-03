package com.discovery.parser.model

data class ViewThreadResult(
    val posts: List<PostItem>,
    val currentPage: Int,
    val maxPage: Int
)
