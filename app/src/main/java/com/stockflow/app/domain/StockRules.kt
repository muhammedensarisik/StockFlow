package com.stockflow.app.domain

enum class MovementType { IN, OUT }
enum class SyncState { PENDING, SYNCED, REJECTED }

class StockRuleViolation(message: String) : IllegalArgumentException(message)

object StockRules {
    const val MAX_STOCK = 1_000_000_000L

    fun validate(currentStock: Long, quantity: Long, type: MovementType): Long {
        if (currentStock !in 0..MAX_STOCK) {
            throw StockRuleViolation("Stok kaydı tutarsız. İşlem durduruldu.")
        }
        if (quantity !in 1..MAX_STOCK) {
            throw StockRuleViolation("Miktar 1 ile 1.000.000.000 arasında tam sayı olmalı.")
        }
        return when (type) {
            MovementType.IN -> {
                if (quantity > MAX_STOCK - currentStock) {
                    throw StockRuleViolation("Stok üst sınırı aşılamaz.")
                }
                currentStock + quantity
            }
            MovementType.OUT -> {
                if (quantity > currentStock) throw StockRuleViolation("Yetersiz stok.")
                currentStock - quantity
            }
        }
    }
}
