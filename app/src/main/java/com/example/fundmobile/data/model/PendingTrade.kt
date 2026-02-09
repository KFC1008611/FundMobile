package com.example.fundmobile.data.model

data class PendingTrade(
    val id: String,
    val fundCode: String,
    val fundName: String,
    val type: String,
    val share: Double?,
    val amount: Double?,
    val feeRate: Double?,
    val feeMode: String?,
    val feeValue: String?,
    val date: String,
    val isAfter3pm: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
