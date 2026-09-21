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
import com.cornguard.app.R
import com.cornguard.app.databinding.FragmentProfileBinding
import com.cornguard.app.di.ServiceLocator
import kotlinx.coroutines.launch

/**
 * Profile + farm list, against [com.cornguard.app.data.repository.UserFarmRepository]. Online-only
 * with the same sign-in-prompt pattern as Community/Map. A new farm reuses the signed-in user's
 * profile barangay/municipality/province rather than collecting a second address — D-03 (one farm
 * per farmer vs. many) is unresolved, so this deliberately doesn't assume a farm-specific address
 * form is even the right shape yet (claude/03_SOURCE_ALIGNMENT_AND_DECISION_GATES.md).
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

        binding.profileSignInButton.setOnClickListener {
            findNavController().navigate(R.id.authFragment)
        }
        binding.profileSignOutButton.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch { ServiceLocator.authRepository.signOut() }
        }
        binding.profileAddFarmButton.setOnClickListener { addFarm() }

        observeAuthState()
    }

    private fun observeAuthState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                ServiceLocator.authRepository.observeAuthState().collect { user ->
                    val signedIn = user != null
                    binding.profileNotSignedIn.visibility = if (signedIn) View.GONE else View.VISIBLE
                    binding.profileSignInButton.visibility = if (signedIn) View.GONE else View.VISIBLE
                    binding.profileContent.visibility = if (signedIn) View.VISIBLE else View.GONE
                    if (signedIn) {
                        binding.profileSignedInAs.text =
                            getString(R.string.auth_signed_in_as, user!!.email ?: user.uid)
                        loadFarms(user.uid)
                    }
                }
            }
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
