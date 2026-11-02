package com.cornguard.app.ui.community

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.cornguard.app.data.model.ReportTarget
import com.cornguard.app.databinding.SheetCommentsBinding
import com.cornguard.app.di.ServiceLocator
import com.cornguard.app.ui.common.CornGuardBottomSheet
import kotlinx.coroutines.launch

/**
 * "Comments" sheet (caps 3 design). Comments are a live Convex subscription, so replies from
 * anyone appear while it's open. Adding a comment also notifies the post's author (server side).
 */
class CommentsBottomSheet : CornGuardBottomSheet() {

    private var _binding: SheetCommentsBinding? = null
    private val binding get() = _binding!!
    private val adapter = CommentsAdapter(
        onReport = { comment ->
            ReportBottomSheet.newInstance(ReportTarget.COMMENT, comment.commentId)
                .show(childFragmentManager, ReportBottomSheet.TAG)
        },
        currentUserId = { ServiceLocator.authRepository.getCurrentUser()?.uid }
    )
    private var commented = false

    private val postId: String get() = requireArguments().getString(ARG_POST_ID).orEmpty()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetCommentsBinding.inflate(inflater, container, false)
        binding.root.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, preferredHeight())
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog ?: return
        // Shrink the dialog's window for the keyboard instead of letting the keyboard cover the sheet.
        @Suppress("DEPRECATION")
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        // A fixed-height sheet can't shrink with that window: with the keyboard up its bottom (the
        // comment field) ended up behind the keyboard. Cap it at the room left, so the list gives up
        // the space and the header and input row stay on screen.
        val container = dialog.findViewById<View>(com.google.android.material.R.id.coordinator) ?: return
        container.addOnLayoutChangeListener(fitToContainer)
        container.post { fitToContainer.onLayoutChange(container, 0, 0, 0, 0, 0, 0, 0, 0) }
    }

    private val fitToContainer = View.OnLayoutChangeListener { container, _, _, _, _, _, _, _, _ ->
        val root = _binding?.root ?: return@OnLayoutChangeListener
        val height = minOf(preferredHeight(), container.height)
        if (container.height > 0 && root.layoutParams.height != height) {
            root.layoutParams = root.layoutParams.apply { this.height = height }
        }
    }

    /** The design's comments sheet covers ~80% of the screen. */
    private fun preferredHeight() = (resources.displayMetrics.heightPixels * 0.8).toInt()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.commentsClose.setOnClickListener { dismiss() }
        binding.commentsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.commentsRecyclerView.adapter = adapter
        binding.commentsSend.setOnClickListener { send() }
        // A reported comment drops out of the live list by itself; the feed's counts and previews
        // refresh when this sheet closes.
        childFragmentManager.setFragmentResultListener(CommunityFragment.RESULT_FEED_CHANGED, viewLifecycleOwner) { _, _ ->
            commented = true
        }
        observeComments()
    }

    private fun observeComments() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                runCatching { ServiceLocator.communityRepository.observeComments(postId) }
                    .getOrNull()?.collect { comments ->
                        adapter.submitList(comments) {
                            if (comments.isNotEmpty()) binding.commentsRecyclerView.scrollToPosition(comments.lastIndex)
                        }
                        binding.commentsEmpty.visibility = if (comments.isEmpty()) View.VISIBLE else View.GONE
                    }
            }
        }
    }

    private fun send() {
        val user = ServiceLocator.authRepository.getCurrentUser() ?: return
        val body = binding.commentsInput.text.toString().trim()
        if (body.isEmpty()) return
        binding.commentsSend.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching { ServiceLocator.communityRepository.addComment(postId, user.uid, body) }
            binding.commentsSend.isEnabled = true
            if (result.isSuccess) {
                binding.commentsInput.text.clear()
                commented = true
            }
        }
    }

    override fun onDestroyView() {
        dialog?.findViewById<View>(com.google.android.material.R.id.coordinator)
            ?.removeOnLayoutChangeListener(fitToContainer)
        // Let the feed refresh its comment counts and previews.
        if (commented) setFragmentResult(CommunityFragment.RESULT_FEED_CHANGED, Bundle())
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "CommentsBottomSheet"
        private const val ARG_POST_ID = "postId"

        fun newInstance(postId: String) = CommentsBottomSheet().apply {
            arguments = bundleOf(ARG_POST_ID to postId)
        }
    }
}
