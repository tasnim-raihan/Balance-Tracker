package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.example.data.LedgerEntry
import com.example.data.FinancialTransaction
import com.example.data.WalletAccount
import com.example.domain.LedgerCalculator
import com.example.viewmodel.LedgerViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerDashboard(
    viewModel: LedgerViewModel,
    modifier: Modifier = Modifier
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(0) } // 0 = Points Ledger, 1 = Manual Transactions
    var isAddDialogOpen by remember { mutableStateOf(false) }
    var isAddTxDialogOpen by remember { mutableStateOf(false) }
    var isDeleteAllConfirmOpen by remember { mutableStateOf(false) }
    var isDeleteAllTxConfirmOpen by remember { mutableStateOf(false) }

    var filterCategory by remember { mutableStateOf("All") }
    var filterDateRangePreset by remember { mutableStateOf("All Time") }
    var customStartDate by remember { mutableStateOf("") }
    var customEndDate by remember { mutableStateOf("") }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "Balance Tracker",
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                },
                actions = {
                    if (selectedTab == 0 && entries.isNotEmpty()) {
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
                    } else if (selectedTab == 1 && transactions.isNotEmpty()) {
                        IconButton(
                            onClick = { isDeleteAllTxConfirmOpen = true },
                            modifier = Modifier.testTag("delete_all_tx_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Clear All Transactions",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                )
            )
        },
        floatingActionButton = {
            if (selectedTab < 2) {
                FloatingActionButton(
                    onClick = {
                        if (selectedTab == 0) {
                            viewModel.resetForm()
                            isAddDialogOpen = true
                        } else {
                            viewModel.resetTxForm()
                            isAddTxDialogOpen = true
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag(if (selectedTab == 0) "add_ledger_fab" else "add_tx_fab")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = if (selectedTab == 0) "Add New Points Entry" else "Add New Financial Transaction",
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
            // Tab Selector Switches
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("Points Ledger", "Manual Transactions", "Wallet Accounts").forEachIndexed { index, title ->
                    val isSelected = selectedTab == index
                    val containerColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(containerColor)
                            .clickable { selectedTab = index }
                            .padding(vertical = 10.dp)
                            .testTag("tab_selector_$index"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = when (index) {
                                    0 -> Icons.Default.ShoppingCart
                                    1 -> Icons.Default.Star
                                    else -> Icons.Default.List
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = contentColor
                            )
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = contentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            if (selectedTab == 0) {
                // Points Ledger View
                MetricsSummaryPanel(entries)

                if (entries.isEmpty()) {
                    EmptyStateView {
                        viewModel.resetForm()
                        isAddDialogOpen = true
                    }
                } else {
                    Text(
                        text = "Ledger History",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                            .testTag("ledger_list"),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = entries,
                            key = { it.id }
                        ) { entry ->
                            LedgerCard(
                                entry = entry,
                                onDelete = { viewModel.deleteEntry(entry.id) }
                            )
                        }
                    }
                }
            } else if (selectedTab == 1) {
                // Manual Transactions View
                val filteredTransactions = filterTransactions(
                    transactions = transactions,
                    category = filterCategory,
                    dateRangePreset = filterDateRangePreset,
                    customStart = customStartDate,
                    customEnd = customEndDate
                )

                TransactionMetricsPanel(filteredTransactions)

                if (transactions.isEmpty()) {
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
                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(64.dp)
                                )
                            }

                            Text(
                                text = "No Transactions Yet",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onBackground
                            )

                            Text(
                                text = "Track physical and manual financial flows with custom dates, specific categories, details, and exact monetary values.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )

                            Button(
                                onClick = {
                                    viewModel.resetTxForm()
                                    isAddTxDialogOpen = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("empty_state_add_tx_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Create First Transaction", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    TransactionFilterBar(
                        selectedCategory = filterCategory,
                        onCategorySelected = { filterCategory = it },
                        selectedDateRange = filterDateRangePreset,
                        onDateRangeSelected = { filterDateRangePreset = it },
                        customStartDate = customStartDate,
                        onCustomStartSelected = { customStartDate = it },
                        customEndDate = customEndDate,
                        onCustomEndSelected = { customEndDate = it },
                        onResetFilters = {
                            filterCategory = "All"
                            filterDateRangePreset = "All Time"
                            customStartDate = ""
                            customEndDate = ""
                        }
                    )

                    if (filteredTransactions.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
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
                                        .size(80.dp)
                                        .clip(RoundedCornerShape(40.dp))
                                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }

                                Text(
                                    text = "No Matching Transactions",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )

                                Text(
                                    text = "No stored transactions match your current category or date range filter.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                )

                                Button(
                                    onClick = {
                                        filterCategory = "All"
                                        filterDateRangePreset = "All Time"
                                        customStartDate = ""
                                        customEndDate = ""
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("filter_reset_empty_button")
                                ) {
                                    Text("Clear All Filters", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "Transaction History",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f)
                                .testTag("tx_list"),
                            contentPadding = PaddingValues(bottom = 80.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                items = filteredTransactions,
                                key = { it.id }
                            ) { transaction ->
                                TransactionCard(
                                    transaction = transaction,
                                    onDelete = { viewModel.deleteTransaction(transaction.id) }
                                )
                            }
                        }
                    }
                }
            } else {
                // Wallet Accounts View
                val walletAccounts by viewModel.walletAccounts.collectAsStateWithLifecycle()
                val totalWalletBalance by viewModel.totalWalletBalance.collectAsStateWithLifecycle()
                WalletAccountsView(
                    walletAccounts = walletAccounts,
                    totalWalletBalance = totalWalletBalance,
                    onUpdateBalance = { id, balance -> viewModel.updateWalletAccountBalance(id, balance) }
                )
            }
        }
    }

    // Modal Add Dialog (Points)
    if (isAddDialogOpen) {
        AddLedgerDialog(
            viewModel = viewModel,
            onDismiss = { isAddDialogOpen = false },
            onSave = {
                viewModel.saveEntry()
                isAddDialogOpen = false
            }
        )
    }

    // Modal Add Dialog (Financial Transactions)
    if (isAddTxDialogOpen) {
        AddTransactionDialog(
            viewModel = viewModel,
            onDismiss = { isAddTxDialogOpen = false },
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
}

@Composable
fun MetricsSummaryPanel(entries: List<LedgerEntry>) {
    val usdFormatter = remember { NumberFormat.getCurrencyInstance(Locale.US) }
    val latest = entries.firstOrNull()

    val walletBalance = latest?.walletBalance ?: 0
    val totalDeficit = entries.sumOf { it.deficit }
    val salesCount = entries.count { it.transactionType == "Sale" }
    val productInHandCount = entries.count { it.transactionType == "Product in Hand" }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
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
                text = "OVERVIEW SUMMARY",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Wallet Balance Info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Current Wallet Balance",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (entries.isEmpty()) "--" else usdFormatter.format(walletBalance),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
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
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = deficitColor
                    )
                }
            }

            Divider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricMiniItem(
                    label = "Total Entries",
                    value = entries.size.toString(),
                    icon = Icons.Default.List
                )
                MetricMiniItem(
                    label = "Sales Tracked",
                    value = salesCount.toString(),
                    icon = Icons.Default.KeyboardArrowUp,
                    tint = Color(0xFF028A3C)
                )
                MetricMiniItem(
                    label = "Products in Hand",
                    value = productInHandCount.toString(),
                    icon = Icons.Default.List,
                    tint = Color(0xFFC07000)
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
    tint: Color = MaterialTheme.colorScheme.primary
) {
    Row(
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
fun LedgerCard(
    entry: LedgerEntry,
    onDelete: () -> Unit
) {
    val usdFormatter = remember { NumberFormat.getCurrencyInstance(Locale.US) }
    val formattedDate = remember(entry.timestamp) {
        val sdf = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
        sdf.format(Date(entry.timestamp))
    }

    var isMenuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("ledger_card_${entry.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp)
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
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Transaction Type Badge
                    TransactionBadge(type = entry.transactionType)

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
            fontSize = 10.sp
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
    val isFormValid by viewModel.isFormValid.collectAsStateWithLifecycle()

    val liveCalc by viewModel.liveCalculation.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()

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
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Add Ledger Record",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
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
                        text = "3. Ledger Notes (Optional)",
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

                    Spacer(modifier = Modifier.height(6.dp))

                    // Live auto calculations displaying results as the user types
                    LiveCalculationsPreviewBlock(liveCalc)
                }

                // Error validation prompt if relevant
                if (!isFormValid && (prevPoints.isNotEmpty() || availPoints.isNotEmpty() || prevBalance.isNotEmpty() || walletBalance.isNotEmpty())) {
                    Text(
                        text = "⚠️ Please enter valid integers in all 4 numbered fields.",
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
                        Text("Save Record", fontWeight = FontWeight.Black)
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
                    color = MaterialTheme.colorScheme.primary
                )

                // Quick badge
                TransactionBadge(type = calc.transactionType)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Net change
                MathDataRow(
                    label = "Step 1: Net Change (Prev - Avail Points)",
                    value = if (calc.netChange >= 0) "+${calc.netChange}" else "${calc.netChange}"
                )

                // Transaction type
                MathDataRow(
                    label = "Step 2: Transaction Pattern Amount",
                    value = "${calc.transactionAmount} (${calc.transactionType})"
                )

                // Expected Balance
                MathDataRow(
                    label = "Step 3: Expected Balance (Prev Bal + Net Change)",
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
                    label = "Step 4: Variance Deficit (Expected - Wallet)",
                    value = usdFormatter.format(calc.deficit),
                    valueColor = defColor,
                    isBold = true
                )
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
            fontSize = 11.sp
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isBold) FontWeight.Black else FontWeight.Bold,
            color = valueColor,
            fontSize = 11.sp
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
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("tx_card_${transaction.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp)
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
                        color = color
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = transaction.date,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
    
    val context = LocalContext.current
    
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Add Transaction",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
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
                        Text("Save Transaction", fontWeight = FontWeight.Black)
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
                    onEditClick = { editingAccount = account }
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
    onEditClick: () -> Unit
) {
    val isBkash = account.type.contains("Bkash", ignoreCase = true)
    val brandColor = if (isBkash) Color(0xFFE2125B) else Color(0xFFF15A22)
    val brandContainer = if (isBkash) Color(0xFFFDF0F4) else Color(0xFFFFF2EE)

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onEditClick() }
            .testTag("wallet_account_card_${account.id}"),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.5.dp, brandColor.copy(alpha = 0.3f))
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
