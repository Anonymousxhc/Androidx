package com.akatsuki.trading.network

import android.content.Context
import android.util.Log
import com.akatsuki.trading.data.model.ExpiryEntry
import com.akatsuki.trading.data.model.OptionInfo
import com.akatsuki.trading.data.model.OptionChainResult
import com.akatsuki.trading.data.model.StrikeRow
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TreeMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

private const val TAG = "InstrumentCsv"

data class OptionRecord(
    val tradingSymbol: String,
    val token: String,
    val segment: String,
    val optionType: String,
    val lotSize: Int,
    val strike: Double,
    val expiry: Date
)

@Singleton
class InstrumentCsvParser @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient
) {
    private val db = mutableMapOf<String, MutableMap<String, TreeMap<Double, MutableMap<String, OptionRecord>>>>()
    private val expiryLists = mutableMapOf<String, List<ExpiryEntry>>()

    private val expiryFmt = SimpleDateFormat("ddMMMyy", Locale.ENGLISH)
    private val labelFmt = SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH).apply {
        isLenient = false
    }

    private val spotSymbols = mapOf(
        "NIFTY" to Pair("nse_cm", "Nifty 50"),
        "BANKNIFTY" to Pair("nse_cm", "Nifty Bank"),
        "SENSEX" to Pair("bse_cm", "SENSEX"),
        "FINNIFTY" to Pair("nse_cm", "Nifty Fin Service"),
        "MIDCPNIFTY" to Pair("nse_cm", "Nifty MidCap Select"),
        "BANKEX" to Pair("bse_cm", "BANKEX")
    )

    private val stepMap = mapOf(
        "NIFTY" to 50.0,
        "BANKNIFTY" to 100.0,
        "SENSEX" to 100.0,
        "FINNIFTY" to 50.0,
        "MIDCPNIFTY" to 25.0,
        "BANKEX" to 100.0
    )

    fun getSpotInfo(index: String): Pair<String, String>? = spotSymbols[index.uppercase()]

    suspend fun downloadAndParse(
        csvUrl: String,
        indices: List<String>
    ): Result<Unit> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching {
        val csvKey = if (csvUrl.contains("bse_fo")) "bse_fo" else "nse_fo"
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val file = File(context.filesDir, "${csvKey}_${today}.csv")

        if (!file.exists() || file.length() < 1000) {
            Log.d(TAG, "Downloading CSV from $csvUrl")
            val req = Request.Builder().url(csvUrl).build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: error("Empty CSV response")
            if (body.length < 100 || body.trimStart().startsWith("<?xml")) {
                error("Invalid CSV data received")
            }
            file.writeText(body)
            Log.d(TAG, "CSV saved: ${file.length()} bytes")

            context.filesDir.listFiles()
                ?.filter { it.name.startsWith(csvKey) && !it.name.contains(today) }
                ?.forEach { it.delete() }
        } else {
            Log.d(TAG, "CSV cache hit: ${file.name} (${file.length()} bytes)")
        }

        indices.forEach { index ->
            val key = index.uppercase()
            val csvKey2 = if (key == "SENSEX" || key == "BANKEX") "bse_fo" else "nse_fo"
            if (csvUrl.contains(csvKey2)) {
                parseCsvForIndex(file, key)
            }
        }
    } }

    private fun parseCsvForIndex(file: File, indexName: String) {
        val t0 = System.currentTimeMillis()
        val lines = file.bufferedReader().readLines()
        if (lines.isEmpty()) return

        val header = lines[0].split(",").map { it.trim().replace(";", "") }
        val colIdx = header.withIndex().associate { (i, col) -> col to i }

        fun getCol(row: List<String>, name: String): String {
            val i = colIdx[name] ?: return ""
            return if (i < row.size) row[i].trim() else ""
        }

        val today = Date()
        val byExpiryAndStrike = mutableMapOf<String, TreeMap<Double, MutableMap<String, OptionRecord>>>()
        val expiryDates = mutableMapOf<String, Date>()

        for (line in lines.drop(1)) {
            if (line.isBlank()) continue
            val row = line.split(",")

            val symName = getCol(row, "pSymbolName").uppercase()
            if (symName != indexName) continue

            val optType = getCol(row, "pOptionType").trim()
            if (optType != "CE" && optType != "PE") continue

            if (indexName == "NIFTY" || indexName == "BANKNIFTY" || indexName == "FINNIFTY") {
                val instType = getCol(row, "pInstType").uppercase().trim()
                if (instType != "OPTIDX") continue
            }

            val ts = getCol(row, "pTrdSymbol").uppercase()
            val token = getCol(row, "pSymbol")
            val seg = getCol(row, "pExchSeg")
            val lotStr = getCol(row, "lLotSize")
            val lot = lotStr.toIntOrNull()?.takeIf { it > 0 } ?: 1

            val strikePriceRaw = getCol(row, "dStrikePrice").toDoubleOrNull() ?: continue
            val strike = strikePriceRaw / 100.0
            if (strike <= 0) continue

            val scripRef = getCol(row, "pScripRefKey").uppercase()
            val prefix = indexName.uppercase()
            if (!scripRef.startsWith(prefix) || scripRef.length <= prefix.length + 6) continue

            val datePart = scripRef.substring(prefix.length, prefix.length + 7)
            val expiryDate = try {
                expiryFmt.parse(datePart.lowercase().replaceFirstChar { it.uppercaseChar() }
                    .replace(Regex("([a-z])([A-Z])")) { "${it.groupValues[1]}${it.groupValues[2]}" }
                ) ?: continue
            } catch (_: Exception) {
                continue
            }

            if (expiryDate.before(today)) continue
            val expiryYear = expiryDate.year + 1900
            if (expiryYear < 2025 || expiryYear > 2030) continue

            val label = labelFmt.format(expiryDate).uppercase()

            byExpiryAndStrike.getOrPut(label) { TreeMap() }
                .getOrPut(strike) { mutableMapOf() }[optType] = OptionRecord(
                tradingSymbol = ts,
                token = token,
                segment = seg,
                optionType = optType,
                lotSize = lot,
                strike = strike,
                expiry = expiryDate
            )
            expiryDates[label] = expiryDate
        }

        val sortedExpiries = expiryDates.entries
            .sortedBy { it.value }
            .mapIndexed { i, (label, _) -> ExpiryEntry(label, i == 0) }

        db[indexName] = byExpiryAndStrike
        expiryLists[indexName] = sortedExpiries

        val elapsed = System.currentTimeMillis() - t0
        Log.d(TAG, "$indexName parsed: ${sortedExpiries.size} expiries in ${elapsed}ms")
    }

    fun getExpiries(index: String): List<ExpiryEntry> =
        expiryLists[index.uppercase()] ?: emptyList()

    fun buildChain(
        index: String,
        expiry: String,
        spot: Double,
        numStrikes: Int
    ): OptionChainResult? {
        val key = index.uppercase()
        val indexDb = db[key] ?: return null
        val expDb = indexDb[expiry] ?: return null

        val step = stepMap[key] ?: 50.0
        val allStrikes = expDb.keys.toList()
        if (allStrikes.isEmpty()) return null

        val atm = allStrikes.minByOrNull { abs(it - spot) } ?: allStrikes[0]
        val atmIdx = allStrikes.indexOf(atm)

        val start = (atmIdx - numStrikes).coerceAtLeast(0)
        val end = (atmIdx + numStrikes + 1).coerceAtMost(allStrikes.size)
        val selected = allStrikes.subList(start, end)

        var lotSize = 1
        val rows = selected.map { strike ->
            val strikeData = expDb[strike] ?: emptyMap()
            val ceRecord = strikeData["CE"]
            val peRecord = strikeData["PE"]
            if (lotSize == 1 && ceRecord != null) lotSize = ceRecord.lotSize

            StrikeRow(
                strike = strike,
                isAtm = abs(strike - atm) < step / 2,
                ce = ceRecord?.toOptionInfo(),
                pe = peRecord?.toOptionInfo()
            )
        }

        return OptionChainResult(
            atmStrike = atm,
            spotPrice = spot,
            chain = rows,
            index = key,
            expiry = expiry,
            lotSize = lotSize,
            step = step
        )
    }

    fun isLoaded(index: String) = db.containsKey(index.uppercase())

    private fun OptionRecord.toOptionInfo() = OptionInfo(
        tradingSymbol = tradingSymbol,
        token = token,
        segment = segment,
        lotSize = lotSize
    )
}
