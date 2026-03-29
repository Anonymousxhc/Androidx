package com.akatsuki.trading.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akatsuki.trading.data.model.Order
import com.akatsuki.trading.data.model.UiEvent
import com.akatsuki.trading.ui.components.*
import com.akatsuki.trading.ui.theme.*
import com.akatsuki.trading.viewmodel.PositionsViewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
fun OrderbookScreen(vm: PositionsViewModel) {
    val orders by vm.orders.collectAsState()
    val isLoading by vm.isLoadingOrders.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedFilter by remember { mutableStateOf("All") }

    LaunchedEffect(Unit) {
        vm.uiEvents.collectLatest { event ->
            if (event is UiEvent.ShowToast) snackbarHostState.showSnackbar(event.message)
        }
    }

    val filters = listOf("All", "Pending", "Complete", "Cancelled")
    val filteredOrders = orders.filter { order ->
        when (selectedFilter) {
            "Pending" -> order.status.lowercase().let { it.contains("open") || it.contains("pending") || it.contains("trigger") }
            "Complete" -> order.status.lowercase().let { it.contains("complete") || it.contains("traded") }
            "Cancelled" -> order.status.lowercase().let { it.contains("cancel") || it.contains("reject") }
            else -> true
        }
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
                Text("ORDERS", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                IconButton(onClick = vm::refreshOrders, modifier = Modifier.size(32.dp)) {
                    if (isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 1.5.dp, color = Blue)
                    else Icon(Icons.Default.Refresh, null, tint = TextMuted, modifier = Modifier.size(18.dp))
                }
            }

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BackgroundAlt)
                    .border(1.dp, Border, RoundedCornerShape(0.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filters) { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BlueAlpha,
                            selectedLabelColor = Blue,
                            containerColor = Surface2,
                            labelColor = TextMuted
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            selectedBorderColor = Blue.copy(alpha = 0.3f),
                            borderColor = Border
                        )
                    )
                }
            }

            if (filteredOrders.isEmpty() && !isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📋", fontSize = 40.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("No orders found", color = TextMuted, fontSize = 14.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredOrders, key = { it.orderNo }) { order ->
                        OrderCard(
                            order = order,
                            onCancel = if (order.status.lowercase().contains("open") ||
                                order.status.lowercase().contains("pending")) {
                                { vm.cancelOrder(order.orderNo) }
                            } else null
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OrderCard(order: Order, onCancel: (() -> Unit)?) {
    val isBuy = order.side.uppercase() == "B"
    val sideLabel = if (isBuy) "BUY" else "SELL"
    val sideColor = if (isBuy) Green else Red

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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isBuy) GreenAlpha else RedAlpha)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(sideLabel, color = sideColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = order.tradingSymbol,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = TextPrimary
                    )
                    Text("× ${order.qty}", fontSize = 11.sp, color = TextSecondary)
                }
                StatusChip(order.status)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(order.orderType, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = TextMuted)
                    Text("·", color = TextMuted, fontSize = 10.sp)
                    Text(order.productCode, fontSize = 10.sp, color = TextMuted)
                    if (order.price > 0) {
                        Text("· ₹%.2f".format(order.price), fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = TextSecondary)
                    }
                }
                Text(order.time.take(8), fontSize = 10.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
            }

            if (order.orderNo.isNotEmpty()) {
                Text(
                    "# ${order.orderNo}",
                    fontSize = 9.sp,
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace
                )
            }

            if (onCancel != null) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth().height(30.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Red),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RedAlpha),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Cancel Order", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
