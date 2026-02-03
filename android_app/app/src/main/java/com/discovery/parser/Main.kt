package com.discovery.parser

import com.discovery.parser.model.ParseStatus
import com.discovery.parser.network.DiscuzClient
import com.discovery.parser.service.ForumParser

fun main() {
    val parser = ForumParser()

    // Test forum display
    println("--- Testing Forum Display ---")
    val listUrl = "https://www.4d4y.com/forum/forumdisplay.php?fid=2&page=1"
    val listResult = DiscuzClient.fetch(listUrl)

    if (listResult.status == ParseStatus.SUCCESS) {
        val html = listResult.data!!
        val threadsResult = parser.parseForumDisplay(html)

        if (threadsResult.status == ParseStatus.SUCCESS) {
            val pageInfo = threadsResult.data!!
            println("Page: ${pageInfo.currentPage} / ${pageInfo.forumMaxPage}")
            if (!pageInfo.nextPageUrl.isNullOrBlank()) {
                println("Next: ${pageInfo.nextPageUrl}")
            }
            pageInfo.threads.forEach {
                println("[Thread] ${it.tid} : ${it.title} (By ${it.authorName})")
            }
        } else {
            println("Parse Failed: ${threadsResult.errorMessage}")
        }
    } else {
        println("Fetch Failed: ${listResult.status}, Message: ${listResult.errorMessage}")
        if (listResult.rawHtmlSnippet != null) {
            println("Snippet: ${listResult.rawHtmlSnippet}")
        }
    }

    // Test view thread
    println("\n--- Testing View Thread ---")
    val tid = "3430772"
    val detailUrl = "https://www.4d4y.com/forum/viewthread.php?tid=$tid&page=1"
    val detailResult = DiscuzClient.fetch(detailUrl)

    if (detailResult.status == ParseStatus.SUCCESS) {
        val html = detailResult.data!!
        val postsResult = parser.parseViewThread(html)
        
        if (postsResult.status == ParseStatus.SUCCESS) {
            val pageInfo = postsResult.data!!
            pageInfo.posts.forEach {
                println("[Post #${it.pid}] Length: ${it.contentText.length} chars")
            }
        } else {
            println("Parse Detail Failed: ${postsResult.errorMessage}")
        }
    } else {
        println("Fetch Detail Failed: ${detailResult.status}")
    }
}
