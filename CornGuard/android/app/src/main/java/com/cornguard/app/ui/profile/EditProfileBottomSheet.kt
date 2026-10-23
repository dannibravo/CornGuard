package com.cornguard.app.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.cornguard.app.R
import com.cornguard.app.databinding.SheetEditProfileBinding
import com.cornguard.app.di.ServiceLocator
import com.cornguard.app.ui.common.CornGuardBottomSheet
import com.cornguard.app.ui.common.BarangayPickerBottomSheet
import kotlinx.coroutines.launch

/**
 * "Edit Profile" sheet (caps 3 design): name and area fields saved through
 * [com.cornguard.app.data.repository.UserFarmRepository.updateUserProfile].
 */
class EditProfileBottomSheet : CornGuardBottomSheet() {

    private var _binding: SheetEditProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetEditProfileBinding.inflate(inflater, container, false)
        // The design's edit sheet covers ~90% of the screen.
        binding.root.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (resources.displayMetrics.heightPixels * 0.9).toInt()
        )
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.editProfileCancel.setOnClickListener { dismiss() }
        binding.editProfileSave.setOnClickListener { save() }
        binding.editProfileName.doAfterTextChanged { text ->
            binding.editProfileAvatar.text = text?.trim()?.firstOrNull()?.uppercase() ?: ""
        }
        // Barangay + municipality come from the boundary list as a pair. Typing them freely let a
        // profile end up as e.g. "Patpat, City of Valencia" (Patpat is in Malaybalay), which the
        // map and outbreak alerts then can't place.
        binding.editProfileBarangay.setOnClickListener { pickArea() }
        binding.editProfileMunicipality.setOnClickListener { pickArea() }
        childFragmentManager.setFragmentResultListener(BarangayPickerBottomSheet.RESULT, viewLifecycleOwner) { _, result ->
            binding.editProfileBarangay.setText(result.getString(BarangayPickerBottomSheet.KEY_BARANGAY))
            binding.editProfileMunicipality.setText(result.getString(BarangayPickerBottomSheet.KEY_MUNICIPALITY))
            binding.editProfileProvince.setText(PROVINCE)
        }

        val user = ServiceLocator.authRepository.getCurrentUser() ?: return dismiss()
        viewLifecycleOwner.lifecycleScope.launch {
            val profile = runCatching { ServiceLocator.userFarmRepository.getUserProfile(user.uid) }.getOrNull()
                ?: return@launch
            binding.editProfileName.setText(profile.displayName)
            binding.editProfileBarangay.setText(profile.barangay)
            binding.editProfileMunicipality.setText(profile.municipality)
            binding.editProfileProvince.setText(profile.province)
            binding.editProfileMobile.setText(profile.mobileNumber.orEmpty())
        }
    }

    private fun save() {
        val user = ServiceLocator.authRepository.getCurrentUser() ?: return
        val name = binding.editProfileName.text.toString().trim()
        if (name.isEmpty()) {
            showError(getString(R.string.auth_error_missing_fields))
            return
        }
        val fields = mapOf(
            "display_name" to name,
            "barangay" to binding.editProfileBarangay.text.toString().trim(),
            "municipality" to binding.editProfileMunicipality.text.toString().trim(),
            "province" to binding.editProfileProvince.text.toString().trim(),
            "mobile_number" to binding.editProfileMobile.text.toString().trim()
        )
        binding.editProfileProgress.visibility = View.VISIBLE
        binding.editProfileSave.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching { ServiceLocator.userFarmRepository.updateUserProfile(user.uid, fields) }
            binding.editProfileProgress.visibility = View.GONE
            binding.editProfileSave.isEnabled = true
            if (result.isSuccess) {
                setFragmentResult(ProfileFragment.RESULT_PROFILE_CHANGED, Bundle())
                dismiss()
            } else {
                showError(getString(R.string.edit_profile_save_failed))
            }
        }
    }

    private fun pickArea() {
        if (childFragmentManager.findFragmentByTag(BarangayPickerBottomSheet.TAG) != null) return
        BarangayPickerBottomSheet().show(childFragmentManager, BarangayPickerBottomSheet.TAG)
    }

    private fun showError(message: String) {
        binding.editProfileError.visibility = View.VISIBLE
        binding.editProfileError.text = message
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "EditProfileBottomSheet"
        /** Every barangay in the picker is in Bukidnon. */
        private const val PROVINCE = "Bukidnon"
    }
}
