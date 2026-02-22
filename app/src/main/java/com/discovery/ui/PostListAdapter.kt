package com.discovery.ui

import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import androidx.core.text.HtmlCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.discovery.Constants
import com.discovery.R
import com.discovery.parser.model.PostItem
import com.discovery.util.HtmlImageGetter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI

/**
 * 帖子列表 Adapter
 *
 * 核心优化：HTML → Spanned 转换从主线程移到后台协程执行，
 * 避免滑动时因 fromHtml() 阻塞主线程导致卡顿。
 *
 * 渲染流程：
 * 1. 检查 LruCache 缓存命中 → 直接赋值
 * 2. 缓存未中 → 先显示纯文本占位 → 后台解析 HTML → 主线程更新
 * 3. ViewHolder 复用时自动取消旧的解析任务，防止内容错乱
 */
class PostListAdapter(
    private val scope: CoroutineScope
) : ListAdapter<PostItem, PostListAdapter.ViewHolder>(DiffCallback) {

    /** 内容字体大小（sp），可通过 updateFontSize() 动态调整 */
    var contentFontSizeSp: Float = 19f
        private set

    /** 更新字体大小并刷新所有 item */
    fun updateFontSize(sizeSp: Int) {
        contentFontSizeSp = sizeSp.toFloat()
        notifyDataSetChanged()
    }

    companion object {
        private const val HTML_CACHE_SIZE = 120
        private const val HTML_FLAGS = HtmlCompat.FROM_HTML_MODE_LEGACY

        // 移除 CSS 样式和脚本块，减少 fromHtml 解析负担
        private val STYLE_BLOCK_REGEX = Regex("(?is)<style[^>]*>.*?</style>")
        private val SCRIPT_BLOCK_REGEX = Regex("(?is)<script[^>]*>.*?</script>")
        private val IMG_CSS_REGEX = Regex("""(?im)^\n?\s*img\s*\{[^}]*\}\s*$""")
        private val A_CSS_REGEX = Regex("""(?im)^\n?\s*a\s*\{[^}]*\}\s*$""")
        private val BODY_CSS_REGEX = Regex("""(?im)^\n?\s*body\s*\{[^}]*\}\s*$""")

        // HTML 渲染结果缓存，避免相同帖子反复解析
        private val htmlSpannedCache = LruCache<String, CharSequence>(HTML_CACHE_SIZE)

        private val DiffCallback = object : DiffUtil.ItemCallback<PostItem>() {
            override fun areItemsTheSame(oldItem: PostItem, newItem: PostItem): Boolean {
                return oldItem.pid == newItem.pid
            }

            override fun areContentsTheSame(oldItem: PostItem, newItem: PostItem): Boolean {
                return oldItem == newItem
            }
        }

        /** 清除危险的 CSS/JS 标签，减轻 fromHtml 处理量 */
        private fun cleanHtml(rawHtml: String): String {
            return rawHtml
                .replace(STYLE_BLOCK_REGEX, "")
                .replace(SCRIPT_BLOCK_REGEX, "")
                .replace(IMG_CSS_REGEX, "")
                .replace(A_CSS_REGEX, "")
                .replace(BODY_CSS_REGEX, "")
        }

        private fun buildCacheKey(contentId: String, cleanedHtml: String): String {
            return "$contentId:${cleanedHtml.length}:${cleanedHtml.hashCode()}"
        }

        private fun resolveLinkUrl(rawUrl: String?): String? {
            val url = rawUrl?.trim().orEmpty()
            if (url.isBlank()) return null
            if (url.startsWith("#")) return null
            if (url.startsWith("javascript:", ignoreCase = true)) return null

            if (
                url.startsWith("http://", ignoreCase = true) ||
                url.startsWith("https://", ignoreCase = true) ||
                url.startsWith("mailto:", ignoreCase = true) ||
                url.startsWith("tel:", ignoreCase = true) ||
                url.startsWith("content://", ignoreCase = true) ||
                url.startsWith("file://", ignoreCase = true)
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

        private fun normalizeUrlSpans(text: CharSequence): CharSequence {
            if (text !is Spanned) return text

            val spannable = SpannableStringBuilder(text)
            val urlSpans = spannable.getSpans(0, spannable.length, URLSpan::class.java)
            for (span in urlSpans) {
                val start = spannable.getSpanStart(span)
                val end = spannable.getSpanEnd(span)
                val flags = spannable.getSpanFlags(span)
                spannable.removeSpan(span)

                val resolved = resolveLinkUrl(span.url)
                if (resolved != null) {
                    spannable.setSpan(URLSpan(resolved), start, end, flags)
                }
            }
            return spannable
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val avatar: TextView = itemView.findViewById(R.id.tvAvatar)
        val author: TextView = itemView.findViewById(R.id.tvAuthor)
        val time: TextView = itemView.findViewById(R.id.tvTime)
        val content: TextView = itemView.findViewById(R.id.tvContent)

        // 当前正在执行的 HTML 解析协程，复用时需要取消
        var parseJob: Job? = null

        init {
            // 同时支持：短按链接跳转 + 长按文字选择复制
            content.linksClickable = true
            content.setTextIsSelectable(true)
            content.movementMethod = LinkMovementMethod.getInstance()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.item_post, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val name = item.authorName.ifBlank { "?" }
        holder.avatar.text = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        holder.author.text = name
        holder.time.text = item.postTime

        val contentId = "${item.tid}:${item.pid}"
        holder.content.setTag(R.id.tag_html_content_id, contentId)

        // 动态设置字体大小
        holder.content.textSize = contentFontSizeSp

        // 取消此 ViewHolder 之前可能仍在运行的解析任务
        // 避免快速滑动时旧任务完成后覆盖新内容
        holder.parseJob?.cancel()

        val rawHtml = item.contentHtml.ifBlank { item.contentText }
        val cleanedHtml = cleanHtml(rawHtml)
        val cacheKey = buildCacheKey(contentId, cleanedHtml)

        // 缓存命中：直接赋值，零延迟（最常见路径）
        val cachedSpanned = htmlSpannedCache.get(cacheKey)
        if (cachedSpanned != null) {
            holder.content.text = cachedSpanned
            return
        }

        // 缓存未中：先显示纯文本占位，让用户立即看到内容概要
        holder.content.text = item.contentText.ifBlank { "..." }

        // 后台协程解析 HTML（CPU 密集，不阻塞主线程）
        holder.parseJob = scope.launch {
            // 在 Default 调度器上执行 fromHtml（利用 CPU 线程池）
            val imageGetter = HtmlImageGetter(holder.content, contentId)
            val spanned = withContext(Dispatchers.Default) {
                HtmlCompat.fromHtml(cleanedHtml, HTML_FLAGS, imageGetter, null)
            }
            val normalizedSpanned = normalizeUrlSpans(spanned)

            // 回到主线程更新 UI 之前，检查 ViewHolder 是否仍对应同一帖子
            // 防止快速滑动时旧结果覆盖新帖子
            val currentId = holder.content.getTag(R.id.tag_html_content_id) as? String
            if (currentId == contentId) {
                htmlSpannedCache.put(cacheKey, normalizedSpanned)
                holder.content.text = normalizedSpanned
            }
        }
    }

    /**
     * ViewHolder 被回收时取消未完成的解析任务
     * 避免协程泄漏和无效的 UI 更新
     */
    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.parseJob?.cancel()
        holder.parseJob = null
    }

    fun replaceAll(newItems: List<PostItem>) {
        submitList(newItems.toList())
    }
}
