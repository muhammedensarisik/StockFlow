package com.stockflow.app

import android.app.Application
import androidx.room.Room
import com.stockflow.app.data.StockRepository
import com.stockflow.app.data.local.StockFlowDatabase
import com.stockflow.app.data.remote.StockApi
import com.stockflow.app.data.sync.SyncManager
import com.stockflow.app.data.sync.SyncScheduler

class StockFlowApplication : Application() {
    val database: StockFlowDatabase by lazy {
        Room.databaseBuilder(this, StockFlowDatabase::class.java, "stockflow.db").build()
    }
    val repository: StockRepository by lazy { StockRepository(database) }
    val syncManager: SyncManager by lazy { SyncManager(database.stockDao(), StockApi.create(BuildConfig.SYNC_BASE_URL)) }
    val syncScheduler: SyncScheduler by lazy { SyncScheduler(this, syncManager) }

    override fun onCreate() {
        super.onCreate()
        // Anything left PENDING from an earlier session is sent once a network is available.
        syncScheduler.request()
    }
}
