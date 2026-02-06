package com.discovery.ui

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
) : ListAdapter<PostListAdapter.PostListItem, RecyclerView.ViewHolder>(DiffCallback) {

    sealed class PostListItem {
        data class Post(val item: PostItem) : PostListItem()
        data class Footer(val onlyAuthorEnabled: Boolean) : PostListItem()
    }

    companion object {
        private const val TYPE_POST = 0
        private const val TYPE_FOOTER = 1

        private const val IMAGE_CSS = """
            <style>
                img { 
                    width: 100%% !important;
                    max-width: 100%% !important;
                    height: auto !important;
                    display: block;
                    margin: 10px 0;
                }
                a { color: #2F7DB8; }
                body { word-wrap: break-word; }
            </style>
        """

        private val DiffCallback = object : DiffUtil.ItemCallback<PostListItem>() {
            override fun areItemsTheSame(oldItem: PostListItem, newItem: PostListItem): Boolean {
                return when {
                    oldItem is PostListItem.Post && newItem is PostListItem.Post -> oldItem.item.pid == newItem.item.pid
                    oldItem is PostListItem.Footer && newItem is PostListItem.Footer -> true
                    else -> false
                }
            }

            override fun areContentsTheSame(oldItem: PostListItem, newItem: PostListItem): Boolean {
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
    }

    class FooterViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val reply: Button = itemView.findViewById(R.id.btnReply)
        val onlyAuthor: Button = itemView.findViewById(R.id.btnOnlyAuthor)
    }

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position) is PostListItem.Footer) TYPE_FOOTER else TYPE_POST
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
        val item = (getItem(position) as PostListItem.Post).item
        val name = item.authorName.ifBlank { "?" }
        postHolder.avatar.text = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        postHolder.author.text = name
        postHolder.time.text = item.postTime
        
        val rawHtml = item.contentHtml.ifBlank { item.contentText }
        val withStyle = if (rawHtml.contains("<style", ignoreCase = true)) {
            rawHtml
        } else {
            IMAGE_CSS + rawHtml
        }
        val imageGetter = HtmlImageGetter(postHolder.content, postHolder.content.context)
        val spanned = HtmlCompat.fromHtml(withStyle, HtmlCompat.FROM_HTML_MODE_LEGACY, imageGetter, null)
        postHolder.content.text = spanned
    }

    fun replaceAll(newItems: List<PostItem>) {
        submitList(buildItems(newItems, onlyAuthorEnabled))
    }

    fun appendAll(newItems: List<PostItem>) {
        val currentPosts = currentList.filterIsInstance<PostListItem.Post>().map { it.item }
        submitList(buildItems(currentPosts + newItems, onlyAuthorEnabled))
    }

    fun setOnlyAuthorEnabled(enabled: Boolean) {
        onlyAuthorEnabled = enabled
        val currentPosts = currentList.filterIsInstance<PostListItem.Post>().map { it.item }
        submitList(buildItems(currentPosts, onlyAuthorEnabled))
    }

    private fun buildItems(posts: List<PostItem>, onlyAuthor: Boolean): List<PostListItem> {
        val items = posts.map { PostListItem.Post(it) }.toMutableList<PostListItem>()
        items.add(PostListItem.Footer(onlyAuthor))
        return items
    }
}
