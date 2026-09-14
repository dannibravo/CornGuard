package com.cornguard.app.ui.scan

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.cornguard.app.databinding.FragmentScanBinding

/**
 * Sprint 0 placeholder shell. Camera capture, gallery selection, preprocessing, and TFLite
 * inference are built in Sprint 2 (feature/tflite-integration), gated on the model contract being
 * frozen (D-04) — see [com.cornguard.app.model.CornLeafClassifier].
 */
class ScanFragment : Fragment() {

    private var _binding: FragmentScanBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
