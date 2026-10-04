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
 * `body` (or `message`) keys; the backend sends data-only messages. Deep-linking to a specific
 * post/area from the notification is not implemented — tapping it just opens the app.
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
        showNotification(title, body, isOutbreak = message.data["type"] == TYPE_OUTBREAK)
    }

    /**
     * Outbreak alerts (backend/convex/outbreakAlerts.ts) use their own high-importance channel, as
     * caps 3 did, so they pop up; tapping one opens the Outbreak Map.
     */
    private fun showNotification(title: String, body: String, isOutbreak: Boolean) {
        ensureChannels()

        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_MAP, isOutbreak)
        val contentIntent = PendingIntent.getActivity(
            this,
            if (isOutbreak) 1 else 0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, if (isOutbreak) OUTBREAK_CHANNEL_ID else CHANNEL_ID)
            // Status-bar icons must be a single-colour silhouette; the launcher icon shows as a grey dot.
            .setSmallIcon(R.drawable.ic_leaf)
            .setColor(ContextCompat.getColor(this, if (isOutbreak) R.color.cg_danger else R.color.cg_primary))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(if (isOutbreak) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        getSystemService<NotificationManager>()?.notify(System.currentTimeMillis().toInt(), notification)
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
    }

    companion object {
        private const val CHANNEL_ID = "cornguard_community_alerts"
        private const val OUTBREAK_CHANNEL_ID = "cornguard_outbreak_alerts"
        private const val TYPE_OUTBREAK = "outbreak_alert"
    }
}
