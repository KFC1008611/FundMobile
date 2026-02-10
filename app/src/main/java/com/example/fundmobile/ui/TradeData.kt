package com.example.fundmobile.ui

data class TradeData(
    val type: String,
    val amount: Double? = null,
    val share: Double? = null,
    val price: Double? = null,
    val feeRate: Double? = null,
    val feeMode: String? = null,
    val feeValue: String? = null,
    val date: String,
    val isAfter3pm: Boolean
)
