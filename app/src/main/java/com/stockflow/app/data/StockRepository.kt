package com.stockflow.app.data

import androidx.room.withTransaction
import com.stockflow.app.data.local.CategoryEntity
import com.stockflow.app.data.local.ProductEntity
import com.stockflow.app.data.local.StockFlowDatabase
import com.stockflow.app.data.local.StockMovementEntity
import com.stockflow.app.domain.MovementType
import com.stockflow.app.domain.StockRuleViolation
import com.stockflow.app.domain.StockRules
import com.stockflow.app.domain.SyncState
import java.util.UUID

class StockRepository(private val database: StockFlowDatabase) {
    private val dao = database.stockDao()
    val products = dao.observeProducts()
    val categories = dao.observeCategories()
    val movements = dao.observeMovements()

    suspend fun createCategory(name: String): String = database.withTransaction {
        val normalized = checkedName(name)
        if (dao.findCategory(normalized) != null) {
            throw StockRuleViolation("Bu kategori zaten var.")
        }
        val category = CategoryEntity(UUID.randomUUID().toString(), normalized, System.currentTimeMillis())
        dao.insertCategory(category)
        category.id
    }

    suspend fun saveProduct(id: String?, name: String, categoryId: String?): String = database.withTransaction {
        val normalized = checkedName(name)
        if (categoryId != null && dao.getCategory(categoryId) == null) {
            throw StockRuleViolation("Kategori bulunamadı.")
        }
        val now = System.currentTimeMillis()
        if (id == null) {
            val product = ProductEntity(UUID.randomUUID().toString(), normalized, categoryId, now)
            dao.insertProduct(product)
            product.id
        } else {
            val existing = dao.getProduct(id) ?: throw StockRuleViolation("Ürün bulunamadı.")
            dao.updateProduct(existing.copy(name = normalized, categoryId = categoryId,
                updatedAt = now, syncState = SyncState.PENDING))
            id
        }
    }

    suspend fun recordMovement(
        productId: String,
        type: MovementType,
        quantity: Long,
        note: String,
        movementId: String = UUID.randomUUID().toString(),
    ): String = database.withTransaction {
        if (movementId.isBlank()) throw StockRuleViolation("İşlem kimliği boş olamaz.")
        val normalizedNote = note.trim()
        if (normalizedNote.length > 500) throw StockRuleViolation("Açıklama en fazla 500 karakter olabilir.")
        // A retry must reuse the original ID. A different user action gets a new ID.
        val previous = dao.getMovement(movementId)
        if (previous != null) {
            if (previous.productId != productId || previous.type != type ||
                previous.quantity != quantity || previous.note != normalizedNote) {
                throw StockRuleViolation("Aynı işlem kimliği farklı içerikle kullanılamaz.")
            }
            return@withTransaction previous.id
        }
        if (dao.getProduct(productId) == null) throw StockRuleViolation("Ürün bulunamadı.")
        // Reading stock, checking it, and inserting the movement are one transaction.
        StockRules.validate(dao.getStock(productId), quantity, type)
        val movement = StockMovementEntity(movementId, productId, type, quantity,
            normalizedNote, System.currentTimeMillis())
        dao.insertMovement(movement)
        movement.id
    }

    private fun checkedName(value: String): String {
        val name = value.trim()
        if (name.isEmpty() || name.length > 100) {
            throw StockRuleViolation("Ad 1–100 karakter arasında olmalı.")
        }
        return name
    }
}
