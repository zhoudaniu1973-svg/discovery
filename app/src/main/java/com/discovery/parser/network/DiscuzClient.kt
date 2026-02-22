package com.discovery.parser.network

import com.discovery.Constants
import com.discovery.parser.model.ParseResult
import com.discovery.parser.model.ParseStatus
import com.discovery.util.DebugLog
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Discuz 网络客户端 (单例模式)
 * - 支持 GBK 解码
 * - 内置状态检测
 * - 连接池复用
 * - 指数退避自动重试（仅对瞬态网络错误）
 */
object DiscuzClient {

    // 最大重试次数（不含首次请求，总共最多发 1+MAX_RETRIES 次请求）
    private const val MAX_RETRIES = 2
    // 初始退避时长（每次翻倍：100ms → 200ms → 400ms）
    private const val INITIAL_BACKOFF_MS = 100L

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder()
                    .header("User-Agent", Constants.DESKTOP_UA)
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                val cookieHeader = CookieStore.get()
                if (!cookieHeader.isNullOrBlank()) {
                    builder.header("Cookie", cookieHeader)
                }
                chain.proceed(builder.build())
            }
            .build()
    }

    // Content-Type charset 提取正则
    private val charsetPattern: Pattern = Pattern.compile("charset=([\\w-]+)", Pattern.CASE_INSENSITIVE)

    /**
     * 发起网络请求，内置指数退避重试。
     *
     * **重试策略**：
     * - 仅对瞬态错误重试：IOException、HTTP 5xx、空响应体
     * - 不重试状态性错误：CF_CHALLENGE、NEED_LOGIN、CAPTCHA 等（重试无效果）
     * - 退避间隔：100ms → 200ms（每次翻倍）
     *
     * @param url 目标 URL
     * @return ParseResult，包含 HTML 或错误状态
     */
    fun fetch(url: String): ParseResult<String> {
        var lastResult: ParseResult<String>? = null
        var backoffMs = INITIAL_BACKOFF_MS

        for (attempt in 0..MAX_RETRIES) {
            if (attempt > 0) {
                DebugLog.d("DiscuzClient") { "第 $attempt 次重试，等待 ${backoffMs}ms，URL=$url" }
                Thread.sleep(backoffMs)
                backoffMs *= 2  // 指数退避
            }

            val result = fetchOnce(url)

            // 成功或状态性错误（CF/登录/验证码）→ 立即返回，不重试
            if (result.status != ParseStatus.NETWORK_ERROR) {
                return result
            }

            // 记录最后一次网络错误结果，供耗尽重试后返回
            lastResult = result
            DebugLog.w("DiscuzClient") { "第 ${attempt + 1} 次请求失败(NETWORK_ERROR)，msg=${result.errorMessage}，URL=$url" }
        }

        // 所有重试耗尽，返回最后一次的错误结果
        DebugLog.w("DiscuzClient") { "所有 ${MAX_RETRIES + 1} 次请求均失败，放弃，URL=$url" }
        return lastResult ?: ParseResult(ParseStatus.NETWORK_ERROR, errorMessage = "Unknown error")
    }

    /**
     * 单次 HTTP 请求（不含重试逻辑）
     * 拆分为独立私有方法，便于测试和重试循环调用
     */
    private fun fetchOnce(url: String): ParseResult<String> {
        val request = Request.Builder().url(url).build()
        var response: Response? = null
        try {
            response = client.newCall(request).execute()

            val finalUrl = response.request.url.toString()
            val status = response.code
            val contentType = response.header("Content-Type")
            val bodyBytes = response.body?.bytes() ?: ByteArray(0)

            // 动态解析 charset，默认 GBK
            val charset = parseCharset(contentType) ?: Constants.DEFAULT_CHARSET
            val html = if (bodyBytes.isNotEmpty()) String(bodyBytes, Charset.forName(charset)) else ""
            val snippet = if (html.length > 300) html.substring(0, 300) else html

            DebugLog.d("DiscuzClient") {
                "FinalUrl=$finalUrl, Status=$status, Charset=$charset, BodyLen=${bodyBytes.size}"
            }

            // HTTP 5xx 服务器错误 → 属于瞬态错误，可以重试
            if (status in 500..599) {
                return ParseResult(ParseStatus.NETWORK_ERROR, errorMessage = "HTTP $status", rawHtmlSnippet = snippet)
            }

            // 其他 HTTP 错误（4xx 等）→ 状态性错误，不需要重试
            if (!response.isSuccessful) {
                return ParseResult(ParseStatus.NETWORK_ERROR, errorMessage = "HTTP $status", rawHtmlSnippet = snippet)
            }

            // 空响应体 → 可能是连接中断，可以重试
            if (html.isEmpty()) {
                return ParseResult(ParseStatus.NETWORK_ERROR, errorMessage = "Empty Body")
            }

            // 内容状态检测（CF/登录/验证码等）→ 状态性错误，不需要重试
            val detected = detectStatus(html)
            if (detected != null) {
                return ParseResult(detected, rawHtmlSnippet = snippet)
            }

            return ParseResult(ParseStatus.SUCCESS, data = html)

        } catch (e: IOException) {
            // IOException（连接超时、DNS 失败等）→ 瞬态错误，可以重试
            DebugLog.w("DiscuzClient", e) { "IOException: $url" }
            return ParseResult(ParseStatus.NETWORK_ERROR, errorMessage = e.message)
        } catch (e: Exception) {
            // 其他异常（如 URL 格式错误）→ 不太可能通过重试解决，但统一归为 NETWORK_ERROR
            DebugLog.w("DiscuzClient", e) { "Unexpected error: $url" }
            return ParseResult(ParseStatus.NETWORK_ERROR, errorMessage = e.message)
        } finally {
            response?.close()
        }
    }

    /**
     * 从 Content-Type 中提取 charset
     */
    private fun parseCharset(contentType: String?): String? {
        if (contentType.isNullOrBlank()) return null
        val matcher = charsetPattern.matcher(contentType)
        return if (matcher.find()) matcher.group(1) else null
    }

    /**
     * 页面内容状态检测（优化版：减少误判）
     * 返回 null 表示内容正常，可以继续解析
     */
    fun detectStatus(html: String): ParseStatus? {
        // 1. Cloudflare 检测
        if (html.contains("cdn-cgi") || html.contains("__CF\$cv_params") ||
            html.contains("Checking your browser") || html.contains("cf-chl-")) {
            return ParseStatus.CF_CHALLENGE
        }

        // 2. 需要登录 - 使用更严格的规则
        // 检查是否有明确的"登录提示"文字，而非仅仅导航栏的登录链接
        val hasLoginPrompt = html.contains("您需要先登录") ||
                             html.contains("您还未登录") ||
                             html.contains("对不起，您无权访问该版块") ||
                             html.contains("无权访问该版块")
        if (hasLoginPrompt) {
            return ParseStatus.NEED_LOGIN
        }

        // 3. 验证码
        if (html.contains("seccode") && html.contains("验证码")) {
            return ParseStatus.CAPTCHA_REQUIRED
        }

        // 4. 无权限（更明确的提示）
        if (html.contains("您无权进行当前操作")) {
            return ParseStatus.NO_PERMISSION
        }

        return null
    }
}
