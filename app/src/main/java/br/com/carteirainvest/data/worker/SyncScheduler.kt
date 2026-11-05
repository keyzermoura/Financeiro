package br.com.carteirainvest.data.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Periodic intervals are configurable but cannot go below WorkManager's 15-minute
 * minimum. The 5-minute B3 TTL is enforced by the repository during foreground refresh;
 * the background worker only needs to keep Room reasonably current without burning the
 * Brapi free-tier quota.
 */
data class SyncScheduleConfig(
    val b3Minutes: Long = 15L,
    val cryptoMinutes: Long = 15L,
    val fxMinutes: Long = 60L,
    val flexMinutes: Long = 5L,
)

@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: SyncScheduleConfig = SyncScheduleConfig(),
) {

    fun schedulePeriodicSync() {
        val manager = WorkManager.getInstance(context)
        enqueue(manager, B3_UNIQUE_NAME, B3_TAG) {
            PeriodicWorkRequestBuilder<B3SyncWorker>(
                config.b3Minutes,
                TimeUnit.MINUTES,
                config.flexMinutes,
                TimeUnit.MINUTES,
            )
        }
        enqueue(manager, CRYPTO_UNIQUE_NAME, CRYPTO_TAG) {
            PeriodicWorkRequestBuilder<CryptoSyncWorker>(
                config.cryptoMinutes,
                TimeUnit.MINUTES,
                config.flexMinutes,
                TimeUnit.MINUTES,
            )
        }
        enqueue(manager, FX_UNIQUE_NAME, FX_TAG) {
            PeriodicWorkRequestBuilder<FxSyncWorker>(
                config.fxMinutes,
                TimeUnit.MINUTES,
                config.flexMinutes,
                TimeUnit.MINUTES,
            )
        }
    }

    private fun enqueue(
        manager: WorkManager,
        uniqueName: String,
        tag: String,
        requestFactory: () -> PeriodicWorkRequest.Builder,
    ) {
        val request = requestFactory()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                BACKOFF_SECONDS,
                TimeUnit.SECONDS,
            )
            .addTag(tag)
            .build()

        // REPLACE guarantees that changing the intervals takes effect on the next app
        // launch instead of silently keeping an old schedule.
        runCatching {
            manager.enqueueUniquePeriodicWork(
                uniqueName,
                ExistingPeriodicWorkPolicy.REPLACE,
                request,
            )
        }
    }

    companion object {
        const val B3_UNIQUE_NAME = "carteira_b3_sync"
        const val CRYPTO_UNIQUE_NAME = "carteira_crypto_sync"
        const val FX_UNIQUE_NAME = "carteira_fx_sync"

        const val B3_TAG = "b3-sync"
        const val CRYPTO_TAG = "crypto-sync"
        const val FX_TAG = "fx-sync"

        private const val BACKOFF_SECONDS = 30L
    }
}
