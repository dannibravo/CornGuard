package com.cornguard.app.ui.profile

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
import com.cornguard.app.BuildConfig
import com.cornguard.app.R
import com.cornguard.app.data.model.AuthUser
import com.cornguard.app.databinding.FragmentProfileBinding
import com.cornguard.app.di.ServiceLocator
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * Profile & Settings (caps 3 design): profile card with an Edit Profile sheet, the farm list from
 * [com.cornguard.app.data.repository.UserFarmRepository], About, and Log Out. Online-only for the
 * profile parts, with a sign-in card when signed out. A new farm reuses the profile's
 * barangay/municipality/province (D-03 is unresolved, so no farm-specific address form).
 */
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val farmAdapter = FarmAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.profileFarmsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.profileFarmsRecyclerView.adapter = farmAdapter

        binding.profileSignInButton.setOnClickListener { findNavController().navigate(R.id.authFragment) }
        binding.profileEditButton.setOnClickListener {
            EditProfileBottomSheet().show(childFragmentManager, EditProfileBottomSheet.TAG)
        }
        binding.profileAddFarmButton.setOnClickListener { addFarm() }
        binding.profileAboutRow.setOnClickListener { showAbout() }
        binding.profileLogOutRow.setOnClickListener { signOut() }

        childFragmentManager.setFragmentResultListener(RESULT_PROFILE_CHANGED, viewLifecycleOwner) { _, _ ->
            ServiceLocator.authRepository.getCurrentUser()?.let { loadProfile(it) }
        }
        observeAuthState()
    }

    private fun observeAuthState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                ServiceLocator.authRepository.observeAuthState().collect { user ->
                    val signedIn = user != null
                    binding.profileSignedOutCard.visibility = if (signedIn) View.GONE else View.VISIBLE
                    binding.profileContent.visibility = if (signedIn) View.VISIBLE else View.GONE
                    if (user != null) {
                        loadProfile(user)
                        loadFarms(user.uid)
                    }
                }
            }
        }
    }

    private fun loadProfile(user: AuthUser) {
        binding.profileEmail.text = user.email.orEmpty()
        viewLifecycleOwner.lifecycleScope.launch {
            val profile = runCatching { ServiceLocator.userFarmRepository.getUserProfile(user.uid) }.getOrNull()
            val name = profile?.displayName?.takeIf { it.isNotBlank() } ?: getString(R.string.default_farmer_name)
            binding.profileName.text = name
            binding.profileAvatar.text = name.first().uppercase()
            val location = listOfNotNull(profile?.barangay, profile?.municipality).filter { it.isNotBlank() }
            binding.profileLocation.text =
                if (location.isEmpty()) getString(R.string.profile_location_not_set) else location.joinToString(", ")
        }
    }

    private fun loadFarms(uid: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val farms = runCatching { ServiceLocator.userFarmRepository.getFarmsForUser(uid) }.getOrDefault(emptyList())
            farmAdapter.submitList(farms)
            binding.profileFarmsEmpty.visibility = if (farms.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun addFarm() {
        val name = binding.profileFarmNameInput.text.toString().trim()
        if (name.isEmpty()) return
        val user = ServiceLocator.authRepository.getCurrentUser() ?: return

        binding.profileAddFarmButton.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val profile = runCatching { ServiceLocator.userFarmRepository.getUserProfile(user.uid) }.getOrNull()
            if (profile != null) {
                runCatching {
                    ServiceLocator.userFarmRepository.createFarm(
                        ownerUserId = user.uid,
                        farmNameOrLabel = name,
                        barangay = profile.barangay,
                        municipality = profile.municipality,
                        province = profile.province,
                        latitude = null,
                        longitude = null
                    )
                }
                binding.profileFarmNameInput.text.clear()
                loadFarms(user.uid)
            }
            binding.profileAddFarmButton.isEnabled = true
        }
    }

    private fun showAbout() {
        val modelVersion = runCatching { ServiceLocator.cornLeafClassifier.modelVersion }.getOrDefault("—")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.profile_about)
            .setMessage(getString(R.string.profile_about_message, BuildConfig.VERSION_NAME, modelVersion))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun signOut() {
        val uid = ServiceLocator.authRepository.getCurrentUser()?.uid
        viewLifecycleOwner.lifecycleScope.launch {
            if (uid != null) {
                // Best-effort — a failure here must never block sign-out itself.
                runCatching { ServiceLocator.gisRepository.deactivateDeviceToken(uid, ServiceLocator.deviceId) }
            }
            ServiceLocator.authRepository.signOut()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val RESULT_PROFILE_CHANGED = "profile_changed"
    }
}
