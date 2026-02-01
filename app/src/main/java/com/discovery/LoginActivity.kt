package com.discovery

import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.TextView
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.TaskStackBuilder
import com.discovery.parser.network.CookieStore
import com.discovery.parser.model.ParseStatus
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.discovery.parser.network.DiscuzClient

class LoginActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnEnter: Button
    private lateinit var webView: WebView
    private var reasonText: String? = null
    private var allowAutoJump = true
    private var hasJumped = false
    private var isVerifying = false
    private var pendingNativeUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        tvStatus = findViewById(R.id.tvLoginStatus)
        btnEnter = findViewById(R.id.btnEnter)
        webView = findViewById(R.id.webLogin)

        reasonText = intent.getStringExtra(EXTRA_REASON)
        allowAutoJump = reasonText.isNullOrBlank() || reasonText == ParseStatus.NEED_LOGIN.name
        if (!reasonText.isNullOrBlank()) {
            tvStatus.text = "Login required: $reasonText"
        }

        CookieStore.load(this)

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.textZoom = 125
        settings.userAgentString = Constants.DESKTOP_UA

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return handleForumNavigation(view, url, cookieManager)
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean {
                return handleForumNavigation(view, request.url.toString(), cookieManager)
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                val cookie = mergeCookies(
                    cookieManager.getCookie(url),
                    cookieManager.getCookie(BASE_DOMAIN_URL),
                    cookieManager.getCookie(BASE_FORUM_URL)
                )
                val loggedIn = isLoggedIn(cookie)
                val cfCleared = isCloudflareCleared(cookie)
                val ready = loggedIn || cfCleared

                if (loggedIn) {
                    tvStatus.text = "Login: OK"
                } else if (cfCleared) {
                    tvStatus.text = "Cloudflare OK, please login if needed"
                } else {
                    tvStatus.text = reasonText?.let { "Login required: $it" } ?: "Login: waiting..."
                }

                btnEnter.isEnabled = ready

                val shouldAutoJump = if (reasonText == ParseStatus.CF_CHALLENGE.name) {
                    loggedIn && cfCleared
                } else {
                    loggedIn || (allowAutoJump && cfCleared)
                }

                if (loggedIn) {
                    if (openNativeIfForumUrl(url, cookie)) {
                        return
                    }
                }

                if (shouldAutoJump && !hasJumped && loggedIn) {
                    verifyAndOpenNative(LIST_URL, cookie)
                }
            }
        }

        btnEnter.setOnClickListener {
            val currentUrl = webView.url ?: BASE_FORUM_URL
            val cookie = mergeCookies(
                cookieManager.getCookie(currentUrl),
                cookieManager.getCookie(BASE_DOMAIN_URL),
                cookieManager.getCookie(BASE_FORUM_URL)
            )
            if (!cookie.isNullOrBlank()) {
                CookieStore.save(this, cookie)
            }
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }

        val startUrl = if (reasonText == ParseStatus.CF_CHALLENGE.name) {
            LIST_URL
        } else {
            LOGIN_URL
        }
        webView.loadUrl(startUrl)
    }

    private fun isLoggedIn(cookie: String?): Boolean {
        if (cookie.isNullOrBlank()) return false
        val parts = cookie.split(';')
        for (part in parts) {
            val kv = part.trim().split('=', limit = 2)
            if (kv.size == 2 && kv[0] == "discuz_uid") {
                return kv[1].isNotBlank() && kv[1] != "0"
            }
            if (kv.size == 2 && kv[0].endsWith("_uid")) {
                return kv[1].isNotBlank() && kv[1] != "0"
            }
            if (kv.size == 2 && (kv[0] == "discuz_auth" || kv[0].endsWith("_auth"))) {
                return kv[1].isNotBlank()
            }
        }
        return false
    }

    private fun isCloudflareCleared(cookie: String?): Boolean {
        if (cookie.isNullOrBlank()) return false
        val parts = cookie.split(';')
        for (part in parts) {
            val kv = part.trim().split('=', limit = 2)
            if (kv.isNotEmpty() && kv[0] == "cf_clearance") {
                return kv.size == 2 && kv[1].isNotBlank()
            }
        }
        return false
    }

    private fun handleForumNavigation(view: WebView, url: String, cookieManager: CookieManager): Boolean {
        val lower = url.lowercase()
        if (lower.contains("forumdisplay.php") || lower.contains("viewthread.php")) {
            val cookie = mergeCookies(
                cookieManager.getCookie(url),
                cookieManager.getCookie(BASE_DOMAIN_URL),
                cookieManager.getCookie(BASE_FORUM_URL)
            )
            if (isLoggedIn(cookie)) {
                verifyAndOpenNative(url, cookie)
                return true
            }
            view.post { view.loadUrl(LOGIN_URL) }
            return true
        }
        return false
    }

    private fun openNativeIfForumUrl(url: String, cookie: String?): Boolean {
        val lower = url.lowercase()
        if (!lower.contains("forumdisplay.php") && !lower.contains("viewthread.php")) {
            return false
        }
        if (cookie.isNullOrBlank()) return false
        if (!isLoggedIn(cookie)) return false
        verifyAndOpenNative(url, cookie)
        return true
    }

    private fun verifyAndOpenNative(targetUrl: String, cookie: String?) {
        if (hasJumped) return
        if (cookie.isNullOrBlank()) return
        if (isVerifying) {
            pendingNativeUrl = targetUrl
            return
        }
        isVerifying = true
        pendingNativeUrl = targetUrl
        CookieStore.save(this@LoginActivity, cookie)
        CoroutineScope(Dispatchers.IO).launch {
            val status = DiscuzClient.fetch(LIST_URL).status
            withContext(Dispatchers.Main) {
                isVerifying = false
                if (status == ParseStatus.SUCCESS) {
                    openNativeForUrl(pendingNativeUrl ?: LIST_URL)
                    return@withContext
                }
                // Even if native is blocked by CF, go native and let WebView fallback fetch there.
                openNativeForUrl(pendingNativeUrl ?: LIST_URL)
            }
        }
    }

    private fun openNativeForUrl(url: String) {
        if (hasJumped) return
        hasJumped = true
        webView.visibility = View.GONE
        if (url.lowercase().contains("viewthread.php")) {
            val tid = extractQueryParamFromUrl(url, "tid") ?: "0"
            val mainIntent = Intent(this@LoginActivity, MainActivity::class.java)
            val detailIntent = Intent(this@LoginActivity, DetailActivity::class.java)
            detailIntent.putExtra(DetailActivity.EXTRA_TID, tid)
            detailIntent.putExtra(DetailActivity.EXTRA_TITLE, "Thread")
            detailIntent.putExtra(DetailActivity.EXTRA_MAX_PAGE, 1)
            TaskStackBuilder.create(this@LoginActivity)
                .addNextIntent(mainIntent)
                .addNextIntent(detailIntent)
                .startActivities()
            return
        }
        val intent = Intent(this@LoginActivity, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    private fun extractQueryParamFromUrl(url: String, key: String): String? {
        val http = url.toHttpUrlOrNull() ?: return null
        return http.queryParameter(key)
    }

    private fun mergeCookies(vararg cookies: String?): String? {
        val map = linkedMapOf<String, String>()
        for (cookie in cookies) {
            if (cookie.isNullOrBlank()) continue
            val parts = cookie.split(';')
            for (part in parts) {
                val kv = part.trim().split('=', limit = 2)
                if (kv.isNotEmpty() && kv[0].isNotBlank()) {
                    map[kv[0]] = if (kv.size > 1) kv[1] else ""
                }
            }
        }
        if (map.isEmpty()) return null
        return map.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    companion object {
        private val BASE_DOMAIN_URL = Constants.BASE_DOMAIN
        private val BASE_FORUM_URL = Constants.BASE_FORUM_URL
        private val LOGIN_URL = Constants.LOGIN_URL
        private val LIST_URL = Constants.buildForumDisplayUrl()
        const val EXTRA_REASON = "extra_reason"
    }
}
