package com.cornguard.app.ui.common

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.cornguard.app.databinding.ViewOfflineStatusBannerBinding

/**
 * Reusable connectivity-state banner (claude/05_DEVELOPMENT_PLAN.md Sprint 0: "reusable UI
 * components"; claude/04_DEVELOPMENT_RULES.md #11: clear online/offline status). Hosted once in
 * MainActivity above the nav host so every screen shares the same indicator instead of each
 * screen re-implementing its own.
 */
class OfflineStatusBanner @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding =
        ViewOfflineStatusBannerBinding.inflate(android.view.LayoutInflater.from(context), this, true)

    init {
        visibility = GONE
    }

    fun setOnline(isOnline: Boolean) {
        visibility = if (isOnline) GONE else VISIBLE
    }
}
