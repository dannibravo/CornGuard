package com.cornguard.app.ui.community

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.cornguard.app.R
import com.cornguard.app.data.model.CommunityPost
import com.cornguard.app.databinding.FragmentCommunityBinding
import com.cornguard.app.di.ServiceLocator
import kotlinx.coroutines.launch

/**
 * Community feed (caps 3 design's Global Feed). Online-only and sign-in gated. The + button opens
 * [CreatePostBottomSheet]; the comment icon opens [CommentsBottomSheet]; the heart toggles the
 * existing upvote.
 */
class CommunityFragment : Fragment() {

    private var _binding: FragmentCommunityBinding? = null
    private val binding get() = _binding!!
    private val adapter = CommunityPostAdapter(onLike = ::toggleLike, onComments = ::openComments)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCommunityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.communityHeader.headerTitle.setText(R.string.community_title)
        binding.communityHeader.headerBackButton.setOnClickListener { findNavController().popBackStack() }

        binding.communityRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.communityRecyclerView.adapter = adapter
        binding.communitySignInButton.setOnClickListener { findNavController().navigate(R.id.authFragment) }
        binding.communityCreatePostButton.setOnClickListener {
            CreatePostBottomSheet().show(childFragmentManager, CreatePostBottomSheet.TAG)
        }
        binding.communitySwipeRefresh.setColorSchemeResources(R.color.cg_primary)
        binding.communitySwipeRefresh.setOnRefreshListener { loadFeed() }

        // Sheets report back so the feed shows new posts, comments and counts.
        childFragmentManager.setFragmentResultListener(RESULT_FEED_CHANGED, viewLifecycleOwner) { _, _ -> loadFeed() }

        observeAuthState()
    }

    private fun observeAuthState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                ServiceLocator.authRepository.observeAuthState().collect { user ->
                    val signedIn = user != null
                    binding.communitySignedOut.visibility = if (signedIn) View.GONE else View.VISIBLE
                    binding.communityCreatePostButton.visibility = if (signedIn) View.VISIBLE else View.GONE
                    binding.communitySwipeRefresh.visibility = if (signedIn) View.VISIBLE else View.GONE
                    if (signedIn) loadFeed() else binding.communityEmptyState.visibility = View.GONE
                }
            }
        }
    }

    private fun loadFeed() {
        binding.communitySwipeRefresh.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                ServiceLocator.communityRepository.getPostsFeed(areaFilter = null, diseaseTag = null)
            }
            binding.communitySwipeRefresh.isRefreshing = false
            val posts = result.getOrNull()
            if (posts == null) {
                binding.communityEmptyState.visibility = View.VISIBLE
                binding.communityEmptyState.setText(R.string.community_load_failed)
                return@launch
            }
            adapter.submitList(posts)
            binding.communityEmptyState.setText(R.string.community_empty)
            binding.communityEmptyState.visibility = if (posts.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    /** Optimistic like toggle, then the server's counts replace it. */
    private fun toggleLike(post: CommunityPost) {
        val user = ServiceLocator.authRepository.getCurrentUser() ?: return
        val optimistic = post.copy(
            likedByMe = !post.likedByMe,
            upvoteCount = (post.upvoteCount + if (post.likedByMe) -1 else 1).coerceAtLeast(0)
        )
        adapter.submitList(adapter.currentList.map { if (it.postId == post.postId) optimistic else it })
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { ServiceLocator.communityRepository.toggleUpvote(post.postId, user.uid) }
            loadFeed()
        }
    }

    private fun openComments(post: CommunityPost) {
        CommentsBottomSheet.newInstance(post.postId).show(childFragmentManager, CommentsBottomSheet.TAG)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val RESULT_FEED_CHANGED = "community_feed_changed"
    }
}
