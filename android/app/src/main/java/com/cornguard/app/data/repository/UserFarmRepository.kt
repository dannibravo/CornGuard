package com.cornguard.app.data.repository

import com.cornguard.app.data.model.Farm
import com.cornguard.app.data.model.UserProfile
import kotlinx.coroutines.flow.Flow

/**
 * Profile and farm data boundary. Mirrors
 * firebase/docs/repositories/user-farm-repository-interface.md — see that file for the full contract.
 *
 * Callable more than once per user; does not enforce a one-farm limit. This is now the confirmed
 * design per D-03 (resolved 2026-10-30, claude/03_SOURCE_ALIGNMENT_AND_DECISION_GATES.md): one
 * farmer may register and manage multiple farms.
 *
 * Online-only, same connectivity-handling expectation as AuthRepository.
 */
interface UserFarmRepository {

    /**
     * Called once, immediately after a successful AuthRepository.registerWithEmail — never
     * before. security/firestore.rules fixes role="farmer" and account_status="active" at
     * creation; this call must not (and cannot) set either.
     */
    suspend fun createUserProfile(
        uid: String,
        displayName: String,
        barangay: String,
        municipality: String,
        province: String
    )

    suspend fun getUserProfile(uid: String): UserProfile?
    fun observeUserProfile(uid: String): Flow<UserProfile?>

    /**
     * Only display_name/barangay/municipality/province may be present in [fields] — the rules
     * reject role/account_status/farm_ids here regardless of what the client sends.
     */
    suspend fun updateUserProfile(uid: String, fields: Map<String, Any>)

    /** Returns the new farm's id. */
    suspend fun createFarm(
        ownerUserId: String,
        farmNameOrLabel: String,
        barangay: String,
        municipality: String,
        province: String,
        latitude: Double?,
        longitude: Double?
    ): String

    suspend fun getFarmsForUser(uid: String): List<Farm>
    fun observeFarmsForUser(uid: String): Flow<List<Farm>>
}
