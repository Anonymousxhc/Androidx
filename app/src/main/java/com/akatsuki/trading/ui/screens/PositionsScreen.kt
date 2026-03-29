package com.akatsuki.trading.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akatsuki.trading.data.model.Position
import com.akatsuki.trading.data.model.UiEvent
import com.akatsuki.trading.ui.components.*
import com.akatsuki.trading.ui.theme.*
import com.akatsuki.trading.viewmodel.PositionsViewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
fun PositionsScreen(vm: PositionsViewModel) {
    val positions by vm.positions.collectAsState()
    val totalPnl by vm.totalPnl.collectAsState()
    val isLoading by vm.isLoadingPositions.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.uiEvents.collectLatest { event ->
            if (event is UiEvent.ShowToast) snackbarHostState.showSnackbar(event.message)
        }
    }

    var showCloseDialog by remember { mutableStateOf<Position?>(null) }
    showCloseDialog?.let { pos ->
        ConfirmDialog(
            title = "Close Position",
            message = "Close ${pos.tradingSymbol} × ${kotlin.math.abs(pos.netQty)}?",
            onConfirm = {
                vm.closePosition(pos)
                showCloseDialog = null
            },
            onDismiss = { showCloseDialog = null }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Background)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Surface)
                    .border(1.dp, Border, RoundedCornerShape(0.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "POSITIONS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )
                    if (positions.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(BlueAlpha)
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text("${positions.size}", color = Blue, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                IconButton(onClick = vm::refreshPositions, modifier = Modifier.size(32.dp)) {
                    if (isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 1.5.dp, color = Blue)
                    else Icon(Icons.Default.Refresh, null, tint = TextMuted, modifier = Modifier.size(18.dp))
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BackgroundAlt)
                    .border(1.dp, Border, RoundedCornerShape(0.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Total P&L", fontSize = 11.sp, color = TextMuted)
                PnlText(totalPnl, fontSize = 16.sp)
            }

            if (positions.isEmpty() && !isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📊", fontSize = 40.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("No open positions", color = TextMuted, fontSize = 14.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(positions, key = { it.tradingSymbol }) { pos ->
                        PositionCard(
                            position = pos,
                            onClose = { showCloseDialog = pos }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PositionCard(position: Position, onClose: () -> Unit) {
    val pnl = position.pnl
    val pnlColor = if (pnl >= 0) Green else Red
    val isLong = position.netQty > 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = position.tradingSymbol,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "${position.productCode} · ${if (isLong) "LONG" else "SHORT"}",
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }
                PnlText(pnl, fontSize = 15.sp)
            }

            HorizontalDivider(color = Border, thickness = 0.5.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DataPoint("QTY", "${position.netQty}", if (isLong) Green else Red)
                DataPoint("LTP", "₹%.2f".format(position.ltp), TextPrimary)
                DataPoint("BUY", "₹%.2f".format(if (position.buyQty > 0) position.buyAmt / position.buyQty else 0.0), TextSecondary)
                DataPoint("SELL", "₹%.2f".format(if (position.sellQty > 0) position.sellAmt / position.sellQty else 0.0), TextSecondary)
            }

            OutlinedButton(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth().height(32.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Red),
                border = androidx.compose.foundation.BorderStroke(1.dp, RedAlpha),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("Close Position", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun DataPoint(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 0.06.sp)
        Text(value, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}
