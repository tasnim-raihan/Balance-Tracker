package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import android.content.Context
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Intent
import com.example.data.LedgerEntry
import com.example.data.FinancialTransaction
import com.example.data.WalletAccount
import com.example.domain.LedgerCalculator
import com.example.viewmodel.LedgerViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerDashboard(
    viewModel: LedgerViewModel,
    isDarkTheme: Boolean = false,
    onThemeToggle: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(0) } // 0 = Points Ledger, 1 = Manual Transactions
    var isAddDialogOpen by remember { mutableStateOf(false) }
    var isAddTxDialogOpen by remember { mutableStateOf(false) }
    var isDeleteAllConfirmOpen by remember { mutableStateOf(false) }
    var isDeleteAllTxConfirmOpen by remember { mutableStateOf(false) }

    var ledgerSearchQuery by remember { mutableStateOf("") }
    var ledgerTypeFilter by remember { mutableStateOf("All") }
    var ledgerSortOption by remember { mutableStateOf("Newest First") }
    var isLedgerFiltersExpanded by remember { mutableStateOf(false) }
    var detailedEntry by remember { mutableStateOf<LedgerEntry?>(null) }

    val context = LocalContext.current
    var lastBackPressTime by remember { mutableLongStateOf(0L) }

    BackHandler {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastBackPressTime < 2000) {
            (context as? android.app.Activity)?.finish()
        } else {
            Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
            lastBackPressTime = currentTime
        }
    }

    var isExportDialogOpen by remember { mutableStateOf(false) }
    var exportTargetType by remember { mutableStateOf("Ledger") } 
    var csvTextToWrite by remember { mutableStateOf("") }
    var jsonTextToWrite by remember { mutableStateOf("") }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(csvTextToWrite.toByteArray())
                    outputStream.flush()
                }
                Toast.makeText(context, "$exportTargetType exported successfully!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Export failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val createJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonTextToWrite.toByteArray())
                    outputStream.flush()
                }
                Toast.makeText(context, "$exportTargetType backup exported successfully!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Backup failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val jsonString = inputStream.bufferedReader().use { it.readText() }
                    viewModel.importLedgerFromJson(jsonString,
                        onSuccess = { count ->
                            Toast.makeText(context, "Successfully imported $count ledger records!", Toast.LENGTH_LONG).show()
                        },
                        onError = { error ->
                            Toast.makeText(context, "Import failed: $error", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error reading file: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    var filterCategory by remember { mutableStateOf("All") }
    var filterDateRangePreset by remember { mutableStateOf("All Time") }
    var customStartDate by remember { mutableStateOf("") }
    var customEndDate by remember { mutableStateOf("") }
    var isFabExpanded by remember { mutableStateOf(false) }
    var isLedgerFullscreen by remember { mutableStateOf(false) }

    var isDraftWarningOpen by remember { mutableStateOf(false) }

    val curTxDesc by viewModel.txDescriptionText.collectAsStateWithLifecycle()
    val curTxAmt by viewModel.txAmountText.collectAsStateWithLifecycle()
    val curPrevPoints by viewModel.previousPointsText.collectAsStateWithLifecycle()
    val curAvailPoints by viewModel.availablePointsText.collectAsStateWithLifecycle()

    val isTxFormValid by viewModel.isTxFormValid.collectAsStateWithLifecycle()
    val isPointsFormValid by viewModel.isFormValid.collectAsStateWithLifecycle()

    val isFormDirty = remember(curTxDesc, curTxAmt, curPrevPoints, curAvailPoints) {
        curTxDesc.isNotBlank() || curTxAmt.isNotBlank() || curPrevPoints.isNotBlank() || curAvailPoints.isNotBlank()
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val performBackAction = {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
            Unit
        } else if (isLedgerFullscreen) {
            isLedgerFullscreen = false
        } else if (isAddDialogOpen || isAddTxDialogOpen) {
            if (isFormDirty) {
                isDraftWarningOpen = true
            } else {
                isAddDialogOpen = false
                isAddTxDialogOpen = false
            }
        } else if (selectedTab != 0) {
            if (isFormDirty && selectedTab == 3) {
                isDraftWarningOpen = true
            } else {
                selectedTab = 0
            }
        } else {
            if (isFormDirty) {
                isDraftWarningOpen = true
            } else {
                scope.launch {
                    if (drawerState.isOpen) {
                        drawerState.close()
                    } else {
                        drawerState.open()
                    }
                }
                Unit
            }
        }
    }

    BackHandler(enabled = true) {
        performBackAction()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !isLedgerFullscreen,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                modifier = Modifier.width(300.dp)
            ) {
                Spacer(modifier = Modifier.statusBarsPadding())
                
                // Header / App Branding
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(15.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Balance Tracker",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Active Account Ledger",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Navigation items
                val drawerItems = listOf(
                    Triple("Point Ledger Summary", Icons.Default.Info, 0),
                    Triple("Ledger History", Icons.Default.List, 1),
                    Triple("Wallet Balance", Icons.Default.ShoppingCart, 2),
                    Triple("Income & Expense Ledger", Icons.Default.AccountBalanceWallet, 3)
                )
                
                drawerItems.forEach { (title, icon, index) ->
                    val selected = selectedTab == index
                    NavigationDrawerItem(
                        label = { 
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            ) 
                        },
                        selected = selected,
                        onClick = {
                            selectedTab = index
                            scope.launch { drawerState.close() }
                        },
                        icon = { 
                            Icon(
                                imageVector = icon, 
                                contentDescription = null,
                                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            ) 
                        },
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .testTag("drawer_item_$index"),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = {
                        viewModel.resetForm()
                        isAddDialogOpen = true
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .testTag("drawer_primary_action_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Log Points Entry",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Log Points Entry",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                Text(
                    text = "UTCOffsets Sync v1.4",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier
                        .padding(24.dp)
                        .align(Alignment.CenterHorizontally)
                )
            }
        }
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
                topBar = {
                    AnimatedVisibility(visible = !isLedgerFullscreen) {
                    TopAppBar(
                        navigationIcon = {
                            AppDedicatedBackButton(
                                selectedTab = selectedTab,
                                isFormDirty = isFormDirty,
                                onClick = { performBackAction() },
                                onOpenDrawer = {
                                    scope.launch {
                                        if (drawerState.isOpen) drawerState.close() else drawerState.open()
                                    }
                                }
                            )
                        },
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = when (selectedTab) {
                                        0 -> "Ledger Summary"
                                        1 -> "Ledger History"
                                        2 -> "Wallet Balance"
                                        else -> "Income & Expense"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.SansSerif,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = onThemeToggle,
                                modifier = Modifier.testTag("theme_toggle_button")
                            ) {
                                Icon(
                                    imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    contentDescription = if (isDarkTheme) "Switch to Light Mode" else "Switch to Dark Mode",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = { isLedgerFullscreen = !isLedgerFullscreen },
                                modifier = Modifier.testTag("fullscreen_toggle_button")
                            ) {
                                Icon(
                                    imageVector = if (isLedgerFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = "Toggle Immersive Focus Screen",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            if ((selectedTab == 0 || selectedTab == 1) && entries.isNotEmpty()) {
                                IconButton(
                                    onClick = { 
                                        exportTargetType = "Ledger"
                                        isExportDialogOpen = true 
                                    },
                                    modifier = Modifier.testTag("export_ledger_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Export Ledger CSV",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(
                                    onClick = { isDeleteAllConfirmOpen = true },
                                    modifier = Modifier.testTag("delete_all_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Clear All Records",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            } else if (selectedTab == 3) {
                                val transactions by viewModel.transactions.collectAsStateWithLifecycle()
                                if (transactions.isNotEmpty()) {
                                    IconButton(
                                        onClick = { 
                                            exportTargetType = "Transactions"
                                            isExportDialogOpen = true 
                                        },
                                        modifier = Modifier.testTag("export_transactions_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Export Transactions CSV",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.deleteAllTransactions() },
                                        modifier = Modifier.testTag("delete_all_transactions_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Clear All Transactions",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                        )
                    )
                }
            },
            floatingActionButton = {
                if (!isLedgerFullscreen) {
                    FloatingActionButton(
                        onClick = {
                            viewModel.resetForm()
                            isAddDialogOpen = true
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("add_ledger_fab")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Log Points Entry",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                if (isLoading) {
                    DashboardSkeletonLoader(selectedTab = selectedTab)
                } else if (selectedTab == 0) {
                    // Points Ledger Summary screen (with newly requested scrolling feature!)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        MetricsSummaryPanel(entries)
                        DailySummaryWidget(entries)
                        MonthlySummaryWidget(entries)

                        // Ledger History Preview Section
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.List,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "LEDGER HISTORY PREVIEW",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                TextButton(onClick = { selectedTab = 1 }) {
                                    Text("View Full History")
                                }
                            }

                            if (entries.isEmpty()) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = "No recent entries found.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(24.dp),
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                entries.take(3).forEach { entry ->
                                    LedgerCard(
                                        entry = entry,
                                        onEdit = { 
                                            viewModel.startEditingEntry(entry)
                                            isAddDialogOpen = true 
                                        },
                                        onDelete = { viewModel.deleteEntry(entry.id) },
                                        onCardClick = { detailedEntry = entry }
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                } else if (selectedTab == 1) {
                    // Ledger History Screen
                    val filteredEntries = remember(entries, ledgerSearchQuery, ledgerTypeFilter, ledgerSortOption) {
                        entries.filter { entry ->
                            val matchesSearch = entry.deficitSpendingNotes.contains(ledgerSearchQuery, ignoreCase = true) ||
                                    entry.transactionType.contains(ledgerSearchQuery, ignoreCase = true)
                            val matchesType = when (ledgerTypeFilter) {
                                "All" -> true
                                "Sale" -> entry.transactionType == "Sale"
                                "Product in Hand" -> entry.transactionType == "Product in Hand"
                                "Deficit Only" -> entry.deficit > 0
                                "Loss Only" -> entry.loss > 0
                                else -> true
                            }
                            matchesSearch && matchesType
                        }.sortedWith { a, b ->
                            when (ledgerSortOption) {
                                "Newest First" -> b.timestamp.compareTo(a.timestamp)
                                "Oldest First" -> a.timestamp.compareTo(b.timestamp)
                                "Deficit (High to Low)" -> b.deficit.compareTo(a.deficit)
                                "Net Change (High to Low)" -> {
                                    val netA = abs(a.previousPoints - a.availablePoints)
                                    val netB = abs(b.previousPoints - b.availablePoints)
                                    netB.compareTo(netA)
                                }
                                else -> b.timestamp.compareTo(a.timestamp)
                            }
                        }
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        if (entries.isEmpty()) {
                            EmptyStateView {
                                viewModel.resetForm()
                                isAddDialogOpen = true
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.clickable { isLedgerFullscreen = !isLedgerFullscreen }.padding(4.dp)
                                    ) {
                                        Text(
                                            text = "Ledger History",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(100.dp)
                                        ) {
                                            Text(
                                                text = "${filteredEntries.size}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                        Icon(
                                            imageVector = if (isLedgerFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                            contentDescription = "Toggle Fullscreen",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { isLedgerFiltersExpanded = !isLedgerFiltersExpanded },
                                        modifier = Modifier.testTag("ledger_filter_toggle")
                                    ) {
                                        Icon(
                                            imageVector = if (isLedgerFiltersExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.Settings,
                                            contentDescription = "Toggle Ledger Filtering Options",
                                            tint = if (isLedgerFiltersExpanded || ledgerTypeFilter != "All" || ledgerSortOption != "Newest First" || ledgerSearchQuery.isNotEmpty()) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.outline
                                            }
                                        )
                                    }
                                }

                                AnimatedVisibility(
                                    visible = isLedgerFiltersExpanded || ledgerSearchQuery.isNotEmpty(),
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)), RoundedCornerShape(12.dp))
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = ledgerSearchQuery,
                                            onValueChange = { ledgerSearchQuery = it },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("ledger_search_field"),
                                            placeholder = { Text("Search notes, types...") },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Search,
                                                    contentDescription = "Search"
                                                )
                                            },
                                            trailingIcon = if (ledgerSearchQuery.isNotEmpty()) {
                                                {
                                                    IconButton(onClick = { ledgerSearchQuery = "" }) {
                                                        Icon(
                                                            imageVector = Icons.Default.Clear,
                                                            contentDescription = "Clear Search"
                                                        )
                                                    }
                                                }
                                            } else null,
                                            singleLine = true,
                                            textStyle = MaterialTheme.typography.bodyMedium,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                            )
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "FILTER BY TYPE",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(bottom = 4.dp)
                                                )
                                                var isTypeDropdownExpanded by remember { mutableStateOf(false) }
                                                Box(modifier = Modifier.fillMaxWidth()) {
                                                    Surface(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable { isTypeDropdownExpanded = true }
                                                            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(8.dp))
                                                            .padding(10.dp),
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = MaterialTheme.colorScheme.surface
                                                    ) {
                                                        Row(
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(
                                                                text = ledgerTypeFilter,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurface
                                                            )
                                                            Icon(
                                                                imageVector = Icons.Default.ArrowDropDown,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }
                                                    DropdownMenu(
                                                        expanded = isTypeDropdownExpanded,
                                                        onDismissRequest = { isTypeDropdownExpanded = false }
                                                    ) {
                                                        listOf("All", "Sale", "Product in Hand", "Deficit Only", "Loss Only").forEach { type ->
                                                            DropdownMenuItem(
                                                                text = { Text(type, style = MaterialTheme.typography.bodyMedium) },
                                                                onClick = {
                                                                    ledgerTypeFilter = type
                                                                    isTypeDropdownExpanded = false
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "SORT BY",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(bottom = 4.dp)
                                                )
                                                var isSortDropdownExpanded by remember { mutableStateOf(false) }
                                                Box(modifier = Modifier.fillMaxWidth()) {
                                                    Surface(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable { isSortDropdownExpanded = true }
                                                            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(8.dp))
                                                            .padding(10.dp),
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = MaterialTheme.colorScheme.surface
                                                    ) {
                                                        Row(
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(
                                                                text = ledgerSortOption,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurface,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            Icon(
                                                                imageVector = Icons.Default.ArrowDropDown,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }
                                                    DropdownMenu(
                                                        expanded = isSortDropdownExpanded,
                                                        onDismissRequest = { isSortDropdownExpanded = false }
                                                    ) {
                                                        listOf("Newest First", "Oldest First", "Deficit (High to Low)", "Net Change (High to Low)").forEach { option ->
                                                            DropdownMenuItem(
                                                                text = { Text(option, style = MaterialTheme.typography.bodyMedium) },
                                                                onClick = {
                                                                    ledgerSortOption = option
                                                                    isSortDropdownExpanded = false
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        if (ledgerTypeFilter != "All" || ledgerSortOption != "Newest First" || ledgerSearchQuery.isNotEmpty()) {
                                            TextButton(
                                                onClick = {
                                                    ledgerTypeFilter = "All"
                                                    ledgerSortOption = "Newest First"
                                                    ledgerSearchQuery = ""
                                                },
                                                modifier = Modifier.align(Alignment.End)
                                            ) {
                                                Text(
                                                    text = "Clear active filters",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (filteredEntries.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Text(
                                            text = "No matching records found",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "Try adjusting your search notes, types, or sort order",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .weight(1f)
                                        .testTag("ledger_list"),
                                    contentPadding = PaddingValues(bottom = 80.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(
                                        items = filteredEntries,
                                        key = { it.id }
                                    ) { entry ->
                                        LedgerCard(
                                            entry = entry,
                                            onEdit = {
                                                viewModel.startEditingEntry(entry)
                                                isAddDialogOpen = true
                                            },
                                            onDelete = { viewModel.deleteEntry(entry.id) },
                                            onCardClick = {
                                                detailedEntry = entry
                                            },
                                            modifier = Modifier.animateItem()
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (selectedTab == 2) {
                    // Wallet Accounts / Balance View
                    val walletAccounts by viewModel.walletAccounts.collectAsStateWithLifecycle()
                    val totalWalletBalance by viewModel.totalWalletBalance.collectAsStateWithLifecycle()
                    WalletAccountsView(
                        walletAccounts = walletAccounts,
                        totalWalletBalance = totalWalletBalance,
                        onUpdateBalance = { id, balance -> viewModel.updateWalletAccountBalance(id, balance) }
                    )
                } else {
                    // Income & Expense Ledger View
                    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
                    IncomeExpenseLedgerView(
                        viewModel = viewModel,
                        transactions = transactions
                    )
                }
            }
        }
    }

    AnimatedVisibility(
            visible = isLedgerFullscreen,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it })
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("fullscreen_hud_bar"),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    tonalElevation = 8.dp,
                    shape = RoundedCornerShape(28.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { isLedgerFullscreen = false },
                            modifier = Modifier.testTag("fullscreen_exit_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Exit Fullscreen Mode",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(100.dp)
                            ) {
                                Text(
                                    text = "IMMERSIVE FOCUS",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            Text(
                                text = when (selectedTab) {
                                    0 -> "Summary"
                                    1 -> "History"
                                    2 -> "Wallet Balance"
                                    else -> "Income & Expense"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (isFormDirty) {
                                Surface(
                                    color = Color(0xFFFFECEB),
                                    border = BorderStroke(1.dp, Color(0xFFF44336)),
                                    shape = RoundedCornerShape(100.dp)
                                ) {
                                    Text(
                                        text = "DRAFT",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFFD32F2F),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = { isLedgerFullscreen = false },
                                modifier = Modifier.testTag("fullscreen_shrink_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FullscreenExit,
                                    contentDescription = "Exit Immersive View",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

    // Modal Detail Dialog (Points)
    detailedEntry?.let { entry ->
        LedgerDetailDialog(
            entry = entry,
            onDismiss = { detailedEntry = null },
            onEdit = {
                detailedEntry = null
                viewModel.startEditingEntry(entry)
                isAddDialogOpen = true
            },
            onDelete = {
                detailedEntry = null
                viewModel.deleteEntry(entry.id)
            }
        )
    }

    // Modal Add Dialog (Points)
    if (isAddDialogOpen) {
        AddLedgerDialog(
            viewModel = viewModel,
            onDismiss = {
                viewModel.resetForm()
                isAddDialogOpen = false
            },
            onSave = {
                viewModel.saveEntry()
                isAddDialogOpen = false
            }
        )
    }

    // Unsaved Draft Warnings adaptive dialog
    if (isDraftWarningOpen) {
        val activeSaveEnabled = isTxFormValid || isPointsFormValid
        DraftWarningDialog(
            onDismiss = { isDraftWarningOpen = false },
            onAutoSave = {
                isDraftWarningOpen = false
                isAddDialogOpen = false
                isAddTxDialogOpen = false
                selectedTab = 0
            },
            onDiscard = {
                viewModel.resetTxForm()
                viewModel.resetForm()
                isDraftWarningOpen = false
                isAddDialogOpen = false
                isAddTxDialogOpen = false
                selectedTab = 0
            },
            onQuickSave = {
                if (isTxFormValid) {
                    viewModel.saveTransaction()
                } else if (isPointsFormValid) {
                    viewModel.saveEntry()
                }
                isDraftWarningOpen = false
                isAddDialogOpen = false
                isAddTxDialogOpen = false
                selectedTab = 0
            },
            isSaveEnabled = activeSaveEnabled
        )
    }

    // Modal Add Dialog (Financial Transactions)
    if (isAddTxDialogOpen) {
        AddTransactionDialog(
            viewModel = viewModel,
            onDismiss = {
                viewModel.resetTxForm()
                isAddTxDialogOpen = false
            },
            onSave = {
                viewModel.saveTransaction()
                isAddTxDialogOpen = false
            }
        )
    }

    // Modal Delete-All Confirmation
    if (isDeleteAllConfirmOpen) {
        AlertDialog(
            onDismissRequest = { isDeleteAllConfirmOpen = false },
            title = { Text("Reset Application Ledger?") },
            text = { Text("This will permanently delete all local database entries. This operation is offline and cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAllEntries()
                        isDeleteAllConfirmOpen = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { isDeleteAllConfirmOpen = false }) {
                    Text("Cancel")
                }
            },
            modifier = Modifier.testTag("delete_all_dialog")
        )
    }

    // Modal Delete-All Transactions Confirmation
    if (isDeleteAllTxConfirmOpen) {
        AlertDialog(
            onDismissRequest = { isDeleteAllTxConfirmOpen = false },
            title = { Text("Delete All Transactions?") },
            text = { Text("This will permanently delete all local manual financial records. This operation is strictly offline and irreversible.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAllTransactions()
                        isDeleteAllTxConfirmOpen = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                TextButton(onClick = { isDeleteAllTxConfirmOpen = false }) {
                    Text("Cancel")
                }
            },
            modifier = Modifier.testTag("delete_all_tx_dialog")
        )
    }

    // Modal Export Dialog
    if (isExportDialogOpen) {
        val countStr = if (exportTargetType == "Ledger") "${entries.size} Records" else "${transactions.size} Transactions"
        AlertDialog(
            onDismissRequest = { isExportDialogOpen = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Backup & Import Ledger Records",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Manage your active record-keeping data. Share or download structured CSV sheets, backup complete JSON files, or select a previously saved JSON snapshot to restore all your stored entries instantly.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Diagnostic info card
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Active Dataset",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (exportTargetType == "Ledger") "Points Ledger Logs" else "Financial Income/Expense Logs",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                                    .padding(vertical = 4.dp, horizontal = 8.dp)
                            ) {
                                Text(
                                    text = countStr,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Text(
                        text = "CHOOSE EXPORT METHOD",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Black
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // SAVE AS FILE
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    val csv = if (exportTargetType == "Ledger") generateLedgerCsv(entries) else generateTransactionsCsv(transactions)
                                    csvTextToWrite = csv
                                    val prefix = if (exportTargetType == "Ledger") "ledger_export_" else "transactions_export_"
                                    val filename = "$prefix${System.currentTimeMillis()}.csv"
                                    createDocumentLauncher.launch(filename)
                                    isExportDialogOpen = false
                                }
                                .testTag("export_save_file_card")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Save as CSV File",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Save directly on your phone storage",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        // BACKUP AS JSON
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    val json = if (exportTargetType == "Ledger") generateLedgerJson(entries) else generateTransactionsJson(transactions)
                                    jsonTextToWrite = json
                                    val prefix = if (exportTargetType == "Ledger") "ledger_backup_" else "transactions_backup_"
                                    val filename = "$prefix${System.currentTimeMillis()}.json"
                                    createJsonLauncher.launch(filename)
                                    isExportDialogOpen = false
                                }
                                .testTag("export_backup_json_card")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.tertiaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Build,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Backup as JSON File",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Download structured data for data recovery",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        // SHARE VIA SEND
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    val csv = if (exportTargetType == "Ledger") generateLedgerCsv(entries) else generateTransactionsCsv(transactions)
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "Balance Tracker - $exportTargetType CSV Export")
                                        putExtra(Intent.EXTRA_TEXT, csv)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share CSV Export"))
                                    isExportDialogOpen = false
                                }
                                .testTag("export_share_text_card")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.secondaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Share as Plain Text",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Send via E-mail, Notes, or messaging apps",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        // COPY TO CLIPBOARD
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    val csv = if (exportTargetType == "Ledger") generateLedgerCsv(entries) else generateTransactionsCsv(transactions)
                                    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Balance Tracker CSV Export", csv)
                                    clipboardManager.setPrimaryClip(clip)
                                    Toast.makeText(context, "Copied CSV to Clipboard!", Toast.LENGTH_SHORT).show()
                                    isExportDialogOpen = false
                                }
                                .testTag("export_copy_clipboard_card")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.tertiaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.List,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Copy to Clipboard",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Paste directly into sheets or notepad",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "IMPORT / RESTORE DATA",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Black
                        )

                        // IMPORT FROM JSON FILE
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    importJsonLauncher.launch(arrayOf("application/json"))
                                    isExportDialogOpen = false
                                }
                                .testTag("import_json_card")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Import JSON Backup",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Select a previously saved .json file to restore records",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { isExportDialogOpen = false },
                    modifier = Modifier.testTag("export_close_button")
                ) {
                    Text("Close")
                }
            },
            modifier = Modifier.testTag("export_data_dialog")
        )
    }
}

@Composable
fun MetricsSummaryPanel(entries: List<LedgerEntry>) {
    val usdFormatter = remember { NumberFormat.getCurrencyInstance(Locale.US) }
    val latest = entries.firstOrNull()

    val walletBalance = latest?.walletBalance ?: 0
    val totalDeficit = entries.sumOf { it.deficit }
    val totalLoss = entries.sumOf { it.loss }
    
    // Calculates Total Income and Total Expenses based on Ledger Entries
    val totalIncome = entries.filter { it.transactionType == "Sale" }.sumOf { it.transactionAmount }
    val totalExpenses = entries.filter { it.transactionType == "Product in Hand" }.sumOf { it.transactionAmount }
    
    val salesVolume = entries.filter { it.transactionType == "Sale" }.sumOf { it.transactionAmount }
    val productInHandVolume = entries.filter { it.transactionType == "Product in Hand" }.sumOf { it.transactionAmount }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("ledger_metrics_panel"),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 5.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "POINTS LEDGER SUMMARY",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // Current Balance Big Display
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Current Balance",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (entries.isEmpty()) "--" else usdFormatter.format(walletBalance),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("ledger_current_balance")
                    )
                }

                // Balance Deficit Info
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "Accumulated Deficit",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val deficitColor = if (totalDeficit > 0) {
                        MaterialTheme.colorScheme.error
                    } else if (totalDeficit < 0) {
                        Color(0xFF007C30) // Green savings
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                    Text(
                        text = usdFormatter.format(totalDeficit),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = deficitColor,
                        modifier = Modifier.testTag("ledger_total_deficit")
                    )
                }
            }

            // Loss Section visual block
            if (totalLoss != 0) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                ) {
                     Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Loss Warning Icon",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Text(
                                    text = "LOSS SECTION",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.error,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Unmatched remaining deficit loss",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Text(
                            text = usdFormatter.format(totalLoss),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .testTag("ledger_total_loss")
                                .padding(start = 4.dp)
                        )
                    }
                }
            }

            Divider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)

            // Row for Total Income & Total Expenses
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Total Income Column
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Total Income",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = usdFormatter.format(totalIncome),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF028A3C),
                        modifier = Modifier.testTag("ledger_total_income")
                    )
                }

                // Total Expenses Column
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "Total Expenses",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = usdFormatter.format(totalExpenses),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFC07000),
                        modifier = Modifier.testTag("ledger_total_expenses")
                    )
                }
            }

            Divider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricMiniItem(
                    label = "Total Entries",
                    value = entries.size.toString(),
                    icon = Icons.Default.List,
                    modifier = Modifier.weight(1f)
                )
                MetricMiniItem(
                    label = "Sales",
                    value = salesVolume.toString(),
                    icon = Icons.Default.KeyboardArrowUp,
                    tint = Color(0xFF028A3C),
                    modifier = Modifier.weight(1f)
                )
                MetricMiniItem(
                    label = "Products in Hand",
                    value = productInHandVolume.toString(),
                    icon = Icons.Default.List,
                    tint = Color(0xFFC07000),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun MetricMiniItem(
    label: String,
    value: String,
    icon: ImageVector,
    tint: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun DailySummaryWidget(entries: List<LedgerEntry>) {
    val usdFormatter = remember { NumberFormat.getCurrencyInstance(Locale.US) }
    
    val todayEntries = remember(entries) {
        val todayCal = Calendar.getInstance()
        val todayYear = todayCal.get(Calendar.YEAR)
        val todayDay = todayCal.get(Calendar.DAY_OF_YEAR)
        entries.filter { entry ->
            val entryCal = Calendar.getInstance().apply { timeInMillis = entry.timestamp }
            entryCal.get(Calendar.YEAR) == todayYear && entryCal.get(Calendar.DAY_OF_YEAR) == todayDay
        }
    }

    val dailyIncome = todayEntries.filter { it.transactionType == "Sale" }.sumOf { it.transactionAmount }
    val dailyExpenses = todayEntries.filter { it.transactionType == "Product in Hand" }.sumOf { it.transactionAmount }
    val dailyLoss = todayEntries.sumOf { it.loss }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("daily_summary_widget"),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "TODAY's SUMMARY",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 4.dp)) {
                    Text(
                        text = "Income",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                    Text(
                        text = usdFormatter.format(dailyIncome),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF028A3C), // Green flavor
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Column(modifier = Modifier.weight(1f).padding(end = 4.dp)) {
                    Text(
                        text = "Expenses",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                    Text(
                        text = usdFormatter.format(dailyExpenses),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFC07000), // Orange/Warn flavor
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Loss",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                    Text(
                        text = usdFormatter.format(dailyLoss),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun MonthlySummaryWidget(entries: List<LedgerEntry>) {
    var expanded by remember { mutableStateOf(false) }
    val usdFormatter = remember { NumberFormat.getCurrencyInstance(Locale.US) }
    
    val monthEntries = remember(entries) {
        val todayCal = Calendar.getInstance()
        val todayYear = todayCal.get(Calendar.YEAR)
        val todayMonth = todayCal.get(Calendar.MONTH)
        entries.filter { entry ->
            val entryCal = Calendar.getInstance().apply { timeInMillis = entry.timestamp }
            entryCal.get(Calendar.YEAR) == todayYear && entryCal.get(Calendar.MONTH) == todayMonth
        }
    }

    val monthlyIncome = monthEntries.filter { it.transactionType == "Sale" }.sumOf { it.transactionAmount }
    val monthlyExpenses = monthEntries.filter { it.transactionType == "Product in Hand" }.sumOf { it.transactionAmount }
    val monthlyNetProfit = monthlyIncome - monthlyExpenses
    val monthlyLoss = monthEntries.sumOf { it.loss }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("monthly_summary_widget")
            .clickable { expanded = !expanded },
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "THIS MONTH",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.2f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 4.dp)) {
                            Text(
                                text = "Net Profit",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                            )
                            Text(
                                text = usdFormatter.format(monthlyNetProfit),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (monthlyNetProfit >= 0) Color(0xFF028A3C) else MaterialTheme.colorScheme.error,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        
                        Column(modifier = Modifier.weight(1f).padding(end = 4.dp)) {
                            Text(
                                text = "Gross Income",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                            )
                            Text(
                                text = usdFormatter.format(monthlyIncome),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Loss",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                            )
                            Text(
                                text = usdFormatter.format(monthlyLoss),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LedgerCard(
    entry: LedgerEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val usdFormatter = remember { NumberFormat.getCurrencyInstance(Locale.US) }
    val formattedDate = remember(entry.timestamp) {
        val sdf = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
        sdf.format(Date(entry.timestamp))
    }

    var isMenuExpanded by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onCardClick() }
            .testTag("ledger_card_${entry.id}"),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Date + Type Badge + Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Transaction Type Badge
                    TransactionBadge(type = entry.transactionType)

                    // Direct Edit Icon Button
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .size(24.dp)
                            .testTag("edit_ledger_entry_${entry.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Entry",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Card operations menu
                    Box {
                        IconButton(
                            onClick = { isMenuExpanded = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Entry Options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = isMenuExpanded,
                            onDismissRequest = { isMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit Entry") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                onClick = {
                                    isMenuExpanded = false
                                    onEdit()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Entry", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    isMenuExpanded = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }

            Divider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.5.dp)

            // Financial Calculations Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Left Column: Points Math
                CardFieldSegment(
                    title = "Points Data",
                    modifier = Modifier.weight(1f)
                ) {
                    DataLineItem("Prev Points", entry.previousPoints.toString())
                    DataLineItem("Avail Points", entry.availablePoints.toString())
                    val netPointsChange = entry.previousPoints - entry.availablePoints
                    val netColor = if (netPointsChange >= 0) Color(0xFF007A3E) else Color(0xFFD63031)
                    DataLineItem(
                        label = "Points Net Change",
                        value = if (netPointsChange >= 0) "+$netPointsChange" else "$netPointsChange",
                        valueColor = netColor,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Right Column: Wallet Math
                CardFieldSegment(
                    title = "Wallet Ledger",
                    modifier = Modifier.weight(1f)
                ) {
                    DataLineItem("Prev Balance", usdFormatter.format(entry.previousBalance))
                    DataLineItem("Expected Bal", usdFormatter.format(entry.expectedBalance))
                    DataLineItem("Wallet Balance", usdFormatter.format(entry.walletBalance))
                }
            }

            // Deficit Highlight Row
            HighlightDeficitRow(entry.deficit, usdFormatter)

            if (entry.loss != 0) {
                HighlightLossRow(entry.loss, entry.declaredDeficit, usdFormatter)
            }

            // Deficit Spending Notes (If present)
            if (entry.deficitSpendingNotes.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Notes",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Column {
                            Text(
                                text = "Notes",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = entry.deficitSpendingNotes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CardFieldSegment(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
            .padding(10.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        content()
    }
}

@Composable
fun DataLineItem(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    fontWeight: FontWeight = FontWeight.Normal
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.2f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = valueColor,
            fontWeight = fontWeight,
            fontSize = 11.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.8f)
        )
    }
}

@Composable
fun HighlightDeficitRow(deficit: Int, usdFormatter: NumberFormat) {
    val (backgroundColor, textColor, icon, label) = when {
        deficit > 0 -> {
            Quadruple(
                Color(0xFFFFEAEE),
                Color(0xFFC01F37),
                Icons.Default.KeyboardArrowDown,
                "Deficit / Variance"
            )
        }
        deficit < 0 -> {
            Quadruple(
                Color(0xFFE3FBE9),
                Color(0xFF007C30),
                Icons.Default.KeyboardArrowUp,
                "Surplus / Savings"
            )
        }
        else -> {
            Quadruple(
                Color(0xFFF1F3F0),
                Color(0xFF4C524E),
                Icons.Default.Check,
                "Balanced - Perfect Match"
            )
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .padding(vertical = 8.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
        Text(
            text = usdFormatter.format(abs(deficit)),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Black,
            color = textColor
        )
    }
}

@Composable
fun HighlightLossRow(loss: Int, declaredDeficit: Int, usdFormatter: NumberFormat) {
    val backgroundColor = Color(0xFFFDF2F0)
    val textColor = MaterialTheme.colorScheme.error

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .padding(vertical = 8.dp, horizontal = 12.dp)
            .testTag("ledger_card_loss_row"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(16.dp)
            )
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = "Deficit Discrepancy (Unexplained Loss)",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Declared deficit of ${usdFormatter.format(declaredDeficit)} does not match total variance",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            text = usdFormatter.format(loss),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Black,
            color = textColor,
            textAlign = TextAlign.End,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun TransactionBadge(type: String) {
    val bgColor = if (type == "Sale") Color(0xFFE2F9EB) else Color(0xFFFEEAD2)
    val txtColor = if (type == "Sale") Color(0xFF036B2B) else Color(0xFF904F00)
    val icon = if (type == "Sale") Icons.Default.ShoppingCart else Icons.Default.List

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = txtColor,
            modifier = Modifier.size(11.dp)
        )
        Text(
            text = type,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            color = txtColor,
            fontSize = 10.sp,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun EmptyStateView(
    onAddClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ShoppingCart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(64.dp)
                )
            }

            Text(
                text = "Empty Ledger",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "Track ledger entries locally, offline. We'll automatically calculate Net Changes, Transaction types, expected balances, and any deficits.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Button(
                onClick = onAddClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("empty_state_add_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Create First Entry", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun skeletonPulseAlpha(): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    return alpha
}

@Composable
fun DashboardSkeletonLoader(
    selectedTab: Int,
    modifier: Modifier = Modifier
) {
    val alpha = skeletonPulseAlpha()
    val pulseColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val shimmerColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.06f)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Beautiful Spinner & Status Overlay at the top to indicate background DB retrieval
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f))
                .padding(12.dp)
                .testTag("skeleton_status_header")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp).testTag("skeleton_spinner"),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Retrieving local ledger storage...",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        when (selectedTab) {
            0 -> {
                // Points Summary Skeleton (Metrics cards, Daily/Monthly blocks)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(96.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(shimmerColor)
                                .border(BorderStroke(1.dp, pulseColor), RoundedCornerShape(16.dp))
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(modifier = Modifier.fillMaxWidth(0.6f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(pulseColor.copy(alpha = alpha)))
                                Box(modifier = Modifier.fillMaxWidth(0.9f).height(24.dp).clip(RoundedCornerShape(6.dp)).background(pulseColor.copy(alpha = alpha)))
                            }
                        }
                    }
                }

                repeat(2) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(shimmerColor)
                            .border(BorderStroke(1.dp, pulseColor), RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(modifier = Modifier.width(140.dp).height(18.dp).clip(RoundedCornerShape(4.dp)).background(pulseColor.copy(alpha = alpha)))
                        repeat(3) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.width(100.dp).height(12.dp).clip(RoundedCornerShape(4.dp)).background(pulseColor.copy(alpha = alpha)))
                                Box(modifier = Modifier.width(60.dp).height(12.dp).clip(RoundedCornerShape(4.dp)).background(pulseColor.copy(alpha = alpha)))
                            }
                        }
                    }
                }
            }
            1 -> {
                // Ledger History List Skeletons
                repeat(4) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(shimmerColor)
                            .border(BorderStroke(1.dp, pulseColor), RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Box(modifier = Modifier.width(120.dp).height(16.dp).clip(RoundedCornerShape(4.dp)).background(pulseColor.copy(alpha = alpha)))
                                Box(modifier = Modifier.width(80.dp).height(16.dp).clip(RoundedCornerShape(4.dp)).background(pulseColor.copy(alpha = alpha)))
                            }
                            Box(modifier = Modifier.fillMaxWidth(0.85f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(pulseColor.copy(alpha = alpha)))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Box(modifier = Modifier.width(60.dp).height(20.dp).clip(RoundedCornerShape(10.dp)).background(pulseColor.copy(alpha = alpha)))
                                Box(modifier = Modifier.width(60.dp).height(20.dp).clip(RoundedCornerShape(10.dp)).background(pulseColor.copy(alpha = alpha)))
                            }
                        }
                    }
                }
            }
            2 -> {
                // Wallet Grid view skeletons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    repeat(2) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(120.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(shimmerColor)
                                .border(BorderStroke(1.dp, pulseColor), RoundedCornerShape(16.dp))
                                .padding(16.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(modifier = Modifier.width(50.dp).height(14.dp).clip(RoundedCornerShape(4.dp)).background(pulseColor.copy(alpha = alpha)))
                                Box(modifier = Modifier.fillMaxWidth(0.8f).height(22.dp).clip(RoundedCornerShape(6.dp)).background(pulseColor.copy(alpha = alpha)))
                                Box(modifier = Modifier.width(70.dp).height(12.dp).clip(RoundedCornerShape(4.dp)).background(pulseColor.copy(alpha = alpha)))
                            }
                        }
                    }
                }

                repeat(2) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(shimmerColor)
                            .border(BorderStroke(1.dp, pulseColor), RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(modifier = Modifier.width(110.dp).height(14.dp).clip(RoundedCornerShape(4.dp)).background(pulseColor.copy(alpha = alpha)))
                                Box(modifier = Modifier.width(70.dp).height(10.dp).clip(RoundedCornerShape(4.dp)).background(pulseColor.copy(alpha = alpha)))
                            }
                            Box(modifier = Modifier.width(80.dp).height(24.dp).clip(RoundedCornerShape(6.dp)).background(pulseColor.copy(alpha = alpha)))
                        }
                    }
                }
            }
            else -> {
                // Income & Expense ledger table skeleton
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(pulseColor.copy(alpha = alpha))
                )
                repeat(4) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1.5f).height(16.dp).clip(RoundedCornerShape(4.dp)).background(shimmerColor))
                        Box(modifier = Modifier.weight(1f).height(16.dp).clip(RoundedCornerShape(4.dp)).background(shimmerColor))
                        Box(modifier = Modifier.weight(1.2f).height(16.dp).clip(RoundedCornerShape(4.dp)).background(shimmerColor))
                    }
                }
            }
        }
    }
}

@Composable
fun AddLedgerDialog(
    viewModel: LedgerViewModel,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val prevPoints by viewModel.previousPointsText.collectAsStateWithLifecycle()
    val availPoints by viewModel.availablePointsText.collectAsStateWithLifecycle()
    val prevBalance by viewModel.previousBalanceText.collectAsStateWithLifecycle()
    val walletBalance by viewModel.walletBalanceText.collectAsStateWithLifecycle()
    val notes by viewModel.deficitSpendingNotesText.collectAsStateWithLifecycle()
    val declaredDeficit by viewModel.declaredDeficitText.collectAsStateWithLifecycle()
    val isFormValid by viewModel.isFormValid.collectAsStateWithLifecycle()
    val editingEntryId by viewModel.editingEntryId.collectAsStateWithLifecycle()
    val deficitFields by viewModel.deficitFields.collectAsStateWithLifecycle()

    val liveCalc by viewModel.liveCalculation.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val ledgerTimestamp by viewModel.ledgerTimestampText.collectAsStateWithLifecycle()

    val isTimestampValid = remember(ledgerTimestamp) {
        if (ledgerTimestamp.isBlank()) true
        else {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            try {
                val parsed = sdf.parse(ledgerTimestamp)
                parsed != null && parsed.time <= System.currentTimeMillis()
            } catch (e: Exception) {
                false
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false // allows us to customize full widths
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
                .clip(RoundedCornerShape(16.dp)),
            tonalElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Toolbar Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (editingEntryId != null) Icons.Default.Edit else Icons.Default.List,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = if (editingEntryId != null) "Edit Ledger Record" else "Add Ledger Record",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            val syncStatus by viewModel.draftSyncStatus.collectAsStateWithLifecycle()
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(RoundedCornerShape(100.dp))
                                        .background(
                                            if (syncStatus == "Draft Synced") Color(0xFF4CAF50) else Color(0xFFFF9800)
                                        )
                                )
                                Text(
                                    text = syncStatus,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close dialog")
                    }
                }

                Divider(color = MaterialTheme.colorScheme.surfaceVariant)

                // Fill Form panel
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Quick pre-populate assistance
                    if (entries.isNotEmpty()) {
                        Button(
                            onClick = { viewModel.prefillFromLatest() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("prefill_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Prefill using previous ledger values",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Display Note with Last Transaction / Last Ledger entry
                    val lastTx = transactions.firstOrNull()
                    val lastEntry = entries.firstOrNull()
                    if (lastTx != null || lastEntry != null) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().testTag("last_saved_info_block"),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "LAST SAVED DETAILS",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (lastTx != null) {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = "Last Transaction Note:",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = lastTx.description.ifBlank { "(No notes)" } + " — $$${lastTx.amount} [${lastTx.category}] (${lastTx.date})",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                if (lastTx != null && lastEntry != null) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                                        thickness = 1.dp
                                    )
                                }

                                if (lastEntry != null) {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = "Last Ledger Record Notes:",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        Text(
                                            text = lastEntry.deficitSpendingNotes.ifBlank { "(No deficit notes listed)" },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                        )
                                        val entryDateLabelStr = remember(lastEntry.timestamp) {
                                            java.text.SimpleDateFormat("MMM dd, yyyy • hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(lastEntry.timestamp))
                                        }
                                        Text(
                                            text = "Recorded on $entryDateLabelStr (Deficit: $$${lastEntry.deficit})",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Text(
                        text = "1. Points Tracking",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = prevPoints,
                            onValueChange = { viewModel.previousPointsText.value = it },
                            label = { Text("Prev Points") },
                            placeholder = { Text("e.g. 100") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_previous_points"),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary
                            )
                        )

                        OutlinedTextField(
                            value = availPoints,
                            onValueChange = { viewModel.availablePointsText.value = it },
                            label = { Text("Avail Points") },
                            placeholder = { Text("e.g. 75") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_available_points"),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    Text(
                        text = "2. Financial Balance Tracking",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = prevBalance,
                            onValueChange = { viewModel.previousBalanceText.value = it },
                            label = { Text("Prev Balance ($)") },
                            placeholder = { Text("e.g. 1200") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_previous_balance"),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary
                            )
                        )

                        OutlinedTextField(
                            value = walletBalance,
                            onValueChange = { viewModel.walletBalanceText.value = it },
                            label = { Text("Wallet Balance ($)") },
                            placeholder = { Text("e.g. 1225") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_wallet_balance"),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    Text(
                        text = "3. Deficit Spending Breakdown & Calculator (Optional)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Declare specific expenses (e.g. transport, meals) to justify the variance/deficit. These amounts are deducted from the deficit. The remaining balance is counted as Loss.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            deficitFields.forEachIndexed { index, field ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = field.description,
                                        onValueChange = { viewModel.updateDeficitField(index, it, field.amount) },
                                        label = { Text("Item Spent On") },
                                        placeholder = { Text("e.g. Uber, Dinner") },
                                        modifier = Modifier
                                            .weight(1.5f)
                                            .testTag("deficit_item_desc_$index"),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary
                                        )
                                    )

                                    val isFieldAmtValid = remember(field.amount) {
                                        field.amount.isBlank() || (field.amount.toIntOrNull()?.let { it >= 0 } ?: false)
                                    }

                                    OutlinedTextField(
                                        value = field.amount,
                                        onValueChange = { viewModel.updateDeficitField(index, field.description, it) },
                                        label = { Text("Amount ($)") },
                                        placeholder = { Text("e.g. 15") },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("deficit_item_amt_$index"),
                                        singleLine = true,
                                        isError = !isFieldAmtValid,
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Number,
                                            imeAction = ImeAction.Next
                                        ),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary
                                        )
                                    )

                                    IconButton(
                                        onClick = { viewModel.removeDeficitField(index) },
                                        modifier = Modifier.testTag("delete_deficit_item_$index")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete item",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = { viewModel.addDeficitField() },
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .testTag("add_deficit_field_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add Item", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Spending Item", style = MaterialTheme.typography.labelMedium)
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

                            val totalVariance = liveCalc.deficit
                            val explainedSum = deficitFields.sumOf { it.amount.toIntOrNull() ?: 0 }
                            val remainingLoss = liveCalc.loss

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Total Variance (Deficit):", style = MaterialTheme.typography.bodyMedium)
                                    Text("$${totalVariance}", fontWeight = FontWeight.Bold, color = if (totalVariance > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Total Explained Breakdown:", style = MaterialTheme.typography.bodyMedium)
                                    Text("$${explainedSum}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Unexplained Remaining (Loss):", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        text = "$${remainingLoss}",
                                        fontWeight = FontWeight.Bold,
                                        color = if (remainingLoss > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = "4. Ledger Notes (Optional)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { viewModel.deficitSpendingNotesText.value = it },
                        label = { Text("Notes & Ledger details") },
                        placeholder = { Text("Explain any price adjustments, sales, or deficit variances...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .testTag("input_notes"),
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "5. Custom Record Date & Time Timestamp",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = ledgerTimestamp,
                        onValueChange = { viewModel.ledgerTimestampText.value = it },
                        label = { Text("Timestamp (yyyy-MM-dd HH:mm)") },
                        placeholder = { Text("e.g. 2026-06-13 11:20") },
                        isError = !isTimestampValid,
                        supportingText = if (!isTimestampValid) {
                            { Text("Format must be yyyy-MM-dd HH:mm and not in the future", color = MaterialTheme.colorScheme.error) }
                        } else null,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_ledger_timestamp"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    // Shift micro-adjuster row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Shift:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val sdfParser = remember { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()) }

                        val adjustTime = { minutesToShift: Int ->
                            try {
                                val currentParsed = sdfParser.parse(ledgerTimestamp) ?: java.util.Date()
                                val cal = java.util.Calendar.getInstance()
                                cal.time = currentParsed
                                cal.add(java.util.Calendar.MINUTE, minutesToShift)
                                viewModel.ledgerTimestampText.value = sdfParser.format(cal.time)
                            } catch (e: Exception) {
                                val cal = java.util.Calendar.getInstance()
                                cal.add(java.util.Calendar.MINUTE, minutesToShift)
                                viewModel.ledgerTimestampText.value = sdfParser.format(cal.time)
                            }
                        }

                        listOf(
                            "-1 Hr" to { adjustTime(-60) },
                            "-10 Min" to { adjustTime(-10) },
                            "+10 Min" to { adjustTime(10) },
                            "Now" to { viewModel.ledgerTimestampText.value = sdfParser.format(java.util.Date()) }
                        ).forEach { (label, onClick) ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .clickable { onClick() }
                                    .padding(vertical = 4.dp, horizontal = 8.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Live auto calculations displaying results as the user types
                    LiveCalculationsPreviewBlock(liveCalc)
                }

                // Error validation prompt if relevant
                if (!isFormValid && (prevPoints.isNotEmpty() || availPoints.isNotEmpty() || prevBalance.isNotEmpty() || walletBalance.isNotEmpty() || declaredDeficit.isNotEmpty())) {
                    Text(
                        text = "⚠️ Please enter valid integers in all numbered/calculator/breakdown fields.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Call-To-Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = onSave,
                        enabled = isFormValid,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("save_ledger_button")
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (editingEntryId != null) "Update Record" else "Save Record",
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LiveCalculationsPreviewBlock(calc: LedgerCalculator.CalculationResult) {
    val usdFormatter = remember { NumberFormat.getCurrencyInstance(Locale.US) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            )
            .clip(RoundedCornerShape(8.dp)),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LIVE CALCULATED PREVIEW",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )

                // Quick badge
                TransactionBadge(type = calc.transactionType)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Net change
                MathDataRow(
                    label = "Net Change (Prev - Avail Points)",
                    value = if (calc.netChange >= 0) "+${calc.netChange}" else "${calc.netChange}"
                )

                // Transaction type
                MathDataRow(
                    label = "Transaction Pattern Amount",
                    value = "${calc.transactionAmount} (${calc.transactionType})"
                )

                // Expected Balance
                MathDataRow(
                    label = "Expected Balance (Prev Bal + Net Change)",
                    value = usdFormatter.format(calc.expectedBalance)
                )

                // Deficit
                val defColor = if (calc.deficit > 0) {
                    MaterialTheme.colorScheme.error
                } else if (calc.deficit < 0) {
                    Color(0xFF007C30)
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
                MathDataRow(
                    label = "Variance Deficit (Expected - Wallet)",
                    value = usdFormatter.format(calc.deficit),
                    valueColor = defColor,
                    isBold = true
                )

                if (calc.loss != 0) {
                    MathDataRow(
                        label = "Unexplained Remaining Loss",
                        value = usdFormatter.format(calc.loss),
                        valueColor = MaterialTheme.colorScheme.error,
                        isBold = true
                    )
                }
            }
        }
    }
}

@Composable
fun MathDataRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    isBold: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isBold) FontWeight.Black else FontWeight.Bold,
            color = valueColor,
            fontSize = 11.sp,
            maxLines = 1,
            textAlign = TextAlign.End
        )
    }
}

@Composable
fun TransactionMetricsPanel(transactions: List<FinancialTransaction>) {
    val usdFormatter = remember { NumberFormat.getCurrencyInstance(Locale.US) }
    
    val totalIncome = transactions.filter { it.category == "Income" }.sumOf { it.amount }
    val totalExpenses = transactions.filter { it.category != "Income" }.sumOf { it.amount }
    val netBalance = totalIncome - totalExpenses
    
    val count = transactions.size
    val categories = transactions.filter { it.category != "Income" }.groupBy { it.category }.mapValues { (_, txs) -> txs.sumOf { it.amount } }
    val topExpenseCategory = categories.maxByOrNull { it.value }?.key ?: "None"
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("tx_metrics_panel"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "TRANSACTION FINANCIAL SUMMARY",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            // Net Balance Big Display
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Total Net Balance",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                val netBalanceColor = if (netBalance >= 0) Color(0xFF00833E) else MaterialTheme.colorScheme.error
                val formattedBalance = usdFormatter.format(Math.abs(netBalance))
                Text(
                    text = if (netBalance >= 0) formattedBalance else "-$formattedBalance",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = netBalanceColor,
                    modifier = Modifier.testTag("net_balance_value")
                )
            }
            
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Total Income",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = usdFormatter.format(totalIncome),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00833E)
                    )
                }
                
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "Total Expenses",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = usdFormatter.format(totalExpenses),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Column {
                        Text(
                            text = "Top Expense",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                        Text(
                            text = topExpenseCategory,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Column {
                        Text(
                            text = "Record Count",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                        Text(
                            text = "$count entries",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TransactionCard(
    transaction: FinancialTransaction,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val usdFormatter = remember { NumberFormat.getCurrencyInstance(Locale.US) }
    var isMenuExpanded by remember { mutableStateOf(false) }
    
    val (icon, color) = when (transaction.category) {
        "Income" -> Icons.Default.Check to Color(0xFF00833E)
        "Food & Dining" -> Icons.Default.ShoppingCart to Color(0xFFD63031)
        "Shopping" -> Icons.Default.Star to Color(0xFFE07C00)
        "Travel & Transit" -> Icons.Default.Place to Color(0xFF0075C0)
        "Entertainment" -> Icons.Default.PlayArrow to Color(0xFF7D00C0)
        "Rent & Bills" -> Icons.Default.Home to Color(0xFF7F8C8D)
        "Healthcare" -> Icons.Default.Face to Color(0xFFC00080)
        else -> Icons.Default.Info to Color(0xFF4C524E)
    }
    
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag("tx_card_${transaction.id}"),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(22.dp)
                )
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.description,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = transaction.category,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = color,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = transaction.date,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val isIncome = transaction.category == "Income"
                val displayColor = if (isIncome) Color(0xFF00833E) else MaterialTheme.colorScheme.error
                val prefix = if (isIncome) "+" else "-"
                Text(
                    text = "$prefix${usdFormatter.format(transaction.amount)}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Black,
                    color = displayColor
                )
                
                // Direct Edit Icon Button
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .size(24.dp)
                        .testTag("edit_tx_${transaction.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Transaction",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Box {
                    IconButton(
                        onClick = { isMenuExpanded = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Entry Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = isMenuExpanded,
                        onDismissRequest = { isMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit Transaction") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            onClick = {
                                isMenuExpanded = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete Transaction", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                isMenuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddTransactionDialog(
    viewModel: LedgerViewModel,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val txDate by viewModel.txDateText.collectAsStateWithLifecycle()
    val txDesc by viewModel.txDescriptionText.collectAsStateWithLifecycle()
    val txAmt by viewModel.txAmountText.collectAsStateWithLifecycle()
    val txCat by viewModel.txCategoryText.collectAsStateWithLifecycle()
    val isFormValid by viewModel.isTxFormValid.collectAsStateWithLifecycle()
    val editingTxId by viewModel.editingTxId.collectAsStateWithLifecycle()
    
    val context = LocalContext.current
    
    val isAmtValid = remember(txAmt) {
        if (txAmt.isBlank()) true
        else {
            val parsed = txAmt.toDoubleOrNull()
            parsed != null && parsed > 0.0
        }
    }
    
    val isDateValid = remember(txDate) {
        if (txDate.isBlank()) true
        else {
            try {
                val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(txDate)
                parsed != null && parsed.time <= System.currentTimeMillis()
            } catch (e: Exception) {
                false
            }
        }
    }
    
    val categories = listOf(
        "Income",
        "Food & Dining",
        "Shopping",
        "Travel & Transit",
        "Entertainment",
        "Rent & Bills",
        "Healthcare",
        "Other"
    )
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(16.dp)),
            tonalElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (editingTxId != null) Icons.Default.Edit else Icons.Default.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = if (editingTxId != null) "Edit Transaction" else "Add Transaction",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            val syncStatus by viewModel.draftSyncStatus.collectAsStateWithLifecycle()
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(RoundedCornerShape(100.dp))
                                        .background(
                                            if (syncStatus == "Draft Synced") Color(0xFF4CAF50) else Color(0xFFFF9800)
                                        )
                                )
                                Text(
                                    text = syncStatus,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close dialog")
                    }
                }
                
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "1. Transaction Date",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    OutlinedTextField(
                        value = txDate,
                        onValueChange = { viewModel.txDateText.value = it },
                        label = { Text("Date (YYYY-MM-DD)") },
                        placeholder = { Text("Select Date") },
                        readOnly = true,
                        isError = !isDateValid,
                        supportingText = if (!isDateValid) {
                            { Text("Date cannot be in the future", color = MaterialTheme.colorScheme.error) }
                        } else null,
                        trailingIcon = {
                            IconButton(onClick = {
                                val calendar = Calendar.getInstance()
                                try {
                                    val parts = txDate.split("-")
                                    if (parts.size == 3) {
                                        calendar.set(Calendar.YEAR, parts[0].toInt())
                                        calendar.set(Calendar.MONTH, parts[1].toInt() - 1)
                                        calendar.set(Calendar.DAY_OF_MONTH, parts[2].toInt())
                                    }
                                } catch (e: Exception) {}
                                
                                android.app.DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        val cal = Calendar.getInstance()
                                        cal.set(year, month, dayOfMonth)
                                        val formatted = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
                                        viewModel.txDateText.value = formatted
                                    },
                                    calendar.get(Calendar.YEAR),
                                    calendar.get(Calendar.MONTH),
                                    calendar.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            }) {
                                Icon(imageVector = Icons.Default.DateRange, contentDescription = "Choose Date")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_tx_date")
                            .clickable {
                                val calendar = Calendar.getInstance()
                                try {
                                    val parts = txDate.split("-")
                                    if (parts.size == 3) {
                                        calendar.set(Calendar.YEAR, parts[0].toInt())
                                        calendar.set(Calendar.MONTH, parts[1].toInt() - 1)
                                        calendar.set(Calendar.DAY_OF_MONTH, parts[2].toInt())
                                    }
                                } catch (e: Exception) {}
                                
                                android.app.DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        val cal = Calendar.getInstance()
                                        cal.set(year, month, dayOfMonth)
                                        val formatted = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
                                        viewModel.txDateText.value = formatted
                                    },
                                    calendar.get(Calendar.YEAR),
                                    calendar.get(Calendar.MONTH),
                                    calendar.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    
                    Text(
                        text = "2. Transaction Details",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    OutlinedTextField(
                        value = txDesc,
                        onValueChange = { viewModel.txDescriptionText.value = it },
                        label = { Text("Description") },
                        placeholder = { Text("e.g. Grocery Store, Paycheck, Gas etc.") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_tx_description"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    
                    OutlinedTextField(
                        value = txAmt,
                        onValueChange = { viewModel.txAmountText.value = it },
                        label = { Text("Amount ($)") },
                        placeholder = { Text("e.g. 45.50") },
                        singleLine = true,
                        isError = !isAmtValid,
                        supportingText = if (!isAmtValid) {
                            { Text("Amount must be a positive number", color = MaterialTheme.colorScheme.error) }
                        } else null,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_tx_amount"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    
                    Text(
                        text = "3. Select Category",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val chunks = categories.chunked(3)
                        chunks.forEach { rowCategories ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowCategories.forEach { category ->
                                    val isSelected = txCat == category
                                    val chipColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                    
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(chipColor)
                                            .clickable { viewModel.txCategoryText.value = category }
                                            .border(
                                                1.dp,
                                                if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(vertical = 8.dp, horizontal = 4.dp)
                                            .testTag("category_pill_$category"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = category,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = textColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (rowCategories.size < 3) {
                                    repeat(3 - rowCategories.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    ) {
                        Text("Cancel")
                    }
                    
                    Button(
                        onClick = onSave,
                        enabled = isFormValid,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("save_tx_button")
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (editingTxId != null) "Update Transaction" else "Save Transaction",
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
    }
}

fun filterTransactions(
    transactions: List<FinancialTransaction>,
    category: String,
    dateRangePreset: String,
    customStart: String,
    customEnd: String
): List<FinancialTransaction> {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val todayDate = Date()
    
    return transactions.filter { tx ->
        // 1. Category Filter
        val matchesCategory = category == "All" || tx.category == category
        
        // 2. Date Range Filter
        val matchesDate = try {
            val txDate = sdf.parse(tx.date) ?: return@filter false
            when (dateRangePreset) {
                "All Time" -> true
                "Today" -> {
                    val todayStr = sdf.format(todayDate)
                    tx.date == todayStr
                }
                "Last 7 Days" -> {
                    val cal = Calendar.getInstance()
                    cal.time = todayDate
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    cal.add(Calendar.DAY_OF_YEAR, -7)
                    
                    val compareCal = Calendar.getInstance().apply {
                        time = txDate
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    compareCal.time.after(cal.time) || compareCal.time == cal.time
                }
                "Last 30 Days" -> {
                    val cal = Calendar.getInstance()
                    cal.time = todayDate
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    cal.add(Calendar.DAY_OF_YEAR, -30)
                    
                    val compareCal = Calendar.getInstance().apply {
                        time = txDate
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    compareCal.time.after(cal.time) || compareCal.time == cal.time
                }
                "This Month" -> {
                    val calTx = Calendar.getInstance().apply { time = txDate }
                    val calToday = Calendar.getInstance().apply { time = todayDate }
                    calTx.get(Calendar.YEAR) == calToday.get(Calendar.YEAR) &&
                            calTx.get(Calendar.MONTH) == calToday.get(Calendar.MONTH)
                }
                "Custom Date Range" -> {
                    var startOk = true
                    var endOk = true
                    val txCompareCal = Calendar.getInstance().apply {
                        time = txDate
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    
                    if (customStart.isNotBlank()) {
                        val startD = sdf.parse(customStart)
                        if (startD != null) {
                            val startCal = Calendar.getInstance().apply {
                                time = startD
                                set(Calendar.HOUR_OF_DAY, 0)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                            startOk = txCompareCal.time.after(startCal.time) || txCompareCal.time == startCal.time
                        }
                    }
                    if (customEnd.isNotBlank()) {
                        val endD = sdf.parse(customEnd)
                        if (endD != null) {
                            val endCal = Calendar.getInstance().apply {
                                time = endD
                                set(Calendar.HOUR_OF_DAY, 0)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                            endOk = txCompareCal.time.before(endCal.time) || txCompareCal.time == endCal.time
                        }
                    }
                    startOk && endOk
                }
                else -> true
            }
        } catch (e: Exception) {
            true
        }
        
        matchesCategory && matchesDate
    }
}

@Composable
fun TransactionFilterBar(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    selectedDateRange: String,
    onDateRangeSelected: (String) -> Unit,
    customStartDate: String,
    onCustomStartSelected: (String) -> Unit,
    customEndDate: String,
    onCustomEndSelected: (String) -> Unit,
    onResetFilters: () -> Unit
) {
    val context = LocalContext.current
    var isCategoryMenuExpanded by remember { mutableStateOf(false) }
    var isDateRangeMenuExpanded by remember { mutableStateOf(false) }
    
    val categoryOptions = listOf(
        "All", "Income", "Food & Dining", "Shopping", "Travel & Transit", 
        "Entertainment", "Rent & Bills", "Healthcare", "Other"
    )
    
    val dateRangeOptions = listOf(
        "All Time", "Today", "Last 7 Days", "Last 30 Days", "This Month", "Custom Date Range"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Dropdown
            Box(
                modifier = Modifier
                    .weight(1f)
                    .testTag("filter_category_box")
            ) {
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isCategoryMenuExpanded = true }
                        .testTag("filter_category_button"),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Category",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = selectedCategory,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Select Category",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                DropdownMenu(
                    expanded = isCategoryMenuExpanded,
                    onDismissRequest = { isCategoryMenuExpanded = false },
                    modifier = Modifier
                        .fillMaxWidth(0.45f)
                        .testTag("filter_category_dropdown")
                ) {
                    categoryOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { 
                                Text(
                                    text = option,
                                    fontWeight = if (option == selectedCategory) FontWeight.Bold else FontWeight.Normal,
                                    color = if (option == selectedCategory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                ) 
                            },
                            onClick = {
                                onCategorySelected(option)
                                isCategoryMenuExpanded = false
                            },
                            modifier = Modifier.testTag("filter_category_item_$option")
                        )
                    }
                }
            }

            // Date Range Dropdown
            Box(
                modifier = Modifier
                    .weight(1f)
                    .testTag("filter_date_box")
            ) {
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isDateRangeMenuExpanded = true }
                        .testTag("filter_date_button"),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Date Range",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = selectedDateRange,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Select Date Range",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                DropdownMenu(
                    expanded = isDateRangeMenuExpanded,
                    onDismissRequest = { isDateRangeMenuExpanded = false },
                    modifier = Modifier
                        .fillMaxWidth(0.45f)
                        .testTag("filter_date_dropdown")
                ) {
                    dateRangeOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { 
                                Text(
                                    text = option,
                                    fontWeight = if (option == selectedDateRange) FontWeight.Bold else FontWeight.Normal,
                                    color = if (option == selectedDateRange) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                ) 
                            },
                            onClick = {
                                onDateRangeSelected(option)
                                isDateRangeMenuExpanded = false
                            },
                            modifier = Modifier.testTag("filter_date_item_${option.replace(" ", "_")}")
                        )
                    }
                }
            }
        }

        // Custom Date Range Pickers (only shown if Custom Date Range is selected)
        AnimatedVisibility(
            visible = selectedDateRange == "Custom Date Range",
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Custom Date Range Selection:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Start Date
                    OutlinedCard(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                val calendar = Calendar.getInstance()
                                if (customStartDate.isNotBlank()) {
                                    try {
                                        val parts = customStartDate.split("-")
                                        if (parts.size == 3) {
                                            calendar.set(Calendar.YEAR, parts[0].toInt())
                                            calendar.set(Calendar.MONTH, parts[1].toInt() - 1)
                                            calendar.set(Calendar.DAY_OF_MONTH, parts[2].toInt())
                                        }
                                    } catch (e: Exception) {}
                                }
                                android.app.DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        val cal = Calendar.getInstance()
                                        cal.set(year, month, dayOfMonth)
                                        val formatted = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
                                        onCustomStartSelected(formatted)
                                    },
                                    calendar.get(Calendar.YEAR),
                                    calendar.get(Calendar.MONTH),
                                    calendar.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            }
                            .testTag("filter_custom_start_date"),
                        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Column {
                                Text("Start Date", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
                                Text(
                                    text = if (customStartDate.isBlank()) "Choose Date" else customStartDate,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // End Date
                    OutlinedCard(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                val calendar = Calendar.getInstance()
                                if (customEndDate.isNotBlank()) {
                                    try {
                                        val parts = customEndDate.split("-")
                                        if (parts.size == 3) {
                                            calendar.set(Calendar.YEAR, parts[0].toInt())
                                            calendar.set(Calendar.MONTH, parts[1].toInt() - 1)
                                            calendar.set(Calendar.DAY_OF_MONTH, parts[2].toInt())
                                        }
                                    } catch (e: Exception) {}
                                }
                                android.app.DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        val cal = Calendar.getInstance()
                                        cal.set(year, month, dayOfMonth)
                                        val formatted = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
                                        onCustomEndSelected(formatted)
                                    },
                                    calendar.get(Calendar.YEAR),
                                    calendar.get(Calendar.MONTH),
                                    calendar.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            }
                            .testTag("filter_custom_end_date"),
                        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Column {
                                Text("End Date", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
                                Text(
                                    text = if (customEndDate.isBlank()) "Choose Date" else customEndDate,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Active Filter Indicators / Clear button (only shown when any filter is active)
        val isFiltered = selectedCategory != "All" || selectedDateRange != "All Time"
        if (isFiltered) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Active filters applied.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "Reset Filters",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { onResetFilters() }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .testTag("filter_reset_button")
                )
            }
        }
    }
}

@Composable
fun WalletAccountsView(
    walletAccounts: List<WalletAccount>,
    totalWalletBalance: Double,
    onUpdateBalance: (String, Double) -> Unit
) {
    val usdFormatter = remember { NumberFormat.getCurrencyInstance(Locale.US) }
    var selectedFilter by remember { mutableStateOf("All") } // "All", "Bkash", "Nagad"
    var editingAccount by remember { mutableStateOf<WalletAccount?>(null) }

    val filteredAccounts = remember(walletAccounts, selectedFilter) {
        when (selectedFilter) {
            "Bkash" -> walletAccounts.filter { it.type.contains("Bkash", ignoreCase = true) }
            "Nagad" -> walletAccounts.filter { it.type.contains("Nagad", ignoreCase = true) }
            else -> walletAccounts
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("wallet_accounts_list"),
        contentPadding = PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Total Wallet Balance Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("wallet_total_card"),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Total Wallet Balance",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                            Text(
                                text = usdFormatter.format(totalWalletBalance),
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Text(
                        text = "Sum of all active bKash & Nagad accounts below.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // Filter Chips row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("All", "Bkash", "Nagad").forEach { network ->
                    val isSelected = selectedFilter == network
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = network },
                        label = { Text(text = network) },
                        modifier = Modifier.testTag("filter_chip_$network"),
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null
                    )
                }
            }
        }

        // Accounts List Title
        item {
            Text(
                text = "$selectedFilter Accounts (${filteredAccounts.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (filteredAccounts.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No accounts found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(
                items = filteredAccounts,
                key = { it.id }
            ) { account ->
                WalletAccountCard(
                    account = account,
                    usdFormatter = usdFormatter,
                    onEditClick = { editingAccount = account },
                    modifier = Modifier.animateItem()
                )
            }
        }
    }

    // Editing Dialog
    editingAccount?.let { account ->
        WalletBalanceEditDialog(
            account = account,
            onDismiss = { editingAccount = null },
            onSave = { newBal ->
                onUpdateBalance(account.id, newBal)
                editingAccount = null
            }
        )
    }
}

@Composable
fun WalletAccountCard(
    account: WalletAccount,
    usdFormatter: NumberFormat,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isBkash = account.type.contains("Bkash", ignoreCase = true)
    val brandColor = if (isBkash) Color(0xFFE2125B) else Color(0xFFF15A22)
    val brandContainer = if (isBkash) Color(0xFFFDF0F4) else Color(0xFFFFF2EE)

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { onEditClick() }
            .testTag("wallet_account_card_${account.id}"),
        colors = CardDefaults.elevatedCardColors(
            containerColor = brandContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(brandContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isBkash) "bK" else "Ng",
                        fontWeight = FontWeight.ExtraBold,
                        color = brandColor,
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Column {
                    Text(
                        text = account.type,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = account.number,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace
                    )
                    if (account.lastUpdated > 0L) {
                        val sdf = remember { SimpleDateFormat("MMM dd, yyyy h:mm a", Locale.getDefault()) }
                        val formattedDate = remember(account.lastUpdated) { sdf.format(java.util.Date(account.lastUpdated)) }
                        Text(
                            text = "Last Entry: $formattedDate",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "Balance",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = usdFormatter.format(account.balance),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = brandColor
                    )
                }

                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier
                        .testTag("wallet_account_edit_${account.id}")
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit balance of ${account.number}",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun WalletBalanceEditDialog(
    account: WalletAccount,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var amountInput by remember { mutableStateOf(if (account.balance > 0.0) account.balance.toString() else "") }
    var isError by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = true)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("wallet_edit_dialog"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Update Account Amount",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = account.type,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = account.number,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { input ->
                        if (input.isEmpty() || input.toDoubleOrNull() != null) {
                            amountInput = input
                            isError = false
                        } else {
                            isError = true
                        }
                    },
                    label = { Text("Input Wallet Amount") },
                    placeholder = { Text("e.g. 1500") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    singleLine = true,
                    isError = isError,
                    supportingText = if (isError) {
                        { Text("Please enter a valid amount", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("wallet_edit_amount_input"),
                    trailingIcon = {
                        Text(
                            text = "$",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("wallet_edit_cancel")
                    ) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val parsed = amountInput.toDoubleOrNull() ?: 0.0
                            if (parsed >= 0.0) {
                                onSave(parsed)
                            } else {
                                isError = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.testTag("wallet_edit_save")
                    ) {
                        Text("Save Amount")
                    }
                }
            }
        }
    }
}

@Composable
fun LedgerDetailDialog(
    entry: LedgerEntry,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val usdFormatter = remember { NumberFormat.getCurrencyInstance(Locale.US) }
    val fullDateStr = remember(entry.timestamp) {
        val sdf = SimpleDateFormat("EEEE, MMMM dd, yyyy • hh:mm:ss a", Locale.getDefault())
        sdf.format(Date(entry.timestamp))
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(16.dp)
                .testTag("ledger_detail_dialog_${entry.id}"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Header with icon and title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Entry Details",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "ID: ${entry.id}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("ledger_detail_close_icon")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close detailed view",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Date & Type Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "TIMESTAMP",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = fullDateStr,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        TransactionBadge(type = entry.transactionType)
                    }

                    // Section 1: Points calculation details
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)), RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "POINTS METRIC BREAKDOWN",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        DetailMetricRow("Previous Points", entry.previousPoints.toString())
                        DetailMetricRow("Available Points", entry.availablePoints.toString())

                        val netPointsChange = entry.previousPoints - entry.availablePoints
                        val netColor = if (netPointsChange >= 0) Color(0xFF007A3E) else Color(0xFFD63031)
                        val netSign = if (netPointsChange >= 0) "+" else ""
                        DetailMetricRow(
                            label = "Net Points Deducted/Added",
                            value = "$netSign$netPointsChange pts",
                            valueColor = netColor,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Formula: Prev Points (${entry.previousPoints}) - Avail Points (${entry.availablePoints}) = Net Change ($netPointsChange)",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }

                    // Section 2: Wallet Balance Reconciliation
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)), RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ShoppingCart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "WALLET CASH RECONCILIATION",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        DetailMetricRow("Previous Cash Balance", usdFormatter.format(entry.previousBalance))
                        DetailMetricRow("Specified/Product Cost", usdFormatter.format(entry.transactionAmount))
                        DetailMetricRow("Expected Cash Balance", usdFormatter.format(entry.expectedBalance), fontWeight = FontWeight.SemiBold)
                        DetailMetricRow("Actual Wallet Balance", usdFormatter.format(entry.walletBalance), valueColor = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (entry.transactionType == "Sale") {
                                "Calculation Flow (Sale): Previous Cash (${usdFormatter.format(entry.previousBalance)}) + Value from points (${usdFormatter.format(entry.transactionAmount)}) = Expected Balance (${usdFormatter.format(entry.expectedBalance)})"
                            } else {
                                "Calculation Flow (Product in Hand): Previous Cash (${usdFormatter.format(entry.previousBalance)}) - Product Cost (${usdFormatter.format(entry.transactionAmount)}) = Expected Balance (${usdFormatter.format(entry.expectedBalance)})"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }

                    // Section 3: Variance (Deficit / Savings) Analysis
                    val hasDeficit = entry.deficit > 0
                    val hasSavings = entry.deficit < 0
                    val varianceContainerColor = when {
                        hasDeficit -> Color(0xFFFFEAEE)
                        hasSavings -> Color(0xFFE6F7ED)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    }
                    val varianceBorderColor = when {
                        hasDeficit -> Color(0xFFFAB6C0)
                        hasSavings -> Color(0xFFA3E4C1)
                        else -> MaterialTheme.colorScheme.outlineVariant
                    }
                    val varianceTextColor = when {
                        hasDeficit -> Color(0xFFC01F37)
                        hasSavings -> Color(0xFF1E7E34)
                        else -> MaterialTheme.colorScheme.onSurface
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(varianceContainerColor)
                            .border(BorderStroke(1.dp, varianceBorderColor), RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (hasDeficit) Icons.Default.Warning else Icons.Default.Check,
                                contentDescription = null,
                                tint = varianceTextColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "VARIANCE & DEFICIT ANALYSIS",
                                style = MaterialTheme.typography.labelSmall,
                                color = varianceTextColor,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        DetailMetricRow(
                            label = if (hasDeficit) "Deficit Spending Variance" else if (hasSavings) "Cash Savings Surplus" else "Variance/Difference",
                            value = usdFormatter.format(abs(entry.deficit)),
                            valueColor = varianceTextColor,
                            fontWeight = FontWeight.Bold
                        )

                        DetailMetricRow("Declared Deficit Limit", usdFormatter.format(entry.declaredDeficit))
                        DetailMetricRow("Capitalized Net Loss", usdFormatter.format(entry.loss), valueColor = if (entry.loss > 0) Color(0xFFC01F37) else MaterialTheme.colorScheme.onSurface)

                        Text(
                            text = "Variance Formula: Expected Cash (${usdFormatter.format(entry.expectedBalance)}) - Actual Wallet cash (${usdFormatter.format(entry.walletBalance)}) = Deficit (${usdFormatter.format(entry.deficit)})",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = varianceTextColor.copy(alpha = 0.8f)
                        )
                    }

                    // Section 4: Operational Deficit Notes
                    if (entry.deficitSpendingNotes.isNotBlank()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
                                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)), RoundedCornerShape(16.dp))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "OPERATIONAL & DEFICIT NOTES",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = entry.deficitSpendingNotes,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(16.dp))

                // Actions Footer Row: Edit, Delete, Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ledger_detail_delete_button"),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    OutlinedButton(
                        onClick = onEdit,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ledger_detail_edit_button"),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ledger_detail_close_button"),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Text("Close", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete This Record?", fontWeight = FontWeight.Bold) },
            text = { Text("This will permanently remove this points ledger log entry from your local history.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("ledger_detail_delete_confirm")
                ) {
                    Text("Delete Record")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            },
            modifier = Modifier.testTag("ledger_detail_delete_dialog")
        )
    }
}

@Composable
fun DetailMetricRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    fontWeight: FontWeight = FontWeight.Normal
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            fontWeight = fontWeight,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

enum class SortColumn { DATE, CATEGORY, AMOUNT }

@Composable
fun SortableHeader(
    title: String,
    column: SortColumn,
    currentSortColumn: SortColumn,
    sortAscending: Boolean,
    onHeaderClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clickable { onHeaderClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (currentSortColumn == column) {
            Icon(
                imageVector = if (sortAscending) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier
                    .size(14.dp)
                    .padding(start = 2.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun SortableTransactionTable(
    transactions: List<FinancialTransaction>,
    onEdit: (FinancialTransaction) -> Unit,
    onDelete: (Int) -> Unit
) {
    var sortColumn by remember { mutableStateOf(SortColumn.DATE) }
    var sortAscending by remember { mutableStateOf(false) }
    var selectedTxForAction by remember { mutableStateOf<FinancialTransaction?>(null) }

    if (selectedTxForAction != null) {
        AlertDialog(
            onDismissRequest = { selectedTxForAction = null },
            title = { Text("Transaction Actions", fontWeight = FontWeight.Bold) },
            text = { Text("Would you like to edit or delete this transaction record?") },
            confirmButton = {
                Button(
                    onClick = {
                        val tx = selectedTxForAction!!
                        selectedTxForAction = null
                        onEdit(tx)
                    },
                    modifier = Modifier.testTag("tx_action_edit")
                ) {
                    Text("Edit Entry")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val tx = selectedTxForAction!!
                        selectedTxForAction = null
                        onDelete(tx.id)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("tx_action_delete")
                ) {
                    Text("Delete Record")
                }
            },
            modifier = Modifier.testTag("tx_action_dialog")
        )
    }

    val sortedData = remember(transactions, sortColumn, sortAscending) {
        when (sortColumn) {
            SortColumn.DATE -> if (sortAscending) transactions.sortedBy { it.date } else transactions.sortedByDescending { it.date }
            SortColumn.CATEGORY -> if (sortAscending) transactions.sortedBy { it.category } else transactions.sortedByDescending { it.category }
            SortColumn.AMOUNT -> if (sortAscending) transactions.sortedBy { it.amount } else transactions.sortedByDescending { it.amount }
        }
    }

    val usdFormatter = remember { NumberFormat.getCurrencyInstance(Locale.US) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Headers
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SortableHeader(
                title = "Date",
                column = SortColumn.DATE,
                currentSortColumn = sortColumn,
                sortAscending = sortAscending,
                onHeaderClick = {
                    if (sortColumn == SortColumn.DATE) sortAscending = !sortAscending else { sortColumn = SortColumn.DATE; sortAscending = true }
                },
                modifier = Modifier.weight(1.5f)
            )
            SortableHeader(
                title = "Details",
                column = SortColumn.CATEGORY,
                currentSortColumn = sortColumn,
                sortAscending = sortAscending,
                onHeaderClick = {
                    if (sortColumn == SortColumn.CATEGORY) sortAscending = !sortAscending else { sortColumn = SortColumn.CATEGORY; sortAscending = true }
                },
                modifier = Modifier.weight(2.5f)
            )
            SortableHeader(
                title = "Amount",
                column = SortColumn.AMOUNT,
                currentSortColumn = sortColumn,
                sortAscending = sortAscending,
                onHeaderClick = {
                    if (sortColumn == SortColumn.AMOUNT) sortAscending = !sortAscending else { sortColumn = SortColumn.AMOUNT; sortAscending = true }
                },
                modifier = Modifier.weight(1.5f)
            )
            Text(
                text = "Action",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .testTag("tx_list"),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            items(
                items = sortedData,
                key = { it.id }
            ) { tx ->
                var isHovered by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .animateItem()
                        .fillMaxWidth()
                        .pointerInput(tx.id) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    when (event.type) {
                                        PointerEventType.Enter -> isHovered = true
                                        PointerEventType.Exit -> isHovered = false
                                    }
                                }
                            }
                        }
                        .clickable { 
                            selectedTxForAction = tx
                        }
                        .background(
                            if (isHovered) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.surface
                        )
                        .border(
                            width = 1.dp,
                            color = if (isHovered) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                        .padding(vertical = 12.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1.5f)) {
                        Text(
                            text = tx.date,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(modifier = Modifier.weight(2.5f)) {
                        Text(
                            text = tx.category,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = tx.description,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(modifier = Modifier.weight(1.5f)) {
                        Text(
                            text = usdFormatter.format(tx.amount),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (tx.category == "Income") Color(0xFF00833E) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        IconButton(
                            onClick = { onEdit(tx) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = { onDelete(tx.id) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

fun generateLedgerCsv(entries: List<LedgerEntry>): String {
    val header = "ID,Timestamp,Formatted Date,Previous Points,Available Points,Transaction Type,Transaction Cost,Previous Balance,Expected Balance,Actual Wallet Balance,Deficit / Savings,Deficit Notes,Declared Deficit Limit,Capitalized Loss\n"
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val sb = java.lang.StringBuilder()
    sb.append(header)
    for (entry in entries) {
        val dateStr = sdf.format(Date(entry.timestamp))
        sb.append(entry.id).append(",")
        sb.append(entry.timestamp).append(",")
        sb.append(escapeCsvField(dateStr)).append(",")
        sb.append(entry.previousPoints).append(",")
        sb.append(entry.availablePoints).append(",")
        sb.append(escapeCsvField(entry.transactionType)).append(",")
        sb.append(entry.transactionAmount).append(",")
        sb.append(entry.previousBalance).append(",")
        sb.append(entry.expectedBalance).append(",")
        sb.append(entry.walletBalance).append(",")
        sb.append(entry.deficit).append(",")
        sb.append(escapeCsvField(entry.deficitSpendingNotes)).append(",")
        sb.append(entry.declaredDeficit).append(",")
        sb.append(entry.loss).append("\n")
    }
    return sb.toString()
}

fun generateTransactionsCsv(transactions: List<FinancialTransaction>): String {
    val header = "ID,Date,Description,Amount,Category\n"
    val sb = java.lang.StringBuilder()
    sb.append(header)
    for (tx in transactions) {
        sb.append(tx.id).append(",")
        sb.append(escapeCsvField(tx.date)).append(",")
        sb.append(escapeCsvField(tx.description)).append(",")
        sb.append(tx.amount).append(",")
        sb.append(escapeCsvField(tx.category)).append("\n")
    }
    return sb.toString()
}

fun escapeCsvField(value: String): String {
    if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
        return "\"" + value.replace("\"", "\"\"") + "\""
    }
    return value
}

fun generateLedgerJson(entries: List<LedgerEntry>): String {
    val jsonArray = org.json.JSONArray()
    for (entry in entries) {
        val obj = org.json.JSONObject()
        obj.put("id", entry.id)
        obj.put("timestamp", entry.timestamp)
        obj.put("previousPoints", entry.previousPoints)
        obj.put("availablePoints", entry.availablePoints)
        obj.put("transactionType", entry.transactionType)
        obj.put("transactionAmount", entry.transactionAmount)
        obj.put("previousBalance", entry.previousBalance)
        obj.put("expectedBalance", entry.expectedBalance)
        obj.put("walletBalance", entry.walletBalance)
        obj.put("deficit", entry.deficit)
        obj.put("deficitSpendingNotes", entry.deficitSpendingNotes)
        obj.put("declaredDeficit", entry.declaredDeficit)
        obj.put("loss", entry.loss)
        jsonArray.put(obj)
    }
    return jsonArray.toString(4)
}

fun generateTransactionsJson(transactions: List<FinancialTransaction>): String {
    val jsonArray = org.json.JSONArray()
    for (tx in transactions) {
        val obj = org.json.JSONObject()
        obj.put("id", tx.id)
        obj.put("date", tx.date)
        obj.put("description", tx.description)
        obj.put("amount", tx.amount)
        obj.put("category", tx.category)
        jsonArray.put(obj)
    }
    return jsonArray.toString(4)
}

@Composable
fun IncomeExpenseForm(
    viewModel: com.example.viewmodel.LedgerViewModel,
    modifier: Modifier = Modifier
) {
    val txDate by viewModel.txDateText.collectAsStateWithLifecycle()
    val txDesc by viewModel.txDescriptionText.collectAsStateWithLifecycle()
    val txAmt by viewModel.txAmountText.collectAsStateWithLifecycle()
    val txCat by viewModel.txCategoryText.collectAsStateWithLifecycle()
    val isFormValid by viewModel.isTxFormValid.collectAsStateWithLifecycle()
    val editingTxId by viewModel.editingTxId.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val incomeCategories = listOf("Income", "Investments", "Gifts", "Other Income")
    val expenseCategories = listOf("Food & Dining", "Shopping", "Travel & Transit", "Entertainment", "Rent & Bills", "Healthcare", "Other")

    val isIncomeMode = remember(txCat) {
        txCat in incomeCategories
    }

    val isAmtValid = remember(txAmt) {
        if (txAmt.isBlank()) true
        else {
            val parsed = txAmt.toDoubleOrNull()
            parsed != null && parsed > 0.0
        }
    }

    val isDateValid = remember(txDate) {
        if (txDate.isBlank()) true
        else {
            try {
                val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(txDate)
                parsed != null && parsed.time <= System.currentTimeMillis()
            } catch (e: Exception) {
                false
            }
        }
    }

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (editingTxId != null) "Edit Entry #${editingTxId}" else "Log Income / Expense",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // Dynamic Option Switcher (Expense vs Income Segment Row)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("Expense", "Income").forEach { mode ->
                    val selected = (mode == "Expense" && !isIncomeMode) || (mode == "Income" && isIncomeMode)
                    val activeBgColor = if (selected) {
                        if (mode == "Income") Color(0xFF2E7D32) else Color(0xFFC62828)
                    } else Color.Transparent
                    
                    val activeTextColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(activeBgColor)
                            .clickable {
                                if (mode == "Expense") {
                                    viewModel.txCategoryText.value = "Food & Dining"
                                } else {
                                    viewModel.txCategoryText.value = "Income"
                                }
                            }
                            .testTag("form_mode_btn_$mode"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (mode == "Income") Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                contentDescription = null,
                                tint = activeTextColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = mode,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = activeTextColor
                            )
                        }
                    }
                }
            }

            // Description OutlinedTextField
            OutlinedTextField(
                value = txDesc,
                onValueChange = { viewModel.txDescriptionText.value = it },
                label = { Text("Description") },
                placeholder = { Text("e.g. Rice, Salary, Grocery, Netflix") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_tx_description"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                )
            )

            // Amount & Date side-by-side
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Amount Field
                OutlinedTextField(
                    value = txAmt,
                    onValueChange = { viewModel.txAmountText.value = it },
                    label = { Text("Amount ($)") },
                    placeholder = { Text("0.00") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        )
                    },
                    isError = !isAmtValid,
                    supportingText = if (!isAmtValid) {
                        { Text("Positive number expected", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("input_tx_amount"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    )
                )

                // Date Field (Clickable)
                Box(
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = txDate,
                        onValueChange = { },
                        label = { Text("Date") },
                        readOnly = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            )
                        },
                        isError = !isDateValid,
                        supportingText = if (!isDateValid) {
                            { Text("Future dates invalid", color = MaterialTheme.colorScheme.error) }
                        } else null,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_tx_date")
                            .clickable {
                                val calendar = Calendar.getInstance()
                                try {
                                    val parts = txDate.split("-")
                                    if (parts.size == 3) {
                                        calendar.set(Calendar.YEAR, parts[0].toInt())
                                        calendar.set(Calendar.MONTH, parts[1].toInt() - 1)
                                        calendar.set(Calendar.DAY_OF_MONTH, parts[2].toInt())
                                    }
                                } catch (e: Exception) {}
                                
                                android.app.DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        val cal = Calendar.getInstance()
                                        cal.set(year, month, dayOfMonth)
                                        val formatted = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
                                        viewModel.txDateText.value = formatted
                                    },
                                    calendar.get(Calendar.YEAR),
                                    calendar.get(Calendar.MONTH),
                                    calendar.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            // Categories Selection Header
            Text(
                text = "Select Category",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Category Chips Selection Flow
            val activeCategories = if (isIncomeMode) incomeCategories else expenseCategories
            
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val chunks = activeCategories.chunked(3)
                chunks.forEach { rowCategories ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowCategories.forEach { category ->
                            val isSelected = txCat == category
                            val isColored = isSelected
                            
                            val chipColor = if (isColored) {
                                if (isIncomeMode) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                            } else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)

                            val textColor = if (isColored) {
                                if (isIncomeMode) Color(0xFF2E7D32) else Color(0xFFC62828)
                            } else MaterialTheme.colorScheme.onSurface

                            val borderColor = if (isColored) {
                                if (isIncomeMode) Color(0xFF81C784) else Color(0xFFEF5350)
                            } else MaterialTheme.colorScheme.outlineVariant

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(chipColor)
                                    .border(
                                        width = 1.dp,
                                        color = borderColor,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { viewModel.txCategoryText.value = category }
                                    .padding(vertical = 10.dp, horizontal = 4.dp)
                                    .testTag("form_category_pill_$category"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = category,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = textColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (rowCategories.size < 3) {
                            repeat(3 - rowCategories.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ElevatedButton(
                    onClick = {
                        viewModel.resetTxForm()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Clear Form")
                }

                Button(
                    onClick = {
                        viewModel.saveTransaction()
                        Toast.makeText(context, "Transaction successfully logged!", Toast.LENGTH_SHORT).show()
                    },
                    enabled = isFormValid,
                    modifier = Modifier
                        .weight(1.5f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isIncomeMode) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    )
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            text = if (editingTxId != null) "Update Entry" else "Save Entry",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun IncomeExpenseLedgerView(
    viewModel: com.example.viewmodel.LedgerViewModel,
    transactions: List<FinancialTransaction>
) {
    var searchCategory by remember { mutableStateOf("All") }
    var searchDatePreset by remember { mutableStateOf("All Time") }
    var customStartDate by remember { mutableStateOf("") }
    var customEndDate by remember { mutableStateOf("") }

    val filtered = remember(transactions, searchCategory, searchDatePreset, customStartDate, customEndDate) {
        filterTransactions(
            transactions = transactions,
            category = searchCategory,
            dateRangePreset = searchDatePreset,
            customStart = customStartDate,
            customEnd = customEndDate
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .testTag("income_expense_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            IncomeExpenseForm(
                viewModel = viewModel,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        item {
            TransactionMetricsPanel(transactions = filtered)
        }

        if (transactions.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "History Logs",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    
                    if (searchCategory != "All" || searchDatePreset != "All Time") {
                        TextButton(
                            onClick = {
                                searchCategory = "All"
                                searchDatePreset = "All Time"
                                customStartDate = ""
                                customEndDate = ""
                            }
                        ) {
                            Text("Reset Filters")
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val filterCategories = listOf("All", "Income", "Food & Dining", "Shopping", "Entertainment", "Rent & Bills", "Healthcare", "Other")
                    
                    Box(modifier = Modifier.weight(1f)) {
                        var expanded by remember { mutableStateOf(false) }
                        OutlinedCard(
                            onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Cat: $searchCategory",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            filterCategories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat) },
                                    onClick = {
                                        searchCategory = cat
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    val datePresets = listOf("All Time", "Today", "Last 7 Days")
                    
                    Box(modifier = Modifier.weight(1f)) {
                        var expanded by remember { mutableStateOf(false) }
                        OutlinedCard(
                            onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = searchDatePreset,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            datePresets.forEach { preset ->
                                DropdownMenuItem(
                                    text = { Text(preset) },
                                    onClick = {
                                        searchDatePreset = preset
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No records match current filters.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                    ) {
                        SortableTransactionTable(
                            transactions = filtered,
                            onEdit = { viewModel.startEditingTransaction(it) },
                            onDelete = { viewModel.deleteTransaction(it) }
                        )
                    }
                }
            }
        } else {
            item {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "No recorded logs yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Fill in the description, expected amount, category, and entry date in the form above to record your first income/expense ledger block.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
fun AppDedicatedBackButton(
    selectedTab: Int,
    isFormDirty: Boolean,
    onClick: () -> Unit,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val showBackIcon = selectedTab != 0

    Box(
        modifier = modifier
            .size(48.dp)
            .testTag("app_dedicated_back_container"),
        contentAlignment = Alignment.Center
    ) {
        if (showBackIcon) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onClick() }
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Navigate Back",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )

                // Unsaved Draft Halo / Badge
                if (isFormDirty) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = (-2).dp, y = 2.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFE53935)) // Glowing red for draft
                    )
                }
            }
        } else {
            // Summary Tab -> drawer menu button but also shows standard interactive back/menu if form dirty
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onOpenDrawer() }
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Open Navigation Drawer",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )

                if (isFormDirty) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = (-2).dp, y = 2.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF2E7D32)) // Glowing green for background draft
                    )
                }
            }
        }
    }
}

@Composable
fun DraftWarningDialog(
    onDismiss: () -> Unit,
    onAutoSave: () -> Unit,
    onDiscard: () -> Unit,
    onQuickSave: () -> Unit,
    isSaveEnabled: Boolean
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight()
                .clip(RoundedCornerShape(20.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with Warning Icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.errorContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    Column {
                        Text(
                            text = "Unsaved Draft Content",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Active session entries detected",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Text(
                    text = "You are departing from the current view, but there are unsubmitted inputs in your entry forms. Please choose what to do with your active draft session:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Dialog Action List
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Option 1: Auto-Save as Draft (leaves fields intact in background, closes)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onAutoSave() },
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Store Draft Session",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Maintain offline drafts. You can resume editing upon returning.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Option 2: Quick Save & Exit (Only if all form attributes are valid)
                    if (isSaveEnabled) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onQuickSave() },
                            color = Color(0xFFE8F5E9),
                            border = BorderStroke(1.dp, Color(0xFF81C784))
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color(0xFF2E7D32)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Commit & Save Record",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2E7D32)
                                    )
                                    Text(
                                        text = "Compile and log this transaction entry into history instantly.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Option 3: Discard Changes (Clears parameters and goes back)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onDiscard() },
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Discard Session Data",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = "Wipe current temporary unsaved values fully.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Close dialog without action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Keep Editing", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

