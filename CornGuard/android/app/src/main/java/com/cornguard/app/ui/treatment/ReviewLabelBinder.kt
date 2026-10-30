package com.cornguard.app.ui.treatment

import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.cornguard.app.R
import com.cornguard.app.data.local.db.entity.DiseaseReferenceEntity
import com.cornguard.app.model.ReviewStatus
import com.cornguard.app.model.reviewLabelFor
import com.cornguard.app.ui.common.TimeFormat

/**
 * Binds the small review label (`view_review_label.xml`) under a Treatment section. Hidden unless
 * [reviewLabelFor] says the content was actually reviewed — the label never shows by default.
 */
object ReviewLabelBinder {

    fun bind(label: TextView, reference: DiseaseReferenceEntity?) {
        val review = reference?.let(::reviewLabelFor)
        if (review == null) {
            label.visibility = View.GONE
            return
        }
        val context = label.context
        val date = TimeFormat.monthYear(review.reviewedAt)
        label.text = when (review.status) {
            ReviewStatus.EXPERT_VERIFIED -> context.getString(R.string.review_label_expert, review.reviewedBy, date)
            else -> context.getString(R.string.review_label_sources, date)
        }
        val size = context.resources.getDimensionPixelSize(R.dimen.review_label_icon_size)
        val icon = ContextCompat.getDrawable(context, R.drawable.ic_check_circle)?.mutate()?.apply {
            setTint(ContextCompat.getColor(context, R.color.cg_primary))
            setBounds(0, 0, size, size)
        }
        label.setCompoundDrawablesRelative(icon, null, null, null)
        label.visibility = View.VISIBLE
    }
}
