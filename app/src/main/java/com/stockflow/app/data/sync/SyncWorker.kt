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

interface SyncTrigger {
    val status: StateFlow<SyncStatus>
    fun request()
}

class SyncScheduler(private val context: Context, private val syncManager: SyncManager) : SyncTrigger {
    override val status: StateFlow<SyncStatus> = syncManager.status

    override fun request() {
        syncManager.onQueued()
        val work = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, work)
    }

    private companion object {
        const val WORK_NAME = "stockflow-sync"
    }
}
