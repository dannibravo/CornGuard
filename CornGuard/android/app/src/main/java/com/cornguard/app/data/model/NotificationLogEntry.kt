package com.cornguard.app.data.model

/** Admin-facing view of notifications/{id}, per fcm-plan.md — for monitoring delivery, not sending. */
data class NotificationLogEntry(
    val notificationId: String,
    val type: String,
    val recipientUserId: String?,
    val areaScope: String?,
    val title: String,
    val deliveryStatus: String,
    val createdAt: Long
)
