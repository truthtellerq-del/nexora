package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class AppLanguage(val code: String, val displayName: String, val speechLocale: String) {
    EN("en", "English", "en-IN"),
    HI("hi", "हिन्दी", "hi-IN"),
    HINGLISH("hinglish", "Hinglish", "hi-IN");

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code == code } ?: EN
    }
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@Keep
@IgnoreExtraProperties
data class SubscriptionPlan(
    @DocumentId val id: String = "",
    val code: String = "trial",
    val name: String = "",
    val price: Double = 0.0,
    val billingDays: Long = 30L,
    val badge: String = "",
    val sortOrder: Long = 0L,
    val highlights: List<String> = emptyList(),
    @ServerTimestamp val updatedAt: Timestamp? = null
)

val DEFAULT_PLANS = listOf(
    SubscriptionPlan(
        id = "trial", code = "trial", name = "Free Trial (7 Days)", price = 0.0, billingDays = 7L,
        badge = "Full Access", sortOrder = 0L,
        highlights = listOf(
            "All Starter, Growth & Pro features unlocked for 7 days",
            "Sakha AI Voice & Text",
            "Unlimited Single-Customer Khata",
            "POS, Inventory, Multi-Branch & Analytics"
        )
    ),
    SubscriptionPlan(
        id = "starter", code = "starter", name = "Starter", price = 200.0, billingDays = 30L,
        badge = "Habit Builder", sortOrder = 1L,
        highlights = listOf(
            "Includes Sakha AI (Voice & Text)",
            "Single Customer Udhaar Khata",
            "Daily Bikri, Kharidi & Profit Tracker",
            "POS Billing, Basic Inventory & Expenses"
        )
    ),
    SubscriptionPlan(
        id = "growth", code = "growth", name = "Growth", price = 599.0, billingDays = 30L,
        badge = "Most Popular", sortOrder = 2L,
        highlights = listOf(
            "Everything in Starter + Sakha AI",
            "Multi-Branch Support",
            "Suppliers & Purchase Workflows",
            "Staff Roles, Attendance & Tasks/Reminders"
        )
    ),
    SubscriptionPlan(
        id = "pro", code = "pro", name = "Pro", price = 999.0, billingDays = 30L,
        badge = "Multi-Store Scale", sortOrder = 3L,
        highlights = listOf(
            "Everything in Growth",
            "Advanced Profit & Margin Analytics",
            "Priority CSV & Ledger Export Tools",
            "Multi-Store Scale Controls"
        )
    )
)

@Keep
@IgnoreExtraProperties
data class Business(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val ownerName: String = "",
    val phone: String = "",
    val businessType: String = "Kirana",
    val city: String = "",
    val state: String = "",
    val preferredLang: String = "en",
    val currency: String = "₹",
    val fyStart: String = "April",
    val gstin: String = "",
    val planId: String = "trial",
    val subscriptionStatus: String = "trialing",
    val trialStartMillis: Long = 0L,
    val trialEndMillis: Long = 0L,
    val planEndMillis: Long = 0L,
    val negativeStockProtection: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Branch(
    @DocumentId val id: String = "",
    val userId: String = "",
    val businessId: String = "",
    val name: String = "",
    val city: String = "",
    val primaryBranch: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Customer(
    @DocumentId val id: String = "",
    val userId: String = "",
    val businessId: String = "",
    val branchId: String = "",
    val name: String = "",
    val phone: String = "",
    val openingBalance: Double = 0.0,
    val overdueDays: Long = 15L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class KhataEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val businessId: String = "",
    val branchId: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val type: String = "credit", // credit | payment | credit_reversal | payment_reversal
    val amount: Double = 0.0,
    val note: String = "",
    val date: String = "",
    val refEntryId: String? = null,
    val saleId: String? = null,
    val idempotencyKey: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class CustomerLedgerSummary(
    val customer: Customer,
    val entries: List<KhataEntry>,
    val creditTotal: Double,
    val paymentTotal: Double,
    val closingBalance: Double,
    val reversedEntryIds: Set<String>,
    val overdueFlag: Boolean
)

@Keep
@IgnoreExtraProperties
data class Product(
    @DocumentId val id: String = "",
    val userId: String = "",
    val businessId: String = "",
    val branchId: String = "",
    val name: String = "",
    val sku: String = "",
    val unit: String = "pcs",
    val costPrice: Double = 0.0,
    val sellPrice: Double = 0.0,
    val taxRate: Double = 0.0,
    val stock: Double = 0.0,
    val lowStockThreshold: Double = 5.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class StockMovement(
    @DocumentId val id: String = "",
    val userId: String = "",
    val businessId: String = "",
    val productId: String = "",
    val productName: String = "",
    val type: String = "adjustment", // opening | sale | sale_return | purchase | adjustment
    val qty: Double = 0.0,
    val reason: String = "",
    val date: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SaleItem(
    val productId: String = "",
    val name: String = "",
    val unit: String = "pcs",
    val qty: Double = 1.0,
    val price: Double = 0.0,
    val costPrice: Double = 0.0,
    val taxRate: Double = 0.0
)

@Keep
@IgnoreExtraProperties
data class Sale(
    @DocumentId val id: String = "",
    val userId: String = "",
    val businessId: String = "",
    val branchId: String = "",
    val invoiceNo: String = "",
    val date: String = "",
    val status: String = "completed", // completed | returned
    val items: List<SaleItem> = emptyList(),
    val subtotal: Double = 0.0,
    val tax: Double = 0.0,
    val discount: Double = 0.0,
    val total: Double = 0.0,
    val totalCost: Double = 0.0,
    val paymentMode: String = "Cash",
    val paidNow: Double = 0.0,
    val udhaarAmount: Double = 0.0,
    val customerId: String? = null,
    val customerName: String = "Walk-in Customer",
    val idempotencyKey: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Supplier(
    @DocumentId val id: String = "",
    val userId: String = "",
    val businessId: String = "",
    val name: String = "",
    val phone: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Purchase(
    @DocumentId val id: String = "",
    val userId: String = "",
    val businessId: String = "",
    val branchId: String = "",
    val supplierId: String? = null,
    val supplierName: String = "",
    val productId: String? = null,
    val productName: String = "",
    val qty: Double = 1.0,
    val total: Double = 0.0,
    val paid: Double = 0.0,
    val date: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Expense(
    @DocumentId val id: String = "",
    val userId: String = "",
    val businessId: String = "",
    val branchId: String = "",
    val category: String = "Other",
    val amount: Double = 0.0,
    val note: String = "",
    val date: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class StaffMember(
    @DocumentId val id: String = "",
    val userId: String = "",
    val businessId: String = "",
    val branchId: String = "",
    val name: String = "",
    val role: String = "Cashier",
    val phone: String = "",
    val salary: Double = 0.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AttendanceRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val businessId: String = "",
    val staffId: String = "",
    val date: String = "",
    val status: String = "present", // present | half | absent
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ShopTask(
    @DocumentId val id: String = "",
    val userId: String = "",
    val businessId: String = "",
    val title: String = "",
    val dueDate: String = "",
    val completed: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class SubscriptionInfo(
    val expired: Boolean,
    val daysLeft: Int,
    val activePlanCode: String,
    val plan: SubscriptionPlan
)

sealed interface SakhaPendingAction {
    val date: String
    val note: String

    data class AddUdhaar(
        val customerId: String,
        val customerName: String,
        val amount: Double,
        val beforeBal: Double,
        val afterBal: Double,
        override val date: String,
        override val note: String
    ) : SakhaPendingAction

    data class AddUdhaarNewCustomer(
        val customerName: String,
        val amount: Double,
        override val date: String,
        override val note: String
    ) : SakhaPendingAction

    data class ReceivePayment(
        val customerId: String,
        val customerName: String,
        val amount: Double,
        val beforeBal: Double,
        val afterBal: Double,
        override val date: String,
        override val note: String
    ) : SakhaPendingAction

    data class AddExpense(
        val category: String,
        val amount: Double,
        override val date: String,
        override val note: String
    ) : SakhaPendingAction

    data class AddPurchase(
        val amount: Double,
        override val date: String,
        override val note: String
    ) : SakhaPendingAction

    data class QuickSale(
        val amount: Double,
        val paymentMode: String,
        override val date: String,
        override val note: String
    ) : SakhaPendingAction

    data class StockAdjust(
        val productId: String,
        val productName: String,
        val qty: Double,
        val unit: String,
        val beforeStock: Double,
        val afterStock: Double,
        override val date: String,
        override val note: String
    ) : SakhaPendingAction
}

data class SakhaChatMessage(
    val id: String,
    val isUser: Boolean,
    val text: String
)
