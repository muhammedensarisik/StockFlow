package com.stockflow.app.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [CategoryEntity::class, ProductEntity::class, StockMovementEntity::class],
    version = 2,
    exportSchema = true,
    // v2 adds products.serverStock. Room compares schemas/1.json and 2.json and writes the
    // ALTER TABLE for us, so data on an installed v1 app is kept.
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@TypeConverters(RoomConverters::class)
abstract class StockFlowDatabase : RoomDatabase() {
    abstract fun stockDao(): StockDao
}
