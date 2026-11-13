package com.cornguard.app.data.model

/** One entry in the farmer's notification inbox (the Dashboard bell). */
data class AppNotification(
    val notificationId: String,
    val type: String,
    val title: String,
    val message: String,
    val relatedPostId: String?,
    val createdAt: Long,
    val read: Boolean
) {
    companion object {
        /** Severe level reached nearby (outbreak warning). */
        const val TYPE_OUTBREAK_ALERT = "outbreak_alert"
        /** Moderate level reached nearby (awareness notice). */
        const val TYPE_DISEASE_AWARENESS = "disease_awareness"
        const val TYPE_COMMUNITY_REPLY = "community_reply"
    }
}
