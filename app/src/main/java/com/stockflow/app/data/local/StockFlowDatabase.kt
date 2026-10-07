package com.stockflow.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [CategoryEntity::class, ProductEntity::class, StockMovementEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(RoomConverters::class)
abstract class StockFlowDatabase : RoomDatabase() {
    abstract fun stockDao(): StockDao
}
