package com.cornguard.app.ui.treatment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import com.cornguard.app.R
import com.cornguard.app.databinding.ItemDetailsSectionBinding
import com.cornguard.app.databinding.SheetFullDetailsBinding
import com.cornguard.app.databinding.ViewReviewLabelBinding
import com.cornguard.app.di.ServiceLocator
import com.cornguard.app.model.ReviewStatus
import com.cornguard.app.model.reviewLabelFor
import com.cornguard.app.ui.common.CornGuardBottomSheet
import kotlinx.coroutines.launch

/**
 * "Full Details" sheet (caps 3 design): the offline disease reference for one disease code —
 * treatment, causes, duration, prevention and symptoms — from Room, never the network. Content is
 * seeded by DiseaseReferenceSeedData. The Treatment section carries a review label when the content
 * has been reviewed (ReviewLabelBinder); the disclaimer stays until an expert has verified it.
 */
class FullDetailsBottomSheet : CornGuardBottomSheet() {

    private var _binding: SheetFullDetailsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetFullDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.detailsCloseButton.setOnClickListener { dismiss() }
        val diseaseCode = requireArguments().getString(ARG_DISEASE_CODE).orEmpty()

        viewLifecycleOwner.lifecycleScope.launch {
            val reference = ServiceLocator.diseaseReferenceRepository.getByCode(diseaseCode)
            if (reference == null) {
                binding.detailsEmpty.visibility = View.VISIBLE
                binding.detailsSource.visibility = View.GONE
                return@launch
            }
            binding.detailsDiseaseName.text = reference.displayName
            addSection(R.string.treatment_steps_label, reference.treatmentSteps)?.let { treatment ->
                val label = ViewReviewLabelBinding.inflate(layoutInflater, treatment.root, true).root
                ReviewLabelBinder.bind(label, reference)
            }
            addSection(R.string.treatment_causes_label, reference.causes)
            addSection(R.string.treatment_duration_label, reference.duration)
            addSection(R.string.treatment_prevention_label, reference.preventionSteps)
            addSection(R.string.treatment_symptoms_label, reference.symptoms)
            binding.detailsSource.text = getString(R.string.treatment_source_label) + ": " + reference.sourceReference
            // Expert sign-off replaces the "not yet reviewed by an expert" disclaimer.
            binding.detailsDisclaimer.visibility =
                if (reviewLabelFor(reference)?.status == ReviewStatus.EXPERT_VERIFIED) View.GONE else View.VISIBLE
        }
    }

    private fun addSection(titleRes: Int, body: String): ItemDetailsSectionBinding? {
        if (body.isBlank()) return null
        val section = ItemDetailsSectionBinding.inflate(layoutInflater, binding.detailsSections, true)
        section.sectionTitle.setText(titleRes)
        section.sectionBody.text = body
        return section
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "FullDetailsBottomSheet"
        private const val ARG_DISEASE_CODE = "diseaseCode"

        fun newInstance(diseaseCode: String) = FullDetailsBottomSheet().apply {
            arguments = bundleOf(ARG_DISEASE_CODE to diseaseCode)
        }
    }
}
