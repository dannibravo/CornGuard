package com.cornguard.app.ui.community

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.cornguard.app.R
import com.cornguard.app.data.model.CommunityPost
import com.cornguard.app.databinding.FragmentPostDetailBinding
import com.cornguard.app.di.ServiceLocator
import kotlinx.coroutines.launch

/**
 * Post detail + comments + upvote, reached from [CommunityFragment]'s feed (signed-in only, so
 * [ServiceLocator.authRepository.getCurrentUser] is expected non-null for the reply/upvote
 * actions here).
 */
class PostDetailFragment : Fragment() {

    private var _binding: FragmentPostDetailBinding? = null
    private val binding get() = _binding!!
    private val commentsAdapter = CommentsAdapter()
    private var isUpvoted = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPostDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val postId = requireArguments().getString("postId") ?: error("PostDetailFragment requires a postId argument")

        binding.commentsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.commentsRecyclerView.adapter = commentsAdapter

        loadPost(postId)
        observeComments(postId)

        binding.postUpvoteButton.setOnClickListener { toggleUpvote(postId) }
        binding.postReplyButton.setOnClickListener { submitReply(postId) }
    }

    private fun loadPost(postId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val post = runCatching { ServiceLocator.communityRepository.getPost(postId) }.getOrNull()
            if (post != null) render(post)
        }
    }

    private fun render(post: CommunityPost) {
        binding.postDetailTitle.text = post.title
        binding.postDetailMeta.text = "${post.diseaseTag} • ${post.barangay}"
        binding.postDetailBody.text = post.body
        binding.postUpvoteCount.text = getString(R.string.post_upvote_count_format, post.upvoteCount)
    }

    private fun observeComments(postId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                runCatching { ServiceLocator.communityRepository.observeComments(postId) }
                    .getOrNull()?.collect { comments ->
                        commentsAdapter.submitList(comments)
                        binding.postCommentsEmpty.visibility = if (comments.isEmpty()) View.VISIBLE else View.GONE
                    }
            }
        }
    }

    private fun toggleUpvote(postId: String) {
        val user = ServiceLocator.authRepository.getCurrentUser() ?: return
        binding.postUpvoteButton.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching { ServiceLocator.communityRepository.toggleUpvote(postId, user.uid) }
            binding.postUpvoteButton.isEnabled = true
            result.getOrNull()?.let { nowUpvoted ->
                isUpvoted = nowUpvoted
                binding.postUpvoteButton.setText(
                    if (isUpvoted) R.string.post_upvote_action_active else R.string.post_upvote_action
                )
                loadPost(postId)
            }
        }
    }

    private fun submitReply(postId: String) {
        val user = ServiceLocator.authRepository.getCurrentUser() ?: return
        val body = binding.postReplyInput.text.toString().trim()
        if (body.isEmpty()) return
        binding.postReplyButton.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { ServiceLocator.communityRepository.addComment(postId, user.uid, body) }
            binding.postReplyButton.isEnabled = true
            binding.postReplyInput.text.clear()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
