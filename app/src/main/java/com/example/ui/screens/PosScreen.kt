package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.i18n.stringsFor
import com.example.model.AppLanguage
import com.example.model.Business
import com.example.model.Customer
import com.example.model.Product
import com.example.model.Sale
import com.example.model.SaleItem
import com.example.ui.theme.Amber100
import com.example.ui.theme.Amber600
import com.example.ui.theme.Crimson600
import com.example.ui.theme.Emerald600
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.Navy900
import com.example.util.formatINR
import com.example.util.shareText

@Composable
fun PosScreen(
    language: AppLanguage,
    business: Business,
    products: List<Product>,
    customers: List<Customer>,
    sales: List<Sale>,
    onCompleteSale: (
        items: List<SaleItem>, subtotal: Double, tax: Double, discount: Double, total: Double,
        totalCost: Double, paymentMode: String, paidNow: Double, udhaarAmount: Double,
        customerId: String?, customerName: String, onDone: (Sale) -> Unit
    ) -> Unit,
    onReturnSale: (Sale) -> Unit,
    onErrorMsg: (String) -> Unit
) {
    val t = stringsFor(language)
    val context = LocalContext.current

    var tab by remember { mutableStateOf("billing") } // billing | history
    var query by remember { mutableStateOf("") }
    var cart by remember { mutableStateOf<List<SaleItem>>(emptyList()) }
    var discountStr by remember { mutableStateOf("0") }
    var paymentMode by remember { mutableStateOf("Cash") }
    var splitPaidStr by remember { mutableStateOf("0") }
    var selectedCustomerId by remember { mutableStateOf<String?>(null) }
    var invoiceSale by remember { mutableStateOf<Sale?>(null) }

    val filteredProducts = remember(products, query) {
        val q = query.trim()
        if (q.isEmpty()) products
        else products.filter { it.name.contains(q, ignoreCase = true) || it.sku.contains(q, ignoreCase = true) }
    }

    val subtotal = cart.sumOf { it.qty * it.price }
    val tax = cart.sumOf { it.qty * it.price * it.taxRate / 100.0 }
    val discount = maxOf(0.0, discountStr.toDoubleOrNull() ?: 0.0)
    val total = maxOf(0.0, subtotal + tax - discount)
    val totalCost = cart.sumOf { it.qty * it.costPrice }

    fun addProductToCart(prod: Product) {
        if (business.negativeStockProtection && prod.stock <= 0.0) {
            onErrorMsg("${prod.name}: ${t.negativeStockErr}")
            return
        }
        val existing = cart.firstOrNull { it.productId == prod.id }
        if (existing != null) {
            val nextQty = existing.qty + 1.0
            if (business.negativeStockProtection && nextQty > prod.stock) {
                onErrorMsg("${prod.name}: ${t.negativeStockErr}")
                return
            }
            cart = cart.map { if (it.productId == prod.id) it.copy(qty = nextQty) else it }
        } else {
            cart = cart + SaleItem(
                productId = prod.id,
                name = prod.name,
                unit = prod.unit,
                qty = 1.0,
                price = prod.sellPrice,
                costPrice = prod.costPrice,
                taxRate = prod.taxRate
            )
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = tab == "billing",
                    onClick = { tab = "billing" },
                    label = { Text("POS Billing") },
                    modifier = Modifier.testTag("pos_tab_billing")
                )
                FilterChip(
                    selected = tab == "history",
                    onClick = { tab = "history" },
                    label = { Text("Sales & Returns (${sales.size})") },
                    modifier = Modifier.testTag("pos_tab_history")
                )
            }
        }

        if (tab == "billing") {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search product name or SKU...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("pos_search_input")
                )
            }

            if (filteredProducts.isEmpty()) {
                item {
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                        Text("No products found. Add products in Stock/Inventory first.", modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(filteredProducts, key = { it.id }) { prod ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        modifier = Modifier.fillMaxWidth().clickable { addProductToCart(prod) }.testTag("pos_product_${prod.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(prod.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Text("SKU: ${prod.sku.ifBlank { "-" }}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Surface(
                                    color = if (prod.stock <= prod.lowStockThreshold) Amber100 else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        "${prod.stock} ${prod.unit}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (prod.stock <= prod.lowStockThreshold) Amber600 else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Text(formatINR(prod.sellPrice), fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                                Icon(Icons.Default.Add, contentDescription = "Add to cart", tint = Emerald600)
                            }
                        }
                    }
                }
            }

            // Current Bill Summary Card
            item {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Current Bill (${cart.size} items)", style = MaterialTheme.typography.titleMedium)
                        if (cart.isEmpty()) {
                            Text("Tap products above to add to bill.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            cart.forEach { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text("${formatINR(item.price)} / ${item.unit}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        OutlinedButton(onClick = {
                                            val next = item.qty - 1.0
                                            cart = if (next <= 0.0) cart.filterNot { it.productId == item.productId }
                                            else cart.map { if (it.productId == item.productId) it.copy(qty = next) else it }
                                        }) { Text("-") }
                                        Text("${item.qty}", fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                                        OutlinedButton(onClick = {
                                            val prod = products.firstOrNull { it.id == item.productId }
                                            if (prod != null && business.negativeStockProtection && item.qty + 1.0 > prod.stock) {
                                                onErrorMsg(t.negativeStockErr)
                                            } else {
                                                cart = cart.map { if (it.productId == item.productId) it.copy(qty = it.qty + 1.0) else it }
                                            }
                                        }) { Text("+") }
                                        IconButton(onClick = { cart = cart.filterNot { it.productId == item.productId } }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Crimson600)
                                        }
                                    }
                                }
                            }

                            HorizontalDivider()
                            OutlinedTextField(
                                value = discountStr,
                                onValueChange = { discountStr = it },
                                label = { Text("Discount (₹)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text("Payment Mode", style = MaterialTheme.typography.labelLarge)
                            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("Cash", "UPI", "Card", "Udhaar", "Split").forEach { mode ->
                                    FilterChip(
                                        selected = paymentMode == mode,
                                        onClick = { paymentMode = mode },
                                        label = { Text(mode) }
                                    )
                                }
                            }

                            if (customers.isNotEmpty() || paymentMode == "Udhaar" || paymentMode == "Split") {
                                Text("Customer ${if (paymentMode == "Udhaar" || paymentMode == "Split") "(Required for Udhaar)" else "(Optional)"}", style = MaterialTheme.typography.labelSmall)
                                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = selectedCustomerId == null,
                                        onClick = { selectedCustomerId = null },
                                        label = { Text("Walk-in") }
                                    )
                                    customers.forEach { c ->
                                        FilterChip(
                                            selected = selectedCustomerId == c.id,
                                            onClick = { selectedCustomerId = c.id },
                                            label = { Text(c.name) }
                                        )
                                    }
                                }
                            }

                            if (paymentMode == "Split") {
                                OutlinedTextField(
                                    value = splitPaidStr,
                                    onValueChange = { splitPaidStr = it },
                                    label = { Text("Paid Now (₹) — Remaining goes to Khata") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(formatINR(total), fontFamily = JetBrainsMono, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Emerald600)
                            }

                            Button(
                                onClick = {
                                    if ((paymentMode == "Udhaar" || paymentMode == "Split") && selectedCustomerId == null) {
                                        onErrorMsg("Select a customer for Udhaar / Split payment.")
                                        return@Button
                                    }
                                    val paidNow = when (paymentMode) {
                                        "Udhaar" -> 0.0
                                        "Split" -> (splitPaidStr.toDoubleOrNull() ?: 0.0).coerceIn(0.0, total)
                                        else -> total
                                    }
                                    val udhaarAmt = maxOf(0.0, total - paidNow)
                                    val cust = customers.firstOrNull { it.id == selectedCustomerId }
                                    onCompleteSale(
                                        cart, subtotal, tax, discount, total, totalCost, paymentMode,
                                        paidNow, udhaarAmt, cust?.id, cust?.name ?: "Walk-in Customer"
                                    ) { completedSale ->
                                        invoiceSale = completedSale
                                        cart = emptyList()
                                        discountStr = "0"
                                        splitPaidStr = "0"
                                    }
                                },
                                enabled = cart.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("pos_checkout_button")
                            ) {
                                Text("${t.checkout} (${formatINR(total)})")
                            }
                        }
                    }
                }
            }
        } else {
            if (sales.isEmpty()) {
                item {
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                        Text("No sales recorded yet.", modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(sales.sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }, key = { it.id }) { sale ->
                    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(14.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${sale.invoiceNo} • ${sale.customerName}", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${sale.date} • ${sale.paymentMode}${if (sale.status == "returned") " • RETURNED" else ""}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (sale.status == "returned") Crimson600 else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(formatINR(sale.total), fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                                Row {
                                    TextButton(onClick = { invoiceSale = sale }) { Text("Invoice") }
                                    if (sale.status != "returned") {
                                        TextButton(onClick = { onReturnSale(sale) }) { Text("Return", color = Crimson600) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }

    if (invoiceSale != null) {
        val inv = invoiceSale!!
        AlertDialog(
            onDismissRequest = { invoiceSale = null },
            title = { Text("Invoice ${inv.invoiceNo}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${business.name} (${business.city})", fontWeight = FontWeight.Bold)
                    if (business.gstin.isNotBlank()) Text("GSTIN: ${business.gstin}", style = MaterialTheme.typography.labelSmall)
                    Text("Date: ${inv.date} • Customer: ${inv.customerName}", style = MaterialTheme.typography.labelSmall)
                    HorizontalDivider()
                    inv.items.forEach { it ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${it.name} (${it.qty} ${it.unit})", style = MaterialTheme.typography.bodyMedium)
                            Text(formatINR(it.qty * it.price), fontFamily = JetBrainsMono, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    HorizontalDivider()
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total (${inv.paymentMode})", fontWeight = FontWeight.Bold)
                        Text(formatINR(inv.total), fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                    }
                    if (inv.udhaarAmount > 0) {
                        Text("Added to Khata: ${formatINR(inv.udhaarAmount)}", color = Crimson600, style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val text = buildString {
                        appendLine("${business.name} - Invoice ${inv.invoiceNo}")
                        appendLine("Date: ${inv.date} | Customer: ${inv.customerName}")
                        inv.items.forEach { appendLine("- ${it.name} x${it.qty}: ${formatINR(it.qty * it.price)}") }
                        appendLine("Total: ${formatINR(inv.total)} (${inv.paymentMode})")
                    }
                    shareText(context, "Invoice ${inv.invoiceNo}", text)
                }) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Text(" Share")
                }
            },
            dismissButton = { TextButton(onClick = { invoiceSale = null }) { Text("Close") } }
        )
    }
}
