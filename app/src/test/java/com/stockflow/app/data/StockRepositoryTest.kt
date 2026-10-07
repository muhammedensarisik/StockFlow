package com.stockflow.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.stockflow.app.data.local.StockFlowDatabase
import com.stockflow.app.domain.MovementType
import com.stockflow.app.domain.StockRuleViolation
import com.stockflow.app.domain.SyncState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE, application = android.app.Application::class)
class StockRepositoryTest {
    private lateinit var database: StockFlowDatabase
    private lateinit var repository: StockRepository
    private lateinit var context: Context

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, StockFlowDatabase::class.java).build()
        repository = StockRepository(database)
    }
    @After fun teardown() { database.close() }

    @Test fun `product starts at zero and offline movement is pending`() = runBlocking {
        val categoryId = repository.createCategory("Malzeme")
        val productId = repository.saveProduct(null, "Vida", categoryId)
        assertEquals(0L, repository.products.first().single().stock)
        repository.recordMovement(productId, MovementType.IN, 100, "İlk teslimat")
        repository.recordMovement(productId, MovementType.OUT, 20, "Satış")
        assertEquals(80L, repository.products.first().single().stock)
        assertEquals(2L, repository.products.first().single().pendingCount)
        assertTrue(repository.movements.first().all { it.syncState == SyncState.PENDING })
    }

    @Test fun `duplicate movement ID does not subtract twice`() = runBlocking {
        val productId = repository.saveProduct(null, "Vida", null)
        repository.recordMovement(productId, MovementType.IN, 100, "")
        repeat(2) { repository.recordMovement(productId, MovementType.OUT, 20, "Satış", "same-operation") }
        assertEquals(80L, database.stockDao().getStock(productId))
        assertEquals(2, repository.movements.first().size)
    }

    @Test fun `two distinct operations on one product are both applied`() = runBlocking {
        val productId = repository.saveProduct(null, "Vida", null)
        repository.recordMovement(productId, MovementType.IN, 10, "")
        repository.recordMovement(productId, MovementType.OUT, 3, "", "first-operation")
        repository.recordMovement(productId, MovementType.OUT, 3, "", "second-operation")
        assertEquals(4L, database.stockDao().getStock(productId))
    }

    @Test fun `reusing ID with different payload fails without modifying stock`() = runBlocking {
        val productId = repository.saveProduct(null, "Vida", null)
        repository.recordMovement(productId, MovementType.IN, 10, "", "original")
        var rejected = false
        try { repository.recordMovement(productId, MovementType.IN, 20, "", "original") }
        catch (_: StockRuleViolation) { rejected = true }
        assertTrue(rejected)
        assertEquals(10L, database.stockDao().getStock(productId))
    }

    @Test fun `insufficient stock does not leave a movement behind`() = runBlocking {
        val productId = repository.saveProduct(null, "Vida", null)
        repository.recordMovement(productId, MovementType.IN, 10, "")
        var rejected = false
        try { repository.recordMovement(productId, MovementType.OUT, 11, "") }
        catch (_: StockRuleViolation) { rejected = true }
        assertTrue(rejected)
        assertEquals(10L, database.stockDao().getStock(productId))
        assertEquals(1, repository.movements.first().size)
    }

    @Test fun `concurrent withdrawals cannot overspend local stock`() = runBlocking {
        val productId = repository.saveProduct(null, "Vida", null)
        repository.recordMovement(productId, MovementType.IN, 10, "")
        val results = coroutineScope {
            (1..12).map { number -> async(Dispatchers.Default) {
                try {
                    repository.recordMovement(productId, MovementType.OUT, 2, "", "withdraw-$number")
                    true
                } catch (_: StockRuleViolation) { false }
            } }.awaitAll()
        }
        assertEquals(5, results.count { it })
        assertEquals(0L, database.stockDao().getStock(productId))
        assertEquals(6, repository.movements.first().size)
    }

    @Test fun `editing product preserves its identity and stock`() = runBlocking {
        val productId = repository.saveProduct(null, "Vida", null)
        repository.recordMovement(productId, MovementType.IN, 10, "")
        val returnedId = repository.saveProduct(productId, "Çelik vida", null)
        assertEquals(productId, returnedId)
        assertEquals("Çelik vida", repository.products.first().single().name)
        assertEquals(10L, database.stockDao().getStock(productId))
    }

    @Test fun `duplicate category is rejected`() = runBlocking {
        repository.createCategory("Malzeme")
        var rejected = false
        try { repository.createCategory("Malzeme") } catch (_: StockRuleViolation) { rejected = true }
        assertTrue(rejected)
        assertEquals(1, repository.categories.first().size)
    }

    @Test fun `disk data survives database close and reopen`() = runBlocking {
        val fileName = "persistence-test.db"
        context.deleteDatabase(fileName)
        val diskDb = Room.databaseBuilder(context, StockFlowDatabase::class.java, fileName).build()
        val productId: String
        try {
            val diskRepository = StockRepository(diskDb)
            productId = diskRepository.saveProduct(null, "Vida", null)
            diskRepository.recordMovement(productId, MovementType.IN, 25, "Teslimat")
        } finally { diskDb.close() }
        val reopened = Room.databaseBuilder(context, StockFlowDatabase::class.java, fileName).build()
        try {
            assertEquals(25L, reopened.stockDao().getStock(productId))
            assertEquals(1, StockRepository(reopened).movements.first().size)
        } finally { reopened.close(); context.deleteDatabase(fileName) }
    }
}
