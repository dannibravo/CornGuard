package com.cornguard.app.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.cornguard.app.R
import com.cornguard.app.databinding.FragmentHomeBinding

/**
 * Landing screen. The single most important element here is [FragmentHomeBinding.scanCtaButton]:
 * it must always be reachable in one tap so the overall camera-in-three-taps NFR holds
 * (claude/02_PROJECT_CONTEXT.md).
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

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
        binding.scanCtaButton.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_scan)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
