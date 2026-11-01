package com.stockflow.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.stockflow.app.domain.SyncState
import kotlinx.coroutines.flow.Flow

@Dao
interface StockDao {
    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("""
        SELECT p.id, p.name, p.categoryId, c.name AS categoryName,
            COALESCE(SUM(CASE
                WHEN m.syncState = 'REJECTED' THEN 0
                WHEN m.type = 'IN' THEN m.quantity
                WHEN m.type = 'OUT' THEN -m.quantity
                ELSE 0 END), 0) AS stock,
            SUM(CASE WHEN m.syncState = 'PENDING' THEN 1 ELSE 0 END) AS pendingCount,
            p.serverStock
        FROM products p
        LEFT JOIN categories c ON c.id = p.categoryId
        LEFT JOIN stock_movements m ON m.productId = p.id
        GROUP BY p.id
        ORDER BY p.name COLLATE NOCASE
    """)
    fun observeProducts(): Flow<List<ProductStock>>

    @Query("SELECT * FROM stock_movements ORDER BY createdAt DESC, id DESC")
    fun observeMovements(): Flow<List<StockMovementEntity>>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getProduct(id: String): ProductEntity?

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategory(id: String): CategoryEntity?

    @Query("SELECT * FROM categories WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findCategory(name: String): CategoryEntity?

    @Query("SELECT * FROM stock_movements WHERE id = :id")
    suspend fun getMovement(id: String): StockMovementEntity?

    @Query("""
        SELECT COALESCE(SUM(CASE
            WHEN syncState = 'REJECTED' THEN 0
            WHEN type = 'IN' THEN quantity ELSE -quantity END), 0)
        FROM stock_movements WHERE productId = :productId
    """)
    suspend fun getStock(productId: String): Long

    @Insert suspend fun insertCategory(category: CategoryEntity)
    @Insert suspend fun insertProduct(product: ProductEntity)
    @Update suspend fun updateProduct(product: ProductEntity)
    @Insert suspend fun insertMovement(movement: StockMovementEntity)

    // --- Sync queue ---

    @Query("SELECT * FROM categories WHERE syncState = 'PENDING' ORDER BY updatedAt")
    suspend fun pendingCategories(): List<CategoryEntity>

    @Query("SELECT * FROM products WHERE syncState = 'PENDING' ORDER BY updatedAt")
    suspend fun pendingProducts(): List<ProductEntity>

    // A movement is sent only after its product exists on the server.
    @Query("""
        SELECT m.* FROM stock_movements m JOIN products p ON p.id = m.productId
        WHERE m.syncState = 'PENDING' AND p.syncState = 'SYNCED'
        ORDER BY m.createdAt, m.id
    """)
    suspend fun sendableMovements(): List<StockMovementEntity>

    @Query("SELECT COUNT(*) FROM categories WHERE syncState = 'PENDING'")
    suspend fun pendingCategoryCount(): Int

    @Query("SELECT COUNT(*) FROM products WHERE syncState = 'PENDING'")
    suspend fun pendingProductCount(): Int

    @Query("SELECT COUNT(*) FROM stock_movements WHERE syncState = 'PENDING'")
    suspend fun pendingMovementCount(): Int

    // updatedAt guard: if the user edited the row while it was being sent, it stays PENDING.
    @Query("UPDATE categories SET syncState = 'SYNCED' WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markCategorySynced(id: String, updatedAt: Long)

    @Query("UPDATE categories SET syncState = 'PENDING' WHERE id = :id")
    suspend fun markCategoryPending(id: String)

    @Query("UPDATE products SET syncState = 'SYNCED', serverStock = :serverStock WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markProductSynced(id: String, updatedAt: Long, serverStock: Long)

    @Query("UPDATE products SET syncState = 'PENDING' WHERE id = :id")
    suspend fun markProductPending(id: String)

    @Query("UPDATE products SET serverStock = :serverStock WHERE id = :id")
    suspend fun updateServerStock(id: String, serverStock: Long)

    @Query("UPDATE stock_movements SET syncState = :state, rejectionReason = :reason WHERE id = :id AND syncState = 'PENDING'")
    suspend fun markMovementResult(id: String, state: SyncState, reason: String?)
}
