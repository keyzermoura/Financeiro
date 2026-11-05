package br.com.carteirainvest.data.remote.coingecko

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

data class CoinGeckoPriceDto(
    @SerializedName("brl") val brl: BigDecimal? = null,
    @SerializedName("brl_24h_change") val brl24hChange: BigDecimal? = null,
    @SerializedName("last_updated_at") val lastUpdatedAt: Long? = null,
)
