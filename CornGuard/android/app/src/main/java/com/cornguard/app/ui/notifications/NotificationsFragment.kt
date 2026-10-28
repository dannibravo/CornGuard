package com.cornguard.app.ui.notifications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.cornguard.app.R
import com.cornguard.app.data.model.AppNotification
import com.cornguard.app.databinding.FragmentNotificationsBinding
import com.cornguard.app.di.ServiceLocator
import com.cornguard.app.ui.community.CommunityFragment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * The Dashboard bell's inbox: every alert the farmer was pushed, so it can be read again after
 * the system notification is gone. Live, online-only and sign-in gated. Tapping an item marks it
 * read and opens the Outbreak Map (outbreak alerts) or the post's comments (community replies).
 */
class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!
    private val adapter = NotificationsAdapter(onClick = ::open)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.notificationsHeader.headerTitle.setText(R.string.notifications_title)
        binding.notificationsHeader.headerBackButton.setOnClickListener { findNavController().popBackStack() }

        binding.notificationsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.notificationsRecyclerView.adapter = adapter
        binding.notificationsSignInButton.setOnClickListener { findNavController().navigate(R.id.authFragment) }
        binding.notificationsMarkAllRead.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                runCatching { ServiceLocator.notificationRepository.markAllRead() }
            }
        }

        observeNotifications()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeNotifications() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                ServiceLocator.authRepository.observeAuthState()
                    .flatMapLatest { user ->
                        val signedIn = user != null
                        binding.notificationsSignedOut.visibility = if (signedIn) View.GONE else View.VISIBLE
                        binding.notificationsRecyclerView.visibility = if (signedIn) View.VISIBLE else View.GONE
                        binding.notificationsEmptyState.visibility = View.GONE
                        if (signedIn) ServiceLocator.notificationRepository.observeNotifications() else flowOf(null)
                    }
                    .collect(::show)
            }
        }
    }

    /** [items] is null while signed out. */
    private fun show(items: List<AppNotification>?) {
        adapter.submitList(items.orEmpty())
        binding.notificationsEmptyState.visibility = if (items?.isEmpty() == true) View.VISIBLE else View.GONE
        binding.notificationsMarkAllRead.visibility =
            if (items.orEmpty().any { !it.read }) View.VISIBLE else View.GONE
    }

    private fun open(item: AppNotification) {
        if (!item.read) {
            // Activity scope: navigating away below destroys this view before the mutation returns.
            requireActivity().lifecycleScope.launch {
                runCatching { ServiceLocator.notificationRepository.markRead(item.notificationId) }
            }
        }
        val nav = findNavController()
        when {
            item.type == AppNotification.TYPE_OUTBREAK_ALERT -> nav.navigate(R.id.mapFragment)
            item.relatedPostId != null ->
                nav.navigate(R.id.communityFragment, bundleOf(CommunityFragment.ARG_OPEN_POST_ID to item.relatedPostId))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
