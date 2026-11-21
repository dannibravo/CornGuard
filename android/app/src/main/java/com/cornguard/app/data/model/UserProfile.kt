package com.cornguard.app.data.model

/**
 * Mirrors firebase/schema/logical-schema.md's users/{userId} shape (field-for-field, in
 * camelCase) and firebase/repositories/user-farm-repository-interface.md's UserProfile value
 * type. `role`/`accountStatus` are intentionally absent here — they're fixed server/rule-side at
 * creation and are never client-writable after that (security/firestore.rules), so this app-side
 * model only carries the fields a screen actually needs to render or edit.
 */
data class UserProfile(
    val userId: String,
    val displayName: String,
    val email: String?,
    val mobileNumber: String?,
    val barangay: String,
    val municipality: String,
    val province: String,
    val farmIds: List<String>
)
