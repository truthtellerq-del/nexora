package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.SakhaEngine
import com.example.ai.SakhaParseResult
import com.example.data.NexoraDataSource
import com.example.model.AppLanguage
import com.example.model.AttendanceRecord
import com.example.model.Branch
import com.example.model.Business
import com.example.model.Customer
import com.example.model.DEFAULT_PLANS
import com.example.model.Expense
import com.example.model.KhataEntry
import com.example.model.Product
import com.example.model.Purchase
import com.example.model.SakhaChatMessage
import com.example.model.SakhaPendingAction
import com.example.model.Sale
import com.example.model.SaleItem
import com.example.model.ShopTask
import com.example.model.StaffMember
import com.example.model.StockMovement
import com.example.model.SubscriptionInfo
import com.example.model.SubscriptionPlan
import com.example.model.Supplier
import com.example.model.UiState
import com.example.util.computeCustomerLedger
import com.example.util.formatINR
import com.example.util.getSubscriptionInfo
import com.example.util.localDateStr
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface NavSection {
    data object Dashboard : NavSection
    data object Pos : NavSection
    data class Khata(val initialFilter: String = "all") : NavSection
    data class Inventory(val lowStockOnly: Boolean = false) : NavSection
    data object Purchases : NavSection
    data object Expenses : NavSection
    data object Staff : NavSection
    data object Tasks : NavSection
    data object Settings : NavSection
    data object Admin : NavSection
}

data class ToastEvent(val message: String, val isError: Boolean = false)

@OptIn(ExperimentalCoroutinesApi::class)
class NexoraViewModel(
    private val repository: NexoraDataSource,
    private val sakhaEngine: SakhaEngine,
    val currentUserId: String,
    val userEmail: String,
    val userDisplayName: String
) : ViewModel() {

    val isSuperAdmin: Boolean = userEmail.equals("truth.tellerq@gmail.com", ignoreCase = true)

    private val _language = MutableStateFlow(AppLanguage.EN)
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private val _selectedBizId = MutableStateFlow("")
    private val _selectedBranchId = MutableStateFlow("")

    private val _showOnboarding = MutableStateFlow(false)
    val showOnboarding: StateFlow<Boolean> = _showOnboarding.asStateFlow()

    private val _isCreatingShop = MutableStateFlow(false)
    val isCreatingShop: StateFlow<Boolean> = _isCreatingShop.asStateFlow()

    private val _retryTrigger = MutableStateFlow(0)

    private val _showUpgradeModal = MutableStateFlow(false)
    val showUpgradeModal: StateFlow<Boolean> = _showUpgradeModal.asStateFlow()

    private val _currentSection = MutableStateFlow<NavSection>(NavSection.Dashboard)
    val currentSection: StateFlow<NavSection> = _currentSection.asStateFlow()

    private val _toastEvents = MutableSharedFlow<ToastEvent>(extraBufferCapacity = 8)
    val toastEvents = _toastEvents.asSharedFlow()

    // Sakha AI State
    private val _sakhaMessages = MutableStateFlow(
        listOf(
            SakhaChatMessage(
                id = "welcome",
                isUser = false,
                text = "Namaste! Main Sakha (सखा) hoon. Bolkar ya likhkar udhaar, bikri, kharch ya hisaab poochein."
            )
        )
    )
    val sakhaMessages: StateFlow<List<SakhaChatMessage>> = _sakhaMessages.asStateFlow()

    private val _sakhaPending = MutableStateFlow<SakhaPendingAction?>(null)
    val sakhaPending: StateFlow<SakhaPendingAction?> = _sakhaPending.asStateFlow()

    private val _sakhaDisambig = MutableStateFlow<SakhaParseResult.DisambiguateCustomer?>(null)
    val sakhaDisambig: StateFlow<SakhaParseResult.DisambiguateCustomer?> = _sakhaDisambig.asStateFlow()

    private val _sakhaBusy = MutableStateFlow(false)
    val sakhaBusy: StateFlow<Boolean> = _sakhaBusy.asStateFlow()

    private val _sakhaSpeakText = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val sakhaSpeakText = _sakhaSpeakText.asSharedFlow()

    private fun <T> Flow<List<T>>.toListStateFlow(tag: String): StateFlow<List<T>> =
        this.catch { error ->
            val fsErr = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fsErr == null && error is CancellationException) throw error
            logWarn("Stream error ($tag)", fsErr ?: error)
            emit(emptyList())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val plans: StateFlow<List<SubscriptionPlan>> = repository.observePlans()
        .toListStateFlow("plans")
        .let { flow ->
            flow.map { it.ifEmpty { DEFAULT_PLANS } }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DEFAULT_PLANS)
        }

    val businessesState: StateFlow<UiState<List<Business>>> = _retryTrigger
        .flatMapLatest {
            repository.observeBusinesses(currentUserId)
                .map<List<Business>, UiState<List<Business>>> { UiState.Success(it) }
                .onStart { emit(UiState.Loading) }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val root = fsErr ?: error
                    logWarn("Stream error (businesses)", root)
                    emit(UiState.Error(root.localizedMessage ?: "Failed to load businesses"))
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val businesses: StateFlow<List<Business>> = businessesState
        .map { (it as? UiState.Success)?.data ?: emptyList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val activeBusiness: StateFlow<Business?> = combine(businesses, _selectedBizId) { list, sel ->
        list.firstOrNull { it.id == sel } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val subscriptionInfo: StateFlow<SubscriptionInfo> = combine(activeBusiness, plans) { biz, pl ->
        getSubscriptionInfo(biz, pl)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), SubscriptionInfo(false, 7, "trial", DEFAULT_PLANS.first()))

    private fun <T> observeForActiveBiz(loader: (String) -> Flow<List<T>>): Flow<List<T>> =
        activeBusiness.flatMapLatest { biz ->
            if (biz == null) flowOf(emptyList()) else loader(biz.id)
        }

    val branches: StateFlow<List<Branch>> = observeForActiveBiz { repository.observeBranches(currentUserId, it) }
        .toListStateFlow("branches")

    val activeBranch: StateFlow<Branch?> = combine(branches, _selectedBranchId) { list, sel ->
        list.firstOrNull { it.id == sel } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val customers: StateFlow<List<Customer>> = observeForActiveBiz { repository.observeCustomers(currentUserId, it) }
        .toListStateFlow("customers")

    val khataEntries: StateFlow<List<KhataEntry>> = observeForActiveBiz { repository.observeKhataEntries(currentUserId, it) }
        .toListStateFlow("khataEntries")

    val products: StateFlow<List<Product>> = observeForActiveBiz { repository.observeProducts(currentUserId, it) }
        .toListStateFlow("products")

    val stockMovements: StateFlow<List<StockMovement>> = observeForActiveBiz { repository.observeStockMovements(currentUserId, it) }
        .toListStateFlow("stockMovements")

    val sales: StateFlow<List<Sale>> = observeForActiveBiz { repository.observeSales(currentUserId, it) }
        .toListStateFlow("sales")

    val suppliers: StateFlow<List<Supplier>> = observeForActiveBiz { repository.observeSuppliers(currentUserId, it) }
        .toListStateFlow("suppliers")

    val purchases: StateFlow<List<Purchase>> = observeForActiveBiz { repository.observePurchases(currentUserId, it) }
        .toListStateFlow("purchases")

    val expenses: StateFlow<List<Expense>> = observeForActiveBiz { repository.observeExpenses(currentUserId, it) }
        .toListStateFlow("expenses")

    val staff: StateFlow<List<StaffMember>> = observeForActiveBiz { repository.observeStaff(currentUserId, it) }
        .toListStateFlow("staff")

    val attendance: StateFlow<List<AttendanceRecord>> = observeForActiveBiz { repository.observeAttendance(currentUserId, it) }
        .toListStateFlow("attendance")

    val tasks: StateFlow<List<ShopTask>> = observeForActiveBiz { repository.observeTasks(currentUserId, it) }
        .toListStateFlow("tasks")

    init {
        viewModelScope.launch {
            activeBusiness.collect { biz ->
                if (biz != null) {
                    _language.value = AppLanguage.fromCode(biz.preferredLang)
                }
            }
        }
    }

    fun emitToast(message: String, isError: Boolean = false) {
        _toastEvents.tryEmit(ToastEvent(message, isError))
    }

    fun setLanguage(lang: AppLanguage) {
        _language.value = lang
        val biz = activeBusiness.value ?: return
        viewModelScope.launch {
            repository.updateBusinessSettings(biz.id, preferredLang = lang.code)
        }
    }

    fun selectBusiness(businessId: String) {
        _selectedBizId.value = businessId
        _selectedBranchId.value = ""
    }

    fun selectBranch(branchId: String) {
        _selectedBranchId.value = branchId
    }

    fun retryLoadBusinesses() {
        _retryTrigger.value += 1
    }

    fun openOnboarding(open: Boolean) { _showOnboarding.value = open }
    fun openUpgradeModal(open: Boolean) { _showUpgradeModal.value = open }
    fun navigate(section: NavSection) { _currentSection.value = section }

    private fun ensureWriteAllowed(): Boolean {
        val sub = subscriptionInfo.value
        if (sub.expired) {
            _showUpgradeModal.value = true
            emitToast("Free trial expired. Upgrade plan to record new entries (data remains exportable).", true)
            return false
        }
        return true
    }

    fun createShop(
        name: String, ownerName: String, phone: String, businessType: String,
        city: String, state: String, preferredLang: AppLanguage, fyStart: String, gstin: String
    ) {
        if (_isCreatingShop.value) return
        if (!_showOnboarding.value && businesses.value.isNotEmpty()) return
        viewModelScope.launch {
            _isCreatingShop.value = true
            try {
                val res = repository.createBusinessWithMainBranch(
                    name, ownerName, phone, businessType, city, state, preferredLang.code, fyStart, gstin
                )
                res.onSuccess { (bizId, brId) ->
                    _selectedBizId.value = bizId
                    _selectedBranchId.value = brId
                    _language.value = preferredLang
                    _showOnboarding.value = false
                    emitToast("Shop created! 7-Day Full Access Trial started.")
                }.onFailure { err ->
                    emitToast(err.localizedMessage ?: "Failed to create shop", true)
                }
            } finally {
                _isCreatingShop.value = false
            }
        }
    }

    fun toggleNegativeStockProtection(enabled: Boolean) {
        val biz = activeBusiness.value ?: return
        viewModelScope.launch {
            repository.updateBusinessSettings(biz.id, negativeStockProtection = enabled)
                .onSuccess { emitToast("Negative stock protection updated.") }
                .onFailure { emitToast(it.localizedMessage ?: "Failed to update setting", true) }
        }
    }

    fun addBranch(name: String, city: String) {
        val biz = activeBusiness.value ?: return
        if (!ensureWriteAllowed()) return
        viewModelScope.launch {
            repository.addBranch(biz.id, name, city)
                .onSuccess { emitToast("Branch $name added.") }
                .onFailure { emitToast(it.localizedMessage ?: "Failed to add branch", true) }
        }
    }

    fun addCustomer(name: String, phone: String, openingBalance: Double, overdueDays: Long) {
        val biz = activeBusiness.value ?: return
        val brId = activeBranch.value?.id ?: "main"
        if (!ensureWriteAllowed()) return
        val dup = customers.value.firstOrNull {
            it.name.trim().equals(name.trim(), ignoreCase = true) && it.phone.trim() == phone.trim()
        }
        if (dup != null) {
            emitToast("Customer already exists — using their single running Khata.")
            return
        }
        viewModelScope.launch {
            repository.addCustomer(biz.id, brId, name, phone, openingBalance, overdueDays)
                .onSuccess { emitToast("Customer $name created.") }
                .onFailure { emitToast(it.localizedMessage ?: "Failed to add customer", true) }
        }
    }

    fun addKhataEntry(
        customerId: String,
        customerName: String,
        type: String,
        amount: Double,
        note: String,
        date: String,
        refEntryId: String? = null
    ) {
        val biz = activeBusiness.value ?: return
        val brId = activeBranch.value?.id ?: "main"
        if (!ensureWriteAllowed()) return
        viewModelScope.launch {
            repository.addKhataEntry(
                biz.id, brId, customerId, customerName, type, amount,
                note.ifBlank { if (type == "credit") "Udhaar" else "Payment Received" },
                date, refEntryId, null, UUID.randomUUID().toString()
            ).onSuccess {
                emitToast("Khata entry saved.")
            }.onFailure {
                emitToast(it.localizedMessage ?: "Failed to save entry", true)
            }
        }
    }

    fun addProduct(
        name: String, sku: String, unit: String, costPrice: Double,
        sellPrice: Double, taxRate: Double, openingStock: Double, lowStockThreshold: Double
    ) {
        val biz = activeBusiness.value ?: return
        val brId = activeBranch.value?.id ?: "main"
        if (!ensureWriteAllowed()) return
        viewModelScope.launch {
            repository.addProduct(
                biz.id, brId, name, sku, unit, costPrice, sellPrice, taxRate, openingStock, lowStockThreshold
            ).onSuccess { emitToast("Product $name added.") }
                .onFailure { emitToast(it.localizedMessage ?: "Failed to add product", true) }
        }
    }

    fun adjustStock(product: Product, deltaQty: Double, reason: String) {
        val biz = activeBusiness.value ?: return
        if (!ensureWriteAllowed()) return
        viewModelScope.launch {
            repository.adjustStock(biz.id, product, deltaQty, reason, biz.negativeStockProtection)
                .onSuccess { emitToast("Stock updated.") }
                .onFailure { emitToast(it.localizedMessage ?: "Stock update failed", true) }
        }
    }

    fun completeSale(
        items: List<SaleItem>,
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
        onComplete: (Sale) -> Unit
    ) {
        val biz = activeBusiness.value ?: return
        val brId = activeBranch.value?.id ?: "main"
        if (!ensureWriteAllowed()) return
        val invoiceNo = "INV-${(sales.value.size + 1).toString().padStart(4, '0')}"
        viewModelScope.launch {
            repository.completePosSale(
                biz.id, brId, invoiceNo, items, products.value, biz.negativeStockProtection,
                subtotal, tax, discount, total, totalCost, paymentMode, paidNow, udhaarAmount,
                customerId, customerName, UUID.randomUUID().toString()
            ).onSuccess { sale ->
                emitToast("Bill ${sale.invoiceNo} completed!")
                onComplete(sale)
            }.onFailure {
                emitToast(it.localizedMessage ?: "Checkout failed", true)
            }
        }
    }

    fun returnSale(sale: Sale) {
        val biz = activeBusiness.value ?: return
        if (!ensureWriteAllowed()) return
        viewModelScope.launch {
            repository.returnSale(biz.id, sale, products.value)
                .onSuccess { emitToast("Sale ${sale.invoiceNo} returned & stock restored.") }
                .onFailure { emitToast(it.localizedMessage ?: "Return failed", true) }
        }
    }

    fun addSupplier(name: String, phone: String) {
        val biz = activeBusiness.value ?: return
        if (!ensureWriteAllowed()) return
        viewModelScope.launch {
            repository.addSupplier(biz.id, name, phone)
                .onSuccess { emitToast("Supplier $name added.") }
                .onFailure { emitToast(it.localizedMessage ?: "Failed to add supplier", true) }
        }
    }

    fun addPurchase(
        supplierId: String?, supplierName: String, product: Product?, productName: String,
        qty: Double, total: Double, paid: Double, date: String
    ) {
        val biz = activeBusiness.value ?: return
        val brId = activeBranch.value?.id ?: "main"
        if (!ensureWriteAllowed()) return
        viewModelScope.launch {
            repository.addPurchase(biz.id, brId, supplierId, supplierName, product, productName, qty, total, paid, date)
                .onSuccess { emitToast("Purchase recorded.") }
                .onFailure { emitToast(it.localizedMessage ?: "Failed to record purchase", true) }
        }
    }

    fun addExpense(category: String, amount: Double, note: String, date: String) {
        val biz = activeBusiness.value ?: return
        val brId = activeBranch.value?.id ?: "main"
        if (!ensureWriteAllowed()) return
        viewModelScope.launch {
            repository.addExpense(biz.id, brId, category, amount, note, date)
                .onSuccess { emitToast("Expense saved.") }
                .onFailure { emitToast(it.localizedMessage ?: "Failed to save expense", true) }
        }
    }

    fun addStaff(name: String, role: String, phone: String, salary: Double) {
        val biz = activeBusiness.value ?: return
        val brId = activeBranch.value?.id ?: "main"
        if (!ensureWriteAllowed()) return
        viewModelScope.launch {
            repository.addStaff(biz.id, brId, name, role, phone, salary)
                .onSuccess { emitToast("Staff $name added.") }
                .onFailure { emitToast(it.localizedMessage ?: "Failed to add staff", true) }
        }
    }

    fun markAttendance(staffId: String, date: String, status: String) {
        val biz = activeBusiness.value ?: return
        if (!ensureWriteAllowed()) return
        viewModelScope.launch {
            repository.markAttendance(biz.id, staffId, date, status)
                .onFailure { emitToast(it.localizedMessage ?: "Attendance failed", true) }
        }
    }

    fun addTask(title: String, dueDate: String) {
        val biz = activeBusiness.value ?: return
        if (!ensureWriteAllowed()) return
        viewModelScope.launch {
            repository.addTask(biz.id, title, dueDate)
                .onSuccess { emitToast("Task added.") }
                .onFailure { emitToast(it.localizedMessage ?: "Failed to add task", true) }
        }
    }

    fun toggleTask(task: ShopTask) {
        val biz = activeBusiness.value ?: return
        if (!ensureWriteAllowed()) return
        viewModelScope.launch {
            repository.toggleTask(biz.id, task)
                .onFailure { emitToast(it.localizedMessage ?: "Failed to update task", true) }
        }
    }

    // Admin Plan & Trial Controls
    fun savePlansAsAdmin(updatedPlans: List<SubscriptionPlan>) {
        if (!isSuperAdmin) return
        viewModelScope.launch {
            repository.savePlansAsAdmin(updatedPlans)
                .onSuccess { emitToast("SaaS Plan prices updated globally.") }
                .onFailure { emitToast(it.localizedMessage ?: "Failed to save plans", true) }
        }
    }

    fun adminSetBusinessPlanState(planId: String, status: String, trialEndMillis: Long? = null, planEndMillis: Long? = null) {
        val biz = activeBusiness.value ?: return
        viewModelScope.launch {
            repository.updateBusinessSettings(
                biz.id,
                planId = planId,
                subscriptionStatus = status,
                trialEndMillis = trialEndMillis,
                planEndMillis = planEndMillis
            ).onSuccess { emitToast("Business subscription updated: $planId ($status)") }
                .onFailure { emitToast(it.localizedMessage ?: "Update failed", true) }
        }
    }

    // Sakha Assistant Logic
    fun sendSakhaMessage(rawText: String) {
        val txt = rawText.trim()
        if (txt.isEmpty() || _sakhaBusy.value) return
        _sakhaMessages.value = _sakhaMessages.value + SakhaChatMessage(UUID.randomUUID().toString(), true, txt)

        viewModelScope.launch {
            _sakhaBusy.value = true
            try {
                when (val res = sakhaEngine.processCommand(
                    rawText = txt,
                    customers = customers.value,
                    products = products.value,
                    khataEntries = khataEntries.value,
                    sales = sales.value,
                    purchases = purchases.value,
                    expenses = expenses.value,
                    pendingAction = _sakhaPending.value
                )) {
                    is SakhaParseResult.ConfirmPending -> {
                        _sakhaBusy.value = false
                        confirmPendingSakhaAction()
                        return@launch
                    }
                    is SakhaParseResult.CancelPending -> cancelPendingSakhaAction()
                    is SakhaParseResult.ReadAnswer -> appendSakhaReply(res.reply)
                    is SakhaParseResult.DisambiguateCustomer -> {
                        _sakhaDisambig.value = res
                        appendSakhaReply("Ek se zyada customer match hue (${res.matches.size}). Neeche se sahi customer chunein.")
                    }
                    is SakhaParseResult.ProposeAction -> {
                        _sakhaDisambig.value = null
                        _sakhaPending.value = res.action
                        appendSakhaReply("Kya main yeh entry save kar doon? (Kar do / Mat karo)")
                    }
                    is SakhaParseResult.Error -> appendSakhaReply("Error: ${res.message}")
                }
            } finally {
                _sakhaBusy.value = false
            }
        }
    }

    fun pickDisambiguatedCustomer(customer: Customer) {
        val disambig = _sakhaDisambig.value ?: return
        _sakhaDisambig.value = null
        val bal = computeCustomerLedger(customer, khataEntries.value).closingBalance
        val today = localDateStr()
        _sakhaPending.value = if (disambig.isPayment) {
            SakhaPendingAction.ReceivePayment(customer.id, customer.name, disambig.amount, bal, bal - disambig.amount, today, disambig.rawText)
        } else {
            SakhaPendingAction.AddUdhaar(customer.id, customer.name, disambig.amount, bal, bal + disambig.amount, today, disambig.rawText)
        }
        appendSakhaReply("${customer.name} chuna gaya. Kya main yeh entry save kar doon?")
    }

    fun confirmPendingSakhaAction() {
        val action = _sakhaPending.value ?: return
        val biz = activeBusiness.value ?: return
        val brId = activeBranch.value?.id ?: "main"
        if (!ensureWriteAllowed()) return

        viewModelScope.launch {
            _sakhaBusy.value = true
            try {
                val reply = when (action) {
                    is SakhaPendingAction.AddUdhaar -> {
                        val res = repository.addKhataEntry(
                            biz.id, brId, action.customerId, action.customerName, "credit",
                            action.amount, action.note, action.date, idempotencyKey = UUID.randomUUID().toString()
                        )
                        if (res.isSuccess) "Save ho gaya! ${action.customerName} ke khate mein ${formatINR(action.amount)} jod diye. Naya balance: ${formatINR(action.afterBal)}."
                        else "Error: ${res.exceptionOrNull()?.localizedMessage}"
                    }
                    is SakhaPendingAction.AddUdhaarNewCustomer -> {
                        val cRes = repository.addCustomer(biz.id, brId, action.customerName, "", 0.0, 15L)
                        val newCid = cRes.getOrNull()
                        if (newCid != null) {
                            repository.addKhataEntry(
                                biz.id, brId, newCid, action.customerName, "credit",
                                action.amount, action.note, action.date, idempotencyKey = UUID.randomUUID().toString()
                            )
                            "Naya khata banakar ${action.customerName} ke naam ${formatINR(action.amount)} save kar diya!"
                        } else {
                            "Error: ${cRes.exceptionOrNull()?.localizedMessage}"
                        }
                    }
                    is SakhaPendingAction.ReceivePayment -> {
                        val res = repository.addKhataEntry(
                            biz.id, brId, action.customerId, action.customerName, "payment",
                            action.amount, action.note, action.date, idempotencyKey = UUID.randomUUID().toString()
                        )
                        if (res.isSuccess) "Payment jama ho gaya! ${action.customerName} ka naya balance: ${formatINR(action.afterBal)}."
                        else "Error: ${res.exceptionOrNull()?.localizedMessage}"
                    }
                    is SakhaPendingAction.AddExpense -> {
                        val res = repository.addExpense(biz.id, brId, action.category, action.amount, action.note, action.date)
                        if (res.isSuccess) "${action.category} expense ${formatINR(action.amount)} save ho gaya."
                        else "Error: ${res.exceptionOrNull()?.localizedMessage}"
                    }
                    is SakhaPendingAction.AddPurchase -> {
                        val res = repository.addPurchase(biz.id, brId, null, "Cash Supplier", null, action.note, 1.0, action.amount, action.amount, action.date)
                        if (res.isSuccess) "Kharidi ${formatINR(action.amount)} save ho gayi."
                        else "Error: ${res.exceptionOrNull()?.localizedMessage}"
                    }
                    is SakhaPendingAction.QuickSale -> {
                        val inv = "INV-${(sales.value.size + 1).toString().padStart(4, '0')}"
                        val item = SaleItem(productId = "", name = action.note, unit = "bill", qty = 1.0, price = action.amount, costPrice = 0.0, taxRate = 0.0)
                        val res = repository.completePosSale(
                            biz.id, brId, inv, listOf(item), products.value, biz.negativeStockProtection,
                            action.amount, 0.0, 0.0, action.amount, 0.0, action.paymentMode, action.amount, 0.0,
                            null, "Sakha Quick Sale", UUID.randomUUID().toString()
                        )
                        if (res.isSuccess) "Quick Sale ${formatINR(action.amount)} (${action.paymentMode}) save ho gayi!"
                        else "Error: ${res.exceptionOrNull()?.localizedMessage}"
                    }
                    is SakhaPendingAction.StockAdjust -> {
                        val prod = products.value.firstOrNull { it.id == action.productId }
                        if (prod != null) {
                            val res = repository.adjustStock(biz.id, prod, action.qty, "Sakha voice stock in", biz.negativeStockProtection)
                            if (res.isSuccess) "${action.productName} mein +${action.qty} ${action.unit} stock add ho gaya."
                            else "Error: ${res.exceptionOrNull()?.localizedMessage}"
                        } else "Product not found."
                    }
                }
                _sakhaPending.value = null
                appendSakhaReply(reply)
            } finally {
                _sakhaBusy.value = false
            }
        }
    }

    fun cancelPendingSakhaAction() {
        _sakhaPending.value = null
        _sakhaDisambig.value = null
        appendSakhaReply("Theek hai, entry cancel kar di gayi hai.")
    }

    private fun appendSakhaReply(text: String) {
        _sakhaMessages.value = _sakhaMessages.value + SakhaChatMessage(UUID.randomUUID().toString(), false, text)
        _sakhaSpeakText.tryEmit(text)
    }

    private fun logWarn(message: String, error: Throwable) {
        runCatching { Log.w(TAG, message, error) }
    }

    private companion object {
        const val TAG = "NexoraViewModel"
    }
}
