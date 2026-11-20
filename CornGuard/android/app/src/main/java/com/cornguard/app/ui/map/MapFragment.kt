package com.cornguard.app.ui.map

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.cornguard.app.R
import com.cornguard.app.data.model.MapOccurrence
import com.cornguard.app.data.model.NearbyReportSummary
import com.cornguard.app.databinding.FragmentMapBinding
import com.cornguard.app.di.ServiceLocator
import com.cornguard.app.model.diseaseCodeToLabel
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.overlay.Marker
import java.text.DateFormat
import java.util.Date

/** Malaybalay City, Bukidnon — the project's own reference area (dev fixtures, seed data). */
private const val DEFAULT_MAP_LATITUDE = 8.1575
private const val DEFAULT_MAP_LONGITUDE = 125.1278
private const val DEFAULT_MAP_ZOOM = 12.0

/**
 * Base map (Phase 3, D-10 resolved 2026-10-30: osmdroid/OpenStreetMap) with disease occurrence
 * markers (Phase 4, resolved 2026-10-30), plus the existing list-based GIS view below it.
 * Online-only, same sign-in-prompt pattern as [com.cornguard.app.ui.community.CommunityFragment]
 * — the base map tiles render regardless of sign-in state, only markers/lists are gated.
 *
 * Two marker sources, deliberately kept separate (never conflated into one "confirmed" pin):
 * - [GisRepository.getVerifiedOccurrences][com.cornguard.app.data.repository.GisRepository.getVerifiedOccurrences]
 *   — the redacted `verifiedOccurrences` projection, solid pin icon. Rendered only after an admin
 *   has verified a record that had coordinates on it.
 * - [GisRepository.getNearbyReports][com.cornguard.app.data.repository.GisRepository.getNearbyReports]
 *   — communityPosts, any verification_status, hollow-circle icon. Never styled to look
 *   "confirmed," per D-08 and this phase's explicit constraint.
 *
 * A community post whose `linked_diagnosis_record_id` matches a record that IS in the verified
 * set is skipped (not plotted a second time) — that diagnosis already has its own verified pin;
 * showing both would be the same real-world event appearing twice on the map.
 *
 * "Verified Outbreaks"/markers are expected to render empty until an admin actually verifies
 * something with a location on it (D-08/D-02) — that's correct, not a bug.
 */
class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private val verifiedAdapter = MapOccurrenceAdapter()
    private val nearbyAdapter = NearbyReportAdapter()
    private var currentUid: String? = null

    /** Markers this fragment added, tracked separately so a reload removes exactly these — never osmdroid's own internal overlays. */
    private val mapMarkers = mutableListOf<Marker>()

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
        setUpBaseMap()
        binding.mapVerifiedRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.mapVerifiedRecyclerView.adapter = verifiedAdapter
        binding.mapNearbyRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.mapNearbyRecyclerView.adapter = nearbyAdapter

        binding.mapSignInButton.setOnClickListener {
            findNavController().navigate(R.id.authFragment)
        }
        binding.mapSwipeRefresh.setOnRefreshListener {
            val uid = currentUid
            if (uid != null) loadGisData(uid) else binding.mapSwipeRefresh.isRefreshing = false
        }

        observeAuthState()
    }

    /**
     * [org.osmdroid.config.Configuration] is set up process-wide in
     * [com.cornguard.app.CornGuardApplication] before any MapView is created — this only
     * configures this one instance (tile source, default position, zoom controls, touch
     * handling).
     */
    private fun setUpBaseMap() {
        val mapView = binding.mapView
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        // Fade out after use rather than staying on-screen permanently — large touch targets
        // for users who can't rely on pinch-zoom (claude/04_DEVELOPMENT_RULES.md #11), without
        // permanently covering the map.
        mapView.zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
        mapView.controller.setZoom(DEFAULT_MAP_ZOOM)
        mapView.controller.setCenter(GeoPoint(DEFAULT_MAP_LATITUDE, DEFAULT_MAP_LONGITUDE))

        // The screen's root is a ScrollView (for the lists below) — without this, dragging to
        // pan the map and scrolling the screen fight over the same vertical gesture. Standard
        // osmdroid-inside-a-ScrollView workaround: claim the gesture stream while a finger is on
        // the map, release it once lifted.
        mapView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> v.parent.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.parent.requestDisallowInterceptTouchEvent(false)
            }
            false // never consume — let the MapView's own touch handling run too
        }
    }

    override fun onResume() {
        super.onResume()
        _binding?.mapView?.onResume()
    }

    override fun onPause() {
        _binding?.mapView?.onPause()
        super.onPause()
    }

    private fun observeAuthState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                ServiceLocator.authRepository.observeAuthState().collect { user ->
                    val signedIn = user != null
                    binding.mapSignInPrompt.visibility = if (signedIn) View.GONE else View.VISIBLE
                    binding.mapSignInButton.visibility = if (signedIn) View.GONE else View.VISIBLE
                    binding.mapContent.visibility = if (signedIn) View.VISIBLE else View.GONE
                    currentUid = user?.uid
                    if (signedIn) loadGisData(user!!.uid) else binding.mapSwipeRefresh.isRefreshing = false
                }
            }
        }
    }

    private fun loadGisData(uid: String) {
        binding.mapSwipeRefresh.isRefreshing = true
        binding.mapMarkersError.visibility = View.GONE
        viewLifecycleOwner.lifecycleScope.launch {
            val profile = runCatching { ServiceLocator.userFarmRepository.getUserProfile(uid) }.getOrNull()
            if (profile == null || profile.barangay.isBlank()) {
                binding.mapSwipeRefresh.isRefreshing = false
                binding.mapVerifiedEmpty.text = getString(R.string.map_no_area_profile)
                binding.mapVerifiedEmpty.visibility = View.VISIBLE
                binding.mapNearbyEmpty.text = getString(R.string.map_no_area_profile)
                binding.mapNearbyEmpty.visibility = View.VISIBLE
                clearMapMarkers()
                return@launch
            }

            val areaFilter = "barangay" to profile.barangay

            val occurrencesResult = runCatching {
                ServiceLocator.gisRepository.getVerifiedOccurrences(areaFilter, diseaseFilter = null)
            }
            val occurrences = occurrencesResult.getOrDefault(emptyList())
            verifiedAdapter.submitList(occurrences)
            binding.mapVerifiedEmpty.text = getString(R.string.map_verified_empty)
            binding.mapVerifiedEmpty.visibility = if (occurrences.isEmpty()) View.VISIBLE else View.GONE

            val nearbyResult = runCatching {
                ServiceLocator.gisRepository.getNearbyReports(areaFilter, diseaseFilter = null)
            }
            val nearbyReports = nearbyResult.getOrDefault(emptyList())
            nearbyAdapter.submitList(nearbyReports)
            binding.mapNearbyEmpty.text = getString(R.string.map_nearby_empty)
            binding.mapNearbyEmpty.visibility = if (nearbyReports.isEmpty()) View.VISIBLE else View.GONE

            // A genuine fetch failure (e.g. a Firestore permission error) must be visible, not
            // silently rendered as "no reports" — runCatching + getOrDefault above already makes
            // each list degrade gracefully; this surfaces the failure on top of that, it doesn't
            // replace it.
            binding.mapMarkersError.visibility =
                if (occurrencesResult.isFailure || nearbyResult.isFailure) View.VISIBLE else View.GONE

            buildMapMarkers(occurrences, nearbyReports)
            binding.mapSwipeRefresh.isRefreshing = false
        }
    }

    /**
     * Verified occurrences always get a pin; a community report is skipped if its
     * `linkedDiagnosisRecordId` matches a verified occurrence already being plotted — the same
     * real-world event must not appear as two markers. Records/posts missing coordinates are
     * silently excluded (never plotted at (0,0) or any invented location).
     */
    private fun buildMapMarkers(occurrences: List<MapOccurrence>, nearbyReports: List<NearbyReportSummary>) {
        clearMapMarkers()
        val verifiedRecordIds = occurrences.map { it.occurrenceId }.toSet()

        occurrences.forEach { occurrence ->
            addMarker(
                latitude = occurrence.latitude,
                longitude = occurrence.longitude,
                icon = R.drawable.ic_marker_verified,
                title = "${getString(R.string.map_marker_verified_prefix)}: ${diseaseCodeToLabel(occurrence.diseaseCode)}",
                snippet = markerSnippet(occurrence.occurredAt, occurrence.barangay, occurrence.hasImage)
            )
        }

        nearbyReports
            .filter { it.linkedDiagnosisRecordId == null || it.linkedDiagnosisRecordId !in verifiedRecordIds }
            .forEach { report ->
                val lat = report.latitude ?: return@forEach
                val lon = report.longitude ?: return@forEach
                addMarker(
                    latitude = lat,
                    longitude = lon,
                    icon = R.drawable.ic_marker_unverified,
                    title = "${getString(R.string.map_marker_unverified_prefix)}: ${diseaseCodeToLabel(report.diseaseTag)}",
                    snippet = markerSnippet(report.createdAt, report.barangay, hasImage = null)
                )
            }

        binding.mapView.invalidate()
    }

    private fun markerSnippet(at: Long, barangay: String, hasImage: Boolean?): String {
        val dateText = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(at))
        val photoText = when (hasImage) {
            true -> getString(R.string.map_marker_photo_yes)
            false -> getString(R.string.map_marker_photo_no)
            null -> null // community reports don't carry a has_image field — omit, don't guess.
        }
        return listOfNotNull(barangay.ifBlank { null }, dateText, photoText).joinToString(" • ")
    }

    private fun addMarker(latitude: Double, longitude: Double, icon: Int, title: String, snippet: String) {
        val marker = Marker(binding.mapView).apply {
            position = GeoPoint(latitude, longitude)
            setIcon(ContextCompat.getDrawable(requireContext(), icon))
            this.title = title
            this.snippet = snippet
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
        binding.mapView.overlays.add(marker)
        mapMarkers.add(marker)
    }

    private fun clearMapMarkers() {
        binding.mapView.overlays.removeAll(mapMarkers)
        mapMarkers.clear()
        binding.mapView.invalidate()
    }

    override fun onDestroyView() {
        mapMarkers.clear()
        binding.mapView.onDetach()
        super.onDestroyView()
        _binding = null
    }
}
