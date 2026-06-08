package com.nedrichards.agileprices

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
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

        return runCatching {
            createRepository(applicationContext).refresh()
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
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
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                workName,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
