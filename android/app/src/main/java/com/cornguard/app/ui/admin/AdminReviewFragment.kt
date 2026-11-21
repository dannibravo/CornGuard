package com.cornguard.app.ui.admin

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
import com.cornguard.app.data.model.DiagnosisRecordCloud
import com.cornguard.app.databinding.FragmentAdminReviewBinding
import com.cornguard.app.di.ServiceLocator
import kotlinx.coroutines.launch

/**
 * Admin Review — structured verification workflow (Phase 2, resolved D-02 2026-10-30). Lists
 * pending (verification_status == "unverified") diagnosisRecordsCloud records and lets an Admin
 * verify or reject each one; both actions append one immutable verificationHistory entry via
 * [com.cornguard.app.data.repository.AdminRepository] (see FirebaseAdminRepository for the
 * WriteBatch that keeps the record's status and its audit trail from ever diverging).
 *
 * Reachable only from [com.cornguard.app.ui.profile.ProfileFragment]'s Admin Review link, which
 * is itself hidden for non-admins — but this fragment independently re-checks sign-in and the
 * admin role claim on every entry, including direct navigation, since hiding a UI entry point is
 * not authorization (claude/04_DEVELOPMENT_RULES.md #14). The real enforcement is always the
 * Firestore rule: a non-admin's [getPendingDiagnosisRecords][com.cornguard.app.data.repository.AdminRepository.getPendingDiagnosisRecords]
 * call fails with permission-denied regardless of what this screen shows.
 */
class AdminReviewFragment : Fragment() {

    private var _binding: FragmentAdminReviewBinding? = null
    private val binding get() = _binding!!
    private val adapter = PendingDiagnosisAdapter(onVerify = ::verify, onReject = ::reject)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminReviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.adminReviewRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.adminReviewRecyclerView.adapter = adapter

        binding.adminReviewSignInButton.setOnClickListener {
            findNavController().navigate(R.id.authFragment)
        }
        binding.adminReviewSwipeRefresh.setOnRefreshListener { loadPending() }

        observeAuthState()
    }

    private fun observeAuthState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                ServiceLocator.authRepository.observeAuthState().collect { user ->
                    val signedIn = user != null
                    binding.adminReviewSignInPrompt.visibility = if (signedIn) View.GONE else View.VISIBLE
                    binding.adminReviewSignInButton.visibility = if (signedIn) View.GONE else View.VISIBLE
                    binding.adminReviewAccessDenied.visibility = View.GONE
                    binding.adminReviewSwipeRefresh.visibility = View.GONE
                    binding.adminReviewEmptyState.visibility = View.GONE
                    binding.adminReviewError.visibility = View.GONE
                    if (signedIn) checkAdminAndLoad()
                }
            }
        }
    }

    private fun checkAdminAndLoad() {
        viewLifecycleOwner.lifecycleScope.launch {
            val isAdmin = runCatching { ServiceLocator.authRepository.isCurrentUserAdmin() }.getOrDefault(false)
            if (isAdmin) {
                loadPending()
            } else {
                binding.adminReviewAccessDenied.visibility = View.VISIBLE
            }
        }
    }

    private fun loadPending() {
        binding.adminReviewError.visibility = View.GONE
        binding.adminReviewSwipeRefresh.visibility = View.VISIBLE
        binding.adminReviewSwipeRefresh.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching { ServiceLocator.adminRepository.getPendingDiagnosisRecords() }
            binding.adminReviewSwipeRefresh.isRefreshing = false
            val records = result.getOrNull()
            if (records == null) {
                binding.adminReviewSwipeRefresh.visibility = View.GONE
                binding.adminReviewError.visibility = View.VISIBLE
                binding.adminReviewError.text = getString(R.string.admin_review_load_failed)
                return@launch
            }
            adapter.submitList(records)
            binding.adminReviewEmptyState.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun verify(record: DiagnosisRecordCloud) = runAction(record) { adminUid ->
        ServiceLocator.adminRepository.verifyDiagnosisRecord(record.recordId, adminUid)
    }

    private fun reject(record: DiagnosisRecordCloud) = runAction(record) { adminUid ->
        ServiceLocator.adminRepository.rejectDiagnosisRecord(record.recordId, adminUid)
    }

    private fun runAction(record: DiagnosisRecordCloud, action: suspend (adminUid: String) -> Unit) {
        val adminUid = ServiceLocator.authRepository.getCurrentUser()?.uid
        if (adminUid == null) {
            adapter.onActionResolved(record.recordId, succeeded = false)
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching { action(adminUid) }
            adapter.onActionResolved(record.recordId, succeeded = result.isSuccess)
            if (adapter.currentList.isEmpty()) {
                binding.adminReviewEmptyState.visibility = View.VISIBLE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
