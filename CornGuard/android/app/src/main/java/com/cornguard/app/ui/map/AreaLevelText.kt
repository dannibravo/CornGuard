package com.cornguard.app.ui.map

import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.cornguard.app.R
import com.cornguard.app.ui.common.DiseaseStyle

/**
 * How an area's outbreak level reads on screen: the level badge and the one-sentence explanation.
 * Shared by the map's bottom sheet and Home's "Your barangay" card so both always say the same
 * thing. The level itself comes from [MapAreaSummary].
 */
object AreaLevelText {

    fun applyBadge(badge: TextView, level: MapAreaSummary.Level) {
        val (labelRes, bgRes, textRes) = when (level) {
            MapAreaSummary.Level.SEVERE -> Triple(R.string.map_level_severe, R.color.level_severe_bg, R.color.level_severe_text)
            MapAreaSummary.Level.MODERATE ->
                Triple(R.string.map_level_moderate, R.color.level_moderate_bg, R.color.level_moderate_text)
            MapAreaSummary.Level.MILD -> Triple(R.string.map_level_mild, R.color.level_mild_bg, R.color.level_mild_text)
            MapAreaSummary.Level.COLLECTING ->
                Triple(R.string.map_level_collecting, R.color.level_collecting_bg, R.color.level_collecting_text)
            MapAreaSummary.Level.NONE -> Triple(R.string.map_level_none, R.color.cg_button_secondary, R.color.cg_text_secondary)
        }
        val context = badge.context
        badge.visibility = View.VISIBLE
        badge.setText(labelRes)
        badge.setTextColor(ContextCompat.getColor(context, textRes))
        badge.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, bgRes))
        badge.contentDescription = context.getString(R.string.map_level_badge_a11y, context.getString(labelRes))
    }

    /** @param diseaseFilter null = all diseases (Home always passes null). */
    fun explanation(context: Context, summary: MapAreaSummary.Summary, diseaseFilter: String?, now: Long = System.currentTimeMillis()): String {
        val lead = summary.lead ?: return diseaseFilter
            ?.let { context.getString(R.string.map_explain_none_filtered, DiseaseStyle.label(it)) }
            ?: context.getString(R.string.map_explain_none)
        val farms = context.resources.getQuantityString(R.plurals.map_farms, lead.distinctFarms, lead.distinctFarms)
        val disease = DiseaseStyle.label(lead.diseaseCode)
        val parts = mutableListOf<String>()
        parts += when (summary.level) {
            MapAreaSummary.Level.COLLECTING -> context.getString(
                R.string.map_explain_collecting,
                MapAreaSummary.warmupWeeks(lead, now), MapAreaSummary.WARMUP_WEEKS,
                lead.historyScans.coerceAtMost(MapAreaSummary.WARMUP_SCANS), MapAreaSummary.WARMUP_SCANS,
                farms, disease
            )
            MapAreaSummary.Level.SEVERE -> context.getString(R.string.map_explain_severe, farms, disease)
            MapAreaSummary.Level.MODERATE -> context.getString(R.string.map_explain_moderate, farms, disease)
            else -> context.getString(R.string.map_explain_mild, farms, disease)
        }
        if (summary.level != MapAreaSummary.Level.COLLECTING) {
            lead.baselineFarms?.let { parts += context.getString(R.string.map_explain_usual, String.format("%.1f", it)) }
        }
        if (summary.others.isNotEmpty()) {
            parts += context.getString(R.string.map_explain_also, summary.others.joinToString(", ") { other ->
                val level = when {
                    other.monitoringStatus == "collecting" -> R.string.map_level_collecting
                    other.severityTier == "severe" -> R.string.map_level_severe
                    other.severityTier == "moderate" -> R.string.map_level_moderate
                    else -> R.string.map_level_mild
                }
                context.getString(R.string.map_explain_other_item, DiseaseStyle.label(other.diseaseCode), context.getString(level))
            })
        }
        return parts.joinToString(" ")
    }
}
