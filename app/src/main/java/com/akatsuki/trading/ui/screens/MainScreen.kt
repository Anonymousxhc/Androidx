package com.akatsuki.trading.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.akatsuki.trading.data.model.StrikeRow
import com.akatsuki.trading.data.model.UiEvent
import com.akatsuki.trading.ui.components.*
import com.akatsuki.trading.ui.theme.*
import com.akatsuki.trading.viewmodel.AuthViewModel
import com.akatsuki.trading.viewmodel.TradingViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MainHostScreen(
    authViewModel: AuthViewModel,
    onLogout: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tradingViewModel: TradingViewModel = hiltViewModel()
    val positionsViewModel: com.akatsuki.trading.viewmodel.PositionsViewModel = hiltViewModel()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        tradingViewModel.uiEvents.collectLatest { event ->
            when (event) {
                is UiEvent.OrderResult -> {
                    val msg = if (event.success) "✓ ${event.symbol}" else "✗ ${event.message}"
                    snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
                    if (event.success) positionsViewModel.refreshPositions()
                }
                is UiEvent.ShowToast -> snackbarHostState.showSnackbar(event.message, duration = SnackbarDuration.Short)
                else -> {}
            }
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                val isSuccess = data.visuals.message.startsWith("✓")
                val isError = data.visuals.message.startsWith("✗")
                Snackbar(
                    modifier = Modifier.padding(12.dp),
                    containerColor = when {
                        isSuccess -> GreenAlpha
                        isError -> RedAlpha
                        else -> Surface2
                    },
                    contentColor = when {
                        isSuccess -> Green
                        isError -> Red
                        else -> TextPrimary
                    },
                    snackbarData = data
                )
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = Surface,
                tonalElevation = 0.dp,
                modifier = Modifier.border(
                    width = 1.dp,
                    color = Border,
                    shape = RoundedCornerShape(0.dp)
                )
            ) {
                val items = listOf(
                    Triple("Trade", Icons.Default.ShowChart, 0),
                    Triple("Positions", Icons.Default.AccountBalance, 1),
                    Triple("Orders", Icons.Default.Receipt, 2),
                    Triple("Settings", Icons.Default.Settings, 3)
                )
                items.forEach { (label, icon, index) ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp)) },
                        label = { Text(label, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Blue,
                            selectedTextColor = Blue,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted,
                            indicatorColor = BlueAlpha
                        )
                    )
                }
            }
        },
        containerColor = Background
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> TradingTab(tradingViewModel)
                1 -> PositionsScreen(positionsViewModel)
                2 -> OrderbookScreen(positionsViewModel)
                3 -> SettingsScreen(authViewModel, onLogout)
            }
        }
    }
}

@Composable
fun TradingTab(vm: TradingViewModel) {
    val selectedIndex by vm.selectedIndex.collectAsState()
    val expiries by vm.expiries.collectAsState()
    val selectedExpiry by vm.selectedExpiry.collectAsState()
    val numStrikes by vm.numStrikes.collectAsState()
    val spotPrice by vm.spotPrice.collectAsState()
    val chain by vm.chain.collectAsState()
    val selectedStrike by vm.selectedStrike.collectAsState()
    val lotMultiplier by vm.lotMultiplier.collectAsState()
    val limits by vm.limits.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val instrumentsReady by vm.instrumentsReady.collectAsState()

    var showCloseAllDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (showCloseAllDialog) {
        ConfirmDialog(
            title = "Close All Positions",
            message = "This will market-sell all open positions. Continue?",
            onConfirm = {
                showCloseAllDialog = false
                scope.launch { vm.closeAllPositions() }
            },
            onDismiss = { showCloseAllDialog = false }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(Background)) {
        AppHeader(
            selectedIndex = selectedIndex,
            spotPrice = spotPrice,
            isConnected = true,
            limits = limits,
            onLogout = {}
        )

        ControlsBar(
            indices = vm.indices,
            selectedIndex = selectedIndex,
            expiries = expiries.map { it.label },
            selectedExpiry = selectedExpiry,
            strikeOptions = listOf(3, 5, 7, 10),
            numStrikes = numStrikes,
            onIndexSelected = vm::selectIndex,
            onExpirySelected = vm::selectExpiry,
            onStrikesSelected = vm::selectStrikeCount,
            onRefresh = vm::refresh,
            onCloseAll = { showCloseAllDialog = true },
            isLoading = isLoading
        )

        ActionBar(
            selectedStrike = selectedStrike,
            lotMultiplier = lotMultiplier,
            onLotDecrement = { vm.setLotMultiplier(lotMultiplier - 1) },
            onLotIncrement = { vm.setLotMultiplier(lotMultiplier + 1) },
            onBuyCall = vm::buyCall,
            onSellCall = vm::sellCall,
            onBuyPut = vm::buyPut,
            onSellPut = vm::sellPut,
            enabled = instrumentsReady
        )

        if (!instrumentsReady) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = Blue, strokeWidth = 2.dp)
                    Text("Loading instruments...", color = TextMuted, fontSize = 12.sp)
                }
            }
        } else {
            OptionChainTable(
                chain = chain,
                selectedStrike = selectedStrike,
                onStrikeSelected = vm::selectStrike,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun AppHeader(
    selectedIndex: String,
    spotPrice: Double,
    isConnected: Boolean,
    limits: com.akatsuki.trading.data.model.Limits?,
    onLogout: () -> Unit
) {
    var clock by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        fmt.timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        while (true) {
            clock = fmt.format(Date())
            kotlinx.coroutines.delay(1000L)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface)
            .border(width = 1.dp, color = Border, shape = RoundedCornerShape(0.dp))
            .padding(horizontal = 14.dp)
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("⚡", fontSize = 18.sp)
            Text(
                text = "AKATSUKI",
                fontFamily = FontFamily.Monospace,
                color = Blue,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "IST $clock",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TextMuted
            )
            StatusPill(isLive = isConnected)
        }
    }

    if (limits != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BackgroundAlt)
                .border(width = 1.dp, color = Border, shape = RoundedCornerShape(0.dp))
                .height(28.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FundItem("CASH", "₹${"%.0f".format(limits.availableCash)}")
            VerticalDivider(modifier = Modifier.height(20.dp), thickness = 1.dp, color = Border)
            FundItem("MARGIN", "₹${"%.0f".format(limits.usedMargin)}")
            VerticalDivider(modifier = Modifier.height(20.dp), thickness = 1.dp, color = Border)
            FundItem("NET", "₹${"%.0f".format(limits.totalBalance)}")
        }
    }
}

@Composable
fun RowScope.FundItem(label: String, value: String) {
    Row(
        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.06.sp, color = TextMuted)
        Text(value, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
    }
}

@Composable
fun ControlsBar(
    indices: List<String>,
    selectedIndex: String,
    expiries: List<String>,
    selectedExpiry: String,
    strikeOptions: List<Int>,
    numStrikes: Int,
    onIndexSelected: (String) -> Unit,
    onExpirySelected: (String) -> Unit,
    onStrikesSelected: (Int) -> Unit,
    onRefresh: () -> Unit,
    onCloseAll: () -> Unit,
    isLoading: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface)
            .border(width = 1.dp, color = Border, shape = RoundedCornerShape(0.dp))
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DropdownChip(
                label = "IDX",
                selected = selectedIndex,
                options = indices,
                onSelected = onIndexSelected,
                modifier = Modifier.weight(1.2f)
            )
            DropdownChip(
                label = "EXP",
                selected = selectedExpiry.take(11),
                options = expiries,
                onSelected = onExpirySelected,
                modifier = Modifier.weight(1.8f)
            )
            DropdownChip(
                label = "STK",
                selected = numStrikes.toString(),
                options = strikeOptions.map { it.toString() },
                onSelected = { onStrikesSelected(it.toInt()) },
                modifier = Modifier.weight(0.8f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = onRefresh,
                    modifier = Modifier.height(30.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Border),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    if (isLoading) CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp, color = Blue)
                    else Text("↺ Reload", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }

                OutlinedButton(
                    onClick = onCloseAll,
                    modifier = Modifier.height(30.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Red),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RedAlpha),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    Text("✕ Close All", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun DropdownChip(
    label: String,
    selected: String,
    options: List<String>,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().height(32.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = BackgroundAlt,
                contentColor = TextPrimary
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, Border),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
        ) {
            Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
                Text(label, fontSize = 8.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                Text(
                    selected.ifEmpty { "—" },
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextPrimary,
                    maxLines = 1
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Surface2)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                    modifier = if (option == selected) Modifier.background(BlueAlpha) else Modifier
                )
            }
        }
    }
}

@Composable
fun ActionBar(
    selectedStrike: StrikeRow?,
    lotMultiplier: Int,
    onLotDecrement: () -> Unit,
    onLotIncrement: () -> Unit,
    onBuyCall: () -> Unit,
    onSellCall: () -> Unit,
    onBuyPut: () -> Unit,
    onSellPut: () -> Unit,
    enabled: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface)
            .border(width = 2.dp, color = Border, shape = RoundedCornerShape(0.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(GreenAlpha2)
                    .border(1.dp, Color(0x3310B981), RoundedCornerShape(4.dp))
                    .padding(vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("CE", color = Green, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            TradeButton("BUY CE", onBuyCall, true, modifier = Modifier.fillMaxWidth(), enabled = enabled && selectedStrike?.ce != null)
            TradeButton("SELL CE", onSellCall, false, modifier = Modifier.fillMaxWidth(), enabled = enabled && selectedStrike?.ce != null)
            if (selectedStrike != null && selectedStrike.ceLtp > 0) {
                FlashingPriceText(selectedStrike.ceLtp, color = Green, fontSize = 11.sp, modifier = Modifier.fillMaxWidth())
            }
        }

        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (selectedStrike != null) {
                Text(
                    text = "%.0f".format(selectedStrike.strike),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Yellow,
                    textAlign = TextAlign.Center
                )
            } else {
                Text("— Select Strike —", color = TextMuted, fontSize = 11.sp, textAlign = TextAlign.Center)
            }
            LotSelector(
                value = lotMultiplier,
                onDecrement = onLotDecrement,
                onIncrement = onLotIncrement,
                onValueChange = {}
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(RedAlpha2)
                    .border(1.dp, Color(0x33EF4444), RoundedCornerShape(4.dp))
                    .padding(vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("PE", color = Red, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            TradeButton("BUY PE", onBuyPut, true, modifier = Modifier.fillMaxWidth(), enabled = enabled && selectedStrike?.pe != null)
            TradeButton("SELL PE", onSellPut, false, modifier = Modifier.fillMaxWidth(), enabled = enabled && selectedStrike?.pe != null)
            if (selectedStrike != null && selectedStrike.peLtp > 0) {
                FlashingPriceText(selectedStrike.peLtp, color = Red, fontSize = 11.sp, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun OptionChainTable(
    chain: com.akatsuki.trading.data.model.OptionChainResult?,
    selectedStrike: StrikeRow?,
    onStrikeSelected: (StrikeRow) -> Unit,
    modifier: Modifier = Modifier
) {
    if (chain == null) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("No data — tap Reload", color = TextMuted, fontSize = 12.sp)
        }
        return
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface)
                .border(width = 1.dp, color = Border, shape = RoundedCornerShape(0.dp))
                .padding(vertical = 7.dp, horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("CE LTP", color = Green, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Text("STRIKE", color = Yellow, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.8f), textAlign = TextAlign.Center)
            Text("PE LTP", color = Red, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(chain.chain, key = { it.strike }) { row ->
                ChainRow(
                    row = row,
                    isSelected = selectedStrike?.strike == row.strike,
                    onClick = { onStrikeSelected(row) }
                )
                HorizontalDivider(color = Border.copy(alpha = 0.5f), thickness = 0.5.dp)
            }
        }
    }
}

@Composable
fun ChainRow(row: StrikeRow, isSelected: Boolean, onClick: () -> Unit) {
    val bg = when {
        isSelected -> YellowAlpha
        row.isAtm -> BlueAlpha
        else -> Color.Transparent
    }
    val leftBorder = when {
        isSelected -> Yellow
        row.isAtm -> Blue
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .border(width = 0.dp, color = Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 6.dp, horizontal = 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(3.dp).height(40.dp).background(leftBorder))

        Box(
            modifier = Modifier
                .weight(1f)
                .background(if (row.isAtm) GreenAlpha2 else GreenAlpha2.copy(alpha = 0.3f))
                .padding(vertical = 4.dp, horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FlashingPriceText(
                    price = row.ceLtp,
                    color = Green,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                if (row.ce != null) {
                    Text(
                        text = row.ce.tradingSymbol.takeLast(12),
                        fontSize = 8.sp,
                        color = TextMuted,
                        maxLines = 1
                    )
                }
            }
        }

        Box(
            modifier = Modifier.weight(0.8f).padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "%.0f".format(row.strike),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Yellow,
                    textAlign = TextAlign.Center
                )
                if (row.isAtm) {
                    Text(
                        "ATM",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Blue,
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(BlueAlpha)
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .background(if (row.isAtm) RedAlpha2 else RedAlpha2.copy(alpha = 0.3f))
                .padding(vertical = 4.dp, horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FlashingPriceText(
                    price = row.peLtp,
                    color = Red,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                if (row.pe != null) {
                    Text(
                        text = row.pe.tradingSymbol.takeLast(12),
                        fontSize = 8.sp,
                        color = TextMuted,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
