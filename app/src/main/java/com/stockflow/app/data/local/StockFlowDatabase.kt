package com.stockflow.app.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [CategoryEntity::class, ProductEntity::class, StockMovementEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@TypeConverters(RoomConverters::class)
abstract class StockFlowDatabase : RoomDatabase() {
    abstract fun stockDao(): StockDao
}
