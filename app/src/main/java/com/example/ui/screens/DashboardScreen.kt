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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.example.i18n.stringsFor
import com.example.model.AppLanguage
import com.example.model.Customer
import com.example.model.Expense
import com.example.model.KhataEntry
import com.example.model.Product
import com.example.model.Purchase
import com.example.model.Sale
import com.example.ui.theme.Amber100
import com.example.ui.theme.Amber600
import com.example.ui.theme.Crimson600
import com.example.ui.theme.Emerald50
import com.example.ui.theme.Emerald600
import com.example.ui.theme.JetBrainsMono
import com.example.ui.viewmodel.NavSection
import com.example.util.computeCustomerLedger
import com.example.util.formatINR
import com.example.util.localDateStr
import com.example.util.shiftDays

private data class RecentTx(
    val id: String,
    val kind: String,
    val label: String,
    val amount: Double,
    val date: String,
    val timestamp: Long,
    val target: NavSection
)

@Composable
fun DashboardScreen(
    language: AppLanguage,
    sales: List<Sale>,
    purchases: List<Purchase>,
    expenses: List<Expense>,
    customers: List<Customer>,
    khataEntries: List<KhataEntry>,
    products: List<Product>,
    onNavigate: (NavSection) -> Unit
) {
    val t = stringsFor(language)
    val today = remember { localDateStr() }
    var preset by remember { mutableStateOf("today") }
    var customFrom by remember { mutableStateOf(today) }
    var customTo by remember { mutableStateOf(today) }

    val (fromDate, toDate) = remember(preset, today, customFrom, customTo) {
        when (preset) {
            "today" -> today to today
            "yesterday" -> { val y = shiftDays(today, -1); y to y }
            "week" -> shiftDays(today, -6) to today
            "month" -> (today.take(8) + "01") to today
            else -> customFrom to customTo
        }
    }

    val periodSales = remember(sales, fromDate, toDate) {
        sales.filter { it.status != "returned" && it.date in fromDate..toDate }
    }
    val periodPurchases = remember(purchases, fromDate, toDate) {
        purchases.filter { it.date in fromDate..toDate }
    }
    val periodExpenses = remember(expenses, fromDate, toDate) {
        expenses.filter { it.date in fromDate..toDate }
    }

    val bikri = periodSales.sumOf { it.total }
    val cogs = periodSales.sumOf { it.totalCost }
    val kharidi = periodPurchases.sumOf { it.total }
    val opExpenses = periodExpenses.sumOf { it.amount }
    val grossMargin = bikri - cogs
    val netProfit = grossMargin - opExpenses

    val totalPendingUdhaar = remember(customers, khataEntries) {
        customers.sumOf { maxOf(0.0, computeCustomerLedger(it, khataEntries).closingBalance) }
    }
    val lowStock = remember(products) { products.filter { it.stock <= it.lowStockThreshold } }

    val recentTx = remember(sales, purchases, expenses, khataEntries, fromDate, toDate) {
        val list = mutableListOf<RecentTx>()
        sales.filter { it.date in fromDate..toDate }.forEach {
            list += RecentTx(it.id, "Sale", it.customerName, it.total, it.date, it.createdAt?.toDate()?.time ?: 0L, NavSection.Pos)
        }
        purchases.filter { it.date in fromDate..toDate }.forEach {
            list += RecentTx(it.id, "Purchase", it.supplierName, it.total, it.date, it.createdAt?.toDate()?.time ?: 0L, NavSection.Purchases)
        }
        expenses.filter { it.date in fromDate..toDate }.forEach {
            list += RecentTx(it.id, "Expense", it.category, it.amount, it.date, it.createdAt?.toDate()?.time ?: 0L, NavSection.Expenses)
        }
        khataEntries.filter { it.date in fromDate..toDate }.forEach {
            list += RecentTx(it.id, if (it.type == "payment") "Khata Pay" else "Khata", it.customerName, it.amount, it.date, it.createdAt?.toDate()?.time ?: 0L, NavSection.Khata())
        }
        list.sortedByDescending { it.timestamp }.take(10)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "today" to t.today,
                    "yesterday" to t.yesterday,
                    "week" to t.thisWeek,
                    "month" to t.thisMonth,
                    "custom" to t.custom
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = preset == key,
                        onClick = { preset = key },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("dash_preset_$key")
                    )
                }
            }
            if (preset == "custom") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    OutlinedTextField(
                        value = customFrom,
                        onValueChange = { customFrom = it },
                        label = { Text("From YYYY-MM-DD") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = customTo,
                        onValueChange = { customTo = it },
                        label = { Text("To YYYY-MM-DD") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Primary 2x2 KPI Grid
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                KpiCard(
                    title = t.todaySales,
                    value = formatINR(bikri),
                    sub = "${periodSales.size} bills",
                    valueColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f).testTag("kpi_sales_card"),
                    onClick = { onNavigate(NavSection.Pos) }
                )
                KpiCard(
                    title = t.todayPurchases,
                    value = formatINR(kharidi),
                    sub = "${periodPurchases.size} bills",
                    valueColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f).testTag("kpi_purchases_card"),
                    onClick = { onNavigate(NavSection.Purchases) }
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                KpiCard(
                    title = t.todayProfit,
                    value = formatINR(netProfit),
                    sub = "${t.grossMargin}: ${formatINR(grossMargin)}",
                    valueColor = if (netProfit >= 0) Emerald600 else Crimson600,
                    modifier = Modifier.weight(1f).testTag("kpi_profit_card"),
                    onClick = { onNavigate(NavSection.Expenses) }
                )
                KpiCard(
                    title = t.pendingUdhaar,
                    value = formatINR(totalPendingUdhaar),
                    sub = "${customers.size} ${t.customersCount}",
                    valueColor = Crimson600,
                    modifier = Modifier.weight(1f).testTag("kpi_udhaar_card"),
                    onClick = { onNavigate(NavSection.Khata("outstanding")) }
                )
            }
        }

        // Profit Transparency Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Profit Transparency", style = MaterialTheme.typography.titleMedium)
                    MetricLine("Sales Revenue", formatINR(bikri))
                    MetricLine("Less: Cost of Goods Sold (COGS)", "- ${formatINR(cogs)}", MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider()
                    MetricLine(t.grossMargin, formatINR(grossMargin), Emerald600, bold = true)
                    MetricLine("Less: ${t.operatingExpenses}", "- ${formatINR(opExpenses)}", MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider()
                    MetricLine(t.todayProfit, formatINR(netProfit), if (netProfit >= 0) Emerald600 else Crimson600, bold = true)
                }
            }
        }

        // Low Stock Alert Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(NavSection.Inventory(lowStockOnly = true)) }
                    .testTag("dash_low_stock_card")
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(t.lowStockItems, style = MaterialTheme.typography.titleMedium)
                        Surface(
                            color = if (lowStock.isNotEmpty()) Amber100 else Emerald50,
                            shape = RoundedCornerShape(99.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (lowStock.isNotEmpty()) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = Amber600, modifier = Modifier.padding(end = 2.dp))
                                }
                                Text(
                                    "${lowStock.size} Alerts",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (lowStock.isNotEmpty()) Amber600 else Emerald600
                                )
                            }
                        }
                    }
                    if (lowStock.isEmpty()) {
                        Text("All ${products.size} products have healthy stock levels.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        lowStock.take(4).forEach { prod ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(prod.name, style = MaterialTheme.typography.bodyMedium)
                                Text("${prod.stock} ${prod.unit}", fontFamily = JetBrainsMono, color = Amber600, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }

        // Recent Transactions
        item {
            Text(t.recentTransactions, style = MaterialTheme.typography.titleMedium)
        }

        if (recentTx.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(t.noDataYet, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(
                            onClick = { onNavigate(NavSection.Pos) },
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                            modifier = Modifier.testTag("dash_empty_quick_sale_button")
                        ) {
                            Text("+ ${t.quickSale}")
                        }
                    }
                }
            }
        } else {
            items(recentTx, key = { it.id }) { tx ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigate(tx.target) }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(tx.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text("${tx.kind} • ${tx.date}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(formatINR(tx.amount), fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun KpiCard(
    title: String,
    value: String,
    sub: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontFamily = JetBrainsMono, style = MaterialTheme.typography.titleLarge, color = valueColor, fontWeight = FontWeight.Bold)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MetricLine(label: String, value: String, color: Color = MaterialTheme.colorScheme.onSurface, bold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(value, fontFamily = JetBrainsMono, style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium)
    }
}
