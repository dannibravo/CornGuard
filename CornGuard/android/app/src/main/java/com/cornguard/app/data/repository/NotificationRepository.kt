package com.cornguard.app.data.repository

import com.cornguard.app.data.model.AppNotification
import kotlinx.coroutines.flow.Flow

/**
 * The signed-in farmer's notification inbox: every outbreak alert and community reply they were
 * pushed, kept so it can be read again after the system notification is dismissed. Online-only;
 * signed out, both flows emit empty values.
 */
interface NotificationRepository {

    /** Newest first, capped server-side. */
    fun observeNotifications(): Flow<List<AppNotification>>

    fun observeUnreadCount(): Flow<Int>

    /** Idempotent; unknown or foreign ids are ignored server-side. */
    suspend fun markRead(notificationId: String)

    suspend fun markAllRead()
}
