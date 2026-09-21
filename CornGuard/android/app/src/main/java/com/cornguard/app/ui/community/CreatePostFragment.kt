package com.cornguard.app.ui.community

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.cornguard.app.R
import com.cornguard.app.data.model.DraftPost
import com.cornguard.app.databinding.FragmentCreatePostBinding
import com.cornguard.app.di.ServiceLocator
import com.cornguard.app.model.DiseaseCode
import kotlinx.coroutines.launch

/**
 * Builds and submits a [DraftPost] via
 * [com.cornguard.app.data.repository.CommunityRepository.createPost]. Only reachable while
 * signed in (from [CommunityFragment]'s create button, itself only shown when signed in), so
 * [ServiceLocator.authRepository.getCurrentUser] is expected non-null here.
 */
class CreatePostFragment : Fragment() {

    private var _binding: FragmentCreatePostBinding? = null
    private val binding get() = _binding!!

    private val diseaseOptions = listOf("") + DiseaseCode.ALL

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreatePostBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.postDiseaseSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            diseaseOptions.map { code -> if (code.isEmpty()) getString(R.string.post_disease_none) else code }
        )

        binding.postSubmitButton.setOnClickListener { submit() }
    }

    private fun submit() {
        val title = binding.postTitleInput.text.toString().trim()
        val body = binding.postBodyInput.text.toString().trim()
        val diseaseTag = diseaseOptions[binding.postDiseaseSpinner.selectedItemPosition]

        if (title.isEmpty() || body.isEmpty()) {
            showError(getString(R.string.auth_error_missing_fields))
            return
        }

        val user = ServiceLocator.authRepository.getCurrentUser()
        if (user == null) {
            showError(getString(R.string.auth_required_prompt))
            return
        }

        setLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val profile = runCatching { ServiceLocator.userFarmRepository.getUserProfile(user.uid) }.getOrNull()
            if (profile == null) {
                setLoading(false)
                showError(getString(R.string.community_load_failed))
                return@launch
            }

            val draft = DraftPost(
                title = title,
                body = body,
                diseaseTag = diseaseTag,
                imageUri = null,
                linkedDiagnosisRecordId = null,
                barangay = profile.barangay,
                municipality = profile.municipality,
                province = profile.province
            )

            val result = runCatching { ServiceLocator.communityRepository.createPost(user.uid, draft) }
            setLoading(false)
            if (result.isSuccess) {
                findNavController().popBackStack()
            } else {
                showError(result.exceptionOrNull()?.message ?: getString(R.string.community_load_failed))
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.createPostProgress.visibility = if (loading) View.VISIBLE else View.GONE
        binding.postSubmitButton.isEnabled = !loading
    }

    private fun showError(message: String) {
        binding.createPostError.visibility = View.VISIBLE
        binding.createPostError.text = message
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
