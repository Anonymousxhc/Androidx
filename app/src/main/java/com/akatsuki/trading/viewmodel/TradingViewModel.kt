package com.akatsuki.trading.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akatsuki.trading.data.CredentialStore
import com.akatsuki.trading.data.model.ExpiryEntry
import com.akatsuki.trading.data.model.Limits
import com.akatsuki.trading.data.model.OptionChainResult
import com.akatsuki.trading.data.model.OrderRequest
import com.akatsuki.trading.data.model.StrikeRow
import com.akatsuki.trading.data.model.UiEvent
import com.akatsuki.trading.repository.InstrumentsRepository
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
class TradingViewModel @Inject constructor(
    private val kotakRepository: KotakRepository,
    private val instrumentsRepository: InstrumentsRepository,
    private val credentialStore: CredentialStore
) : ViewModel() {

    val indices = listOf("NIFTY", "BANKNIFTY", "SENSEX", "FINNIFTY", "MIDCPNIFTY", "BANKEX")

    private val _selectedIndex = MutableStateFlow("NIFTY")
    val selectedIndex: StateFlow<String> = _selectedIndex.asStateFlow()

    private val _selectedExpiry = MutableStateFlow("")
    val selectedExpiry: StateFlow<String> = _selectedExpiry.asStateFlow()

    private val _expiries = MutableStateFlow<List<ExpiryEntry>>(emptyList())
    val expiries: StateFlow<List<ExpiryEntry>> = _expiries.asStateFlow()

    private val _numStrikes = MutableStateFlow(5)
    val numStrikes: StateFlow<Int> = _numStrikes.asStateFlow()

    private val _spotPrice = MutableStateFlow(0.0)
    val spotPrice: StateFlow<Double> = _spotPrice.asStateFlow()

    private val _chain = MutableStateFlow<OptionChainResult?>(null)
    val chain: StateFlow<OptionChainResult?> = _chain.asStateFlow()

    private val _ltpMap = MutableStateFlow<Map<String, Double>>(emptyMap())
    val ltpMap: StateFlow<Map<String, Double>> = _ltpMap.asStateFlow()

    private val _selectedStrike = MutableStateFlow<StrikeRow?>(null)
    val selectedStrike: StateFlow<StrikeRow?> = _selectedStrike.asStateFlow()

    private val _lotMultiplier = MutableStateFlow(1)
    val lotMultiplier: StateFlow<Int> = _lotMultiplier.asStateFlow()

    private val _limits = MutableStateFlow<Limits?>(null)
    val limits: StateFlow<Limits?> = _limits.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _instrumentsReady = MutableStateFlow(false)
    val instrumentsReady: StateFlow<Boolean> = _instrumentsReady.asStateFlow()

    private val _uiEvents = MutableSharedFlow<UiEvent>(extraBufferCapacity = 32)
    val uiEvents = _uiEvents.asSharedFlow()

    init {
        observeLtpTicks()
        observeHsiEvents()
        preloadInstruments()
        refreshLimits()
    }

    private fun preloadInstruments() {
        viewModelScope.launch {
            instrumentsRepository.injectedCredentialStore = credentialStore
            _isLoading.value = true
            instrumentsRepository.preloadInstruments()
                .onSuccess {
                    _instrumentsReady.value = true
                    loadExpiries()
                    refreshSpotAndChain()
                }
                .onFailure { e ->
                    _uiEvents.emit(UiEvent.ShowToast("Instruments load failed: ${e.message}"))
                    _isLoading.value = false
                }
        }
    }

    private fun loadExpiries() {
        val expiries = instrumentsRepository.getExpiries(_selectedIndex.value)
        _expiries.value = expiries
        if (_selectedExpiry.value.isEmpty() && expiries.isNotEmpty()) {
            _selectedExpiry.value = expiries.first().label
        }
    }

    fun selectIndex(index: String) {
        _selectedIndex.value = index
        _selectedExpiry.value = ""
        _selectedStrike.value = null
        _chain.value = null
        loadExpiries()
        viewModelScope.launch { refreshSpotAndChain() }
    }

    fun selectExpiry(expiry: String) {
        _selectedExpiry.value = expiry
        rebuildChain()
    }

    fun selectStrikeCount(count: Int) {
        _numStrikes.value = count
        rebuildChain()
    }

    fun selectStrike(strike: StrikeRow) {
        _selectedStrike.value = strike
    }

    fun setLotMultiplier(mult: Int) {
        _lotMultiplier.value = mult.coerceIn(1, 50)
    }

    fun refresh() {
        viewModelScope.launch { refreshSpotAndChain() }
    }

    private suspend fun refreshSpotAndChain() {
        _isLoading.value = true
        kotakRepository.getSpot(_selectedIndex.value)
            .onSuccess { spot ->
                _spotPrice.value = spot
                rebuildChain()
                subscribeChainTokens()
            }
            .onFailure { e ->
                _uiEvents.emit(UiEvent.ShowToast("Spot fetch failed: ${e.message}"))
            }
        _isLoading.value = false
    }

    private fun rebuildChain() {
        val expiry = _selectedExpiry.value
        val spot = _spotPrice.value
        if (expiry.isEmpty() || spot <= 0) return

        val result = instrumentsRepository.buildChain(
            _selectedIndex.value,
            expiry,
            spot,
            _numStrikes.value
        ) ?: return

        val currentLtps = _ltpMap.value
        val updatedRows = result.chain.map { row ->
            row.copy(
                ceLtp = currentLtps[row.ce?.token ?: ""] ?: 0.0,
                peLtp = currentLtps[row.pe?.token ?: ""] ?: 0.0
            )
        }
        _chain.value = result.copy(chain = updatedRows)
    }

    private fun subscribeChainTokens() {
        val result = _chain.value ?: return
        val tokens = result.chain.flatMap { row ->
            buildList {
                row.ce?.let { ce ->
                    if (ce.token.isNotEmpty())
                        add(Triple(ce.segment, ce.token, ce.tradingSymbol))
                }
                row.pe?.let { pe ->
                    if (pe.token.isNotEmpty())
                        add(Triple(pe.segment, pe.token, pe.tradingSymbol))
                }
            }
        }
        if (tokens.isNotEmpty()) {
            kotakRepository.subscribeMarketTokens(tokens)
        }
    }

    private fun observeLtpTicks() {
        viewModelScope.launch {
            kotakRepository.getLtpEvents().collect { tick ->
                val updated = _ltpMap.value.toMutableMap()
                updated[tick.token] = tick.ltp
                _ltpMap.value = updated

                val currentChain = _chain.value ?: return@collect
                val updatedRows = currentChain.chain.map { row ->
                    var r = row
                    if (row.ce?.token == tick.token) r = r.copy(ceLtp = tick.ltp)
                    if (row.pe?.token == tick.token) r = r.copy(peLtp = tick.ltp)
                    r
                }
                _chain.value = currentChain.copy(chain = updatedRows)

                val sel = _selectedStrike.value
                if (sel != null) {
                    if (sel.ce?.token == tick.token) {
                        _selectedStrike.value = sel.copy(ceLtp = tick.ltp)
                    } else if (sel.pe?.token == tick.token) {
                        _selectedStrike.value = sel.copy(peLtp = tick.ltp)
                    }
                }
            }
        }
    }

    private fun observeHsiEvents() {
        viewModelScope.launch {
            kotakRepository.getHsiEvents().collect { event ->
                when (event) {
                    is UiEvent.OrderUpdate -> {
                        _uiEvents.emit(UiEvent.ShowToast("Order ${event.orderNo}: ${event.status}"))
                    }
                    else -> {}
                }
            }
        }
    }

    fun buyCall() {
        val strike = _selectedStrike.value ?: run {
            viewModelScope.launch { _uiEvents.emit(UiEvent.ShowToast("Select a strike first")) }
            return
        }
        val ce = strike.ce ?: return
        placeOrder(ce.segment, ce.tradingSymbol, "B", ce.token, ce.lotSize)
    }

    fun sellCall() {
        val strike = _selectedStrike.value ?: run {
            viewModelScope.launch { _uiEvents.emit(UiEvent.ShowToast("Select a strike first")) }
            return
        }
        val ce = strike.ce ?: return
        placeOrder(ce.segment, ce.tradingSymbol, "S", ce.token, ce.lotSize)
    }

    fun buyPut() {
        val strike = _selectedStrike.value ?: run {
            viewModelScope.launch { _uiEvents.emit(UiEvent.ShowToast("Select a strike first")) }
            return
        }
        val pe = strike.pe ?: return
        placeOrder(pe.segment, pe.tradingSymbol, "B", pe.token, pe.lotSize)
    }

    fun sellPut() {
        val strike = _selectedStrike.value ?: run {
            viewModelScope.launch { _uiEvents.emit(UiEvent.ShowToast("Select a strike first")) }
            return
        }
        val pe = strike.pe ?: return
        placeOrder(pe.segment, pe.tradingSymbol, "S", pe.token, pe.lotSize)
    }

    private fun placeOrder(seg: String, ts: String, tt: String, tok: String, lotSize: Int) {
        val qty = lotSize * _lotMultiplier.value
        val req = OrderRequest(es = seg, ts = ts, tt = tt, qty = qty, tok = tok, pc = "MIS")
        viewModelScope.launch {
            kotakRepository.placeOrder(req)
                .onSuccess { (ok, msg) ->
                    _uiEvents.emit(
                        UiEvent.OrderResult(
                            success = ok,
                            message = msg,
                            symbol = "$ts × $qty"
                        )
                    )
                }
                .onFailure { e ->
                    _uiEvents.emit(UiEvent.OrderResult(false, e.message ?: "Order failed", ts))
                }
        }
    }

    suspend fun closeAllPositions(): Result<Int> = kotakRepository.closeAllPositions()

    private fun refreshLimits() {
        viewModelScope.launch {
            while (true) {
                kotakRepository.getLimits()
                    .onSuccess { _limits.value = it }
                delay(30_000L)
            }
        }
    }
}
