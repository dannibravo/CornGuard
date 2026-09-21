package com.cornguard.app.ui.history

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
import com.cornguard.app.databinding.FragmentHistoryBinding
import com.cornguard.app.di.ServiceLocator
import kotlinx.coroutines.launch

/**
 * Reads local scan history via [ServiceLocator.diagnosisHistoryRepository]. Must work with the
 * network disabled (claude/12_TESTING_AND_ACCEPTANCE_PLAN.md offline functionality testing) — the
 * repository is backed by Room only, never Firebase.
 */
class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private val adapter = DiagnosisHistoryAdapter(onClick = ::openRecord)

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

    private fun openRecord(record: com.cornguard.app.data.local.db.entity.DiagnosisRecordEntity) {
        findNavController().navigate(
            R.id.action_history_to_result,
            bundleOf(
                "localId" to record.localId,
                "diseaseCode" to record.diseaseCode,
                "displayLabel" to record.displayLabel,
                "confidence" to record.confidence,
                "capturedAt" to record.capturedAt,
                "modelVersion" to record.modelVersion,
                "sharedToCloud" to record.sharedToCloud,
                "hasLocation" to (record.latitude != null && record.longitude != null)
            )
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
