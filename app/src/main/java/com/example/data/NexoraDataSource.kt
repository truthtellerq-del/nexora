package com.example.data

import com.example.model.AttendanceRecord
import com.example.model.Branch
import com.example.model.Business
import com.example.model.Customer
import com.example.model.Expense
import com.example.model.KhataEntry
import com.example.model.Product
import com.example.model.Purchase
import com.example.model.Sale
import com.example.model.SaleItem
import com.example.model.ShopTask
import com.example.model.StaffMember
import com.example.model.StockMovement
import com.example.model.SubscriptionPlan
import com.example.model.Supplier
import kotlinx.coroutines.flow.Flow

interface NexoraDataSource {
    fun observePlans(): Flow<List<SubscriptionPlan>>
    fun observeBusinesses(uid: String): Flow<List<Business>>
    fun observeBranches(uid: String, businessId: String): Flow<List<Branch>>
    fun observeCustomers(uid: String, businessId: String): Flow<List<Customer>>
    fun observeKhataEntries(uid: String, businessId: String): Flow<List<KhataEntry>>
    fun observeProducts(uid: String, businessId: String): Flow<List<Product>>
    fun observeStockMovements(uid: String, businessId: String): Flow<List<StockMovement>>
    fun observeSales(uid: String, businessId: String): Flow<List<Sale>>
    fun observeSuppliers(uid: String, businessId: String): Flow<List<Supplier>>
    fun observePurchases(uid: String, businessId: String): Flow<List<Purchase>>
    fun observeExpenses(uid: String, businessId: String): Flow<List<Expense>>
    fun observeStaff(uid: String, businessId: String): Flow<List<StaffMember>>
    fun observeAttendance(uid: String, businessId: String): Flow<List<AttendanceRecord>>
    fun observeTasks(uid: String, businessId: String): Flow<List<ShopTask>>

    suspend fun createBusinessWithMainBranch(
        name: String,
        ownerName: String,
        phone: String,
        businessType: String,
        city: String,
        state: String,
        preferredLang: String,
        fyStart: String,
        gstin: String
    ): Result<Pair<String, String>>

    suspend fun updateBusinessSettings(
        businessId: String,
        preferredLang: String? = null,
        negativeStockProtection: Boolean? = null,
        planId: String? = null,
        subscriptionStatus: String? = null,
        trialEndMillis: Long? = null,
        planEndMillis: Long? = null
    ): Result<Unit>

    suspend fun addBranch(businessId: String, name: String, city: String): Result<String>

    suspend fun addCustomer(
        businessId: String,
        branchId: String,
        name: String,
        phone: String,
        openingBalance: Double,
        overdueDays: Long
    ): Result<String>

    suspend fun addKhataEntry(
        businessId: String,
        branchId: String,
        customerId: String,
        customerName: String,
        type: String,
        amount: Double,
        note: String,
        date: String,
        refEntryId: String? = null,
        saleId: String? = null,
        idempotencyKey: String
    ): Result<String>

    suspend fun addProduct(
        businessId: String,
        branchId: String,
        name: String,
        sku: String,
        unit: String,
        costPrice: Double,
        sellPrice: Double,
        taxRate: Double,
        openingStock: Double,
        lowStockThreshold: Double
    ): Result<String>

    suspend fun adjustStock(
        businessId: String,
        product: Product,
        deltaQty: Double,
        reason: String,
        negativeStockProtection: Boolean
    ): Result<Unit>

    suspend fun completePosSale(
        businessId: String,
        branchId: String,
        invoiceNo: String,
        items: List<SaleItem>,
        currentProducts: List<Product>,
        negativeStockProtection: Boolean,
        subtotal: Double,
        tax: Double,
        discount: Double,
        total: Double,
        totalCost: Double,
        paymentMode: String,
        paidNow: Double,
        udhaarAmount: Double,
        customerId: String?,
        customerName: String,
        idempotencyKey: String
    ): Result<Sale>

    suspend fun returnSale(
        businessId: String,
        sale: Sale,
        currentProducts: List<Product>
    ): Result<Unit>

    suspend fun addSupplier(businessId: String, name: String, phone: String): Result<String>

    suspend fun addPurchase(
        businessId: String,
        branchId: String,
        supplierId: String?,
        supplierName: String,
        product: Product?,
        productName: String,
        qty: Double,
        total: Double,
        paid: Double,
        date: String
    ): Result<String>

    suspend fun addExpense(
        businessId: String,
        branchId: String,
        category: String,
        amount: Double,
        note: String,
        date: String
    ): Result<String>

    suspend fun addStaff(
        businessId: String,
        branchId: String,
        name: String,
        role: String,
        phone: String,
        salary: Double
    ): Result<String>

    suspend fun markAttendance(
        businessId: String,
        staffId: String,
        date: String,
        status: String
    ): Result<Unit>

    suspend fun addTask(businessId: String, title: String, dueDate: String): Result<String>

    suspend fun toggleTask(businessId: String, task: ShopTask): Result<Unit>

    suspend fun savePlansAsAdmin(plans: List<SubscriptionPlan>): Result<Unit>
}
