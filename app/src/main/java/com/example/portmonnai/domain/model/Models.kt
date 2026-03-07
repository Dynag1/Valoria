package com.example.portmonnai.domain.model

enum class AssetType {
    ETF, METAL, CRYPTO, GOLD_BAR, GOLD_INGOT, GOLD_COIN, STOCK
}

data class Asset(
    val id: String,
    val name: String,
    val symbol: String,
    val type: AssetType,
    val currentPrice: Double? = null,
    val priceChange24h: Double? = null
)

enum class TransactionType {
    BUY, SELL
}

data class Transaction(
    val id: Long = 0,
    val assetId: String,
    val type: TransactionType,
    val quantity: Double,
    val priceAtDate: Double,
    val date: Long,
    val fees: Double = 0.0
)

data class PortfolioAsset(
    val asset: Asset,
    val totalQuantity: Double,
    val averageBuyPrice: Double,
    val totalValue: Double,
    val totalProfit: Double,
    val profitPercentage: Double,
    val profitToday: Double = 0.0,
    val profitTodayPercentage: Double = 0.0
)
