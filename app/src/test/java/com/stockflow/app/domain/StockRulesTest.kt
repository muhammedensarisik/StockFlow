package com.stockflow.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StockRulesTest {
    @Test fun `entry increases stock`() {
        assertEquals(120L, StockRules.validate(100, 20, MovementType.IN))
    }
    @Test fun `exit decreases stock`() {
        assertEquals(80L, StockRules.validate(100, 20, MovementType.OUT))
    }
    @Test fun `stock may reach zero`() {
        assertEquals(0L, StockRules.validate(10, 10, MovementType.OUT))
    }
    @Test fun `insufficient stock is rejected`() {
        assertThrows(StockRuleViolation::class.java) { StockRules.validate(10, 11, MovementType.OUT) }
    }
    @Test fun `zero and negative amounts are rejected`() {
        for (quantity in listOf(0L, -1L)) {
            assertThrows(StockRuleViolation::class.java) { StockRules.validate(10, quantity, MovementType.IN) }
        }
    }
    @Test fun `stock upper bound is enforced`() {
        assertThrows(StockRuleViolation::class.java) { StockRules.validate(StockRules.MAX_STOCK, 1, MovementType.IN) }
    }
    @Test fun `corrupt starting balance blocks writes`() {
        assertThrows(StockRuleViolation::class.java) { StockRules.validate(-1, 1, MovementType.IN) }
    }
}
