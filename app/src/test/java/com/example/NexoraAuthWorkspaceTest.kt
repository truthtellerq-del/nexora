package com.example

import androidx.lifecycle.ViewModelStore
import com.example.ai.SakhaEngine
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
import com.example.model.Sale
import com.example.model.SaleItem
import com.example.model.ShopTask
import com.example.model.StaffMember
import com.example.model.StockMovement
import com.example.model.SubscriptionPlan
import com.example.model.Supplier
import com.example.model.UiState
import com.example.ui.viewmodel.NexoraViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class NexoraAuthWorkspaceTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private inline fun withClearedViewModel(
        vm: NexoraViewModel,
        block: (NexoraViewModel) -> Unit
    ) {
        val store = ViewModelStore()
        store.put("test_vm", vm)
        try {
            block(vm)
        } finally {
            store.clear()
        }
    }

    @Test
    fun existingUser_loadsWorkspaceWithoutOnboardingOrDuplicate() = runTest(testDispatcher) {
        val repo = FakeNexoraDataSource().apply {
            businessesByUid["uid-existing"] = MutableStateFlow(
                listOf(
                    Business(
                        id = "biz-1",
                        userId = "uid-existing",
                        name = "Sharma Kirana",
                        city = "Jaipur",
                        preferredLang = "hi"
                    )
                )
            )
        }

        val vm = NexoraViewModel(
            repository = repo,
            sakhaEngine = SakhaEngine(),
            currentUserId = "uid-existing",
            userEmail = "sharma@example.com",
            userDisplayName = "Sharma"
        )

        withClearedViewModel(vm) {
            val state = it.businessesState.first { s -> s !is UiState.Loading }
            assertTrue(state is UiState.Success)
            assertEquals(1, (state as UiState.Success).data.size)
            assertEquals("Sharma Kirana", state.data.first().name)
            assertEquals(AppLanguage.HI, it.language.value)
            assertFalse(it.showOnboarding.value)

            // Attempting to trigger createShop while workspace exists and onboarding modal is closed must not create a duplicate
            it.createShop("Duplicate Shop", "Sharma", "", "Kirana", "Jaipur", "RJ", AppLanguage.EN, "April", "")
            advanceUntilIdle()
            assertEquals(0, repo.createBusinessCallCount)
        }
        advanceUntilIdle()
    }

    @Test
    fun newUser_startsWithEmptyWorkspace_andCreatesSingleShopWithDoubleTapGuard() = runTest(testDispatcher) {
        val repo = FakeNexoraDataSource().apply {
            businessesByUid["uid-new"] = MutableStateFlow(emptyList())
        }

        val vm = NexoraViewModel(
            repository = repo,
            sakhaEngine = SakhaEngine(),
            currentUserId = "uid-new",
            userEmail = "newowner@example.com",
            userDisplayName = "Asha"
        )

        withClearedViewModel(vm) {
            val state = it.businessesState.first { s -> s !is UiState.Loading }
            assertTrue(state is UiState.Success)
            assertTrue((state as UiState.Success).data.isEmpty())

            // Simulate rapid double-tap on Launch Workspace button
            it.createShop("Asha Mart", "Asha", "9999999999", "Kirana", "Pune", "MH", AppLanguage.EN, "April", "")
            it.createShop("Asha Mart", "Asha", "9999999999", "Kirana", "Pune", "MH", AppLanguage.EN, "April", "")
            advanceUntilIdle()

            assertEquals(1, repo.createBusinessCallCount)
            assertEquals("biz-created-1", it.activeBusiness.first { b -> b != null }?.id)
            assertFalse(it.isCreatingShop.value)
        }
        advanceUntilIdle()
    }

    @Test
    fun workspaceLoadFailure_emitsErrorAndRecoversOnRetry() = runTest(testDispatcher) {
        val repo = FakeNexoraDataSource().apply {
            failBusinessesFirstLoad = true
            businessesByUid["uid-retry"] = MutableStateFlow(
                listOf(
                    Business(
                        id = "biz-recovered",
                        userId = "uid-retry",
                        name = "Recovered Store",
                        city = "Indore"
                    )
                )
            )
        }

        val vm = NexoraViewModel(
            repository = repo,
            sakhaEngine = SakhaEngine(),
            currentUserId = "uid-retry",
            userEmail = "retry@example.com",
            userDisplayName = "Retry User"
        )

        withClearedViewModel(vm) {
            val errorState = it.businessesState.first { s -> s is UiState.Error }
            assertEquals("Temporary network failure", (errorState as UiState.Error).message)

            val collectorJob = backgroundScope.async {
                it.businessesState.first { s -> s is UiState.Success }
            }

            it.retryLoadBusinesses()
            advanceUntilIdle()

            val recovered = collectorJob.await() as UiState.Success
            assertEquals("Recovered Store", recovered.data.single().name)
        }
        advanceUntilIdle()
    }

    @Test
    fun signOutAndReLoginAsDifferentUser_isolatesWorkspaceData() = runTest(testDispatcher) {
        val repo = FakeNexoraDataSource().apply {
            businessesByUid["uid-user-a"] = MutableStateFlow(
                listOf(Business(id = "biz-a", userId = "uid-user-a", name = "User A Store", city = "Delhi"))
            )
            businessesByUid["uid-user-b"] = MutableStateFlow(
                listOf(Business(id = "biz-b", userId = "uid-user-b", name = "User B Store", city = "Surat"))
            )
        }

        val vmA = NexoraViewModel(repo, SakhaEngine(), "uid-user-a", "a@example.com", "User A")
        withClearedViewModel(vmA) {
            val stateA = it.businessesState.first { s -> s is UiState.Success } as UiState.Success
            assertEquals("User A Store", stateA.data.single().name)
        }

        // After sign-out and sign-in with another account, MainActivity keys a fresh ViewModel on uid-user-b
        val vmB = NexoraViewModel(repo, SakhaEngine(), "uid-user-b", "b@example.com", "User B")
        withClearedViewModel(vmB) {
            val stateB = it.businessesState.first { s -> s is UiState.Success } as UiState.Success
            assertEquals("User B Store", stateB.data.single().name)
        }
        advanceUntilIdle()
    }

    private class FakeNexoraDataSource : NexoraDataSource {
        val businessesByUid = mutableMapOf<String, MutableStateFlow<List<Business>>>()
        var failBusinessesFirstLoad = false
        var createBusinessCallCount = 0

        override fun observePlans(): Flow<List<SubscriptionPlan>> = flowOf(DEFAULT_PLANS)

        override fun observeBusinesses(uid: String): Flow<List<Business>> = flow {
            if (failBusinessesFirstLoad) {
                failBusinessesFirstLoad = false
                throw IOException("Temporary network failure")
            }
            val source = businessesByUid.getOrPut(uid) { MutableStateFlow(emptyList()) }
            source.collect { emit(it) }
        }

        override fun observeBranches(uid: String, businessId: String): Flow<List<Branch>> = flowOf(emptyList())
        override fun observeCustomers(uid: String, businessId: String): Flow<List<Customer>> = flowOf(emptyList())
        override fun observeKhataEntries(uid: String, businessId: String): Flow<List<KhataEntry>> = flowOf(emptyList())
        override fun observeProducts(uid: String, businessId: String): Flow<List<Product>> = flowOf(emptyList())
        override fun observeStockMovements(uid: String, businessId: String): Flow<List<StockMovement>> = flowOf(emptyList())
        override fun observeSales(uid: String, businessId: String): Flow<List<Sale>> = flowOf(emptyList())
        override fun observeSuppliers(uid: String, businessId: String): Flow<List<Supplier>> = flowOf(emptyList())
        override fun observePurchases(uid: String, businessId: String): Flow<List<Purchase>> = flowOf(emptyList())
        override fun observeExpenses(uid: String, businessId: String): Flow<List<Expense>> = flowOf(emptyList())
        override fun observeStaff(uid: String, businessId: String): Flow<List<StaffMember>> = flowOf(emptyList())
        override fun observeAttendance(uid: String, businessId: String): Flow<List<AttendanceRecord>> = flowOf(emptyList())
        override fun observeTasks(uid: String, businessId: String): Flow<List<ShopTask>> = flowOf(emptyList())

        override suspend fun createBusinessWithMainBranch(
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
            createBusinessCallCount += 1
            val bizId = "biz-created-$createBusinessCallCount"
            val brId = "br-created-$createBusinessCallCount"
            val targetUid = businessesByUid.keys.firstOrNull() ?: "uid-new"
            val flow = businessesByUid.getOrPut(targetUid) { MutableStateFlow(emptyList()) }
            flow.value = flow.value + Business(
                id = bizId,
                userId = targetUid,
                name = name,
                ownerName = ownerName,
                phone = phone,
                businessType = businessType,
                city = city,
                state = state,
                preferredLang = preferredLang,
                fyStart = fyStart,
                gstin = gstin
            )
            return Result.success(bizId to brId)
        }

        override suspend fun updateBusinessSettings(
            businessId: String,
            preferredLang: String?,
            negativeStockProtection: Boolean?,
            planId: String?,
            subscriptionStatus: String?,
            trialEndMillis: Long?,
            planEndMillis: Long?
        ): Result<Unit> = Result.success(Unit)

        override suspend fun addBranch(businessId: String, name: String, city: String): Result<String> = Result.success("br-1")
        override suspend fun addCustomer(businessId: String, branchId: String, name: String, phone: String, openingBalance: Double, overdueDays: Long): Result<String> = Result.success("c-1")
        override suspend fun addKhataEntry(businessId: String, branchId: String, customerId: String, customerName: String, type: String, amount: Double, note: String, date: String, refEntryId: String?, saleId: String?, idempotencyKey: String): Result<String> = Result.success("k-1")
        override suspend fun addProduct(businessId: String, branchId: String, name: String, sku: String, unit: String, costPrice: Double, sellPrice: Double, taxRate: Double, openingStock: Double, lowStockThreshold: Double): Result<String> = Result.success("p-1")
        override suspend fun adjustStock(businessId: String, product: Product, deltaQty: Double, reason: String, negativeStockProtection: Boolean): Result<Unit> = Result.success(Unit)
        override suspend fun completePosSale(businessId: String, branchId: String, invoiceNo: String, items: List<SaleItem>, currentProducts: List<Product>, negativeStockProtection: Boolean, subtotal: Double, tax: Double, discount: Double, total: Double, totalCost: Double, paymentMode: String, paidNow: Double, udhaarAmount: Double, customerId: String?, customerName: String, idempotencyKey: String): Result<Sale> = Result.failure(UnsupportedOperationException())
        override suspend fun returnSale(businessId: String, sale: Sale, currentProducts: List<Product>): Result<Unit> = Result.success(Unit)
        override suspend fun addSupplier(businessId: String, name: String, phone: String): Result<String> = Result.success("sup-1")
        override suspend fun addPurchase(businessId: String, branchId: String, supplierId: String?, supplierName: String, product: Product?, productName: String, qty: Double, total: Double, paid: Double, date: String): Result<String> = Result.success("pur-1")
        override suspend fun addExpense(businessId: String, branchId: String, category: String, amount: Double, note: String, date: String): Result<String> = Result.success("exp-1")
        override suspend fun addStaff(businessId: String, branchId: String, name: String, role: String, phone: String, salary: Double): Result<String> = Result.success("st-1")
        override suspend fun markAttendance(businessId: String, staffId: String, date: String, status: String): Result<Unit> = Result.success(Unit)
        override suspend fun addTask(businessId: String, title: String, dueDate: String): Result<String> = Result.success("t-1")
        override suspend fun toggleTask(businessId: String, task: ShopTask): Result<Unit> = Result.success(Unit)
        override suspend fun savePlansAsAdmin(plans: List<SubscriptionPlan>): Result<Unit> = Result.success(Unit)
    }
}
