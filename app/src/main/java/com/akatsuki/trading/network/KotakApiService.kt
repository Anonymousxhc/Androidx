package com.akatsuki.trading.network

import com.akatsuki.trading.data.model.KotakCredentials
import com.akatsuki.trading.data.model.KotakSession
import com.akatsuki.trading.data.model.Limits
import com.akatsuki.trading.data.model.Order
import com.akatsuki.trading.data.model.OrderRequest
import com.akatsuki.trading.data.model.Position
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private val JSON_MEDIA = "application/json".toMediaType()

private const val LOGIN_URL = "https://mis.kotaksecurities.com/login/1.0/tradeApiLogin"
private const val VALIDATE_URL = "https://mis.kotaksecurities.com/login/1.0/tradeApiValidate"

@Singleton
class KotakApiService @Inject constructor(
    private val client: OkHttpClient
) {
    suspend fun loginTotp(
        credentials: KotakCredentials,
        totp: String
    ): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().apply {
                put("mobileNumber", credentials.mobileNumber)
                put("ucc", credentials.ucc)
                put("totp", totp)
            }.toString()

            val req = Request.Builder()
                .url(LOGIN_URL)
                .post(body.toRequestBody())
                .header("Authorization", credentials.accessToken)
                .header("neo-fin-key", "neotradeapi")
                .header("Content-Type", "application/json")
                .build()

            val resp = client.newCall(req).execute()
            val bodyStr = resp.body?.string() ?: error("Empty response")
            val root = JSONObject(bodyStr)
            val data = root.optJSONObject("data") ?: error("No data field")
            check(data.optString("status") == "success") {
                root.optString("message").ifEmpty { "TOTP login failed" }
            }
            val viewToken = data.getString("token")
            val viewSid = data.getString("sid")
            Pair(viewToken, viewSid)
        }
    }

    suspend fun validateMpin(
        credentials: KotakCredentials,
        viewToken: String,
        viewSid: String
    ): Result<KotakSession> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().apply {
                put("mpin", credentials.mpin)
            }.toString()

            val req = Request.Builder()
                .url(VALIDATE_URL)
                .post(body.toRequestBody())
                .header("Authorization", credentials.accessToken)
                .header("neo-fin-key", "neotradeapi")
                .header("Content-Type", "application/json")
                .header("sid", viewSid)
                .header("Auth", viewToken)
                .build()

            val resp = client.newCall(req).execute()
            val bodyStr = resp.body?.string() ?: error("Empty response")
            val root = JSONObject(bodyStr)
            val data = root.optJSONObject("data") ?: error("No data field: $bodyStr")
            check(data.optString("status") == "success") {
                root.optString("message").ifEmpty { data.optString("message").ifEmpty { "MPIN validation failed" } }
            }

            val sessionToken = data.getString("token")
            val sessionSid = data.getString("sid")
            val baseUrl = data.getString("baseUrl")
            val dataCenter = data.optString("dataCenter", "")
            val greetingName = data.optString("greetingName", "")

            KotakSession(
                sessionToken = sessionToken,
                sessionSid = sessionSid,
                baseUrl = baseUrl,
                dataCenter = dataCenter,
                orderUrl = "$baseUrl/quick/order/rule/ms/place",
                greetingName = greetingName
            )
        }
    }

    suspend fun fetchScripPaths(session: KotakSession, accessToken: String): Result<List<String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "${session.baseUrl}/script-details/1.0/masterscrip/file-paths"
                val req = Request.Builder()
                    .url(url)
                    .get()
                    .header("Authorization", accessToken)
                    .header("Content-Type", "application/json")
                    .build()

                val resp = client.newCall(req).execute()
                val bodyStr = resp.body?.string() ?: error("Empty response")
                val root = JSONObject(bodyStr)
                val data = root.optJSONObject("data") ?: error("No data")
                val paths = data.optJSONArray("filesPaths") ?: return@runCatching emptyList()
                (0 until paths.length()).mapNotNull { paths.optString(it).ifEmpty { null } }
            }
        }

    suspend fun fetchLtp(
        session: KotakSession,
        accessToken: String,
        seg: String,
        sym: String
    ): Result<Double> = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = sym.replace(" ", "%20")
            val url = "${session.baseUrl}/script-details/1.0/quotes/neosymbol/$seg|$encoded/ltp"
            val req = Request.Builder()
                .url(url)
                .get()
                .header("Authorization", accessToken)
                .header("Content-Type", "application/json")
                .build()

            val resp = client.newCall(req).execute()
            val bodyStr = resp.body?.string() ?: error("Empty response")
            val root = when (val p = parseFlexibleJson(bodyStr)) {
                is JSONObject -> p
                is JSONArray -> if (p.length() > 0) p.getJSONObject(0) else JSONObject()
                else -> JSONObject()
            }
            root.optDouble("ltp", 0.0)
        }
    }

    suspend fun fetchSpot(
        session: KotakSession,
        accessToken: String,
        seg: String,
        sym: String
    ): Result<Double> = fetchLtp(session, accessToken, seg, sym)

    suspend fun placeOrder(
        session: KotakSession,
        req: OrderRequest
    ): Result<Pair<Boolean, String>> = withContext(Dispatchers.IO) {
        runCatching {
            val jData = buildJData(req)
            val formBody = FormBody.Builder()
                .add("jData", jData)
                .build()

            val httpReq = Request.Builder()
                .url(session.orderUrl)
                .post(formBody)
                .applySessionHeaders(session)
                .build()

            val resp = client.newCall(httpReq).execute()
            val bodyStr = resp.body?.string() ?: error("Empty response")
            val result = JSONObject(bodyStr)
            val stat = result.optString("stat").lowercase()
            val isOk = stat == "ok"
            val msg = if (isOk) {
                result.optString("nOrdNo").ifEmpty { "Order placed" }
            } else {
                result.optString("emsg").ifEmpty { result.optString("errMsg").ifEmpty { bodyStr } }
            }
            Pair(isOk, msg)
        }
    }

    suspend fun placeOrderWithLtpFallback(
        session: KotakSession,
        accessToken: String,
        req: OrderRequest
    ): Result<Pair<Boolean, String>> = withContext(Dispatchers.IO) {
        val result = placeOrder(session, req)
        val (ok, msg) = result.getOrElse { return@withContext Result.failure(it) }
        if (ok) return@withContext Result.success(Pair(true, msg))

        if ("LTP" in msg.uppercase()) {
            val lookupSym = req.tok.ifEmpty { req.ts }
            val ltpResult = fetchLtp(session, accessToken, req.es, lookupSym)
            val ltp = ltpResult.getOrNull() ?: 0.0
            if (ltp > 0) {
                val mult = if (req.tt == "S") 0.998 else 1.002
                val limitPrice = "%.2f".format(ltp * mult)
                val retryReq = req.copy(pt = "L", pr = limitPrice)
                return@withContext placeOrder(session, retryReq)
            }
        }
        Result.success(Pair(false, msg))
    }

    suspend fun cancelOrder(session: KotakSession, orderNo: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            runCatching {
                val jData = """{"on":"$orderNo","am":"NO"}"""
                val formBody = FormBody.Builder().add("jData", jData).build()
                val req = Request.Builder()
                    .url("${session.baseUrl}/quick/order/cancel")
                    .post(formBody)
                    .applySessionHeaders(session)
                    .build()
                val resp = client.newCall(req).execute()
                val bodyStr = resp.body?.string() ?: ""
                val result = JSONObject(bodyStr)
                result.optString("stat").lowercase() == "ok"
            }
        }

    suspend fun getPositions(session: KotakSession): Result<List<Position>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder()
                    .url("${session.baseUrl}/quick/user/positions")
                    .get()
                    .applyGetHeaders(session)
                    .build()
                val resp = client.newCall(req).execute()
                val bodyStr = resp.body?.string() ?: error("Empty response")
                val root = JSONObject(bodyStr)
                val data = root.optJSONArray("data") ?: return@runCatching emptyList()
                parsePositions(data)
            }
        }

    suspend fun getOrders(session: KotakSession): Result<List<Order>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder()
                    .url("${session.baseUrl}/quick/user/orders")
                    .get()
                    .applyGetHeaders(session)
                    .build()
                val resp = client.newCall(req).execute()
                val bodyStr = resp.body?.string() ?: error("Empty")
                val root = JSONObject(bodyStr)
                val data = root.optJSONArray("data") ?: return@runCatching emptyList()
                parseOrders(data)
            }
        }

    suspend fun getLimits(session: KotakSession): Result<Limits> = withContext(Dispatchers.IO) {
        runCatching {
            val jData = """{"seg":"ALL","exch":"ALL","prod":"ALL"}"""
            val formBody = FormBody.Builder().add("jData", jData).build()
            val req = Request.Builder()
                .url("${session.baseUrl}/quick/user/limits")
                .post(formBody)
                .applySessionHeaders(session)
                .build()
            val resp = client.newCall(req).execute()
            val bodyStr = resp.body?.string() ?: error("Empty")
            val root = JSONObject(bodyStr)
            val data = root.optJSONObject("data") ?: return@runCatching Limits(0.0, 0.0, 0.0)
            Limits(
                availableCash = data.optDouble("cash", 0.0),
                usedMargin = data.optDouble("marginUsed", 0.0),
                totalBalance = data.optDouble("net", 0.0)
            )
        }
    }

    suspend fun closeAllPositions(session: KotakSession): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                val positions = getPositions(session).getOrElse { return@runCatching 0 }
                var closed = 0
                positions.filter { it.netQty != 0 }.forEach { pos ->
                    val tt = if (pos.netQty > 0) "S" else "B"
                    val qty = kotlin.math.abs(pos.netQty)
                    val req = OrderRequest(
                        es = pos.segment,
                        ts = pos.tradingSymbol,
                        tt = tt,
                        qty = qty,
                        pc = pos.productCode.ifEmpty { "MIS" }
                    )
                    val result = placeOrder(session, req)
                    if (result.getOrNull()?.first == true) closed++
                }
                closed
            }
        }

    private fun buildJData(req: OrderRequest): String {
        return buildString {
            append("{")
            append("\"am\":\"NO\",")
            append("\"dq\":\"0\",")
            append("\"es\":\"${req.es}\",")
            append("\"mp\":\"0\",")
            append("\"pc\":\"${req.pc}\",")
            append("\"pf\":\"N\",")
            append("\"pr\":\"${req.pr}\",")
            append("\"pt\":\"${req.pt}\",")
            append("\"qt\":\"${req.qty}\",")
            append("\"rt\":\"DAY\",")
            append("\"tp\":\"${req.tp}\",")
            append("\"ts\":\"${req.ts}\",")
            append("\"tt\":\"${req.tt}\"")
            append("}")
        }
    }

    private fun parsePositions(data: JSONArray): List<Position> {
        val list = mutableListOf<Position>()
        for (i in 0 until data.length()) {
            val p = data.optJSONObject(i) ?: continue
            val bq = p.optDouble("flBuyQty", p.optDouble("cfBuyQty", p.optDouble("buyQty", 0.0))).toInt()
            val sq = p.optDouble("flSellQty", p.optDouble("cfSellQty", p.optDouble("sellQty", 0.0))).toInt()
            var netQty = bq - sq
            val netQtyStr = p.optString("netQty", "")
            if (netQtyStr.isNotEmpty()) netQty = netQtyStr.toIntOrNull() ?: netQty

            val ba = p.optDouble("buyAmt", p.optDouble("cfBuyAmt", 0.0))
            val sa = p.optDouble("sellAmt", p.optDouble("cfSellAmt", 0.0))
            val ltp = p.optDouble("lp", p.optDouble("ltp", p.optDouble("lastPrice", 0.0)))
            val ts = p.optString("trdSym", p.optString("ts", ""))
            val seg = p.optString("seg", p.optString("exSeg", "nse_fo"))
            val pc = p.optString("prod", p.optString("pc", "MIS"))
            val tok = p.optString("tok", p.optString("token", ""))

            list.add(
                Position(
                    tradingSymbol = ts,
                    netQty = netQty,
                    buyQty = bq,
                    sellQty = sq,
                    buyAmt = ba,
                    sellAmt = sa,
                    pnl = sa - ba,
                    ltp = ltp,
                    segment = seg,
                    productCode = pc,
                    token = tok
                )
            )
        }
        return list
    }

    private fun parseOrders(data: JSONArray): List<Order> {
        val list = mutableListOf<Order>()
        for (i in 0 until data.length()) {
            val o = data.optJSONObject(i) ?: continue
            list.add(
                Order(
                    orderNo = o.optString("nOrdNo", o.optString("orderNo", "")),
                    tradingSymbol = o.optString("trdSym", o.optString("ts", "")),
                    qty = o.optInt("qty", 0),
                    side = o.optString("tt", o.optString("buyOrSell", "B")),
                    status = o.optString("ordSt", o.optString("status", "")),
                    time = o.optString("ordTm", o.optString("time", "")),
                    price = o.optDouble("prc", o.optDouble("pr", 0.0)),
                    orderType = o.optString("ordTyp", o.optString("pt", "MKT")),
                    productCode = o.optString("prod", o.optString("pc", "MIS"))
                )
            )
        }
        return list
    }

    private fun parseFlexibleJson(s: String): Any? {
        return try {
            JSONObject(s)
        } catch (_: Exception) {
            try { JSONArray(s) } catch (_: Exception) { null }
        }
    }

    private fun String.toRequestBody() = toRequestBody(JSON_MEDIA)

    private fun Request.Builder.applySessionHeaders(session: KotakSession) = this
        .header("accept", "application/json")
        .header("Auth", session.sessionToken)
        .header("Sid", session.sessionSid)
        .header("neo-fin-key", "neotradeapi")
        .header("Content-Type", "application/x-www-form-urlencoded")

    private fun Request.Builder.applyGetHeaders(session: KotakSession) = this
        .header("accept", "application/json")
        .header("Auth", session.sessionToken)
        .header("Sid", session.sessionSid)
        .header("neo-fin-key", "neotradeapi")
}
