package com.discovery.parser.service

import com.discovery.parser.model.ParseStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * ForumParser 单元测试
 *
 * 覆盖以下解析场景：
 * - 正常论坛列表页（线程列表 + 分页）
 * - 正常帖子详情页（帖子列表 + 图片处理）
 * - 异常场景（空 HTML、需要登录页）
 */
class ForumParserTest {

    private lateinit var parser: ForumParser

    @Before
    fun setUp() {
        parser = ForumParser()
    }

    // ===========================================================
    // parseForumDisplay 测试
    // ===========================================================

    @Test
    fun `parseForumDisplay - 正常列表页 - 返回3条线程`() {
        val html = loadHtmlFixture("forumdisplay_normal.html")

        val result = parser.parseForumDisplay(html)

        assertEquals("状态应为 SUCCESS", ParseStatus.SUCCESS, result.status)
        assertNotNull("data 不应为 null", result.data)
        assertEquals("应解析出 3 条线程", 3, result.data!!.threads.size)
    }

    @Test
    fun `parseForumDisplay - 正常列表页 - 线程字段正确`() {
        val html = loadHtmlFixture("forumdisplay_normal.html")

        val result = parser.parseForumDisplay(html)
        val firstThread = result.data!!.threads.first()

        assertEquals("tid 应为 1001", "1001", firstThread.tid)
        assertEquals("标题应正确解析", "测试帖子标题一", firstThread.title)
        assertEquals("作者应正确解析", "作者甲", firstThread.authorName)
        assertEquals("回复数应为 42", 42, firstThread.replies)
    }

    @Test
    fun `parseForumDisplay - 正常列表页 - 多页帖子解析最大页码`() {
        val html = loadHtmlFixture("forumdisplay_normal.html")

        val result = parser.parseForumDisplay(html)
        // thread_1001 包含 3 个分页链接
        val multiPageThread = result.data!!.threads.find { it.tid == "1001" }

        assertNotNull("应找到 tid=1001 的帖子", multiPageThread)
        assertTrue("多页帖子的 threadMaxPage 应 >= 3", multiPageThread!!.threadMaxPage >= 3)
    }

    @Test
    fun `parseForumDisplay - 正常列表页 - 论坛最大页码为5`() {
        val html = loadHtmlFixture("forumdisplay_normal.html")

        val result = parser.parseForumDisplay(html)

        assertEquals("论坛应有 5 页", 5, result.data!!.forumMaxPage)
    }

    @Test
    fun `parseForumDisplay - 需要登录的页面 - 返回 SUCCESS 但线程为空`() {
        // 登录要求页面没有帖子列表，应解析成功但无数据
        // 注意：状态检测由 DiscuzClient 负责，ForumParser 只负责 HTML 结构解析
        val html = loadHtmlFixture("forumdisplay_need_login.html")

        val result = parser.parseForumDisplay(html)

        // 该页面无 normalthread 元素，解析结果应为空列表或 PARSE_ERROR
        val noThreads = result.status == ParseStatus.PARSE_ERROR
            || (result.status == ParseStatus.SUCCESS && result.data?.threads.isNullOrEmpty())
        assertTrue("登录页解析不应产生有效帖子列表", noThreads)
    }

    @Test
    fun `parseForumDisplay - 空白HTML - 返回 PARSE_ERROR`() {
        val result = parser.parseForumDisplay("")

        assertEquals("空 HTML 应返回 PARSE_ERROR", ParseStatus.PARSE_ERROR, result.status)
    }

    // ===========================================================
    // parseViewThread 测试
    // ===========================================================

    @Test
    fun `parseViewThread - 正常详情页 - 返回3条帖子`() {
        val html = loadHtmlFixture("viewthread_normal.html")

        val result = parser.parseViewThread(html)

        assertEquals("状态应为 SUCCESS", ParseStatus.SUCCESS, result.status)
        assertNotNull("data 不应为 null", result.data)
        assertEquals("应解析出 3 条帖子", 3, result.data!!.posts.size)
    }

    @Test
    fun `parseViewThread - 正常详情页 - 楼主字段正确`() {
        val html = loadHtmlFixture("viewthread_normal.html")

        val result = parser.parseViewThread(html)
        val firstPost = result.data!!.posts.first()

        assertEquals("pid 应为 100", "100", firstPost.pid)
        assertEquals("作者应为 楼主网名", "楼主网名", firstPost.authorName)
    }

    @Test
    fun `parseViewThread - 正常详情页 - 解析为3页`() {
        val html = loadHtmlFixture("viewthread_normal.html")

        val result = parser.parseViewThread(html)

        assertEquals("应解析出 3 页", 3, result.data!!.maxPage)
    }

    @Test
    fun `parseViewThread - 懒加载图片 - 使用 zoomfile 属性`() {
        val html = loadHtmlFixture("viewthread_normal.html")

        val result = parser.parseViewThread(html)
        // 楼主帖子包含懒加载图片 zoomfile="attach/pic_001_large.jpg"
        val firstPostContent = result.data!!.posts.first().contentHtml

        assertTrue(
            "懒加载图片应使用 zoomfile 属性的大图 URL",
            firstPostContent.contains("pic_001_large.jpg")
        )
    }

    @Test
    fun `parseViewThread - 相对路径图片 - 转换为绝对路径`() {
        val html = loadHtmlFixture("viewthread_normal.html")

        val result = parser.parseViewThread(html)
        // 第二个帖子包含相对路径图片 src="forum/attachments/relative_image.png"
        val secondPostContent = result.data!!.posts[1].contentHtml

        assertTrue(
            "相对路径图片应转换为包含 www.4d4y.com 的绝对 URL",
            secondPostContent.contains("www.4d4y.com") || secondPostContent.contains("http")
        )
    }

    @Test
    fun `parseViewThread - 空白HTML - 返回空帖子列表或 PARSE_ERROR`() {
        // Jsoup 可解析空字符串（不会抛异常），ForumParser 在无帖子时返回 SUCCESS+空列表
        // 此测试确认解析器不会崩溃，且不会产生虚假数据
        val result = parser.parseViewThread("")

        val noValidPosts = result.status == ParseStatus.PARSE_ERROR
            || (result.status == ParseStatus.SUCCESS && result.data?.posts.isNullOrEmpty())
        assertTrue("空 HTML 解析不应产生有效帖子列表", noValidPosts)
    }

    // ===========================================================
    // parseViewThreadQuick 测试
    // ===========================================================

    @Test
    fun `parseViewThreadQuick - 正常页面 - 限制返回前2条帖子`() {
        val html = loadHtmlFixture("viewthread_normal.html")

        val result = parser.parseViewThreadQuick(html, limit = 2)

        assertEquals("状态应为 SUCCESS", ParseStatus.SUCCESS, result.status)
        assertNotNull("data 不应为 null", result.data)
        assertEquals("limit=2 应返回 2 条帖子", 2, result.data!!.posts.size)
    }

    @Test
    fun `parseViewThreadQuick - 纯文本不含 img 标签`() {
        val html = loadHtmlFixture("viewthread_normal.html")

        val result = parser.parseViewThreadQuick(html, limit = 3)
        // 楼主帖子原本含有 <img> 标签，快速解析应移除
        val firstPostHtml = result.data!!.posts.first().contentHtml

        assertTrue(
            "快速解析的 contentHtml 不应包含 <img 标签",
            !firstPostHtml.contains("<img", ignoreCase = true)
        )
    }

    @Test
    fun `parseViewThreadQuick - 分页信息正确`() {
        val html = loadHtmlFixture("viewthread_normal.html")

        val result = parser.parseViewThreadQuick(html, limit = 1)

        assertEquals("应正确解析出 3 页", 3, result.data!!.maxPage)
    }

    // ===========================================================
    // trimHtmlForViewThread 测试
    // ===========================================================

    @Test
    fun `trimHtmlForViewThread - 小文档直接返回原文`() {
        val smallHtml = "<html><body><td class=\"t_msgfont\">短内容</td></body></html>"

        val result = parser.trimHtmlForViewThread(smallHtml)

        assertEquals("小文档应返回原文", smallHtml, result)
    }

    @Test
    fun `trimHtmlForViewThread - 大文档裁剪后保留 postmessage`() {
        // 构造超过 5000 字符的 HTML，包含 t_msgfont 区域
        val padding = "x".repeat(6000)
        val largeHtml = """
            <html><head>$padding</head><body>
            <div class="pages"><strong>1</strong></div>
            <form id="postform" action="post.php?action=reply&tid=999"></form>
            <table><tr><td class="t_msgfont" id="postmessage_1">帖子内容</td></tr></table>
            </body></html>
        """.trimIndent()

        val result = parser.trimHtmlForViewThread(largeHtml)

        assertTrue("裁剪后应包含 postmessage_", result.contains("postmessage_"))
        assertTrue("裁剪后应包含帖子内容", result.contains("帖子内容"))
        assertTrue("裁剪后应包含分页信息", result.contains("pages"))
        assertTrue("裁剪后长度应小于原文", result.length < largeHtml.length)
    }

    @Test
    fun `trimHtmlForViewThread - 找不到标记时回退全量`() {
        val padding = "x".repeat(6000)
        val noMarkerHtml = "<html><body>$padding<div>没有帖子标记的页面</div></body></html>"

        val result = parser.trimHtmlForViewThread(noMarkerHtml)

        assertEquals("找不到标记应返回原文", noMarkerHtml, result)
    }

    // ===========================================================
    // 辅助方法
    // ===========================================================

    /**
     * 从 test/resources/html/ 目录加载 HTML 测试夹具文件
     */
    private fun loadHtmlFixture(filename: String): String {
        val stream = javaClass.classLoader?.getResourceAsStream("html/$filename")
            ?: error("找不到测试 fixture 文件: html/$filename，请检查 app/src/test/resources/html/")
        return stream.bufferedReader(Charsets.UTF_8).readText()
    }
}
