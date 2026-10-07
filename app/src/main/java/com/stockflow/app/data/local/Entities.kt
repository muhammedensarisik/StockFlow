package com.stockflow.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.stockflow.app.domain.MovementType
import com.stockflow.app.domain.SyncState

@Entity(tableName = "categories", indices = [Index(value = ["name"], unique = true)])
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val updatedAt: Long,
    val syncState: SyncState = SyncState.PENDING,
)

@Entity(
    tableName = "products",
    foreignKeys = [ForeignKey(
        entity = CategoryEntity::class,
        parentColumns = ["id"], childColumns = ["categoryId"],
        onDelete = ForeignKey.RESTRICT,
    )],
    indices = [Index("categoryId")],
)
data class ProductEntity(
    @PrimaryKey val id: String,
    val name: String,
    val categoryId: String?,
    val updatedAt: Long,
    val syncState: SyncState = SyncState.PENDING,
)

@Entity(
    tableName = "stock_movements",
    foreignKeys = [ForeignKey(
        entity = ProductEntity::class,
        parentColumns = ["id"], childColumns = ["productId"],
        onDelete = ForeignKey.RESTRICT,
    )],
    indices = [Index("productId"), Index("syncState")],
)
data class StockMovementEntity(
    @PrimaryKey val id: String,
    val productId: String,
    val type: MovementType,
    val quantity: Long,
    val note: String,
    val createdAt: Long,
    val syncState: SyncState = SyncState.PENDING,
    val rejectionReason: String? = null,
)

data class ProductStock(
    val id: String,
    val name: String,
    val categoryId: String?,
    val categoryName: String?,
    val stock: Long,
    val pendingCount: Long,
)

class RoomConverters {
    @TypeConverter fun typeToString(value: MovementType): String = value.name
    @TypeConverter fun stringToType(value: String): MovementType = MovementType.valueOf(value)
    @TypeConverter fun stateToString(value: SyncState): String = value.name
    @TypeConverter fun stringToState(value: String): SyncState = SyncState.valueOf(value)
}
