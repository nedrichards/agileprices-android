package com.nedrichards.agileprices

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

internal data class AtOrBelowZeroAlert(
    val currentPrice: PriceWindow,
    val positiveAt: Instant?,
    val nextBoundary: Instant,
)

internal fun atOrBelowZeroAlert(snapshot: PriceSnapshot, now: Instant): AtOrBelowZeroAlert? {
    val current = snapshot.currentPrice ?: return null
    if (current.pricePencePerKwh > 0.0) return null

    var cursor = current.validTo
    val futurePrices = snapshot.sparklinePrices
        .filter { it.validFrom >= current.validTo && it.validTo > now }
        .sortedByValidFrom()
    for (price in futurePrices) {
        if (price.validFrom != cursor || price.pricePencePerKwh > 0.0) break
        cursor = price.validTo
    }

    return AtOrBelowZeroAlert(
        currentPrice = current,
        positiveAt = futurePrices.firstOrNull { it.validFrom == cursor && it.pricePencePerKwh > 0.0 }?.validFrom,
        nextBoundary = current.validTo,
    )
}

internal fun AtOrBelowZeroAlert.detailText(now: Instant): String {
    val positiveAt = positiveAt ?: return "At or below zero until ${formatTime(nextBoundary)}; later rates unavailable"
    val remaining = Duration.between(now, positiveAt).toCompactDurationText()
    return "At or below zero for $remaining; positive from ${formatTime(positiveAt)}"
}

object NegativePriceNotifier {
    private const val channelId = "at_or_below_zero_prices"
    private const val notificationId = 41
    private const val updateWorkName = "at-or-below-zero-price-notification"

    fun canPost(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    suspend fun update(
        context: Context,
        scheduleNextBoundary: Boolean = true,
    ): Instant? {
        val appContext = context.applicationContext
        if (!canPost(appContext)) {
            cancel(appContext)
            return null
        }

        val snapshot = AgileRepository(SettingsStore(appContext)).snapshots.first()
        val now = Instant.now()
        val alert = atOrBelowZeroAlert(snapshot, now)
        if (alert == null) {
            cancel(appContext)
            return null
        }

        createChannel(appContext)
        val detail = alert.detailText(now)
        val timeout = alert.positiveAt?.let { Duration.between(now, it).toMillis().coerceAtLeast(1) }
        NotificationManagerCompat.from(appContext).notify(
            notificationId,
            NotificationCompat.Builder(appContext, channelId)
                .setSmallIcon(R.drawable.ic_notification_price)
                .setContentTitle("Agile price ${alert.currentPrice.pricePencePerKwh.formatPrice()}p/kWh")
                .setContentText(detail)
                .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
                .setContentIntent(agileLaunchPendingIntent(appContext))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .apply { timeout?.let(::setTimeoutAfter) }
                .build(),
        )
        if (scheduleNextBoundary) {
            scheduleBoundaryUpdate(appContext, now, alert.nextBoundary)
        }
        return alert.nextBoundary
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(notificationId)
        WorkManager.getInstance(context).cancelUniqueWork(updateWorkName)
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                channelId,
                context.getString(R.string.negative_price_alert_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.negative_price_alert_channel_description)
            },
        )
    }

    internal fun scheduleBoundaryUpdate(context: Context, now: Instant, nextBoundary: Instant) {
        val delay = Duration.between(now, nextBoundary).toMillis()
        if (delay <= 0) return
        val request = OneTimeWorkRequestBuilder<NegativePriceAlertWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            updateWorkName,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}

class NegativePriceAlertWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val nextBoundary = NegativePriceNotifier.update(
            context = applicationContext,
            scheduleNextBoundary = false,
        )
        nextBoundary?.let {
            NegativePriceNotifier.scheduleBoundaryUpdate(applicationContext, Instant.now(), it)
        }
        return Result.success()
    }
}
