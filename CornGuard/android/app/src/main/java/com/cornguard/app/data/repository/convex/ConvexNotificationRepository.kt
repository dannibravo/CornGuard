package com.cornguard.app.data.repository.convex

import com.cornguard.app.data.model.AppNotification
import com.cornguard.app.data.remote.convex.CornGuardConvexClient
import com.cornguard.app.data.remote.convex.InboxNotificationDto
import com.cornguard.app.data.remote.convex.convexArgs
import com.cornguard.app.data.remote.convex.execute
import com.cornguard.app.data.remote.convex.observe
import com.cornguard.app.data.repository.NotificationRepository
import dev.convex.android.AuthState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull

/** Inbox backed by backend/convex/notifications.ts; both queries are live subscriptions. */
class ConvexNotificationRepository(
    private val client: CornGuardConvexClient
) : NotificationRepository {

    override fun observeNotifications(): Flow<List<AppNotification>> =
        client.observe<List<InboxNotificationDto>>("notifications:mine", emptyMap(), fallback = emptyList())
            .map { rows -> rows.map { it.toModel() } }

    override fun observeUnreadCount(): Flow<Int> =
        client.observe<Double>("notifications:unreadCount", emptyMap(), fallback = 0.0)
            .map { it.toInt() }

    override suspend fun markRead(notificationId: String) {
        awaitSession()
        client.execute("notifications:markRead", convexArgs("notificationId" to notificationId))
    }

    override suspend fun markAllRead() {
        awaitSession()
        client.execute("notifications:markAllRead")
    }

    /**
     * Tapping a push can cold-start the app, when the cached user is shown as signed in but
     * ConvexAuthRepository.restoreSession hasn't re-authenticated the client yet; a mutation sent
     * then would be rejected as signed out. Queries don't need this: they re-run once auth lands.
     */
    private suspend fun awaitSession() {
        withTimeoutOrNull(SESSION_WAIT_MS) { client.authState.first { it is AuthState.Authenticated<*> } }
    }

    private fun InboxNotificationDto.toModel() = AppNotification(
        notificationId = notificationId,
        type = type,
        title = title,
        message = message,
        relatedPostId = relatedPostId,
        createdAt = createdAt.toLong(),
        read = read
    )

    private companion object {
        const val SESSION_WAIT_MS = 15_000L
    }
}
