package br.com.carteirainvest

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import br.com.carteirainvest.data.notification.AndroidAlertNotifier
import br.com.carteirainvest.data.notification.SyncFailureNotifier
import br.com.carteirainvest.data.worker.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class InvestApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var syncScheduler: SyncScheduler

    @Inject
    lateinit var alertNotifier: AndroidAlertNotifier

    @Inject
    lateinit var syncFailureNotifier: SyncFailureNotifier

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        alertNotifier.ensureChannels()
        syncFailureNotifier.ensureChannel()
        syncScheduler.schedulePeriodicSync()
    }
}
