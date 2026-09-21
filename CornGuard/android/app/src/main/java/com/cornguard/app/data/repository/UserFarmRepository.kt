package com.cornguard.app.data.repository

import com.cornguard.app.data.model.Farm
import com.cornguard.app.data.model.UserProfile
import kotlinx.coroutines.flow.Flow

/**
 * Profile and farm data boundary. Mirrors
 * firebase/repositories/user-farm-repository-interface.md — see that file for the full contract.
 *
 * Callable more than once per user; does not enforce a one-farm limit (D-03 is not resolved —
 * claude/03_SOURCE_ALIGNMENT_AND_DECISION_GATES.md). If the team later decides "one farm per
 * farmer," that's a UI-layer restriction, not a change to this interface.
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
