package com.cornguard.app.ui.result

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import coil.load
import com.cornguard.app.R
import com.cornguard.app.data.local.db.entity.DiagnosisRecordEntity
import com.cornguard.app.data.model.Farm
import com.cornguard.app.databinding.SheetResultBinding
import com.cornguard.app.di.ServiceLocator
import com.cornguard.app.model.ConfidenceBand
import com.cornguard.app.ui.scan.ScanFragment
import com.cornguard.app.ui.common.CornGuardBottomSheet
import com.cornguard.app.ui.common.runCatchingCancellable
import com.cornguard.app.ui.common.TimeFormat
import com.cornguard.app.ui.treatment.FullDetailsBottomSheet
import com.cornguard.app.ui.treatment.ReviewLabelBinder
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Diagnosis result (caps 3 design's result sheet), shown after a scan and from History. Reads the
 * saved record from Room, so it works offline. "Share to Community" is the one online, opt-in
 * action (D-11: a scan is private by default). CornGuard saves every scan automatically, so the
 * design's "Save to History" button becomes a "Saved to History" note. A farmer with saved farms
 * (My Farms) picks which farm the scan is from, and it is filed at that farm.
 *
 * Offline, "Share" still files the scan under its barangay but queues it (data.sync.ShareQueue),
 * which sends it once the phone is back online; the farmer can cancel until then. Below the
 * auto-verify cut-off ([ConfidenceBand.LOW]) the sheet says it isn't sure and offers a retake.
 */
class ResultBottomSheet : CornGuardBottomSheet() {

    private var _binding: SheetResultBinding? = null
    private val binding get() = _binding!!

    private val localId: Long get() = requireArguments().getLong(ARG_LOCAL_ID)
    private var sharedToCloud = false
    /** Waiting in the offline share queue (live from Room). */
    private var shareQueued = false
    private var shareLastError: String? = null
    private var online = true
    private var sharing = false
    /** The farmer's saved farms, and the one this scan will be filed at (null = Settings barangay). */
    private var farms: List<Farm> = emptyList()
    private var selectedFarm: Farm? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.resultThumbnail.clipToOutline = true
        binding.resultCloseButton.setOnClickListener { dismiss() }
        binding.shareToCommunityButton.setOnClickListener { shareToCommunity() }
        binding.resultFarmChooser.setOnClickListener { chooseFarm() }
        binding.shareCancelButton.setOnClickListener { cancelQueuedShare() }
        binding.resultRetakeButton.setOnClickListener { retake() }
        online = ServiceLocator.connectivityObserver.isCurrentlyOnline()
        observeShareState()

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
            runCatchingCancellable { ServiceLocator.userFarmRepository.getUserProfile(user.uid) }
            farms = runCatchingCancellable { ServiceLocator.userFarmRepository.getFarmsForUser(user.uid) }
                .getOrDefault(emptyList())
            val last = lastFarmId(user.uid)
            selectedFarm = farms.firstOrNull { it.farmId == last } ?: farms.firstOrNull()
            if (_binding == null) return@launch
            renderFarmChoice()
            renderLocation(record)
        }
    }

    /** "Which farm is this from?" for an unshared scan; a hint to add farms when there are none. */
    private fun renderFarmChoice() {
        val b = _binding ?: return
        val signedIn = ServiceLocator.authRepository.getCurrentUser() != null
        val open = !sharedToCloud && !shareQueued
        val choosing = open && farms.isNotEmpty()
        b.resultFarmLabel.visibility = if (choosing) View.VISIBLE else View.GONE
        b.resultFarmChooser.visibility = if (choosing) View.VISIBLE else View.GONE
        b.resultFarmHint.visibility = if (open && signedIn && farms.isEmpty()) View.VISIBLE else View.GONE
        val label = selectedFarm?.let { farmLabel(it) } ?: getString(R.string.result_farm_none)
        b.resultFarmChooser.text = "$label  \u25BE"
    }

    private fun farmLabel(farm: Farm) = getString(R.string.result_farm_option, farm.farmNameOrLabel, farm.barangay)

    private fun chooseFarm() {
        val labels = farms.map { farmLabel(it) } + getString(R.string.result_farm_none)
        val checked = selectedFarm?.let { farms.indexOf(it) } ?: farms.size
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.result_farm_label)
            .setSingleChoiceItems(labels.toTypedArray(), checked) { dialog, which ->
                selectedFarm = farms.getOrNull(which)
                dialog.dismiss()
                renderFarmChoice()
                viewLifecycleOwner.lifecycleScope.launch {
                    val record = ServiceLocator.diagnosisHistoryRepository.getById(localId)
                    if (record != null && _binding != null) renderLocation(record)
                }
            }
            .show()
    }

    private fun lastFarmId(uid: String): String? =
        requireContext().getSharedPreferences(PREFS_LAST_FARM, Context.MODE_PRIVATE).getString(uid, null)

    private fun rememberFarm(uid: String, farmId: String?) {
        val prefs = requireContext().getSharedPreferences(PREFS_LAST_FARM, Context.MODE_PRIVATE).edit()
        if (farmId == null) prefs.remove(uid) else prefs.putString(uid, farmId)
        prefs.apply()
    }

    private fun bind(record: DiagnosisRecordEntity) {
        binding.resultLabel.text = record.displayLabel
        val band = ConfidenceBand.of(record.confidence)
        binding.resultConfidence.text = getString(
            R.string.result_confidence_band,
            TimeFormat.percent(record.confidence),
            getString(
                when (band) {
                    ConfidenceBand.HIGH -> R.string.result_band_high
                    ConfidenceBand.MEDIUM -> R.string.result_band_medium
                    ConfidenceBand.LOW -> R.string.result_band_low
                }
            )
        )
        val low = band == ConfidenceBand.LOW
        // Low confidence reads in the warning tone (the layout's green otherwise), as in the mock.
        if (low) binding.resultConfidence.setTextColor(ContextCompat.getColor(requireContext(), R.color.notice_warning_text))
        binding.resultLowConfidence.visibility = if (low) View.VISIBLE else View.GONE
        binding.resultRetakeButton.visibility = if (low) View.VISIBLE else View.GONE
        if (low) {
            binding.resultLowConfidenceText.text = buildSpannedString {
                bold { append(getString(R.string.result_low_title)) }
                append(' ')
                append(getString(R.string.result_low_body))
            }
        }
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
            ReviewLabelBinder.bind(binding.resultReviewLabel.root, reference)
        }
        binding.resultViewDetails.setOnClickListener {
            FullDetailsBottomSheet.newInstance(record.diseaseCode)
                .show(parentFragmentManager, FullDetailsBottomSheet.TAG)
        }

        sharedToCloud = record.sharedToCloud
        shareQueued = record.isShareQueued
        shareLastError = record.shareLastError
        renderShareState()
    }

    /** Shared / queued / failed-retry state follows Room (the share queue updates it in the background). */
    private fun observeShareState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    ServiceLocator.diagnosisHistoryRepository.observeById(localId).filterNotNull().collect { record ->
                        val justShared = record.sharedToCloud && !sharedToCloud
                        sharedToCloud = record.sharedToCloud
                        shareQueued = record.isShareQueued
                        shareLastError = record.shareLastError
                        renderShareState()
                        if (justShared) renderLocation(record)
                    }
                }
                ServiceLocator.connectivityObserver.observe().collect { isOnline ->
                    online = isOnline
                    renderShareState()
                }
            }
        }
    }

    /** A low-confidence result: back to the camera (closing this sheet, or opening Scan from History). */
    private fun retake() {
        if (parentFragment is ScanFragment) dismiss() else navigateTo(R.id.scanFragment)
    }

    private fun cancelQueuedShare() {
        viewLifecycleOwner.lifecycleScope.launch {
            ServiceLocator.diagnosisHistoryRepository.cancelQueuedShare(localId)
            _binding?.shareError?.visibility = View.GONE
        }
    }

    /**
     * caps 3 location line, read-only:
     *  - shared: the area it was shared under (frozen);
     *  - unshared, a farm chosen: that farm's barangay;
     *  - unshared, no farm: the account's current Settings area, with "Change in Settings";
     *  - no Settings area / signed out: an orange warning linking to Settings / sign-in.
     */
    private fun renderLocation(record: DiagnosisRecordEntity) {
        val user = ServiceLocator.authRepository.getCurrentUser()
        val farm = if (record.sharedToCloud) null else selectedFarm
        val area = when {
            record.sharedToCloud && !record.barangay.isNullOrBlank() ->
                getString(R.string.result_location_area, record.barangay, record.municipality.orEmpty())
            farm != null -> getString(R.string.result_location_area, farm.barangay, farm.municipality)
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
        link.visibility = if (record.sharedToCloud || farm != null) View.GONE else View.VISIBLE
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
        val b = _binding ?: return
        b.shareToCommunityButton.isEnabled = !sharedToCloud && !shareQueued && !sharing
        b.shareToCommunityButton.setText(
            when {
                sharedToCloud -> R.string.result_shared_action_done
                shareQueued -> R.string.result_share_queued
                !online -> R.string.result_share_when_online
                else -> R.string.result_share_action
            }
        )
        b.shareCancelButton.visibility = if (shareQueued) View.VISIBLE else View.GONE
        b.resultSavedNote.setText(
            when {
                sharedToCloud -> R.string.result_saved_shared
                shareQueued -> R.string.result_saved_waiting
                else -> R.string.result_saved_local
            }
        )
        val retryError = shareLastError
        if (shareQueued && retryError != null) {
            showShareNotice(getString(R.string.result_share_retry_error, retryError), isError = true)
        } else if (sharedToCloud) {
            b.shareError.visibility = View.GONE
        }
        renderFarmChoice()
    }

    private fun shareToCommunity() {
        binding.shareError.visibility = View.GONE
        val user = ServiceLocator.authRepository.getCurrentUser()
        if (user == null) {
            showShareError(R.string.result_share_requires_sign_in)
            return
        }

        // Offline: file it now and queue it (ShareQueue sends it once online).
        val sendNow = ServiceLocator.connectivityObserver.isCurrentlyOnline()
        setSharing(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val record = runCatchingCancellable { ServiceLocator.diagnosisHistoryRepository.getById(localId) }.getOrNull()
            if (record == null) {
                setSharing(false)
                showShareError(R.string.result_share_failed)
                return@launch
            }
            val resolver = ServiceLocator.barangayResolver
            // File the scan at the chosen farm (My Farms). Without one, use the account's current
            // Settings area, read fresh from the profile (sharing is online anyway).
            val farm = selectedFarm
            val barangay = if (farm != null) {
                resolver.find(farm.barangay, farm.municipality)
            } else if (!sendNow) {
                // Offline: the last-known Settings area cached on this phone.
                ServiceLocator.profileAreaStore.get(user.uid)?.let { resolver.find(it.barangay, it.municipality) }
            } else {
                val profile = runCatchingCancellable { ServiceLocator.userFarmRepository.getUserProfile(user.uid) }
                if (profile.isFailure) {
                    setSharing(false)
                    showShareError(R.string.result_share_failed)
                    return@launch
                }
                profile.getOrNull()?.let { resolver.find(it.barangay, it.municipality) }
            }
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
            // A farm scan's dot is the farm pin; otherwise it keeps the scan's GPS fix only when that
            // lies inside the barangay. (The backend files a farm scan at the farm either way.)
            val (lat, lng) = withContext(Dispatchers.Default) {
                resolver.placeScan(barangay, farm?.latitude ?: record.latitude, farm?.longitude ?: record.longitude)
            }
            ServiceLocator.diagnosisHistoryRepository.updateLocation(
                localId, barangay.name, barangay.municipality, barangay.province, lat, lng, farm?.farmId
            )
            val filed = record.copy(
                barangay = barangay.name, municipality = barangay.municipality, province = barangay.province,
                latitude = lat, longitude = lng
            )
            if (!sendNow) {
                queueShare(user.uid, farm?.farmId)
                return@launch
            }
            val result = runCatchingCancellable {
                val cloudRecordId = ServiceLocator.diagnosisSharingRepository.shareScan(
                    record = filed,
                    ownerUserId = user.uid,
                    farmId = farm?.farmId
                )
                ServiceLocator.diagnosisHistoryRepository.markShared(localId, cloudRecordId)
            }
            setSharing(false)
            if (result.isSuccess) {
                rememberFarm(user.uid, farm?.farmId)
                sharedToCloud = true
                renderShareState()
                renderLocation(filed.copy(sharedToCloud = true))
            } else if (!ServiceLocator.connectivityObserver.isCurrentlyOnline()) {
                // The connection dropped mid-share: queue it rather than make the farmer retry.
                queueShare(user.uid, farm?.farmId)
            } else {
                showShareError(R.string.result_share_failed)
            }
        }
    }

    private suspend fun queueShare(uid: String, farmId: String?) {
        ServiceLocator.diagnosisHistoryRepository.queueShare(localId, System.currentTimeMillis())
        ServiceLocator.shareQueue.kick()
        rememberFarm(uid, farmId)
        setSharing(false)
        showShareNotice(getString(R.string.result_share_queued_notice), isError = false)
    }

    private fun setSharing(sharing: Boolean) {
        this.sharing = sharing
        val b = _binding ?: return
        b.shareProgress.visibility = if (sharing) View.VISIBLE else View.GONE
        renderShareState()
    }

    private fun showShareError(messageRes: Int) = showShareNotice(getString(messageRes), isError = true)

    private fun showShareNotice(message: String, isError: Boolean) {
        val b = _binding ?: return
        b.shareError.visibility = View.VISIBLE
        b.shareError.text = message
        b.shareError.setTextColor(
            ContextCompat.getColor(requireContext(), if (isError) R.color.cg_danger else R.color.cg_text_secondary)
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ResultBottomSheet"
        /** The farm last chosen per account, so the next scan defaults to it. */
        private const val PREFS_LAST_FARM = "cornguard_last_farm"
        private const val ARG_LOCAL_ID = "localId"

        fun newInstance(localId: Long) = ResultBottomSheet().apply {
            arguments = bundleOf(ARG_LOCAL_ID to localId)
        }
    }
}
