package com.cornguard.app.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.cornguard.app.R
import com.cornguard.app.data.local.db.entity.DiagnosisRecordEntity
import com.cornguard.app.databinding.FragmentHistoryBinding
import com.cornguard.app.di.ServiceLocator
import com.cornguard.app.ui.result.ResultBottomSheet
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * Detection History (caps 3 design). Reads local scan history via
 * [ServiceLocator.diagnosisHistoryRepository] — Room only, so it works with the network disabled
 * (claude/12_TESTING_AND_ACCEPTANCE_PLAN.md offline functionality testing).
 */
class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private val adapter = DiagnosisHistoryAdapter(onClick = ::openRecord, onDelete = ::confirmDelete)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.historyRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.historyRecyclerView.adapter = adapter
        observeHistory()
        observeTreatments()
    }

    private fun observeHistory() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                ServiceLocator.diagnosisHistoryRepository.observeHistory().collect { records ->
                    adapter.submitList(records)
                    binding.historyEmptyState.visibility =
                        if (records.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun observeTreatments() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                ServiceLocator.diseaseReferenceRepository.observeAll().collect { references ->
                    adapter.treatmentByCode = references.associate { it.diseaseCode to it.treatmentSteps }
                }
            }
        }
    }

    private fun openRecord(record: DiagnosisRecordEntity) {
        ResultBottomSheet.newInstance(record.localId).show(childFragmentManager, ResultBottomSheet.TAG)
    }

    private fun confirmDelete(record: DiagnosisRecordEntity) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.history_delete_title)
            .setMessage(R.string.history_delete_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    ServiceLocator.diagnosisHistoryRepository.delete(record)
                }
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
