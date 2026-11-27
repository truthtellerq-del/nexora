package com.example.ai

import android.util.Log
import com.example.model.Customer
import com.example.model.Expense
import com.example.model.KhataEntry
import com.example.model.Product
import com.example.model.Purchase
import com.example.model.SakhaPendingAction
import com.example.model.Sale
import com.example.util.computeCustomerLedger
import com.example.util.formatINR
import com.example.util.localDateStr
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

sealed interface SakhaParseResult {
    data object ConfirmPending : SakhaParseResult
    data object CancelPending : SakhaParseResult
    data class ReadAnswer(val reply: String) : SakhaParseResult
    data class ProposeAction(val action: SakhaPendingAction) : SakhaParseResult
    data class DisambiguateCustomer(
        val matches: List<Customer>,
        val amount: Double,
        val isPayment: Boolean,
        val rawText: String
    ) : SakhaParseResult
    data class Error(val message: String) : SakhaParseResult
}

class SakhaEngine {

    private val sakhaSchema = Schema.obj(
        properties = mapOf(
            "intent" to Schema.enumeration(
                values = listOf(
                    "READ_ANSWER",
                    "ADD_UDHAAR",
                    "RECEIVE_PAYMENT",
                    "ADD_EXPENSE",
                    "ADD_PURCHASE",
                    "QUICK_SALE",
                    "STOCK_ADJUST",
                    "UNKNOWN"
                ),
                description = "Classified intent of the Indian shopkeeper's Hinglish/Hindi/English command"
            ),
            "replyText" to Schema.string(description = "Concise grounded answer in the user's language for READ_ANSWER or UNKNOWN"),
            "customerName" to Schema.string(description = "Customer name if khata/payment intent"),
            "productName" to Schema.string(description = "Product name if stock adjustment intent"),
            "category" to Schema.string(description = "Expense category: Rent, Electricity, Salary, Transport, Packaging, or Other"),
            "paymentMode" to Schema.string(description = "Cash, UPI, or Card for quick sale"),
            "amount" to Schema.double(description = "Monetary amount in INR"),
            "qty" to Schema.double(description = "Stock quantity if STOCK_ADJUST"),
            "note" to Schema.string(description = "Short item/reason note")
        ),
        optionalProperties = listOf("customerName", "productName", "category", "paymentMode", "amount", "qty", "note")
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = sakhaSchema
            }
        )
    }

    suspend fun processCommand(
        rawText: String,
        customers: List<Customer>,
        products: List<Product>,
        khataEntries: List<KhataEntry>,
        sales: List<Sale>,
        purchases: List<Purchase>,
        expenses: List<Expense>,
        pendingAction: SakhaPendingAction?
    ): SakhaParseResult {
        val text = rawText.trim()
        val lower = text.lowercase()

        // 1. Strict Two-Step Confirmation handling ("Kar do" / "Mat karo")
        if (pendingAction != null) {
            if (Regex("^(kar\\s*do|kardo|haan|ha|yes|confirm|save|ok|theek\\s*hai|done|जी|हाँ|कर\\s*दो|सेव\\s*करो)").containsMatchIn(lower)) {
                return SakhaParseResult.ConfirmPending
            }
            if (Regex("^(mat\\s*karo|nahi|no|cancel|ruko|stop|नहीं|मत\\s*करो|कैंसल)").containsMatchIn(lower)) {
                return SakhaParseResult.CancelPending
            }
        }

        // 2. Fast local deterministic read/write check first for instant response
        val localResult = tryLocalParse(text, customers, products, khataEntries, sales, purchases, expenses)
        if (localResult != null) return localResult

        // 3. Route open-ended / complex Hinglish/Hindi/English commands to Firebase AI Logic (Gemini)
        val today = localDateStr()
        val todaySales = sales.filter { it.date == today && it.status != "returned" }
        val todayBikri = todaySales.sumOf { it.total }
        val todayCogs = todaySales.sumOf { it.totalCost }
        val todayKharidi = purchases.filter { it.date == today }.sumOf { it.total }
        val todayExp = expenses.filter { it.date == today }.sumOf { it.amount }
        val totalUdhaar = customers.sumOf { maxOf(0.0, computeCustomerLedger(it, khataEntries).closingBalance) }
        val lowStockList = products.filter { it.stock <= it.lowStockThreshold }.take(6)
            .joinToString(", ") { "${it.name} (${it.stock} ${it.unit})" }
        val custSummary = customers.take(25).joinToString(", ") {
            val bal = computeCustomerLedger(it, khataEntries).closingBalance
            "${it.name}: ${formatINR(bal)}"
        }
        val prodSummary = products.take(25).joinToString(", ") { "${it.name} (${it.stock} ${it.unit}, ₹${it.sellPrice})" }

        val prompt = """
            You are Sakha (सखा), the digital business companion for an Indian retail shopkeeper in Nexora ERP.
            Today's Date: $today
            Live Shop Snapshot:
            - Today's Bikri (Sales): ${formatINR(todayBikri)} (${todaySales.size} bills)
            - Today's Kharidi (Purchases): ${formatINR(todayKharidi)}
            - Today's Operating Expenses: ${formatINR(todayExp)}
            - Today's Net Profit: ${formatINR(todayBikri - todayCogs - todayExp)}
            - Total Pending Udhaar: ${formatINR(totalUdhaar)} (${customers.size} customers)
            - Low Stock Items: ${lowStockList.ifEmpty { "None" }}
            - Customers: ${custSummary.ifEmpty { "None yet" }}
            - Products: ${prodSummary.ifEmpty { "None yet" }}

            User Message: "$text"

            Rules:
            - If the user is asking a question (bikri, profit, balance, low stock, kharidi, summary), return intent READ_ANSWER with accurate numbers grounded ONLY in the snapshot above.
            - If the user wants to record udhaar/credit, return intent ADD_UDHAAR with customerName, amount, note.
            - If the user wants to record payment received from a customer, return intent RECEIVE_PAYMENT with customerName, amount, note.
            - If the user wants to add an expense, return intent ADD_EXPENSE with category, amount, note.
            - If the user wants to record a purchase, return intent ADD_PURCHASE with amount, note.
            - If the user wants to record a quick sale, return intent QUICK_SALE with paymentMode, amount, note.
            - If the user wants to add stock, return intent STOCK_ADJUST with productName, qty, note.
            - Match the user's language (English, Hindi, or Hinglish) in replyText.
        """.trimIndent()

        return try {
            val response = withTimeout(60_000L) {
                model.generateContent(prompt)
            }
            val rawJson = response.text ?: throw IllegalStateException("Sakha returned an empty response.")
            val json = JSONObject(rawJson)
            val intent = json.optString("intent", "UNKNOWN")
            val amount = json.optDouble("amount", 0.0).takeIf { !it.isNaN() && it > 0 } ?: 0.0
            val note = json.optString("note", text).ifBlank { text }

            when (intent) {
                "READ_ANSWER" -> SakhaParseResult.ReadAnswer(json.optString("replyText", "Done."))
                "ADD_UDHAAR", "RECEIVE_PAYMENT" -> {
                    val isPay = intent == "RECEIVE_PAYMENT"
                    val cname = json.optString("customerName", "").trim()
                    if (amount <= 0.0 || cname.isEmpty()) {
                        SakhaParseResult.ReadAnswer(json.optString("replyText", "Please specify customer name and amount."))
                    } else {
                        val matches = customers.filter { it.name.contains(cname, ignoreCase = true) }
                        when {
                            matches.size > 1 -> SakhaParseResult.DisambiguateCustomer(matches, amount, isPay, text)
                            matches.size == 1 -> {
                                val c = matches.first()
                                val bal = computeCustomerLedger(c, khataEntries).closingBalance
                                SakhaParseResult.ProposeAction(
                                    if (isPay) SakhaPendingAction.ReceivePayment(c.id, c.name, amount, bal, bal - amount, today, note)
                                    else SakhaPendingAction.AddUdhaar(c.id, c.name, amount, bal, bal + amount, today, note)
                                )
                            }
                            !isPay -> SakhaParseResult.ProposeAction(
                                SakhaPendingAction.AddUdhaarNewCustomer(cname, amount, today, note)
                            )
                            else -> SakhaParseResult.ReadAnswer("Customer \"$cname\" not found for payment.")
                        }
                    }
                }
                "ADD_EXPENSE" -> if (amount > 0) {
                    val cat = json.optString("category", "Other").ifBlank { "Other" }
                    SakhaParseResult.ProposeAction(SakhaPendingAction.AddExpense(cat, amount, today, note))
                } else SakhaParseResult.ReadAnswer(json.optString("replyText", "Please specify expense amount."))
                "ADD_PURCHASE" -> if (amount > 0) {
                    SakhaParseResult.ProposeAction(SakhaPendingAction.AddPurchase(amount, today, note))
                } else SakhaParseResult.ReadAnswer(json.optString("replyText", "Please specify purchase amount."))
                "QUICK_SALE" -> if (amount > 0) {
                    val mode = json.optString("paymentMode", "Cash").ifBlank { "Cash" }
                    SakhaParseResult.ProposeAction(SakhaPendingAction.QuickSale(amount, mode, today, note))
                } else SakhaParseResult.ReadAnswer(json.optString("replyText", "Please specify sale amount."))
                "STOCK_ADJUST" -> {
                    val pname = json.optString("productName", "").trim()
                    val qty = json.optDouble("qty", 0.0).takeIf { !it.isNaN() && it > 0 } ?: 0.0
                    val pMatch = products.firstOrNull { it.name.contains(pname, ignoreCase = true) }
                    if (pMatch != null && qty > 0) {
                        SakhaParseResult.ProposeAction(
                            SakhaPendingAction.StockAdjust(pMatch.id, pMatch.name, qty, pMatch.unit, pMatch.stock, pMatch.stock + qty, today, note)
                        )
                    } else {
                        SakhaParseResult.ReadAnswer(json.optString("replyText", "Product not found or quantity missing."))
                    }
                }
                else -> SakhaParseResult.ReadAnswer(json.optString("replyText", "Samajh nahi aaya. Please try again."))
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Sakha AI timed out", e)
            SakhaParseResult.Error("Sakha timed out after 60 seconds. Please try again.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Sakha AI failed", e)
            SakhaParseResult.Error(e.localizedMessage ?: "Sakha AI request failed.")
        }
    }

    private fun tryLocalParse(
        text: String,
        customers: List<Customer>,
        products: List<Product>,
        khataEntries: List<KhataEntry>,
        sales: List<Sale>,
        purchases: List<Purchase>,
        expenses: List<Expense>
    ): SakhaParseResult? {
        val lower = text.lowercase()
        val today = localDateStr()

        // Simple instant read queries
        if (Regex("(bikri|sale|sales|बिक्री).*(kitni|kitna|today|aaj|आज|total|kya)|(aaj|today|आज).*(bikri|sale|sales|बिक्री)").containsMatchIn(lower)) {
            val todaySales = sales.filter { it.date == today && it.status != "returned" }
            val total = todaySales.sumOf { it.total }
            return SakhaParseResult.ReadAnswer("Aaj ($today) ki total bikri ${formatINR(total)} hai (${todaySales.size} bills).")
        }
        if (Regex("(profit|munafa|मुनाफा|मुनाफ़ा|kamai)").containsMatchIn(lower)) {
            val tSales = sales.filter { it.date == today && it.status != "returned" }
            val rev = tSales.sumOf { it.total }
            val cogs = tSales.sumOf { it.totalCost }
            val exp = expenses.filter { it.date == today }.sumOf { it.amount }
            val gross = rev - cogs
            val net = gross - exp
            return SakhaParseResult.ReadAnswer("Aaj ki Bikri: ${formatINR(rev)}, Gross Margin: ${formatINR(gross)}, Expenses: ${formatINR(exp)}, aur Net Profit: ${formatINR(net)} hai.")
        }
        if (Regex("(low\\s*stock|kam\\s*stock|स्टॉक)").containsMatchIn(lower) && !Regex("(add|jod|जोड़)").containsMatchIn(lower)) {
            val low = products.filter { it.stock <= it.lowStockThreshold }
            val msg = if (low.isEmpty()) "Abhi koi bhi item low stock mein nahi hai (Total ${products.size} items)."
            else "${low.size} items low stock mein hain: ${low.take(5).joinToString(", ") { "${it.name} (${it.stock} ${it.unit})" }}."
            return SakhaParseResult.ReadAnswer(msg)
        }

        val amtMatch = Regex("(?:₹|rs\\.?|rupaye|rupees|रुपये|रुपए)?\\s*(\\d+(?:\\.\\d{1,2})?)\\s*(?:₹|rs\\.?|rupaye|rupees|रुपये|रुपए|ka|ki|ke)?", RegexOption.IGNORE_CASE).find(text)
        val amount = amtMatch?.groupValues?.getOrNull(1)?.toDoubleOrNull()

        if (amount != null && amount > 0 && Regex("(udhaar|udhar|khate|khata|jod|उधार|खाते|जोड़)").containsMatchIn(lower)) {
            val words = lower.replace(Regex("[^a-z0-9\\u0900-\\u097f\\s]"), " ").split(Regex("\\s+")).filter { it.length >= 2 }
            val matches = customers.filter { c ->
                val cn = c.name.lowercase()
                words.any { w -> cn.contains(w) && w !in setOf("ka", "ke", "ki", "ko", "mein", "khate", "khata", "udhaar", "jod", "add", "rupaye", "rs") }
            }
            if (matches.size > 1) return SakhaParseResult.DisambiguateCustomer(matches, amount, false, text)
            if (matches.size == 1) {
                val c = matches.first()
                val bal = computeCustomerLedger(c, khataEntries).closingBalance
                return SakhaParseResult.ProposeAction(SakhaPendingAction.AddUdhaar(c.id, c.name, amount, bal, bal + amount, today, text))
            }
        }

        if (amount != null && amount > 0 && Regex("(diye|diya|jama|payment|received|mila|mile|दिए|जमा|पेमेंट)").containsMatchIn(lower)) {
            val words = lower.replace(Regex("[^a-z0-9\\u0900-\\u097f\\s]"), " ").split(Regex("\\s+")).filter { it.length >= 2 }
            val matches = customers.filter { c ->
                val cn = c.name.lowercase()
                words.any { w -> cn.contains(w) && w !in setOf("ne", "ka", "ke", "ki", "ko", "diye", "jama", "payment", "rupaye", "rs") }
            }
            if (matches.size > 1) return SakhaParseResult.DisambiguateCustomer(matches, amount, true, text)
            if (matches.size == 1) {
                val c = matches.first()
                val bal = computeCustomerLedger(c, khataEntries).closingBalance
                return SakhaParseResult.ProposeAction(SakhaPendingAction.ReceivePayment(c.id, c.name, amount, bal, bal - amount, today, text))
            }
        }

        return null
    }

    private companion object {
        const val TAG = "SakhaEngine"
    }
}
