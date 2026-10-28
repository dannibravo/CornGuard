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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Dashboard (caps 3 design): "Welcome back, <name>" and the three entry cards — Scan Corn,
 * Community, Outbreak Map. Works signed out and offline; the name falls back to "Farmer". Signed
 * in, a bell with an unread badge opens the notification inbox.
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
        binding.homeBellButton.setOnClickListener { findNavController().navigate(R.id.action_home_to_notifications) }
        observeName()
        observeUnreadCount()
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

    /** Bell hidden while signed out; the badge follows the live unread count. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeUnreadCount() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                runCatching { ServiceLocator.authRepository.observeAuthState() }.getOrNull()
                    ?.flatMapLatest { user ->
                        binding.homeBellContainer.visibility = if (user != null) View.VISIBLE else View.GONE
                        if (user == null) flowOf(0) else ServiceLocator.notificationRepository.observeUnreadCount()
                    }
                    ?.collect(::showUnreadCount)
            }
        }
    }

    private fun showUnreadCount(count: Int) {
        binding.homeBellBadge.visibility = if (count > 0) View.VISIBLE else View.GONE
        binding.homeBellBadge.text =
            if (count > 9) getString(R.string.notifications_badge_overflow) else count.toString()
        binding.homeBellButton.contentDescription =
            if (count > 0) getString(R.string.notifications_unread_description, count)
            else getString(R.string.notifications_title)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
