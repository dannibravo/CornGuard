package com.cornguard.app.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.cornguard.app.R
import com.cornguard.app.databinding.FragmentHomeBinding
import com.cornguard.app.di.ServiceLocator
import kotlinx.coroutines.launch

/**
 * Dashboard (caps 3 design): "Welcome back, <name>" and the three entry cards — Scan Corn,
 * Community, Outbreak Map. Works signed out and offline; the name falls back to "Farmer".
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.homeScanCard.setOnClickListener { findNavController().navigate(R.id.action_home_to_scan) }
        binding.homeCommunityCard.setOnClickListener { findNavController().navigate(R.id.action_home_to_community) }
        binding.homeMapCard.setOnClickListener { findNavController().navigate(R.id.action_home_to_map) }
        observeName()
    }

    private fun observeName() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                runCatching { ServiceLocator.authRepository.observeAuthState() }.getOrNull()?.collect { user ->
                    val name = user?.let {
                        runCatching { ServiceLocator.userFarmRepository.getUserProfile(it.uid) }.getOrNull()?.displayName
                    }
                    binding.homeName.text = name?.takeIf { it.isNotBlank() } ?: getString(R.string.default_farmer_name)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
