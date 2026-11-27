package com.example.util

import android.content.Context
import android.content.Intent
import com.example.model.Business
import com.example.model.Customer
import com.example.model.CustomerLedgerSummary
import com.example.model.DEFAULT_PLANS
import com.example.model.KhataEntry
import com.example.model.SubscriptionInfo
import com.example.model.SubscriptionPlan
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

private val indianLocale = Locale("en", "IN")

fun formatINR(amount: Double, symbol: String = "₹"): String {
    val absVal = abs(amount)
    val pattern = if (absVal % 1.0 == 0.0) "#,##,##0" else "#,##,##0.00"
    val df = DecimalFormat(pattern, DecimalFormatSymbols(indianLocale))
    val formatted = df.format(absVal)
    return if (amount < 0) "-$symbol$formatted" else "$symbol$formatted"
}

fun localDateStr(millis: Long = System.currentTimeMillis()): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    return sdf.format(Date(millis))
}

fun shiftDays(dateStr: String, deltaDays: Int): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val parsed = runCatching { sdf.parse(dateStr) }.getOrNull() ?: Date()
    val cal = Calendar.getInstance().apply {
        time = parsed
        add(Calendar.DAY_OF_YEAR, deltaDays)
    }
    return sdf.format(cal.time)
}

fun daysBetween(startYmd: String, endYmd: String): Int {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val s = runCatching { sdf.parse(startYmd)?.time }.getOrNull() ?: return 0
    val e = runCatching { sdf.parse(endYmd)?.time }.getOrNull() ?: return 0
    return ((e - s) / 86_400_000L).toInt()
}

fun computeCustomerLedger(customer: Customer, allEntries: List<KhataEntry>): CustomerLedgerSummary {
    val entries = allEntries
        .filter { it.customerId == customer.id }
        .sortedBy { it.createdAt?.toDate()?.time ?: 0L }

    var creditTotal = 0.0
    var paymentTotal = 0.0
    var creditReversalTotal = 0.0
    var paymentReversalTotal = 0.0
    val reversedIds = mutableSetOf<String>()
    var lastCreditDate: String? = null

    for (e in entries) {
        if (!e.refEntryId.isNullOrBlank()) reversedIds.add(e.refEntryId)
        when (e.type) {
            "credit" -> {
                creditTotal += e.amount
                lastCreditDate = e.date
            }
            "payment" -> paymentTotal += e.amount
            "credit_reversal" -> creditReversalTotal += e.amount
            "payment_reversal" -> paymentReversalTotal += e.amount
        }
    }

    val closing = customer.openingBalance + creditTotal - paymentTotal - creditReversalTotal + paymentReversalTotal
    val today = localDateStr()
    val isOverdue = closing > 0.0 && lastCreditDate != null &&
        daysBetween(lastCreditDate, today) > customer.overdueDays.toInt()

    return CustomerLedgerSummary(
        customer = customer,
        entries = entries,
        creditTotal = creditTotal,
        paymentTotal = paymentTotal,
        closingBalance = closing,
        reversedEntryIds = reversedIds,
        overdueFlag = isOverdue
    )
}

fun getSubscriptionInfo(business: Business?, plans: List<SubscriptionPlan>): SubscriptionInfo {
    val activePlans = plans.ifEmpty { DEFAULT_PLANS }
    if (business == null) {
        return SubscriptionInfo(false, 7, "trial", activePlans.first())
    }
    val now = System.currentTimeMillis()
    val planCode = business.planId.ifBlank { "trial" }
    val planObj = activePlans.firstOrNull { it.code == planCode } ?: activePlans.first()

    if (planCode == "trial") {
        val msLeft = business.trialEndMillis - now
        val daysLeft = max(0, ceil(msLeft / 86_400_000.0).toInt())
        val expired = msLeft <= 0L || business.subscriptionStatus == "expired"
        return SubscriptionInfo(expired, daysLeft, "trial", planObj)
    }

    val msLeft = (if (business.planEndMillis > 0L) business.planEndMillis else now + 30L * 86_400_000L) - now
    val daysLeft = max(0, ceil(msLeft / 86_400_000.0).toInt())
    val expired = business.subscriptionStatus == "expired" || msLeft <= 0L
    return SubscriptionInfo(expired, daysLeft, planCode, planObj)
}

private val FEATURE_MIN_PLAN = mapOf(
    "dashboard" to "starter",
    "pos" to "starter",
    "khata" to "starter",
    "inventory" to "starter",
    "expenses" to "starter",
    "sakha" to "starter",
    "purchases" to "growth",
    "staff" to "growth",
    "branches" to "growth",
    "tasks" to "growth",
    "analytics" to "pro",
    "exports" to "starter"
)

private val PLAN_RANK = mapOf("trial" to 3, "starter" to 1, "growth" to 2, "pro" to 3)

fun canUseFeature(subInfo: SubscriptionInfo, featureKey: String): Boolean {
    if (subInfo.expired) return false
    val minPlan = FEATURE_MIN_PLAN[featureKey] ?: "starter"
    return (PLAN_RANK[subInfo.activePlanCode] ?: 0) >= (PLAN_RANK[minPlan] ?: 1)
}

fun shareText(context: Context, title: String, content: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, content)
    }
    context.startActivity(Intent.createChooser(intent, title))
}

fun buildCsv(headers: List<String>, rows: List<List<Any?>>): String {
    fun esc(v: Any?): String = "\"${(v?.toString() ?: "").replace("\"", "\"\"")}\""
    val headerLine = headers.joinToString(",") { esc(it) }
    val body = rows.joinToString("\n") { r -> r.joinToString(",") { esc(it) } }
    return "$headerLine\n$body"
}
