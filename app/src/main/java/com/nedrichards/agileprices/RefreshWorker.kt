package com.nedrichards.agileprices

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

class RefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val settings = SettingsStore(applicationContext).settings.first()
        if (settings.selectedRegionCode.isNullOrBlank() && settings.selectedTariffCode.isNullOrBlank()) {
            return Result.success()
        }

        return runCatchingPreservingCancellation {
            createRepository(applicationContext).refresh()
        }.fold(
            onSuccess = {
                val refreshedSettings = SettingsStore(applicationContext).settings.first()
                if (refreshedSettings.requiresMorePriceData(Instant.now())) {
                    Result.retry()
                } else {
                    Result.success()
                }
            },
            onFailure = { error ->
                if (error.isRetryableRefreshFailure()) Result.retry() else Result.success()
            },
        )
    }

    companion object {
        private const val workName = "agile-price-refresh"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(30, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    10,
                    TimeUnit.MINUTES,
                )
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                workName,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}

internal fun AgileSettings.requiresMorePriceData(now: Instant): Boolean {
    if (selectedTariffCode.isNullOrBlank()) return false
    return !pricesCoverExpectedHorizon(cachedPrices, now)
}

internal fun expectedPriceHorizon(now: Instant): Instant {
    val zone = ZoneId.of("Europe/London")
    val localNow = now.atZone(zone)
    val releaseDay = localNow.toLocalDate().plusDays(if (localNow.hour >= 16) 1 else 0)
    // Agile publishes rates through 23:00 UK time for the released day.
    return releaseDay.atTime(23, 0).atZone(zone).toInstant()
}

internal fun pricesCoverExpectedHorizon(prices: List<PriceWindow>, now: Instant): Boolean {
    var coveredUntil = Instant.ofEpochSecond(now.epochSecond / 1800 * 1800)
    val horizon = expectedPriceHorizon(now)
    for (price in prices.sortedBy { it.validFrom }) {
        if (price.validTo <= coveredUntil) continue
        if (price.validFrom > coveredUntil) return false
        coveredUntil = price.validTo
        if (coveredUntil >= horizon) return true
    }
    return false
}

internal fun Throwable.isRetryableRefreshFailure(): Boolean = when (this) {
    is IOException -> this !is javax.net.ssl.SSLException
    is OctopusApiException -> statusCode == 408 || statusCode == 429 || statusCode in 500..599
    else -> false
}
