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
    /** "fresh" until the first-sign-in tutorial is finished or skipped; older accounts are "done". */
    val onboardingStatus: String = "done",
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
    val isActiveOutbreak: Boolean,
    /** "collecting", "provisional" or "established"; absent from older backends. */
    val monitoringStatus: String = "collecting",
    val baselineFarms: Double? = null,
    val lastWeekFarms: Double? = null,
    val dataWeeks: Double = 0.0,
    val historyScans: Double = 0.0,
    val monitoringSince: Double? = null
)

@Serializable
data class MapReportDto(
    /** "active" (last 14 days, counted in the level) or "recent" (14-28 days, drawn faded). */
    val stage: String = "active",
    val recordId: String,
    val diseaseCode: String,
    val confidence: Double,
    val capturedAt: Double,
    val latitude: Double,
    val longitude: Double,
    val barangay: String,
    val municipality: String
    // No reporter identity field: the backend's general mapReports query stopped sending one
    // (privacy fix, see backend/convex/diagnosisRecords.ts).
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

/**
 * A row of `diseaseReferences:list` — one admin-managed treatment guide. The app's bundled
 * snapshot (assets/treatment/guides-snapshot.json) is a list of the same shape.
 */
@Serializable
data class TreatmentGuideDto(
    val diseaseCode: String,
    val displayName: String,
    val symptoms: String = "",
    val causes: String = "",
    val treatmentSteps: String = "",
    val duration: String = "",
    val preventionSteps: String = "",
    val sourceReference: String = "",
    val contentVersion: String = "",
    val updatedAt: Double = 0.0,
    val reviewStatus: String = "unreviewed",
    val reviewedBy: String = "",
    val reviewedAt: Double = 0.0
)

/** A row of `notifications:mine` — the farmer's own inbox, unlike the admin [NotificationDto]. */
@Serializable
data class InboxNotificationDto(
    val notificationId: String,
    val type: String,
    val title: String,
    val message: String,
    val relatedPostId: String? = null,
    val diseaseCode: String? = null,
    val createdAt: Double,
    val read: Boolean
)
