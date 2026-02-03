package com.discovery.ui

import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.discovery.R
import com.discovery.parser.model.PostItem
import com.discovery.util.HtmlImageGetter

class PostListAdapter(
    private val onReplyClick: () -> Unit,
    private val onOnlyAuthorClick: () -> Unit
) : ListAdapter<PostItem, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

    companion object {
        private const val TYPE_POST = 0
        private const val TYPE_FOOTER = 1

        private const val HTML_FLAGS = HtmlCompat.FROM_HTML_MODE_LEGACY

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<PostItem>() {
            override fun areItemsTheSame(oldItem: PostItem, newItem: PostItem): Boolean {
                return oldItem.tid == newItem.tid && oldItem.pid == newItem.pid
            }

            override fun areContentsTheSame(oldItem: PostItem, newItem: PostItem): Boolean {
                return oldItem == newItem
            }
        }
    }

    private var onlyAuthorEnabled = false

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val avatar: TextView = itemView.findViewById(R.id.tvAvatar)
        val author: TextView = itemView.findViewById(R.id.tvAuthor)
        val time: TextView = itemView.findViewById(R.id.tvTime)
        val content: TextView = itemView.findViewById(R.id.tvContent)

        init {
            content.movementMethod = LinkMovementMethod.getInstance()
            content.linksClickable = true
        }
    }

    class FooterViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val reply: Button = itemView.findViewById(R.id.btnReply)
        val onlyAuthor: Button = itemView.findViewById(R.id.btnOnlyAuthor)
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == currentList.size) TYPE_FOOTER else TYPE_POST
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
        val item = getItem(position)
        val name = item.authorName.ifBlank { "?" }
        postHolder.avatar.text = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        postHolder.author.text = name
        postHolder.time.text = item.postTime

        val htmlContent = item.contentHtml.ifBlank { item.contentText }
        val contentId = "${item.tid}:${item.pid}"
        postHolder.content.setTag(R.id.tag_html_content_id, contentId)
        val imageGetter = HtmlImageGetter(postHolder.content, contentId = contentId)
        val spanned = HtmlCompat.fromHtml(htmlContent, HTML_FLAGS, imageGetter, null)
        postHolder.content.text = spanned
    }

    override fun getItemCount(): Int = currentList.size + 1

    fun setOnlyAuthorEnabled(enabled: Boolean) {
        onlyAuthorEnabled = enabled
        notifyItemChanged(currentList.size)
    }
}
