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
import br.com.carteirainvest.core.util.BrFormat
import br.com.carteirainvest.domain.model.AlertRule
import br.com.carteirainvest.domain.model.Asset
import br.com.carteirainvest.domain.model.Quote
import br.com.carteirainvest.domain.repository.AlertNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidAlertNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : AlertNotifier {

    private val manager = NotificationManagerCompat.from(context)

    init {
        ensureChannels()
    }

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alerts = NotificationChannel(
                CHANNEL_ALERTS,
                context.getString(R.string.notification_channel_alerts),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.notification_channel_alerts_description)
                enableVibration(true)
            }
            val sync = NotificationChannel(
                CHANNEL_SYNC,
                context.getString(R.string.notification_channel_sync),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.notification_channel_sync_description)
            }
            manager.createNotificationChannel(alerts)
            manager.createNotificationChannel(sync)
        }
    }

    override fun notifyPriceChange(
        alert: AlertRule,
        asset: Asset,
        price: Quote,
        changePercent: BigDecimal,
    ): Boolean {
        val direction = if (changePercent.signum() >= 0) "subiu" else "caiu"
        val title = "${asset.ticker} $direction ${BrFormat.percent(changePercent.abs())}"
        val body = "Cotação atual: ${BrFormat.currency(price.price)} · " +
            "Variação do dia: ${BrFormat.percent(changePercent, signed = true)}"
        return post(alert.id.hashCode(), title, body)
    }

    override fun notifyMaturity(alert: AlertRule, asset: Asset, daysLeft: Long): Boolean {
        val title = "Vencimento próximo: ${asset.ticker}"
        val whenText = when (daysLeft) {
            0L -> "vence hoje"
            1L -> "vence amanhã"
            else -> "vence em $daysLeft dias"
        }
        val body = "${asset.displayName} $whenText (${BrFormat.date(asset.maturityDate)})."
        return post(alert.id.hashCode(), title, body)
    }

    private fun post(id: Int, title: String, body: String): Boolean {
        if (!canNotify()) return false
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setColor(ContextCompat.getColor(context, R.color.brand_primary))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        return try {
            manager.notify(id, notification)
            true
        } catch (_: SecurityException) {
            false
        }
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
        const val CHANNEL_ALERTS = "investment_alerts"
        const val CHANNEL_SYNC = "quote_sync"
    }
}
