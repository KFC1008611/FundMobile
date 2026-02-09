package com.example.fundmobile.data.model

data class FundData(
    val code: String,
    val name: String,
    val dwjz: String?,
    val gsz: String?,
    val gztime: String?,
    val jzrq: String?,
    val gszzl: Double?,
    val zzl: Double?,
    val noValuation: Boolean = false,
    val holdings: List<StockHolding> = emptyList()
)
