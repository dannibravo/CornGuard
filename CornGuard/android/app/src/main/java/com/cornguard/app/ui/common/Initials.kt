package com.cornguard.app.ui.common

/** "Juan dela Cruz" → "JC": first letters of the first and last words (one word → one letter). */
object Initials {
    fun of(name: String?): String {
        val words = name.orEmpty().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return ""
        val first = words.first().first()
        val last = if (words.size > 1) words.last().first().toString() else ""
        return (first + last).uppercase()
    }
}
