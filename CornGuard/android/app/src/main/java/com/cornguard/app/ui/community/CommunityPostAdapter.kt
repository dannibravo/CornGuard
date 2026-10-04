package com.cornguard.app.ui.community

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.text.inSpans
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.cornguard.app.R
import com.cornguard.app.data.model.CommunityPost
import com.cornguard.app.databinding.ItemCommunityPostBinding
import com.cornguard.app.ui.common.DiseaseStyle
import com.cornguard.app.ui.common.TimeFormat

/** Feed posts (caps 3 design): author, text, photo, heart + comment counts, two comment previews. */
class CommunityPostAdapter(
    private val onLike: (CommunityPost) -> Unit,
    private val onComments: (CommunityPost) -> Unit
) : ListAdapter<CommunityPost, CommunityPostAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCommunityPostBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onLike, onComments)
    }

    class ViewHolder(private val binding: ItemCommunityPostBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(post: CommunityPost, onLike: (CommunityPost) -> Unit, onComments: (CommunityPost) -> Unit) {
            val context = binding.root.context
            binding.postAuthor.text = post.authorName.ifBlank { context.getString(R.string.default_farmer_name) }
            binding.postTime.text = listOf(TimeFormat.relative(context, post.createdAt), post.barangay)
                .filter { it.isNotBlank() }
                .joinToString(" · ")

            if (post.diseaseTag.isNotBlank()) {
                binding.postDiseaseBadge.visibility = View.VISIBLE
                DiseaseStyle.applyBadge(binding.postDiseaseBadge, post.diseaseTag)
            } else {
                binding.postDiseaseBadge.visibility = View.GONE
            }

            binding.postTitle.text = post.title
            binding.postTitle.visibility = if (post.title.isBlank()) View.GONE else View.VISIBLE
            binding.postBody.text = post.body
            binding.postBody.visibility = if (post.body.isBlank()) View.GONE else View.VISIBLE

            if (post.imageUrl.isNullOrBlank()) {
                binding.postImage.visibility = View.GONE
            } else {
                binding.postImage.visibility = View.VISIBLE
                binding.postImage.load(post.imageUrl) { crossfade(true) }
            }

            val likeColor = ContextCompat.getColor(
                context,
                if (post.likedByMe) R.color.cg_like else R.color.cg_text_secondary
            )
            binding.postLikeIcon.setImageResource(if (post.likedByMe) R.drawable.ic_heart else R.drawable.ic_heart_outline)
            binding.postLikeIcon.setColorFilter(likeColor)
            binding.postLikeCount.setTextColor(likeColor)
            binding.postLikeCount.text = post.upvoteCount.toString()
            binding.postCommentCount.text = post.commentCount.toString()

            binding.postCommentPreviews.removeAllViews()
            post.recentComments.forEach { comment ->
                binding.postCommentPreviews.addView(previewLine(context, comment.authorName, comment.body))
            }
            if (post.commentCount > post.recentComments.size) {
                binding.postViewAllComments.visibility = View.VISIBLE
                binding.postViewAllComments.text =
                    context.getString(R.string.community_view_all_comments, post.commentCount)
            } else {
                binding.postViewAllComments.visibility = View.GONE
            }

            binding.postLikeButton.setOnClickListener { onLike(post) }
            binding.postCommentButton.setOnClickListener { onComments(post) }
            binding.postViewAllComments.setOnClickListener { onComments(post) }
            binding.postCommentPreviews.setOnClickListener { onComments(post) }
        }

        /** "**Name**  comment text", as in the design's inline previews. */
        private fun previewLine(context: android.content.Context, name: String, body: String) =
            TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(ContextCompat.getColor(context, R.color.cg_text_secondary))
                maxLines = 2
                setPadding(0, (6 * resources.displayMetrics.density).toInt(), 0, 0)
                text = SpannableStringBuilder()
                    .inSpans(
                        StyleSpan(Typeface.BOLD),
                        ForegroundColorSpan(ContextCompat.getColor(context, R.color.cg_text_primary))
                    ) { append(name.ifBlank { context.getString(R.string.default_farmer_name) }) }
                    .append("  ")
                    .append(body)
            }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<CommunityPost>() {
            override fun areItemsTheSame(old: CommunityPost, new: CommunityPost) = old.postId == new.postId
            override fun areContentsTheSame(old: CommunityPost, new: CommunityPost) = old == new
        }
    }
}
