package com.cornguard.app.ui.community

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import coil.load
import com.cornguard.app.R
import com.cornguard.app.data.model.CommunityPost
import com.cornguard.app.data.model.DraftPost
import com.cornguard.app.databinding.SheetCreatePostBinding
import com.cornguard.app.di.ServiceLocator
import com.cornguard.app.model.DiseaseCode
import com.cornguard.app.ui.common.CornGuardBottomSheet
import com.cornguard.app.ui.common.DiseaseStyle
import com.cornguard.app.ui.common.runCatchingCancellable
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch

/**
 * "Create Post" sheet (caps 3 design): free text, optional photo, and CornGuard's optional disease
 * tag. The post is filed under the author's profile barangay/municipality/province, as before.
 *
 * Opened with [forEdit] it becomes "Edit Post" for the author's own post: text and disease tag can
 * change (`posts:updateContent`); the photo is shown but can't be swapped (the backend keeps it).
 */
class CreatePostBottomSheet : CornGuardBottomSheet() {

    private var _binding: SheetCreatePostBinding? = null
    private val binding get() = _binding!!

    private var selectedImageUri: Uri? = null
    private val editPostId: String? get() = arguments?.getString(ARG_POST_ID)
    private val diseaseOptions = listOf("") + DiseaseCode.ALL

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                selectedImageUri = uri
                binding.createPostPreview.clipToOutline = true
                binding.createPostPreview.visibility = View.VISIBLE
                binding.createPostPreview.load(uri)
                binding.createPostAddPhoto.setText(R.string.post_change_photo_action)
                updateSubmitEnabled()
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetCreatePostBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        // Shrink the sheet's window for the keyboard (the sheet scrolls) so the Post button isn't
        // hidden behind it while typing.
        @Suppress("DEPRECATION")
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.createPostClose.setOnClickListener { dismiss() }
        binding.createPostAddPhoto.setOnClickListener { pickImageLauncher.launch("image/*") }
        binding.createPostSubmit.setOnClickListener { submit() }
        binding.createPostBody.doAfterTextChanged { updateSubmitEnabled() }

        diseaseOptions.forEachIndexed { index, code ->
            val chip = Chip(requireContext()).apply {
                id = View.generateViewId()
                tag = code
                text = if (code.isEmpty()) getString(R.string.post_disease_none) else DiseaseStyle.label(code)
                isCheckable = true
                isChecked = index == 0
            }
            binding.createPostDiseaseChips.addView(chip)
        }
        editPostId?.let { prefillForEdit(requireArguments()) }
        binding.createPostBody.requestFocus()
    }

    private fun prefillForEdit(args: Bundle) {
        binding.createPostTitle.setText(R.string.edit_post_title)
        binding.createPostSubmit.setText(R.string.action_save)
        binding.createPostAddPhoto.visibility = View.INVISIBLE
        binding.createPostBody.setText(args.getString(ARG_BODY).orEmpty())
        binding.createPostBody.setSelection(binding.createPostBody.text.length)
        val tag = args.getString(ARG_DISEASE_TAG).orEmpty()
        for (i in 0 until binding.createPostDiseaseChips.childCount) {
            val chip = binding.createPostDiseaseChips.getChildAt(i) as Chip
            chip.isChecked = chip.tag == tag
        }
        args.getString(ARG_IMAGE_URL)?.takeIf { it.isNotBlank() }?.let { url ->
            binding.createPostPreview.clipToOutline = true
            binding.createPostPreview.visibility = View.VISIBLE
            binding.createPostPreview.load(url)
        }
        updateSubmitEnabled()
    }

    private fun updateSubmitEnabled() {
        binding.createPostSubmit.isEnabled = binding.createPostBody.text.isNotBlank()
    }

    private fun selectedDiseaseTag(): String {
        val checked = binding.createPostDiseaseChips.checkedChipId
        return binding.createPostDiseaseChips.findViewById<Chip>(checked)?.tag as? String ?: ""
    }

    private fun submit() {
        val body = binding.createPostBody.text.toString().trim()
        if (body.isEmpty()) {
            showError(getString(R.string.post_empty_error))
            return
        }
        val user = ServiceLocator.authRepository.getCurrentUser()
        if (user == null) {
            showError(getString(R.string.community_sign_in_prompt))
            return
        }

        editPostId?.let { postId ->
            saveEdit(postId, body)
            return
        }

        setLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val profile = runCatchingCancellable { ServiceLocator.userFarmRepository.getUserProfile(user.uid) }.getOrNull()
            val draft = DraftPost(
                title = "",
                body = body,
                diseaseTag = selectedDiseaseTag(),
                imageUri = selectedImageUri?.toString(),
                linkedDiagnosisRecordId = null,
                barangay = profile?.barangay.orEmpty(),
                municipality = profile?.municipality.orEmpty(),
                province = profile?.province.orEmpty()
            )
            val result = runCatchingCancellable { ServiceLocator.communityRepository.createPost(user.uid, draft) }
            setLoading(false)
            if (result.isSuccess) {
                setFragmentResult(CommunityFragment.RESULT_FEED_CHANGED, Bundle())
                dismiss()
            } else {
                showError(result.exceptionOrNull()?.message ?: getString(R.string.community_load_failed))
            }
        }
    }

    private fun saveEdit(postId: String, body: String) {
        setLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatchingCancellable {
                ServiceLocator.communityRepository.updatePostContent(
                    postId, mapOf("body" to body, "diseaseTag" to selectedDiseaseTag())
                )
            }
            if (_binding == null) return@launch
            setLoading(false)
            if (result.isSuccess) {
                setFragmentResult(CommunityFragment.RESULT_FEED_CHANGED, Bundle())
                dismiss()
            } else {
                showError(getString(R.string.edit_post_failed))
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.createPostProgress.visibility = if (loading) View.VISIBLE else View.GONE
        binding.createPostSubmit.isEnabled = !loading && binding.createPostBody.text.isNotBlank()
    }

    private fun showError(message: String) {
        binding.createPostError.visibility = View.VISIBLE
        binding.createPostError.text = message
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "CreatePostBottomSheet"
        private const val ARG_POST_ID = "postId"
        private const val ARG_BODY = "body"
        private const val ARG_DISEASE_TAG = "diseaseTag"
        private const val ARG_IMAGE_URL = "imageUrl"

        /** "Edit Post" for the author's own [post]. */
        fun forEdit(post: CommunityPost) = CreatePostBottomSheet().apply {
            arguments = bundleOf(
                ARG_POST_ID to post.postId,
                ARG_BODY to post.body,
                ARG_DISEASE_TAG to post.diseaseTag,
                ARG_IMAGE_URL to post.imageUrl
            )
        }
    }
}
