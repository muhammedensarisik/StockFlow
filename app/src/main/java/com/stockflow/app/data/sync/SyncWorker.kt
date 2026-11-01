package com.stockflow.app.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.stockflow.app.StockFlowApplication
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.TimeUnit

/** Runs one sync pass in the background. WorkManager keeps the request across app restarts. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val syncManager = (applicationContext as StockFlowApplication).syncManager
        return when (syncManager.syncOnce()) {
            SyncOutcome.SUCCESS -> Result.success()
            SyncOutcome.RETRY -> Result.retry()
            SyncOutcome.FAILED -> Result.failure()
        }
    }
}

/** What the UI needs from sync: its status and a way to ask for a run. */
interface SyncTrigger {
    val status: StateFlow<SyncStatus>
    fun request()
}

class SyncScheduler(private val context: Context, private val syncManager: SyncManager) : SyncTrigger {
    override val status: StateFlow<SyncStatus> = syncManager.status

    override fun request() {
        syncManager.onQueued()
        val work = OneTimeWorkRequestBuilder<SyncWorker>()
            // WorkManager starts the job only when a network is available, even after a reboot.
            .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        // One sync chain at a time; a new request runs after the current one instead of in parallel.
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, work)
    }

    private companion object {
        const val WORK_NAME = "stockflow-sync"
    }
}
