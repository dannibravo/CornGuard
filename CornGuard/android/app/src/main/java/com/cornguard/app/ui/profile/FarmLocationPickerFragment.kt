package com.cornguard.app.ui.profile

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import com.cornguard.app.BuildConfig
import com.cornguard.app.R
import com.cornguard.app.databinding.FragmentFarmPickerBinding
import com.cornguard.app.di.ServiceLocator
import com.cornguard.app.location.BarangayResolver
import com.cornguard.app.permissions.AppPermission
import com.cornguard.app.ui.common.runCatchingCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * My Farms: add a farm by dropping a pin on the map (assets/map/farm-picker.html), or with "Use my
 * location" while standing at the farm. The barangay is worked out from the pin with the bundled
 * Bukidnon boundaries; a pin outside them can't be saved (farms only work inside the outbreak map's
 * area). Farms are private to their owner (users.farmsForUser / users.createFarm).
 */
class FarmLocationPickerFragment : Fragment() {

    private var _binding: FragmentFarmPickerBinding? = null
    private val binding get() = _binding!!

    private var pin: Pair<Double, Double>? = null
    private var pinBarangay: BarangayResolver.Barangay? = null
    private var saving = false

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            if (results.values.any { it }) useMyLocation() else showError(R.string.farm_picker_location_denied)
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFarmPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (ServiceLocator.authRepository.getCurrentUser() == null) {
            findNavController().popBackStack()
            return
        }
        binding.farmPickerBack.setOnClickListener { findNavController().popBackStack() }
        binding.farmPickerName.doAfterTextChanged { updateSaveEnabled() }
        binding.farmPickerUseLocation.setOnClickListener { useMyLocation() }
        binding.farmPickerSave.setOnClickListener { save() }
        setUpWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setUpWebView() {
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(requireContext()))
            .build()
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        binding.farmPickerWebView.apply {
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            webViewClient = object : WebViewClientCompat() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                    assetLoader.shouldInterceptRequest(request.url)

                // A dead renderer can't be reused; leave the screen instead of showing a blank map.
                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    view.post { if (isAdded) findNavController().popBackStack() }
                    return true
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    Log.d(BRIDGE_NAME, "${message.message()} (${message.sourceId()}:${message.lineNumber()})")
                    return true
                }
            }
            addJavascriptInterface(Bridge(), BRIDGE_NAME)
            loadUrl(PAGE_URL)
        }
    }

    /** Called from the page (on a WebView thread). */
    private inner class Bridge {
        @JavascriptInterface
        fun onReady() = Unit

        @JavascriptInterface
        fun onPin(latitude: Double, longitude: Double) {
            _binding?.farmPickerWebView?.post { onPinMoved(latitude, longitude) }
        }
    }

    private fun onPinMoved(latitude: Double, longitude: Double) {
        if (_binding == null) return
        pin = latitude to longitude
        hideError()
        viewLifecycleOwner.lifecycleScope.launch {
            val barangay = withContext(Dispatchers.Default) {
                ServiceLocator.barangayResolver.resolve(latitude, longitude)
            }
            if (_binding == null || pin != latitude to longitude) return@launch
            pinBarangay = barangay
            renderArea()
        }
    }

    private fun renderArea() {
        val barangay = pinBarangay
        val color = when {
            pin == null -> R.color.cg_text_secondary
            barangay == null -> R.color.cg_warning
            else -> R.color.cg_accent_green
        }
        binding.farmPickerArea.text = when {
            pin == null -> getString(R.string.farm_picker_no_pin)
            barangay == null -> getString(R.string.farm_picker_outside)
            else -> getString(R.string.result_location_area, barangay.name, barangay.municipality)
        }
        val tint = ContextCompat.getColor(requireContext(), color)
        binding.farmPickerArea.setTextColor(tint)
        binding.farmPickerAreaIcon.setColorFilter(tint)
        updateSaveEnabled()
    }

    private fun updateSaveEnabled() {
        val b = _binding ?: return
        b.farmPickerSave.isEnabled = !saving && pinBarangay != null && b.farmPickerName.text.toString().isNotBlank()
    }

    private fun useMyLocation() {
        if (!ServiceLocator.permissionManager.isGranted(AppPermission.LOCATION)) {
            locationPermissionLauncher.launch(AppPermission.LOCATION.manifestPermissions.toTypedArray())
            return
        }
        binding.farmPickerUseLocation.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val location = runCatchingCancellable { ServiceLocator.locationHelper.getLastKnownLocation() }.getOrNull()
            if (_binding == null) return@launch
            binding.farmPickerUseLocation.isEnabled = true
            if (location == null) {
                showError(R.string.farm_picker_location_unavailable)
                return@launch
            }
            binding.farmPickerWebView.evaluateJavascript("window.setPin(${location.latitude}, ${location.longitude})", null)
            onPinMoved(location.latitude, location.longitude)
        }
    }

    private fun save() {
        val user = ServiceLocator.authRepository.getCurrentUser() ?: return
        val (latitude, longitude) = pin ?: return
        val barangay = pinBarangay ?: return
        val name = binding.farmPickerName.text.toString().trim()
        if (name.isEmpty()) return
        saving = true
        updateSaveEnabled()
        hideError()
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatchingCancellable {
                ServiceLocator.userFarmRepository.createFarm(
                    ownerUserId = user.uid,
                    farmNameOrLabel = name,
                    barangay = barangay.name,
                    municipality = barangay.municipality,
                    province = barangay.province,
                    latitude = latitude,
                    longitude = longitude
                )
            }
            if (_binding == null) return@launch
            saving = false
            if (result.isSuccess) {
                findNavController().popBackStack()
            } else {
                updateSaveEnabled()
                showError(R.string.farm_picker_save_failed)
            }
        }
    }

    private fun showError(messageRes: Int) {
        val b = _binding ?: return
        b.farmPickerError.setText(messageRes)
        b.farmPickerError.visibility = View.VISIBLE
    }

    private fun hideError() {
        _binding?.farmPickerError?.visibility = View.GONE
    }

    override fun onDestroyView() {
        _binding?.farmPickerWebView?.apply {
            removeJavascriptInterface(BRIDGE_NAME)
            destroy()
        }
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val BRIDGE_NAME = "CornGuardFarmPicker"
        const val PAGE_URL = "https://appassets.androidplatform.net/assets/map/farm-picker.html"
    }
}
