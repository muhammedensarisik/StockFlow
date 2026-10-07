package com.stockflow.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.stockflow.app.data.local.CategoryEntity
import com.stockflow.app.data.local.ProductStock
import com.stockflow.app.data.local.StockMovementEntity
import com.stockflow.app.domain.MovementType
import com.stockflow.app.domain.SyncState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Green = Color(0xFF176B55)
private val Cream = Color(0xFFF3F6F0)
private val Ink = Color(0xFF142924)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockFlowApp(vm: StockViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val snackbar = remember { SnackbarHostState() }
    var showProductForm by rememberSaveable { mutableStateOf(false) }
    var showCategoryForm by rememberSaveable { mutableStateOf(false) }
    var editId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(message, showProductForm, showCategoryForm) {
        if (message != null && !showProductForm && !showCategoryForm) {
            snackbar.showSnackbar(message!!)
            vm.clearMessage()
        }
    }

    MaterialTheme(colorScheme = lightColorScheme(
        primary = Green, onPrimary = Color.White,
        primaryContainer = Color(0xFFD7EDE2), onPrimaryContainer = Ink,
        background = Cream, surface = Color.White,
        onBackground = Ink, onSurface = Ink,
    )) {
        Scaffold(
            containerColor = Cream,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = { Column {
                        Text("StockFlow", fontWeight = FontWeight.Bold)
                        Text("Stok ve hareket yönetimi", style = MaterialTheme.typography.labelMedium)
                    } },
                    navigationIcon = {
                        if (route == "product/{productId}") {
                            TextButton(onClick = { nav.popBackStack() }) { Text("Geri") }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream),
                )
            },
            bottomBar = {
                if (route != "product/{productId}") {
                    NavigationBar(containerColor = Color.White) {
                        NavigationBarItem(selected = route == "products",
                            onClick = { nav.navigate("products") { popUpTo("products"); launchSingleTop = true } },
                            icon = { Text("▦") }, label = { Text("Ürünler") })
                        NavigationBarItem(selected = route == "categories",
                            onClick = { nav.navigate("categories") { popUpTo("products"); launchSingleTop = true } },
                            icon = { Text("≡") }, label = { Text("Kategoriler") })
                    }
                }
            },
        ) { padding ->
            NavHost(navController = nav, startDestination = "products", modifier = Modifier.padding(padding)) {
                composable("products") {
                    ProductsScreen(state, onAdd = {
                        vm.clearMessage(); editId = null; showProductForm = true
                    }, onOpen = { nav.navigate("product/$it") })
                }
                composable("categories") {
                    CategoriesScreen(state, onAdd = { vm.clearMessage(); showCategoryForm = true })
                }
                composable("product/{productId}") { entry ->
                    val productId = entry.arguments?.getString("productId").orEmpty()
                    val product = state.products.firstOrNull { it.id == productId }
                    if (product == null) {
                        Text(if (state.loading) "Yükleniyor…" else "Ürün bulunamadı.", Modifier.padding(24.dp))
                    } else {
                        ProductScreen(product, state.movements.filter { it.productId == productId }, busy,
                            onEdit = { vm.clearMessage(); editId = productId; showProductForm = true },
                            onMovement = { type, quantity, note, onSuccess ->
                                vm.recordMovement(productId, type, quantity, note, onSuccess)
                            })
                    }
                }
            }
        }
        if (showProductForm) {
            val product = state.products.firstOrNull { it.id == editId }
            ProductForm(product, state.categories, busy, message,
                onDismiss = { if (!busy) { showProductForm = false; vm.clearMessage() } },
                onSave = { name, categoryId ->
                    vm.saveProduct(editId, name, categoryId) { showProductForm = false; vm.clearMessage() }
                })
        }
        if (showCategoryForm) {
            var name by rememberSaveable { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { if (!busy) { showCategoryForm = false; vm.clearMessage() } },
                title = { Text("Yeni kategori") },
                text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Kategori adı") }, singleLine = true)
                    ErrorText(message)
                } },
                confirmButton = { Button(enabled = !busy, onClick = {
                    vm.createCategory(name) { showCategoryForm = false; vm.clearMessage() }
                }) { Text(if (busy) "Kaydediliyor…" else "Kaydet") } },
                dismissButton = { TextButton(enabled = !busy, onClick = { showCategoryForm = false; vm.clearMessage() }) { Text("Vazgeç") } },
            )
        }
    }
}

@Composable
private fun ProductsScreen(state: StockUiState, onAdd: () -> Unit, onOpen: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Stok görünümü", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item { LocalNotice() }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("${state.products.size} ürün", style = MaterialTheme.typography.titleMedium)
                Text("${state.products.sumOf { it.pendingCount }} hareket onay bekliyor", style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onAdd, enabled = !state.loading && state.readError == null) { Text("+ Ürün") }
        } }
        item { ErrorText(state.readError) }
        if (state.loading) item { CircularProgressIndicator() }
        if (!state.loading && state.products.isEmpty() && state.readError == null) {
            item { Text("İlk ürününü ekle. Ardından stok girişi yaparak başlayabilirsin.") }
        }
        items(state.products, key = { it.id }) { product ->
            Card(onClick = { onOpen(product.id) }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(18.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(product.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(product.categoryName ?: "Kategorisiz", style = MaterialTheme.typography.bodySmall)
                        if (product.pendingCount > 0) Text("${product.pendingCount} bekleyen hareket", color = Green, style = MaterialTheme.typography.labelMedium)
                    }
                    Column {
                        Text("${product.stock}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(if (product.stock == 0L) "Stok tükendi" else "adet", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoriesScreen(state: StockUiState, onAdd: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Kategoriler", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item { Text("Ürünlerini gruplandır.") }
        item { Button(onClick = onAdd, enabled = !state.loading && state.readError == null) { Text("+ Kategori") } }
        item { ErrorText(state.readError) }
        if (state.loading) item { CircularProgressIndicator() }
        if (!state.loading && state.categories.isEmpty()) item { Text("Henüz kategori eklenmedi.") }
        items(state.categories, key = { it.id }) { category ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text(category.name, style = MaterialTheme.typography.titleMedium)
                    Text("${state.products.count { it.categoryId == category.id }} ürün", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun ProductScreen(
    product: ProductStock,
    movements: List<StockMovementEntity>,
    busy: Boolean,
    onEdit: () -> Unit,
    onMovement: (MovementType, String, String, () -> Unit) -> Unit,
) {
    var type by rememberSaveable(product.id) { mutableStateOf(MovementType.IN) }
    var quantity by rememberSaveable(product.id) { mutableStateOf("") }
    var note by rememberSaveable(product.id) { mutableStateOf("") }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.imePadding()) {
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(product.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(product.categoryName ?: "Kategorisiz")
            }
            TextButton(onClick = onEdit, enabled = !busy) { Text("Düzenle") }
        } }
        item { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Green)) {
            Column(Modifier.padding(24.dp)) {
                Text("Cihazda görünen stok", color = Color.White.copy(alpha = 0.8f))
                Text("${product.stock} adet", color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                if (product.stock == 0L) Text("Stok tükendi", color = Color.White)
                Text("${product.pendingCount} hareket sunucu onayı bekliyor", color = Color.White, style = MaterialTheme.typography.bodySmall)
            }
        } }
        item { LocalNotice() }
        item { Text("Yeni stok hareketi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(selected = type == MovementType.IN, onClick = { type = MovementType.IN }, enabled = !busy, label = { Text("Giriş +") })
            FilterChip(selected = type == MovementType.OUT, onClick = { type = MovementType.OUT }, enabled = !busy, label = { Text("Çıkış −") })
        } }
        item { OutlinedTextField(value = quantity, onValueChange = { quantity = it }, enabled = !busy,
            label = { Text("Miktar (adet)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(value = note, onValueChange = { note = it }, enabled = !busy,
            label = { Text("Açıklama (isteğe bağlı)") }, placeholder = { Text("Örn. teslimat, satış veya fire") },
            maxLines = 3, modifier = Modifier.fillMaxWidth()) }
        item { Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
            onMovement(type, quantity, note) { quantity = ""; note = "" }
        }) { Text(if (busy) "Kaydediliyor…" else "Hareketi kaydet") } }
        item { Text("Hareket geçmişi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        if (movements.isEmpty()) item { Text("Henüz hareket yok. İlk stok girişini kaydet.") }
        items(movements, key = { it.id }) { movement ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${if (movement.type == MovementType.IN) "+" else "−"}${movement.quantity} adet · ${if (movement.type == MovementType.IN) "Giriş" else "Çıkış"}",
                        fontWeight = FontWeight.SemiBold)
                    if (movement.note.isNotBlank()) Text(movement.note)
                    Text(formatDate(movement.createdAt), style = MaterialTheme.typography.bodySmall)
                    Text(when (movement.syncState) {
                        SyncState.PENDING -> "Senkronizasyon bekliyor"
                        SyncState.SYNCED -> "Sunucu tarafından onaylandı"
                        SyncState.REJECTED -> "Reddedildi: ${movement.rejectionReason.orEmpty()}"
                    }, style = MaterialTheme.typography.labelMedium, color = Green)
                }
            }
        }
    }
}

@Composable
private fun ProductForm(
    product: ProductStock?, categories: List<CategoryEntity>, busy: Boolean,
    error: String?, onDismiss: () -> Unit, onSave: (String, String?) -> Unit,
) {
    var name by rememberSaveable(product?.id) { mutableStateOf(product?.name.orEmpty()) }
    var categoryId by rememberSaveable(product?.id) { mutableStateOf(product?.categoryId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (product == null) "Yeni ürün" else "Ürünü düzenle") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it }, enabled = !busy,
                label = { Text("Ürün adı") }, singleLine = true)
            Text("Kategori", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = categoryId == null, onClick = { categoryId = null }, enabled = !busy, label = { Text("Kategorisiz") })
                categories.forEach { category ->
                    FilterChip(selected = categoryId == category.id, onClick = { categoryId = category.id }, enabled = !busy, label = { Text(category.name) })
                }
            }
            if (product == null) Text("Başlangıç stoğu 0. Kaydettikten sonra stok girişi yapabilirsin.", style = MaterialTheme.typography.bodySmall)
            ErrorText(error)
        } },
        confirmButton = { Button(enabled = !busy, onClick = { onSave(name, categoryId) }) { Text(if (busy) "Kaydediliyor…" else "Kaydet") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Vazgeç") } },
    )
}

@Composable
private fun LocalNotice() {
    Text("Veriler bu cihazda saklanır. Sunucu senkronizasyonu henüz eklenmedi.",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.fillMaxWidth().background(Color(0xFFE1EDE4), MaterialTheme.shapes.medium).padding(12.dp))
}

@Composable
private fun ErrorText(message: String?) {
    if (message != null) Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

private fun formatDate(timestamp: Long): String = DateTimeFormatter
    .ofPattern("dd MMM yyyy · HH:mm", Locale.forLanguageTag("tr-TR"))
    .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(timestamp))
