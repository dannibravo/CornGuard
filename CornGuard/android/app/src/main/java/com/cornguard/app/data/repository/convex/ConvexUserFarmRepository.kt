package com.cornguard.app.data.repository.convex

import com.cornguard.app.data.model.Farm
import com.cornguard.app.data.model.UserProfile
import com.cornguard.app.data.remote.convex.CornGuardConvexClient
import com.cornguard.app.data.remote.convex.FarmDto
import com.cornguard.app.data.remote.convex.UserDto
import com.cornguard.app.data.remote.convex.convexArgs
import com.cornguard.app.data.remote.convex.execute
import com.cornguard.app.data.remote.convex.observe
import com.cornguard.app.data.remote.convex.queryOnce
import com.cornguard.app.data.repository.UserFarmRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Profiles and farms in Convex (`users`, `farms` tables). The backend always acts on the signed-in
 * user for writes, so the `uid`/`ownerUserId` parameters here only select what to read.
 */
class ConvexUserFarmRepository(
    private val client: CornGuardConvexClient
) : UserFarmRepository {

    override suspend fun createUserProfile(
        uid: String,
        displayName: String,
        barangay: String,
        municipality: String,
        province: String
    ) {
        client.execute(
            "users:createProfile",
            convexArgs(
                "displayName" to displayName,
                "barangay" to barangay,
                "municipality" to municipality,
                "province" to province
            )
        )
    }

    override suspend fun getUserProfile(uid: String): UserProfile? =
        client.queryOnce<UserDto?>("users:get", convexArgs("userId" to uid))?.toUserProfile()

    override fun observeUserProfile(uid: String): Flow<UserProfile?> =
        client.observe<UserDto?>("users:get", convexArgs("userId" to uid), fallback = null)
            .map { it?.toUserProfile() }

    /**
     * Accepts the former Firestore field names (`display_name`, `mobile_number`) as well as the
     * Convex ones. Only profile fields are editable; anything else is rejected.
     */
    override suspend fun updateUserProfile(uid: String, fields: Map<String, Any>) {
        val args = fields.entries.associate { (key, value) ->
            val field = PROFILE_FIELDS[key] ?: throw IllegalArgumentException("Field '$key' can't be edited")
            field to value
        }
        client.execute("users:update", convexArgs(*args.toList().toTypedArray()))
    }

    override suspend fun createFarm(
        ownerUserId: String,
        farmNameOrLabel: String,
        barangay: String,
        municipality: String,
        province: String,
        latitude: Double?,
        longitude: Double?
    ): String = client.mutation<String>(
        "users:createFarm",
        convexArgs(
            "name" to farmNameOrLabel,
            "barangay" to barangay,
            "municipality" to municipality,
            "province" to province,
            "latitude" to latitude,
            "longitude" to longitude
        )
    )

    override suspend fun getFarmsForUser(uid: String): List<Farm> =
        client.queryOnce<List<FarmDto>>("users:farmsForUser", convexArgs("userId" to uid)).map { it.toFarm() }

    override fun observeFarmsForUser(uid: String): Flow<List<Farm>> =
        client.observe<List<FarmDto>>("users:farmsForUser", convexArgs("userId" to uid), fallback = emptyList())
            .map { farms -> farms.map { it.toFarm() } }

    private fun UserDto.toUserProfile(): UserProfile? = displayName?.let { name ->
        UserProfile(
            userId = userId,
            displayName = name,
            email = email,
            mobileNumber = mobileNumber,
            barangay = barangay,
            municipality = municipality,
            province = province,
            farmIds = farmIds
        )
    }

    private fun FarmDto.toFarm() = Farm(
        farmId = farmId,
        ownerUserId = ownerUserId,
        farmNameOrLabel = name,
        barangay = barangay,
        municipality = municipality,
        province = province,
        latitude = latitude,
        longitude = longitude
    )

    private companion object {
        val PROFILE_FIELDS = mapOf(
            "display_name" to "displayName", "displayName" to "displayName",
            "mobile_number" to "mobileNumber", "mobileNumber" to "mobileNumber",
            "barangay" to "barangay", "municipality" to "municipality", "province" to "province"
        )
    }
}
