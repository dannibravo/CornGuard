package com.cornguard.app.data.model

/**
 * Mirrors firebase/schema/logical-schema.md's farms/{farmId} shape. Provisional per D-03
 * (claude/03_SOURCE_ALIGNMENT_AND_DECISION_GATES.md) — a user having more than one Farm is
 * structurally supported, not assumed away.
 */
data class Farm(
    val farmId: String,
    val ownerUserId: String,
    val farmNameOrLabel: String,
    val barangay: String,
    val municipality: String,
    val province: String,
    val latitude: Double?,
    val longitude: Double?
)
