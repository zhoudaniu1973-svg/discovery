package com.discovery.parser.service

import com.discovery.Constants
import com.discovery.parser.model.ForumDisplayResult
import com.discovery.parser.model.ParseResult
import com.discovery.parser.model.ParseStatus
import com.discovery.parser.model.PostItem
import com.discovery.parser.model.ThreadListItem
import com.discovery.parser.model.ViewThreadResult
import com.discovery.util.DebugLog
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI

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
                    DebugLog.w("ForumParser", e) { "Parse single thread error" }
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
            DebugLog.w("ForumParser", e) { "Parse forumdisplay failed" }
            writeParseErrorSnippet("forumdisplay", html, e)
            val snippet = if (html.length > 500) html.substring(0, 500) else html
            return ParseResult(ParseStatus.PARSE_ERROR, errorMessage = e.message, rawHtmlSnippet = snippet)
        }
    }

    fun parseViewThread(html: String): ParseResult<ViewThreadResult> {
        try {
            // 优化：先裁剪 HTML 只保留帖子区域，减少 Jsoup 解析量
            val trimmedHtml = trimHtmlForViewThread(html)
            val doc = Jsoup.parse(trimmedHtml)
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

                    val contentHtml = sanitizePostHtml(msgNode)
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
                    DebugLog.w("ForumParser", e) { "Parse post error" }
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
            DebugLog.w("ForumParser", e) { "Parse viewthread failed" }
            writeParseErrorSnippet("viewthread", html, e)
            val snippet = if (html.length > 500) html.substring(0, 500) else html
            return ParseResult(ParseStatus.PARSE_ERROR, errorMessage = e.message, rawHtmlSnippet = snippet)
        }
    }

    /**
     * 快速解析帖子详情（首屏阶段）
     *
     * 与 parseViewThread() 的区别：
     * - 只解析前 [limit] 条帖子，跳过后续帖子
     * - 不调用 sanitizePostHtml()（最耗时的步骤），直接用纯文本
     * - 移除 img 标签，实现图片延迟加载
     * - 预期耗时从 200-500ms 降到 50-100ms
     */
    fun parseViewThreadQuick(html: String, limit: Int = 3): ParseResult<ViewThreadResult> {
        try {
            val trimmedHtml = trimHtmlForViewThread(html)
            val doc = Jsoup.parse(trimmedHtml)

            // 解析分页信息（与完整解析相同）
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

            val posts = mutableListOf<PostItem>()
            val msgFonts = doc.select("td.t_msgfont[id^=postmessage_]")
            // 只解析前 limit 条帖子
            for ((index, msgNode) in msgFonts.withIndex()) {
                if (index >= limit) break
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

                    // 快速模式：移除 img 标签后取纯文本，跳过耗时的 sanitizePostHtml
                    val contentText = msgNode.text()
                    val quickHtml = stripImgTags(msgNode.html())

                    posts.add(
                        PostItem(
                            tid = tid,
                            pid = pid,
                            page = page,
                            contentHtml = quickHtml,
                            contentText = contentText,
                            authorName = authorName,
                            postTime = postTime
                        )
                    )
                } catch (e: Exception) {
                    DebugLog.w("ForumParser", e) { "Quick parse post error" }
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
            DebugLog.w("ForumParser", e) { "Quick parse viewthread failed" }
            return ParseResult(ParseStatus.PARSE_ERROR, errorMessage = e.message)
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

    private fun sanitizePostHtml(msgNode: Element): String {
        val node = msgNode.clone()
        node.select("style,script").remove()

        val lazyAttrs = arrayOf("zoomfile", "file", "data-src", "data-original", "data-ks-lazyload")
        node.select("img").forEach { img ->
            val src = img.attr("src").trim()
            var lazySrc = ""
            for (attr in lazyAttrs) {
                val value = img.attr(attr).trim()
                if (value.isNotBlank()) {
                    lazySrc = value
                    break
                }
            }

            val candidate = if (lazySrc.isNotBlank()) lazySrc else src
            val removeAsPlaceholder = candidate.isBlank() || (lazySrc.isBlank() && isPlaceholderImage(src))
            if (removeAsPlaceholder || isDecorativeAttachmentIcon(candidate)) {
                img.remove()
                return@forEach
            }

            img.attr("src", absolutizeImageUrl(candidate))
            for (attr in lazyAttrs) {
                img.removeAttr(attr)
            }
            img.removeAttr("onload")
        }

        return node.html()
    }

    private fun isPlaceholderImage(src: String): Boolean {
        return src.lowercase().contains("none.gif")
    }

    private fun isDecorativeAttachmentIcon(src: String): Boolean {
        val lower = src.lowercase()
        return lower.contains("attachimg.gif") ||
            lower.contains("/attachicons/") ||
            lower.contains("attachicons\\")
    }

    private fun absolutizeImageUrl(rawUrl: String): String {
        val url = rawUrl.trim()
        if (url.isBlank()) return url
        if (url.startsWith("http://", ignoreCase = true) ||
            url.startsWith("https://", ignoreCase = true) ||
            url.startsWith("data:", ignoreCase = true) ||
            url.startsWith("content://", ignoreCase = true)
        ) {
            return url
        }
        if (url.startsWith("//")) {
            return "https:$url"
        }
        return try {
            URI(Constants.BASE_FORUM_URL).resolve(url).toString()
        } catch (_: Exception) {
            url
        }
    }

    private fun extractPostTime(root: Element?): String {
        if (root == null) return ""
        val fromPostInfo = root.selectFirst("div.postinfo em")?.text()?.trim()
        if (!fromPostInfo.isNullOrBlank()) return fromPostInfo
        val fromEm = root.selectFirst("em")?.text()?.trim()
        return fromEm ?: ""
    }

    private fun findPostRoot(msgNode: Element): Element? {
        var current: Element? = msgNode
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
        val snippet = if (html.length > 1000) html.substring(0, 1000) else html
        val msg = buildString {
            append("=== Parse Error [$tag] ===\n")
            append("Error: ").append(error?.message ?: "unknown").append('\n')
            append("Snippet:\n").append(snippet)
        }
        DebugLog.d("ForumParser") { msg }
    }

    /**
     * 裁剪 HTML，只保留帖子列表区域 + 分页信息
     *
     * 真实 4d4y 页面 HTML 约 100-300KB，其中帖子内容区域只占 30-40%。
     * 裁掉 <head>、导航栏、侧边栏、<script> 等无关内容，可以大幅减轻
     * Jsoup.parse() 的负担。
     *
     * 策略：
     * 1. 优先寻找 <div id="postlist"> 作为帖子起始位置
     * 2. 回退到第一个 <td class="t_msgfont" 作为粗略起始
     * 3. 始终保留 <div class="pages"> 分页区域
     * 4. 找不到标记时回退完整 HTML，保证不丢数据
     */
    internal fun trimHtmlForViewThread(html: String): String {
        if (html.length < 5000) return html  // 小文档无需裁剪

        // 保留 <form id="postform"...> 用于提取 tid
        val formStart = html.indexOf("<form", ignoreCase = true)
        val formEnd = if (formStart >= 0) html.indexOf("</form>", formStart, ignoreCase = true) else -1
        val formSnippet = if (formStart >= 0 && formEnd >= 0) {
            html.substring(formStart, formEnd + "</form>".length)
        } else ""

        // 保留分页区域
        val pagesStart = html.indexOf("<div class=\"pages\"", ignoreCase = true)
            .takeIf { it >= 0 }
            ?: html.indexOf("class=\"pages\"", ignoreCase = true)
                .takeIf { it >= 0 }
                ?.let { html.lastIndexOf('<', it) }
                ?: -1
        val pagesEnd = if (pagesStart >= 0) html.indexOf("</div>", pagesStart, ignoreCase = true) else -1
        val pagesSnippet = if (pagesStart >= 0 && pagesEnd >= 0) {
            html.substring(pagesStart, pagesEnd + "</div>".length)
        } else ""

        // 策略 1：<div id="postlist"> → </div> 的最后匹配
        val postlistStart = html.indexOf("id=\"postlist\"", ignoreCase = true)
        if (postlistStart >= 0) {
            // 找到 postlist 的起始 div 标签
            val divStart = html.lastIndexOf('<', postlistStart)
            if (divStart >= 0) {
                // 从 postlist 开始，找到合理的结束位置（足够包含所有帖子）
                // 使用最后一个 postmessage_ 之后的 </table> 作为结束标记
                val lastMsg = html.lastIndexOf("postmessage_", ignoreCase = true)
                if (lastMsg >= 0) {
                    val tableEnd = html.indexOf("</table>", lastMsg, ignoreCase = true)
                    val endPos = if (tableEnd >= 0) tableEnd + "</table>".length else html.length
                    return buildString {
                        append("<html><body>")
                        append(pagesSnippet)
                        append(formSnippet)
                        append(html, divStart, endPos.coerceAtMost(html.length))
                        append("</body></html>")
                    }
                }
            }
        }

        // 策略 2：回退到第一个 td.t_msgfont 所在的 <table>
        val firstMsgFont = html.indexOf("t_msgfont", ignoreCase = true)
        if (firstMsgFont >= 0) {
            // 从第一个 t_msgfont 向前找最近的 <table>
            val tableStart = html.lastIndexOf("<table", firstMsgFont, ignoreCase = true)
            val lastMsg = html.lastIndexOf("postmessage_", ignoreCase = true)
            if (tableStart >= 0 && lastMsg >= 0) {
                val tableEnd = html.indexOf("</table>", lastMsg, ignoreCase = true)
                val endPos = if (tableEnd >= 0) tableEnd + "</table>".length else html.length
                return buildString {
                    append("<html><body>")
                    append(pagesSnippet)
                    append(formSnippet)
                    append(html, tableStart, endPos.coerceAtMost(html.length))
                    append("</body></html>")
                }
            }
        }

        // 策略 3：找不到标记，回退完整 HTML
        return html
    }

    /**
     * 移除 HTML 中的 <img> 标签（用于快速解析阶段）
     *
     * 首屏只展示文字内容，图片在 Stage2 完整解析时再加回来。
     * 使用正则匹配，比 Jsoup DOM 操作更快。
     */
    private fun stripImgTags(html: String): String {
        return html.replace(IMG_TAG_REGEX, "")
    }

    companion object {
        // 匹配 <img .../> 和 <img ...> 两种写法
        private val IMG_TAG_REGEX = Regex("""<img[^>]*/?>""", RegexOption.IGNORE_CASE)
    }
}
