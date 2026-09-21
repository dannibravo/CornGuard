package com.cornguard.app.ui.result

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.cornguard.app.R
import com.cornguard.app.databinding.FragmentDiagnosisResultBinding
import com.cornguard.app.di.ServiceLocator
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/**
 * Renders the diagnosis record passed as nav arguments (from History today; from Scan once
 * Sprint 2's TFLite integration exists). Reads only the arguments already on this device — never
 * calls Firebase — per the offline-first Project Principle. Fields follow
 * claude/16_UI_UX_AND_DIAGRAM_REVISION_GUIDE.md section 5.8 (detected class, confidence, result
 * type, scan time, location status, save status).
 *
 * "Share to Community" is the one action here that does touch Firebase — an explicit, opt-in
 * action per D-11 (a scan is local/private by default, claude/03_SOURCE_ALIGNMENT_AND_DECISION_GATES.md).
 */
class DiagnosisResultFragment : Fragment() {

    private var _binding: FragmentDiagnosisResultBinding? = null
    private val binding get() = _binding!!

    private var localId: Long = -1
    private var sharedToCloud: Boolean = false

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
        localId = args.getLong("localId", -1)
        val diseaseCode = args.getString("diseaseCode") ?: error("missing diseaseCode argument")
        val displayLabel = args.getString("displayLabel") ?: diseaseCode
        val confidence = args.getFloat("confidence")
        val capturedAt = args.getLong("capturedAt")
        val modelVersion = args.getString("modelVersion").orEmpty()
        sharedToCloud = args.getBoolean("sharedToCloud", false)
        val hasLocation = args.getBoolean("hasLocation", false)

        binding.resultLabel.text = displayLabel
        binding.resultConfidence.text =
            getString(R.string.result_confidence_format, (confidence * 100).roundToInt())
        binding.resultScanTime.text = getString(R.string.result_scan_time_label) + ": " +
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(capturedAt))
        binding.resultLocationStatus.text = getString(
            if (hasLocation) R.string.result_location_captured else R.string.result_location_not_captured
        )
        binding.resultModelVersion.text = getString(R.string.result_model_version_format, modelVersion)
        renderShareState()

        binding.viewTreatmentButton.setOnClickListener {
            findNavController().navigate(
                R.id.action_result_to_treatment,
                bundleOf("diseaseCode" to diseaseCode)
            )
        }

        binding.shareToCommunityButton.setOnClickListener { shareToCommunity() }
    }

    private fun renderShareState() {
        binding.resultSaveStatus.text = getString(
            if (sharedToCloud) R.string.result_save_status_shared else R.string.result_save_status_local
        )
        binding.shareToCommunityButton.isEnabled = !sharedToCloud
        binding.shareToCommunityButton.setText(
            if (sharedToCloud) R.string.result_shared_action_done else R.string.result_share_action
        )
    }

    private fun shareToCommunity() {
        binding.shareError.visibility = View.GONE

        val user = ServiceLocator.authRepository.getCurrentUser()
        if (user == null) {
            binding.shareError.visibility = View.VISIBLE
            binding.shareError.text = getString(R.string.result_share_requires_sign_in)
            return
        }
        if (localId < 0) {
            binding.shareError.visibility = View.VISIBLE
            binding.shareError.text = getString(R.string.result_share_failed)
            return
        }

        setSharing(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val record = runCatching { ServiceLocator.diagnosisHistoryRepository.getById(localId) }.getOrNull()
            if (record == null) {
                setSharing(false)
                binding.shareError.visibility = View.VISIBLE
                binding.shareError.text = getString(R.string.result_share_failed)
                return@launch
            }

            val result = runCatching {
                val cloudRecordId = ServiceLocator.diagnosisSharingRepository.shareScan(
                    record = record,
                    ownerUserId = user.uid,
                    farmId = null
                )
                ServiceLocator.diagnosisHistoryRepository.markShared(localId, cloudRecordId)
            }
            setSharing(false)

            if (result.isSuccess) {
                sharedToCloud = true
                renderShareState()
            } else {
                binding.shareError.visibility = View.VISIBLE
                binding.shareError.text = getString(R.string.result_share_failed)
            }
        }
    }

    private fun setSharing(sharing: Boolean) {
        binding.shareProgress.visibility = if (sharing) View.VISIBLE else View.GONE
        binding.shareToCommunityButton.isEnabled = !sharing && !sharedToCloud
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
