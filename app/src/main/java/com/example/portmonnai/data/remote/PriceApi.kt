package com.example.portmonnai.data.remote

import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Path

interface CoinGeckoApi {
    @GET("simple/price")
    suspend fun getPrices(
        @Query("ids") ids: String,
        @Query("vs_currencies") currencies: String = "eur",
        @Query("include_24hr_change") includeChange: Boolean = true
    ): Map<String, Map<String, Double>>

    @GET("coins/{id}/market_chart")
    suspend fun getMarketChart(
        @Path("id") id: String,
        @Query("vs_currency") currency: String = "eur",
        @Query("days") days: String = "max" // ou "1", "7", "365", "max"
    ): CoinGeckoMarketChartResponse

    @GET("search")
    suspend fun searchCoins(
        @Query("query") query: String
    ): SearchResponse
}

data class CoinGeckoMarketChartResponse(
    val prices: List<List<Any>>? // listOf([timestamp, price], ...)
)

data class SearchResponse(
    val coins: List<CoinSearchItem>
)

data class CoinSearchItem(
    val id: String,
    val name: String,
    val symbol: String,
    val thumb: String?
)

interface YahooFinanceApi {
    @GET("v8/finance/chart/{ticker}")
    suspend fun getChartData(
        @retrofit2.http.Path("ticker") ticker: String,
        @Query("interval") interval: String = "1d",
        @Query("range") range: String = "1d"
    ): YahooChartResponse

    @GET("v8/finance/chart/{ticker}")
    suspend fun getHistoricalChartData(
        @retrofit2.http.Path("ticker") ticker: String,
        @Query("period1") period1: Long? = null,
        @Query("period2") period2: Long? = null,
        @Query("range") range: String? = null,
        @Query("interval") interval: String = "1d"
    ): YahooChartResponse

    @GET("v7/finance/quote")
    suspend fun getQuotes(
        @Query("symbols") symbols: String
    ): YahooQuoteResponse

    @GET("v1/finance/search")
    suspend fun searchTicker(
        @Query("q") query: String,
        @Query("quotesCount") quotesCount: Int = 8,
        @Query("newsCount") newsCount: Int = 0,
        @Query("lang") lang: String = "fr-FR"
    ): YahooSearchResponse
}

data class YahooQuoteResponse(
    val quoteResponse: QuoteResultWrapper
)

data class QuoteResultWrapper(
    val result: List<YahooQuote>?,
    val error: Any?
)

data class YahooQuote(
    val symbol: String,
    val regularMarketPrice: Double,
    val regularMarketChangePercent: Double? = 0.0,
    val regularMarketPreviousClose: Double? = 0.0,
    val currency: String? = "EUR"
)

data class YahooChartResponse(
    val chart: ChartResultWrapper
)

data class ChartResultWrapper(
    val result: List<ChartData>?,
    val error: Any?
)

data class ChartData(
    val meta: ChartMeta,
    val timestamp: List<Long>?,
    val indicators: ChartIndicators?
)

data class ChartIndicators(
    val quote: List<ChartQuote>?
)

data class ChartQuote(
    val close: List<Double?>?
)

data class ChartMeta(
    val currency: String,
    val regularMarketPrice: Double,
    val chartPreviousClose: Double,
    val symbol: String
)

data class YahooSearchResponse(
    val quotes: List<YahooSearchQuote>?
)

data class YahooSearchQuote(
    val symbol: String,
    val shortname: String?,
    val longname: String?,
    val quoteType: String?,
    val exchange: String?
)
