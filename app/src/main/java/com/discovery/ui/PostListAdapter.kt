package com.discovery.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebSettings
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.discovery.R
import com.discovery.parser.model.PostItem

class PostListAdapter(
    private val items: MutableList<PostItem>,
    private val onReplyClick: () -> Unit,
    private val onOnlyAuthorClick: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_POST = 0
        private const val TYPE_FOOTER = 1
        
        // WebView 内容模板，支持图片自适应
        private const val HTML_TEMPLATE = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body { 
                        margin: 0; 
                        padding: 0; 
                        font-size: 16px; 
                        line-height: 1.6;
                        color: #111111;
                        word-wrap: break-word;
                    }
                    img { 
                        width: 100%% !important;
                        max-width: 100%% !important;
                        height: auto !important;
                        display: block;
                        margin: 10px 0;
                    }
                    a { color: #2F7DB8; }
                </style>
            </head>
            <body>%s</body>
            </html>
        """
    }

    private var onlyAuthorEnabled = false

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val avatar: TextView = itemView.findViewById(R.id.tvAvatar)
        val author: TextView = itemView.findViewById(R.id.tvAuthor)
        val time: TextView = itemView.findViewById(R.id.tvTime)
        val webView: WebView = itemView.findViewById(R.id.wvContent)
        
        init {
            // 配置 WebView
            webView.settings.apply {
                javaScriptEnabled = false
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = false
                displayZoomControls = false
                cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
            }
            webView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
    }

    class FooterViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val reply: Button = itemView.findViewById(R.id.btnReply)
        val onlyAuthor: Button = itemView.findViewById(R.id.btnOnlyAuthor)
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == items.size) TYPE_FOOTER else TYPE_POST
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_FOOTER) {
            val view = inflater.inflate(R.layout.item_post_footer, parent, false)
            FooterViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_post, parent, false)
            ViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is FooterViewHolder) {
            holder.reply.setOnClickListener { onReplyClick() }
            holder.onlyAuthor.text = if (onlyAuthorEnabled) "取消只看作者" else "只看作者"
            holder.onlyAuthor.setOnClickListener { onOnlyAuthorClick() }
            return
        }
        val postHolder = holder as ViewHolder
        val item = items[position]
        val name = item.authorName.ifBlank { "?" }
        postHolder.avatar.text = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        postHolder.author.text = name
        postHolder.time.text = item.postTime
        
        // 使用 WebView 加载 HTML 内容（支持图片）
        val htmlContent = item.contentHtml.ifBlank { item.contentText }
        val fullHtml = String.format(HTML_TEMPLATE, htmlContent)
        postHolder.webView.loadDataWithBaseURL(
            "https://www.4d4y.com/forum/",
            fullHtml,
            "text/html",
            "UTF-8",
            null
        )
    }

    override fun getItemCount(): Int = items.size + 1

    fun replaceAll(newItems: List<PostItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun appendAll(newItems: List<PostItem>) {
        val start = items.size
        items.addAll(newItems)
        notifyItemRangeInserted(start, newItems.size)
    }

    fun setOnlyAuthorEnabled(enabled: Boolean) {
        onlyAuthorEnabled = enabled
        notifyItemChanged(items.size)
    }
}
