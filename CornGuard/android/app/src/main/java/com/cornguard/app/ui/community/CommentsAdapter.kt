package com.cornguard.app.ui.community

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cornguard.app.R
import com.cornguard.app.data.model.Comment
import com.cornguard.app.databinding.ItemCommentBinding
import com.cornguard.app.ui.common.TimeFormat

class CommentsAdapter : ListAdapter<Comment, CommentsAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val comment = getItem(position)
        val context = holder.binding.root.context
        holder.binding.commentAuthor.text =
            comment.authorName.ifBlank { context.getString(R.string.default_farmer_name) }
        holder.binding.commentTime.text = TimeFormat.relative(context, comment.createdAt)
        holder.binding.commentBody.text = comment.body
    }

    class ViewHolder(val binding: ItemCommentBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Comment>() {
            override fun areItemsTheSame(old: Comment, new: Comment) = old.commentId == new.commentId
            override fun areContentsTheSame(old: Comment, new: Comment) = old == new
        }
    }
}
