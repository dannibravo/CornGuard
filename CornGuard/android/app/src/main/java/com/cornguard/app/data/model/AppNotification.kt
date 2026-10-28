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
        const val TYPE_OUTBREAK_ALERT = "outbreak_alert"
        const val TYPE_COMMUNITY_REPLY = "community_reply"
    }
}
