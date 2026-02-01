package com.discovery.parser.model

data class PostItem(
    val tid: String,
    val pid: String,
    val page: Int,
    val contentHtml: String,
    val contentText: String,
    val authorName: String = "",
    val postTime: String = ""
)
