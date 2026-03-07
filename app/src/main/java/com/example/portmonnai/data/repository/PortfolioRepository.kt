package com.example.portmonnai.data.repository

import com.example.portmonnai.data.local.AssetEntity
import com.example.portmonnai.data.local.PortfolioDao
import com.example.portmonnai.data.local.TransactionEntity
import com.example.portmonnai.data.remote.CoinGeckoApi
import com.example.portmonnai.domain.model.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

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

@Singleton
class PortfolioRepository @Inject constructor(
    private val portfolioDao: PortfolioDao,
    private val coinGeckoApi: CoinGeckoApi,
    private val yahooFinanceApi: com.example.portmonnai.data.remote.YahooFinanceApi
) {
    fun getPortfolioAssets(): Flow<List<PortfolioAsset>> {
        return combine(
            portfolioDao.getAllAssets(),
            portfolioDao.getAllTransactions(),
            flow {
                while (true) {
                    emit(fetchCurrentPrices())
                    kotlinx.coroutines.delay(60000)
                }
            }
        ) { assets, transactions, prices ->
            assets.map { entity ->
                val assetTransactions = transactions.filter { it.assetId == entity.id }
                val totalQty = assetTransactions.sumOf {
                    if (it.type == TransactionType.BUY) it.quantity else -it.quantity
                }
                val buyTransactions = assetTransactions.filter { it.type == TransactionType.BUY }
                val totalBuyQty = buyTransactions.sumOf { it.quantity }
                val avgPrice = if (totalBuyQty > 0) {
                    buyTransactions.sumOf { it.quantity * it.priceAtDate } / totalBuyQty
                } else 0.0

                val priceData = prices[entity.id]
                val currentPrice = priceData?.price ?: 0.0
                val totalValue = totalQty * currentPrice
                val profit = totalValue - (totalQty * avgPrice)
                val profitPct = if (avgPrice > 0) (profit / (totalQty * avgPrice)) * 100 else 0.0

                val change24h = priceData?.change24h ?: 0.0
                val profitToday = totalValue * (change24h / 100)

                PortfolioAsset(
                    asset = Asset(entity.id, entity.name, entity.symbol, entity.type, currentPrice, change24h),
                    totalQuantity = totalQty,
                    averageBuyPrice = avgPrice,
                    totalValue = totalValue,
                    totalProfit = profit,
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
        var usdEurRate = 1.10
        try {
            val eurUsdResponse = yahooFinanceApi.getChartData("EURUSD=X")
            usdEurRate = eurUsdResponse.chart.result?.firstOrNull()?.meta?.regularMarketPrice ?: 1.10
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

        // 3. Fetch metals via Yahoo Finance (prix spot or/argent)
        var goldSpotEur = 0.0
        var goldChange24h = 0.0
        try {
            val goldResp = yahooFinanceApi.getChartData("XAUEUR=X")
            val goldMeta = goldResp.chart.result?.firstOrNull()?.meta
            if (goldMeta != null && !goldMeta.regularMarketPrice.isNaN()) {
                goldSpotEur = goldMeta.regularMarketPrice
                val prevClose = goldMeta.chartPreviousClose
                goldChange24h = if (prevClose > 0) ((goldSpotEur - prevClose) / prevClose) * 100 else 0.0
            }
        } catch (e: Exception) {
            // Fallback: utilise PAX Gold (CoinGecko)
            val paxGoldPrice = result["pax-gold"]?.price ?: 0.0
            if (paxGoldPrice > 0) goldSpotEur = paxGoldPrice
        }

        if (goldSpotEur > 0) {
            val gramPrice = goldSpotEur / 31.1035
            result["gold_bar"] = PriceData(gramPrice, goldChange24h)
            result["gold_ingot"] = PriceData(gramPrice, goldChange24h)
            // Napoléon 20Fr = 5.806g d'or fin
            result["gold_coin_napoleon"] = PriceData(gramPrice * 5.806, goldChange24h)
        }

        try {
            val silverResp = yahooFinanceApi.getChartData("XAGEUR=X")
            val silverMeta = silverResp.chart.result?.firstOrNull()?.meta
            if (silverMeta != null && !silverMeta.regularMarketPrice.isNaN()) {
                val silverSpot = silverMeta.regularMarketPrice
                val prevClose = silverMeta.chartPreviousClose
                val silverChange = if (prevClose > 0) ((silverSpot - prevClose) / prevClose) * 100 else 0.0
                result["silver_bar"] = PriceData(silverSpot / 31.1035, silverChange)
            }
        } catch (e: Exception) { e.printStackTrace() }

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

        return result
    }

    fun getTransactionsForAsset(assetId: String): Flow<List<Transaction>> {
        return portfolioDao.getTransactionsForAsset(assetId).map { list ->
            list.map { e ->
                Transaction(e.id, e.assetId, e.type, e.quantity, e.priceAtDate, e.date, e.fees)
            }
        }
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

    suspend fun exportToJson(): String {
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

    suspend fun importFromJson(json: String): ImportResult {
        return try {
            val gson = com.google.gson.Gson()
            @Suppress("UNCHECKED_CAST")
            val data = gson.fromJson(json, Map::class.java) as Map<String, Any>

            @Suppress("UNCHECKED_CAST")
            val assetsList = data["assets"] as? List<Map<String, Any>> ?: emptyList()
            @Suppress("UNCHECKED_CAST")
            val transactionsList = data["transactions"] as? List<Map<String, Any>> ?: emptyList()

            var importedAssets = 0
            var importedTransactions = 0

            for (a in assetsList) {
                try {
                    portfolioDao.insertAsset(AssetEntity(
                        id = a["id"] as String,
                        name = a["name"] as String,
                        symbol = a["symbol"] as String,
                        type = AssetType.valueOf(a["type"] as String)
                    ))
                    importedAssets++
                } catch (e: Exception) { /* skip malformed */ }
            }

            for (t in transactionsList) {
                try {
                    val idDouble = (t["id"] as? Double) ?: 0.0
                    portfolioDao.insertTransaction(TransactionEntity(
                        id = 0, // auto-generate new ID to avoid conflicts
                        assetId = t["assetId"] as String,
                        type = TransactionType.valueOf(t["type"] as String),
                        quantity = (t["quantity"] as Double),
                        priceAtDate = (t["priceAtDate"] as Double),
                        date = ((t["date"] as? Double)?.toLong() ?: 0L),
                        fees = (t["fees"] as? Double) ?: 0.0
                    ))
                    importedTransactions++
                } catch (e: Exception) { /* skip malformed */ }
            }

            ImportResult.Success(importedAssets, importedTransactions)
        } catch (e: Exception) {
            ImportResult.Error(e.message ?: "Erreur inconnue")
        }
    }

    sealed class ImportResult {
        data class Success(val assets: Int, val transactions: Int) : ImportResult()
        data class Error(val message: String) : ImportResult()
    }
}

