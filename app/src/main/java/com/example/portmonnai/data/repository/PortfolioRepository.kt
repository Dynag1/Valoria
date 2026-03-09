package com.example.portmonnai.data.repository

import com.example.portmonnai.data.local.AssetEntity
import com.example.portmonnai.data.local.AppDatabase
import com.example.portmonnai.data.local.PortfolioDao
import com.example.portmonnai.data.local.TransactionEntity
import com.example.portmonnai.data.remote.CoinGeckoApi
import com.example.portmonnai.domain.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import androidx.room.withTransaction

// IDs spéciaux pour les métaux physiques (pas de ticker Yahoo standard)
private val METAL_IDS = setOf(
    "gold_bar", "gold_ingot", "gold_coin_napoleon", "silver_bar"
)

// Tickers Yahoo Finance pour les métaux
private val METAL_TICKERS = mapOf(
    "gold_bar" to "XAUEUR=X",
    "gold_ingot" to "XAUEUR=X",
    "gold_coin_napoleon" to "XAUEUR=X",
    "silver_bar" to "XAGEUR=X"
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Singleton
class PortfolioRepository @Inject constructor(
    private val database: AppDatabase,
    private val coinGeckoApi: CoinGeckoApi,
    private val yahooFinanceApi: com.example.portmonnai.data.remote.YahooFinanceApi
) {
    private val portfolioDao = database.portfolioDao()
    private val syncMutex = Mutex()
    private val cachedPrices = mutableMapOf<String, PriceData>()
    private val refreshTrigger = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }

    fun requestRefresh() {
        refreshTrigger.tryEmit(Unit)
    }

    fun getPortfolioAssets(): Flow<List<PortfolioAsset>> {
        val pricesFlow = refreshTrigger.flatMapLatest {
            flow {
                while (true) {
                    try {
                        emit(fetchCurrentPrices())
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    kotlinx.coroutines.delay(60000)
                }
            }
        }

        return combine(
            portfolioDao.getAllAssets(),
            portfolioDao.getAllTransactions(),
            pricesFlow
        ) { assets, transactions, prices ->
            assets.map { entity ->
                val assetTransactions = transactions.filter { it.assetId == entity.id }
                var currentQty = 0.0
                var totalCostBasis = 0.0
                var realizedProfit = 0.0

                // Traitement chronologique des transactions pour un PRU (Prix de Revient Unitaire) exact
                for (tx in assetTransactions.sortedBy { it.date }) {
                    if (tx.type == TransactionType.BUY) {
                        currentQty += tx.quantity
                        totalCostBasis += (tx.quantity * tx.priceAtDate) + tx.fees
                    } else if (tx.type == TransactionType.SELL) {
                        // Le PRU au moment de la vente
                        val currentPru = if (currentQty > 0) totalCostBasis / currentQty else 0.0
                        // Le coût de ce qui est vendu part du CostBasis
                        val costOfSold = tx.quantity * currentPru
                        totalCostBasis -= costOfSold
                        if (totalCostBasis < 0) totalCostBasis = 0.0
                        
                        currentQty -= tx.quantity
                        
                        // Plus-value réalisée sur cette vente : (Prix vente - PRU) - frais
                        val revenue = (tx.quantity * tx.priceAtDate) - tx.fees
                        realizedProfit += (revenue - costOfSold)
                    }
                }

                // PRU moyen actuel des actifs restants
                val avgPrice = if (currentQty > 0) totalCostBasis / currentQty else 0.0

                val priceData = prices[entity.id]
                val currentPrice = priceData?.price ?: 0.0
                
                val totalValue = currentQty * currentPrice
                
                // Plus-value latente (sur ce que l'on possède encore) = Valeur actuelle - Coût de revient actuel
                val unrealizedProfit = totalValue - totalCostBasis
                
                // Bénéfice total = Gains encaissés (Ventes) + Gains virtuels (Actuels)
                val totalProfit = realizedProfit + unrealizedProfit
                
                // % de bénéfice (basé sur le total réellement dépensé pour acheter ces actifs)
                val totalAllTimeCost = assetTransactions.filter { it.type == TransactionType.BUY }
                    .sumOf { it.quantity * it.priceAtDate + it.fees }
                val profitPct = if (totalAllTimeCost > 0) (totalProfit / totalAllTimeCost) * 100.0 else 0.0

                val change24h = priceData?.change24h ?: 0.0
                val profitToday = totalValue * (change24h / 100)

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
    }

    data class PriceData(val price: Double, val change24h: Double = 0.0)

    private suspend fun fetchCurrentPrices(): Map<String, PriceData> {
        val result = mutableMapOf<String, PriceData>()

        // 1. Fetch EUR/USD rate
        var usdEurRate = 1.08 // Fallback raisonnable
        try {
            val eurUsdResponse = yahooFinanceApi.getChartData("EURUSD=X")
            val price = eurUsdResponse.chart.result?.firstOrNull()?.meta?.regularMarketPrice
            if (price != null && price > 0) usdEurRate = price
        } catch (e: Exception) { e.printStackTrace() }

        // 2. Fetch cryptos from CoinGecko
        try {
            val dbAssets = portfolioDao.getAllAssetsOnce()
            val cryptoIds = dbAssets.filter { it.type == AssetType.CRYPTO }.map { it.id }
                .plus(listOf("bitcoin", "ethereum", "cardano", "solana", "pax-gold"))
                .distinct()
                .joinToString(",")

            if (cryptoIds.isNotEmpty()) {
                val cryptoPrices = coinGeckoApi.getPrices(cryptoIds)
                cryptoPrices.forEach { (id, data) ->
                    result[id] = PriceData(
                        data["eur"] ?: 0.0,
                        data["eur_24h_change"] ?: 0.0
                    )
                }
            }
        } catch (e: Exception) { e.printStackTrace() }

        // 3. Fetch metals
        var goldSpotEur = 0.0
        var goldChange24h = 0.0
        
        // Try Yahoo Spot XAUEUR=X
        try {
            val goldResp = yahooFinanceApi.getChartData("XAUEUR=X")
            val goldMeta = goldResp.chart.result?.firstOrNull()?.meta
            if (goldMeta != null && goldMeta.regularMarketPrice > 0) {
                goldSpotEur = goldMeta.regularMarketPrice
                val prevClose = goldMeta.chartPreviousClose
                goldChange24h = if (prevClose > 0) ((goldSpotEur - prevClose) / prevClose) * 100 else 0.0
            }
        } catch (e: Exception) { e.printStackTrace() }

        // Try Yahoo Futures GC=F (USD) if spot failed
        if (goldSpotEur <= 0.0) {
            try {
                val goldResp = yahooFinanceApi.getChartData("GC=F")
                val goldMeta = goldResp.chart.result?.firstOrNull()?.meta
                if (goldMeta != null && goldMeta.regularMarketPrice > 0) {
                    goldSpotEur = goldMeta.regularMarketPrice / usdEurRate
                    val prevClose = goldMeta.chartPreviousClose
                    goldChange24h = if (prevClose > 0) ((goldMeta.regularMarketPrice - prevClose) / prevClose) * 100 else 0.0
                }
            } catch (e: Exception) { e.printStackTrace() }
        }

        // Final Fallback: CoinGecko PAX Gold
        if (goldSpotEur <= 0.0) {
            val paxGoldData = result["pax-gold"]
            if (paxGoldData != null && paxGoldData.price > 0) {
                goldSpotEur = paxGoldData.price
                goldChange24h = paxGoldData.change24h
            }
        }

        if (goldSpotEur > 0) {
            val gramPrice = goldSpotEur / 31.1035
            result["gold_bar"] = PriceData(gramPrice, goldChange24h)
            result["gold_ingot"] = PriceData(gramPrice, goldChange24h)
            result["gold_coin_napoleon"] = PriceData(gramPrice * 5.806, goldChange24h)
        }

        // Silver
        var silverSpotEur = 0.0
        var silverChange24h = 0.0
        try {
            val silverResp = yahooFinanceApi.getChartData("XAGEUR=X")
            val silverMeta = silverResp.chart.result?.firstOrNull()?.meta
            if (silverMeta != null && silverMeta.regularMarketPrice > 0) {
                silverSpotEur = silverMeta.regularMarketPrice
                val prevClose = silverMeta.chartPreviousClose
                silverChange24h = if (prevClose > 0) ((silverSpotEur - prevClose) / prevClose) * 100 else 0.0
            }
        } catch (e: Exception) { e.printStackTrace() }

        if (silverSpotEur <= 0.0) {
            try {
                val silverResp = yahooFinanceApi.getChartData("SI=F")
                val silverMeta = silverResp.chart.result?.firstOrNull()?.meta
                if (silverMeta != null && silverMeta.regularMarketPrice > 0) {
                    silverSpotEur = silverMeta.regularMarketPrice / usdEurRate
                    val prevClose = silverMeta.chartPreviousClose
                    silverChange24h = if (prevClose > 0) ((silverMeta.regularMarketPrice - prevClose) / prevClose) * 100 else 0.0
                }
            } catch (e: Exception) { e.printStackTrace() }
        }

        if (silverSpotEur > 0) {
            result["silver_bar"] = PriceData(silverSpotEur / 31.1035, silverChange24h)
        }

        // 4. Fetch stocks/ETFs via Yahoo Finance en utilisant directement le symbole de l'actif
        //    Le symbole stocké en base EST le ticker Yahoo (ex: HO.PA, CW8.PA, AAPL, etc.)
        val dbAssets = try { portfolioDao.getAllAssetsOnce() } catch (e: Exception) { emptyList() }
        val stockAssets = dbAssets.filter {
            it.type in listOf(AssetType.STOCK, AssetType.ETF) && it.symbol.isNotBlank()
        }

        for (asset in stockAssets) {
            if (result.containsKey(asset.id)) continue // déjà fetchée (ex: crypto)
            try {
                val response = yahooFinanceApi.getChartData(asset.symbol)
                val meta = response.chart.result?.firstOrNull()?.meta ?: continue
                var price = meta.regularMarketPrice
                val prevClose = meta.chartPreviousClose

                // Conversion USD → EUR si nécessaire
                if (meta.currency == "USD") price /= usdEurRate

                if (!price.isNaN() && !price.isInfinite() && price > 0) {
                    val changePct = if (prevClose > 0 && !prevClose.isNaN())
                        ((meta.regularMarketPrice - prevClose) / prevClose) * 100.0 else 0.0
                    result[asset.id] = PriceData(price, if (changePct.isNaN()) 0.0 else changePct)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 5. Update cache and return merged results
        result.forEach { (id, data) ->
            if (data.price > 0) {
                cachedPrices[id] = data
            }
        }
        
        // Merge with cache for missing items
        cachedPrices.forEach { (id, data) ->
            if (!result.containsKey(id)) {
                result[id] = data
            }
        }

        return result
    }

    fun getTransactionsForAsset(assetId: String): Flow<List<Transaction>> {
        return portfolioDao.getTransactionsForAsset(assetId).map { list ->
            list.map { e ->
                Transaction(e.id, e.assetId, e.type, e.quantity, e.priceAtDate, e.date, e.fees)
            }
        }
    }

    suspend fun getAssetHistoricalPrices(assetId: String, symbol: String, type: AssetType, startDateMs: Long): List<Pair<Long, Double>> {
        val result = mutableListOf<Pair<Long, Double>>()
        try {
            val now = System.currentTimeMillis()
            val daysDiff = ((now - startDateMs) / 86400000L).coerceAtLeast(1)

            val isMetal = type in listOf(AssetType.GOLD_BAR, AssetType.GOLD_INGOT, AssetType.GOLD_COIN, AssetType.METAL)

            if (type == AssetType.CRYPTO || isMetal) {
                // CoinGecko
                val cgId = when {
                    isMetal && assetId.contains("silver") -> "kinesis-silver"
                    isMetal -> "pax-gold"
                    assetId == "pax-gold" -> "pax-gold"
                    else -> assetId
                }
                
                // On utilise une valeur explicite en jours si possible pour forcer CG à remonter assez loin
                val daysParam = when {
                    daysDiff <= 1 -> "1"
                    daysDiff <= 90 -> daysDiff.toString()
                    daysDiff <= 365 -> "365"
                    else -> "max"
                }
                
                val response = coinGeckoApi.getMarketChart(cgId, "eur", daysParam)
                
                val multiplier = when (assetId) {
                    "gold_bar", "gold_ingot" -> 1.0 / 31.1035
                    "gold_coin_napoleon" -> (1.0 / 31.1035) * 5.806
                    "silver_bar" -> 1.0 / 31.1035
                    else -> 1.0
                }

                response.prices?.forEach { point ->
                    if (point.size >= 2) {
                        val timestamp = point[0].toLong()
                        if (timestamp >= startDateMs - 3600000L) {
                            result.add(Pair(timestamp, point[1] * multiplier))
                        }
                    }
                }
            } else {
                // Yahoo Finance
                val yahooTicker = symbol
                
                // Détermination de l'intervalle et de la range la plus proche
                // On utilise une range un peu plus large (5d) pour les périodes courtes (1d, 7d)
                // pour s'assurer d'avoir des points même si le marché était fermé
                val (rangeParam, interval) = when {
                    daysDiff <= 1 -> "5d" to "15m"
                    daysDiff <= 7 -> "7d" to "1h"
                    daysDiff <= 30 -> "1mo" to "1h"
                    daysDiff <= 365 -> "1y" to "1d"
                    daysDiff <= 1825 -> "5y" to "1d"
                    else -> "max" to "1wk"
                }
                
                val response = if (rangeParam != null) {
                    yahooFinanceApi.getHistoricalChartData(yahooTicker, range = rangeParam, interval = interval)
                } else {
                    yahooFinanceApi.getHistoricalChartData(yahooTicker, period1 = startDateMs / 1000L, period2 = now / 1000L, interval = interval)
                }

                val chartResult = response.chart.result?.firstOrNull()
                val timestamps = chartResult?.timestamp
                val closes = chartResult?.indicators?.quote?.firstOrNull()?.close

                if (timestamps != null && closes != null) {
                    val tempResult = mutableListOf<Pair<Long, Double>>()
                    for (i in timestamps.indices) {
                        val tsMs = timestamps[i] * 1000L
                        val closePrice = closes.getOrNull(i)
                        
                        if (closePrice != null) {
                            tempResult.add(Pair(tsMs, closePrice))
                        }
                    }
                    
                    // Filtrage intelligent : si 24h et marché fermé (week-end), 
                    // on prend au moins les derniers points dispos de la range
                    val filtered = tempResult.filter { it.first >= startDateMs - 3600000L }
                    if (filtered.size >= 2) {
                        result.addAll(filtered)
                    } else if (tempResult.size >= 2) {
                        // Fallback : on prend les derniers points de la range (ex: les points du vendredi)
                        result.addAll(tempResult.takeLast(50))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result.sortedBy { it.first }.distinctBy { it.first }
    }

    suspend fun addTransaction(transaction: Transaction) {
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

    suspend fun updateTransaction(transaction: Transaction) {
        portfolioDao.updateTransaction(
            TransactionEntity(
                id = transaction.id,
                assetId = transaction.assetId,
                type = transaction.type,
                quantity = transaction.quantity,
                priceAtDate = transaction.priceAtDate,
                date = transaction.date,
                fees = transaction.fees
            )
        )
    }

    suspend fun deleteTransaction(transactionId: Long, assetId: String) {
        portfolioDao.deleteTransaction(
            TransactionEntity(
                id = transactionId,
                assetId = assetId,
                type = TransactionType.BUY,
                quantity = 0.0,
                priceAtDate = 0.0,
                date = 0,
                fees = 0.0
            )
        )
    }

    suspend fun deleteAsset(assetId: String) {
        portfolioDao.deleteAsset(assetId)
    }

    suspend fun getAssetPrice(assetId: String): Double? {
        val prices = fetchCurrentPrices()
        return prices[assetId]?.price
    }

    /**
     * Recherche d'actifs : cryptos via CoinGecko + actions/ETFs via Yahoo Finance Search.
     * Le symbole retourné pour les actions/ETFs est directement le ticker Yahoo (ex: HO.PA).
     */
    suspend fun searchAssets(query: String): List<Asset> {
        if (query.length < 2) return emptyList()

        return try {
            // Recherche parallèle CoinGecko + Yahoo
            val cryptosDeferred = try {
                val response = coinGeckoApi.searchCoins(query)
                response.coins.take(3).map {
                    Asset(it.id, it.name, it.symbol.uppercase(), AssetType.CRYPTO)
                }
            } catch (e: Exception) { emptyList() }

            // Recherche Yahoo Finance Search — retourne actions, ETFs, etc.
            val yahooResults = try {
                val response = yahooFinanceApi.searchTicker(query)
                response.quotes
                    ?.filter { it.quoteType in listOf("EQUITY", "ETF", "MUTUALFUND") }
                    ?.take(7)
                    ?.map { quote ->
                        val name = quote.longname ?: quote.shortname ?: quote.symbol
                        val type = when (quote.quoteType) {
                            "ETF", "MUTUALFUND" -> AssetType.ETF
                            else -> AssetType.STOCK
                        }
                        // L'ID = symbole normalisé (lowercase, sans points)
                        val id = quote.symbol.lowercase().replace(".", "_")
                        // Le symbole = ticker Yahoo tel quel (ex: HO.PA, AAPL, CW8.PA)
                        Asset(id, name, quote.symbol, type)
                    } ?: emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }

            // Métaux physiques (recherche locale)
            val metals = listOf(
                Asset("gold_bar", "Lingot d'Or (1kg)", "XAUEUR=X", AssetType.GOLD_BAR),
                Asset("gold_ingot", "Lingotin d'Or (100g)", "XAUEUR=X", AssetType.GOLD_INGOT),
                Asset("gold_coin_napoleon", "Pièce Napoléon 20Fr", "XAUEUR=X", AssetType.GOLD_COIN),
                Asset("silver_bar", "Lingot d'Argent", "XAGEUR=X", AssetType.METAL)
            ).filter {
                it.name.contains(query, ignoreCase = true) ||
                it.symbol.contains(query, ignoreCase = true)
            }

            metals + yahooResults + cryptosDeferred
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun ensureAssetExists(asset: Asset) {
        portfolioDao.insertAsset(
            AssetEntity(asset.id, asset.name, asset.symbol, asset.type)
        )
    }

    // ───────────────────────────────────────────────
    // Export / Import JSON
    // ───────────────────────────────────────────────

    suspend fun exportToJson(): String = syncMutex.withLock {
        val assets = portfolioDao.getAllAssetsOnce()
        val transactions = portfolioDao.getAllTransactionsOnce()

        val exportData = mapOf(
            "version" to 1,
            "exportedAt" to System.currentTimeMillis(),
            "assets" to assets.map { mapOf(
                "id" to it.id,
                "name" to it.name,
                "symbol" to it.symbol,
                "type" to it.type.name
            )},
            "transactions" to transactions.map { mapOf(
                "id" to it.id,
                "assetId" to it.assetId,
                "type" to it.type.name,
                "quantity" to it.quantity,
                "priceAtDate" to it.priceAtDate,
                "date" to it.date,
                "fees" to it.fees
            )}
        )

        return com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(exportData)
    }

    suspend fun importFromJson(json: String, clearExisting: Boolean = false): ImportResult = syncMutex.withLock {
        return try {
            val gson = com.google.gson.Gson()
            @Suppress("UNCHECKED_CAST")
            val data = gson.fromJson(json, Map::class.java) as Map<String, Any>

            @Suppress("UNCHECKED_CAST")
            val assetsList = data["assets"] as? List<Map<String, Any>> ?: emptyList()
            @Suppress("UNCHECKED_CAST")
            val transactionsList = data["transactions"] as? List<Map<String, Any>> ?: emptyList()

            database.withTransaction {
                if (clearExisting) {
                    portfolioDao.deleteAllTransactions()
                    portfolioDao.deleteAllAssets()
                }

                var importedAssets = 0
                var importedTransactions = 0

                // 1. Charger les transactions existantes pour déduplication (si on ne clear pas tout)
                val existingTxs = if (!clearExisting) portfolioDao.getAllTransactionsOnce() else emptyList()
                val processedKeys = mutableSetOf<String>()

                for (a in assetsList) {
                    try {
                        portfolioDao.insertAsset(AssetEntity(
                            id = a["id"] as String,
                            name = a["name"] as String,
                            symbol = a["symbol"] as String,
                            type = AssetType.valueOf(a["type"] as String)
                        ))
                        importedAssets++
                    } catch (e: Exception) { e.printStackTrace() }
                }

                for (t in transactionsList) {
                    try {
                        val assetId = t["assetId"] as String
                        val typeStr = t["type"] as String
                        val type = TransactionType.valueOf(typeStr)
                        
                        // Extraction plus directe et robuste des nombres (Gson/Room)
                        val quantity = (t["quantity"] as? Number)?.toDouble() ?: 0.0
                        val priceAtDate = (t["priceAtDate"] as? Number)?.toDouble() ?: 0.0
                        val date = (t["date"] as? Number)?.toLong() ?: 0L
                        val fees = (t["fees"] as? Number)?.toDouble() ?: 0.0
                        val jsonId = (t["id"] as? Number)?.toLong() ?: 0L

                        // Déduplication logique uniquement si on ne vide pas tout (import manuel)
                        if (!clearExisting) {
                            val isDuplicate = existingTxs.any {
                                it.assetId == assetId && it.date == date && 
                                it.type == type && Math.abs(it.quantity - quantity) < 0.000001 &&
                                Math.abs(it.priceAtDate - priceAtDate) < 0.000001
                            }
                            if (isDuplicate) continue
                        }

                        // En mode sync (clearExisting), on tente de garder l'ID original du JSON
                        val txId = if (clearExisting && jsonId > 0) jsonId else 0L

                        portfolioDao.insertTransaction(TransactionEntity(
                            id = txId,
                            assetId = assetId,
                            type = type,
                            quantity = quantity,
                            priceAtDate = priceAtDate,
                            date = date,
                            fees = fees
                        ))
                        importedTransactions++
                    } catch (e: Exception) { e.printStackTrace() }
                }

                ImportResult.Success(importedAssets, importedTransactions)
            }
        } catch (e: Exception) {
            ImportResult.Error(e.message ?: "Erreur inconnue")
        }
    }

    sealed class ImportResult {
        data class Success(val assets: Int, val transactions: Int) : ImportResult()
        data class Error(val message: String) : ImportResult()
    }
}

