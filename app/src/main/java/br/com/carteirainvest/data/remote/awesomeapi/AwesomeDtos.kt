package br.com.carteirainvest.data.remote.awesomeapi

import com.google.gson.annotations.SerializedName

data class AwesomeRateDto(
    @SerializedName("code") val code: String? = null,
    @SerializedName("codein") val codeIn: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("bid") val bid: String? = null,
    @SerializedName("ask") val ask: String? = null,
    @SerializedName("pctChange") val pctChange: String? = null,
    @SerializedName("timestamp") val timestamp: String? = null,
)
