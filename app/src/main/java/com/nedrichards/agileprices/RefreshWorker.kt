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
    if (cachedPrices.isEmpty() || currentPriceAt(cachedPrices, now) == null) return true
    if (cachedPrices.maxOfOrNull { it.validTo }?.let { it <= now } != false) return true

    return findBestLoadWindow(
        prices = cachedPrices,
        now = now,
        durationMinutes = loadDurationMinutes,
        searchHorizonMinutes = searchHorizonMinutes,
    ) == null
}

internal fun Throwable.isRetryableRefreshFailure(): Boolean = when (this) {
    is IOException -> this !is javax.net.ssl.SSLException
    is OctopusApiException -> statusCode == 408 || statusCode == 429 || statusCode in 500..599
    else -> false
}
