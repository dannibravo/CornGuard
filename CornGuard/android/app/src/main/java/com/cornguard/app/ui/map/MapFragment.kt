package com.cornguard.app.ui.map

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
import com.cornguard.app.databinding.FragmentMapBinding
import com.cornguard.app.di.ServiceLocator
import kotlinx.coroutines.launch

/**
 * List-based GIS view — data layer only, no map-SDK rendering, per the pragmatic D-10 lean
 * documented in claude/03_SOURCE_ALIGNMENT_AND_DECISION_GATES.md
 * ([com.cornguard.app.data.repository.GisRepository]'s doc comment explains why). Online-only,
 * same sign-in-prompt pattern as [com.cornguard.app.ui.community.CommunityFragment].
 *
 * "Verified Outbreaks" is expected to render empty until an admin actually verifies something
 * (D-08/D-02 — see GisRepository.getVerifiedOccurrences) — that's correct, not a bug.
 */
class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private val verifiedAdapter = MapOccurrenceAdapter()
    private val nearbyAdapter = NearbyReportAdapter()
    private var currentUid: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.mapVerifiedRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.mapVerifiedRecyclerView.adapter = verifiedAdapter
        binding.mapNearbyRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.mapNearbyRecyclerView.adapter = nearbyAdapter

        binding.mapSignInButton.setOnClickListener {
            findNavController().navigate(R.id.authFragment)
        }
        binding.mapSwipeRefresh.setOnRefreshListener {
            val uid = currentUid
            if (uid != null) loadGisData(uid) else binding.mapSwipeRefresh.isRefreshing = false
        }

        observeAuthState()
    }

    private fun observeAuthState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                ServiceLocator.authRepository.observeAuthState().collect { user ->
                    val signedIn = user != null
                    binding.mapSignInPrompt.visibility = if (signedIn) View.GONE else View.VISIBLE
                    binding.mapSignInButton.visibility = if (signedIn) View.GONE else View.VISIBLE
                    binding.mapContent.visibility = if (signedIn) View.VISIBLE else View.GONE
                    currentUid = user?.uid
                    if (signedIn) loadGisData(user!!.uid) else binding.mapSwipeRefresh.isRefreshing = false
                }
            }
        }
    }

    private fun loadGisData(uid: String) {
        binding.mapSwipeRefresh.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
            val profile = runCatching { ServiceLocator.userFarmRepository.getUserProfile(uid) }.getOrNull()
            if (profile == null || profile.barangay.isBlank()) {
                binding.mapSwipeRefresh.isRefreshing = false
                binding.mapVerifiedEmpty.text = getString(R.string.map_no_area_profile)
                binding.mapVerifiedEmpty.visibility = View.VISIBLE
                binding.mapNearbyEmpty.text = getString(R.string.map_no_area_profile)
                binding.mapNearbyEmpty.visibility = View.VISIBLE
                return@launch
            }

            val areaFilter = "barangay" to profile.barangay

            val occurrences = runCatching {
                ServiceLocator.gisRepository.getVerifiedOccurrences(areaFilter, diseaseFilter = null)
            }.getOrDefault(emptyList())
            verifiedAdapter.submitList(occurrences)
            binding.mapVerifiedEmpty.text = getString(R.string.map_verified_empty)
            binding.mapVerifiedEmpty.visibility = if (occurrences.isEmpty()) View.VISIBLE else View.GONE

            val nearbyReports = runCatching {
                ServiceLocator.gisRepository.getNearbyReports(areaFilter, diseaseFilter = null)
            }.getOrDefault(emptyList())
            nearbyAdapter.submitList(nearbyReports)
            binding.mapNearbyEmpty.text = getString(R.string.map_nearby_empty)
            binding.mapNearbyEmpty.visibility = if (nearbyReports.isEmpty()) View.VISIBLE else View.GONE

            binding.mapSwipeRefresh.isRefreshing = false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
