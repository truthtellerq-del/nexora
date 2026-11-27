package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
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
import com.example.model.CustomerLedgerSummary
import com.example.model.KhataEntry
import com.example.ui.theme.Crimson100
import com.example.ui.theme.Crimson600
import com.example.ui.theme.Emerald50
import com.example.ui.theme.Emerald600
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.Navy900
import com.example.util.computeCustomerLedger
import com.example.util.formatINR
import com.example.util.localDateStr
import com.example.util.shareText

@Composable
fun KhataScreen(
    language: AppLanguage,
    business: Business,
    customers: List<Customer>,
    khataEntries: List<KhataEntry>,
    initialFilter: String,
    onAddCustomer: (name: String, phone: String, openingBal: Double, overdueDays: Long) -> Unit,
    onAddKhataEntry: (customerId: String, customerName: String, type: String, amount: Double, note: String, date: String, refEntryId: String?) -> Unit
) {
    val t = stringsFor(language)
    val context = LocalContext.current

    var search by remember { mutableStateOf("") }
    var filter by remember(initialFilter) { mutableStateOf(initialFilter) }
    var selectedCustomerId by remember { mutableStateOf<String?>(null) }

    var showAddCustomer by remember { mutableStateOf(false) }
    var entryDialogType by remember { mutableStateOf<String?>(null) } // "credit" | "payment"
    var reversalTarget by remember { mutableStateOf<KhataEntry?>(null) }

    val summaries = remember(customers, khataEntries) {
        customers.map { computeCustomerLedger(it, khataEntries) }
    }

    val filtered = remember(summaries, search, filter) {
        summaries.filter { s ->
            val matchSearch = search.isBlank() ||
                s.customer.name.contains(search, ignoreCase = true) ||
                s.customer.phone.contains(search, ignoreCase = true)
            if (!matchSearch) return@filter false
            when (filter) {
                "outstanding" -> s.closingBalance > 0.0
                "paid" -> s.closingBalance <= 0.0
                "overdue" -> s.overdueFlag
                else -> true
            }
        }
    }

    val activeSummary = remember(summaries, selectedCustomerId) {
        summaries.firstOrNull { it.customer.id == selectedCustomerId }
    }

    if (activeSummary != null) {
        BackHandler { selectedCustomerId = null }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 720.dp

        if (isWide) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.weight(0.45f)) {
                    KhataListPane(
                        banner = t.singleKhataBanner,
                        searchHint = t.search,
                        search = search,
                        onSearchChange = { search = it },
                        filter = filter,
                        onFilterChange = { filter = it },
                        filterLabels = listOf("all" to t.all, "outstanding" to t.outstanding, "paid" to t.paid, "overdue" to t.overdue),
                        filtered = filtered,
                        selectedId = activeSummary?.customer?.id,
                        onSelect = { selectedCustomerId = it },
                        onAddCustomerClick = { showAddCustomer = true }
                    )
                }
                Column(modifier = Modifier.weight(0.55f)) {
                    if (activeSummary != null) {
                        KhataDetailPane(
                            summary = activeSummary,
                            business = business,
                            language = language,
                            showBack = false,
                            onBack = { selectedCustomerId = null },
                            onAddUdhaar = { entryDialogType = "credit" },
                            onReceivePay = { entryDialogType = "payment" },
                            onReverseEntry = { reversalTarget = it },
                            onShare = {
                                val text = "${business.name} - Khata Statement\nCustomer: ${activeSummary.customer.name}\nClosing Balance: ${formatINR(activeSummary.closingBalance)}\nDate: ${localDateStr()}"
                                shareText(context, "Khata Statement", text)
                            }
                        )
                    } else {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            modifier = Modifier.fillMaxSize().padding(vertical = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(24.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Select or add a customer to view their single running Khata.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        } else {
            if (activeSummary == null) {
                KhataListPane(
                    banner = t.singleKhataBanner,
                    searchHint = t.search,
                    search = search,
                    onSearchChange = { search = it },
                    filter = filter,
                    onFilterChange = { filter = it },
                    filterLabels = listOf("all" to t.all, "outstanding" to t.outstanding, "paid" to t.paid, "overdue" to t.overdue),
                    filtered = filtered,
                    selectedId = null,
                    onSelect = { selectedCustomerId = it },
                    onAddCustomerClick = { showAddCustomer = true },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            } else {
                KhataDetailPane(
                    summary = activeSummary,
                    business = business,
                    language = language,
                    showBack = true,
                    onBack = { selectedCustomerId = null },
                    onAddUdhaar = { entryDialogType = "credit" },
                    onReceivePay = { entryDialogType = "payment" },
                    onReverseEntry = { reversalTarget = it },
                    onShare = {
                        val text = "${business.name} - Khata Statement\nCustomer: ${activeSummary.customer.name}\nClosing Balance: ${formatINR(activeSummary.closingBalance)}\nDate: ${localDateStr()}"
                        shareText(context, "Khata Statement", text)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }

    // Add Customer Dialog
    if (showAddCustomer) {
        var name by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var opening by remember { mutableStateOf("0") }
        var overdue by remember { mutableStateOf("15") }
        AlertDialog(
            onDismissRequest = { showAddCustomer = false },
            title = { Text("Add Customer (Single Khata)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Customer Name *") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("khata_add_cust_name"))
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = opening, onValueChange = { opening = it }, label = { Text("Opening Bal ₹") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                        OutlinedTextField(value = overdue, onValueChange = { overdue = it }, label = { Text("Overdue Days") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddCustomer(name.trim(), phone.trim(), opening.toDoubleOrNull() ?: 0.0, overdue.toLongOrNull() ?: 15L)
                        showAddCustomer = false
                    },
                    enabled = name.trim().isNotEmpty(),
                    modifier = Modifier.testTag("khata_save_cust_button")
                ) { Text(t.save) }
            },
            dismissButton = {
                TextButton(onClick = { showAddCustomer = false }) { Text(t.cancel) }
            }
        )
    }

    // Add Udhaar / Receive Payment Dialog
    if (entryDialogType != null && activeSummary != null) {
        val isCredit = entryDialogType == "credit"
        var amountStr by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        var dateStr by remember { mutableStateOf(localDateStr()) }
        AlertDialog(
            onDismissRequest = { entryDialogType = null },
            title = { Text(if (isCredit) t.addUdhaar else t.receivePayment) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Customer: ${activeSummary.customer.name}", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(value = amountStr, onValueChange = { amountStr = it }, label = { Text("Amount (₹) *") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().testTag("khata_entry_amount_input"))
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Item / Reason (e.g. Atta, UPI)") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("khata_entry_note_input"))
                    OutlinedTextField(value = dateStr, onValueChange = { dateStr = it }, label = { Text("Date (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                val amt = amountStr.toDoubleOrNull() ?: 0.0
                Button(
                    onClick = {
                        onAddKhataEntry(
                            activeSummary.customer.id,
                            activeSummary.customer.name,
                            entryDialogType!!,
                            amt,
                            note,
                            dateStr.ifBlank { localDateStr() },
                            null
                        )
                        entryDialogType = null
                    },
                    enabled = amt > 0.0,
                    colors = ButtonDefaults.buttonColors(containerColor = if (isCredit) Crimson600 else Emerald600),
                    modifier = Modifier.testTag("khata_entry_save_button")
                ) { Text(t.save) }
            },
            dismissButton = { TextButton(onClick = { entryDialogType = null }) { Text(t.cancel) } }
        )
    }

    // Reversal Entry Dialog
    if (reversalTarget != null) {
        val orig = reversalTarget!!
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { reversalTarget = null },
            title = { Text(t.reverseEntry) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Original entry of ${formatINR(orig.amount)} (${orig.note}) stays in audit history and an offsetting reversal is appended.")
                    OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("${t.reversalReason} *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val revType = if (orig.type == "credit") "credit_reversal" else "payment_reversal"
                        onAddKhataEntry(orig.customerId, orig.customerName, revType, orig.amount, "Reversal (${reason.trim()})", localDateStr(), orig.id)
                        reversalTarget = null
                    },
                    enabled = reason.trim().isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = Crimson600)
                ) { Text("Confirm Reversal") }
            },
            dismissButton = { TextButton(onClick = { reversalTarget = null }) { Text(t.cancel) } }
        )
    }
}

@Composable
private fun KhataListPane(
    banner: String,
    searchHint: String,
    search: String,
    onSearchChange: (String) -> Unit,
    filter: String,
    onFilterChange: (String) -> Unit,
    filterLabels: List<Pair<String, String>>,
    filtered: List<CustomerLedgerSummary>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onAddCustomerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            Surface(color = Emerald50, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(banner, style = MaterialTheme.typography.labelSmall, color = Emerald600, modifier = Modifier.weight(1f))
                    Button(
                        onClick = onAddCustomerClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Navy900),
                        modifier = Modifier.padding(start = 8.dp).heightIn(min = 40.dp).testTag("khata_add_customer_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("Customer", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = search,
                onValueChange = onSearchChange,
                placeholder = { Text(searchHint) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("khata_search_input")
            )
        }

        item {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                filterLabels.forEach { (key, lbl) ->
                    FilterChip(
                        selected = filter == key,
                        onClick = { onFilterChange(key) },
                        label = { Text(lbl, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "No customers yet. Tap + Customer or use Sakha AI to create a Single-Customer Khata.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }
        } else {
            items(filtered, key = { it.customer.id }) { s ->
                val isSelected = s.customer.id == selectedId
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isSelected) Emerald50 else Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(s.customer.id) }
                        .testTag("khata_customer_card_${s.customer.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(s.customer.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                if (s.overdueFlag) {
                                    Surface(color = Crimson100, shape = RoundedCornerShape(6.dp)) {
                                        Text("OVERDUE", style = MaterialTheme.typography.labelSmall, color = Crimson600, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                            }
                            Text("${s.customer.phone.ifBlank { "No phone" }} • ${s.entries.size} entries", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            formatINR(s.closingBalance),
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.Bold,
                            color = if (s.closingBalance > 0) Crimson600 else Emerald600
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun KhataDetailPane(
    summary: CustomerLedgerSummary,
    business: Business,
    language: AppLanguage,
    showBack: Boolean,
    onBack: () -> Unit,
    onAddUdhaar: () -> Unit,
    onReceivePay: () -> Unit,
    onReverseEntry: (KhataEntry) -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val t = stringsFor(language)
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (showBack) {
                                IconButton(onClick = onBack, modifier = Modifier.testTag("khata_back_button")) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            }
                            Column {
                                Text("${business.name} • Khata", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(summary.customer.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(summary.customer.phone.ifBlank { "No phone" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(t.closingBal, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                formatINR(summary.closingBalance),
                                fontFamily = JetBrainsMono,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (summary.closingBalance > 0) Crimson600 else Emerald600
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = onAddUdhaar,
                            colors = ButtonDefaults.buttonColors(containerColor = Crimson600),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("khata_add_udhaar_button")
                        ) { Text("+ ${t.addUdhaar}", style = MaterialTheme.typography.labelSmall) }
                        Button(
                            onClick = onReceivePay,
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("khata_receive_pay_button")
                        ) { Text("+ ${t.receivePayment}", style = MaterialTheme.typography.labelSmall) }
                        OutlinedButton(
                            onClick = onShare,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("khata_share_button")
                        ) { Icon(Icons.Default.Share, contentDescription = t.shareText) }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${t.openingBal}: ${formatINR(summary.customer.openingBalance)}", style = MaterialTheme.typography.labelSmall)
                        Text("Udhaar: ${formatINR(summary.creditTotal)}", style = MaterialTheme.typography.labelSmall, color = Crimson600)
                        Text("Paid: ${formatINR(summary.paymentTotal)}", style = MaterialTheme.typography.labelSmall, color = Emerald600)
                    }
                }
            }
        }

        if (summary.entries.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                    Text("No entries yet in this Khata.", modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(summary.entries, key = { it.id }) { ent ->
                val isCreditSide = ent.type == "credit" || ent.type == "payment_reversal"
                val isReversed = summary.reversedEntryIds.contains(ent.id)
                val isReversal = ent.type.endsWith("_reversal")
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(ent.note, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${ent.date} • ${ent.type.replace("_", " ").uppercase()}${if (isReversed) " • REVERSED" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "${if (isCreditSide) "+" else "-"}${formatINR(ent.amount)}",
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.Bold,
                                color = if (isCreditSide) Crimson600 else Emerald600
                            )
                            if (!isReversed && !isReversal) {
                                TextButton(onClick = { onReverseEntry(ent) }) {
                                    Text("Reverse", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(color = Color.Transparent)
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}
