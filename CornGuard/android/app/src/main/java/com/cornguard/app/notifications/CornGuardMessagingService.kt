package com.cornguard.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.cornguard.app.MainActivity
import com.cornguard.app.R
import com.cornguard.app.di.ServiceLocator
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receiving side of the notification pipeline whose sending side is the Convex action
 * backend/convex/push.ts (FCM HTTP v1). Registered in AndroidManifest.xml with the
 * com.google.firebase.MESSAGING_EVENT intent filter.
 *
 * Accepts either a `notification` payload (title/body) or a plain `data` payload with `title` and
 * `body` (or `message`) keys; the backend sends data-only messages. Tapping one marks its inbox
 * entry (the Dashboard bell) read and opens the Outbreak Map or the replied-to post.
 */
class CornGuardMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Runs in a background service: if the online backend can't start (e.g. not configured),
        // skip registration rather than crash the app.
        val uid = runCatching { ServiceLocator.authRepository.getCurrentUser()?.uid }.getOrNull() ?: return
        // No Fragment lifecycle to scope this to — the service's own process-lifetime scope is
        // the right owner here, same reasoning as ServiceLocator's own appScope.
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { ServiceLocator.gisRepository.registerDeviceToken(uid, ServiceLocator.deviceId, token) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title ?: message.data["title"] ?: getString(R.string.app_name)
        val body = message.notification?.body ?: message.data["body"] ?: message.data["message"] ?: return
        val type = message.data["type"]
        showNotification(
            title,
            body,
            level = when (type) {
                TYPE_OUTBREAK -> Level.SEVERE
                TYPE_AWARENESS -> Level.MODERATE
                else -> Level.NONE
            },
            notificationId = message.data["notification_id"]?.takeIf { it.isNotBlank() },
            relatedPostId = message.data["related_post_id"]?.takeIf { it.isNotBlank() }
        )
    }

    /** Disease-alert level of an incoming push (backend/convex/outbreakAlerts.ts). */
    private enum class Level { NONE, MODERATE, SEVERE }

    /**
     * Outbreak warnings (Severe) use their own high-importance channel, as caps 3 did, so they pop up;
     * awareness notices (Moderate) use a default-importance channel of their own so farmers can mute
     * them separately. Tapping either opens the Outbreak Map.
     */
    private fun showNotification(
        title: String,
        body: String,
        level: Level,
        notificationId: String?,
        relatedPostId: String?
    ) {
        ensureChannels()

        val isDiseaseAlert = level != Level.NONE
        val notifyId = System.currentTimeMillis().toInt()
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_MAP, isDiseaseAlert)
            .putExtra(MainActivity.EXTRA_NOTIFICATION_ID, notificationId)
            .putExtra(MainActivity.EXTRA_POST_ID, relatedPostId)
        // One request code per notification, so stacked notifications keep their own extras.
        val contentIntent = PendingIntent.getActivity(
            this,
            notifyId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val (channel, color) = when (level) {
            Level.SEVERE -> OUTBREAK_CHANNEL_ID to R.color.cg_danger
            Level.MODERATE -> AWARENESS_CHANNEL_ID to R.color.cg_accent_amber
            Level.NONE -> CHANNEL_ID to R.color.cg_primary
        }
        val notification = NotificationCompat.Builder(this, channel)
            // Status-bar icons must be a single-colour silhouette; the launcher icon shows as a grey dot.
            .setSmallIcon(R.drawable.ic_leaf)
            .setColor(ContextCompat.getColor(this, color))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(if (level == Level.SEVERE) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        getSystemService<NotificationManager>()?.notify(notifyId, notification)
    }

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService<NotificationManager>() ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_alerts_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = getString(R.string.notification_channel_alerts_description) }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                OUTBREAK_CHANNEL_ID,
                getString(R.string.notification_channel_outbreak_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = getString(R.string.notification_channel_outbreak_description) }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                AWARENESS_CHANNEL_ID,
                getString(R.string.notification_channel_awareness_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = getString(R.string.notification_channel_awareness_description) }
        )
    }

    companion object {
        private const val CHANNEL_ID = "cornguard_community_alerts"
        private const val OUTBREAK_CHANNEL_ID = "cornguard_outbreak_alerts"
        private const val AWARENESS_CHANNEL_ID = "cornguard_disease_awareness"
        private const val TYPE_OUTBREAK = "outbreak_alert"
        private const val TYPE_AWARENESS = "disease_awareness"
    }
}
