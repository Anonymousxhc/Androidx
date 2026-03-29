package com.akatsuki.trading.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akatsuki.trading.data.model.Order
import com.akatsuki.trading.data.model.Position
import com.akatsuki.trading.data.model.UiEvent
import com.akatsuki.trading.repository.KotakRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PositionsViewModel @Inject constructor(
    private val kotakRepository: KotakRepository
) : ViewModel() {

    private val _positions = MutableStateFlow<List<Position>>(emptyList())
    val positions: StateFlow<List<Position>> = _positions.asStateFlow()

    private val _orders = MutableStateFlow<List<Order>>(emptyList())
    val orders: StateFlow<List<Order>> = _orders.asStateFlow()

    private val _isLoadingPositions = MutableStateFlow(false)
    val isLoadingPositions: StateFlow<Boolean> = _isLoadingPositions.asStateFlow()

    private val _isLoadingOrders = MutableStateFlow(false)
    val isLoadingOrders: StateFlow<Boolean> = _isLoadingOrders.asStateFlow()

    private val _totalPnl = MutableStateFlow(0.0)
    val totalPnl: StateFlow<Double> = _totalPnl.asStateFlow()

    private val _uiEvents = MutableSharedFlow<UiEvent>(extraBufferCapacity = 16)
    val uiEvents = _uiEvents.asSharedFlow()

    init {
        refreshPositions()
        refreshOrders()
        observeHsiUpdates()
        startAutoRefresh()
    }

    fun refreshPositions() {
        viewModelScope.launch {
            _isLoadingPositions.value = true
            kotakRepository.getPositions()
                .onSuccess { list ->
                    _positions.value = list
                    _totalPnl.value = list.sumOf { it.pnl }
                }
                .onFailure { e ->
                    _uiEvents.emit(UiEvent.ShowToast("Positions error: ${e.message}"))
                }
            _isLoadingPositions.value = false
        }
    }

    fun refreshOrders() {
        viewModelScope.launch {
            _isLoadingOrders.value = true
            kotakRepository.getOrders()
                .onSuccess { _orders.value = it }
                .onFailure { e ->
                    _uiEvents.emit(UiEvent.ShowToast("Orders error: ${e.message}"))
                }
            _isLoadingOrders.value = false
        }
    }

    fun cancelOrder(orderNo: String) {
        viewModelScope.launch {
            kotakRepository.cancelOrder(orderNo)
                .onSuccess { ok ->
                    if (ok) {
                        _uiEvents.emit(UiEvent.ShowToast("Order cancelled"))
                        refreshOrders()
                    } else {
                        _uiEvents.emit(UiEvent.ShowToast("Cancel failed"))
                    }
                }
                .onFailure { e ->
                    _uiEvents.emit(UiEvent.ShowToast("Cancel error: ${e.message}"))
                }
        }
    }

    fun closePosition(position: Position) {
        viewModelScope.launch {
            val tt = if (position.netQty > 0) "S" else "B"
            val qty = kotlin.math.abs(position.netQty)
            val req = com.akatsuki.trading.data.model.OrderRequest(
                es = position.segment,
                ts = position.tradingSymbol,
                tt = tt,
                qty = qty,
                pc = position.productCode.ifEmpty { "MIS" }
            )
            kotakRepository.placeOrder(req)
                .onSuccess { (ok, msg) ->
                    _uiEvents.emit(
                        if (ok) UiEvent.ShowToast("Closed ${position.tradingSymbol}")
                        else UiEvent.ShowToast("Close failed: $msg")
                    )
                    if (ok) refreshPositions()
                }
                .onFailure { e ->
                    _uiEvents.emit(UiEvent.ShowToast("Error: ${e.message}"))
                }
        }
    }

    private fun observeHsiUpdates() {
        viewModelScope.launch {
            kotakRepository.getHsiEvents().collect { event ->
                when (event) {
                    is UiEvent.OrderUpdate -> {
                        val status = event.status.lowercase()
                        if (status.contains("complete") || status.contains("traded")) {
                            delay(500L)
                            refreshPositions()
                        }
                        refreshOrders()
                    }
                    is UiEvent.PositionUpdate -> {
                        val updated = _positions.value.map { pos ->
                            if (pos.tradingSymbol == event.tradingSymbol) {
                                pos.copy(ltp = event.ltp)
                            } else pos
                        }
                        _positions.value = updated
                        _totalPnl.value = updated.sumOf { it.pnl }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun startAutoRefresh() {
        viewModelScope.launch {
            while (true) {
                delay(30_000L)
                refreshPositions()
            }
        }
    }
}
