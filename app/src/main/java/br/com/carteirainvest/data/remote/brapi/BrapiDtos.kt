package br.com.carteirainvest.data.remote.brapi

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

/** Raw Brapi payloads. They are intentionally kept out of the domain layer. */
data class BrapiQuoteResponse(
    @SerializedName("results") val results: List<BrapiQuoteDto>? = null,
    @SerializedName("error") val error: Boolean? = null,
    @SerializedName("message") val message: String? = null,
)

data class BrapiQuoteDto(
    @SerializedName("symbol") val symbol: String? = null,
    @SerializedName("shortName") val shortName: String? = null,
    @SerializedName("longName") val longName: String? = null,
    @SerializedName("regularMarketPrice") val regularMarketPrice: BigDecimal? = null,
    @SerializedName("regularMarketChange") val regularMarketChange: BigDecimal? = null,
    @SerializedName("regularMarketChangePercent") val regularMarketChangePercent: BigDecimal? = null,
    @SerializedName("regularMarketPreviousClose") val regularMarketPreviousClose: BigDecimal? = null,
    @SerializedName("regularMarketTime") val regularMarketTime: String? = null,
    @SerializedName("logourl") val logoUrl: String? = null,
    @SerializedName("historicalDataPrice") val historicalDataPrice: List<BrapiHistoricalDto>? = null,
    @SerializedName("dividendsData") val dividendsData: BrapiDividendsDataDto? = null,
)

data class BrapiHistoricalDto(
    @SerializedName("date") val date: Long? = null,
    @SerializedName("close") val close: BigDecimal? = null,
    @SerializedName("open") val open: BigDecimal? = null,
    @SerializedName("high") val high: BigDecimal? = null,
    @SerializedName("low") val low: BigDecimal? = null,
    @SerializedName("volume") val volume: BigDecimal? = null,
)

data class BrapiDividendsDataDto(
    @SerializedName("cashDividends") val cashDividends: List<BrapiCashDividendDto>? = null,
)

data class BrapiCashDividendDto(
    @SerializedName("paymentDate") val paymentDate: String? = null,
    @SerializedName("approvedOn") val approvedOn: String? = null,
    @SerializedName("rate") val rate: BigDecimal? = null,
    @SerializedName("label") val label: String? = null,
    @SerializedName("relatedTo") val relatedTo: String? = null,
    @SerializedName("isinCode") val isinCode: String? = null,
)
