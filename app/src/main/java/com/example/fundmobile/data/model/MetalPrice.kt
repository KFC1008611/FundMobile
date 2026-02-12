package com.example.fundmobile.data.model

data class MetalPrice(
    val name: String,
    val price: String,
    val changeAmount: String,
    val changePct: String,
    val openPrice: String,
    val highPrice: String,
    val lowPrice: String,
    val prevClose: String,
    val updateTime: String,
    val unit: String
)
