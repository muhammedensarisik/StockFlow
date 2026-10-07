package com.stockflow.app

import android.app.Application
import androidx.room.Room
import com.stockflow.app.data.StockRepository
import com.stockflow.app.data.local.StockFlowDatabase

class StockFlowApplication : Application() {
    val database: StockFlowDatabase by lazy {
        Room.databaseBuilder(this, StockFlowDatabase::class.java, "stockflow.db").build()
    }
    val repository: StockRepository by lazy { StockRepository(database) }
}
