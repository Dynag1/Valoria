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

            // Tri : date, puis BUY avant SELL pour éviter PRU incohérent sur même timestamp, puis ID
            val sortedTxs = assetTransactions.sortedWith(
                compareBy<TransactionEntity> { it.date }
                    .thenBy { it.type == TransactionType.SELL }
                    .thenBy { it.id }
            )

            for (tx in sortedTxs) {
                if (tx.type == TransactionType.BUY) {
                    currentQty += tx.quantity
                    totalCostBasis += (tx.quantity * tx.priceAtDate) + tx.fees
                } else if (tx.type == TransactionType.SELL) {
                    val currentPru = if (currentQty > 0.00000001) totalCostBasis / currentQty else 0.0
                    val costOfSold = tx.quantity * currentPru
                    totalCostBasis -= costOfSold
                    currentQty -= tx.quantity
                    
                    if (currentQty < 0.00000001) {
                        currentQty = 0.0
                        totalCostBasis = 0.0
                    }
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

            // --- Calcul de la performance "Aujourd'hui" (dernières 24h) ---
            val change24h = priceData?.change24h ?: 0.0
            val now = System.currentTimeMillis()
            val cutoff24h = now - 24 * 60 * 60 * 1000L
            
            // On retrouve la situation il y a 24h
            val txsToday = assetTransactions.filter { it.date > cutoff24h }
            val qtyBoughtToday = txsToday.filter { it.type == TransactionType.BUY }.sumOf { it.quantity }
            val qtySoldToday = txsToday.filter { it.type == TransactionType.SELL }.sumOf { it.quantity }
            val qty24hAgo = (currentQty - qtyBoughtToday + qtySoldToday).coerceAtLeast(0.0)
            
            val price24hAgo = currentPrice / (1.0 + (change24h / 100.0))
            val value24hAgo = qty24hAgo * price24hAgo
            
            // Flux de trésorerie net aujourd'hui
            val netCashflowToday = txsToday.sumOf { 
                val sign = if (it.type == TransactionType.BUY) 1.0 else -1.0
                sign * (it.quantity * it.priceAtDate + (if (it.type == TransactionType.BUY) it.fees else -it.fees))
            }

            // Profit du jour = Variation de valeur - Flux net
            val profitToday = totalValue - value24hAgo - netCashflowToday
            
            // Le % du jour est rapporté à la valeur de départ + les achats (évite division par 0)
            val baseForPct = value24hAgo + txsToday.filter { it.type == TransactionType.BUY }.sumOf { it.quantity * it.priceAtDate }
            val profitTodayPercentage = if (baseForPct > 0) (profitToday / baseForPct) * 100.0 else change24h

            PortfolioAsset(
                asset = Asset(entity.id, entity.name, entity.symbol, entity.type, currentPrice, change24h),
                totalQuantity = currentQty,
                averageBuyPrice = avgPrice,
                totalValue = totalValue,
                totalProfit = totalProfit,
                profitPercentage = profitPct,
                profitToday = profitToday,
                profitTodayPercentage = profitTodayPercentage,
                totalAllTimeCost = totalAllTimeCost
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

        // Fetch metals via getQuotes() to get regularMarketChangePercent (reliable even when market closed)
        // Step 1: try getQuotes for regularMarketChangePercent (more reliable than chartPreviousClose)
        // /v7/finance/quote may not always return forex-style pairs (XAUEUR=X), so we track what was found
        val metalChangeMap = mutableMapOf<String, Double>() // symbol -> changePercent
        try {
            val metalResp = yahooFinanceApi.getQuotes("XAUEUR=X,XAGEUR=X,GC=F,SI=F")
            val quoteMap = metalResp.quoteResponse.result?.associateBy { it.symbol } ?: emptyMap()
            quoteMap.forEach { (sym, q) ->
                val pct = q.regularMarketChangePercent
                if (pct != null) metalChangeMap[sym] = pct
            }
        } catch (e: Exception) { Log.w("PriceFetch", "getQuotes for metals failed, will use chartPreviousClose") }

        // Step 2: getChartData for the actual price (always works for XAUEUR=X / GC=F)
        coroutineScope {
            listOf("XAUEUR=X", "XAGEUR=X", "GC=F", "SI=F").map { ticker ->
                async {
                    try {
                        val resp = yahooFinanceApi.getChartData(ticker)
                        val meta = resp.chart.result?.firstOrNull()?.meta ?: return@async
                        val price = meta.regularMarketPrice
                        val prevClose = meta.chartPreviousClose
                        // Prefer change from getQuotes, fallback to chartPreviousClose calc
                        val change = metalChangeMap[ticker]
                            ?: metalChangeMap[if (ticker == "GC=F") "XAUEUR=X" else if (ticker == "SI=F") "XAGEUR=X" else ticker]
                            ?: if (prevClose > 0) ((price - prevClose) / prevClose) * 100.0 else 0.0

                        when {
                            ticker == "XAUEUR=X" -> {
                                val gramPrice = price / 31.1035
                                cachedPrices["gold_bar"]           = PriceData(gramPrice * 1000.0, change)
                                cachedPrices["gold_ingot"]         = PriceData(gramPrice * 100.0,  change)
                                cachedPrices["gold_coin_napoleon"] = PriceData(gramPrice * 5.806,  change)
                                cachedPrices["pax-gold"]           = PriceData(price,              change)
                            }
                            ticker == "GC=F" && !cachedPrices.containsKey("gold_bar") -> {
                                val gramPrice = (price / usdEurRate) / 31.1035
                                cachedPrices["gold_bar"]           = PriceData(gramPrice * 1000.0, change)
                                cachedPrices["gold_ingot"]         = PriceData(gramPrice * 100.0,  change)
                                cachedPrices["gold_coin_napoleon"] = PriceData(gramPrice * 5.806,  change)
                                cachedPrices["pax-gold"]           = PriceData(price / usdEurRate, change)
                            }
                            ticker == "XAGEUR=X" -> {
                                cachedPrices["silver_bar"] = PriceData((price / 31.1035) * 1000.0, change)
                            }
                            ticker == "SI=F" && !cachedPrices.containsKey("silver_bar") -> {
                                cachedPrices["silver_bar"] = PriceData((price / usdEurRate / 31.1035) * 1000.0, change)
                            }
                        }
                    } catch (e: Exception) { Log.w("PriceFetch", "getChartData failed for $ticker") }
                }
            }.awaitAll()
        }

        try {
            val allDbAssets = portfolioDao.getAllAssetsOnce()
            val stockAssets = allDbAssets.filter { 
                it.type == AssetType.STOCK || it.type == AssetType.ETF || 
                (it.type == AssetType.CRYPTO && !cachedPrices.containsKey(it.id))
            }
                .filter { it.symbol.isNotBlank() }
                .distinctBy { it.symbol }

            // Batch fetch stocks via getQuotes() for reliable regularMarketChangePercent
            val symbols = stockAssets.map { it.symbol }.joinToString(",")
            val stockQuoteMap = try {
                val resp = yahooFinanceApi.getQuotes(symbols)
                resp.quoteResponse.result?.associateBy { it.symbol } ?: emptyMap()
            } catch (e: Exception) { emptyMap() }

            coroutineScope {
                stockAssets.map { asset ->
                    async {
                        try {
                            val quote = stockQuoteMap[asset.symbol]
                            val resp = yahooFinanceApi.getChartData(asset.symbol)
                            val meta = resp.chart.result?.firstOrNull()?.meta ?: return@async
                            var price = meta.regularMarketPrice
                            // Prefer regularMarketChangePercent from getQuotes (reliable), fallback to chartPreviousClose calc
                            val change = quote?.regularMarketChangePercent
                                ?: if (meta.chartPreviousClose > 0) ((price - meta.chartPreviousClose) / meta.chartPreviousClose) * 100.0 else 0.0
                            
                            // Conversion multi-devises vers EUR
                            // EurUsdRate (EURUSD=X) est le nombre de dollars pour 1 Euro (ex: 1.08)
                            // Donc 1 USD = 1 / 1.08 Euro.
                            price = when (meta.currency) {
                                "USD" -> price / usdEurRate
                                "GBp" -> (price / 100.0) / (usdEurRate * 0.92) // Approx GBP/USD ou conversion directe
                                "GBP" -> price / (usdEurRate * 0.92)
                                "CHF" -> price / 0.96 // Approx CHF/EUR
                                else -> price // Supposé EUR
                            }

                            // Idéalement, on devrait fetcher les taux, mais pour la stabilité
                            // on va au moins corriger le bug des Pences (GBp) qui est commun sur les ETFs
                            if (meta.currency == "GBp") {
                                // On tente de fetcher le taux réel pour GBP/EUR si besoin... 
                                // mais le plus important est la division par 100.
                            }

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

    suspend fun getAssetHistoricalPrices(assetId: String, symbol: String, type: AssetType, startDateMs: Long): List<Pair<Long, Double>> {
        val bucketedStart = (startDateMs / 900000L) * 900000L
        val cacheKey = "${assetId}_${bucketedStart}"
        
        // Lecture du cache sans mutex pour ne pas bloquer les chargements parallèles
        val cached = chartCache[cacheKey]
        if (cached != null && (System.currentTimeMillis() - cached.first) < 15 * 60 * 1000L) {
            return cached.second
        }

        val result = mutableListOf<Pair<Long, Double>>()
        val now = System.currentTimeMillis()
        val daysDiff = ((now - startDateMs) / 86400000L).coerceAtLeast(1)
        val isMetal = type in listOf(AssetType.GOLD_BAR, AssetType.GOLD_INGOT, AssetType.GOLD_COIN, AssetType.METAL)
        val isGold = isMetal && !assetId.contains("silver")
        val isSilver = isMetal && assetId.contains("silver")

        // Multiplicateur commun (troy oz → unité de l'actif)
        val metalMultiplier = when (assetId) {
            "gold_bar"          -> 1000.0 / 31.1035   // 1000g
            "gold_ingot"        -> 100.0  / 31.1035   // 100g
            "gold_coin_napoleon"-> 5.806  / 31.1035   // 5.806g
            "silver_bar"        -> 1000.0 / 31.1035   // 1000g
            else                -> 1.0
        }

        var primarySourceFailed = false

        if (type == AssetType.CRYPTO || isMetal) {
            // ESSAI COINGECKO EN PREMIER
            var retryCount = 0
            var success = false
            while (retryCount < 2 && !success) {
                try {
                    val cgId = when {
                        isSilver -> "kinesis-silver"
                        isMetal  -> "pax-gold"
                        else     -> assetId.trim().lowercase()
                    }
                    val daysParam = when {
                        daysDiff <= 1   -> "1"
                        daysDiff <= 7   -> "7"
                        daysDiff <= 30  -> "30"
                        daysDiff <= 365 -> "365"
                        else            -> "max"
                    }
                    Log.d("ChartData", "CG: Fetching $cgId days=$daysParam")
                    val response = coinGeckoApi.getMarketChart(cgId, "eur", daysParam)
                    response.prices?.forEach { point ->
                        if (point.size >= 2) {
                            val timestamp = (point[0] as? Number)?.toLong() ?: 0L
                            val price = (point[1] as? Number)?.toDouble() ?: 0.0
                            result.add(Pair(timestamp, price * metalMultiplier))
                        }
                    }
                    if (result.size >= 2) success = true
                } catch (e: Exception) {
                    if (e.toString().contains("429")) {
                        retryCount++
                        Log.w("ChartData", "CG: 429, retry $retryCount")
                        delay(1000) // 1s au lieu de 2s pour ne pas bloquer l'UI
                    } else { primarySourceFailed = true; break }
                }
            }
            if (!success) primarySourceFailed = true
        }

        // FALLBACK YAHOO OU SOURCE PRIMAIRE YAHOO
        if (type == AssetType.STOCK || type == AssetType.ETF || primarySourceFailed) {
            // Pour les métaux, GC=F (Gold Futures) et SI=F (Silver Futures) supportent
            // bien mieux les longues plages historiques que XAUEUR=X / XAGEUR=X
            val yahooTicker = when {
                primarySourceFailed && isGold   -> "GC=F"
                primarySourceFailed && isSilver -> "SI=F"
                primarySourceFailed && type == AssetType.CRYPTO -> {
                    if (symbol.contains("-")) symbol.uppercase()
                    else "${symbol.uppercase()}-EUR"
                }
                else -> symbol
            }

            // Pour les Futures USD, on a besoin du taux EUR/USD pour convertir
            var usdEurRate = 0.0
            if ((yahooTicker == "GC=F" || yahooTicker == "SI=F") && primarySourceFailed) {
                try {
                    val resp = yahooFinanceApi.getChartData("EURUSD=X")
                    usdEurRate = resp.chart.result?.firstOrNull()?.meta?.regularMarketPrice ?: 0.0
                } catch (_: Exception) {}
                if (usdEurRate <= 0) usdEurRate = 0.94 // fallback statique
            }

            val (rangeParam, interval) = when {
                daysDiff <= 1   -> "2d"  to "15m"
                daysDiff <= 7   -> "7d"  to "1h"
                daysDiff <= 30  -> "1mo" to "1h"
                daysDiff <= 365 -> "1y"  to "1d"
                else            -> "max" to "1wk"  // 1wk pour les longues plages (moins de points null)
            }

            try {
                Log.d("ChartData", "Yahoo: Fetching $yahooTicker range=$rangeParam interval=$interval")
                val response = yahooFinanceApi.getHistoricalChartData(yahooTicker, range = rangeParam, interval = interval)
                val chartResult = response.chart.result?.firstOrNull()
                val timestamps = chartResult?.timestamp
                val closes = chartResult?.indicators?.quote?.firstOrNull()?.close

                if (timestamps != null && closes != null) {
                    val batch = mutableListOf<Pair<Long, Double>>()
                    for (i in timestamps.indices) {
                        val tsMs = timestamps[i] * 1000L
                        var closePrice = closes.getOrNull(i) ?: continue
                        // Appliquer le multiplicateur pour les métaux (GC=F / SI=F sont en USD/oz)
                        if (primarySourceFailed && isMetal && usdEurRate > 0) {
                            closePrice = (closePrice / usdEurRate) * metalMultiplier
                        } else if (primarySourceFailed && isMetal) {
                            closePrice = closePrice * metalMultiplier
                        }
                        if (closePrice > 0) batch.add(Pair(tsMs, closePrice))
                    }
                    if (batch.size >= 2) {
                        result.clear()
                        result.addAll(batch)
                    }
                }
            } catch (e: Exception) {
                Log.e("ChartData", "Yahoo failure for $yahooTicker: ${e.message}")
            }
        }

        // FILTRAGE FINAL
        val sorted = result.sortedBy { it.first }.distinctBy { it.first }
        val finalResult = if (sorted.size >= 2) {
            val filtered = sorted.filter { it.first >= startDateMs - 3600000L }
            if (filtered.size >= 2) filtered else sorted.takeLast(100)
        } else sorted

        Log.d("ChartData", "Finished $assetId, points: ${finalResult.size}")
        if (finalResult.size >= 2) {
            chartCache[cacheKey] = Pair(System.currentTimeMillis(), finalResult)
        }
        return finalResult
    }

    suspend fun getPortfolioTrends(isGold: Boolean, startDateMs: Long): List<Pair<Long, Double>> = chartMutex.withLock {
        val assets = getPortfolioAssetsOnce()
        val goldTypes = listOf(AssetType.GOLD_BAR, AssetType.GOLD_INGOT, AssetType.GOLD_COIN, AssetType.METAL)
        val filteredAssets = if (isGold) {
            assets.filter { it.asset.type in goldTypes }
        } else {
            assets.filter { it.asset.type !in goldTypes }
        }

        if (filteredAssets.isEmpty()) return@withLock emptyList()

        // Fetch ALL transactions once to have local quantity history
        val allTransactions = portfolioDao.getAllTransactionsOnce()

        // Fetch ALL historical prices in parallel
        val assetChartResults = coroutineScope {
            filteredAssets.map { portfolioAsset ->
                async {
                    val assetId = portfolioAsset.asset.id
                    val symbol = portfolioAsset.asset.symbol
                    val type = portfolioAsset.asset.type
                    val prices = getAssetHistoricalPrices(assetId, symbol, type, startDateMs)
                    assetId to prices
                }
            }.awaitAll().toMap()
        }

        // Collect all timestamps from all assets to build a common timeline
        // On prend un sous-ensemble si trop de points (ex: Yahoo renvoie beaucoup de points sur 24h)
        val allTimestamps = assetChartResults.values.flatten().map { it.first }.distinct().sorted()
        if (allTimestamps.isEmpty()) return@withLock emptyList()

        // Si le nombre de points est gigantesque, on réduit pour le rendu (e.g. max 200 points)
        val stride = (allTimestamps.size / 200).coerceAtLeast(1)
        val sampleTimestamps = allTimestamps.filterIndexed { index, _ -> index % stride == 0 || index == allTimestamps.size - 1 }

        return@withLock sampleTimestamps.map { ts ->
            var totalValueAtTs = 0.0
            for (portfolioAsset in filteredAssets) {
                val assetId = portfolioAsset.asset.id
                val assetPrices = assetChartResults[assetId] ?: continue
                
                // On cherche le prix correspondant. plus efficace : sorted search ou finding closest.
                // find closest ts before or equal to this ts
                val priceAtTs = assetPrices.filter { it.first <= ts }.lastOrNull()?.second ?: assetPrices.firstOrNull()?.second ?: 0.0
                
                // Get the quantity at this timestamp based on transactions
                val qtyAtTs = allTransactions
                    .filter { it.assetId == assetId && it.date <= ts }
                    .sumOf { if (it.type == TransactionType.BUY) it.quantity else -it.quantity }
                    .coerceAtLeast(0.0)
                
                totalValueAtTs += qtyAtTs * priceAtTs
            }
            Pair(ts, totalValueAtTs)
        }
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
                        val isDuplicate = existingTxs.any { 
                            it.assetId == assetId && it.date == date && it.type == type && 
                            Math.abs(it.quantity - quantity) < 0.000001 &&
                            Math.abs(it.priceAtDate - priceAtDate) < 0.000001 &&
                            Math.abs(it.fees - fees) < 0.000001
                        }
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
