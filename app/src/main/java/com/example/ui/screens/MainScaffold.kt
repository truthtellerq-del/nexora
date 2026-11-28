package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.i18n.stringsFor
import com.example.model.AppLanguage
import com.example.model.UiState
import com.example.ui.theme.Amber50
import com.example.ui.theme.Amber600
import com.example.ui.theme.Crimson50
import com.example.ui.theme.Crimson600
import com.example.ui.theme.Emerald50
import com.example.ui.theme.Emerald600
import com.example.ui.theme.Navy900
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate50
import com.example.ui.viewmodel.NavSection
import com.example.ui.viewmodel.NexoraViewModel
import com.example.util.canUseFeature

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    viewModel: NexoraViewModel,
    onSignOut: () -> Unit
) {
    val language by viewModel.language.collectAsStateWithLifecycle()
    val t = stringsFor(language)

    val businessesState by viewModel.businessesState.collectAsStateWithLifecycle()
    val businesses by viewModel.businesses.collectAsStateWithLifecycle()
    val activeBusiness by viewModel.activeBusiness.collectAsStateWithLifecycle()
    val branches by viewModel.branches.collectAsStateWithLifecycle()
    val activeBranch by viewModel.activeBranch.collectAsStateWithLifecycle()
    val plans by viewModel.plans.collectAsStateWithLifecycle()
    val subInfo by viewModel.subscriptionInfo.collectAsStateWithLifecycle()

    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val khataEntries by viewModel.khataEntries.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val stockMovements by viewModel.stockMovements.collectAsStateWithLifecycle()
    val sales by viewModel.sales.collectAsStateWithLifecycle()
    val suppliers by viewModel.suppliers.collectAsStateWithLifecycle()
    val purchases by viewModel.purchases.collectAsStateWithLifecycle()
    val expenses by viewModel.expenses.collectAsStateWithLifecycle()
    val staff by viewModel.staff.collectAsStateWithLifecycle()
    val attendance by viewModel.attendance.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()

    val showOnboarding by viewModel.showOnboarding.collectAsStateWithLifecycle()
    val isCreatingShop by viewModel.isCreatingShop.collectAsStateWithLifecycle()
    val showUpgradeModal by viewModel.showUpgradeModal.collectAsStateWithLifecycle()
    val currentSection by viewModel.currentSection.collectAsStateWithLifecycle()

    val sakhaMessages by viewModel.sakhaMessages.collectAsStateWithLifecycle()
    val sakhaPending by viewModel.sakhaPending.collectAsStateWithLifecycle()
    val sakhaDisambig by viewModel.sakhaDisambig.collectAsStateWithLifecycle()
    val sakhaBusy by viewModel.sakhaBusy.collectAsStateWithLifecycle()

    var showSakhaSheet by remember { mutableStateOf(false) }
    var showMoreSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.toastEvents.collect { evt ->
            snackbarHostState.showSnackbar(evt.message)
        }
    }

    val guardedNavigate = remember(subInfo, viewModel) {
        { target: NavSection ->
            val featureKey = when (target) {
                NavSection.Purchases -> "purchases"
                NavSection.Staff -> "staff"
                NavSection.Tasks -> "tasks"
                NavSection.Pos -> "pos"
                is NavSection.Khata -> "khata"
                is NavSection.Inventory -> "inventory"
                NavSection.Expenses -> "expenses"
                else -> "dashboard"
            }
            if (target == NavSection.Settings || target == NavSection.Admin || target == NavSection.Dashboard || canUseFeature(subInfo, featureKey)) {
                viewModel.navigate(target)
            } else {
                viewModel.openUpgradeModal(true)
            }
        }
    }

    when (val state = businessesState) {
        UiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize().background(Slate50), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = Emerald600)
                    Text("Loading Nexora ERP...", style = MaterialTheme.typography.bodyMedium)
                }
            }
            return
        }
        is UiState.Error -> {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Could not load shop data", style = MaterialTheme.typography.titleMedium, color = Crimson600)
                        Text(state.message, style = MaterialTheme.typography.bodyMedium)
                        Button(
                            onClick = viewModel::retryLoadBusinesses,
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald600, contentColor = Color.White),
                            modifier = Modifier.fillMaxWidth().testTag("workspace_retry_button")
                        ) {
                            Text("Retry")
                        }
                        OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth().testTag("workspace_error_sign_out_button")) {
                            Text(t.logout)
                        }
                    }
                }
            }
            return
        }
        is UiState.Success -> {
            if ((businesses.isEmpty() && !viewModel.isSuperAdmin) || showOnboarding) {
                OnboardingScreen(
                    defaultOwnerName = viewModel.userDisplayName,
                    language = language,
                    canCancel = businesses.isNotEmpty(),
                    isCreating = isCreatingShop,
                    onCancel = { viewModel.openOnboarding(false) },
                    onCreateShop = { name, owner, phone, type, city, st, lang, fy, gst ->
                        viewModel.createShop(name, owner, phone, type, city, st, lang, fy, gst)
                    }
                )
                return
            }
        }
    }

    // Back press on secondary tabs returns to Dashboard
    BackHandler(enabled = currentSection !is NavSection.Dashboard && !showSakhaSheet && !showMoreSheet && !showUpgradeModal) {
        viewModel.navigate(NavSection.Dashboard)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Slate50)) {
        val isWide = maxWidth >= 600.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (isWide) {
                NavigationRail(
                    containerColor = Color.White,
                    modifier = Modifier.fillMaxHeight().statusBarsPadding().navigationBarsPadding()
                ) {
                    Spacer(Modifier.size(8.dp))
                    Image(
                        painter = painterResource(R.drawable.nexora_launcher_fg_1795795737991),
                        contentDescription = "Nexora ERP",
                        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.size(12.dp))

                    val railItems = buildNavItems(t, viewModel.isSuperAdmin)
                    railItems.forEach { (itemSection, label, icon) ->
                        val selected = isSameSection(currentSection, itemSection)
                        NavigationRailItem(
                            selected = selected,
                            onClick = { guardedNavigate(itemSection) },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.testTag("rail_nav_${label.lowercase().replace(" ", "_")}")
                        )
                    }
                }
            }

            Scaffold(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                containerColor = Slate50,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    NexoraTopBar(
                        language = language,
                        businesses = businesses,
                        activeBusiness = activeBusiness,
                        branches = branches,
                        activeBranch = activeBranch,
                        subInfo = subInfo,
                        isSuperAdmin = viewModel.isSuperAdmin,
                        onSelectBusiness = viewModel::selectBusiness,
                        onSelectBranch = viewModel::selectBranch,
                        onAddBusiness = { viewModel.openOnboarding(true) },
                        onOpenUpgrade = { viewModel.openUpgradeModal(true) },
                        onSelectLanguage = viewModel::setLanguage,
                        onOpenSakha = {
                            if (canUseFeature(subInfo, "sakha")) showSakhaSheet = true
                            else viewModel.openUpgradeModal(true)
                        }
                    )
                },
                bottomBar = {
                    if (!isWide) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White)
                                .navigationBarsPadding()
                        ) {
                            HorizontalDivider(color = Slate200)
                            QuickActionStrip(
                                t = t,
                                onQuickSale = { guardedNavigate(NavSection.Pos) },
                                onAddUdhaar = { guardedNavigate(NavSection.Khata("outstanding")) },
                                onReceivePay = { guardedNavigate(NavSection.Khata("all")) },
                                onAskSakha = {
                                    if (canUseFeature(subInfo, "sakha")) showSakhaSheet = true
                                    else viewModel.openUpgradeModal(true)
                                }
                            )
                            NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                                NavigationBarItem(
                                    selected = currentSection is NavSection.Dashboard,
                                    onClick = { guardedNavigate(NavSection.Dashboard) },
                                    icon = { Icon(Icons.Default.Dashboard, contentDescription = t.navDashboard) },
                                    label = { Text(t.navDashboard, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    modifier = Modifier.testTag("bottom_nav_dashboard")
                                )
                                NavigationBarItem(
                                    selected = currentSection is NavSection.Pos,
                                    onClick = { guardedNavigate(NavSection.Pos) },
                                    icon = { Icon(Icons.Default.PointOfSale, contentDescription = t.navPos) },
                                    label = { Text(t.navPos, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    modifier = Modifier.testTag("bottom_nav_pos")
                                )
                                NavigationBarItem(
                                    selected = currentSection is NavSection.Khata,
                                    onClick = { guardedNavigate(NavSection.Khata()) },
                                    icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = t.navKhata) },
                                    label = { Text(t.navKhata, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    modifier = Modifier.testTag("bottom_nav_khata")
                                )
                                NavigationBarItem(
                                    selected = currentSection is NavSection.Inventory,
                                    onClick = { guardedNavigate(NavSection.Inventory()) },
                                    icon = { Icon(Icons.Default.Inventory2, contentDescription = t.navInventory) },
                                    label = { Text(t.navInventory, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    modifier = Modifier.testTag("bottom_nav_inventory")
                                )
                                NavigationBarItem(
                                    selected = currentSection !is NavSection.Dashboard &&
                                        currentSection !is NavSection.Pos &&
                                        currentSection !is NavSection.Khata &&
                                        currentSection !is NavSection.Inventory,
                                    onClick = { showMoreSheet = true },
                                    icon = { Icon(Icons.Default.Menu, contentDescription = "More modules") },
                                    label = { Text("More", maxLines = 1) },
                                    modifier = Modifier.testTag("bottom_nav_more")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 1100.dp)
                    ) {
                        if (isWide) {
                            QuickActionStrip(
                                t = t,
                                onQuickSale = { guardedNavigate(NavSection.Pos) },
                                onAddUdhaar = { guardedNavigate(NavSection.Khata("outstanding")) },
                                onReceivePay = { guardedNavigate(NavSection.Khata("all")) },
                                onAskSakha = {
                                    if (canUseFeature(subInfo, "sakha")) showSakhaSheet = true
                                    else viewModel.openUpgradeModal(true)
                                }
                            )
                        }

                        if (subInfo.expired && activeBusiness != null) {
                            Surface(
                                color = Crimson50,
                                modifier = Modifier.fillMaxWidth().clickable { viewModel.openUpgradeModal(true) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = Crimson600, modifier = Modifier.size(18.dp))
                                        Text("${t.trialExpired} — Read & Export mode active", style = MaterialTheme.typography.labelLarge, color = Crimson600)
                                    }
                                    Text(t.upgradeNow, style = MaterialTheme.typography.labelLarge, color = Navy900, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        val biz = activeBusiness
                        when (val sec = currentSection) {
                            NavSection.Dashboard -> {
                                if (biz != null) {
                                    DashboardScreen(
                                        language = language,
                                        sales = sales,
                                        purchases = purchases,
                                        expenses = expenses,
                                        customers = customers,
                                        khataEntries = khataEntries,
                                        products = products,
                                        onNavigate = guardedNavigate
                                    )
                                } else if (viewModel.isSuperAdmin) {
                                    SuperAdminScreen(
                                        plans = plans,
                                        activeBusiness = null,
                                        onSavePlans = viewModel::savePlansAsAdmin,
                                        onSetBizSubscription = viewModel::adminSetBusinessPlanState
                                    )
                                }
                            }
                            NavSection.Pos -> if (biz != null) {
                                PosScreen(
                                    language = language,
                                    business = biz,
                                    products = products,
                                    customers = customers,
                                    sales = sales,
                                    onCompleteSale = viewModel::completeSale,
                                    onReturnSale = viewModel::returnSale,
                                    onErrorMsg = { viewModel.emitToast(it, isError = true) }
                                )
                            }
                            is NavSection.Khata -> if (biz != null) {
                                KhataScreen(
                                    language = language,
                                    business = biz,
                                    customers = customers,
                                    khataEntries = khataEntries,
                                    initialFilter = sec.initialFilter,
                                    onAddCustomer = viewModel::addCustomer,
                                    onAddKhataEntry = viewModel::addKhataEntry
                                )
                            }
                            is NavSection.Inventory -> {
                                InventoryScreen(
                                    products = products,
                                    stockMovements = stockMovements,
                                    initialLowStockOnly = sec.lowStockOnly,
                                    onAddProduct = viewModel::addProduct,
                                    onAdjustStock = viewModel::adjustStock
                                )
                            }
                            NavSection.Purchases -> {
                                PurchasesScreen(
                                    suppliers = suppliers,
                                    purchases = purchases,
                                    products = products,
                                    onAddSupplier = viewModel::addSupplier,
                                    onAddPurchase = viewModel::addPurchase
                                )
                            }
                            NavSection.Expenses -> {
                                ExpensesScreen(
                                    expenses = expenses,
                                    onAddExpense = viewModel::addExpense
                                )
                            }
                            NavSection.Staff -> {
                                StaffBranchesScreen(
                                    branches = branches,
                                    staff = staff,
                                    attendance = attendance,
                                    onAddBranch = viewModel::addBranch,
                                    onAddStaff = viewModel::addStaff,
                                    onMarkAttendance = viewModel::markAttendance
                                )
                            }
                            NavSection.Tasks -> {
                                TasksScreen(
                                    tasks = tasks,
                                    onAddTask = viewModel::addTask,
                                    onToggleTask = viewModel::toggleTask
                                )
                            }
                            NavSection.Settings -> if (biz != null) {
                                SettingsScreen(
                                    language = language,
                                    business = biz,
                                    subInfo = subInfo,
                                    customers = customers,
                                    khataEntries = khataEntries,
                                    sales = sales,
                                    products = products,
                                    expenses = expenses,
                                    onLanguageChange = viewModel::setLanguage,
                                    onToggleNegativeStock = viewModel::toggleNegativeStockProtection,
                                    onOpenUpgrade = { viewModel.openUpgradeModal(true) },
                                    onAddBusiness = { viewModel.openOnboarding(true) },
                                    onSignOut = onSignOut
                                )
                            } else {
                                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Button(onClick = { viewModel.openOnboarding(true) }) { Text("+ Create Shop") }
                                        OutlinedButton(onClick = onSignOut) { Text(t.logout) }
                                    }
                                }
                            }
                            NavSection.Admin -> {
                                SuperAdminScreen(
                                    plans = plans,
                                    activeBusiness = activeBusiness,
                                    onSavePlans = viewModel::savePlansAsAdmin,
                                    onSetBizSubscription = viewModel::adminSetBusinessPlanState
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMoreSheet) {
        ModalBottomSheet(onDismissRequest = { showMoreSheet = false }, containerColor = Color.White) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Business Operations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.size(4.dp))
                val moreItems = buildList {
                    add(Triple(NavSection.Purchases, t.navPurchases, Icons.Default.ShoppingCart))
                    add(Triple(NavSection.Expenses, t.navExpenses, Icons.Default.ReceiptLong))
                    add(Triple(NavSection.Staff, "${t.navStaff} & Branches", Icons.Default.Badge))
                    add(Triple(NavSection.Tasks, t.navTasks, Icons.Default.CheckCircle))
                    add(Triple(NavSection.Settings, t.navSettings, Icons.Default.Settings))
                    if (viewModel.isSuperAdmin) {
                        add(Triple(NavSection.Admin, t.navAdmin, Icons.Default.AdminPanelSettings))
                    }
                }
                moreItems.forEach { (section, label, icon) ->
                    Surface(
                        onClick = {
                            showMoreSheet = false
                            guardedNavigate(section)
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSameSection(currentSection, section)) Emerald50 else Slate100,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("more_nav_${label.lowercase().replace(" ", "_")}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(icon, contentDescription = null, tint = Navy900)
                            Text(label, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                Spacer(Modifier.size(8.dp))
            }
        }
    }

    if (showSakhaSheet) {
        SakhaSheet(
            language = language,
            messages = sakhaMessages,
            pendingAction = sakhaPending,
            disambiguation = sakhaDisambig,
            isBusy = sakhaBusy,
            speakEvents = viewModel.sakhaSpeakText,
            onSendMessage = viewModel::sendSakhaMessage,
            onPickCustomer = viewModel::pickDisambiguatedCustomer,
            onConfirmPending = viewModel::confirmPendingSakhaAction,
            onCancelPending = viewModel::cancelPendingSakhaAction,
            onDismiss = { showSakhaSheet = false }
        )
    }

    if (showUpgradeModal) {
        UpgradeDialog(
            language = language,
            plans = plans,
            subInfo = subInfo,
            isSuperAdmin = viewModel.isSuperAdmin,
            onAdminActivatePlan = { planId, days ->
                viewModel.adminSetBusinessPlanState(
                    planId = planId,
                    status = "active",
                    planEndMillis = System.currentTimeMillis() + days * 86_400_000L
                )
                viewModel.openUpgradeModal(false)
            },
            onDismiss = { viewModel.openUpgradeModal(false) }
        )
    }
}

@Composable
private fun NexoraTopBar(
    language: AppLanguage,
    businesses: List<com.example.model.Business>,
    activeBusiness: com.example.model.Business?,
    branches: List<com.example.model.Branch>,
    activeBranch: com.example.model.Branch?,
    subInfo: com.example.model.SubscriptionInfo,
    isSuperAdmin: Boolean,
    onSelectBusiness: (String) -> Unit,
    onSelectBranch: (String) -> Unit,
    onAddBusiness: () -> Unit,
    onOpenUpgrade: () -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit,
    onOpenSakha: () -> Unit
) {
    val t = stringsFor(language)
    var bizMenuOpen by remember { mutableStateOf(false) }
    var branchMenuOpen by remember { mutableStateOf(false) }
    var langMenuOpen by remember { mutableStateOf(false) }

    Surface(color = Color.White, shadowElevation = 2.dp) {
        Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Image(
                        painter = painterResource(R.drawable.nexora_launcher_fg_1795795737991),
                        contentDescription = "Nexora ERP Logo",
                        modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )

                    Box {
                        AssistChip(
                            onClick = { bizMenuOpen = true },
                            label = {
                                Text(
                                    activeBusiness?.name ?: if (isSuperAdmin) "Super Admin" else "Nexora ERP",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = "Switch Shop") },
                            modifier = Modifier.testTag("topbar_business_switcher")
                        )
                        DropdownMenu(expanded = bizMenuOpen, onDismissRequest = { bizMenuOpen = false }) {
                            businesses.forEach { b ->
                                DropdownMenuItem(
                                    text = { Text("${b.name} (${b.city})") },
                                    onClick = {
                                        onSelectBusiness(b.id)
                                        bizMenuOpen = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("+ New Shop / Business", color = Emerald600, fontWeight = FontWeight.Bold) },
                                onClick = {
                                    bizMenuOpen = false
                                    onAddBusiness()
                                }
                            )
                        }
                    }

                    if (branches.size > 1) {
                        Box {
                            AssistChip(
                                onClick = { branchMenuOpen = true },
                                leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text(activeBranch?.name ?: "Branch", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                modifier = Modifier.testTag("topbar_branch_switcher")
                            )
                            DropdownMenu(expanded = branchMenuOpen, onDismissRequest = { branchMenuOpen = false }) {
                                branches.forEach { br ->
                                    DropdownMenuItem(
                                        text = { Text("${br.name} • ${br.city}") },
                                        onClick = {
                                            onSelectBranch(br.id)
                                            branchMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        onClick = onOpenUpgrade,
                        shape = RoundedCornerShape(10.dp),
                        color = if (subInfo.expired) Crimson50 else if (subInfo.activePlanCode == "trial") Amber50 else Emerald50,
                        modifier = Modifier.testTag("topbar_plan_badge")
                    ) {
                        Text(
                            text = if (subInfo.expired) t.trialExpired
                            else if (subInfo.activePlanCode == "trial") "${subInfo.daysLeft}${t.trialLeft}"
                            else subInfo.plan.name,
                            color = if (subInfo.expired) Crimson600 else if (subInfo.activePlanCode == "trial") Amber600 else Emerald600,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }

                    Box {
                        AssistChip(
                            onClick = { langMenuOpen = true },
                            label = { Text(language.displayName, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("topbar_language_button")
                        )
                        DropdownMenu(expanded = langMenuOpen, onDismissRequest = { langMenuOpen = false }) {
                            AppLanguage.entries.forEach { lang ->
                                DropdownMenuItem(
                                    text = { Text(lang.displayName) },
                                    onClick = {
                                        onSelectLanguage(lang)
                                        langMenuOpen = false
                                    }
                                )
                            }
                        }
                    }

                    Surface(
                        onClick = onOpenSakha,
                        shape = RoundedCornerShape(10.dp),
                        color = Emerald600,
                        modifier = Modifier.size(36.dp).testTag("topbar_sakha_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = t.askSakha, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionStrip(
    t: com.example.i18n.AppStrings,
    onQuickSale: () -> Unit,
    onAddUdhaar: () -> Unit,
    onReceivePay: () -> Unit,
    onAskSakha: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onQuickSale,
            colors = ButtonDefaults.buttonColors(containerColor = Navy900, contentColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.heightIn(min = 42.dp).testTag("quick_action_sale")
        ) {
            Icon(Icons.Default.PointOfSale, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(6.dp))
            Text(t.quickSale, style = MaterialTheme.typography.labelLarge)
        }
        Button(
            onClick = onAddUdhaar,
            colors = ButtonDefaults.buttonColors(containerColor = Crimson50, contentColor = Crimson600),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.heightIn(min = 42.dp).testTag("quick_action_udhaar")
        ) {
            Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(6.dp))
            Text(t.addUdhaar, style = MaterialTheme.typography.labelLarge)
        }
        Button(
            onClick = onReceivePay,
            colors = ButtonDefaults.buttonColors(containerColor = Emerald50, contentColor = Emerald600),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.heightIn(min = 42.dp).testTag("quick_action_pay")
        ) {
            Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(6.dp))
            Text(t.receivePayment, style = MaterialTheme.typography.labelLarge)
        }
        Button(
            onClick = onAskSakha,
            colors = ButtonDefaults.buttonColors(containerColor = Emerald600, contentColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.heightIn(min = 42.dp).testTag("quick_action_sakha")
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(6.dp))
            Text(t.askSakha, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun buildNavItems(
    t: com.example.i18n.AppStrings,
    isSuperAdmin: Boolean
): List<Triple<NavSection, String, ImageVector>> = buildList {
    add(Triple(NavSection.Dashboard, t.navDashboard, Icons.Default.Dashboard))
    add(Triple(NavSection.Pos, t.navPos, Icons.Default.PointOfSale))
    add(Triple(NavSection.Khata(), t.navKhata, Icons.Default.AccountBalanceWallet))
    add(Triple(NavSection.Inventory(), t.navInventory, Icons.Default.Inventory2))
    add(Triple(NavSection.Purchases, t.navPurchases, Icons.Default.ShoppingCart))
    add(Triple(NavSection.Expenses, t.navExpenses, Icons.Default.ReceiptLong))
    add(Triple(NavSection.Staff, t.navStaff, Icons.Default.Badge))
    add(Triple(NavSection.Tasks, t.navTasks, Icons.Default.CheckCircle))
    add(Triple(NavSection.Settings, t.navSettings, Icons.Default.Settings))
    if (isSuperAdmin) add(Triple(NavSection.Admin, t.navAdmin, Icons.Default.AdminPanelSettings))
}

private fun isSameSection(a: NavSection, b: NavSection): Boolean = when {
    a is NavSection.Khata && b is NavSection.Khata -> true
    a is NavSection.Inventory && b is NavSection.Inventory -> true
    else -> a == b
}
