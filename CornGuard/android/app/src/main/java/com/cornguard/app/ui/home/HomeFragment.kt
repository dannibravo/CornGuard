package com.cornguard.app.ui.home

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.cornguard.app.MainActivity
import com.cornguard.app.R
import com.cornguard.app.data.local.ProfileAreaStore
import com.cornguard.app.data.local.db.entity.DiagnosisRecordEntity
import com.cornguard.app.data.model.BarangayStat
import com.cornguard.app.data.model.UserProfile
import com.cornguard.app.databinding.FragmentHomeBinding
import com.cornguard.app.di.ServiceLocator
import com.cornguard.app.ui.common.DiseaseStyle
import com.cornguard.app.ui.common.Initials
import com.cornguard.app.ui.common.TimeFormat
import com.cornguard.app.ui.map.AreaLevelText
import com.cornguard.app.ui.map.MapAreaSummary
import com.cornguard.app.ui.result.ResultBottomSheet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

/**
 * Home (UI mock phase 2): greeting, bell (signed in) and the profile button that opens Settings;
 * the "Your barangay" card with this week's outbreak level (same wording as the map's sheet,
 * [AreaLevelText]); the Scan card; and the last scan. Works signed out and offline: the scan
 * path and last scan come from the phone, and the area card falls back to its last-known level
 * ([HomeAreaCache]) or says it needs internet.
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val areaCache by lazy { HomeAreaCache(requireContext()) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.homeScanCard.setOnClickListener { openTab(R.id.scanFragment) }
        binding.homeBellButton.setOnClickListener { findNavController().navigate(R.id.action_home_to_notifications) }
        binding.homeProfileButton.setOnClickListener { findNavController().navigate(R.id.action_home_to_profile) }
        observeProfile()
        observeUnreadCount()
        observeLastScan()
    }

    private fun openTab(destinationId: Int) {
        (activity as? MainActivity)?.selectTab(destinationId) ?: findNavController().navigate(destinationId)
    }

    // ---- Greeting, profile button and area card ---------------------------------------------

    private data class AreaState(
        val uid: String?,
        val profile: UserProfile?,
        val online: Boolean,
        /** Live stats; null until they arrive (or while offline). */
        val stats: List<BarangayStat>?
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeProfile() {
        val connectivity = ServiceLocator.connectivityObserver
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Live profile, not a one-off read: right after registering the profile is saved a
                // moment after sign-in, and a one-off read showed "Farmer" until the app restarted.
                val auth = runCatching { ServiceLocator.authRepository.observeAuthState() }.getOrNull()
                    ?: flowOf(null)
                auth.flatMapLatest { user ->
                    if (user == null) return@flatMapLatest flowOf(AreaState(null, null, false, null))
                    val profile: Flow<UserProfile?> = ServiceLocator.userFarmRepository
                        .observeUserProfile(user.uid).onStart { emit(null) }.catch { emit(null) }
                    combine(profile, connectivity.observe(), liveStats()) { p, online, stats ->
                        AreaState(user.uid, p, online, if (online) stats else null)
                    }
                }.collect(::render)
            }
        }
    }

    /** Live outbreak stats, starting with "not arrived yet" (null). */
    private fun liveStats(): Flow<List<BarangayStat>?> =
        ServiceLocator.gisRepository.observeBarangayStats()
            .map<List<BarangayStat>, List<BarangayStat>?> { it }
            .onStart { emit(null) }
            .catch { emit(null) }

    private fun render(state: AreaState) {
        val b = _binding ?: return
        val name = state.profile?.displayName?.takeIf { it.isNotBlank() }
        b.homeName.text = name ?: getString(R.string.default_farmer_name)
        val initials = Initials.of(name)
        b.homeProfileInitials.text = initials
        b.homeProfileInitials.visibility = if (initials.isNotEmpty()) View.VISIBLE else View.GONE
        b.homeProfileIcon.visibility = if (initials.isEmpty()) View.VISIBLE else View.GONE

        val uid = state.uid
        if (uid == null) {
            showAreaPrompt(R.string.home_area_signed_out) { findNavController().navigate(R.id.authFragment) }
            return
        }
        // The profile when it has loaded, otherwise the copy cached on this phone (works offline).
        val area = state.profile?.takeIf { it.barangay.isNotBlank() && it.municipality.isNotBlank() }
            ?.let { ProfileAreaStore.Area(it.barangay, it.municipality, it.province) }
            ?: ServiceLocator.profileAreaStore.get(uid)
        if (area == null) {
            showAreaPrompt(R.string.home_area_not_set) { findNavController().navigate(R.id.action_home_to_profile) }
            return
        }

        b.homeAreaName.text = getString(R.string.result_location_area, area.barangay, area.municipality)
        b.homeAreaCard.setOnClickListener { openTab(R.id.mapFragment) }
        b.homeAreaFooter.visibility = View.VISIBLE
        b.homeAreaSeeMap.visibility = View.VISIBLE
        b.homeAreaExplanation.visibility = View.VISIBLE

        val now = System.currentTimeMillis()
        val stats = state.stats
        if (stats != null) {
            val summary = MapAreaSummary.summarize(stats, area.barangay, area.municipality, diseaseFilter = null)
            val explanation = AreaLevelText.explanation(requireContext(), summary, diseaseFilter = null, now = now)
            AreaLevelText.applyBadge(b.homeAreaBadge, summary.level)
            b.homeAreaExplanation.text = explanation
            b.homeAreaUpdated.setText(R.string.home_area_updated_now)
            areaCache.put(uid, HomeAreaCache.Entry(area.barangay, area.municipality, summary.level, explanation, now))
        } else {
            val cached = areaCache.get(uid, area.barangay, area.municipality)
            if (cached != null) {
                AreaLevelText.applyBadge(b.homeAreaBadge, cached.level)
                b.homeAreaExplanation.text = cached.explanation
            } else {
                b.homeAreaBadge.visibility = View.GONE
                b.homeAreaExplanation.setText(R.string.home_area_offline_never)
            }
            b.homeAreaUpdated.text = when {
                state.online -> "" // live stats are on their way
                cached != null -> getString(
                    R.string.home_area_updated_ago,
                    TimeFormat.relative(requireContext(), minOf(cached.updatedAt, now - 60_000), now)
                )
                else -> ""
            }
        }
        b.homeAreaCard.contentDescription = getString(
            R.string.home_area_a11y,
            b.homeAreaName.text,
            if (b.homeAreaBadge.visibility == View.VISIBLE) b.homeAreaBadge.contentDescription else "",
            b.homeAreaExplanation.text
        )
    }

    /** Signed out / no barangay set: the card becomes a one-line prompt that opens the fix. */
    private fun showAreaPrompt(messageRes: Int, onClick: () -> Unit) {
        val b = _binding ?: return
        b.homeAreaName.setText(messageRes)
        b.homeAreaBadge.visibility = View.GONE
        b.homeAreaExplanation.visibility = View.GONE
        b.homeAreaFooter.visibility = View.GONE
        b.homeAreaCard.contentDescription = null
        b.homeAreaCard.setOnClickListener { onClick() }
    }

    // ---- Last scan ----------------------------------------------------------------------------

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeLastScan() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val auth = runCatching { ServiceLocator.authRepository.observeAuthState() }.getOrNull() ?: flowOf(null)
                auth.flatMapLatest { user -> ServiceLocator.diagnosisHistoryRepository.observeHistory(user?.uid) }
                    .map { it.firstOrNull() }
                    .catch { emit(null) }
                    .collect(::renderLastScan)
            }
        }
    }

    private fun renderLastScan(record: DiagnosisRecordEntity?) {
        val b = _binding ?: return
        b.homeLastScanSection.visibility = if (record == null) View.GONE else View.VISIBLE
        if (record == null) return
        val context = requireContext()
        b.homeLastScanTitle.text = getString(
            R.string.home_last_scan_title, DiseaseStyle.label(record.diseaseCode), TimeFormat.percent(record.confidence)
        )
        b.homeLastScanMeta.text = TimeFormat.dateTime(record.capturedAt)
        val (labelRes, textColor, bgColor) = when {
            record.isShareQueued -> Triple(
                R.string.history_badge_waiting,
                ContextCompat.getColor(context, R.color.notice_warning_text),
                ContextCompat.getColor(context, R.color.notice_warning_bg)
            )
            record.sharedToCloud -> ContextCompat.getColor(context, R.color.cg_primary).let {
                Triple(R.string.history_badge_shared, it, ColorUtils.setAlphaComponent(it, 0x20))
            }
            else -> ContextCompat.getColor(context, R.color.cg_text_secondary).let {
                Triple(R.string.history_badge_local, it, ColorUtils.setAlphaComponent(it, 0x20))
            }
        }
        b.homeLastScanStatus.setText(labelRes)
        b.homeLastScanStatus.setTextColor(textColor)
        b.homeLastScanStatus.backgroundTintList = ColorStateList.valueOf(bgColor)
        b.homeLastScanCard.setOnClickListener {
            ResultBottomSheet.newInstance(record.localId).show(childFragmentManager, ResultBottomSheet.TAG)
        }
    }

    // ---- Bell ---------------------------------------------------------------------------------

    /** Bell hidden while signed out; the badge follows the live unread count. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeUnreadCount() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                runCatching { ServiceLocator.authRepository.observeAuthState() }.getOrNull()
                    ?.flatMapLatest { user ->
                        binding.homeBellContainer.visibility = if (user != null) View.VISIBLE else View.GONE
                        if (user == null) flowOf(0) else ServiceLocator.notificationRepository.observeUnreadCount()
                    }
                    ?.collect(::showUnreadCount)
            }
        }
    }

    private fun showUnreadCount(count: Int) {
        binding.homeBellBadge.visibility = if (count > 0) View.VISIBLE else View.GONE
        binding.homeBellBadge.text =
            if (count > 9) getString(R.string.notifications_badge_overflow) else count.toString()
        binding.homeBellButton.contentDescription =
            if (count > 0) getString(R.string.notifications_unread_description, count)
            else getString(R.string.notifications_title)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
