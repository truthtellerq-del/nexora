package com.example.ui.screens

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.example.model.AttendanceRecord
import com.example.model.Branch
import com.example.model.Business
import com.example.model.Customer
import com.example.model.Expense
import com.example.model.KhataEntry
import com.example.model.Product
import com.example.model.Purchase
import com.example.model.Sale
import com.example.model.ShopTask
import com.example.model.StaffMember
import com.example.model.SubscriptionInfo
import com.example.model.SubscriptionPlan
import com.example.model.Supplier
import com.example.ui.theme.Crimson600
import com.example.ui.theme.Emerald600
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.Navy900
import com.example.util.buildCsv
import com.example.util.formatINR
import com.example.util.localDateStr
import com.example.util.shareText

@Composable
fun PurchasesScreen(
    suppliers: List<Supplier>,
    purchases: List<Purchase>,
    products: List<Product>,
    onAddSupplier: (String, String) -> Unit,
    onAddPurchase: (supplierId: String?, supplierName: String, product: Product?, productName: String, qty: Double, total: Double, paid: Double, date: String) -> Unit
) {
    var showAddSup by remember { mutableStateOf(false) }
    var selectedSupId by remember { mutableStateOf<String?>(null) }
    var selectedProdId by remember { mutableStateOf<String?>(null) }
    var qtyStr by remember { mutableStateOf("1") }
    var totalStr by remember { mutableStateOf("") }
    var paidStr by remember { mutableStateOf("") }
    var dateStr by remember { mutableStateOf(localDateStr()) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Spacer(Modifier.height(4.dp))
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Record Purchase (Kharidi)", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { showAddSup = true }) { Text("+ New Supplier") }
                    }
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = selectedSupId == null, onClick = { selectedSupId = null }, label = { Text("Cash Supplier") })
                        suppliers.forEach { s ->
                            FilterChip(selected = selectedSupId == s.id, onClick = { selectedSupId = s.id }, label = { Text(s.name) })
                        }
                    }
                    if (products.isNotEmpty()) {
                        Text("Link Inventory Item (auto adds stock):", style = MaterialTheme.typography.labelSmall)
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = selectedProdId == null, onClick = { selectedProdId = null }, label = { Text("General Stock") })
                            products.forEach { p ->
                                FilterChip(selected = selectedProdId == p.id, onClick = { selectedProdId = p.id }, label = { Text(p.name) })
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = qtyStr, onValueChange = { qtyStr = it }, label = { Text("Qty") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                        OutlinedTextField(value = totalStr, onValueChange = { totalStr = it }, label = { Text("Total Bill ₹ *") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = paidStr, onValueChange = { paidStr = it }, label = { Text("Paid Now ₹") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                        OutlinedTextField(value = dateStr, onValueChange = { dateStr = it }, label = { Text("Date") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Button(
                        onClick = {
                            val sup = suppliers.firstOrNull { it.id == selectedSupId }
                            val prod = products.firstOrNull { it.id == selectedProdId }
                            val tot = totalStr.toDoubleOrNull() ?: 0.0
                            val paid = paidStr.toDoubleOrNull() ?: tot
                            onAddPurchase(sup?.id, sup?.name ?: "Cash Supplier", prod, prod?.name ?: "General Stock", qtyStr.toDoubleOrNull() ?: 1.0, tot, paid, dateStr)
                            totalStr = ""
                            paidStr = ""
                        },
                        enabled = (totalStr.toDoubleOrNull() ?: 0.0) > 0.0,
                        colors = ButtonDefaults.buttonColors(containerColor = Navy900),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) { Text("Save Purchase") }
                }
            }
        }

        item { Text("Purchase History (${purchases.size})", style = MaterialTheme.typography.titleMedium) }
        items(purchases.sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }, key = { it.id }) { p ->
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("${p.supplierName} • ${p.productName} (${p.qty})", fontWeight = FontWeight.SemiBold)
                        Text(p.date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(formatINR(p.total), fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
                        val due = maxOf(0.0, p.total - p.paid)
                        if (due > 0) Text("Due: ${formatINR(due)}", style = MaterialTheme.typography.labelSmall, color = Crimson600)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }

    if (showAddSup) {
        var sName by remember { mutableStateOf("") }
        var sPhone by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddSup = false },
            title = { Text("Add Supplier") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = sName, onValueChange = { sName = it }, label = { Text("Supplier Name *") }, singleLine = true)
                    OutlinedTextField(value = sPhone, onValueChange = { sPhone = it }, label = { Text("Phone") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = { onAddSupplier(sName.trim(), sPhone.trim()); showAddSup = false }, enabled = sName.trim().isNotEmpty()) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAddSup = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun ExpensesScreen(
    expenses: List<Expense>,
    onAddExpense: (category: String, amount: Double, note: String, date: String) -> Unit
) {
    var category by remember { mutableStateOf("Rent") }
    var amountStr by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var dateStr by remember { mutableStateOf(localDateStr()) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Spacer(Modifier.height(4.dp))
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Add Operating Expense", style = MaterialTheme.typography.titleMedium)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Rent", "Electricity", "Salary", "Transport", "Packaging", "Other").forEach { cat ->
                            FilterChip(selected = category == cat, onClick = { category = cat }, label = { Text(cat) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = amountStr, onValueChange = { amountStr = it }, label = { Text("Amount ₹ *") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                        OutlinedTextField(value = dateStr, onValueChange = { dateStr = it }, label = { Text("Date") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Notes") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            onAddExpense(category, amountStr.toDoubleOrNull() ?: 0.0, note.trim(), dateStr)
                            amountStr = ""
                            note = ""
                        },
                        enabled = (amountStr.toDoubleOrNull() ?: 0.0) > 0.0,
                        colors = ButtonDefaults.buttonColors(containerColor = Navy900),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) { Text("Save Expense") }
                }
            }
        }

        item { Text("Expense Log (${expenses.size})", style = MaterialTheme.typography.titleMedium) }
        items(expenses.sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }, key = { it.id }) { e ->
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(e.category, fontWeight = FontWeight.SemiBold)
                        Text("${e.date} • ${e.note.ifBlank { "-" }}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(formatINR(e.amount), fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold, color = Crimson600)
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
fun StaffBranchesScreen(
    branches: List<Branch>,
    staff: List<StaffMember>,
    attendance: List<AttendanceRecord>,
    onAddBranch: (String, String) -> Unit,
    onAddStaff: (String, String, String, Double) -> Unit,
    onMarkAttendance: (staffId: String, date: String, status: String) -> Unit
) {
    var brName by remember { mutableStateOf("") }
    var brCity by remember { mutableStateOf("") }
    var stName by remember { mutableStateOf("") }
    var stRole by remember { mutableStateOf("Cashier") }
    var stPhone by remember { mutableStateOf("") }
    val today = remember { localDateStr() }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Spacer(Modifier.height(4.dp))
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Branches (${branches.size})", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = brName, onValueChange = { brName = it }, label = { Text("Branch Name") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = brCity, onValueChange = { brCity = it }, label = { Text("City") }, singleLine = true, modifier = Modifier.weight(1f))
                        Button(onClick = { onAddBranch(brName.trim(), brCity.trim()); brName = ""; brCity = "" }, enabled = brName.isNotBlank() && brCity.isNotBlank()) { Text("Add") }
                    }
                    branches.forEach { b ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(b.name, fontWeight = FontWeight.SemiBold)
                            Text(b.city, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Staff & Attendance ($today)", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = stName, onValueChange = { stName = it }, label = { Text("Staff Name") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = stPhone, onValueChange = { stPhone = it }, label = { Text("Phone") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Cashier", "Helper", "Manager").forEach { r ->
                            FilterChip(selected = stRole == r, onClick = { stRole = r }, label = { Text(r) })
                        }
                    }
                    Button(
                        onClick = { onAddStaff(stName.trim(), stRole, stPhone.trim(), 0.0); stName = ""; stPhone = "" },
                        enabled = stName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("+ Add Staff") }
                }
            }
        }

        items(staff, key = { it.id }) { member ->
            val rec = attendance.firstOrNull { it.staffId == member.id && it.date == today }
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(member.name, fontWeight = FontWeight.SemiBold)
                        Text("${member.role} • ${member.phone.ifBlank { "No phone" }}", style = MaterialTheme.typography.labelSmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("present", "half", "absent").forEach { st ->
                            FilterChip(
                                selected = rec?.status == st,
                                onClick = { onMarkAttendance(member.id, today, st) },
                                label = { Text(st.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
fun TasksScreen(
    tasks: List<ShopTask>,
    onAddTask: (String, String) -> Unit,
    onToggleTask: (ShopTask) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf(localDateStr()) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Spacer(Modifier.height(4.dp))
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tasks & Reminders", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("e.g. Call Mohan for overdue udhaar") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = dueDate, onValueChange = { dueDate = it }, label = { Text("Due Date") }, singleLine = true, modifier = Modifier.weight(1f))
                        Button(onClick = { onAddTask(title.trim(), dueDate); title = "" }, enabled = title.isNotBlank()) { Text("Add") }
                    }
                }
            }
        }
        items(tasks, key = { it.id }) { tk ->
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(10.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Checkbox(checked = tk.completed, onCheckedChange = { onToggleTask(tk) })
                        Text(tk.title, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(tk.dueDate, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
fun SettingsScreen(
    language: AppLanguage,
    business: Business,
    subInfo: SubscriptionInfo,
    customers: List<Customer>,
    khataEntries: List<KhataEntry>,
    sales: List<Sale>,
    products: List<Product>,
    expenses: List<Expense>,
    onLanguageChange: (AppLanguage) -> Unit,
    onToggleNegativeStock: (Boolean) -> Unit,
    onOpenUpgrade: () -> Unit,
    onAddBusiness: () -> Unit,
    onSignOut: () -> Unit
) {
    val t = stringsFor(language)
    val context = LocalContext.current

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Spacer(Modifier.height(4.dp))
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Shop & Preferences", style = MaterialTheme.typography.titleMedium)
                    Text("${business.name} • ${business.city}", style = MaterialTheme.typography.bodyMedium)
                    Text(t.language, style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppLanguage.entries.forEach { lang ->
                            FilterChip(selected = language == lang, onClick = { onLanguageChange(lang) }, label = { Text(lang.displayName) })
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Strict Negative Stock Protection", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = business.negativeStockProtection, onCheckedChange = onToggleNegativeStock)
                    }
                    Text("Plan: ${subInfo.plan.name} (${if (subInfo.expired) "Expired" else "${subInfo.daysLeft} days left"})", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onOpenUpgrade, colors = ButtonDefaults.buttonColors(containerColor = Emerald600)) { Text(t.upgradeNow) }
                        OutlinedButton(onClick = onAddBusiness) { Text("+ New Shop") }
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${t.exportCsv} (Always Available)", style = MaterialTheme.typography.titleMedium)
                    Text("Your business records belong to you. Export CSV anytime, even if your trial expires.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                val csv = buildCsv(listOf("Name", "Phone", "OpeningBalance"), customers.map { listOf(it.name, it.phone, it.openingBalance) })
                                shareText(context, "${business.name} Customers CSV", csv)
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Customers CSV") }
                        OutlinedButton(
                            onClick = {
                                val csv = buildCsv(listOf("Date", "Customer", "Type", "Amount", "Note"), khataEntries.map { listOf(it.date, it.customerName, it.type, it.amount, it.note) })
                                shareText(context, "${business.name} Khata CSV", csv)
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Khata CSV") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                val csv = buildCsv(listOf("Invoice", "Date", "Customer", "Mode", "Total"), sales.map { listOf(it.invoiceNo, it.date, it.customerName, it.paymentMode, it.total) })
                                shareText(context, "${business.name} Sales CSV", csv)
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Sales CSV") }
                        OutlinedButton(
                            onClick = {
                                val csv = buildCsv(listOf("Name", "SKU", "Unit", "Cost", "Sell", "Stock"), products.map { listOf(it.name, it.sku, it.unit, it.costPrice, it.sellPrice, it.stock) })
                                shareText(context, "${business.name} Inventory CSV", csv)
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Stock CSV") }
                    }
                    OutlinedButton(
                        onClick = {
                            val csv = buildCsv(listOf("Date", "Category", "Amount", "Note"), expenses.map { listOf(it.date, it.category, it.amount, it.note) })
                            shareText(context, "${business.name} Expenses CSV", csv)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Expenses CSV") }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = onSignOut,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Crimson600),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("settings_sign_out_button")
            ) { Text(t.logout) }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
fun SuperAdminScreen(
    plans: List<SubscriptionPlan>,
    activeBusiness: Business?,
    onSavePlans: (List<SubscriptionPlan>) -> Unit,
    onSetBizSubscription: (planId: String, status: String, trialEndMillis: Long?, planEndMillis: Long?) -> Unit
) {
    var editablePlans by remember(plans) { mutableStateOf(plans.sortedBy { it.sortOrder }) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Spacer(Modifier.height(4.dp))
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Super Admin — Dynamic SaaS Plans", style = MaterialTheme.typography.titleMedium)
                    editablePlans.forEachIndexed { idx, plan ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(plan.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            OutlinedTextField(
                                value = plan.price.toInt().toString(),
                                onValueChange = { v ->
                                    val num = v.toDoubleOrNull() ?: 0.0
                                    editablePlans = editablePlans.toMutableList().also { it[idx] = plan.copy(price = num) }
                                },
                                label = { Text("Price ₹") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = plan.billingDays.toString(),
                                onValueChange = { v ->
                                    val days = v.toLongOrNull() ?: 30L
                                    editablePlans = editablePlans.toMutableList().also { it[idx] = plan.copy(billingDays = days) }
                                },
                                label = { Text("Days") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(0.8f)
                            )
                        }
                    }
                    Button(
                        onClick = { onSavePlans(editablePlans) },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Save All Plan Prices Globally") }
                }
            }
        }

        if (activeBusiness != null) {
            item {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Active Shop Subscription Controls (${activeBusiness.name})", style = MaterialTheme.typography.titleMedium)
                        Text("Current: ${activeBusiness.planId.uppercase()} (${activeBusiness.subscriptionStatus})", style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { onSetBizSubscription("trial", "expired", System.currentTimeMillis() - 3_600_000L, null) },
                                modifier = Modifier.weight(1f)
                            ) { Text("Expire Trial (Test Gate)", color = Crimson600) }
                            OutlinedButton(
                                onClick = { onSetBizSubscription("trial", "trialing", System.currentTimeMillis() + 7L * 86_400_000L, null) },
                                modifier = Modifier.weight(1f)
                            ) { Text("Reset 7d Trial") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { onSetBizSubscription("starter", "active", null, System.currentTimeMillis() + 30L * 86_400_000L) },
                                modifier = Modifier.weight(1f)
                            ) { Text("Activate Starter") }
                            Button(
                                onClick = { onSetBizSubscription("pro", "active", null, System.currentTimeMillis() + 30L * 86_400_000L) },
                                modifier = Modifier.weight(1f)
                            ) { Text("Activate Pro") }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}
