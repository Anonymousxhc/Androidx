package com.akatsuki.trading.repository

import com.akatsuki.trading.data.CredentialStore
import com.akatsuki.trading.data.SessionStore
import com.akatsuki.trading.data.model.KotakSession
import com.akatsuki.trading.data.model.Limits
import com.akatsuki.trading.data.model.Order
import com.akatsuki.trading.data.model.OrderRequest
import com.akatsuki.trading.data.model.Position
import com.akatsuki.trading.network.HsiWebSocketClient
import com.akatsuki.trading.network.KotakApiService
import com.akatsuki.trading.network.MarketFeedClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KotakRepository @Inject constructor(
    private val apiService: KotakApiService,
    private val credentialStore: CredentialStore,
    private val sessionStore: SessionStore,
    private val hsiClient: HsiWebSocketClient,
    private val marketFeedClient: MarketFeedClient
) {
    var currentSession: KotakSession? = null
        private set

    fun loadSession(): KotakSession? {
        if (sessionStore.isValid()) {
            val s = sessionStore.load()
            currentSession = s
            return s
        }
        return null
    }

    suspend fun loginTotp(totp: String): Result<Pair<String, String>> {
        val creds = credentialStore.load() ?: return Result.failure(Exception("No credentials stored"))
        return apiService.loginTotp(creds, totp)
    }

    suspend fun validateMpin(viewToken: String, viewSid: String): Result<KotakSession> {
        val creds = credentialStore.load() ?: return Result.failure(Exception("No credentials stored"))
        val result = apiService.validateMpin(creds, viewToken, viewSid)
        result.onSuccess { session ->
            currentSession = session
            sessionStore.save(session)
            hsiClient.connect(session)
            marketFeedClient.connect(session)
        }
        return result
    }

    suspend fun getSpot(index: String): Result<Double> {
        val session = currentSession ?: return Result.failure(Exception("Not connected"))
        val creds = credentialStore.load() ?: return Result.failure(Exception("No credentials"))
        val spotInfo = mapOf(
            "NIFTY" to Pair("nse_cm", "Nifty 50"),
            "BANKNIFTY" to Pair("nse_cm", "Nifty Bank"),
            "SENSEX" to Pair("bse_cm", "SENSEX"),
            "FINNIFTY" to Pair("nse_cm", "Nifty Fin Service"),
            "MIDCPNIFTY" to Pair("nse_cm", "Nifty MidCap Select"),
            "BANKEX" to Pair("bse_cm", "BANKEX")
        )
        val (seg, sym) = spotInfo[index.uppercase()] ?: Pair("nse_cm", "Nifty 50")
        return apiService.fetchSpot(session, creds.accessToken, seg, sym)
    }

    suspend fun placeOrder(req: OrderRequest): Result<Pair<Boolean, String>> {
        val session = currentSession ?: return Result.failure(Exception("Not connected"))
        val creds = credentialStore.load() ?: return Result.failure(Exception("No credentials"))
        return apiService.placeOrderWithLtpFallback(session, creds.accessToken, req)
    }

    suspend fun cancelOrder(orderNo: String): Result<Boolean> {
        val session = currentSession ?: return Result.failure(Exception("Not connected"))
        return apiService.cancelOrder(session, orderNo)
    }

    suspend fun getPositions(): Result<List<Position>> {
        val session = currentSession ?: return Result.failure(Exception("Not connected"))
        return apiService.getPositions(session)
    }

    suspend fun getOrders(): Result<List<Order>> {
        val session = currentSession ?: return Result.failure(Exception("Not connected"))
        return apiService.getOrders(session)
    }

    suspend fun getLimits(): Result<Limits> {
        val session = currentSession ?: return Result.failure(Exception("Not connected"))
        return apiService.getLimits(session)
    }

    suspend fun closeAllPositions(): Result<Int> {
        val session = currentSession ?: return Result.failure(Exception("Not connected"))
        return apiService.closeAllPositions(session)
    }

    fun logout() {
        currentSession = null
        sessionStore.clear()
        hsiClient.disconnect()
        marketFeedClient.disconnect()
    }

    fun isConnected() = currentSession != null && sessionStore.isValid()

    fun getHsiEvents() = hsiClient.events
    fun getLtpEvents() = marketFeedClient.ltpEvents

    fun subscribeMarketTokens(tokens: List<Triple<String, String, String>>) {
        marketFeedClient.subscribeForIndex(tokens)
    }
}
