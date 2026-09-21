package com.cornguard.app.ui.community

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
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
 * Location-aware community feed. Online-only (claude/01_MASTER_DEVELOPMENT_CONTEXT.md Project
 * Principle) — signed-out users see a sign-in prompt here rather than the app gating at launch.
 * Feed loading is a one-shot suspend call ([com.cornguard.app.data.repository.CommunityRepository.getPostsFeed]),
 * not a live listener, per that interface's contract; pull-to-refresh is a later polish item, not
 * required for this to be real, working code.
 */
class CommunityFragment : Fragment() {

    private var _binding: FragmentCommunityBinding? = null
    private val binding get() = _binding!!
    private val adapter = CommunityPostAdapter(onClick = ::openPost)

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
        binding.communityRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.communityRecyclerView.adapter = adapter

        binding.communitySignInButton.setOnClickListener {
            findNavController().navigate(R.id.authFragment)
        }
        binding.communityCreatePostButton.setOnClickListener {
            findNavController().navigate(R.id.action_community_to_createPost)
        }

        observeAuthState()
    }

    private fun observeAuthState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                ServiceLocator.authRepository.observeAuthState().collect { user ->
                    val signedIn = user != null
                    binding.communitySignInPrompt.visibility = if (signedIn) View.GONE else View.VISIBLE
                    binding.communitySignInButton.visibility = if (signedIn) View.GONE else View.VISIBLE
                    binding.communityCreatePostButton.visibility = if (signedIn) View.VISIBLE else View.GONE
                    if (signedIn) {
                        loadFeed()
                    } else {
                        binding.communityRecyclerView.visibility = View.GONE
                        binding.communityEmptyState.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun loadFeed() {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                ServiceLocator.communityRepository.getPostsFeed(areaFilter = null, diseaseTag = null)
            }
            val posts = result.getOrNull()
            if (posts == null) {
                binding.communityRecyclerView.visibility = View.GONE
                binding.communityEmptyState.visibility = View.VISIBLE
                binding.communityEmptyState.text = getString(R.string.community_load_failed)
                return@launch
            }
            adapter.submitList(posts)
            binding.communityEmptyState.text = getString(R.string.community_empty)
            binding.communityRecyclerView.visibility = if (posts.isEmpty()) View.GONE else View.VISIBLE
            binding.communityEmptyState.visibility = if (posts.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun openPost(post: CommunityPost) {
        findNavController().navigate(
            R.id.action_community_to_postDetail,
            bundleOf("postId" to post.postId)
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
