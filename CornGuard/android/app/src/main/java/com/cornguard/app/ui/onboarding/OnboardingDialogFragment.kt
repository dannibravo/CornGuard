package com.cornguard.app.ui.onboarding

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentDialog
import androidx.activity.addCallback
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.edit
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.cornguard.app.R
import com.cornguard.app.databinding.DialogOnboardingBinding
import com.cornguard.app.di.ServiceLocator
import com.cornguard.app.ui.common.runCatchingCancellable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The CornGuard guide: 22 annotated app screenshots in 7 chapters (Welcome, Scan, History,
 * Community, Outbreak Map, Alerts, Settings), each with a title and a caption whose ①②③ match the
 * numbered highlights in the picture. Screenshots are built by android/tools/tutorial
 * (shots.json + annotate.py) — re-run it when a screen changes.
 *
 * Shown once to "Fresh" accounts by MainActivity (finishing or skipping marks it done on this phone
 * and via `users:completeOnboarding`), and again any time from Settings with [replay] = true, which
 * changes nothing on the account.
 */
class OnboardingDialogFragment : DialogFragment() {

    private data class Slide(
        @StringRes val chapter: Int,
        @DrawableRes val image: Int,
        @StringRes val title: Int,
        @StringRes val body: Int
    )

    private var _binding: DialogOnboardingBinding? = null
    private val binding get() = _binding!!
    private var index = 0
    private val userId: String get() = requireArguments().getString(ARG_USER_ID).orEmpty()
    private val replay: Boolean get() = requireArguments().getBoolean(ARG_REPLAY)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, R.style.Theme_CornGuard)
        isCancelable = false
        index = savedInstanceState?.getInt(STATE_INDEX) ?: 0
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        (super.onCreateDialog(savedInstanceState) as ComponentDialog).apply {
            // System Back steps back through the slides; on the first one it leaves the guide.
            onBackPressedDispatcher.addCallback(this@OnboardingDialogFragment) {
                if (index > 0) show(index - 1) else finish()
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogOnboardingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (replay) binding.onboardingSkip.setText(R.string.onboarding_close)
        binding.onboardingSkip.setOnClickListener { finish() }
        binding.onboardingBack.setOnClickListener { if (index > 0) show(index - 1) }
        binding.onboardingNext.setOnClickListener { if (index < SLIDES.lastIndex) show(index + 1) else finish() }
        show(index, animate = false)
    }

    private fun show(newIndex: Int, animate: Boolean = true) {
        index = newIndex
        val slide = SLIDES[index]
        val apply = {
            binding.onboardingImage.setImageResource(slide.image)
            binding.onboardingImage.contentDescription = getString(slide.title)
            binding.onboardingTitle.setText(slide.title)
            binding.onboardingBody.setText(slide.body)
        }
        if (animate) {
            binding.onboardingSlide.animate().alpha(0f).setDuration(110).withEndAction {
                if (_binding == null) return@withEndAction
                apply()
                binding.onboardingSlide.animate().alpha(1f).setDuration(170).start()
            }.start()
        } else {
            apply()
        }

        // "Outbreak Map · 2 of 6": position within the chapter.
        val inChapter = SLIDES.filter { it.chapter == slide.chapter }
        val posInChapter = SLIDES.take(index + 1).count { it.chapter == slide.chapter }
        binding.onboardingChapter.text =
            getString(R.string.onboarding_chapter_progress, getString(slide.chapter), posInChapter, inChapter.size)

        binding.onboardingProgress.setProgressCompat((index + 1) * 100 / SLIDES.size, animate)
        binding.onboardingProgress.contentDescription = getString(R.string.onboarding_step, index + 1, SLIDES.size)

        val last = index == SLIDES.lastIndex
        binding.onboardingBack.visibility = if (index == 0) View.INVISIBLE else View.VISIBLE
        binding.onboardingSkip.visibility = if (last && !replay) View.INVISIBLE else View.VISIBLE
        binding.onboardingNext.setText(
            when {
                !last -> R.string.onboarding_next
                replay -> R.string.onboarding_close
                else -> R.string.onboarding_get_started
            }
        )
    }

    /** Finished, skipped, or backed out of the first slide. First-run: never show it again. */
    private fun finish() {
        if (!replay) {
            markDoneLocally(requireContext(), userId)
            // Not tied to this dialog's lifecycle, so the call survives it closing.
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                runCatchingCancellable { ServiceLocator.userFarmRepository.completeOnboarding() }
            }
        }
        dismissAllowingStateLoss()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_INDEX, index)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "OnboardingDialog"
        private const val ARG_USER_ID = "userId"
        private const val ARG_REPLAY = "replay"
        private const val STATE_INDEX = "index"
        private const val PREFS = "onboarding"

        private val SLIDES = listOf(
            Slide(R.string.onboarding_ch_welcome, R.drawable.tutorial_01_home, R.string.onboarding_01_title, R.string.onboarding_01_body),
            Slide(R.string.onboarding_ch_welcome, R.drawable.tutorial_02_home_more, R.string.onboarding_02_title, R.string.onboarding_02_body),
            Slide(R.string.onboarding_ch_scan, R.drawable.tutorial_03_scan, R.string.onboarding_03_title, R.string.onboarding_03_body),
            Slide(R.string.onboarding_ch_scan, R.drawable.tutorial_04_result, R.string.onboarding_04_title, R.string.onboarding_04_body),
            Slide(R.string.onboarding_ch_scan, R.drawable.tutorial_05_result_action, R.string.onboarding_05_title, R.string.onboarding_05_body),
            Slide(R.string.onboarding_ch_scan, R.drawable.tutorial_06_details, R.string.onboarding_06_title, R.string.onboarding_06_body),
            Slide(R.string.onboarding_ch_scan, R.drawable.tutorial_07_details_more, R.string.onboarding_07_title, R.string.onboarding_07_body),
            Slide(R.string.onboarding_ch_scan, R.drawable.tutorial_08_share, R.string.onboarding_08_title, R.string.onboarding_08_body),
            Slide(R.string.onboarding_ch_history, R.drawable.tutorial_09_history, R.string.onboarding_09_title, R.string.onboarding_09_body),
            Slide(R.string.onboarding_ch_community, R.drawable.tutorial_10_feed, R.string.onboarding_10_title, R.string.onboarding_10_body),
            Slide(R.string.onboarding_ch_community, R.drawable.tutorial_11_create_post, R.string.onboarding_11_title, R.string.onboarding_11_body),
            Slide(R.string.onboarding_ch_community, R.drawable.tutorial_12_comments, R.string.onboarding_12_title, R.string.onboarding_12_body),
            Slide(R.string.onboarding_ch_community, R.drawable.tutorial_13_post_menu, R.string.onboarding_13_title, R.string.onboarding_13_body),
            Slide(R.string.onboarding_ch_community, R.drawable.tutorial_14_report, R.string.onboarding_14_title, R.string.onboarding_14_body),
            Slide(R.string.onboarding_ch_map, R.drawable.tutorial_15_map, R.string.onboarding_15_title, R.string.onboarding_15_body),
            Slide(R.string.onboarding_ch_map, R.drawable.tutorial_16_legend, R.string.onboarding_16_title, R.string.onboarding_16_body),
            Slide(R.string.onboarding_ch_map, R.drawable.tutorial_17_popup, R.string.onboarding_17_title, R.string.onboarding_17_body),
            Slide(R.string.onboarding_ch_map, R.drawable.tutorial_18_search, R.string.onboarding_18_title, R.string.onboarding_18_body),
            Slide(R.string.onboarding_ch_map, R.drawable.tutorial_19_filter, R.string.onboarding_19_title, R.string.onboarding_19_body),
            Slide(R.string.onboarding_ch_map, R.drawable.tutorial_20_nearby, R.string.onboarding_20_title, R.string.onboarding_20_body),
            Slide(R.string.onboarding_ch_alerts, R.drawable.tutorial_21_alerts, R.string.onboarding_21_title, R.string.onboarding_21_body),
            Slide(R.string.onboarding_ch_settings, R.drawable.tutorial_22_settings, R.string.onboarding_22_title, R.string.onboarding_22_body)
        )

        /** First-run (default) or [replay] from Settings, which leaves the account untouched. */
        fun newInstance(userId: String, replay: Boolean = false) = OnboardingDialogFragment().apply {
            arguments = bundleOf(ARG_USER_ID to userId, ARG_REPLAY to replay)
        }

        /**
         * A "Fresh" account that hasn't finished the guide on this phone. The local flag covers the
         * gap until the server hears `completeOnboarding` (e.g. it was finished offline).
         */
        fun shouldShow(context: Context, userId: String, isFresh: Boolean) =
            isFresh && !prefs(context).getBoolean(key(userId), false)

        private fun markDoneLocally(context: Context, userId: String) =
            prefs(context).edit { putBoolean(key(userId), true) }

        private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        private fun key(userId: String) = "done_$userId"
    }
}
