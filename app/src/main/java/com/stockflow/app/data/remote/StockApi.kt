package com.stockflow.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

interface StockApi {
    @PUT("categories/{id}")
    suspend fun putCategory(@Path("id") id: String, @Body body: CategoryDto)

    @PUT("products/{id}")
    suspend fun putProduct(@Path("id") id: String, @Body body: ProductDto): ProductResultDto

    @POST("movements")
    suspend fun postMovement(@Body body: MovementDto): MovementResultDto

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun create(baseUrl: String): StockApi {
            val client = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(StockApi::class.java)
        }

        fun errorDetail(error: HttpException): String? = runCatching {
            val body = error.response()?.errorBody()?.string() ?: return null
            json.parseToJsonElement(body).jsonObject["detail"]?.jsonPrimitive?.content
        }.getOrNull()
    }
}

@Serializable
data class CategoryDto(
    val name: String,
    @SerialName("updated_at") val updatedAt: Long,
)

@Serializable
data class ProductDto(
    val name: String,
    @SerialName("category_id") val categoryId: String?,
    @SerialName("updated_at") val updatedAt: Long,
)

@Serializable
data class ProductResultDto(
    val id: String,
    @SerialName("server_stock") val serverStock: Long,
    val applied: Boolean = true,
)

@Serializable
data class MovementDto(
    val id: String,
    @SerialName("product_id") val productId: String,
    val type: String,
    val quantity: Long,
    val note: String,
    @SerialName("created_at") val createdAt: Long,
)

@Serializable
data class MovementResultDto(
    val id: String,
    val status: String,
    val reason: String? = null,
    @SerialName("server_stock") val serverStock: Long,
    val replayed: Boolean = false,
)
