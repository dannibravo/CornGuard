package com.cornguard.app.data.repository.firebase

import com.cornguard.app.data.model.Farm
import com.cornguard.app.data.model.UserProfile
import com.cornguard.app.data.repository.UserFarmRepository
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

private const val USERS_COLLECTION = "users"
private const val FARMS_COLLECTION = "farms"

/**
 * Firestore-backed implementation. Field names match firebase/schema/logical-schema.md exactly
 * (snake_case, as Firestore documents store them) — do not rename either side without updating
 * the other (claude/04_DEVELOPMENT_RULES.md #3, Contract-First Integration).
 */
class FirebaseUserFarmRepository(
    private val db: FirebaseFirestore
) : UserFarmRepository {

    override suspend fun createUserProfile(
        uid: String,
        displayName: String,
        barangay: String,
        municipality: String,
        province: String
    ) {
        val data = mapOf(
            "display_name" to displayName,
            // Fixed at creation — security/firestore.rules rejects any other role/account_status.
            "role" to "farmer",
            "account_status" to "active",
            "barangay" to barangay,
            "municipality" to municipality,
            "province" to province,
            "farm_ids" to emptyList<String>()
        )
        db.collection(USERS_COLLECTION).document(uid).set(data).await()
    }

    override suspend fun getUserProfile(uid: String): UserProfile? {
        val snapshot = db.collection(USERS_COLLECTION).document(uid).get().await()
        return snapshot.toUserProfile(uid)
    }

    override fun observeUserProfile(uid: String): Flow<UserProfile?> = callbackFlow {
        val registration = db.collection(USERS_COLLECTION).document(uid)
            .addSnapshotListener { snapshot, _ -> trySend(snapshot?.toUserProfile(uid)) }
        awaitClose { registration.remove() }
    }

    override suspend fun updateUserProfile(uid: String, fields: Map<String, Any>) {
        // security/firestore.rules rejects role/account_status/farm_ids on this path regardless
        // of what's passed — callers should only ever pass display_name/barangay/municipality/
        // province, but this repository does not re-validate that client-side; the rules are the
        // actual enforcement point.
        db.collection(USERS_COLLECTION).document(uid).update(fields).await()
    }

    override suspend fun createFarm(
        ownerUserId: String,
        farmNameOrLabel: String,
        barangay: String,
        municipality: String,
        province: String,
        latitude: Double?,
        longitude: Double?
    ): String {
        val data = buildMap {
            put("owner_user_id", ownerUserId)
            put("farm_name_or_label", farmNameOrLabel)
            put("barangay", barangay)
            put("municipality", municipality)
            put("province", province)
            latitude?.let { put("latitude", it) }
            longitude?.let { put("longitude", it) }
        }
        val ref = db.collection(FARMS_COLLECTION).add(data).await()
        return ref.id
    }

    override suspend fun getFarmsForUser(uid: String): List<Farm> {
        val snapshot = db.collection(FARMS_COLLECTION)
            .whereEqualTo("owner_user_id", uid)
            .get()
            .await()
        return snapshot.documents.mapNotNull { it.toFarm() }
    }

    override fun observeFarmsForUser(uid: String): Flow<List<Farm>> = callbackFlow {
        val registration = db.collection(FARMS_COLLECTION)
            .whereEqualTo("owner_user_id", uid)
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.documents?.mapNotNull { it.toFarm() } ?: emptyList())
            }
        awaitClose { registration.remove() }
    }

    private fun DocumentSnapshot.toUserProfile(uid: String): UserProfile? {
        if (!exists()) return null
        val name = getString("display_name") ?: return null
        @Suppress("UNCHECKED_CAST")
        val farmIds = (get("farm_ids") as? List<String>) ?: emptyList()
        return UserProfile(
            userId = uid,
            displayName = name,
            email = getString("email"),
            mobileNumber = getString("mobile_number"),
            barangay = getString("barangay") ?: "",
            municipality = getString("municipality") ?: "",
            province = getString("province") ?: "",
            farmIds = farmIds
        )
    }

    private fun DocumentSnapshot.toFarm(): Farm? {
        if (!exists()) return null
        val ownerId = getString("owner_user_id") ?: return null
        return Farm(
            farmId = id,
            ownerUserId = ownerId,
            farmNameOrLabel = getString("farm_name_or_label") ?: "",
            barangay = getString("barangay") ?: "",
            municipality = getString("municipality") ?: "",
            province = getString("province") ?: "",
            latitude = getDouble("latitude"),
            longitude = getDouble("longitude")
        )
    }
}
