package com.cornguard.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
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
 * Receiving side of the notification pipeline whose sending side is
 * firebase/functions/index.mjs's onNotificationCreate (per fcm-plan.md). Registered in
 * AndroidManifest.xml with the com.google.firebase.MESSAGING_EVENT intent filter.
 *
 * Accepts either a `notification` payload (title/body) or a plain `data` payload with `title`/
 * `body` keys, since this project's Cloud Function payload shape isn't pinned down to one of
 * those here — whichever the backend actually sends, this displays it. Deep-linking to a specific
 * post/area from the notification is not implemented — tapping it just opens the app.
 */
class CornGuardMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = ServiceLocator.authRepository.getCurrentUser()?.uid ?: return
        // No Fragment lifecycle to scope this to — the service's own process-lifetime scope is
        // the right owner here, same reasoning as ServiceLocator's own appScope.
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { ServiceLocator.gisRepository.registerDeviceToken(uid, ServiceLocator.deviceId, token) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title ?: message.data["title"] ?: getString(R.string.app_name)
        val body = message.notification?.body ?: message.data["body"] ?: return
        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String) {
        ensureChannel()

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        getSystemService<NotificationManager>()?.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService<NotificationManager>() ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_alerts_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.notification_channel_alerts_description)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "cornguard_community_alerts"
    }
}
