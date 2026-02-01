package com.discovery.parser.service

import com.discovery.parser.model.ForumDisplayResult
import com.discovery.parser.model.ParseResult
import com.discovery.parser.model.ParseStatus
import com.discovery.parser.model.PostItem
import com.discovery.parser.model.ThreadListItem
import com.discovery.parser.model.ViewThreadResult
import org.jsoup.Jsoup
import java.io.File

class ForumParser {

    fun parseForumDisplay(html: String): ParseResult<ForumDisplayResult> {
        try {
            val doc = Jsoup.parse(html)
            val threads = mutableListOf<ThreadListItem>()

            // Pagination info
            val pagesDiv = doc.selectFirst("div.pages")
            val currentPageStr = pagesDiv?.selectFirst("strong")?.text()?.trim()
            val currentPage = currentPageStr?.toIntOrNull() ?: 1
            val nextPageUrl = pagesDiv?.selectFirst("a.next")?.attr("href")?.trim()?.ifBlank { null }

            val lastPageText = pagesDiv?.selectFirst("a.last")?.text()?.trim()
            val forumMaxPage = extractLastInt(lastPageText) ?: currentPage

            // Thread list
            val threadBodys = doc.select("tbody[id^=normalthread_]")
            for (tbody in threadBodys) {
                try {
                    val tidSpan = tbody.selectFirst("span[id^=thread_]") ?: continue
                    val tidText = tidSpan.id().removePrefix("thread_").takeWhile { it.isDigit() }
                    if (tidText.isBlank()) continue

                    val titleLink = tidSpan.selectFirst("a")
                    val title = titleLink?.text()?.trim().orEmpty()

                    val authorCell = tbody.selectFirst("td.author")
                    val authorLink = authorCell?.selectFirst("cite a[href^=space.php?uid=]")
                    val authorName = authorLink?.text()?.trim().takeUnless { it.isNullOrBlank() } ?: "Unknown"
                    val authorUid = extractQueryParam(authorLink?.attr("href"), "uid") ?: "0"

                    val postDate = authorCell?.selectFirst("em")?.text()?.trim().orEmpty()

                    val numsCell = tbody.selectFirst("td.nums")
                    val replies = numsCell?.selectFirst("strong")?.text()?.trim()?.toIntOrNull() ?: 0
                    val views = numsCell?.selectFirst("em")?.text()?.trim()?.toIntOrNull() ?: 0

                    val lastPostCell = tbody.selectFirst("td.lastpost")
                    val lastPoster = lastPostCell?.selectFirst("cite a")?.text()?.trim().orEmpty()
                    val lastTime = lastPostCell?.selectFirst("em a[href*=goto=lastpost]")?.text()?.trim().orEmpty()

                    var threadMaxPage = 1
                    val pageLinks = tidSpan.select("span.threadpages a[href*=page=]")
                    if (pageLinks.isNotEmpty()) {
                        for (link in pageLinks) {
                            val pageStr = extractQueryParam(link.attr("href"), "page")
                            val pageNum = pageStr?.toIntOrNull()
                            if (pageNum != null && pageNum > threadMaxPage) {
                                threadMaxPage = pageNum
                            }
                        }
                    }

                    threads.add(
                        ThreadListItem(
                            tid = tidText,
                            title = title,
                            authorName = authorName,
                            authorUid = authorUid,
                            postDate = postDate,
                            replies = replies,
                            views = views,
                            lastPoster = lastPoster,
                            lastTime = lastTime,
                            threadMaxPage = threadMaxPage
                        )
                    )

                } catch (e: Exception) {
                    println("Parse single thread error: ${e.message}")
                }
            }

            if (threads.isEmpty()) {
                val snippet = if (html.length > 500) html.substring(0, 500) else html
                return ParseResult(
                    ParseStatus.PARSE_ERROR,
                    errorMessage = "No threads found",
                    rawHtmlSnippet = snippet
                )
            }

            return ParseResult(
                ParseStatus.SUCCESS,
                data = ForumDisplayResult(
                    threads = threads,
                    currentPage = currentPage,
                    nextPageUrl = nextPageUrl,
                    forumMaxPage = forumMaxPage
                )
            )

        } catch (e: Exception) {
            e.printStackTrace()
            writeParseErrorSnippet("forumdisplay", html, e)
            val snippet = if (html.length > 500) html.substring(0, 500) else html
            return ParseResult(ParseStatus.PARSE_ERROR, errorMessage = e.message, rawHtmlSnippet = snippet)
        }
    }

    fun parseViewThread(html: String): ParseResult<ViewThreadResult> {
        try {
            val doc = Jsoup.parse(html)
            val posts = mutableListOf<PostItem>()

            val pagesDiv = doc.selectFirst("div.pages")
            val currentPageStr = pagesDiv?.selectFirst("strong")?.text()?.trim()
            val page = currentPageStr?.toIntOrNull() ?: 1
            var maxPage = page
            val lastPageText = pagesDiv?.selectFirst("a.last")?.text()?.trim()
            val lastPage = extractLastInt(lastPageText)
            if (lastPage != null && lastPage > maxPage) {
                maxPage = lastPage
            }
            val pageLinks = pagesDiv?.select("a[href*=page=]").orEmpty()
            for (link in pageLinks) {
                val pageStr = extractQueryParam(link.attr("href"), "page")
                val pageNum = pageStr?.toIntOrNull()
                if (pageNum != null && pageNum > maxPage) {
                    maxPage = pageNum
                }
            }

            var tid = "0"
            val postForm = doc.selectFirst("form[id=postform]")
            if (postForm != null) {
                val action = postForm.attr("action")
                tid = extractQueryParam(action, "tid") ?: "0"
            }

            val msgFonts = doc.select("td.t_msgfont[id^=postmessage_]")
            for (msgNode in msgFonts) {
                try {
                    val idStr = msgNode.id()
                    val pid = idStr.removePrefix("postmessage_").takeWhile { it.isDigit() }
                    if (pid.isBlank()) continue

                    val postRoot = findPostRoot(msgNode)
                    val authorName = postRoot
                        ?.selectFirst("td.postauthor a[href*=\"space.php?uid=\"]")
                        ?.text()
                        ?.trim()
                        .orEmpty()
                    val postTime = extractPostTime(postRoot)

                    val contentHtml = msgNode.html()
                    val contentText = msgNode.text()

                    posts.add(
                        PostItem(
                            tid = tid,
                            pid = pid,
                            page = page,
                            contentHtml = contentHtml,
                            contentText = contentText,
                            authorName = authorName,
                            postTime = postTime
                        )
                    )
                } catch (e: Exception) {
                    println("Parse post error: ${e.message}")
                }
            }

            return ParseResult(
                ParseStatus.SUCCESS,
                data = ViewThreadResult(
                    posts = posts,
                    currentPage = page,
                    maxPage = maxPage
                )
            )

        } catch (e: Exception) {
            e.printStackTrace()
            writeParseErrorSnippet("viewthread", html, e)
            val snippet = if (html.length > 500) html.substring(0, 500) else html
            return ParseResult(ParseStatus.PARSE_ERROR, errorMessage = e.message, rawHtmlSnippet = snippet)
        }
    }

    private fun extractQueryParam(href: String?, key: String): String? {
        if (href.isNullOrBlank()) return null
        val query = href.substringAfter('?', "")
        if (query.isBlank()) return null
        val pairs = query.split('&')
        for (pair in pairs) {
            if (pair.isBlank()) continue
            val parts = pair.split('=', limit = 2)
            if (parts.isNotEmpty() && parts[0] == key) {
                return if (parts.size > 1) parts[1] else ""
            }
        }
        return null
    }

    private fun extractPostTime(root: org.jsoup.nodes.Element?): String {
        if (root == null) return ""
        val fromPostInfo = root.selectFirst("div.postinfo em")?.text()?.trim()
        if (!fromPostInfo.isNullOrBlank()) return fromPostInfo
        val fromEm = root.selectFirst("em")?.text()?.trim()
        return fromEm ?: ""
    }

    private fun findPostRoot(msgNode: org.jsoup.nodes.Element): org.jsoup.nodes.Element? {
        var current: org.jsoup.nodes.Element? = msgNode
        while (current != null) {
            val id = current.id()
            if (id.startsWith("pid") || (id.startsWith("post") && !id.startsWith("postmessage_"))) {
                return current
            }
            current = current.parent()
        }
        return msgNode.parent()
    }

    private fun extractLastInt(text: String?): Int? {
        if (text.isNullOrBlank()) return null
        var last: Int? = null
        var current = 0
        var inNumber = false
        for (ch in text) {
            if (ch.isDigit()) {
                current = if (inNumber) current * 10 + (ch - '0') else (ch - '0')
                inNumber = true
            } else if (inNumber) {
                last = current
                current = 0
                inNumber = false
            }
        }
        if (inNumber) {
            last = current
        }
        return last
    }

    private fun writeParseErrorSnippet(tag: String, html: String, error: Exception?) {
        // Android 环境下直接使用 Logcat 输出，避免文件权限问题
        val snippet = if (html.length > 1000) html.substring(0, 1000) else html
        val msg = buildString {
            append("=== Parse Error [$tag] ===\n")
            append("Error: ").append(error?.message ?: "unknown").append('\n')
            append("Snippet:\n").append(snippet)
        }
        println(msg)  // Logcat 输出
    }
}
