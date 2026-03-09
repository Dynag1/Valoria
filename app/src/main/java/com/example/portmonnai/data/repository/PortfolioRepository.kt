package com.example.portmonnai.data.repository

import android.util.Log
import com.example.portmonnai.data.local.AssetEntity
import com.example.portmonnai.data.local.AppDatabase
import com.example.portmonnai.data.local.PortfolioDao
import com.example.portmonnai.data.local.TransactionEntity
import com.example.portmonnai.data.remote.CoinGeckoApi
import com.example.portmonnai.data.remote.YahooFinanceApi
import com.example.portmonnai.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import androidx.room.withTransaction

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Singleton
class PortfolioRepository @Inject constructor(
    private val database: AppDatabase,
    private val coinGeckoApi: CoinGeckoApi,
    private val yahooFinanceApi: YahooFinanceApi
) {
    private val portfolioDao = database.portfolioDao()
    private val syncMutex = Mutex()
    private val priceMutex = Mutex()
    private val chartMutex = Mutex()
    private val refreshFlow = MutableStateFlow(0)

    private val cachedPrices = mutableMapOf<String, PriceData>()
    private var lastFetchTime: Long = 0
    private val CACHE_DURATION = 5 * 60 * 1000L

    private val chartCache = mutableMapOf<String, Pair<Long, List<Pair<Long, Double>>>>()

    fun requestRefresh() {
        refreshFlow.update { it + 1 }
    }

    fun getPortfolioAssets(): Flow<List<PortfolioAsset>> {
        return combine(
            portfolioDao.getAllAssets(),
            portfolioDao.getAllTransactions(),
            refreshFlow
        ) { assets, transactions, refreshCount ->
            Triple(assets, transactions, refreshCount)
        }
        .debounce(300)
        .mapLatest { (assets, transactions, refreshCount) ->
            val prices = fetchCurrentPrices(force = refreshCount > 0)
            buildPortfolioList(assets, transactions, prices)
        }
    }

    suspend fun getPortfolioAssetsOnce(): List<PortfolioAsset> {
        return getPortfolioAssets().first()
    }

    private fun buildPortfolioList(
        assets: List<AssetEntity>,
        transactions: List<TransactionEntity>,
        prices: Map<String, PriceData>
    ): List<PortfolioAsset> {
        return assets.map { entity ->
            val assetTransactions = transactions.filter { it.assetId == entity.id }
            var currentQty = 0.0
            var totalCostBasis = 0.0
            var realizedProfit = 0.0

            for (tx in assetTransactions.sortedBy { it.date }) {
                if (tx.type == TransactionType.BUY) {
                    currentQty += tx.quantity
                    totalCostBasis += (tx.quantity * tx.priceAtDate) + tx.fees
                } else if (tx.type == TransactionType.SELL) {
                    val currentPru = if (currentQty > 0) totalCostBasis / currentQty else 0.0
                    val costOfSold = tx.quantity * currentPru
                    totalCostBasis -= costOfSold
                    if (totalCostBasis < 0) totalCostBasis = 0.0
                    currentQty -= tx.quantity
                    val revenue = (tx.quantity * tx.priceAtDate) - tx.fees
                    realizedProfit += (revenue - costOfSold)
                }
            }

            val avgPrice = if (currentQty > 0) totalCostBasis / currentQty else 0.0
            val priceData = prices[entity.id]
            val currentPrice = priceData?.price ?: 0.0
            val totalValue = currentQty * currentPrice
            val totalProfit = realizedProfit + (totalValue - totalCostBasis)
            
            val totalAllTimeCost = assetTransactions.filter { it.type == TransactionType.BUY }
                .sumOf { it.quantity * it.priceAtDate + it.fees }
            val profitPct = if (totalAllTimeCost > 0) (totalProfit / totalAllTimeCost) * 100.0 else 0.0

            val change24h = priceData?.change24h ?: 0.0
            val profitToday = totalValue * (change24h / 100.0)

            PortfolioAsset(
                asset = Asset(entity.id, entity.name, entity.symbol, entity.type, currentPrice, change24h),
                totalQuantity = currentQty,
                averageBuyPrice = avgPrice,
                totalValue = totalValue,
                totalProfit = totalProfit,
                profitPercentage = profitPct,
                profitToday = profitToday,
                profitTodayPercentage = change24h
            )
        }
    }

    data class PriceData(val price: Double, val change24h: Double = 0.0)

    suspend fun fetchCurrentPrices(force: Boolean = false): Map<String, PriceData> = priceMutex.withLock {
        val now = System.currentTimeMillis()
        if (!force && cachedPrices.isNotEmpty() && (now - lastFetchTime) < CACHE_DURATION) {
            return@withLock cachedPrices.toMap()
        }

        Log.d("PriceFetch", "Refreshing price cache...")
        
        var usdEurRate = 0.95
        try {
            val usdEurResp = yahooFinanceApi.getChartData("EURUSD=X")
            val meta = usdEurResp.chart.result?.firstOrNull()?.meta
            if (meta != null && meta.regularMarketPrice > 0) {
                usdEurRate = meta.regularMarketPrice
            }
        } catch (e: Exception) { Log.e("PriceFetch", "EUR/USD failed", e) }

        try {
            val dbAssets = portfolioDao.getAllAssetsOnce()
            val cryptoIds = dbAssets.filter { it.type == AssetType.CRYPTO }.map { it.id }
                .plus(listOf("bitcoin", "ethereum", "cardano", "solana", "pax-gold"))
                .distinct().joinToString(",")

            if (cryptoIds.isNotEmpty()) {
                val cryptoPrices = coinGeckoApi.getPrices(cryptoIds)
                cryptoPrices.forEach { (id, data) ->
                    cachedPrices[id] = PriceData(data["eur"] ?: 0.0, data["eur_24h_change"] ?: 0.0)
                }
            }
        } catch (e: Exception) { Log.e("PriceFetch", "Crypto failed", e) }

        coroutineScope {
            val metalQueries = listOf("XAUEUR=X", "XAGEUR=X", "GC=F", "SI=F")
            metalQueries.map { ticker ->
                async {
                    try {
                        val resp = yahooFinanceApi.getChartData(ticker)
                        val meta = resp.chart.result?.firstOrNull()?.meta ?: return@async
                        val price = meta.regularMarketPrice
                        val prevClose = meta.chartPreviousClose
                        val change = if (prevClose > 0) ((price - prevClose) / prevClose) * 100.0 else 0.0
                        
                        if (ticker.contains("EUR=X")) {
                            if (ticker.startsWith("XAU")) {
                                val gramPrice = price / 31.1035
                                cachedPrices["gold_bar"] = PriceData(gramPrice, change)
                                cachedPrices["gold_ingot"] = PriceData(gramPrice, change)
                                cachedPrices["gold_coin_napoleon"] = PriceData(gramPrice * 5.806, change)
                                cachedPrices["pax-gold"] = PriceData(price, change)
                            } else {
                                cachedPrices["silver_bar"] = PriceData(price / 31.1035, change)
                            }
                        } else if (ticker == "GC=F" && !cachedPrices.containsKey("gold_bar")) {
                            val gramPrice = (price * usdEurRate) / 31.1035
                            cachedPrices["gold_bar"] = PriceData(gramPrice, change)
                            cachedPrices["gold_ingot"] = PriceData(gramPrice, change)
                            cachedPrices["gold_coin_napoleon"] = PriceData(gramPrice * 5.806, change)
                            cachedPrices["pax-gold"] = PriceData(price * usdEurRate, change)
                        } else if (ticker == "SI=F" && !cachedPrices.containsKey("silver_bar")) {
                            cachedPrices["silver_bar"] = PriceData((price * usdEurRate) / 31.1035, change)
                        }
                    } catch (e: Exception) { }
                }
            }.awaitAll()
        }

        try {
            val allDbAssets = portfolioDao.getAllAssetsOnce()
            val stockAssets = allDbAssets.filter { it.type == AssetType.STOCK || it.type == AssetType.ETF }
                .filter { it.symbol.isNotBlank() }
                .distinctBy { it.symbol }

            coroutineScope {
                stockAssets.map { asset ->
                    async {
                        try {
                            val resp = yahooFinanceApi.getChartData(asset.symbol)
                            val meta = resp.chart.result?.firstOrNull()?.meta ?: return@async
                            var price = meta.regularMarketPrice
                            val prevClose = meta.chartPreviousClose
                            val change = if (prevClose > 0) ((price - prevClose) / prevClose) * 100.0 else 0.0
                            if (meta.currency == "USD") price *= usdEurRate
                            allDbAssets.filter { it.symbol == asset.symbol }.forEach { 
                                cachedPrices[it.id] = PriceData(price, change)
                            }
                        } catch (e: Exception) { }
                    }
                }.awaitAll()
            }
        } catch (e: Exception) { }

        lastFetchTime = now
        return@withLock cachedPrices.toMap()
    }

    fun getTransactionsForAsset(assetId: String): Flow<List<Transaction>> {
        return portfolioDao.getTransactionsForAsset(assetId).map { list ->
            list.map { e ->
                Transaction(e.id, e.assetId, e.type, e.quantity, e.priceAtDate, e.date, e.fees)
            }
        }
    }

    suspend fun getAssetHistoricalPrices(assetId: String, symbol: String, type: AssetType, startDateMs: Long): List<Pair<Long, Double>> = chartMutex.withLock {
        val bucketedStart = (startDateMs / 900000L) * 900000L
        val cacheKey = "${assetId}_${bucketedStart}"
        
        val cached = chartCache[cacheKey]
        if (cached != null && (System.currentTimeMillis() - cached.first) < 15 * 60 * 1000L) {
            return@withLock cached.second
        }

        val result = mutableListOf<Pair<Long, Double>>()
        val now = System.currentTimeMillis()
        val daysDiff = ((now - startDateMs) / 86400000L).coerceAtLeast(1)
        val isMetal = type in listOf(AssetType.GOLD_BAR, AssetType.GOLD_INGOT, AssetType.GOLD_COIN, AssetType.METAL)

        var primarySourceFailed = false

        if (type == AssetType.CRYPTO || isMetal) {
            // ESSAI COINGECKO EN PREMIER
            var retryCount = 0
            var success = false
            while (retryCount < 2 && !success) {
                try {
                    val cgId = when {
                        isMetal && (assetId.contains("silver") || symbol.contains("XAG")) -> "kinesis-silver"
                        isMetal -> "pax-gold"
                        else -> assetId.trim().lowercase()
                    }
                    val daysParam = when {
                        daysDiff <= 1 -> "1"
                        daysDiff <= 7 -> "7"
                        daysDiff <= 30 -> "30"
                        else -> "365"
                    }
                    Log.d("ChartData", "CG: Fetching $cgId days=$daysParam")
                    val response = coinGeckoApi.getMarketChart(cgId, "eur", daysParam)
                    val multiplier = when (assetId) {
                        "gold_bar", "gold_ingot" -> 1.0 / 31.1035
                        "gold_coin_napoleon" -> (1.0 / 31.1035) * 5.806
                        "silver_bar" -> 1.0 / 31.1035
                        else -> 1.0
                    }
                    response.prices?.forEach { point ->
                        if (point.size >= 2) {
                            val timestamp = (point[0] as? Number)?.toLong() ?: 0L
                            val price = (point[1] as? Number)?.toDouble() ?: 0.0
                            result.add(Pair(timestamp, price * multiplier))
                        }
                    }
                    if (result.size >= 2) success = true
                } catch (e: Exception) {
                    if (e.toString().contains("429")) {
                        retryCount++
                        Log.w("ChartData", "CG: 429, retry $retryCount")
                        delay(2000)
                    } else { primarySourceFailed = true; break }
                }
            }
            if (!success) primarySourceFailed = true
        }

        // FALLBACK YAHOO OU SOURCE PRIMAIRE YAHOO
        if (type == AssetType.STOCK || type == AssetType.ETF || primarySourceFailed) {
            try {
                // Si c'est un fallback pour crypto/métal, on ajuste le ticker
                val ticker = if (primarySourceFailed && (type == AssetType.CRYPTO || isMetal)) {
                    when {
                        isMetal && symbol.contains("XAU") -> "XAUEUR=X"
                        isMetal && symbol.contains("XAG") -> "XAGEUR=X"
                        symbol.contains("-") -> symbol.uppercase()
                        else -> "${symbol.uppercase()}-EUR"
                    }
                } else symbol

                val (rangeParam, interval) = when {
                    daysDiff <= 1 -> "2d" to "15m"
                    daysDiff <= 7 -> "7d" to "1h"
                    daysDiff <= 30 -> "1mo" to "1h"
                    else -> "1y" to "1d"
                }

                Log.d("ChartData", "Yahoo: Fetching $ticker range=$rangeParam")
                val response = yahooFinanceApi.getHistoricalChartData(ticker, range = rangeParam, interval = interval)
                val chartResult = response.chart.result?.firstOrNull()
                val timestamps = chartResult?.timestamp
                val closes = chartResult?.indicators?.quote?.firstOrNull()?.close

                if (timestamps != null && closes != null) {
                    val batch = mutableListOf<Pair<Long, Double>>()
                    for (i in timestamps.indices) {
                        val tsMs = timestamps[i] * 1000L
                        val closePrice = closes.getOrNull(i)
                        if (closePrice != null) batch.add(Pair(tsMs, closePrice))
                    }
                    // On prend tout sans filtrer par startDate pour contrer les problèmes de fuseau/clocks
                    if (batch.size >= 2) {
                        if (result.isEmpty()) result.addAll(batch)
                        else {
                            // Si on a déjà des points CG, on ne mélange pas forcément, 
                            // mais ici primarySourceFailed est true donc result est vide ou pauvre.
                            result.clear()
                            result.addAll(batch)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ChartData", "Yahoo failure for $symbol: ${e.message}")
            }
        }

        // FILTRAGE FINAL : Si on a des données mais elles sont toutes AVANT startDateMs, on garde les 100 dernières
        // pour que l'utilisateur voit QUELQUE CHOSE.
        val sorted = result.sortedBy { it.first }.distinctBy { it.first }
        val finalResult = if (sorted.size >= 2) {
            val filtered = sorted.filter { it.first >= startDateMs - 3600000L }
            if (filtered.size >= 2) filtered else sorted.takeLast(100)
        } else sorted

        Log.d("ChartData", "Finished $assetId, points: ${finalResult.size}")
        if (finalResult.size >= 2) {
            chartCache[cacheKey] = Pair(System.currentTimeMillis(), finalResult)
        }
        return@withLock finalResult
    }

    suspend fun addTransaction(transaction: Transaction) = syncMutex.withLock {
        portfolioDao.insertTransaction(
            TransactionEntity(
                assetId = transaction.assetId,
                type = transaction.type,
                quantity = transaction.quantity,
                priceAtDate = transaction.priceAtDate,
                date = transaction.date,
                fees = transaction.fees
            )
        )
    }

    suspend fun updateTransaction(transaction: Transaction) = syncMutex.withLock {
        portfolioDao.updateTransaction(
            TransactionEntity(
                id = transaction.id, assetId = transaction.assetId, type = transaction.type,
                quantity = transaction.quantity, priceAtDate = transaction.priceAtDate,
                date = transaction.date, fees = transaction.fees
            )
        )
    }

    suspend fun deleteTransaction(transactionId: Long, assetId: String) = syncMutex.withLock {
        portfolioDao.deleteTransaction(TransactionEntity(id = transactionId, assetId = assetId, type = TransactionType.BUY, quantity = 0.0, priceAtDate = 0.0, date = 0, fees = 0.0))
    }

    suspend fun deleteAsset(assetId: String) = syncMutex.withLock {
        portfolioDao.deleteAsset(assetId)
    }

    suspend fun getAssetPrice(assetId: String): Double? = fetchCurrentPrices()[assetId]?.price

    suspend fun searchAssets(query: String): List<Asset> {
        if (query.length < 2) return emptyList()
        return try {
            val cryptos = try {
                coinGeckoApi.searchCoins(query).coins.take(3).map {
                    Asset(it.id, it.name, it.symbol.uppercase(), AssetType.CRYPTO)
                }
            } catch (e: Exception) { emptyList() }

            val yahooResults = try {
                yahooFinanceApi.searchTicker(query).quotes?.filter { it.quoteType in listOf("EQUITY", "ETF", "MUTUALFUND", "CRYPTOCURRENCY") }
                    ?.take(7)
                    ?.map { quote ->
                        val name = quote.longname ?: quote.shortname ?: quote.symbol
                        val type = when {
                            quote.quoteType in listOf("ETF", "MUTUALFUND") -> AssetType.ETF
                            quote.quoteType == "CRYPTOCURRENCY" -> AssetType.CRYPTO
                            else -> AssetType.STOCK
                        }
                        Asset(quote.symbol.lowercase().replace(".", "_"), name, quote.symbol, type)
                    } ?: emptyList()
            } catch (e: Exception) { emptyList() }

            val metals = listOf(
                Asset("gold_bar", "Lingot d'Or (1kg)", "XAUEUR=X", AssetType.GOLD_BAR),
                Asset("gold_ingot", "Lingotin d'Or (100g)", "XAUEUR=X", AssetType.GOLD_INGOT),
                Asset("gold_coin_napoleon", "Pièce Napoléon 20Fr", "XAUEUR=X", AssetType.GOLD_COIN),
                Asset("silver_bar", "Lingot d'Argent", "XAGEUR=X", AssetType.METAL)
            ).filter { it.name.contains(query, ignoreCase = true) }

            metals + yahooResults + cryptos
        } catch (e: Exception) { emptyList() }
    }

    suspend fun ensureAssetExists(asset: Asset) = syncMutex.withLock {
        portfolioDao.insertAsset(AssetEntity(asset.id, asset.name, asset.symbol, asset.type))
    }

    suspend fun exportToJson(): String = syncMutex.withLock {
        val assets = portfolioDao.getAllAssetsOnce()
        val transactions = portfolioDao.getAllTransactionsOnce()
        val exportData = mapOf(
            "version" to 1, "exportedAt" to System.currentTimeMillis(),
            "assets" to assets.map { mapOf("id" to it.id, "name" to it.name, "symbol" to it.symbol, "type" to it.type.name) },
            "transactions" to transactions.map { mapOf(
                "id" to it.id, "assetId" to it.assetId, "type" to it.type.name,
                "quantity" to it.quantity, "priceAtDate" to it.priceAtDate, "date" to it.date, "fees" to it.fees
            )}
        )
        return com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(exportData)
    }

    suspend fun importFromJson(json: String, clearExisting: Boolean = false): ImportResult = syncMutex.withLock {
        return try {
            val data = com.google.gson.Gson().fromJson(json, Map::class.java) as Map<String, Any>
            val assetsList = data["assets"] as? List<Map<String, Any>> ?: emptyList()
            val transactionsList = data["transactions"] as? List<Map<String, Any>> ?: emptyList()

            database.withTransaction {
                if (clearExisting) {
                    portfolioDao.deleteAllTransactions()
                    portfolioDao.deleteAllAssets()
                }
                var importedAssets = 0
                var importedTransactions = 0
                val existingTxs = if (!clearExisting) portfolioDao.getAllTransactionsOnce() else emptyList()

                for (a in assetsList) {
                    portfolioDao.insertAsset(AssetEntity(
                        id = a["id"] as String, name = a["name"] as String,
                        symbol = a["symbol"] as String, type = AssetType.valueOf(a["type"] as String)
                    ))
                    importedAssets++
                }

                for (t in transactionsList) {
                    val assetId = t["assetId"] as String
                    val type = TransactionType.valueOf(t["type"] as String)
                    val quantity = (t["quantity"] as? Number)?.toDouble() ?: 0.0
                    val priceAtDate = (t["priceAtDate"] as? Number)?.toDouble() ?: 0.0
                    val date = (t["date"] as? Number)?.toLong() ?: 0L
                    val fees = (t["fees"] as? Number)?.toDouble() ?: 0.0
                    if (!clearExisting) {
                        val isDuplicate = existingTxs.any { it.assetId == assetId && it.date == date && it.type == type && Math.abs(it.quantity - quantity) < 0.000001 }
                        if (isDuplicate) continue
                    }
                    portfolioDao.insertTransaction(TransactionEntity(assetId = assetId, type = type, quantity = quantity, priceAtDate = priceAtDate, date = date, fees = fees))
                    importedTransactions++
                }
                ImportResult.Success(importedAssets, importedTransactions)
            }
        } catch (e: Exception) { ImportResult.Error(e.message ?: "Erreur inconnue") }
    }

    sealed class ImportResult {
        data class Success(val assets: Int, val transactions: Int) : ImportResult()
        data class Error(val message: String) : ImportResult()
    }
}
