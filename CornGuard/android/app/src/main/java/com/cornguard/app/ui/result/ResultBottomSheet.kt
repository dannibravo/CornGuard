package com.cornguard.app.ui.result

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import coil.load
import com.cornguard.app.R
import com.cornguard.app.data.local.db.entity.DiagnosisRecordEntity
import com.cornguard.app.databinding.SheetResultBinding
import com.cornguard.app.di.ServiceLocator
import com.cornguard.app.ui.common.CornGuardBottomSheet
import com.cornguard.app.ui.common.TimeFormat
import com.cornguard.app.ui.treatment.FullDetailsBottomSheet
import kotlinx.coroutines.launch
import java.io.File

/**
 * Diagnosis result (caps 3 design's result sheet), shown after a scan and from History. Reads the
 * saved record from Room, so it works offline. "Share to Community" is the one online, opt-in
 * action (D-11: a scan is private by default). CornGuard saves every scan automatically, so the
 * design's "Save to History" button becomes a "Saved to History" note.
 */
class ResultBottomSheet : CornGuardBottomSheet() {

    private var _binding: SheetResultBinding? = null
    private val binding get() = _binding!!

    private val localId: Long get() = requireArguments().getLong(ARG_LOCAL_ID)
    private var sharedToCloud = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.resultThumbnail.clipToOutline = true
        binding.resultCloseButton.setOnClickListener { dismiss() }
        binding.shareToCommunityButton.setOnClickListener { shareToCommunity() }
        childFragmentManager.setFragmentResultListener(BarangayPickerBottomSheet.RESULT, viewLifecycleOwner) { _, result ->
            onBarangayPicked(
                result.getString(BarangayPickerBottomSheet.KEY_BARANGAY).orEmpty(),
                result.getString(BarangayPickerBottomSheet.KEY_MUNICIPALITY).orEmpty()
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val record = ServiceLocator.diagnosisHistoryRepository.getById(localId)
            if (record == null) {
                dismiss()
                return@launch
            }
            bind(record)
        }
    }

    private fun bind(record: DiagnosisRecordEntity) {
        binding.resultLabel.text = record.displayLabel
        binding.resultConfidence.text = getString(R.string.result_confidence_format, TimeFormat.percent(record.confidence))
        binding.resultThumbnail.load(File(record.imageUriOrLocalPath)) { crossfade(true) }

        renderLocation(record)
        binding.resultMeta.text = getString(
            R.string.result_meta_format,
            TimeFormat.dateTime(record.capturedAt),
            record.modelVersion
        )

        viewLifecycleOwner.lifecycleScope.launch {
            val reference = ServiceLocator.diseaseReferenceRepository.getByCode(record.diseaseCode)
            binding.resultTreatmentPreview.text = reference?.treatmentSteps ?: getString(R.string.result_no_treatment)
        }
        binding.resultViewDetails.setOnClickListener {
            FullDetailsBottomSheet.newInstance(record.diseaseCode)
                .show(parentFragmentManager, FullDetailsBottomSheet.TAG)
        }

        sharedToCloud = record.sharedToCloud
        renderShareState()
    }

    /**
     * caps 3 location line: "📍 Barangay, Municipality" when the scan is placed in a Bukidnon
     * barangay; otherwise an orange warning with "Set location manually" (the barangay picker).
     * The location can be changed until the scan is shared.
     */
    private fun renderLocation(record: DiagnosisRecordEntity) {
        val hasArea = !record.barangay.isNullOrBlank()
        val hasGps = record.latitude != null && record.longitude != null
        val color = ContextCompat.getColor(requireContext(), if (hasArea) R.color.cg_accent_green else R.color.cg_warning)
        binding.resultLocationIcon.setImageResource(if (hasArea) R.drawable.ic_location else R.drawable.ic_warning)
        binding.resultLocationIcon.setColorFilter(color)
        binding.resultLocationStatus.setTextColor(color)
        binding.resultLocationStatus.text = when {
            hasArea -> getString(R.string.result_location_area, record.barangay, record.municipality.orEmpty())
            hasGps -> getString(R.string.result_location_outside)
            else -> getString(R.string.result_location_not_captured)
        }
        binding.resultSetLocation.visibility = if (record.sharedToCloud) View.GONE else View.VISIBLE
        binding.resultSetLocation.setText(if (hasArea) R.string.result_change_location else R.string.result_set_location)
        binding.resultSetLocation.setOnClickListener {
            BarangayPickerBottomSheet().show(childFragmentManager, BarangayPickerBottomSheet.TAG)
        }
    }

    private fun onBarangayPicked(name: String, municipality: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val barangay = ServiceLocator.barangayResolver.find(name, municipality) ?: return@launch
            val (lat, lng) = barangay.centroid
            ServiceLocator.diagnosisHistoryRepository.updateLocation(
                localId, barangay.name, barangay.municipality, barangay.province, lat, lng
            )
            ServiceLocator.diagnosisHistoryRepository.getById(localId)?.let { renderLocation(it) }
        }
    }

    private fun renderShareState() {
        binding.shareToCommunityButton.isEnabled = !sharedToCloud
        binding.shareToCommunityButton.setText(
            if (sharedToCloud) R.string.result_shared_action_done else R.string.result_share_action
        )
    }

    private fun shareToCommunity() {
        binding.shareError.visibility = View.GONE
        val user = ServiceLocator.authRepository.getCurrentUser()
        if (user == null) {
            showShareError(R.string.result_share_requires_sign_in)
            return
        }

        setSharing(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val record = runCatching { ServiceLocator.diagnosisHistoryRepository.getById(localId) }.getOrNull()
            if (record == null) {
                setSharing(false)
                showShareError(R.string.result_share_failed)
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
                binding.resultSetLocation.visibility = View.GONE
            } else {
                showShareError(R.string.result_share_failed)
            }
        }
    }

    private fun setSharing(sharing: Boolean) {
        binding.shareProgress.visibility = if (sharing) View.VISIBLE else View.GONE
        binding.shareToCommunityButton.isEnabled = !sharing && !sharedToCloud
    }

    private fun showShareError(messageRes: Int) {
        binding.shareError.visibility = View.VISIBLE
        binding.shareError.setText(messageRes)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ResultBottomSheet"
        private const val ARG_LOCAL_ID = "localId"

        fun newInstance(localId: Long) = ResultBottomSheet().apply {
            arguments = bundleOf(ARG_LOCAL_ID to localId)
        }
    }
}
