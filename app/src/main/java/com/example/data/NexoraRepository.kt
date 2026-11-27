package com.example.data

import android.content.Context
import com.example.R
import com.example.model.AttendanceRecord
import com.example.model.Branch
import com.example.model.Business
import com.example.model.Customer
import com.example.model.DEFAULT_PLANS
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
import com.example.util.localDateStr
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class NexoraRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User must be signed in with Google.")

    private fun bizDoc(uid: String, businessId: String) =
        db.collection("users").document(uid).collection("businesses").document(businessId)

    private inline fun <reified T> observeSubcollection(
        uid: String,
        businessId: String,
        subcollection: String
    ): Flow<List<T>> = flow {
        require(uid.isNotBlank() && businessId.isNotBlank()) { "Invalid user or business ID" }
        val path = "users/$uid/businesses/$businessId/$subcollection"
        emitAll(
            bizDoc(uid, businessId).collection(subcollection)
                .snapshots()
                .map { snap -> snap.toObjects(T::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val ex = fsErr ?: (error as? Exception)
                    if (ex != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(ex, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observePlans(): Flow<List<SubscriptionPlan>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("plans").snapshots()
                .map { snap ->
                    val list = snap.toObjects(SubscriptionPlan::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    if (list.isEmpty()) DEFAULT_PLANS else list.sortedBy { it.sortOrder }
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, "plans")
                    }
                    emit(DEFAULT_PLANS)
                }
        )
    }

    fun observeBusinesses(uid: String): Flow<List<Business>> = flow {
        require(uid.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$uid/businesses"
        emitAll(
            db.collection("users").document(uid).collection("businesses")
                .snapshots()
                .map { snap -> snap.toObjects(Business::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeBranches(uid: String, businessId: String) = observeSubcollection<Branch>(uid, businessId, "branches")
    fun observeCustomers(uid: String, businessId: String) = observeSubcollection<Customer>(uid, businessId, "customers")
    fun observeKhataEntries(uid: String, businessId: String) = observeSubcollection<KhataEntry>(uid, businessId, "khataEntries")
    fun observeProducts(uid: String, businessId: String) = observeSubcollection<Product>(uid, businessId, "products")
    fun observeStockMovements(uid: String, businessId: String) = observeSubcollection<StockMovement>(uid, businessId, "stockMovements")
    fun observeSales(uid: String, businessId: String) = observeSubcollection<Sale>(uid, businessId, "sales")
    fun observeSuppliers(uid: String, businessId: String) = observeSubcollection<Supplier>(uid, businessId, "suppliers")
    fun observePurchases(uid: String, businessId: String) = observeSubcollection<Purchase>(uid, businessId, "purchases")
    fun observeExpenses(uid: String, businessId: String) = observeSubcollection<Expense>(uid, businessId, "expenses")
    fun observeStaff(uid: String, businessId: String) = observeSubcollection<StaffMember>(uid, businessId, "staff")
    fun observeAttendance(uid: String, businessId: String) = observeSubcollection<AttendanceRecord>(uid, businessId, "attendance")
    fun observeTasks(uid: String, businessId: String) = observeSubcollection<ShopTask>(uid, businessId, "tasks")

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
    ): Result<Pair<String, String>> {
        var path = "users/unknown/businesses"
        return try {
            val uid = requireUserId()
            require(name.trim().isNotEmpty() && city.trim().isNotEmpty()) { "Business name and city are required." }
            val nowMillis = System.currentTimeMillis()
            val bizRef = db.collection("users").document(uid).collection("businesses").document()
            val brRef = bizRef.collection("branches").document()
            path = bizRef.path

            val bizPayload = mapOf(
                "userId" to uid,
                "name" to name.trim().take(100),
                "ownerName" to ownerName.trim().take(80),
                "phone" to phone.trim().take(20),
                "businessType" to businessType.trim().take(40),
                "city" to city.trim().take(60),
                "state" to state.trim().take(60),
                "preferredLang" to preferredLang,
                "currency" to "₹",
                "fyStart" to fyStart,
                "gstin" to gstin.trim().uppercase().take(20),
                "planId" to "trial",
                "subscriptionStatus" to "trialing",
                "trialStartMillis" to nowMillis,
                "trialEndMillis" to (nowMillis + 7L * 86_400_000L),
                "negativeStockProtection" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val brPayload = mapOf(
                "userId" to uid,
                "businessId" to bizRef.id,
                "name" to "Main Branch",
                "city" to city.trim().take(60),
                "primaryBranch" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val batch = db.batch()
            batch.set(bizRef, bizPayload)
            batch.set(brRef, brPayload)
            batch.commit().await()
            Result.success(bizRef.id to brRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateBusinessSettings(
        businessId: String,
        preferredLang: String? = null,
        negativeStockProtection: Boolean? = null,
        planId: String? = null,
        subscriptionStatus: String? = null,
        trialEndMillis: Long? = null,
        planEndMillis: Long? = null
    ): Result<Unit> {
        var path = "users/unknown/businesses/$businessId"
        return try {
            val uid = requireUserId()
            val ref = bizDoc(uid, businessId)
            path = ref.path
            val updates = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
            preferredLang?.let { updates["preferredLang"] = it }
            negativeStockProtection?.let { updates["negativeStockProtection"] = it }
            planId?.let { updates["planId"] = it }
            subscriptionStatus?.let { updates["subscriptionStatus"] = it }
            trialEndMillis?.let { updates["trialEndMillis"] = it }
            planEndMillis?.let { updates["planEndMillis"] = it }
            ref.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun addBranch(businessId: String, name: String, city: String): Result<String> {
        var path = "users/unknown/businesses/$businessId/branches"
        return try {
            val uid = requireUserId()
            require(name.trim().isNotEmpty() && city.trim().isNotEmpty()) { "Branch name and city are required." }
            val ref = bizDoc(uid, businessId).collection("branches").document()
            path = ref.path
            ref.set(
                mapOf(
                    "userId" to uid,
                    "businessId" to businessId,
                    "name" to name.trim().take(80),
                    "city" to city.trim().take(60),
                    "primaryBranch" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(ref.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun addCustomer(
        businessId: String,
        branchId: String,
        name: String,
        phone: String,
        openingBalance: Double,
        overdueDays: Long
    ): Result<String> {
        var path = "users/unknown/businesses/$businessId/customers"
        return try {
            val uid = requireUserId()
            require(name.trim().isNotEmpty()) { "Customer name is required." }
            val ref = bizDoc(uid, businessId).collection("customers").document()
            path = ref.path
            ref.set(
                mapOf(
                    "userId" to uid,
                    "businessId" to businessId,
                    "branchId" to branchId,
                    "name" to name.trim().take(80),
                    "phone" to phone.trim().take(20),
                    "openingBalance" to openingBalance,
                    "overdueDays" to overdueDays.coerceIn(1L, 365L),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(ref.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

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
    ): Result<String> {
        var path = "users/unknown/businesses/$businessId/khataEntries"
        return try {
            val uid = requireUserId()
            require(amount > 0.0) { "Amount must be greater than zero." }
            require(type in listOf("credit", "payment", "credit_reversal", "payment_reversal")) { "Invalid khata entry type." }
            val ref = bizDoc(uid, businessId).collection("khataEntries").document()
            path = ref.path
            val payload = mapOf(
                "userId" to uid,
                "businessId" to businessId,
                "branchId" to branchId,
                "customerId" to customerId,
                "customerName" to customerName.trim().take(80),
                "type" to type,
                "amount" to amount,
                "note" to note.trim().take(200),
                "date" to date,
                "refEntryId" to refEntryId?.takeIf { it.isNotBlank() },
                "saleId" to saleId?.takeIf { it.isNotBlank() },
                "idempotencyKey" to idempotencyKey.take(128),
                "createdAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            ref.set(payload).await()
            Result.success(ref.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

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
    ): Result<String> {
        var path = "users/unknown/businesses/$businessId/products"
        return try {
            val uid = requireUserId()
            require(name.trim().isNotEmpty()) { "Product name is required." }
            val prodRef = bizDoc(uid, businessId).collection("products").document()
            path = prodRef.path
            val batch = db.batch()
            batch.set(
                prodRef,
                mapOf(
                    "userId" to uid,
                    "businessId" to businessId,
                    "branchId" to branchId,
                    "name" to name.trim().take(100),
                    "sku" to sku.trim().take(60),
                    "unit" to unit.trim().ifEmpty { "pcs" }.take(20),
                    "costPrice" to costPrice.coerceAtLeast(0.0),
                    "sellPrice" to sellPrice.coerceAtLeast(0.0),
                    "taxRate" to taxRate.coerceIn(0.0, 100.0),
                    "stock" to openingStock,
                    "lowStockThreshold" to lowStockThreshold.coerceAtLeast(0.0),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (openingStock != 0.0) {
                val movRef = bizDoc(uid, businessId).collection("stockMovements").document()
                batch.set(
                    movRef,
                    mapOf(
                        "userId" to uid,
                        "businessId" to businessId,
                        "productId" to prodRef.id,
                        "productName" to name.trim().take(100),
                        "type" to "opening",
                        "qty" to openingStock,
                        "reason" to "Opening stock",
                        "date" to localDateStr(),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(prodRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun adjustStock(
        businessId: String,
        product: Product,
        deltaQty: Double,
        reason: String,
        negativeStockProtection: Boolean
    ): Result<Unit> {
        var path = "users/unknown/businesses/$businessId/products/${product.id}"
        return try {
            val uid = requireUserId()
            val nextStock = product.stock + deltaQty
            if (negativeStockProtection && nextStock < 0.0) {
                return Result.failure(IllegalStateException("Insufficient stock (Negative stock protection is ON)."))
            }
            val prodRef = bizDoc(uid, businessId).collection("products").document(product.id)
            path = prodRef.path
            val movRef = bizDoc(uid, businessId).collection("stockMovements").document()
            val batch = db.batch()
            batch.update(
                prodRef,
                mapOf("stock" to nextStock, "updatedAt" to FieldValue.serverTimestamp())
            )
            batch.set(
                movRef,
                mapOf(
                    "userId" to uid,
                    "businessId" to businessId,
                    "productId" to product.id,
                    "productName" to product.name,
                    "type" to "adjustment",
                    "qty" to deltaQty,
                    "reason" to reason.trim().ifEmpty { "Manual adjustment" }.take(200),
                    "date" to localDateStr(),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

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
    ): Result<Sale> {
        var path = "users/unknown/businesses/$businessId/sales"
        return try {
            val uid = requireUserId()
            require(items.isNotEmpty() && items.size <= 50) { "Sale must contain 1 to 50 items." }
            val today = localDateStr()
            val batch = db.batch()

            for (item in items) {
                if (item.productId.isNotBlank()) {
                    val prod = currentProducts.firstOrNull { it.id == item.productId }
                    if (prod != null) {
                        val nextStock = prod.stock - item.qty
                        if (negativeStockProtection && nextStock < 0.0) {
                            return Result.failure(IllegalStateException("${prod.name}: Insufficient stock."))
                        }
                        val prodRef = bizDoc(uid, businessId).collection("products").document(prod.id)
                        batch.update(prodRef, mapOf("stock" to nextStock, "updatedAt" to FieldValue.serverTimestamp()))
                        val movRef = bizDoc(uid, businessId).collection("stockMovements").document()
                        batch.set(
                            movRef,
                            mapOf(
                                "userId" to uid,
                                "businessId" to businessId,
                                "productId" to prod.id,
                                "productName" to prod.name,
                                "type" to "sale",
                                "qty" to -item.qty,
                                "reason" to "POS Bill $invoiceNo",
                                "date" to today,
                                "createdAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }
            }

            val saleRef = bizDoc(uid, businessId).collection("sales").document()
            path = saleRef.path
            val itemMaps = items.map {
                mapOf(
                    "productId" to it.productId,
                    "name" to it.name,
                    "unit" to it.unit,
                    "qty" to it.qty,
                    "price" to it.price,
                    "costPrice" to it.costPrice,
                    "taxRate" to it.taxRate
                )
            }
            val salePayload = mapOf(
                "userId" to uid,
                "businessId" to businessId,
                "branchId" to branchId,
                "invoiceNo" to invoiceNo,
                "date" to today,
                "status" to "completed",
                "items" to itemMaps,
                "subtotal" to subtotal,
                "tax" to tax,
                "discount" to discount,
                "total" to total,
                "totalCost" to totalCost,
                "paymentMode" to paymentMode,
                "paidNow" to paidNow,
                "udhaarAmount" to udhaarAmount,
                "customerId" to customerId?.takeIf { it.isNotBlank() },
                "customerName" to customerName.ifBlank { "Walk-in Customer" }.take(80),
                "idempotencyKey" to idempotencyKey,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            batch.set(saleRef, salePayload)

            if (udhaarAmount > 0.0 && !customerId.isNullOrBlank()) {
                val khRef = bizDoc(uid, businessId).collection("khataEntries").document()
                batch.set(
                    khRef,
                    mapOf(
                        "userId" to uid,
                        "businessId" to businessId,
                        "branchId" to branchId,
                        "customerId" to customerId,
                        "customerName" to customerName.take(80),
                        "type" to "credit",
                        "amount" to udhaarAmount,
                        "note" to "POS Bill $invoiceNo",
                        "saleId" to saleRef.id,
                        "date" to today,
                        "idempotencyKey" to "${saleRef.id}_khata",
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            batch.commit().await()
            Result.success(
                Sale(
                    id = saleRef.id,
                    userId = uid,
                    businessId = businessId,
                    branchId = branchId,
                    invoiceNo = invoiceNo,
                    date = today,
                    status = "completed",
                    items = items,
                    subtotal = subtotal,
                    tax = tax,
                    discount = discount,
                    total = total,
                    totalCost = totalCost,
                    paymentMode = paymentMode,
                    paidNow = paidNow,
                    udhaarAmount = udhaarAmount,
                    customerId = customerId,
                    customerName = customerName,
                    idempotencyKey = idempotencyKey
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun returnSale(
        businessId: String,
        sale: Sale,
        currentProducts: List<Product>
    ): Result<Unit> {
        var path = "users/unknown/businesses/$businessId/sales/${sale.id}"
        return try {
            val uid = requireUserId()
            if (sale.status == "returned") return Result.success(Unit)
            val today = localDateStr()
            val batch = db.batch()

            for (item in sale.items) {
                if (item.productId.isNotBlank()) {
                    val prod = currentProducts.firstOrNull { it.id == item.productId }
                    if (prod != null) {
                        val prodRef = bizDoc(uid, businessId).collection("products").document(prod.id)
                        batch.update(
                            prodRef,
                            mapOf("stock" to (prod.stock + item.qty), "updatedAt" to FieldValue.serverTimestamp())
                        )
                        val movRef = bizDoc(uid, businessId).collection("stockMovements").document()
                        batch.set(
                            movRef,
                            mapOf(
                                "userId" to uid,
                                "businessId" to businessId,
                                "productId" to prod.id,
                                "productName" to prod.name,
                                "type" to "sale_return",
                                "qty" to item.qty,
                                "reason" to "Return ${sale.invoiceNo}",
                                "date" to today,
                                "createdAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }
            }

            val saleRef = bizDoc(uid, businessId).collection("sales").document(sale.id)
            path = saleRef.path
            batch.update(saleRef, mapOf("status" to "returned", "updatedAt" to FieldValue.serverTimestamp()))

            if (sale.udhaarAmount > 0.0 && !sale.customerId.isNullOrBlank()) {
                val khRef = bizDoc(uid, businessId).collection("khataEntries").document()
                batch.set(
                    khRef,
                    mapOf(
                        "userId" to uid,
                        "businessId" to businessId,
                        "branchId" to sale.branchId,
                        "customerId" to sale.customerId,
                        "customerName" to sale.customerName,
                        "type" to "credit_reversal",
                        "amount" to sale.udhaarAmount,
                        "note" to "Sale Return ${sale.invoiceNo}",
                        "saleId" to sale.id,
                        "date" to today,
                        "idempotencyKey" to "${sale.id}_ret",
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun addSupplier(businessId: String, name: String, phone: String): Result<String> {
        var path = "users/unknown/businesses/$businessId/suppliers"
        return try {
            val uid = requireUserId()
            require(name.trim().isNotEmpty()) { "Supplier name required." }
            val ref = bizDoc(uid, businessId).collection("suppliers").document()
            path = ref.path
            ref.set(
                mapOf(
                    "userId" to uid,
                    "businessId" to businessId,
                    "name" to name.trim().take(80),
                    "phone" to phone.trim().take(20),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(ref.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

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
    ): Result<String> {
        var path = "users/unknown/businesses/$businessId/purchases"
        return try {
            val uid = requireUserId()
            require(qty > 0.0 && total > 0.0) { "Quantity and total must be positive." }
            val batch = db.batch()
            val purRef = bizDoc(uid, businessId).collection("purchases").document()
            path = purRef.path
            val payload = mapOf(
                "userId" to uid,
                "businessId" to businessId,
                "branchId" to branchId,
                "supplierId" to supplierId?.takeIf { it.isNotBlank() },
                "supplierName" to supplierName.ifBlank { "Cash Supplier" }.take(80),
                "productId" to product?.id,
                "productName" to (product?.name ?: productName).ifBlank { "General Stock" }.take(120),
                "qty" to qty,
                "total" to total,
                "paid" to paid.coerceAtLeast(0.0),
                "date" to date,
                "createdAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            batch.set(purRef, payload)

            if (product != null) {
                val prodRef = bizDoc(uid, businessId).collection("products").document(product.id)
                batch.update(prodRef, mapOf("stock" to (product.stock + qty), "updatedAt" to FieldValue.serverTimestamp()))
                val movRef = bizDoc(uid, businessId).collection("stockMovements").document()
                batch.set(
                    movRef,
                    mapOf(
                        "userId" to uid,
                        "businessId" to businessId,
                        "productId" to product.id,
                        "productName" to product.name,
                        "type" to "purchase",
                        "qty" to qty,
                        "reason" to "Purchase (${supplierName.ifBlank { "Cash Supplier" }})",
                        "date" to date,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(purRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun addExpense(
        businessId: String,
        branchId: String,
        category: String,
        amount: Double,
        note: String,
        date: String
    ): Result<String> {
        var path = "users/unknown/businesses/$businessId/expenses"
        return try {
            val uid = requireUserId()
            require(amount > 0.0) { "Amount must be positive." }
            val ref = bizDoc(uid, businessId).collection("expenses").document()
            path = ref.path
            ref.set(
                mapOf(
                    "userId" to uid,
                    "businessId" to businessId,
                    "branchId" to branchId,
                    "category" to category.ifBlank { "Other" }.take(40),
                    "amount" to amount,
                    "note" to note.trim().take(200),
                    "date" to date,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(ref.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun addStaff(
        businessId: String,
        branchId: String,
        name: String,
        role: String,
        phone: String,
        salary: Double
    ): Result<String> {
        var path = "users/unknown/businesses/$businessId/staff"
        return try {
            val uid = requireUserId()
            require(name.trim().isNotEmpty()) { "Staff name required." }
            val ref = bizDoc(uid, businessId).collection("staff").document()
            path = ref.path
            ref.set(
                mapOf(
                    "userId" to uid,
                    "businessId" to businessId,
                    "branchId" to branchId,
                    "name" to name.trim().take(80),
                    "role" to role.ifBlank { "Cashier" }.take(40),
                    "phone" to phone.trim().take(20),
                    "salary" to salary.coerceAtLeast(0.0),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(ref.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun markAttendance(
        businessId: String,
        staffId: String,
        date: String,
        status: String
    ): Result<Unit> {
        val docId = "${staffId}_${date}"
        var path = "users/unknown/businesses/$businessId/attendance/$docId"
        return try {
            val uid = requireUserId()
            val ref = bizDoc(uid, businessId).collection("attendance").document(docId)
            path = ref.path
            val snap = ref.get().await()
            if (snap.exists()) {
                ref.update(mapOf("status" to status, "updatedAt" to FieldValue.serverTimestamp())).await()
            } else {
                ref.set(
                    mapOf(
                        "userId" to uid,
                        "businessId" to businessId,
                        "staffId" to staffId,
                        "date" to date,
                        "status" to status,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun addTask(businessId: String, title: String, dueDate: String): Result<String> {
        var path = "users/unknown/businesses/$businessId/tasks"
        return try {
            val uid = requireUserId()
            require(title.trim().isNotEmpty()) { "Task title required." }
            val ref = bizDoc(uid, businessId).collection("tasks").document()
            path = ref.path
            ref.set(
                mapOf(
                    "userId" to uid,
                    "businessId" to businessId,
                    "title" to title.trim().take(200),
                    "dueDate" to dueDate,
                    "completed" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(ref.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleTask(businessId: String, task: ShopTask): Result<Unit> {
        var path = "users/unknown/businesses/$businessId/tasks/${task.id}"
        return try {
            val uid = requireUserId()
            val ref = bizDoc(uid, businessId).collection("tasks").document(task.id)
            path = ref.path
            ref.update(mapOf("completed" to !task.completed, "updatedAt" to FieldValue.serverTimestamp())).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun savePlansAsAdmin(plans: List<SubscriptionPlan>): Result<Unit> {
        return try {
            requireUserId()
            val batch = db.batch()
            for (p in plans) {
                val ref = db.collection("plans").document(p.code)
                batch.set(
                    ref,
                    mapOf(
                        "code" to p.code,
                        "name" to p.name.take(60),
                        "price" to p.price.coerceAtLeast(0.0),
                        "billingDays" to p.billingDays.coerceIn(1L, 3650L),
                        "badge" to p.badge.take(40),
                        "sortOrder" to p.sortOrder,
                        "highlights" to p.highlights.take(10),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "plans")
            Result.failure(e)
        }
    }
}
