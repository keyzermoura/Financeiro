package br.com.carteirainvest.data.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import br.com.carteirainvest.MainActivity
import br.com.carteirainvest.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Notifies only after repeated background sync failures. Routine success is silent and
 * cancels any previous failure notification.
 *
 * Android 13+ requires POST_NOTIFICATIONS. When the permission is missing the worker still
 * retries according to WorkManager's policy; it just cannot surface a notification.
 */
@Singleton
class SyncFailureNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : SyncFailureReporter {
    private val manager = NotificationManagerCompat.from(context)

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_SYNC_FAILURES,
                context.getString(R.string.notification_channel_sync),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.notification_channel_sync_description)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    @Suppress("MissingPermission")
    override fun notifyFailure(label: String, message: String) {
        if (!canNotify()) return
        ensureChannel()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_SYNC_FAILURES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Falha ao sincronizar $label")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()

        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    @Suppress("MissingPermission")
    override fun clear() {
        runCatching { manager.cancel(NOTIFICATION_ID) }
    }

    private fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }
        return manager.areNotificationsEnabled()
    }

    companion object {
        const val CHANNEL_SYNC_FAILURES = "quote_sync_failures"
        const val NOTIFICATION_ID = 7_301
    }
}
