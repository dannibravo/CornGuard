package com.cornguard.app.ui.community

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cornguard.app.data.model.CommunityPost
import com.cornguard.app.databinding.ItemCommunityPostBinding

class CommunityPostAdapter(
    private val onClick: (CommunityPost) -> Unit
) : ListAdapter<CommunityPost, CommunityPostAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCommunityPostBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onClick)
    }

    class ViewHolder(private val binding: ItemCommunityPostBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(post: CommunityPost, onClick: (CommunityPost) -> Unit) {
            binding.postTitle.text = post.title
            binding.postMeta.text = "${post.diseaseTag} • ${post.barangay} • ${post.upvoteCount} helpful"
            binding.root.setOnClickListener { onClick(post) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<CommunityPost>() {
            override fun areItemsTheSame(old: CommunityPost, new: CommunityPost) = old.postId == new.postId
            override fun areContentsTheSame(old: CommunityPost, new: CommunityPost) = old == new
        }
    }
}
