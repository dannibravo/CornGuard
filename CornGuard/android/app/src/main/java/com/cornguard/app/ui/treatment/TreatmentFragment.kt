package com.cornguard.app.ui.treatment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.cornguard.app.databinding.FragmentTreatmentBinding

/**
 * Sprint 0 placeholder shell. Populated from
 * [com.cornguard.app.data.repository.DiseaseReferenceRepository] keyed by the scan's
 * disease_code, once the Scan flow exists to reach this screen with real arguments (Sprint 2).
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
