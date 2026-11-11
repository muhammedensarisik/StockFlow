package com.stockflow.app.data.sync

import com.stockflow.app.data.local.StockDao
import com.stockflow.app.data.remote.CategoryDto
import com.stockflow.app.data.remote.MovementDto
import com.stockflow.app.data.remote.ProductDto
import com.stockflow.app.data.remote.StockApi
import com.stockflow.app.domain.SyncState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.io.IOException

enum class SyncPhase { IDLE, QUEUED, RUNNING, DONE, OFFLINE, ERROR }

data class SyncStatus(
    val phase: SyncPhase = SyncPhase.IDLE,
    val message: String = "Henüz senkronizasyon yapılmadı.",
    val lastSuccessAt: Long? = null,
)

enum class SyncOutcome { SUCCESS, RETRY, FAILED }

class SyncManager(
    private val dao: StockDao,
    private val api: StockApi,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    fun onQueued() {
        if (_status.value.phase != SyncPhase.RUNNING) {
            _status.value = _status.value.copy(phase = SyncPhase.QUEUED,
                message = "Gönderim sırada. İnternet bağlantısı bekleniyor…")
        }
    }

    suspend fun syncOnce(): SyncOutcome = mutex.withLock {
        _status.value = _status.value.copy(phase = SyncPhase.RUNNING, message = "Sunucuyla senkronize ediliyor…")
        try {
            val problems = mutableListOf<String>()
            var sent = 0
            var rejected = 0

            for (category in dao.pendingCategories()) {
                try {
                    api.putCategory(category.id, CategoryDto(category.name, category.updatedAt))
                    dao.markCategorySynced(category.id, category.updatedAt)
                } catch (e: HttpException) {
                    if (e.code() >= 500) throw e
                    problems += "Kategori \"${category.name}\": ${StockApi.errorDetail(e) ?: "HTTP ${e.code()}"}"
                }
            }

            for (product in dao.pendingProducts()) {
                try {
                    val result = api.putProduct(product.id, ProductDto(product.name, product.categoryId, product.updatedAt))
                    dao.markProductSynced(product.id, product.updatedAt, result.serverStock)
                } catch (e: HttpException) {
                    if (e.code() >= 500) throw e
                    if (e.code() == 404 && product.categoryId != null) {
                        dao.markCategoryPending(product.categoryId)
                    } else {
                        problems += "Ürün \"${product.name}\": ${StockApi.errorDetail(e) ?: "HTTP ${e.code()}"}"
                    }
                }
            }

            for (movement in dao.sendableMovements()) {
                try {
                    val result = api.postMovement(MovementDto(movement.id, movement.productId,
                        movement.type.name, movement.quantity, movement.note, movement.createdAt))
                    if (result.status == "ACCEPTED") {
                        dao.markMovementResult(movement.id, SyncState.SYNCED, null)
                        sent++
                    } else {
                        dao.markMovementResult(movement.id, SyncState.REJECTED, result.reason ?: "Sunucu reddetti.")
                        rejected++
                    }
                    dao.updateServerStock(movement.productId, result.serverStock)
                } catch (e: HttpException) {
                    when {
                        e.code() >= 500 -> throw e
                        e.code() == 404 -> {
                            dao.markProductPending(movement.productId)
                            problems += "Ürün sunucuda bulunamadı; yeniden gönderilecek."
                        }
                        else -> {
                            val reason = StockApi.errorDetail(e) ?: "HTTP ${e.code()}"
                            dao.markMovementResult(movement.id, SyncState.REJECTED, reason)
                            rejected++
                        }
                    }
                }
            }

            val stillPending = dao.pendingCategoryCount() + dao.pendingProductCount() + dao.pendingMovementCount()
            val summary = buildString {
                append("$sent hareket onaylandı")
                if (rejected > 0) append(", $rejected hareket reddedildi")
                append('.')
                if (stillPending > 0) append(" $stillPending kayıt hâlâ bekliyor.")
            }
            if (problems.isEmpty() && stillPending == 0) {
                _status.value = SyncStatus(SyncPhase.DONE, "Senkronize edildi. $summary", clock())
                SyncOutcome.SUCCESS
            } else if (problems.isEmpty()) {
                _status.value = SyncStatus(SyncPhase.QUEUED, summary, _status.value.lastSuccessAt)
                SyncOutcome.RETRY
            } else {
                _status.value = SyncStatus(SyncPhase.ERROR, "$summary ${problems.first()}", _status.value.lastSuccessAt)
                SyncOutcome.FAILED
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            _status.value = _status.value.copy(phase = SyncPhase.OFFLINE,
                message = "Sunucuya ulaşılamadı. Bağlantı gelince yeniden denenecek.")
            SyncOutcome.RETRY
        } catch (e: HttpException) {
            _status.value = _status.value.copy(phase = SyncPhase.OFFLINE,
                message = "Sunucu hata verdi (HTTP ${e.code()}). Yeniden denenecek.")
            SyncOutcome.RETRY
        }
    }
}
