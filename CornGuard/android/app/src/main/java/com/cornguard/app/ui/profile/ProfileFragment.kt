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
import com.cornguard.app.data.model.Farm
import com.cornguard.app.databinding.FragmentProfileBinding
import com.cornguard.app.di.ServiceLocator
import com.cornguard.app.ui.common.runCatchingCancellable
import com.cornguard.app.ui.onboarding.OnboardingDialogFragment
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Job
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * Profile & Settings (caps 3 design): profile card with an Edit Profile sheet, My Farms (private
 * farm pins, added with [FarmLocationPickerFragment]), About, and Log Out. Online-only for the
 * profile parts, with a sign-in card when signed out.
 */
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val farmAdapter = FarmAdapter(onRemove = ::confirmRemoveFarm)
    private var farmsJob: Job? = null

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
        binding.profileAddFarmButton.setOnClickListener { findNavController().navigate(R.id.farmPickerFragment) }
        binding.profileAboutRow.setOnClickListener { showAbout() }
        // Replays the onboarding guide; finishing it here changes nothing on the account.
        binding.profileTutorialRow.setOnClickListener {
            val uid = ServiceLocator.authRepository.getCurrentUser()?.uid ?: return@setOnClickListener
            if (childFragmentManager.findFragmentByTag(OnboardingDialogFragment.TAG) == null) {
                OnboardingDialogFragment.newInstance(uid, replay = true)
                    .show(childFragmentManager, OnboardingDialogFragment.TAG)
            }
        }
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
                        observeFarms(user.uid)
                    } else {
                        farmsJob?.cancel()
                    }
                }
            }
        }
    }

    private fun loadProfile(user: AuthUser) {
        binding.profileEmail.text = user.email.orEmpty()
        viewLifecycleOwner.lifecycleScope.launch {
            val profile = runCatchingCancellable { ServiceLocator.userFarmRepository.getUserProfile(user.uid) }.getOrNull()
            val name = profile?.displayName?.takeIf { it.isNotBlank() } ?: getString(R.string.default_farmer_name)
            binding.profileName.text = name
            binding.profileAvatar.text = name.first().uppercase()
            val location = listOfNotNull(profile?.barangay, profile?.municipality).filter { it.isNotBlank() }
            binding.profileLocation.text =
                if (location.isEmpty()) getString(R.string.profile_location_not_set) else location.joinToString(", ")
        }
    }

    /** Live list of the farmer's own farms (updates after adding one in the picker or removing one). */
    private fun observeFarms(uid: String) {
        farmsJob?.cancel()
        farmsJob = viewLifecycleOwner.lifecycleScope.launch {
            ServiceLocator.userFarmRepository.observeFarmsForUser(uid).collect { farms ->
                if (_binding == null) return@collect
                farmAdapter.submitList(farms)
                binding.profileFarmsEmpty.visibility = if (farms.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun confirmRemoveFarm(farm: Farm) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.farm_remove_title, farm.farmNameOrLabel))
            .setMessage(R.string.farm_remove_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.farm_remove_action) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val result = runCatchingCancellable { ServiceLocator.userFarmRepository.deleteFarm(farm.farmId) }
                    if (result.isFailure && _binding != null) {
                        Snackbar.make(binding.root, R.string.farm_remove_failed, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
            .show()
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
                runCatchingCancellable { ServiceLocator.gisRepository.deactivateDeviceToken(uid, ServiceLocator.deviceId) }
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
