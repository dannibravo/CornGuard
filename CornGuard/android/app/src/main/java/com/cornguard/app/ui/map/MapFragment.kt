package com.cornguard.app.ui.map

import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.cornguard.app.R
import com.cornguard.app.data.model.BarangayStat
import com.cornguard.app.data.model.Farm
import com.cornguard.app.databinding.FragmentMapBinding
import com.cornguard.app.di.ServiceLocator
import com.cornguard.app.model.DiseaseCode
import com.cornguard.app.ui.common.DiseaseGlyphDrawable
import com.cornguard.app.ui.common.runCatchingCancellable
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Outbreak map (UI redesign v2): the bundled Leaflet page (assets/map/outbreak-map.html) colours
 * the 464 Bukidnon barangays by this week's outbreak heat (backend barangayStats, fixed scale) and
 * shows the farmer's own farm pins. Disease report markers are not drawn for farmers.
 *
 * Stats are a live Convex subscription pushed into the page with window.updateMapData. The bottom
 * sheet shows the selected barangay (the farmer's own by default, or the one tapped on the map)
 * with its level, the disease pills that filter the heat, and the earlier "Reports near you"
 * lists. While the sheet moves, the page's bottom inset follows it (window.setBottomInset).
 *
 * The WebView and the tab's state (selection, disease filter, sheet lists) live in
 * [MapWebViewHolder], so switching back to the Map tab shows the ready map at once.
 */
class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private val verifiedAdapter = MapOccurrenceAdapter()
    private val nearbyAdapter = NearbyReportAdapter()

    private val pageReady get() = MapWebViewHolder.pageReady
    private var signedIn = false
    private var keyboardOpen = false
    private var latestStats: List<BarangayStat>? = null
    /** The signed-in farmer's own farms (My Farms), drawn as private pins. */
    private var latestFarms: List<Farm> = emptyList()
    /** The barangay shown in the sheet: tapped on the map, else the farmer's profile barangay. */
    private var selectedArea by MapWebViewHolder::selectedArea
    /** null = all diseases. */
    private var diseaseFilter by MapWebViewHolder::diseaseFilter
    private lateinit var sheetBehavior: BottomSheetBehavior<View>
    private val pills = mutableListOf<Pair<MaterialButton, String?>>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Map is a bottom-bar tab: no Back arrow (the tabs and system Back navigate).
        binding.mapBackButton.visibility = View.GONE
        binding.mapSignInButton.setOnClickListener { findNavController().navigate(R.id.authFragment) }

        binding.mapVerifiedRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.mapVerifiedRecyclerView.adapter = verifiedAdapter
        binding.mapNearbyRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.mapNearbyRecyclerView.adapter = nearbyAdapter

        setUpSheet()
        setUpPills()
        refreshSheet()

        attachWebView()
        watchKeyboard()
        observeAuthState()
    }

    /**
     * Collapsed 96dp (handle + barangay + level), expanded 286dp (330dp with Large text). The
     * handle toggles on tap; dragging snaps to the nearer state (BottomSheetBehavior). While the
     * sheet moves, the map's bottom inset follows it so Leaflet's legend and centre stay visible.
     */
    private fun setUpSheet() {
        val sheetView = binding.mapReportsSheet
        sheetView.layoutParams = sheetView.layoutParams.apply {
            height = resources.getDimensionPixelSize(
                if (resources.configuration.fontScale >= LARGE_TEXT_SCALE) R.dimen.map_sheet_expanded_large_text
                else R.dimen.map_sheet_expanded
            )
        }
        sheetBehavior = BottomSheetBehavior.from(sheetView)
        sheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                updateHandleDescription()
                pushBottomInset()
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) = pushBottomInset()
        })
        binding.mapSheetHandle.setOnClickListener {
            sheetBehavior.state = if (sheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
                BottomSheetBehavior.STATE_COLLAPSED
            } else {
                BottomSheetBehavior.STATE_EXPANDED
            }
        }
        updateHandleDescription()
    }

    private fun updateHandleDescription() {
        val expanded = sheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED
        binding.mapSheetHandle.contentDescription =
            getString(if (expanded) R.string.map_sheet_collapse else R.string.map_sheet_expand)
    }

    /** Visible sheet height in dp (= CSS px in the page); 0 while the sheet is hidden. */
    private fun visibleSheetDp(): Int {
        val b = _binding ?: return 0
        val sheet = b.mapReportsSheet
        if (sheet.visibility != View.VISIBLE) return 0
        return ((b.root.height - sheet.top).coerceAtLeast(0) / resources.displayMetrics.density).toInt()
    }

    /** [pan] = false when the sheet only hides/shows for the keyboard, so the map doesn't jump. */
    private fun pushBottomInset(pan: Boolean = true) {
        if (!pageReady) return
        evaluate("window.setBottomInset(${visibleSheetDp()}, $pan)")
    }

    private fun setUpPills() {
        pills.clear()
        pills += binding.mapPillAll to null
        pills += binding.mapPillRust to DiseaseCode.COMMON_RUST
        pills += binding.mapPillGrayLeafSpot to DiseaseCode.GRAY_LEAF_SPOT
        pills += binding.mapPillBlight to DiseaseCode.NORTHERN_LEAF_BLIGHT
        pills.forEach { (button, code) ->
            if (code != null) button.icon = DiseaseGlyphDrawable(requireContext(), code)
            button.isChecked = code == diseaseFilter
            button.setOnClickListener { selectDisease(code) }
        }
    }

    private fun refreshPills() = pills.forEach { (button, c) -> button.isChecked = c == diseaseFilter }

    /** Single choice; tapping the selected pill keeps it selected. */
    private fun selectDisease(code: String?) {
        diseaseFilter = code
        refreshPills()
        evaluate("window.setDiseaseFilter(${JSONObject.quote(code.orEmpty())})")
        refreshSheet()
    }

    /** Fills the sheet's area row and explanation from [selectedArea] and [latestStats]. */
    private fun refreshSheet() {
        val b = _binding ?: return
        val area = selectedArea
        if (area == null) {
            b.mapAreaName.setText(R.string.map_no_selection_title)
            b.mapAreaMunicipality.setText(R.string.map_no_selection_subtitle)
            b.mapLevelBadge.visibility = View.GONE
            b.mapAreaExplanation.visibility = View.GONE
            return
        }
        b.mapAreaName.text = area.first
        b.mapAreaMunicipality.text = area.second
        val summary = MapAreaSummary.summarize(latestStats.orEmpty(), area.first, area.second, diseaseFilter)
        AreaLevelText.applyBadge(b.mapLevelBadge, summary.level)
        b.mapAreaExplanation.visibility = View.VISIBLE
        b.mapAreaExplanation.text = AreaLevelText.explanation(requireContext(), summary, diseaseFilter)
    }

    private val mapListener = object : MapWebViewHolder.Listener {
        override fun onPageReady() {
            if (_binding != null) pushAll(firstLoad = true)
        }

        override fun onAreaSelected(area: Pair<String, String>) {
            if (_binding == null) return
            refreshSheet()
            sheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        }

        override fun onRendererGone() = recoverFromRendererLoss()
    }

    /** Re-attaches the kept map; a page that is already loaded gets this screen's state at once. */
    private fun attachWebView() {
        MapWebViewHolder.attach(binding.mapWebContainer, mapListener)
        if (pageReady) pushAll(firstLoad = false)
    }

    /**
     * Everything the page needs from this screen. [firstLoad] also sets the chrome, which fits the
     * province; a re-attached page keeps the farmer's view (its bottom inset follows the sheet).
     */
    private fun pushAll(firstLoad: Boolean) {
        evaluate("window.setTheme($isNightMode)")
        evaluate("window.setDiseaseFilter(${JSONObject.quote(diseaseFilter.orEmpty())})")
        if (firstLoad) pushChrome()
        pushData(latestStats)
        pushFarms(latestFarms)
        pushSelection()
    }

    /** The renderer died: show a fresh map in place, unless it just died too (then go back). */
    private fun recoverFromRendererLoss() {
        if (_binding == null || !isAdded) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastRendererRecoveryAt < RECOVERY_COOLDOWN_MS) {
            findNavController().popBackStack()
            return
        }
        lastRendererRecoveryAt = now
        attachWebView()
    }

    private val isNightMode: Boolean
        get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    /** Outlines [selectedArea] on the map (without moving it). */
    private fun pushSelection() {
        val area = selectedArea ?: return
        if (!pageReady) return
        evaluate("window.selectArea(${JSONObject.quote(area.first)}, ${JSONObject.quote(area.second)}, false)")
    }

    /** Keeps Leaflet's controls clear of the floating top row and the sheet. */
    private fun pushChrome() {
        binding.root.doOnLayout {
            val b = _binding ?: return@doOnLayout
            val top = (b.mapHeader.height / resources.displayMetrics.density).toInt()
            evaluate("window.setMapChrome($top, ${visibleSheetDp()})")
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeAuthState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                ServiceLocator.authRepository.observeAuthState().collect { user ->
                    signedIn = user != null
                    binding.mapSignedOut.visibility = if (signedIn) View.GONE else View.VISIBLE
                    updateSheetVisibility()
                    if (signedIn) {
                        loadGisData(user!!.uid)
                    } else {
                        latestStats = emptyList()
                        MapWebViewHolder.clearState()
                        refreshPills()
                        evaluate("window.setDiseaseFilter('')")
                        pushData(latestStats)
                        refreshSheet()
                    }
                }
            }
        }
        // Live outbreak levels (signed-in only on the backend; signed out this falls back to empty).
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                ServiceLocator.gisRepository.observeBarangayStats()
                    .catch { emit(emptyList()) }
                    .collect { stats ->
                        latestStats = stats
                        pushData(stats)
                        refreshSheet()
                    }
            }
        }
        // My Farms pins: only the signed-in farmer's own farms (the backend never returns others').
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                ServiceLocator.authRepository.observeAuthState()
                    .flatMapLatest { user ->
                        if (user == null) flowOf(emptyList())
                        else ServiceLocator.userFarmRepository.observeFarmsForUser(user.uid)
                    }
                    .catch { emit(emptyList()) }
                    .collect { farms ->
                        latestFarms = farms
                        pushFarms(farms)
                    }
            }
        }
    }

    /**
     * The window resizes for the soft keyboard, which left the map's search results only a sliver
     * between the top row and the sheet. Hide the sheet while the keyboard is up.
     */
    private fun watchKeyboard() {
        binding.root.viewTreeObserver.addOnGlobalLayoutListener {
            val root = _binding?.root ?: return@addOnGlobalLayoutListener
            val open = ViewCompat.getRootWindowInsets(root)?.isVisible(WindowInsetsCompat.Type.ime()) == true
            if (open != keyboardOpen) {
                keyboardOpen = open
                updateSheetVisibility()
            }
        }
    }

    private fun updateSheetVisibility() {
        binding.mapReportsSheet.visibility = if (signedIn && !keyboardOpen) View.VISIBLE else View.GONE
        binding.root.post { pushBottomInset(pan = false) }
    }

    /** Sends { stats } to the page (null = nothing new). */
    private fun pushData(stats: List<BarangayStat>?) {
        if (!pageReady || _binding == null || stats == null) return
        evaluate("window.updateMapData(${JSONObject().put("stats", statsJson(stats))})")
    }

    private fun pushFarms(farms: List<Farm>) {
        if (!pageReady || _binding == null) return
        evaluate("window.updateMapData(${JSONObject().put("farms", farmsJson(farms))})")
    }

    private fun evaluate(script: String) {
        if (_binding != null) MapWebViewHolder.evaluate(script)
    }

    /** The profile barangay: the sheet's default area and the "Reports near you" lists. */
    private fun loadGisData(uid: String) {
        // Back on the tab: the last lists show at once while they refresh.
        MapWebViewHolder.sheet?.takeIf { it.uid == uid }?.let { showSheetLists(it) }
        viewLifecycleOwner.lifecycleScope.launch {
            val profile = runCatchingCancellable { ServiceLocator.userFarmRepository.getUserProfile(uid) }.getOrNull()
            if (_binding == null) return@launch
            if (profile == null || profile.barangay.isBlank()) {
                binding.mapArea.setText(R.string.map_no_area_profile)
                binding.mapVerifiedEmpty.visibility = View.VISIBLE
                binding.mapNearbyEmpty.visibility = View.VISIBLE
                return@launch
            }
            binding.mapArea.text = getString(
                R.string.map_area_format,
                listOf(profile.barangay, profile.municipality).filter { it.isNotBlank() }.joinToString(", ")
            )
            // The sheet opens on the farmer's own barangay until they tap another one.
            if (selectedArea == null) {
                selectedArea = profile.barangay to profile.municipality
                refreshSheet()
                pushSelection()
            }

            val areaFilter = "barangay" to profile.barangay
            val occurrences = runCatchingCancellable {
                ServiceLocator.gisRepository.getVerifiedOccurrences(
                    areaFilter, diseaseFilter = null, municipality = profile.municipality.ifBlank { null }
                )
            }.getOrDefault(emptyList())
            val nearbyReports = runCatchingCancellable {
                ServiceLocator.gisRepository.getNearbyReports(
                    areaFilter, diseaseFilter = null, municipality = profile.municipality.ifBlank { null }
                )
            }.getOrDefault(emptyList())
            if (_binding == null) return@launch

            val sheet = MapWebViewHolder.SheetCache(uid, profile.barangay, profile.municipality, occurrences, nearbyReports)
            MapWebViewHolder.sheet = sheet
            showSheetLists(sheet)
        }
    }

    private fun showSheetLists(sheet: MapWebViewHolder.SheetCache) {
        binding.mapArea.text = getString(
            R.string.map_area_format,
            listOf(sheet.barangay, sheet.municipality).filter { it.isNotBlank() }.joinToString(", ")
        )
        verifiedAdapter.submitList(sheet.occurrences)
        binding.mapVerifiedEmpty.visibility = if (sheet.occurrences.isEmpty()) View.VISIBLE else View.GONE
        nearbyAdapter.submitList(sheet.nearby)
        binding.mapNearbyEmpty.visibility = if (sheet.nearby.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        // Keep the loaded map for the next visit (MapWebViewHolder); only unhook this screen.
        MapWebViewHolder.detach(mapListener)
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val RECOVERY_COOLDOWN_MS = 15_000L
        private var lastRendererRecoveryAt = 0L
        /** System font scale treated as "Large text" (taller expanded sheet). */
        private const val LARGE_TEXT_SCALE = 1.15f

        private fun statsJson(stats: List<BarangayStat>) = JSONArray().apply {
            stats.forEach { s ->
                put(JSONObject().apply {
                    put("barangay", s.barangay)
                    put("municipality", s.municipality)
                    put("diseaseCode", s.diseaseCode)
                    put("severityTier", s.severityTier)
                    put("weightedScore", s.weightedScore)
                    put("distinctFarms", s.distinctFarms)
                    put("rawReportCount", s.rawReportCount)
                    put("isActiveOutbreak", s.isActiveOutbreak)
                    put("monitoringStatus", s.monitoringStatus)
                    s.baselineFarms?.let { put("baselineFarms", it) }
                    s.lastWeekFarms?.let { put("lastWeekFarms", it) }
                    put("dataWeeks", s.dataWeeks)
                    put("historyScans", s.historyScans)
                    s.monitoringSince?.let { put("monitoringSince", it) }
                    s.moderateAt?.let { put("moderateAt", it) }
                    s.severeAt?.let { put("severeAt", it) }
                    // Absent = the page works it out (older backend; collecting rows have no heat).
                    s.intensity?.let { put("intensity", it) }
                })
            }
        }

        private fun farmsJson(farms: List<Farm>) = JSONArray().apply {
            farms.forEach { f ->
                put(JSONObject().apply {
                    put("name", f.farmNameOrLabel)
                    put("barangay", f.barangay)
                    put("municipality", f.municipality)
                    f.latitude?.let { put("latitude", it) }
                    f.longitude?.let { put("longitude", it) }
                })
            }
        }
    }
}
