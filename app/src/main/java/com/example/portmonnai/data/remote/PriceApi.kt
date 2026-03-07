package com.example.portmonnai.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

interface CoinGeckoApi {
    @GET("simple/price")
    suspend fun getPrices(
        @Query("ids") ids: String,
        @Query("vs_currencies") currencies: String = "eur",
        @Query("include_24hr_change") includeChange: Boolean = true
    ): Map<String, Map<String, Double>>

    @GET("search")
    suspend fun searchCoins(
        @Query("query") query: String
    ): SearchResponse
}

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

    @GET("v1/finance/search")
    suspend fun searchTicker(
        @Query("q") query: String,
        @Query("quotesCount") quotesCount: Int = 8,
        @Query("newsCount") newsCount: Int = 0,
        @Query("lang") lang: String = "fr-FR"
    ): YahooSearchResponse
}

data class YahooChartResponse(
    val chart: ChartResult
)

data class ChartResult(
    val result: List<ChartData>?,
    val error: Any?
)

data class ChartData(
    val meta: ChartMeta
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
