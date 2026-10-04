package com.cornguard.app.ui.common

import android.content.Context
import com.cornguard.app.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Time labels used across the design: compact relative times and full date-times. */
object TimeFormat {

    /** "now", "5m", "3h", "2d" — as on caps 3 feed items and comments. */
    fun relative(context: Context, timestampMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        val minutes = (nowMs - timestampMs).coerceAtLeast(0) / 60_000
        return when {
            minutes < 1 -> context.getString(R.string.time_now)
            minutes < 60 -> "${minutes}m"
            minutes < 60 * 24 -> "${minutes / 60}h"
            else -> "${minutes / (60 * 24)}d"
        }
    }

    /** e.g. "Sep 27, 2026 8:36 PM" (History cards, result sheet). */
    fun dateTime(timestampMs: Long): String =
        SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(Date(timestampMs))

    /** Rounds a 0..1 confidence to one decimal percentage, e.g. 0.674 -> "67.4". */
    fun percent(confidence: Float): String = String.format(Locale.US, "%.1f", confidence * 100)
}
