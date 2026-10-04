package com.cornguard.app.ui.map

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import com.cornguard.app.R
import com.cornguard.app.data.model.BarangayStat
import com.cornguard.app.data.model.MapReport
import com.cornguard.app.databinding.FragmentMapBinding
import com.cornguard.app.di.ServiceLocator
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Outbreak Heatmap, ported from caps 3: the bundled Leaflet page (assets/map/outbreak-map.html)
 * draws the 464 Bukidnon barangay boundaries over OpenStreetMap, with one dot per verified report
 * coloured by its barangay's severity tier (barangayStats engine) and ringed by disease.
 *
 * Both data sets are live Convex subscriptions pushed into the page with window.updateMapData, so
 * the map updates without reopening the screen. The previous Verified Outbreaks / Nearby Reports
 * lists live on in the draggable "Reports near you" sheet.
 */
class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private val verifiedAdapter = MapOccurrenceAdapter()
    private val nearbyAdapter = NearbyReportAdapter()

    private var pageReady = false
    private var latestStats: List<BarangayStat>? = null
    private var latestReports: List<MapReport>? = null

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
        binding.mapBackButton.setOnClickListener { findNavController().popBackStack() }
        binding.mapSignInButton.setOnClickListener { findNavController().navigate(R.id.authFragment) }

        binding.mapVerifiedRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.mapVerifiedRecyclerView.adapter = verifiedAdapter
        binding.mapNearbyRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.mapNearbyRecyclerView.adapter = nearbyAdapter

        val sheet = BottomSheetBehavior.from(binding.mapReportsSheet)
        binding.mapSheetHandle.setOnClickListener {
            sheet.state = if (sheet.state == BottomSheetBehavior.STATE_EXPANDED) {
                BottomSheetBehavior.STATE_COLLAPSED
            } else {
                BottomSheetBehavior.STATE_EXPANDED
            }
        }

        setUpWebView()
        observeAuthState()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setUpWebView() {
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(requireContext()))
            .build()
        binding.mapWebView.apply {
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            webViewClient = object : WebViewClientCompat() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                    assetLoader.shouldInterceptRequest(request.url)
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    Log.d(BRIDGE_NAME, "${message.message()} (${message.sourceId()}:${message.lineNumber()})")
                    return true
                }
            }
            addJavascriptInterface(Bridge(), BRIDGE_NAME)
            loadUrl(MAP_URL)
        }
    }

    /** Called from the page once Leaflet and the boundaries are loaded (on a WebView thread). */
    private inner class Bridge {
        @JavascriptInterface
        fun onReady() {
            binding.mapWebView.post {
                if (_binding == null) return@post
                pageReady = true
                pushChrome()
                pushData(latestStats, latestReports)
            }
        }
    }

    /** Keeps Leaflet's zoom/legend clear of the header overlay and the collapsed sheet. */
    private fun pushChrome() {
        binding.root.doOnLayout {
            val density = resources.displayMetrics.density
            val top = (binding.mapHeader.height / density).toInt()
            val bottom = (resources.getDimensionPixelSize(R.dimen.map_sheet_peek) / density).toInt()
            evaluate("window.setMapChrome($top, $bottom)")
        }
    }

    private fun observeAuthState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                ServiceLocator.authRepository.observeAuthState().collect { user ->
                    val signedIn = user != null
                    binding.mapSignedOut.visibility = if (signedIn) View.GONE else View.VISIBLE
                    binding.mapReportsSheet.visibility = if (signedIn) View.VISIBLE else View.GONE
                    if (signedIn) {
                        loadGisData(user!!.uid)
                    } else {
                        latestStats = emptyList()
                        latestReports = emptyList()
                        pushData(latestStats, latestReports)
                    }
                }
            }
        }
        // Live map data (signed-in only on the backend; signed out these fall back to empty).
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                ServiceLocator.gisRepository.observeBarangayStats()
                    .catch { emit(emptyList()) }
                    .collect { stats ->
                        latestStats = stats
                        pushData(stats, null)
                    }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                ServiceLocator.gisRepository.observeMapReports()
                    .catch { emit(emptyList()) }
                    .collect { reports ->
                        latestReports = reports
                        pushData(null, reports)
                    }
            }
        }
    }

    /** Sends { stats?, detections? } to the page; a null part keeps the page's last value. */
    private fun pushData(stats: List<BarangayStat>?, reports: List<MapReport>?) {
        if (!pageReady || _binding == null) return
        if (stats == null && reports == null) return
        val payload = JSONObject()
        stats?.let { payload.put("stats", statsJson(it)) }
        reports?.let { payload.put("detections", reportsJson(it)) }
        evaluate("window.updateMapData($payload)")
    }

    private fun evaluate(script: String) {
        _binding?.mapWebView?.evaluateJavascript(script, null)
    }

    /** The previous list view, now inside the "Reports near you" sheet. */
    private fun loadGisData(uid: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val profile = runCatching { ServiceLocator.userFarmRepository.getUserProfile(uid) }.getOrNull()
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

            val areaFilter = "barangay" to profile.barangay
            val occurrences = runCatching {
                ServiceLocator.gisRepository.getVerifiedOccurrences(areaFilter, diseaseFilter = null)
            }.getOrDefault(emptyList())
            val nearbyReports = runCatching {
                ServiceLocator.gisRepository.getNearbyReports(areaFilter, diseaseFilter = null)
            }.getOrDefault(emptyList())
            if (_binding == null) return@launch

            verifiedAdapter.submitList(occurrences)
            binding.mapVerifiedEmpty.visibility = if (occurrences.isEmpty()) View.VISIBLE else View.GONE
            nearbyAdapter.submitList(nearbyReports)
            binding.mapNearbyEmpty.visibility = if (nearbyReports.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        pageReady = false
        binding.mapWebView.apply {
            removeJavascriptInterface(BRIDGE_NAME)
            stopLoading()
            destroy()
        }
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val BRIDGE_NAME = "CornGuardMap"
        private const val MAP_URL = "https://appassets.androidplatform.net/assets/map/outbreak-map.html"

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
                })
            }
        }

        private fun reportsJson(reports: List<MapReport>) = JSONArray().apply {
            reports.forEach { r ->
                put(JSONObject().apply {
                    put("recordId", r.recordId)
                    put("diseaseCode", r.diseaseCode)
                    put("confidence", r.confidence)
                    put("capturedAt", r.capturedAt)
                    put("latitude", r.latitude)
                    put("longitude", r.longitude)
                    put("barangay", r.barangay)
                    put("municipality", r.municipality)
                })
            }
        }
    }
}
