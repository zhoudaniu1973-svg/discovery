package com.discovery.parser.network

import com.discovery.parser.model.ParseStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * DiscuzClient.detectStatus() 状态检测单元测试
 *
 * 验证各种页面内容能被正确识别为对应状态，确保状态检测逻辑的准确性。
 * 这些测试在 JVM 上直接运行，无需 Android 模拟器。
 */
class StatusDetectorTest {

    // ---- Cloudflare 拦截检测 ----

    @Test
    fun `detectStatus - cdn-cgi 路径 - 返回 CF_CHALLENGE`() {
        val html = """
            <html><body>
            <script src="/cdn-cgi/scripts/xxx.js"></script>
            <p>Please wait...</p>
            </body></html>
        """.trimIndent()

        val result = DiscuzClient.detectStatus(html)

        assertEquals("包含 cdn-cgi 应识别为 CF 挑战", ParseStatus.CF_CHALLENGE, result)
    }

    @Test
    fun `detectStatus - CF 参数脚本 - 返回 CF_CHALLENGE`() {
        // DiscuzClient 检测的是 __CF$cv_params（注意：不含第二个 $cv）
        val html = """
            <html><body>
            <script>window.__CF${'$'}cv_params={r:'abc',t:'def'};</script>
            </body></html>
        """.trimIndent()

        val result = DiscuzClient.detectStatus(html)

        assertEquals("包含 __CF\$cv_params 应识别为 CF 挑战", ParseStatus.CF_CHALLENGE, result)
    }

    @Test
    fun `detectStatus - Checking your browser - 返回 CF_CHALLENGE`() {
        val html = """
            <html><body>
            <h2>Checking your browser before accessing...</h2>
            </body></html>
        """.trimIndent()

        val result = DiscuzClient.detectStatus(html)

        assertEquals("包含 Checking your browser 应识别为 CF 挑战", ParseStatus.CF_CHALLENGE, result)
    }

    // ---- 登录要求检测 ----

    @Test
    fun `detectStatus - 您还未登录 - 返回 NEED_LOGIN`() {
        val html = """
            <html><body>
            <p>您还未登录，请先登录再访问。</p>
            </body></html>
        """.trimIndent()

        val result = DiscuzClient.detectStatus(html)

        assertEquals("包含'您还未登录'应识别为需要登录", ParseStatus.NEED_LOGIN, result)
    }

    @Test
    fun `detectStatus - 无权访问该版块 - 返回 NEED_LOGIN`() {
        val html = """
            <html><body>
            <p>对不起，您无权访问该版块。</p>
            </body></html>
        """.trimIndent()

        val result = DiscuzClient.detectStatus(html)

        assertEquals("包含'无权访问该版块'应识别为需要登录", ParseStatus.NEED_LOGIN, result)
    }

    // ---- 正常页面 ----

    @Test
    fun `detectStatus - 正常论坛 HTML - 返回 null`() {
        val html = """
            <html><body>
            <table>
              <tbody id="normalthread_1234">
                <td class="subject"><span id="thread_1234"><a href="viewthread.php?tid=1234">帖子标题</a></span></td>
              </tbody>
            </table>
            </body></html>
        """.trimIndent()

        val result = DiscuzClient.detectStatus(html)

        assertNull("正常内容不应触发任何状态检测", result)
    }

    // ---- 验证码检测 ----

    @Test
    fun `detectStatus - seccode 加验证码 - 返回 CAPTCHA_REQUIRED`() {
        val html = """
            <html><body>
            <input name="seccode" type="text" />
            <p>请输入验证码</p>
            </body></html>
        """.trimIndent()

        val result = DiscuzClient.detectStatus(html)

        assertEquals("包含 seccode+验证码 应识别为需要验证码", ParseStatus.CAPTCHA_REQUIRED, result)
    }

    // ---- 无权限检测 ----

    @Test
    fun `detectStatus - 您无权进行当前操作 - 返回 NO_PERMISSION`() {
        val html = """
            <html><body>
            <p>您无权进行当前操作，请联系管理员。</p>
            </body></html>
        """.trimIndent()

        val result = DiscuzClient.detectStatus(html)

        assertEquals("包含'您无权进行当前操作'应识别为无权限", ParseStatus.NO_PERMISSION, result)
    }

    // ---- 使用真实 HTML fixture 文件 ----

    @Test
    fun `detectStatus - 真实CF拦截页面 - 返回 CF_CHALLENGE`() {
        val html = loadHtmlFixture("viewthread_cf_block.html")

        val result = DiscuzClient.detectStatus(html)

        assertEquals("真实 CF 拦截页面应识别为 CF 挑战", ParseStatus.CF_CHALLENGE, result)
    }

    // ---- 辅助方法 ----

    private fun loadHtmlFixture(filename: String): String {
        val stream = javaClass.classLoader?.getResourceAsStream("html/$filename")
            ?: error("找不到测试 fixture 文件: html/$filename")
        return stream.bufferedReader(Charsets.UTF_8).readText()
    }
}
