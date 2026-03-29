package com.akatsuki.trading.repository

import com.akatsuki.trading.data.model.ExpiryEntry
import com.akatsuki.trading.data.model.OptionChainResult
import com.akatsuki.trading.network.InstrumentCsvParser
import com.akatsuki.trading.network.KotakApiService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InstrumentsRepository @Inject constructor(
    private val csvParser: InstrumentCsvParser,
    private val apiService: KotakApiService,
    private val kotakRepository: KotakRepository
) {
    private val supportedIndices = listOf("NIFTY", "BANKNIFTY", "SENSEX", "FINNIFTY", "MIDCPNIFTY", "BANKEX")

    suspend fun preloadInstruments(): Result<Unit> = runCatching {
        val session = kotakRepository.currentSession ?: error("Not connected")
        val credStore = injectedCredentialStore ?: error("Credential store not available")
        val accessToken = credStore.load()?.accessToken ?: error("No credentials")

        val paths = apiService.fetchScripPaths(session, accessToken).getOrThrow()
        check(paths.isNotEmpty()) { "No scrip file paths returned from API" }

        val nseFoUrl = paths.firstOrNull { it.contains("nse_fo") }
        val bseFoUrl = paths.firstOrNull { it.contains("bse_fo") }

        if (nseFoUrl != null) {
            csvParser.downloadAndParse(
                nseFoUrl,
                listOf("NIFTY", "BANKNIFTY", "FINNIFTY", "MIDCPNIFTY")
            ).getOrThrow()
        }

        if (bseFoUrl != null) {
            csvParser.downloadAndParse(
                bseFoUrl,
                listOf("SENSEX", "BANKEX")
            ).getOrThrow()
        }
    }

    var injectedCredentialStore: com.akatsuki.trading.data.CredentialStore? = null

    fun getExpiries(index: String): List<ExpiryEntry> = csvParser.getExpiries(index)

    fun buildChain(
        index: String,
        expiry: String,
        spot: Double,
        numStrikes: Int
    ): OptionChainResult? = csvParser.buildChain(index, expiry, spot, numStrikes)

    fun isLoaded(index: String) = csvParser.isLoaded(index)
    fun supportedIndices() = supportedIndices
}
