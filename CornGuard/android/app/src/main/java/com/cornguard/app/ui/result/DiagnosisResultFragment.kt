package com.cornguard.app.ui.result

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.cornguard.app.R
import com.cornguard.app.databinding.FragmentDiagnosisResultBinding
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/**
 * Renders the diagnosis record passed as nav arguments (from History today; from Scan once
 * Sprint 2's TFLite integration exists). Reads only the arguments already on this device — never
 * calls Firebase — per the offline-first Project Principle. Fields follow
 * claude/16_UI_UX_AND_DIAGRAM_REVISION_GUIDE.md section 5.8 (detected class, confidence, result
 * type, scan time, location status, save status).
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
        val args = requireArguments()
        val diseaseCode = args.getString("diseaseCode") ?: error("missing diseaseCode argument")
        val displayLabel = args.getString("displayLabel") ?: diseaseCode
        val confidence = args.getFloat("confidence")
        val capturedAt = args.getLong("capturedAt")
        val modelVersion = args.getString("modelVersion").orEmpty()
        val sharedToCloud = args.getBoolean("sharedToCloud", false)
        val hasLocation = args.getBoolean("hasLocation", false)

        binding.resultLabel.text = displayLabel
        binding.resultConfidence.text =
            getString(R.string.result_confidence_format, (confidence * 100).roundToInt())
        binding.resultScanTime.text = getString(R.string.result_scan_time_label) + ": " +
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(capturedAt))
        binding.resultLocationStatus.text = getString(
            if (hasLocation) R.string.result_location_captured else R.string.result_location_not_captured
        )
        binding.resultSaveStatus.text = getString(
            if (sharedToCloud) R.string.result_save_status_shared else R.string.result_save_status_local
        )
        binding.resultModelVersion.text = getString(R.string.result_model_version_format, modelVersion)

        binding.viewTreatmentButton.setOnClickListener {
            findNavController().navigate(
                R.id.action_result_to_treatment,
                bundleOf("diseaseCode" to diseaseCode)
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
