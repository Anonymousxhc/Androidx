package com.akatsuki.trading.network

import android.util.Log
import com.akatsuki.trading.data.model.KotakSession
import com.akatsuki.trading.data.model.UiEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MarketFeed"
private const val HSM_URL = "wss://mlhsm.kotaksecurities.com"

@Singleton
class MarketFeedClient @Inject constructor(
    private val client: OkHttpClient
) {
    private var webSocket: WebSocket? = null
    private var scope: CoroutineScope? = null
    private var session: KotakSession? = null
    private var retryCount = 0
    private var isConnected = false

    private val _ltpEvents = MutableSharedFlow<UiEvent.LtpTick>(extraBufferCapacity = 256)
    val ltpEvents = _ltpEvents.asSharedFlow()

    private val tokenToSymbol = mutableMapOf<String, String>()
    private val subscribedSegmentTokens = mutableListOf<Pair<String, String>>()

    fun connect(session: KotakSession) {
        this.session = session
        scope?.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        retryCount = 0
        connectInternal(session)
    }

    private fun connectInternal(session: KotakSession) {
        val request = Request.Builder().url(HSM_URL).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(TAG, "Market feed connected")
                retryCount = 0
                isConnected = true

                val connectMsg = JSONObject().apply {
                    put("Authorization", session.sessionToken)
                    put("Sid", session.sessionSid)
                    put("type", "cn")
                    if (session.dataCenter.isNotEmpty()) {
                        put("dataCenter", session.dataCenter)
                    }
                }.toString()
                ws.send(connectMsg)

                startHeartbeat(ws)

                if (subscribedSegmentTokens.isNotEmpty()) {
                    scope?.launch {
                        delay(500L)
                        resubscribeAll(ws)
                    }
                }
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleTick(text)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "Market feed failure: ${t.message}")
                isConnected = false
                scheduleReconnect()
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Market feed closed: $reason")
                isConnected = false
            }
        })
    }

    fun subscribeForIndex(tokens: List<Triple<String, String, String>>) {
        tokenToSymbol.clear()
        subscribedSegmentTokens.clear()
        tokens.forEach { (seg, token, symbol) ->
            if (token.isNotEmpty()) {
                tokenToSymbol[token] = symbol
                subscribedSegmentTokens.add(Pair(seg, token))
            }
        }

        val ws = webSocket ?: return
        if (!isConnected) return

        val scripsStr = tokens
            .filter { it.second.isNotEmpty() }
            .joinToString("&") { "${it.first}|${it.second}" }
        if (scripsStr.isEmpty()) return

        val msg = JSONObject().apply {
            put("type", "mws")
            put("scrips", scripsStr)
            put("channelnum", 1)
        }.toString()
        ws.send(msg)
        Log.d(TAG, "Subscribed ${tokens.size} tokens")
    }

    private fun resubscribeAll(ws: WebSocket) {
        if (subscribedSegmentTokens.isEmpty()) return
        val scripsStr = subscribedSegmentTokens.joinToString("&") { (seg, token) -> "$seg|$token" }
        val msg = JSONObject().apply {
            put("type", "mws")
            put("scrips", scripsStr)
            put("channelnum", 1)
        }.toString()
        ws.send(msg)
        Log.d(TAG, "Resubscribed ${subscribedSegmentTokens.size} tokens")
    }

    private fun handleTick(text: String) {
        try {
            val tick = JSONObject(text)
            val ltp = tick.optDouble("ltp", tick.optDouble("LTP", Double.NaN))
            val token = tick.optString("tk", "")
            if (!ltp.isNaN() && token.isNotEmpty()) {
                val symbol = tokenToSymbol[token] ?: return
                scope?.launch {
                    _ltpEvents.emit(UiEvent.LtpTick(token, ltp))
                }
            }
        } catch (_: Exception) {}
    }

    private fun startHeartbeat(ws: WebSocket) {
        scope?.launch {
            val hbMsg = JSONObject().apply {
                put("type", "ti")
                put("scrips", "")
            }.toString()
            while (true) {
                delay(30_000L)
                try { ws.send(hbMsg) } catch (_: Exception) { break }
            }
        }
    }

    private fun scheduleReconnect() {
        val s = session ?: return
        retryCount++
        if (retryCount > 10) return
        val delayMs = (retryCount * 5_000L).coerceAtMost(60_000L)
        scope?.launch {
            delay(delayMs)
            connectInternal(s)
        }
    }

    fun disconnect() {
        webSocket?.close(1000, "User logout")
        webSocket = null
        isConnected = false
        tokenToSymbol.clear()
        subscribedSegmentTokens.clear()
        scope?.cancel()
        scope = null
        session = null
    }
}
