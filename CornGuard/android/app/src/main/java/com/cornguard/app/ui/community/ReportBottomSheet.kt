package com.cornguard.app.ui.community

import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.cornguard.app.R
import com.cornguard.app.data.model.ReportReason
import com.cornguard.app.data.model.ReportTarget
import com.cornguard.app.databinding.SheetReportBinding
import com.cornguard.app.di.ServiceLocator
import com.cornguard.app.ui.common.CornGuardBottomSheet
import kotlinx.coroutines.launch

/**
 * "Report post" / "Report comment": one required reason plus an optional note, sent to
 * `reports:create`. On success it reports [CommunityFragment.RESULT_FEED_CHANGED] to its parent so
 * the feed reloads without the reported item (the server hides it for this user only).
 */
class ReportBottomSheet : CornGuardBottomSheet() {

    private var _binding: SheetReportBinding? = null
    private val binding get() = _binding!!

    private val target: ReportTarget
        get() = ReportTarget.valueOf(requireArguments().getString(ARG_TARGET) ?: ReportTarget.POST.name)
    private val targetId: String get() = requireArguments().getString(ARG_TARGET_ID).orEmpty()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        // Shrink the window for the keyboard; the sheet scrolls so the note field stays visible.
        @Suppress("DEPRECATION")
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.reportTitle.setText(
            if (target == ReportTarget.POST) R.string.report_post_title else R.string.report_comment_title
        )
        val minHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48f, resources.displayMetrics).toInt()
        ReportReason.entries.forEach { reason ->
            binding.reportReasons.addView(
                RadioButton(requireContext()).apply {
                    id = View.generateViewId()
                    tag = reason
                    setText(reason.label)
                    textSize = 17f
                    minimumHeight = minHeight
                },
                RadioGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
        }
        binding.reportReasons.setOnCheckedChangeListener { _, _ -> binding.reportSubmit.isEnabled = true }
        binding.reportClose.setOnClickListener { dismiss() }
        binding.reportCancel.setOnClickListener { dismiss() }
        binding.reportSubmit.setOnClickListener { submit() }
    }

    private fun selectedReason(): ReportReason? =
        binding.reportReasons.findViewById<RadioButton>(binding.reportReasons.checkedRadioButtonId)?.tag as? ReportReason

    private fun submit() {
        val reason = selectedReason() ?: return
        val note = binding.reportNote.text.toString()
        binding.reportSubmit.isEnabled = false
        binding.reportError.visibility = View.GONE
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching { ServiceLocator.communityRepository.report(target, targetId, reason, note) }
            if (_binding == null) return@launch
            if (result.isSuccess) {
                Toast.makeText(requireContext(), R.string.report_thanks, Toast.LENGTH_LONG).show()
                setFragmentResult(CommunityFragment.RESULT_FEED_CHANGED, Bundle())
                dismiss()
            } else {
                binding.reportError.visibility = View.VISIBLE
                binding.reportSubmit.isEnabled = true
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ReportBottomSheet"
        private const val ARG_TARGET = "target"
        private const val ARG_TARGET_ID = "targetId"

        fun newInstance(target: ReportTarget, targetId: String) = ReportBottomSheet().apply {
            arguments = bundleOf(ARG_TARGET to target.name, ARG_TARGET_ID to targetId)
        }
    }
}
