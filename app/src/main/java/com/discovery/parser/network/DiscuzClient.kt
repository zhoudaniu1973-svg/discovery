package com.discovery.parser.network

import com.discovery.Constants
import com.discovery.parser.model.ParseResult
import com.discovery.parser.model.ParseStatus
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Discuz 网络客户端 (单例模式)
 * - 支持 GBK 解码
 * - 内置状态检测
 * - 连接池复用
 */
object DiscuzClient {

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

    fun fetch(url: String): ParseResult<String> {
        val request = Request.Builder()
            .url(url)
            .build()

        var response: Response? = null
        try {
            response = client.newCall(request).execute()

            val requestUrl = url
            val finalUrl = response.request.url.toString()
            val status = response.code
            val contentType = response.header("Content-Type")

            val bodyBytes = response.body?.bytes() ?: ByteArray(0)
            val bodyLength = bodyBytes.size
            
            // 动态解析 charset，默认 GBK
            val charset = parseCharset(contentType) ?: Constants.DEFAULT_CHARSET
            val html = if (bodyBytes.isNotEmpty()) String(bodyBytes, Charset.forName(charset)) else ""
            val snippet = if (html.length > 300) html.substring(0, 300) else html

            println("=== Network Debug ===")
            println("RequestUrl: $requestUrl")
            println("FinalUrl: $finalUrl")
            println("Status: $status")
            println("ContentType: $contentType")
            println("Charset: $charset")
            println("BodyBytesLength: $bodyLength")
            println("BodySnippet: $snippet")
            println("=====================")

            if (!response.isSuccessful) {
                return ParseResult(ParseStatus.NETWORK_ERROR, errorMessage = "HTTP $status", rawHtmlSnippet = snippet)
            }

            if (html.isEmpty()) {
                return ParseResult(ParseStatus.NETWORK_ERROR, errorMessage = "Empty Body", rawHtmlSnippet = snippet)
            }

            val detected = detectStatus(html)
            if (detected != null) {
                return ParseResult(detected, rawHtmlSnippet = snippet)
            }

            return ParseResult(ParseStatus.SUCCESS, data = html)

        } catch (e: Exception) {
            e.printStackTrace()
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
     * 状态检测 (优化版：减少误判)
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
        
        // 4. 无权限 (更明确的提示)
        if (html.contains("您无权进行当前操作")) {
            return ParseStatus.NO_PERMISSION
        }
        
        return null
    }
}
