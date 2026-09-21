package com.cornguard.app.ui.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.cornguard.app.R
import com.cornguard.app.data.local.db.entity.DiagnosisRecordEntity
import com.cornguard.app.databinding.FragmentScanBinding
import com.cornguard.app.di.ServiceLocator
import com.cornguard.app.model.DetectionResult
import com.cornguard.app.model.ModelNotReadyException
import com.cornguard.app.permissions.AppPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Camera capture / gallery selection, classification, and local save. Entirely offline — never
 * touches Firebase (claude/01_MASTER_DEVELOPMENT_CONTEXT.md Project Principle). Classification
 * goes through [ServiceLocator.cornLeafClassifier], which is [com.cornguard.app.model.PlaceholderCornLeafClassifier]
 * until Sprint 2's real TFLite model lands — [ModelNotReadyException] must be surfaced honestly,
 * never papered over with a fabricated result (claude/15_CLAUDE.md Model Rule).
 *
 * Attaches an approximate location to the saved record via [ServiceLocator.locationHelper] when
 * [AppPermission.LOCATION] is already granted — best-effort, never requested synchronously in the
 * save path, so a denial or missing fix never blocks or delays the scan itself
 * (claude/04_DEVELOPMENT_RULES.md #9).
 */
class ScanFragment : Fragment() {

    private var _binding: FragmentScanBinding? = null
    private val binding get() = _binding!!

    private var pendingCameraUri: Uri? = null

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) launchCamera() else showError(getString(R.string.scan_permission_denied_camera))
        }

    private val galleryPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) launchGallery() else showError(getString(R.string.scan_permission_denied_gallery))
        }

    // Fired once, best-effort, after a successful detection — never awaited, never blocks saving.
    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { /* no-op: next scan will pick it up if granted */ }

    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val uri = pendingCameraUri
            if (success && uri != null) handleImageSelected(uri)
        }

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) handleImageSelected(uri)
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.scanCameraButton.setOnClickListener { onCameraClicked() }
        binding.scanGalleryButton.setOnClickListener { onGalleryClicked() }
    }

    private fun onCameraClicked() {
        clearError()
        if (ServiceLocator.permissionManager.isGranted(AppPermission.CAMERA)) {
            launchCamera()
        } else {
            cameraPermissionLauncher.launch(AppPermission.CAMERA.manifestPermissions.first())
        }
    }

    private fun onGalleryClicked() {
        clearError()
        if (ServiceLocator.permissionManager.isGranted(AppPermission.GALLERY)) {
            launchGallery()
        } else {
            galleryPermissionLauncher.launch(AppPermission.GALLERY.manifestPermissions.first())
        }
    }

    private fun launchCamera() {
        val dir = File(requireContext().cacheDir, "scan_capture").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            file
        )
        pendingCameraUri = uri
        takePictureLauncher.launch(uri)
    }

    private fun launchGallery() {
        pickImageLauncher.launch("image/*")
    }

    private fun handleImageSelected(uri: Uri) {
        clearError()
        setProcessing(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    requireContext().contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                }.getOrNull()
            }
            if (bitmap == null) {
                setProcessing(false)
                showError(getString(R.string.scan_invalid_image))
                return@launch
            }
            binding.scanPreviewImage.setImageBitmap(bitmap)
            binding.scanPreviewImage.visibility = View.VISIBLE

            val detection = withContext(Dispatchers.Default) {
                runCatching { ServiceLocator.cornLeafClassifier.classify(bitmap) }
            }
            setProcessing(false)

            detection.onSuccess { result -> onDetectionSuccess(bitmap, result) }
            detection.onFailure { error ->
                showError(
                    when (error) {
                        is ModelNotReadyException -> getString(R.string.scan_model_not_ready)
                        is IllegalArgumentException -> getString(R.string.scan_invalid_image)
                        else -> error.message ?: getString(R.string.scan_invalid_image)
                    }
                )
            }
        }
    }

    private fun onDetectionSuccess(bitmap: Bitmap, result: DetectionResult) {
        viewLifecycleOwner.lifecycleScope.launch {
            val imagePath = withContext(Dispatchers.IO) { persistImage(bitmap) }
            val capturedAt = System.currentTimeMillis()
            val location = captureLocationBestEffort()

            val record = DiagnosisRecordEntity(
                userId = null,
                farmId = null,
                diseaseCode = result.diseaseCode,
                displayLabel = result.displayLabel,
                confidence = result.confidence,
                imageUriOrLocalPath = imagePath,
                capturedAt = capturedAt,
                latitude = location?.latitude,
                longitude = location?.longitude,
                barangay = null,
                municipality = null,
                province = null,
                modelVersion = result.modelVersion
            )
            val localId = ServiceLocator.diagnosisHistoryRepository.saveScan(record)
            findNavController().navigate(
                R.id.action_scan_to_result,
                bundleOf(
                    "localId" to localId,
                    "diseaseCode" to result.diseaseCode,
                    "displayLabel" to result.displayLabel,
                    "confidence" to result.confidence,
                    "capturedAt" to capturedAt,
                    "modelVersion" to result.modelVersion,
                    "sharedToCloud" to false,
                    "hasLocation" to (location != null)
                )
            )
        }
    }

    /**
     * Never requests permission synchronously — if it's not already granted, this kicks off a
     * request for *next* time and returns null now, so a scan is never held up waiting on a
     * permission dialog or a location fix.
     */
    private suspend fun captureLocationBestEffort(): android.location.Location? {
        if (!ServiceLocator.permissionManager.isGranted(AppPermission.LOCATION)) {
            locationPermissionLauncher.launch(AppPermission.LOCATION.manifestPermissions.toTypedArray())
            return null
        }
        return runCatching { ServiceLocator.locationHelper.getLastKnownLocation() }.getOrNull()
    }

    private fun persistImage(bitmap: Bitmap): String {
        val dir = File(requireContext().filesDir, "scans").apply { mkdirs() }
        val file = File(dir, "scan_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) }
        return file.absolutePath
    }

    private fun setProcessing(processing: Boolean) {
        binding.scanProgress.visibility = if (processing) View.VISIBLE else View.GONE
        binding.scanProcessingLabel.visibility = if (processing) View.VISIBLE else View.GONE
        binding.scanCameraButton.isEnabled = !processing
        binding.scanGalleryButton.isEnabled = !processing
    }

    private fun showError(message: String) {
        binding.scanError.visibility = View.VISIBLE
        binding.scanError.text = message
    }

    private fun clearError() {
        binding.scanError.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
