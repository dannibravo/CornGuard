package com.cornguard.app.ui.community

import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cornguard.app.R
import com.cornguard.app.data.model.Comment
import com.cornguard.app.databinding.ItemCommentBinding
import com.cornguard.app.ui.common.TimeFormat

/**
 * Comment rows. Long-pressing someone else's comment offers "Report comment" (kept off the row
 * itself so the list stays uncluttered); [currentUserId] decides whose comment it is.
 */
class CommentsAdapter(
    private val onReport: (Comment) -> Unit,
    private val currentUserId: () -> String?
) : ListAdapter<Comment, CommentsAdapter.ViewHolder>(DIFF) {

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

        val viewerId = currentUserId()
        if (viewerId != null && comment.userId != viewerId) {
            holder.binding.root.setOnLongClickListener { row ->
                PopupMenu(row.context, holder.binding.commentBody, Gravity.END).apply {
                    menu.add(R.string.report_comment_action)
                    setOnMenuItemClickListener { onReport(comment); true }
                }.show()
                true
            }
        } else {
            holder.binding.root.setOnLongClickListener(null)
            holder.binding.root.isLongClickable = false
        }
    }

    class ViewHolder(val binding: ItemCommentBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Comment>() {
            override fun areItemsTheSame(old: Comment, new: Comment) = old.commentId == new.commentId
            override fun areContentsTheSame(old: Comment, new: Comment) = old == new
        }
    }
}
