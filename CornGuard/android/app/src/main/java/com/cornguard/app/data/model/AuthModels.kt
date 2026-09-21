package com.cornguard.app.data.model

/**
 * Mirrors firebase/repositories/auth-repository-interface.md's value types exactly.
 */
data class AuthUser(
    val uid: String,
    val email: String?,
    val phoneNumber: String?
)

data class AuthResult(
    val user: AuthUser,
    val isNewUser: Boolean
)
