package com.cornguard.app.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cornguard.app.databinding.FragmentHistoryBinding
import com.cornguard.app.di.ServiceLocator
import kotlinx.coroutines.launch

/**
 * Reads local scan history via [ServiceLocator.diagnosisHistoryRepository]. Must work with the
 * network disabled (claude/12_TESTING_AND_ACCEPTANCE_PLAN.md offline functionality testing) — the
 * repository is backed by Room only, never Firebase.
 *
 * The RecyclerView adapter itself is intentionally not built yet: Sprint 0 ships the screen
 * shell and the empty-state wiring; the list adapter/row layout land with Sprint 2's scan flow,
 * once there is a real [com.cornguard.app.data.local.db.entity.DiagnosisRecordEntity] shape being
 * produced end-to-end to design the row against.
 */
class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

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
        observeHistory()
    }

    private fun observeHistory() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                ServiceLocator.diagnosisHistoryRepository.observeHistory().collect { records ->
                    binding.historyEmptyState.visibility =
                        if (records.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
