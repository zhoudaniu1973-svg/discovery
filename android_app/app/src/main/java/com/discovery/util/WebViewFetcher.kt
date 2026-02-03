package com.discovery.util

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.discovery.Constants
import com.discovery.parser.model.ParseStatus
import com.discovery.parser.network.DiscuzClient
import org.json.JSONArray

/**
 * WebView 抓取辅助类
 * 用于绕过 Cloudflare 等反爬虫机制
 */
class WebViewFetcher(
    private val context: Context,
    private val container: ViewGroup
) {
    private var webView: WebView? = null
    
    interface Callback {
        fun onSuccess(html: String)
        fun onStatusDetected(status: ParseStatus)
        fun onError(message: String)
    }
    
    /**
     * 使用 WebView 抓取页面
     */
    fun fetch(url: String, callback: Callback) {
        val wv = getOrCreateWebView()
        
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, finishedUrl: String) {
                view.evaluateJavascript("(function(){return document.documentElement.outerHTML;})()") { htmlJson ->
                    val html = decodeHtml(htmlJson)
                    if (html.isNullOrBlank()) {
                        callback.onError("WebView fetch failed: empty content")
                        return@evaluateJavascript
                    }
                    
                    val detected = DiscuzClient.detectStatus(html)
                    if (detected != null) {
                        callback.onStatusDetected(detected)
                        return@evaluateJavascript
                    }
                    
                    callback.onSuccess(html)
                }
            }
        }
        
        wv.loadUrl(url)
    }
    
    private fun getOrCreateWebView(): WebView {
        webView?.let { return it }
        
        val wv = WebView(context).apply {
            visibility = View.GONE
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = Constants.DESKTOP_UA
        }
        
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(wv, true)
        }
        
        container.addView(wv, ViewGroup.LayoutParams(1, 1))
        webView = wv
        return wv
    }
    
    private fun decodeHtml(htmlJson: String?): String? {
        if (htmlJson.isNullOrBlank()) return null
        return try {
            JSONArray("[$htmlJson]").getString(0)
        } catch (e: Exception) {
            null
        }
    }
    
    fun destroy() {
        webView?.let {
            container.removeView(it)
            it.destroy()
        }
        webView = null
    }
}
