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

private const val TAG = "HsiWebSocket"

@Singleton
class HsiWebSocketClient @Inject constructor(
    private val client: OkHttpClient
) {
    private var webSocket: WebSocket? = null
    private var scope: CoroutineScope? = null
    private var retryCount = 0

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()

    fun connect(session: KotakSession) {
        scope?.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        retryCount = 0
        connectInternal(session)
    }

    private fun connectInternal(session: KotakSession) {
        val wsUrl = session.baseUrl
            .replace("https://", "wss://")
            .replace("http://", "ws://") + "/realtime"

        val request = Request.Builder().url(wsUrl).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(TAG, "HSI connected: $wsUrl")
                retryCount = 0
                val authMsg = "{type:cn,Authorization:${session.sessionToken},Sid:${session.sessionSid},src:WEB}"
                ws.send(authMsg)
                startHeartbeat(ws)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "HSI failure: ${t.message}")
                scheduleReconnect(session)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "HSI closed: $code $reason")
            }
        })
    }

    private fun handleMessage(text: String) {
        try {
            val msg = JSONObject(text)
            when (msg.optString("type")) {
                "cn" -> Log.d(TAG, "HSI auth ack: ${msg.optString("msg")}")

                "order" -> {
                    val data = msg.optJSONObject("data") ?: return
                    val orderNo = data.optString("nOrdNo", "")
                    val status = data.optString("ordSt", "")
                    val symbol = data.optString("trdSym", data.optString("ts", ""))
                    scope?.launch {
                        _events.emit(UiEvent.OrderUpdate(orderNo, status, symbol))
                    }
                }

                "position" -> {
                    val data = msg.optJSONObject("data") ?: return
                    val symbol = data.optString("trdSym", data.optString("ts", ""))
                    val ltp = data.optDouble("ltp", data.optDouble("lp", 0.0))
                    if (symbol.isNotEmpty()) {
                        scope?.launch {
                            _events.emit(UiEvent.PositionUpdate(symbol, ltp))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "HSI parse error: ${e.message}")
        }
    }

    private fun startHeartbeat(ws: WebSocket) {
        scope?.launch {
            while (true) {
                delay(60_000L)
                ws.send("{type:hb}")
            }
        }
    }

    private fun scheduleReconnect(session: KotakSession) {
        retryCount++
        if (retryCount > 10) {
            Log.w(TAG, "HSI giving up after 10 retries")
            return
        }
        val delayMs = (retryCount * 5_000L).coerceAtMost(60_000L)
        scope?.launch {
            delay(delayMs)
            connectInternal(session)
        }
    }

    fun disconnect() {
        webSocket?.close(1000, "User logout")
        webSocket = null
        scope?.cancel()
        scope = null
        retryCount = 0
    }
}
