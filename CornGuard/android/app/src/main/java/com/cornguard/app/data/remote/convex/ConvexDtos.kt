package com.cornguard.app.data.remote.convex

import kotlinx.serialization.Serializable

/*
 * Shapes returned by the Convex queries in backend/convex. Every number is a float64 on the
 * server, so it's a Double here and converted at the repository boundary. Nullable fields default
 * to null so an absent value decodes cleanly.
 */

@Serializable
data class UserDto(
    val userId: String,
    val email: String? = null,
    val displayName: String? = null,
    val mobileNumber: String? = null,
    val barangay: String = "",
    val municipality: String = "",
    val province: String = "",
    val role: String = "farmer",
    val accountStatus: String = "active",
    val farmIds: List<String> = emptyList()
)

@Serializable
data class FarmDto(
    val farmId: String,
    val ownerUserId: String,
    val name: String,
    val barangay: String,
    val municipality: String,
    val province: String,
    val latitude: Double? = null,
    val longitude: Double? = null
)

@Serializable
data class DiagnosisRecordDto(
    val recordId: String,
    val userId: String,
    val farmId: String? = null,
    val diseaseCode: String,
    val confidence: Double,
    val imageUrl: String? = null,
    val capturedAt: Double,
    val barangay: String,
    val municipality: String,
    val province: String,
    val modelVersion: String,
    val verificationStatus: String,
    val source: String
)

@Serializable
data class PostDto(
    val postId: String,
    val userId: String,
    val linkedDiagnosisRecordId: String? = null,
    val title: String,
    val body: String,
    val diseaseTag: String,
    val imageUrl: String? = null,
    val barangay: String,
    val municipality: String,
    val province: String,
    val verificationStatus: String,
    val moderationStatus: String,
    val upvoteCount: Double,
    val createdAt: Double,
    val authorName: String = "",
    val commentCount: Double = 0.0,
    val likedByMe: Boolean = false,
    val recentComments: List<CommentDto> = emptyList()
)

@Serializable
data class CommentDto(
    val commentId: String,
    val postId: String,
    val userId: String,
    val authorName: String = "",
    val body: String,
    val moderationStatus: String,
    val createdAt: Double
)

@Serializable
data class OccurrenceDto(
    val occurrenceId: String,
    val diseaseCode: String,
    val barangay: String,
    val municipality: String,
    val occurredAt: Double,
    val verificationStatus: String
)

@Serializable
data class NearbyReportDto(
    val postId: String,
    val diseaseTag: String,
    val barangay: String,
    val municipality: String,
    val createdAt: Double,
    val verificationStatus: String
)

@Serializable
data class BarangayStatDto(
    val barangay: String,
    val municipality: String,
    val diseaseCode: String,
    val severityTier: String,
    val weightedScore: Double,
    val distinctFarms: Double,
    val rawReportCount: Double,
    val isActiveOutbreak: Boolean
)

@Serializable
data class MapReportDto(
    val recordId: String,
    val diseaseCode: String,
    val confidence: Double,
    val capturedAt: Double,
    val latitude: Double,
    val longitude: Double,
    val barangay: String,
    val municipality: String
)

@Serializable
data class HeatmapDto(
    val areaLabel: String,
    val count: Double,
    val diseaseBreakdown: Map<String, Double>
)

@Serializable
data class NotificationDto(
    val notificationId: String,
    val type: String,
    val recipientUserId: String? = null,
    val areaScope: String? = null,
    val title: String,
    val deliveryStatus: String,
    val createdAt: Double
)
