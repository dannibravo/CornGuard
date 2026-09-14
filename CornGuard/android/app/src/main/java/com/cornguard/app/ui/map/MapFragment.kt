package com.cornguard.app.ui.map

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.cornguard.app.databinding.FragmentMapBinding

/**
 * Sprint 0 placeholder shell. GIS/heatmap rendering is built in Sprint 4
 * (feature/map-ui), after the map/GIS provider decision (D-10) and Panes's GIS service contract.
 */
class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
