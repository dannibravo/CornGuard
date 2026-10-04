package com.cornguard.app.ui.map

import android.content.res.ColorStateList
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.cornguard.app.R
import com.cornguard.app.databinding.ItemGisEntryBinding
import com.cornguard.app.ui.common.DiseaseStyle
import com.cornguard.app.ui.common.TimeFormat

/** Shared row binding for the Map screen's two lists (verified outbreaks, nearby reports). */
internal object GisEntryBinder {

    fun bind(
        binding: ItemGisEntryBinding,
        diseaseCode: String,
        barangay: String,
        municipality: String,
        timestamp: Long,
        verificationStatus: String,
        isLast: Boolean
    ) {
        val context = binding.root.context
        DiseaseStyle.applyBadge(
            binding.gisEntryDisease,
            diseaseCode,
            DiseaseStyle.label(diseaseCode).ifBlank { context.getString(R.string.post_disease_none) }
        )
        binding.gisEntryMeta.text = listOf(
            listOf(barangay, municipality).filter { it.isNotBlank() }.joinToString(", "),
            TimeFormat.relative(context, timestamp)
        ).filter { it.isNotBlank() }.joinToString(" · ")

        val verified = verificationStatus == "verified"
        val color = ContextCompat.getColor(context, if (verified) R.color.cg_primary else R.color.cg_text_secondary)
        binding.gisEntryStatus.setText(if (verified) R.string.map_verified_badge else R.string.map_unverified_badge)
        binding.gisEntryStatus.setTextColor(color)
        binding.gisEntryStatus.setBackgroundResource(R.drawable.bg_pill)
        binding.gisEntryStatus.backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 0x20))
        binding.gisEntryDivider.visibility = if (isLast) View.GONE else View.VISIBLE
    }
}
