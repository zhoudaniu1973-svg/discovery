package com.discovery.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.discovery.R
import com.discovery.parser.model.ThreadListItem

class ThreadListAdapter(
    private val onItemClick: (ThreadListItem) -> Unit
) : ListAdapter<ThreadListItem, ThreadListAdapter.ViewHolder>(DiffCallback) {

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<ThreadListItem>() {
            override fun areItemsTheSame(oldItem: ThreadListItem, newItem: ThreadListItem): Boolean {
                return oldItem.tid == newItem.tid
            }

            override fun areContentsTheSame(oldItem: ThreadListItem, newItem: ThreadListItem): Boolean {
                return oldItem == newItem
            }
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val avatar: TextView = itemView.findViewById(R.id.tvAvatar)
        val author: TextView = itemView.findViewById(R.id.tvAuthor)
        val metaRight: TextView = itemView.findViewById(R.id.tvMetaRight)
        val title: TextView = itemView.findViewById(R.id.tvTitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_thread, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val name = item.authorName.ifBlank { "?" }
        holder.avatar.text = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        holder.author.text = name
        val date = item.postDate.ifBlank { "" }
        val counts = "${item.replies}/${item.views}"
        holder.metaRight.text = if (date.isBlank()) counts else "$counts $date"
        holder.title.text = item.title.ifBlank { "(no title)" }

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    fun replaceAll(newItems: List<ThreadListItem>) {
        submitList(newItems)
    }
}
