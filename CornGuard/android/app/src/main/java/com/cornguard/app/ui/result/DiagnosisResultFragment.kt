package com.cornguard.app.ui.result

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.cornguard.app.R
import com.cornguard.app.databinding.FragmentDiagnosisResultBinding

/**
 * Sprint 0 placeholder shell. Rendered from a real
 * [com.cornguard.app.model.DetectionResult] starting Sprint 2, once the Scan screen can produce
 * one.
 */
class DiagnosisResultFragment : Fragment() {

    private var _binding: FragmentDiagnosisResultBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDiagnosisResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.viewTreatmentButton.setOnClickListener {
            findNavController().navigate(R.id.action_result_to_treatment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
