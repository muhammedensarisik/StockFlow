package com.stockflow.app.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.stockflow.app.data.StockRepository
import com.stockflow.app.data.local.StockFlowDatabase
import com.stockflow.app.data.remote.StockApi
import com.stockflow.app.domain.MovementType
import com.stockflow.app.domain.SyncState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE, application = android.app.Application::class)
class SyncManagerTest {
    private lateinit var database: StockFlowDatabase
    private lateinit var repository: StockRepository
    private lateinit var server: MockWebServer
    private lateinit var sync: SyncManager

    @Before fun setup() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, StockFlowDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = StockRepository(database)
        server = MockWebServer().apply { start() }
        sync = SyncManager(database.stockDao(), StockApi.create(server.url("/").toString()))
    }

    @After fun teardown() {
        server.shutdown()
        database.close()
    }

    private fun json(body: String, code: Int = 200) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    private suspend fun productWithOneEntry(): Pair<String, String> {
        val productId = repository.saveProduct(null, "Vida", null)
        val movementId = repository.recordMovement(productId, MovementType.IN, 5, "", "m-1")
        return productId to movementId
    }

    @Test fun `accepted movement becomes SYNCED and server stock is stored`() = runBlocking {
        val productId = repository.saveProduct(null, "Vida", null)
        repository.recordMovement(productId, MovementType.IN, 10, "", "m-1")
        server.enqueue(json("""{"id":"$productId","server_stock":0}"""))
        server.enqueue(json("""{"id":"m-1","status":"ACCEPTED","reason":null,"server_stock":10}"""))

        assertEquals(SyncOutcome.SUCCESS, sync.syncOnce())

        val movement = repository.movements.first().single()
        assertEquals(SyncState.SYNCED, movement.syncState)
        val product = repository.products.first().single()
        assertEquals(10L, product.serverStock)
        assertEquals(0L, product.pendingCount)
        // Product is sent before its movement, and the movement keeps the phone's ID.
        assertEquals("/products/$productId", server.takeRequest().path)
        val sent = server.takeRequest()
        assertEquals("/movements", sent.path)
        assertTrue(sent.body.readUtf8().contains("\"id\":\"m-1\""))
    }

    @Test fun `rejected movement is kept with its reason and no longer counts`() = runBlocking {
        val productId = repository.saveProduct(null, "Vida", null)
        repository.recordMovement(productId, MovementType.IN, 10, "", "m-in")
        repository.recordMovement(productId, MovementType.OUT, 7, "", "m-out")
        server.enqueue(json("""{"id":"$productId","server_stock":0}"""))
        server.enqueue(json("""{"id":"m-in","status":"ACCEPTED","server_stock":10}"""))
        // Another device sold first, so the server cannot accept this exit.
        server.enqueue(json("""{"id":"m-out","status":"REJECTED","reason":"Sunucuda yetersiz stok.","server_stock":2}"""))

        sync.syncOnce()

        val rejected = repository.movements.first().first { it.id == "m-out" }
        assertEquals(SyncState.REJECTED, rejected.syncState)
        assertEquals("Sunucuda yetersiz stok.", rejected.rejectionReason)
        val product = repository.products.first().single()
        assertEquals(10L, product.stock)
        assertEquals(2L, product.serverStock)
    }

    @Test fun `no connection keeps everything PENDING and asks for retry`() = runBlocking {
        productWithOneEntry()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        assertEquals(SyncOutcome.RETRY, sync.syncOnce())
        assertEquals(SyncPhase.OFFLINE, sync.status.value.phase)
        assertTrue(repository.movements.first().all { it.syncState == SyncState.PENDING })
    }

    @Test fun `lost response makes the same movement arrive twice with the same ID`() = runBlocking {
        val productId = repository.saveProduct(null, "Vida", null)
        repository.recordMovement(productId, MovementType.IN, 10, "", "m-1")
        server.enqueue(json("""{"id":"$productId","server_stock":0}"""))
        // The server received the movement but the connection dropped before the answer.
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        server.enqueue(json("""{"id":"m-1","status":"ACCEPTED","server_stock":10,"replayed":true}"""))

        // OkHttp silently resends the POST on a new connection. This is exactly the duplicate the
        // server's idempotency (same ID -> stored result) must absorb.
        assertEquals(SyncOutcome.SUCCESS, sync.syncOnce())

        assertEquals(3, server.requestCount)
        server.takeRequest() // product
        val first = server.takeRequest().body.readUtf8()
        val resent = server.takeRequest().body.readUtf8()
        assertEquals(first, resent)
        assertEquals(SyncState.SYNCED, repository.movements.first().single().syncState)
    }

    @Test fun `product edited during sync stays PENDING`() = runBlocking {
        val productId = repository.saveProduct(null, "Vida", null)
        database.stockDao().markProductSynced(productId, updatedAt = -1, serverStock = 0)
        assertEquals(1, database.stockDao().pendingProducts().size)
    }
}
