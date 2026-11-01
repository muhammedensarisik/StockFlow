package com.stockflow.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stockflow.app.data.StockRepository
import com.stockflow.app.data.local.CategoryEntity
import com.stockflow.app.data.local.ProductStock
import com.stockflow.app.data.local.StockMovementEntity
import com.stockflow.app.data.sync.SyncTrigger
import com.stockflow.app.domain.MovementType
import com.stockflow.app.domain.StockRuleViolation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StockUiState(
    val products: List<ProductStock> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val movements: List<StockMovementEntity> = emptyList(),
    val loading: Boolean = true,
    val readError: String? = null,
)

class StockViewModel(
    private val repository: StockRepository,
    private val sync: SyncTrigger,
) : ViewModel() {
    val state = combine(repository.products, repository.categories, repository.movements) { products, categories, movements ->
        StockUiState(products, categories, movements, loading = false)
    }.catch { error ->
        if (error is CancellationException) throw error
        emit(StockUiState(loading = false, readError = "Veriler okunamadı. Uygulamayı yeniden açmayı deneyin."))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StockUiState())

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    val syncStatus = sync.status

    fun clearMessage() { _message.value = null }
    fun syncNow() = sync.request()
    fun createCategory(name: String, onSuccess: () -> Unit) = perform(onSuccess) {
        repository.createCategory(name)
    }
    fun saveProduct(id: String?, name: String, categoryId: String?, onSuccess: () -> Unit) = perform(onSuccess) {
        repository.saveProduct(id, name, categoryId)
    }
    fun recordMovement(productId: String, type: MovementType, quantityText: String, note: String, onSuccess: () -> Unit) = perform(onSuccess) {
        val quantity = quantityText.trim().toLongOrNull()
            ?: throw StockRuleViolation("Geçerli bir tam sayı girin.")
        repository.recordMovement(productId, type, quantity, note)
    }

    private fun perform(onSuccess: () -> Unit, action: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                action()
                // Every successful write leaves a PENDING record behind; ask for a sync.
                sync.request()
                onSuccess()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: StockRuleViolation) {
                _message.value = error.message
            } catch (error: Exception) {
                _message.value = "İşlem kaydedilemedi. Tekrar deneyin."
            } finally {
                _busy.value = false
            }
        }
    }
}
