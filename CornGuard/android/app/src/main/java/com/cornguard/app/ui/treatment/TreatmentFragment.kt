package com.cornguard.app.ui.treatment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.cornguard.app.R
import com.cornguard.app.databinding.FragmentTreatmentBinding
import com.cornguard.app.di.ServiceLocator
import kotlinx.coroutines.launch

/**
 * Renders local, offline-available disease reference content keyed by the `diseaseCode` nav
 * argument (set on this destination in nav_graph.xml). Content is seeded by
 * [com.cornguard.app.data.local.db.DiseaseReferenceSeedData] and is explicitly PLACEHOLDER —
 * see the Treatment Guidance Boundary in claude/09_ML_MODEL_CONTRACT.md. Reads Room only, never
 * Firebase, so this screen works with no connectivity per the offline-first Project Principle.
 */
class TreatmentFragment : Fragment() {

    private var _binding: FragmentTreatmentBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTreatmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val diseaseCode = requireArguments().getString("diseaseCode")
            ?: error("TreatmentFragment requires a diseaseCode argument")
        loadReference(diseaseCode)
    }

    private fun loadReference(diseaseCode: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val reference = ServiceLocator.diseaseReferenceRepository.getByCode(diseaseCode)
            if (reference == null) {
                binding.treatmentTitle.visibility = View.GONE
                binding.treatmentOfflineIndicator.visibility = View.GONE
                binding.treatmentSymptomsLabel.visibility = View.GONE
                binding.treatmentSymptomsBody.visibility = View.GONE
                binding.treatmentStepsLabel.visibility = View.GONE
                binding.treatmentStepsBody.visibility = View.GONE
                binding.treatmentPreventionLabel.visibility = View.GONE
                binding.treatmentPreventionBody.visibility = View.GONE
                binding.treatmentSourceBody.visibility = View.GONE
                binding.treatmentEmptyState.visibility = View.VISIBLE
                return@launch
            }
            binding.treatmentTitle.text = reference.displayName
            binding.treatmentSymptomsBody.text = reference.symptoms
            binding.treatmentStepsBody.text = reference.treatmentSteps
            binding.treatmentPreventionBody.text = reference.preventionSteps
            binding.treatmentSourceBody.text =
                getString(R.string.treatment_source_label) + ": " + reference.sourceReference
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
