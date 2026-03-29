package com.akatsuki.trading.data.model

import kotlinx.serialization.Serializable

@Serializable
data class KotakCredentials(
    val accessToken: String,
    val mobileNumber: String,
    val mpin: String,
    val ucc: String
)

data class KotakSession(
    val sessionToken: String,
    val sessionSid: String,
    val baseUrl: String,
    val dataCenter: String,
    val orderUrl: String,
    val greetingName: String,
    val loginTime: Long = System.currentTimeMillis()
)

data class OptionInfo(
    val tradingSymbol: String,
    val token: String,
    val segment: String,
    val lotSize: Int
)

data class StrikeRow(
    val strike: Double,
    val isAtm: Boolean,
    val ce: OptionInfo?,
    val pe: OptionInfo?,
    val ceLtp: Double = 0.0,
    val peLtp: Double = 0.0
)

data class OptionChainResult(
    val atmStrike: Double,
    val spotPrice: Double,
    val chain: List<StrikeRow>,
    val index: String,
    val expiry: String,
    val lotSize: Int,
    val step: Double
)

data class ExpiryEntry(
    val label: String,
    val isNearest: Boolean
)

data class Position(
    val tradingSymbol: String,
    val netQty: Int,
    val buyQty: Int,
    val sellQty: Int,
    val buyAmt: Double,
    val sellAmt: Double,
    val pnl: Double,
    val ltp: Double,
    val segment: String,
    val productCode: String,
    val token: String = ""
)

data class Order(
    val orderNo: String,
    val tradingSymbol: String,
    val qty: Int,
    val side: String,
    val status: String,
    val time: String,
    val price: Double,
    val orderType: String,
    val productCode: String
)

data class Limits(
    val availableCash: Double,
    val usedMargin: Double,
    val totalBalance: Double
)

sealed class AuthState {
    object NeedsCredentials : AuthState()
    object NeedsConnect : AuthState()
    data class Connected(val session: KotakSession) : AuthState()
    data class Error(val message: String) : AuthState()
    object Loading : AuthState()
}

sealed class UiEvent {
    data class OrderResult(val success: Boolean, val message: String, val symbol: String) : UiEvent()
    data class OrderUpdate(val orderNo: String, val status: String, val symbol: String) : UiEvent()
    data class PositionUpdate(val tradingSymbol: String, val ltp: Double) : UiEvent()
    data class ShowToast(val message: String) : UiEvent()
    data class LtpTick(val token: String, val ltp: Double) : UiEvent()
}

data class OrderRequest(
    val es: String,
    val ts: String,
    val tt: String,
    val qty: Int,
    val tok: String = "",
    val pc: String = "MIS",
    val pt: String = "MKT",
    val pr: String = "0",
    val tp: String = "0"
)
