package com.cornguard.app.ui.result

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import coil.load
import com.cornguard.app.R
import com.cornguard.app.data.local.db.entity.DiagnosisRecordEntity
import com.cornguard.app.databinding.SheetResultBinding
import com.cornguard.app.di.ServiceLocator
import com.cornguard.app.ui.common.CornGuardBottomSheet
import com.cornguard.app.ui.common.TimeFormat
import com.cornguard.app.ui.treatment.FullDetailsBottomSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

        viewLifecycleOwner.lifecycleScope.launch {
            val record = ServiceLocator.diagnosisHistoryRepository.getById(localId)
            if (record == null) {
                dismiss()
                return@launch
            }
            bind(record)
            // Refresh the cached Settings area (online), so the location line is current.
            val user = ServiceLocator.authRepository.getCurrentUser() ?: return@launch
            if (record.sharedToCloud) return@launch
            runCatching { ServiceLocator.userFarmRepository.getUserProfile(user.uid) }
            if (_binding != null) renderLocation(record)
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
     * caps 3 location line. A scan is always filed under the farmer's Settings area (the single
     * source of a scan's location), so this is read-only:
     *  - shared: the area it was shared under (frozen);
     *  - unshared: the account's current Settings area, with "Change in Settings";
     *  - no Settings area / signed out: an orange warning linking to Settings / sign-in.
     */
    private fun renderLocation(record: DiagnosisRecordEntity) {
        val user = ServiceLocator.authRepository.getCurrentUser()
        val area = when {
            record.sharedToCloud && !record.barangay.isNullOrBlank() ->
                getString(R.string.result_location_area, record.barangay, record.municipality.orEmpty())
            user != null -> ServiceLocator.profileAreaStore.get(user.uid)?.let {
                getString(R.string.result_location_area, it.barangay, it.municipality)
            }
            else -> null
        }
        val hasArea = area != null
        val color = ContextCompat.getColor(requireContext(), if (hasArea) R.color.cg_accent_green else R.color.cg_warning)
        binding.resultLocationIcon.setImageResource(if (hasArea) R.drawable.ic_location else R.drawable.ic_warning)
        binding.resultLocationIcon.setColorFilter(color)
        binding.resultLocationStatus.setTextColor(color)
        binding.resultLocationStatus.text = area ?: getString(
            if (user == null) R.string.result_location_signed_out else R.string.result_location_not_set
        )

        val link = binding.resultLocationSettings
        link.visibility = if (record.sharedToCloud) View.GONE else View.VISIBLE
        link.setTextColor(ContextCompat.getColor(requireContext(), if (hasArea) R.color.cg_text_secondary else R.color.cg_warning))
        link.setText(
            when {
                user == null -> R.string.result_location_sign_in
                hasArea -> R.string.result_location_change_in_settings
                else -> R.string.result_location_set_in_settings
            }
        )
        link.setOnClickListener { navigateTo(if (user == null) R.id.authFragment else R.id.profileFragment) }
    }

    private fun navigateTo(destination: Int) {
        val nav = findNavController()
        dismiss()
        nav.navigate(destination)
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
            // File the scan under the account's current Settings area, read fresh from the profile
            // (sharing is online anyway), so an unshared scan always follows Settings.
            val profile = runCatching { ServiceLocator.userFarmRepository.getUserProfile(user.uid) }
            if (profile.isFailure) {
                setSharing(false)
                showShareError(R.string.result_share_failed)
                return@launch
            }
            val resolver = ServiceLocator.barangayResolver
            val barangay = profile.getOrNull()?.let { resolver.find(it.barangay, it.municipality) }
            if (barangay == null) {
                setSharing(false)
                renderLocation(record)
                showShareError(R.string.result_share_requires_location)
                return@launch
            }
            // A scan counts for one farm only: another account on this phone can't share it as theirs.
            if (record.userId != null && record.userId != user.uid) {
                setSharing(false)
                showShareError(R.string.result_share_not_owner)
                return@launch
            }
            if (record.userId == null) ServiceLocator.diagnosisHistoryRepository.assignOwner(localId, user.uid)
            // The dot keeps the scan's GPS fix only when it lies inside that barangay.
            val (lat, lng) = withContext(Dispatchers.Default) {
                resolver.placeScan(barangay, record.latitude, record.longitude)
            }
            ServiceLocator.diagnosisHistoryRepository.updateLocation(
                localId, barangay.name, barangay.municipality, barangay.province, lat, lng
            )
            val filed = record.copy(
                barangay = barangay.name, municipality = barangay.municipality, province = barangay.province,
                latitude = lat, longitude = lng
            )
            val result = runCatching {
                val cloudRecordId = ServiceLocator.diagnosisSharingRepository.shareScan(
                    record = filed,
                    ownerUserId = user.uid,
                    farmId = null
                )
                ServiceLocator.diagnosisHistoryRepository.markShared(localId, cloudRecordId)
            }
            setSharing(false)
            if (result.isSuccess) {
                sharedToCloud = true
                renderShareState()
                renderLocation(filed.copy(sharedToCloud = true))
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
