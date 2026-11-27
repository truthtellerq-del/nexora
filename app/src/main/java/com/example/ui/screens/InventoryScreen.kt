package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.model.Product
import com.example.model.StockMovement
import com.example.ui.theme.Amber600
import com.example.ui.theme.Crimson600
import com.example.ui.theme.Emerald600
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.Navy900
import com.example.util.formatINR

@Composable
fun InventoryScreen(
    products: List<Product>,
    stockMovements: List<StockMovement>,
    initialLowStockOnly: Boolean,
    onAddProduct: (name: String, sku: String, unit: String, cost: Double, sell: Double, tax: Double, stock: Double, low: Double) -> Unit,
    onAdjustStock: (product: Product, delta: Double, reason: String) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var lowOnly by remember(initialLowStockOnly) { mutableStateOf(initialLowStockOnly) }
    var showAdd by remember { mutableStateOf(false) }
    var adjustTarget by remember { mutableStateOf<Product?>(null) }

    val filtered = remember(products, search, lowOnly) {
        products.filter { p ->
            if (lowOnly && p.stock > p.lowStockThreshold) return@filter false
            if (search.isNotBlank() && !p.name.contains(search, ignoreCase = true) && !p.sku.contains(search, ignoreCase = true)) return@filter false
            true
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Search product or SKU...") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("inv_search_input")
                )
                Button(
                    onClick = { showAdd = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Navy900),
                    modifier = Modifier.heightIn(min = 48.dp).testTag("inv_add_product_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("Item")
                }
            }
            FilterChip(
                selected = lowOnly,
                onClick = { lowOnly = !lowOnly },
                label = { Text("Low Stock Only") },
                modifier = Modifier.padding(top = 6.dp).testTag("inv_low_stock_chip")
            )
        }

        if (filtered.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                    Text("No products yet. Tap + Item to add your shop inventory.", modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(filtered, key = { it.id }) { p ->
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text("Cost: ${formatINR(p.costPrice)} | Sell: ${formatINR(p.sellPrice)} | SKU: ${p.sku.ifBlank { "-" }}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "${p.stock} ${p.unit}",
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.Bold,
                                color = if (p.stock <= p.lowStockThreshold) Amber600 else Emerald600
                            )
                            TextButton(onClick = { adjustTarget = p }) { Text("Adjust") }
                        }
                    }
                }
            }
        }

        item {
            Text("Recent Stock Movements", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
        }

        if (stockMovements.isEmpty()) {
            item {
                Text("No stock movements logged yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            items(stockMovements.sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }.take(15), key = { it.id }) { m ->
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("${m.productName} (${m.type})", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("${m.date} • ${m.reason}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            if (m.qty >= 0) "+${m.qty}" else "${m.qty}",
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.Bold,
                            color = if (m.qty >= 0) Emerald600 else Crimson600
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var sku by remember { mutableStateOf("") }
        var unit by remember { mutableStateOf("pcs") }
        var cost by remember { mutableStateOf("") }
        var sell by remember { mutableStateOf("") }
        var stock by remember { mutableStateOf("0") }
        var low by remember { mutableStateOf("5") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Add Product") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Product Name *") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("inv_prod_name_input"))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = sku, onValueChange = { sku = it }, label = { Text("SKU") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text("Unit (pcs/kg)") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = cost, onValueChange = { cost = it }, label = { Text("Cost ₹") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                        OutlinedTextField(value = sell, onValueChange = { sell = it }, label = { Text("Sell ₹ *") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f).testTag("inv_prod_sell_input"))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = stock, onValueChange = { stock = it }, label = { Text("Opening Stock") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                        OutlinedTextField(value = low, onValueChange = { low = it }, label = { Text("Low Alert") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddProduct(
                            name.trim(), sku.trim(), unit.trim().ifEmpty { "pcs" },
                            cost.toDoubleOrNull() ?: 0.0, sell.toDoubleOrNull() ?: 0.0, 0.0,
                            stock.toDoubleOrNull() ?: 0.0, low.toDoubleOrNull() ?: 5.0
                        )
                        showAdd = false
                    },
                    enabled = name.trim().isNotEmpty() && (sell.toDoubleOrNull() ?: -1.0) >= 0.0,
                    modifier = Modifier.testTag("inv_save_prod_button")
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } }
        )
    }

    if (adjustTarget != null) {
        val prod = adjustTarget!!
        var deltaStr by remember { mutableStateOf("") }
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adjustTarget = null },
            title = { Text("Adjust Stock: ${prod.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Current Stock: ${prod.stock} ${prod.unit}")
                    OutlinedTextField(value = deltaStr, onValueChange = { deltaStr = it }, label = { Text("Change (+/- e.g. 10 or -2)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Reason *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                val delta = deltaStr.toDoubleOrNull()
                Button(
                    onClick = {
                        if (delta != null) onAdjustStock(prod, delta, reason)
                        adjustTarget = null
                    },
                    enabled = delta != null && delta != 0.0 && reason.trim().isNotEmpty()
                ) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { adjustTarget = null }) { Text("Cancel") } }
        )
    }
}
